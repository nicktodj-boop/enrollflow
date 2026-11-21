package g9.enrollflow;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

/**
 * 一門課程：基本資料、已選學生與候補名單。
 *
 * <ul>
 *   <li>上課時段存在 {@code Set<Integer>}（編碼見 {@link TimeSlot}），衝堂檢查時與學生的已佔時段比對。</li>
 *   <li>已選學生存學號，用 {@code LinkedHashSet} 保留加選順序，也避免重複。</li>
 *   <li>候補名單是 {@code java.util.Queue}（用 {@link LinkedList} 實作），先排先補：
 *       排候補用 {@code offer()} 加到隊尾，遞補用 {@code poll()} 從隊首取出。</li>
 * </ul>
 */
public class Course {

    private final String code;
    private final String subject;
    private final String name;
    private final String teacher;
    private final int credits;
    private final int capacity;
    private final int grade;
    private final boolean required;
    private final Set<Integer> slots;

    /** 已選上的學生學號。 */
    private final Set<String> enrolledIds = new LinkedHashSet<String>();

    /**
     * 候補名單，隊首是下一個遞補的人。
     * 選 LinkedList 是因為取消候補要從中間刪除（{@code remove(Object)}），
     * 查順位要依序走訪，LinkedList 兩者都直接支援。
     */
    private final Queue<Student> waitlist = new LinkedList<Student>();

    /**
     * @param code     課號：淡江的「開課序號」，例如 "2210"，每個班別一個
     * @param subject  科目編號，例如 "B0302"；同一科目的 A、B、C 班相同，模擬同學用它找同科目的不同班
     * @param name     課名，同科目多班時後面加班別，例如「經濟學 A」
     * @param teacher  授課教師
     * @param credits  學分
     * @param capacity 名額（真實人數已等比例縮小，見 {@link CourseData}）
     * @param grade    開課年級（2 = 大二、3 = 大三）
     * @param required 是否為必修
     * @param slots    上課時段（含助教課），用 {@link TimeSlot#encode(int, int)} 產生
     */
    public Course(String code, String subject, String name, String teacher, int credits, int capacity,
                  int grade, boolean required, int... slots) {
        this.code = code;
        this.subject = subject;
        this.name = name;
        this.teacher = teacher;
        this.credits = credits;
        this.capacity = capacity;
        this.grade = grade;
        this.required = required;
        Set<Integer> slotSet = new LinkedHashSet<Integer>();
        for (int slot : slots) {
            slotSet.add(slot);
        }
        this.slots = Collections.unmodifiableSet(slotSet);
    }

    /** @return 是否已額滿 */
    public boolean isFull() {
        return enrolledIds.size() >= capacity;
    }

    /** @return 剩餘名額 */
    public int getSeatsLeft() {
        return capacity - enrolledIds.size();
    }

    public String getCode() {
        return code;
    }

    /** @return 科目編號（同科目不同班相同） */
    public String getSubject() {
        return subject;
    }

    /** @return 開課年級（2 = 大二、3 = 大三） */
    public int getGrade() {
        return grade;
    }

    /** @return 是否為必修 */
    public boolean isRequired() {
        return required;
    }

    public String getName() {
        return name;
    }

    public String getTeacher() {
        return teacher;
    }

    public int getCredits() {
        return credits;
    }

    public int getCapacity() {
        return capacity;
    }

    /** @return 上課時段（唯讀） */
    public Set<Integer> getSlots() {
        return slots;
    }

    /** @return 已選學生學號；由 {@link CourseSystem} 修改 */
    public Set<String> getEnrolledIds() {
        return enrolledIds;
    }

    /** @return 候補名單；由 {@link CourseSystem} 修改 */
    public Queue<Student> getWaitlist() {
        return waitlist;
    }

    /**
     * 查詢某位學生在候補名單的順位（「我是候補第幾位」）。
     * Queue 沒有直接查位置的方法，所以從隊首往後走訪計數，時間複雜度 O(n)。
     *
     * @param student 要找的學生（以學號比較，見 {@link Student#equals(Object)}）
     * @return 從 1 開始的順位；不在候補名單中回傳 0
     */
    public int waitlistPosition(Student student) {
        int position = 1;
        for (Student s : waitlist) {          // for-each 依序走訪，不會改變佇列內容
            if (s.equals(student)) {
                return position;
            }
            position++;
        }
        return 0;
    }

    /**
     * 時段的中文顯示，例如「二3 二4」。
     *
     * @return 以空白分隔的時段標籤
     */
    public String getSlotLabel() {
        StringBuilder sb = new StringBuilder();
        for (int slot : slots) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(TimeSlot.toLabel(slot));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return code + " " + name;
    }
}
