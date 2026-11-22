package g9.enrollflow;

/**
 * 一筆玩家操作紀錄，push 到復原堆疊 ({@code Deque<Action>})。
 *
 * <p>只記「做了什麼」和「哪一門課」，反向操作由 {@link CourseSystem#undo()} 依種類決定。
 * 取消候補時另外記下原本的順位，復原時告訴玩家「原本第幾位，現在只能排到隊尾」。
 * 建立後內容不會再改變。</p>
 */
public final class Action {

    private final ActionType type;
    private final String courseCode;
    private final int position;

    /**
     * @param type       操作種類
     * @param courseCode 操作的課號
     */
    public Action(ActionType type, String courseCode) {
        this(type, courseCode, 0);
    }

    /**
     * @param type       操作種類
     * @param courseCode 操作的課號
     * @param position   取消候補前的順位（從 1 開始）；其他操作填 0
     */
    public Action(ActionType type, String courseCode, int position) {
        this.type = type;
        this.courseCode = courseCode;
        this.position = position;
    }

    /** @return 操作種類 */
    public ActionType getType() {
        return type;
    }

    /** @return 操作的課號 */
    public String getCourseCode() {
        return courseCode;
    }

    /** @return 取消候補前的順位；其他操作為 0 */
    public int getPosition() {
        return position;
    }

    @Override
    public String toString() {
        return type + " " + courseCode;
    }
}
