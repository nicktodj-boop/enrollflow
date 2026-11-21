package g9.enrollflow;

/**
 * 一筆玩家操作紀錄，push 到復原堆疊 ({@code Deque<Action>})。
 *
 * <p>只記「做了什麼」和「哪一門課」，反向操作由 {@link CourseSystem#undo()} 依種類決定。
 * 建立後內容不會再改變。</p>
 */
public final class Action {

    private final ActionType type;
    private final String courseCode;

    /**
     * @param type       操作種類
     * @param courseCode 操作的課號
     */
    public Action(ActionType type, String courseCode) {
        this.type = type;
        this.courseCode = courseCode;
    }

    /** @return 操作種類 */
    public ActionType getType() {
        return type;
    }

    /** @return 操作的課號 */
    public String getCourseCode() {
        return courseCode;
    }

    @Override
    public String toString() {
        return type + " " + courseCode;
    }
}
