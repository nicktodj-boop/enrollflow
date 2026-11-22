package g9.enrollflow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 選課系統的對外門面 (Facade)。網頁版（經 CheerpJ）與 {@link ConsoleUI} 都只呼叫這裡的 public 方法，
 * 規則只寫在這一處。
 *
 * <p>與 JS 之間只傳 int、boolean、String。操作方法一律回傳 JSON 字串，
 * 格式為 {@code {"ok":true/false,"msg":"..."}}，必要時再加欄位。</p>
 *
 * <p>資料結構分工：</p>
 * <ul>
 *   <li>Map：課號 → 課程、學號 → 學生</li>
 *   <li>Set：學生的已選課號與已佔時段（衝堂檢查），見 {@link Student}</li>
 *   <li>Queue：每門課的候補名單，見 {@link Course#getWaitlist()}</li>
 *   <li>Stack：玩家的復原紀錄 {@link #undoStack}</li>
 *   <li>List：課程清單、操作紀錄 {@link #log}</li>
 * </ul>
 *
 * <p>負責：B 選課規則。</p>
 */
public class CourseSystem {

    /** 加退選期天數。 */
    public static final int TOTAL_DAYS = 10;

    /**
     * 模擬同學人數，大二、大三各一半。34 門課、每門 5–8 個名額，
     * 60 人才會讓必修班額滿、出現候補（估算見 選課系統設計.md 第 3.2 節）。
     */
    static final int SIMULATED_STUDENTS = 60;

    /** 玩家的年級（大二）。 */
    static final int PLAYER_GRADE = 2;

    /** 復原最多記幾步。 */
    static final int UNDO_LIMIT = 10;

    /** 課號 → 課程；LinkedHashMap 保留 CourseData 的排列順序。 */
    private final Map<String, Course> courses = new LinkedHashMap<String, Course>();

    /** 學號 → 學生（含玩家）。 */
    private final Map<String, Student> students = new LinkedHashMap<String, Student>();

    /** 模擬同學（不含玩家），給 Simulator 依序處理。 */
    private final List<Student> simulated = new ArrayList<Student>();

    /**
     * 玩家的復原紀錄，頂端是最近一次操作。
     * Java 官方建議用 {@link Deque} 當堆疊（{@code java.util.Stack} 是舊類別）：
     * {@code push()} 放到頂端、{@code pop()} 取出頂端、{@code peek()} 查看頂端。
     */
    private final Deque<Action> undoStack = new ArrayDeque<Action>();

    /** 操作紀錄，最新的放在最後；畫面顯示時反過來。 */
    private final List<String> log = new ArrayList<String>();

    private Student player;
    private Simulator simulator;
    private int day;
    private boolean missionMode;

    /**
     * 最近一次執行的資料結構操作名稱，用 Java 實際呼叫的方法名，
     * 例如 "offer()"（排候補）、"poll()"（遞補）、"push()"、"pop()"（復原）、"retainAll()"（衝堂檢查）。
     * 給網頁「資料結構透視面板」顯示；由各操作方法在動到資料結構時設定。
     */
    private String lastOp = "";

    /** 復原堆疊在透視面板上最多顯示幾筆。 */
    static final int UNDO_PREVIEW = 5;

    /** 建立系統並直接開一局（隨機種子、不開任務模式）。 */
    public CourseSystem() {
        newGame(0, false);
    }

    /**
     * 開新局：重建課程與學生資料，回到第 1 天。
     *
     * @param seed        亂數種子；0 表示每次不同。用 int 而非 long，因為 JS 傳 long 給 Java 需要 BigInt
     * @param missionMode 是否開啟任務模式（第二階段功能）
     */
    public void newGame(int seed, boolean missionMode) {
        courses.clear();
        students.clear();
        simulated.clear();
        undoStack.clear();
        log.clear();
        lastOp = "";

        for (Course c : CourseData.createCourses()) {
            courses.put(c.getCode(), c);
        }
        // 先建立模擬器：同學姓名與志願清單都用它的亂數產生，固定種子時每局結果相同
        simulator = new Simulator(seed);

        player = new Student("S000", "你", true, PLAYER_GRADE);
        students.put(player.getId(), player);
        List<String> names = simulator.generateNames(SIMULATED_STUDENTS);
        for (int i = 0; i < SIMULATED_STUDENTS; i++) {
            // 前一半是大二、後一半是大三
            int grade = i < SIMULATED_STUDENTS / 2 ? 2 : 3;
            Student s = new Student(String.format("S%03d", i + 1), names.get(i), false, grade);
            students.put(s.getId(), s);
            simulated.add(s);
        }

        simulator.assignWishlists(simulated, new ArrayList<Course>(courses.values()));
        this.missionMode = missionMode;
        day = 1;
        log.add("加退選開始，共 " + TOTAL_DAYS + " 天；學分需在 " + Student.MIN_CREDITS + "–" + Student.MAX_CREDITS + " 之間");
    }

    // ===================== 玩家操作 =====================

    /**
     * 加選。檢查順序：課號存在 → 未重複 → 沒選同科目別班 → 不衝堂 → 不超學分 → 有名額（見 {@link #checkAdd}）。
     *
     * @param code 課號
     * @return JSON；額滿時 ok 為 false，並加上 {@code "canWait":true} 讓畫面詢問是否排候補
     */
    public String addCourse(String code) {
        if (isFinished()) {
            return JsonWriter.result(false, "加退選期已結束");
        }
        Course c = findCourse(code);
        Check check = checkAdd(player, c);
        switch (check) {
            case NOT_FOUND:
                return JsonWriter.result(false, "找不到課號「" + code + "」");
            case TAKEN:
                return JsonWriter.result(false, "你已經選了 " + c);
            case SAME_SUBJECT:
                return JsonWriter.result(false, sameSubjectMessage(c));
            case CONFLICT:
                lastOp = "retainAll()";                       // 衝堂是用 Set 交集查出來的
                return JsonWriter.result(false, conflictMessage(c));
            case OVER_CREDITS:
                return JsonWriter.result(false, "加選後會變成 " + (player.getCredits() + c.getCredits())
                        + " 學分，超過上限 " + Student.MAX_CREDITS + " 學分");
            case FULL:
                if (player.getWaitingCodes().contains(c.getCode())) {
                    return JsonWriter.result(false, c + " 已額滿，你在候補第 " + c.waitlistPosition(player) + " 位");
                }
                // 額滿：不直接排候補，回傳 canWait 讓玩家自己決定
                return new JsonWriter().beginObject()
                        .field("ok", false)
                        .field("msg", c + " 已額滿（" + c.getEnrolledIds().size() + "/" + c.getCapacity()
                                + "），目前 " + c.getWaitlist().size() + " 人候補。要排候補嗎？")
                        .field("canWait", true)
                        .endObject().toString();
            default:
                break;
        }
        enroll(player, c);
        pushUndo(new Action(ActionType.ADD, c.getCode()));
        lastOp = "add()";                                     // 課號加進已選 Set、時段加進已佔 Set
        record("你加選 " + c);
        return JsonWriter.result(true, "加選成功：" + c + "，目前 " + player.getCredits() + " 學分");
    }

    /**
     * 退選。退選後立刻對該課執行遞補（見 {@link #fillFromWaitlist(Course)}）。
     *
     * @param code 課號
     * @return JSON，msg 含遞補結果
     */
    public String dropCourse(String code) {
        if (isFinished()) {
            return JsonWriter.result(false, "加退選期已結束");
        }
        Course c = findCourse(code);
        if (c == null) {
            return JsonWriter.result(false, "找不到課號「" + code + "」");
        }
        if (!player.getCourseCodes().contains(c.getCode())) {
            return JsonWriter.result(false, "你沒有選 " + c);
        }
        unenroll(player, c);
        pushUndo(new Action(ActionType.DROP, c.getCode()));
        lastOp = "remove()";
        record("你退選 " + c);
        List<String> fills = fillFromWaitlist(c);             // 有人遞補時 lastOp 會變成 poll()
        recordAll(fills);
        return JsonWriter.result(true, "已退選 " + c + joinLines(fills));
    }

    /**
     * 排候補，加到該課候補名單隊尾。
     *
     * @param code 課號
     * @return JSON，加上 {@code "position"} 欄位表示目前順位
     */
    public String joinWaitlist(String code) {
        if (isFinished()) {
            return JsonWriter.result(false, "加退選期已結束");
        }
        Course c = findCourse(code);
        Check check = checkJoin(player, c);
        if (check != Check.OK) {
            return JsonWriter.result(false, joinProblem(check, c));
        }
        joinQueue(player, c);
        pushUndo(new Action(ActionType.JOIN_WAIT, c.getCode()));
        lastOp = "offer()";                                   // 加到佇列隊尾
        int position = c.waitlistPosition(player);
        record("你排入 " + c + " 候補，目前第 " + position + " 位");
        return new JsonWriter().beginObject()
                .field("ok", true)
                .field("msg", "已排入 " + c + " 候補，目前第 " + position + " 位")
                .field("position", position)
                .endObject().toString();
    }

    /**
     * 取消候補。後面的人自動往前一位（LinkedList 從中間刪除）。
     *
     * @param code 課號
     * @return JSON
     */
    public String leaveWaitlist(String code) {
        if (isFinished()) {
            return JsonWriter.result(false, "加退選期已結束");
        }
        Course c = findCourse(code);
        if (c == null) {
            return JsonWriter.result(false, "找不到課號「" + code + "」");
        }
        int position = c.waitlistPosition(player);
        if (position == 0) {
            return JsonWriter.result(false, "你沒有排 " + c + " 的候補");
        }
        leaveQueue(player, c);
        pushUndo(new Action(ActionType.LEAVE_WAIT, c.getCode(), position));
        lastOp = "remove()";
        record("你取消 " + c + " 候補（原本第 " + position + " 位）");
        return JsonWriter.result(true, "已取消 " + c + " 候補，後面的人往前一位");
    }

    /**
     * 復原上一步：pop 復原堆疊頂端，依 {@link ActionType} 做反向操作（規則見設計檔第 3.5 節）。
     *
     * <p>反向操作本身不再 push，所以不能「復原的復原」。遞補、模擬同學的動作也不會被撤銷：
     * 堆疊只記得玩家自己做過什麼，別人的動作已經發生了。</p>
     *
     * @return JSON；堆疊為空時 ok 為 false
     */
    public String undo() {
        if (isFinished()) {
            return JsonWriter.result(false, "加退選期已結束");
        }
        if (undoStack.isEmpty()) {
            // 第 2 天以後堆疊是空的，多半是因為按了「下一天」被清空，順便告訴玩家
            return JsonWriter.result(false, day > 1
                    ? "沒有可以復原的操作（按「下一天」後，前一天的操作不能復原）"
                    : "沒有可以復原的操作");
        }
        Action a = undoStack.pop();                           // 取出最近一次操作
        Course c = courses.get(a.getCourseCode());
        String msg;
        switch (a.getType()) {
            case ADD: {
                // 加選 → 退選；空出的名額照樣遞補，遞補結果不會再被復原
                unenroll(player, c);
                List<String> fills = fillFromWaitlist(c);
                msg = "已復原加選：退掉 " + c + joinLines(fills);
                record("復原：退掉 " + c);
                recordAll(fills);
                break;
            }
            case DROP: {
                // 退選 → 重新加選；名額被候補補走時改排候補
                Check check = checkAdd(player, c);
                if (check == Check.OK) {
                    enroll(player, c);
                    msg = "已復原退選：重新選上 " + c;
                } else if (check == Check.FULL) {
                    joinQueue(player, c);
                    msg = "名額已被候補的人補走，無法直接選回 " + c + "，已改排候補第 "
                            + c.waitlistPosition(player) + " 位";
                } else {
                    msg = "無法復原退選 " + c + "：" + reasonOf(check);
                }
                record("復原：" + msg);
                break;
            }
            case JOIN_WAIT:
                leaveQueue(player, c);
                msg = "已復原排候補：取消 " + c + " 候補";
                record("復原：取消 " + c + " 候補");
                break;
            case LEAVE_WAIT: {
                // 取消候補 → 重新排隊；原本的順位拿不回來，只能排到隊尾
                Check check = checkJoin(player, c);
                if (check == Check.OK) {
                    joinQueue(player, c);
                    msg = "已重新排入 " + c + " 候補，但原本第 " + a.getPosition()
                            + " 位拿不回來，只能排到隊尾第 " + c.waitlistPosition(player) + " 位";
                } else {
                    msg = "無法重新排候補：" + joinProblem(check, c);
                }
                record("復原：" + msg);
                break;
            }
            default:
                msg = "未知的操作";
        }
        lastOp = "pop()";
        return JsonWriter.result(true, msg);
    }

    /**
     * 推進一天：模擬同學行動、清空復原堆疊。第 {@value #TOTAL_DAYS} 天之後不再推進。
     *
     * @return JSON，msg 為當天動作摘要
     */
    public String nextDay() {
        if (isFinished()) {
            return JsonWriter.result(false, "加退選期已結束");
        }
        List<String> report = simulator.simulateDay(this, simulated);
        recordAll(report);

        // 玩家被遞補或被移出候補，要在訊息列特別說
        StringBuilder mine = new StringBuilder();
        for (String line : report) {
            if (line.startsWith("你")) {
                mine.append("；").append(line);
            }
        }
        String summary = "第 " + day + " 天結束：" + (report.isEmpty() ? "" : report.get(0)) + mine;

        undoStack.clear();                                    // 前一天的操作不能再復原
        lastOp = "clear()";
        day++;
        if (isFinished()) {
            log.add("加退選結束：你選上 " + player.getCourseCodes().size() + " 門課，共 "
                    + player.getCredits() + " 學分");
            if (!isPassed()) {
                String fail = "學分不足 " + Student.MIN_CREDITS + " 學分，請申請休學";
                log.add(fail);
                summary += "。" + fail;
            }
        }
        return JsonWriter.result(true, summary);
    }

    // ===================== 共用規則（玩家與模擬同學都走這裡） =====================

    /** 加選或排候補前的檢查結果。 */
    enum Check {
        /** 可以進行。 */
        OK,
        /** 沒有這個課號。 */
        NOT_FOUND,
        /** 已經選上。 */
        TAKEN,
        /** 已在候補名單中。 */
        WAITING,
        /** 已選同科目的另一班（例如選了經濟學 A 就不能再選 B）。 */
        SAME_SUBJECT,
        /** 與已選的課衝堂。 */
        CONFLICT,
        /** 加選後超過學分上限。 */
        OVER_CREDITS,
        /** 名額已滿（加選時）。 */
        FULL,
        /** 還有名額，不需要排候補（排候補時）。 */
        NOT_FULL
    }

    /**
     * 加選檢查。順序有意義：先擋「不可能選」的（重複、衝堂、超學分），最後才看名額，
     * 這樣回傳 FULL 時就代表「排候補有機會補上」。
     *
     * @param s 學生
     * @param c 課程；null 表示課號不存在
     * @return 檢查結果
     */
    Check checkAdd(Student s, Course c) {
        if (c == null) {
            return Check.NOT_FOUND;
        }
        if (s.getCourseCodes().contains(c.getCode())) {        // Set.contains：O(1) 查重複
            return Check.TAKEN;
        }
        if (takenSection(s, c) != null) {
            return Check.SAME_SUBJECT;
        }
        if (!s.findConflicts(c).isEmpty()) {                   // Set 交集不為空就是衝堂
            return Check.CONFLICT;
        }
        if (s.wouldExceedCredits(c)) {
            return Check.OVER_CREDITS;
        }
        if (c.isFull()) {
            return Check.FULL;
        }
        return Check.OK;
    }

    /**
     * 排候補檢查：只有「額滿、而且補上時會符合條件」的課才能排。
     * 排的時候符合，不代表輪到時還符合；遞補時會再檢查一次（見 {@link #fillFromWaitlist}）。
     *
     * @param s 學生
     * @param c 課程；null 表示課號不存在
     * @return OK 表示可以排
     */
    Check checkJoin(Student s, Course c) {
        if (c != null && s.getWaitingCodes().contains(c.getCode())) {
            return Check.WAITING;
        }
        Check check = checkAdd(s, c);
        if (check == Check.FULL) {
            return Check.OK;
        }
        return check == Check.OK ? Check.NOT_FULL : check;
    }

    /**
     * 對一門有空位的課執行遞補：從候補隊首依序 poll()，跳過衝堂或超學分的人，直到補滿或候補清空。
     *
     * <p>本作展示重點：排第一位不保證選上。排候補之後改選了同科目別班、選了別的課而衝堂、
     * 或學分滿了的人，輪到他時會被移出候補，佇列繼續往後找下一位。</p>
     *
     * @param course 空出名額的課
     * @return 遞補過程的描述，每筆一行
     */
    List<String> fillFromWaitlist(Course course) {
        List<String> lines = new ArrayList<String>();
        while (!course.isFull() && !course.getWaitlist().isEmpty()) {
            Student next = course.getWaitlist().poll();       // 取出隊首（先排先補）
            next.getWaitingCodes().remove(course.getCode());
            lastOp = "poll()";
            Check check = checkAdd(next, course);
            if (check == Check.OK) {
                enroll(next, course);
                lines.add(who(next) + "從候補遞補選上 " + course);
            } else {
                lines.add(who(next) + reasonOf(check) + "，自動移出 " + course + " 候補");
            }
        }
        return lines;
    }

    /**
     * 模擬同學加選。規則與玩家相同，只是不寫紀錄、不進復原堆疊。
     *
     * @param s    模擬同學
     * @param code 課號
     * @return 檢查結果；OK 表示已選上
     */
    Check simulateAdd(Student s, String code) {
        Course c = courses.get(code);
        Check check = checkAdd(s, c);
        if (check == Check.OK) {
            enroll(s, c);
        }
        return check;
    }

    /**
     * 模擬同學排候補。
     *
     * @param s    模擬同學
     * @param code 課號
     * @return 是否排入
     */
    boolean simulateJoin(Student s, String code) {
        Course c = courses.get(code);
        if (checkJoin(s, c) != Check.OK) {
            return false;
        }
        joinQueue(s, c);
        lastOp = "offer()";
        return true;
    }

    /**
     * 模擬同學退選，並立刻遞補。
     *
     * @param s    模擬同學
     * @param code 課號
     * @return 遞補過程的描述；第一行是退選本身
     */
    List<String> simulateDrop(Student s, String code) {
        Course c = courses.get(code);
        List<String> lines = new ArrayList<String>();
        if (c == null || !s.getCourseCodes().contains(code)) {
            return lines;
        }
        unenroll(s, c);
        List<String> fills = fillFromWaitlist(c);
        if (!fills.isEmpty()) {
            // 沒有人候補的退選不值得記；有遞補的才記，讓紀錄聚焦在佇列的變化
            lines.add(s.getName() + " 退選 " + c);
            lines.addAll(fills);
        }
        return lines;
    }

    /** 把學生加進課程：課程的已選 Set、學生的已選 Set 與已佔時段 Set 同步更新。 */
    private void enroll(Student s, Course c) {
        c.getEnrolledIds().add(s.getId());
        s.getCourseCodes().add(c.getCode());
        s.getOccupiedSlots().addAll(c.getSlots());
        s.setCredits(s.getCredits() + c.getCredits());
        if (s.getWaitingCodes().contains(c.getCode())) {      // 保險：選上就不必再候補
            leaveQueue(s, c);
        }
    }

    /** 把學生移出課程。已選的課彼此不衝堂，所以直接 removeAll 時段不會誤刪別門課的。 */
    private void unenroll(Student s, Course c) {
        c.getEnrolledIds().remove(s.getId());
        s.getCourseCodes().remove(c.getCode());
        s.getOccupiedSlots().removeAll(c.getSlots());
        s.setCredits(s.getCredits() - c.getCredits());
    }

    /** 排到候補隊尾。 */
    private void joinQueue(Student s, Course c) {
        c.getWaitlist().offer(s);                             // Queue.offer：加到隊尾
        s.getWaitingCodes().add(c.getCode());
    }

    /** 從候補名單中間移除（取消候補），後面的人自動往前。 */
    private void leaveQueue(Student s, Course c) {
        c.getWaitlist().remove(s);                            // 依 Student.equals（學號）找到並刪除
        s.getWaitingCodes().remove(c.getCode());
    }

    /**
     * push 一筆操作到復原堆疊；超過 {@value #UNDO_LIMIT} 筆時丟掉最舊的（堆疊底部）。
     *
     * @param action 玩家剛完成的操作
     */
    private void pushUndo(Action action) {
        undoStack.push(action);
        if (undoStack.size() > UNDO_LIMIT) {
            undoStack.removeLast();                           // ArrayDeque 的尾端就是堆疊底部
        }
    }

    // ===================== 訊息工具 =====================

    /** 課號前後空白去掉後查課程；查不到回傳 null。 */
    private Course findCourse(String code) {
        return code == null ? null : courses.get(code.trim());
    }

    /** 記一筆操作紀錄，前面加上第幾天。 */
    private void record(String text) {
        log.add("第 " + day + " 天：" + text);
    }

    private void recordAll(List<String> lines) {
        for (String line : lines) {
            record(line);
        }
    }

    /** 玩家顯示「你」，其他人顯示姓名加空白。 */
    private static String who(Student s) {
        return s.isPlayer() ? "你" : s.getName() + " ";
    }

    /** 把遞補結果接在訊息後面。 */
    private static String joinLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append("；").append(line);
        }
        return sb.toString();
    }

    /** 檢查結果的中文原因（遞補失敗、復原失敗時使用）。 */
    private static String reasonOf(Check check) {
        switch (check) {
            case TAKEN:
                return "已選上";
            case SAME_SUBJECT:
                return "已選同科目別班";
            case CONFLICT:
                return "衝堂";
            case OVER_CREDITS:
                return "學分已達上限";
            case FULL:
                return "名額已滿";
            case NOT_FOUND:
                return "課程不存在";
            default:
                return check.toString();
        }
    }

    /** 排候補失敗時給玩家的說明。 */
    private String joinProblem(Check check, Course c) {
        switch (check) {
            case NOT_FOUND:
                return "找不到這個課號";
            case TAKEN:
                return "你已經選上 " + c + "，不用排候補";
            case WAITING:
                return "你已經在 " + c + " 候補第 " + c.waitlistPosition(player) + " 位";
            case SAME_SUBJECT:
                return sameSubjectMessage(c) + "，排了也補不上";
            case CONFLICT:
                return conflictMessage(c) + "，排了也補不上";
            case OVER_CREDITS:
                return "選上 " + c + " 會超過 " + Student.MAX_CREDITS + " 學分上限，排了也補不上";
            case NOT_FULL:
                return c + " 還有名額，直接加選就可以";
            default:
                return reasonOf(check);
        }
    }

    /**
     * 找出學生已選、與指定課程同科目的另一班。
     *
     * @param s 學生
     * @param c 課程
     * @return 已選的同科目課程；沒有回傳 null
     */
    private Course takenSection(Student s, Course c) {
        for (String code : s.getCourseCodes()) {
            Course t = courses.get(code);
            if (t != c && t.getSubject().equals(c.getSubject())) {
                return t;
            }
        }
        return null;
    }

    /** @return 課號；null 時回傳空字串（給 JSON 欄位用） */
    private static String codeOf(Course c) {
        return c == null ? "" : c.getCode();
    }

    /** 同科目訊息：指出已經選了哪一班。 */
    private String sameSubjectMessage(Course c) {
        return "你已經選了同科目的 " + takenSection(player, c) + "，不能再選 " + c;
    }

    /** 衝堂訊息：指出跟哪一門課、哪幾節衝堂。 */
    private String conflictMessage(Course c) {
        StringBuilder labels = new StringBuilder();
        for (int slot : player.findConflicts(c)) {
            if (labels.length() > 0) {
                labels.append(' ');
            }
            labels.append(TimeSlot.toLabel(slot));
        }
        Course other = courses.get(findConflictCourse(c));
        return c + " 與 " + other + " 衝堂（" + labels + "）";
    }

    // ===================== 查詢 =====================

    /** @return 加退選期是否已結束 */
    public boolean isFinished() {
        return day > TOTAL_DAYS;
    }

    /**
     * 是否達到學分下限。加退選期間隨時可查，畫面只在結束時用它判斷要不要顯示失敗畫面。
     *
     * @return 玩家學分 ≥ {@value Student#MIN_CREDITS} 時為 true
     */
    public boolean isPassed() {
        return player.getCredits() >= Student.MIN_CREDITS;
    }

    /** @return 目前第幾天 */
    public int getDay() {
        return day;
    }

    /** @return 指定課號的課程，查不到回傳 null（給測試與模擬器使用） */
    Course getCourse(String code) {
        return courses.get(code);
    }

    /** @return 指定學號的學生，玩家是 "S000"（給測試使用） */
    Student getStudent(String id) {
        return students.get(id);
    }

    /** @return 全部課程，依 CourseData 的順序（給測試使用） */
    List<Course> getCourses() {
        return new ArrayList<Course>(courses.values());
    }

    /** @return 全部學生，含玩家（給測試使用） */
    List<Student> getStudents() {
        return new ArrayList<Student>(students.values());
    }

    /**
     * 依關鍵字或星期篩選課程。網頁版在 JS 端篩選以免每打一個字就呼叫 Java，這個方法給終端機版與測試使用。
     *
     * @param keyword 課號或課名的一部分；空字串表示不限
     * @param weekday 星期 1–5；0 表示不限
     * @return JSON，{@code {"ok":true,"courses":[{"code","name","teacher","slotLabel","enrolled","capacity"}]}}
     */
    public String searchJson(String keyword, int weekday) {
        List<Course> found = search(keyword, weekday);
        JsonWriter w = new JsonWriter().beginObject().field("ok", true).beginArray("courses");
        for (Course c : found) {
            w.beginObject()
                    .field("code", c.getCode())
                    .field("name", c.getName())
                    .field("teacher", c.getTeacher())
                    .field("slotLabel", c.getSlotLabel())
                    .field("enrolled", c.getEnrolledIds().size())
                    .field("capacity", c.getCapacity())
                    .endObject();
        }
        return w.endArray().endObject().toString();
    }

    /**
     * 篩選課程：走訪 courses.values()，符合條件的依原本順序放進 List。
     *
     * @param keyword 課號或課名的一部分；null 或空字串表示不限
     * @param weekday 星期 1–5；0 表示不限
     * @return 符合的課程
     */
    List<Course> search(String keyword, int weekday) {
        String k = keyword == null ? "" : keyword.trim();
        List<Course> result = new ArrayList<Course>();
        for (Course c : courses.values()) {
            boolean matchKeyword = k.isEmpty() || c.getCode().contains(k) || c.getName().contains(k);
            boolean matchDay = weekday == 0;
            for (int slot : c.getSlots()) {
                if (TimeSlot.dayOf(slot) == weekday) {
                    matchDay = true;
                    break;
                }
            }
            if (matchKeyword && matchDay) {
                result.add(c);
            }
        }
        return result;
    }

    /**
     * 終端機版用的狀態文字：天數、學分、已選課程、候補順位。
     *
     * @return 多行文字
     */
    public String getStatusText() {
        StringBuilder sb = new StringBuilder();
        sb.append(" 第 ").append(Math.min(day, TOTAL_DAYS)).append(" / ").append(TOTAL_DAYS).append(" 天")
                .append(isFinished() ? "（已結束）" : "")
                .append("　學分 ").append(player.getCredits()).append(" / ").append(Student.MAX_CREDITS)
                .append("　可復原 ").append(undoStack.size()).append(" 步\n");
        sb.append(" 已選：");
        if (player.getCourseCodes().isEmpty()) {
            sb.append("（無）");
        }
        for (String code : player.getCourseCodes()) {
            Course c = courses.get(code);
            sb.append("\n   ").append(c).append("　").append(c.getSlotLabel());
        }
        sb.append("\n 候補：");
        if (player.getWaitingCodes().isEmpty()) {
            sb.append("（無）");
        }
        for (String code : player.getWaitingCodes()) {
            Course c = courses.get(code);
            sb.append("\n   ").append(c).append("　第 ").append(c.waitlistPosition(player))
                    .append(" 位 / 共 ").append(c.getWaitlist().size()).append(" 人");
        }
        return sb.toString();
    }

    /**
     * 終端機版用的課程清單。
     *
     * @param keyword 課號或課名的一部分；空字串表示全部
     * @return 每門課一行
     */
    public String getCourseListText(String keyword) {
        StringBuilder sb = new StringBuilder();
        for (Course c : search(keyword, 0)) {
            String mark = player.getCourseCodes().contains(c.getCode()) ? "已選"
                    : c.waitlistPosition(player) > 0 ? "候補" + c.waitlistPosition(player)
                    : c.isFull() ? "額滿" : "";
            sb.append(String.format(" %-5s %-14s %-6s %d學分 %-16s %d/%d 候補%d %s%n",
                    c.getCode(), c.getName(), c.getTeacher(), c.getCredits(), c.getSlotLabel(),
                    c.getEnrolledIds().size(), c.getCapacity(), c.getWaitlist().size(), mark));
        }
        return sb.toString();
    }

    /**
     * 回傳畫面需要的全部狀態，網頁每次操作後呼叫一次並整個重畫。
     *
     * <p>欄位一覽（網頁 docs/app.js 依這些名稱讀取）：</p>
     * <ul>
     *   <li>{@code day, totalDays, finished, credits, maxCredits, minCredits, passed, missionMode, undoSize, lastOp}</li>
     *   <li>{@code courses[]}：每門課的基本資料（含 {@code grade} 年級、{@code required} 必修）、名額、候補名單 {@code waitlist[]}（隊首在前），
     *       以及從玩家角度算出的 {@code taken}、{@code myPosition}、{@code conflictWith}、
     *       {@code sameSubjectWith}（已選的同科目別班課號）</li>
     *   <li>{@code myCourses[]}：玩家已選課號（Set 內容）</li>
     *   <li>{@code occupied[]}：玩家已佔時段（Set 內容，編碼見 {@link TimeSlot}）</li>
     *   <li>{@code undoTop[]}：復原堆疊頂端最多 {@value #UNDO_PREVIEW} 筆，第 0 筆是下一個會被 pop 的</li>
     *   <li>{@code log[]}：操作紀錄，最新的在前</li>
     * </ul>
     *
     * <p>這個方法只讀取狀態，不改動任何資料結構。</p>
     *
     * @return JSON 字串
     */
    public String getStateJson() {
        JsonWriter w = new JsonWriter().beginObject()
                .field("day", day)
                .field("totalDays", TOTAL_DAYS)
                .field("finished", isFinished())
                .field("credits", player.getCredits())
                .field("maxCredits", Student.MAX_CREDITS)
                .field("minCredits", Student.MIN_CREDITS)
                .field("passed", isPassed())
                .field("missionMode", missionMode)
                .field("studentCount", students.size())
                .field("undoSize", undoStack.size())
                .field("lastOp", lastOp);

        w.beginArray("courses");
        for (Course c : courses.values()) {
            // 候補名單：複製成 List 只是走訪，不會出隊；順序 = 隊首到隊尾
            List<Student> queue = new ArrayList<Student>(c.getWaitlist());
            boolean taken = player.getCourseCodes().contains(c.getCode());
            w.beginObject()
                    .field("code", c.getCode())
                    .field("name", c.getName())
                    .field("teacher", c.getTeacher())
                    .field("credits", c.getCredits())
                    .field("capacity", c.getCapacity())
                    .field("enrolled", c.getEnrolledIds().size())
                    .field("waiting", queue.size())
                    .field("slotLabel", c.getSlotLabel())
                    .field("grade", c.getGrade())
                    .field("required", c.isRequired())
                    .field("taken", taken)
                    .field("myPosition", c.waitlistPosition(player))   // 0 表示沒排候補
                    .field("conflictWith", taken ? "" : findConflictCourse(c))
                    .field("sameSubjectWith", taken ? "" : codeOf(takenSection(player, c)));
            w.beginArray("slots");
            for (int slot : c.getSlots()) {
                w.value(slot);
            }
            w.endArray();
            w.beginArray("waitlist");
            for (Student s : queue) {
                w.value(s.getName());
            }
            w.endArray().endObject();
        }
        w.endArray();

        w.beginArray("myCourses");
        for (String code : player.getCourseCodes()) {
            w.value(code);
        }
        w.endArray();

        w.beginArray("occupied");
        for (int slot : player.getOccupiedSlots()) {
            w.value(slot);
        }
        w.endArray();

        w.beginArray("undoTop");
        // ArrayDeque 當堆疊時，走訪順序是從頂端往下；只取前幾筆，不 pop
        int shown = 0;
        for (Action a : undoStack) {
            if (shown++ == UNDO_PREVIEW) {
                break;
            }
            w.value(a.toString());
        }
        w.endArray();

        w.beginArray("log");
        for (int i = log.size() - 1; i >= 0; i--) {
            w.value(log.get(i));
        }
        w.endArray();

        return w.endObject().toString();
    }

    /**
     * 找出玩家已選的課中，第一門與指定課程衝堂的課號。給畫面標示「與某課衝堂」用。
     *
     * @param course 要檢查的課
     * @return 衝堂的課號；不衝堂時回傳空字串
     */
    private String findConflictCourse(Course course) {
        // 先用 Set 交集確認有沒有衝堂，有的話再找出是哪一門造成的
        if (player.findConflicts(course).isEmpty()) {
            return "";
        }
        for (String code : player.getCourseCodes()) {
            Set<Integer> overlap = new HashSet<Integer>(courses.get(code).getSlots());
            overlap.retainAll(course.getSlots());
            if (!overlap.isEmpty()) {
                return code;
            }
        }
        return "";
    }
}
