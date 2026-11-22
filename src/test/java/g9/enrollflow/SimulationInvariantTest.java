package g9.enrollflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 隨機整局測試：玩家亂按、同學照規則行動，每一步之後檢查資料結構之間沒有對不起來的地方。
 *
 * <p>單一情境的測試只能證明「想得到的情況」正確；這裡用 300 個種子各跑完整 10 天，
 * 找出想不到的操作順序造成的錯誤（例如復原與遞補交錯）。</p>
 */
class SimulationInvariantTest {

    private static final int GAMES = 300;

    @Test
    @DisplayName("300 局隨機操作，每一步都維持不變量")
    void randomGamesKeepInvariants() {
        int fills = 0;
        int skipped = 0;
        for (int seed = 1; seed <= GAMES; seed++) {
            CourseSystem cs = new CourseSystem();
            cs.newGame(seed, false);
            Random rnd = new Random(seed * 31L);
            List<Course> all = cs.getCourses();
            while (!cs.isFinished()) {
                for (int k = 0; k < 4; k++) {
                    String code = all.get(rnd.nextInt(all.size())).getCode();
                    int op = rnd.nextInt(6);
                    String r;
                    switch (op) {
                        case 0:
                        case 5:
                            r = cs.addCourse(code);
                            break;
                        case 1:
                            r = cs.dropCourse(code);
                            break;
                        case 2:
                            r = cs.joinWaitlist(code);
                            break;
                        case 3:
                            r = cs.leaveWaitlist(code);
                            break;
                        default:
                            r = cs.undo();
                    }
                    assertInvariants(cs, "seed " + seed + " day " + cs.getDay() + " op " + op + " " + code + " → " + r);
                }
                cs.nextDay();
                assertInvariants(cs, "seed " + seed + " nextDay");
            }
            String state = cs.getStateJson();
            fills += count(state, "遞補選上");
            skipped += count(state, "自動移出");
        }
        // 模擬要真的動起來：遞補與「隊首被跳過」都要出現，簡報才有東西可以展示
        assertTrue(fills > GAMES, "遞補次數太少：" + fills);
        assertTrue(skipped > GAMES / 2, "隊首被跳過的次數太少：" + skipped);
    }

    @Test
    @DisplayName("模擬同學的志願清單不衝堂、不超過學分上限")
    void wishlistsAreValid() {
        for (int seed = 1; seed <= 50; seed++) {
            CourseSystem cs = new CourseSystem();
            cs.newGame(seed, false);
            for (Student s : cs.getStudents()) {
                if (s.isPlayer()) {
                    continue;
                }
                Set<Integer> slots = new HashSet<Integer>();
                Set<String> subjects = new HashSet<String>();
                int credits = 0;
                for (String code : s.getWishlist()) {
                    Course c = cs.getCourse(code);
                    credits += c.getCredits();
                    assertTrue(subjects.add(c.getSubject()), "同科目重複：" + s + " " + s.getWishlist());
                    for (int slot : c.getSlots()) {
                        assertTrue(slots.add(slot), "志願衝堂：" + s + " " + s.getWishlist());
                    }
                }
                assertTrue(credits <= Student.MAX_CREDITS, s + " " + credits);
                assertFalse(s.getWishlist().isEmpty(), s.toString());
            }
        }
    }

    /**
     * 不變量：不管操作順序如何都必須成立的條件。
     *
     * @param cs  選課系統
     * @param ctx 失敗時印出的情境
     */
    private static void assertInvariants(CourseSystem cs, String ctx) {
        for (Course c : cs.getCourses()) {
            check(c.getEnrolledIds().size() <= c.getCapacity(), "超過名額 " + c, ctx);
            // 遞補一定會補到滿或清空候補，所以「有人候補卻還有名額」代表漏了遞補
            check(c.getWaitlist().isEmpty() || c.isFull(), "有候補卻沒額滿 " + c, ctx);
            Set<Student> seen = new HashSet<Student>();
            for (Student s : c.getWaitlist()) {
                check(seen.add(s), "候補名單重複 " + s, ctx);
                check(s.getWaitingCodes().contains(c.getCode()), "Queue 與 waitingCodes 不一致 " + s, ctx);
                check(!s.getCourseCodes().contains(c.getCode()), "已選上又在候補 " + s, ctx);
            }
            for (String id : c.getEnrolledIds()) {
                check(cs.getStudent(id).getCourseCodes().contains(c.getCode()), "課程與學生的已選不一致 " + id, ctx);
            }
        }
        for (Student s : cs.getStudents()) {
            int credits = 0;
            Set<Integer> slots = new HashSet<Integer>();
            Set<String> subjects = new HashSet<String>();
            for (String code : s.getCourseCodes()) {
                Course c = cs.getCourse(code);
                credits += c.getCredits();
                check(subjects.add(c.getSubject()), "選了同科目兩班 " + s, ctx);
                for (int slot : c.getSlots()) {
                    check(slots.add(slot), "衝堂 " + s, ctx);
                }
                check(c.getEnrolledIds().contains(s.getId()), "學生與課程的已選不一致 " + s, ctx);
            }
            check(slots.equals(s.getOccupiedSlots()), "已佔時段不一致 " + s, ctx);
            assertEquals(credits, s.getCredits(), "學分不一致 " + s + " :: " + ctx);
            check(credits <= Student.MAX_CREDITS, "超過學分 " + s, ctx);
            for (String code : s.getWaitingCodes()) {
                check(cs.getCourse(code).waitlistPosition(s) > 0, "waitingCodes 有、Queue 沒有 " + s, ctx);
            }
        }
    }

    private static void check(boolean condition, String what, String ctx) {
        if (!condition) {
            fail(what + " :: " + ctx);
        }
    }

    private static int count(String s, String sub) {
        int n = 0;
        for (int i = s.indexOf(sub); i >= 0; i = s.indexOf(sub, i + sub.length())) {
            n++;
        }
        return n;
    }
}
