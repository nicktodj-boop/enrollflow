package g9.enrollflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link CourseSystem} 的選課規則測試，對應設計檔第 3.3–3.5 節與第 3.8 節的邊界情況清單。
 *
 * <p>每個測試都開一局固定種子的新局（第 1 天、還沒有人選課），
 * 再用模擬同學把課程填滿或排候補，排出想測的情境。</p>
 */
class CourseSystemTest {

    private CourseSystem cs;
    private Student me;
    /** 還沒用過的模擬同學，填名額時依序取用，每人只選一門，避免彼此衝堂。 */
    private List<Student> pool;

    @BeforeEach
    void setUp() {
        cs = new CourseSystem();
        cs.newGame(42, false);
        me = cs.getStudent("S000");
        pool = new ArrayList<Student>();
        for (Student s : cs.getStudents()) {
            if (!s.isPlayer()) {
                pool.add(s);
            }
        }
    }

    // ===================== 工具 =====================

    /** @return 回傳的 JSON 是否為成功 */
    private static boolean ok(String json) {
        return json.startsWith("{\"ok\":true");
    }

    /** 用模擬同學把課程填滿。 */
    private void fill(Course c) {
        while (!c.isFull()) {
            assertEquals(CourseSystem.Check.OK, cs.simulateAdd(pool.remove(0), c.getCode()));
        }
    }

    /** 找一門與 x 時段重疊、但不同科目的課。 */
    private Course conflictOf(Course x) {
        for (Course c : cs.getCourses()) {
            if (c.getSubject().equals(x.getSubject())) {
                continue;
            }
            Set<Integer> overlap = new HashSet<Integer>(c.getSlots());
            overlap.retainAll(x.getSlots());
            if (!overlap.isEmpty()) {
                return c;
            }
        }
        return null;
    }

    // ===================== 加選 =====================

    @Test
    @DisplayName("加選成功：課表、學分、復原堆疊都更新")
    void addSucceeds() {
        Course c = cs.getCourse("2210");
        assertTrue(ok(cs.addCourse("2210")));
        assertTrue(me.getCourseCodes().contains("2210"));
        assertTrue(me.getOccupiedSlots().containsAll(c.getSlots()));
        assertEquals(c.getCredits(), me.getCredits());
        assertTrue(c.getEnrolledIds().contains(me.getId()));
        assertTrue(cs.getStateJson().contains("\"undoSize\":1"));
        assertTrue(cs.getStateJson().contains("\"lastOp\":\"add()\""));
    }

    @Test
    @DisplayName("邊界 1：加選額滿的課，回傳 canWait 並顯示候補人數")
    void addFullCourseOffersWaitlist() {
        fill(cs.getCourse("2211"));
        String r = cs.addCourse("2211");
        assertFalse(ok(r));
        assertTrue(r.contains("\"canWait\":true"), r);
        assertTrue(r.contains("0 人候補"), r);
    }

    @Test
    @DisplayName("邊界 2：加選衝堂的課，指出跟哪一門、哪一節衝堂")
    void addConflictNamesCourseAndSlot() {
        Course x = cs.getCourse("2210");
        Course y = conflictOf(x);
        assertNotNull(y);
        cs.addCourse("2210");
        String r = cs.addCourse(y.getCode());
        assertFalse(ok(r));
        assertTrue(r.contains("2210") && r.contains("衝堂"), r);
        assertTrue(cs.getStateJson().contains("\"lastOp\":\"retainAll()\""));
    }

    @Test
    @DisplayName("邊界 3：加選後超過學分上限，拒絕並顯示學分")
    void addOverCreditLimit() {
        String last = "";
        for (Course c : cs.getCourses()) {
            last = cs.addCourse(c.getCode());
            if (last.contains("超過上限")) {
                break;
            }
        }
        assertTrue(last.contains("超過上限"), last);
        assertTrue(me.getCredits() <= Student.MAX_CREDITS);
    }

    @Test
    @DisplayName("邊界 4：重複加選、重複排候補")
    void duplicateAddAndJoin() {
        cs.addCourse("2210");
        assertTrue(cs.addCourse("2210").contains("已經選了"));

        fill(cs.getCourse("2216"));
        assertTrue(ok(cs.joinWaitlist("2216")));
        assertTrue(cs.joinWaitlist("2216").contains("已經在"));
    }

    @Test
    @DisplayName("同科目只能選一班：選了經濟學 A 就不能再選 B")
    void sameSubjectRejected() {
        cs.addCourse("2210");
        String r = cs.addCourse("2211");
        assertFalse(ok(r));
        assertTrue(r.contains("同科目"), r);
        assertTrue(cs.getStateJson().contains("\"sameSubjectWith\":\"2210\""));
    }

    @Test
    @DisplayName("課號不存在")
    void unknownCourse() {
        assertTrue(cs.addCourse("0000").contains("找不到"));
        assertTrue(cs.dropCourse("0000").contains("找不到"));
    }

    // ===================== 候補與遞補 =====================

    @Test
    @DisplayName("還有名額的課不能排候補")
    void cannotJoinOpenCourse() {
        assertTrue(cs.joinWaitlist("2212").contains("直接加選"));
    }

    @Test
    @DisplayName("排候補用 offer() 加到隊尾，取消候補後後面的人往前一位")
    void joinAndLeaveWaitlist() {
        Course c = cs.getCourse("2211");
        fill(c);
        String r = cs.joinWaitlist("2211");
        assertTrue(r.contains("\"position\":1"), r);
        Student other = pool.remove(0);
        assertTrue(cs.simulateJoin(other, "2211"));
        assertEquals(2, c.waitlistPosition(other));

        assertTrue(ok(cs.leaveWaitlist("2211")));
        assertEquals(0, c.waitlistPosition(me));
        assertEquals(1, c.waitlistPosition(other));
    }

    @Test
    @DisplayName("退選後立刻 poll() 遞補，隊首選上")
    void dropFillsFromHead() {
        Course c = cs.getCourse("2216");
        cs.addCourse("2216");
        fill(c);
        Student first = pool.remove(0);
        Student second = pool.remove(0);
        cs.simulateJoin(first, "2216");
        cs.simulateJoin(second, "2216");

        String r = cs.dropCourse("2216");
        assertTrue(r.contains(first.getName() + " 從候補遞補選上"), r);
        assertTrue(c.getEnrolledIds().contains(first.getId()));
        assertEquals(1, c.waitlistPosition(second));
        assertTrue(cs.getStateJson().contains("\"lastOp\":\"poll()\""));
    }

    @Test
    @DisplayName("邊界 5：候補第一位衝堂，跳過他，由第二位遞補")
    void fillSkipsConflictingHead() {
        Course q = cs.getCourse("2213");
        fill(q);
        Student a = pool.remove(0);
        Student b = pool.remove(0);
        cs.simulateJoin(a, "2213");
        cs.simulateJoin(b, "2213");
        // a 排候補之後又選了一門跟 2213 衝堂的課
        Course qc = conflictOf(q);
        assertEquals(CourseSystem.Check.OK, cs.simulateAdd(a, qc.getCode()));

        String dropper = q.getEnrolledIds().iterator().next();
        List<String> lines = cs.simulateDrop(cs.getStudent(dropper), "2213");
        assertEquals(3, lines.size(), lines.toString());
        assertTrue(lines.get(1).contains("衝堂") && lines.get(1).contains("自動移出"), lines.get(1));
        assertTrue(lines.get(2).contains(b.getName() + " 從候補遞補選上"), lines.get(2));
        assertFalse(a.getWaitingCodes().contains("2213"));
        assertTrue(q.getWaitlist().isEmpty());
    }

    @Test
    @DisplayName("候補第一位已選同科目別班，同樣被跳過")
    void fillSkipsSameSubjectHead() {
        Course a1 = cs.getCourse("2210");
        fill(a1);
        Student a = pool.remove(0);
        Student b = pool.remove(0);
        cs.simulateJoin(a, "2210");
        cs.simulateJoin(b, "2210");
        assertEquals(CourseSystem.Check.OK, cs.simulateAdd(a, "2211"));   // 改選經濟學 B

        String dropper = a1.getEnrolledIds().iterator().next();
        List<String> lines = cs.simulateDrop(cs.getStudent(dropper), "2210");
        assertTrue(lines.get(1).contains("已選同科目別班"), lines.toString());
        assertTrue(a1.getEnrolledIds().contains(b.getId()));
    }

    // ===================== 復原 =====================

    @Test
    @DisplayName("邊界 7：復原堆疊為空時按復原")
    void undoEmpty() {
        assertTrue(cs.undo().contains("沒有可以復原的操作"));
    }

    @Test
    @DisplayName("復原加選 = 退選")
    void undoAdd() {
        cs.addCourse("2210");
        assertTrue(ok(cs.undo()));
        assertTrue(me.getCourseCodes().isEmpty());
        assertEquals(0, me.getCredits());
        assertTrue(cs.getStateJson().contains("\"lastOp\":\"pop()\""));
    }

    @Test
    @DisplayName("復原退選：名額還在就重新選上")
    void undoDropReAdds() {
        cs.addCourse("2210");
        cs.dropCourse("2210");
        assertTrue(cs.undo().contains("重新選上"));
        assertTrue(me.getCourseCodes().contains("2210"));
    }

    @Test
    @DisplayName("邊界 6：復原退選，但名額已被遞補，改為排候補")
    void undoDropAfterFillJoinsWaitlist() {
        Course z = cs.getCourse("2216");
        cs.addCourse("2216");
        fill(z);
        cs.simulateJoin(pool.remove(0), "2216");
        cs.dropCourse("2216");                       // 名額立刻被候補的人補走

        String r = cs.undo();
        assertTrue(r.contains("改排候補"), r);
        assertEquals(1, z.waitlistPosition(me));
    }

    @Test
    @DisplayName("復原取消候補：只能排到隊尾，並告知原本的順位")
    void undoLeaveGoesToTail() {
        Course c = cs.getCourse("2211");
        fill(c);
        cs.joinWaitlist("2211");
        cs.simulateJoin(pool.remove(0), "2211");
        cs.leaveWaitlist("2211");

        String r = cs.undo();
        assertTrue(r.contains("原本第 1 位"), r);
        assertEquals(2, c.waitlistPosition(me));
    }

    @Test
    @DisplayName("復原最多記 10 步，按下一天後清空")
    void undoLimitAndNextDayClears() {
        for (int i = 0; i < 12; i++) {
            cs.addCourse("2210");
            cs.dropCourse("2210");
        }
        assertTrue(cs.getStateJson().contains("\"undoSize\":" + CourseSystem.UNDO_LIMIT));
        cs.nextDay();
        assertTrue(cs.getStateJson().contains("\"undoSize\":0"));
    }

    // ===================== 時間與查詢 =====================

    @Test
    @DisplayName("第 10 天之後結束，不能再操作")
    void finishedBlocksActions() {
        for (int i = 0; i < CourseSystem.TOTAL_DAYS; i++) {
            assertTrue(ok(cs.nextDay()));
        }
        assertTrue(cs.isFinished());
        assertTrue(cs.nextDay().contains("已結束"));
        assertTrue(cs.addCourse("2210").contains("已結束"));
        assertTrue(cs.undo().contains("已結束"));
        assertTrue(cs.getStateJson().contains("\"finished\":true"));
    }

    @Test
    @DisplayName("依關鍵字與星期搜尋")
    void search() {
        assertTrue(cs.search("經濟", 0).size() >= 3);
        assertTrue(cs.search("zzz", 0).isEmpty());
        for (Course c : cs.search("", 5)) {
            boolean onFriday = false;
            for (int slot : c.getSlots()) {
                onFriday |= TimeSlot.dayOf(slot) == 5;
            }
            assertTrue(onFriday, c.toString());
        }
        assertTrue(cs.searchJson("2210", 0).contains("\"code\":\"2210\""));
    }

    @Test
    @DisplayName("同一個種子，模擬結果完全相同")
    void sameSeedSameResult() {
        CourseSystem other = new CourseSystem();
        other.newGame(42, false);
        for (int i = 0; i < 5; i++) {
            cs.nextDay();
            other.nextDay();
        }
        assertEquals(cs.getStateJson(), other.getStateJson());
    }
}
