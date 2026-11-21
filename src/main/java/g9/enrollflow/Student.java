package g9.enrollflow;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 一位學生：玩家本人或電腦模擬的同學。
 *
 * <ul>
 *   <li>已選課號：{@code Set<String>}，避免重複加選。</li>
 *   <li>已佔時段：{@code Set<Integer>}，加選時與課程時段比對有沒有交集（衝堂）。</li>
 *   <li>已排候補的課號：{@code Set<String>}，避免同一門課重複排候補。</li>
 *   <li>志願清單：{@code List<String>}，模擬同學每天依序嘗試加選。</li>
 * </ul>
 */
public class Student {

    /** 每人學分上限。 */
    public static final int MAX_CREDITS = 16;

    private final String id;
    private final String name;
    private final boolean player;

    /** 年級（2 = 大二、3 = 大三），模擬同學依年級挑必修課。 */
    private final int grade;

    private final Set<String> courseCodes = new LinkedHashSet<String>();
    private final Set<Integer> occupiedSlots = new LinkedHashSet<Integer>();
    private final Set<String> waitingCodes = new LinkedHashSet<String>();
    private final List<String> wishlist = new ArrayList<String>();
    private int credits;

    /**
     * @param id     學號
     * @param name   顯示名稱
     * @param player 是否為玩家本人
     * @param grade  年級（2 或 3）
     */
    public Student(String id, String name, boolean player, int grade) {
        this.id = id;
        this.name = name;
        this.grade = grade;
        this.player = player;
    }

    /**
     * 檢查這門課是否和已選的課衝堂。
     *
     * @param course 要檢查的課
     * @return 衝堂的時段（可能多個）；沒有衝堂時回傳空集合
     */
    public Set<Integer> findConflicts(Course course) {
        // 複製一份課程時段，再用 retainAll 留下與已佔時段的交集（Set 的交集運算），不改動原本的集合
        Set<Integer> overlap = new LinkedHashSet<Integer>(course.getSlots());
        overlap.retainAll(occupiedSlots);
        return overlap;
    }

    /**
     * 加入一門課後學分是否會超過上限。
     *
     * @param course 要加入的課
     * @return 會超過時回傳 true
     */
    public boolean wouldExceedCredits(Course course) {
        return credits + course.getCredits() > MAX_CREDITS;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /** @return 年級（2 = 大二、3 = 大三） */
    public int getGrade() {
        return grade;
    }

    /** @return 是否為玩家本人 */
    public boolean isPlayer() {
        return player;
    }

    /** @return 已選課號；由 {@link CourseSystem} 修改 */
    public Set<String> getCourseCodes() {
        return courseCodes;
    }

    /** @return 已佔用時段；由 {@link CourseSystem} 修改 */
    public Set<Integer> getOccupiedSlots() {
        return occupiedSlots;
    }

    /** @return 已排候補的課號；由 {@link CourseSystem} 修改 */
    public Set<String> getWaitingCodes() {
        return waitingCodes;
    }

    /** @return 志願清單（模擬同學用） */
    public List<String> getWishlist() {
        return wishlist;
    }

    /** @return 目前學分 */
    public int getCredits() {
        return credits;
    }

    /** @param credits 新的學分數；由 {@link CourseSystem} 在加退選時更新 */
    void setCredits(int credits) {
        this.credits = credits;
    }

    // 以學號判斷是否同一人；候補名單的 remove(Object)、waitlistPosition 都靠 equals 比對
    @Override
    public boolean equals(Object o) {
        return o instanceof Student && ((Student) o).id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
