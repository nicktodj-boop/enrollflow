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

    /** 玩家的復原紀錄，頂端是最近一次操作。 */
    /**
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
     * 給網頁「資料結構透視面板」顯示；由各操作方法在動到資料結構時設定（TODO：B）。
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
        log.add("加退選開始，共 " + TOTAL_DAYS + " 天");
    }

    // ===================== 玩家操作（TODO：B） =====================

    /**
     * 加選。檢查順序：課號存在 → 未重複 → 不衝堂 → 不超學分 → 有名額。
     *
     * @param code 課號
     * @return JSON；額滿時 ok 為 false，並加上 {@code "canWait":true} 讓畫面詢問是否排候補
     */
    public String addCourse(String code) {
        // TODO(B): 成功時 push new Action(ActionType.ADD, code)
        return JsonWriter.result(false, "尚未實作：加選");
    }

    /**
     * 退選。退選後立刻對該課執行遞補（見 {@link #fillFromWaitlist(Course)}）。
     *
     * @param code 課號
     * @return JSON，msg 含遞補結果
     */
    public String dropCourse(String code) {
        // TODO(B)
        return JsonWriter.result(false, "尚未實作：退選");
    }

    /**
     * 排候補，加到該課候補名單隊尾。
     *
     * @param code 課號
     * @return JSON，加上 {@code "position"} 欄位表示目前順位
     */
    public String joinWaitlist(String code) {
        // TODO(B)
        return JsonWriter.result(false, "尚未實作：排候補");
    }

    /**
     * 取消候補。
     *
     * @param code 課號
     * @return JSON
     */
    public String leaveWaitlist(String code) {
        // TODO(B)
        return JsonWriter.result(false, "尚未實作：取消候補");
    }

    /**
     * 復原上一步：pop 復原堆疊頂端，依 {@link ActionType} 做反向操作（規則見設計檔第 3.5 節）。
     *
     * @return JSON；堆疊為空時 ok 為 false
     */
    public String undo() {
        if (undoStack.isEmpty()) {
            return JsonWriter.result(false, "沒有可以復原的操作");
        }
        // TODO(B)
        return JsonWriter.result(false, "尚未實作：復原");
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
        // TODO(B/D): 呼叫 simulator.simulateDay(this, simulated)，寫入 log，undoStack.clear()
        return JsonWriter.result(false, "尚未實作：下一天");
    }

    /**
     * 對一門有空位的課執行遞補：從候補隊首依序 poll()，跳過衝堂或超學分的人，直到補滿或候補清空。
     *
     * @param course 空出名額的課
     * @return 遞補過程的描述，每筆一行
     */
    List<String> fillFromWaitlist(Course course) {
        // TODO(B): 本作展示重點，排第一位不保證選上
        throw new UnsupportedOperationException("CourseSystem.fillFromWaitlist 尚未實作");
    }

    // ===================== 查詢 =====================

    /** @return 加退選期是否已結束 */
    public boolean isFinished() {
        return day > TOTAL_DAYS;
    }

    /** @return 目前第幾天 */
    public int getDay() {
        return day;
    }

    /**
     * 依關鍵字或星期篩選課程。
     *
     * @param keyword 課號或課名的一部分；空字串表示不限
     * @param weekday 星期 1–5；0 表示不限
     * @return JSON，{@code {"courses":[...]}}
     */
    public String searchJson(String keyword, int weekday) {
        // TODO(B): 走訪 courses.values()，符合條件的加入結果 List
        return JsonWriter.result(false, "尚未實作：搜尋");
    }

    /**
     * 回傳畫面需要的全部狀態，網頁每次操作後呼叫一次並整個重畫。
     *
     * <p>欄位一覽（網頁 docs/app.js 依這些名稱讀取）：</p>
     * <ul>
     *   <li>{@code day, totalDays, finished, credits, maxCredits, missionMode, undoSize, lastOp}</li>
     *   <li>{@code courses[]}：每門課的基本資料（含 {@code grade} 年級、{@code required} 必修）、名額、候補名單 {@code waitlist[]}（隊首在前），
     *       以及從玩家角度算出的 {@code taken}、{@code myPosition}、{@code conflictWith}</li>
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
                    .field("conflictWith", taken ? "" : findConflictCourse(c));
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
