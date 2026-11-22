package g9.enrollflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 模擬同學的行動。每按一次「下一天」由 {@link CourseSystem#nextDay()} 呼叫。
 *
 * <p>規則（見 選課系統設計.md 第 3.6 節）：每位模擬同學有 30% 機率行動，
 * 志願清單裡還有沒選上的課就嘗試加選（額滿則排候補）；另有 10% 機率隨機退掉一門課。</p>
 *
 * <p>亂數使用固定種子時，每次結果相同，方便測試與錄影。</p>
 *
 * <p>負責：D 模擬與文件（12/14–12/20 完成）。</p>
 */
public class Simulator {

    /** 每天行動的機率。 */
    static final double ACT_CHANCE = 0.30;

    /** 每天隨機退選一門課的機率。 */
    static final double DROP_CHANCE = 0.10;

    /** 必修選自己班（A／B／C）的機率；其餘 20% 模擬重修、轉學生等跨班選課的人。 */
    static final double HOME_CLASS_CHANCE = 0.80;

    /** 每一科必修放進志願的機率；沒放的代表已經修過或延後修，讓必修班有滿有不滿。 */
    static final double REQUIRED_CHANCE = 0.75;

    /**
     * 排在候補的必修課，當天另外加選同科目其他有名額班級的機率。
     * 這種人仍留在原本的候補名單裡，輪到他時會因「已選同科目別班」被跳過，
     * 正是「排第一位不保證選上」的情境。
     */
    static final double SWITCH_SECTION_CHANCE = 0.40;

    /** 選修 1 門的機率；其餘的人選 2 門。 */
    static final double ONE_ELECTIVE_CHANCE = 0.50;

    /** 常見姓氏，與 {@link #GIVEN_CHARS} 組合出虛構姓名。 */
    private static final String[] SURNAMES = {
        "陳", "林", "黃", "張", "李", "王", "吳", "劉", "蔡", "楊",
        "許", "鄭", "謝", "郭", "洪", "曾", "邱", "廖", "賴", "周"
    };

    /** 名字常用字，兩兩組合成名。 */
    private static final String[] GIVEN_CHARS = {
        "家", "宇", "怡", "君", "志", "偉", "雅", "婷", "冠", "欣",
        "承", "翰", "佩", "珊", "柏", "宜", "蓁", "明", "哲", "品",
        "妤", "俊", "傑", "子", "芸", "宗", "庭", "詠", "晴", "昱"
    };

    private final Random random;

    /** 必修科目 → 該科目的各班（Map），在 {@link #assignWishlists} 建立，模擬改選別班時使用。 */
    private final Map<String, List<Course>> sectionsBySubject = new LinkedHashMap<String, List<Course>>();

    /**
     * @param seed 亂數種子；0 表示用目前時間（每次不同）
     */
    public Simulator(int seed) {
        this.random = seed == 0 ? new Random() : new Random(seed);
    }

    /**
     * 產生不重複的虛構姓名（姓氏＋兩個字的名）。
     *
     * @param count 要幾個
     * @return 姓名清單；用 LinkedHashSet 去除重複並保留產生順序
     */
    public List<String> generateNames(int count) {
        Set<String> names = new LinkedHashSet<String>();
        while (names.size() < count) {
            names.add(SURNAMES[random.nextInt(SURNAMES.length)]
                    + GIVEN_CHARS[random.nextInt(GIVEN_CHARS.length)]
                    + GIVEN_CHARS[random.nextInt(GIVEN_CHARS.length)]);
        }
        return new ArrayList<String>(names);
    }

    /**
     * 為模擬同學產生志願清單：自己年級的必修（每科一班）加上 1–2 門選修，共 2–6 門。
     *
     * <ol>
     *   <li>必修依科目編號分組（Map：科目 → 該科目的 A、B、C 班）。</li>
     *   <li>每一科有 {@value #REQUIRED_CHANCE} 的機率放進志願（其餘當作已修過或延後修）。</li>
     *   <li>每位同學有一個所屬班級，80% 的機率選自己班，其餘先試別班；
     *       不論哪一班，都只挑不會和志願裡其他課衝堂的。</li>
     *   <li>選修從所有選修課隨機挑 1–2 門不衝堂的。</li>
     *   <li>最後打亂順序：每天依志願順序加選，順序不同，搶到的課也不同。</li>
     * </ol>
     *
     * <p>最多是大三 4 科必修 11 學分＋2 門選修 4 學分 = 15 學分，在 16 學分上限內。</p>
     *
     * @param students 模擬同學
     * @param courses  全部課程
     */
    public void assignWishlists(List<Student> students, List<Course> courses) {
        // 必修依科目分組；LinkedHashMap 保留課程順序，同一個種子每次分組結果都一樣
        sectionsBySubject.clear();
        List<Course> electives = new ArrayList<Course>();
        for (Course c : courses) {
            if (!c.isRequired()) {
                electives.add(c);
                continue;
            }
            List<Course> sections = sectionsBySubject.get(c.getSubject());
            if (sections == null) {
                sections = new ArrayList<Course>();
                sectionsBySubject.put(c.getSubject(), sections);
            }
            sections.add(c);
        }

        for (Student s : students) {
            int homeClass = random.nextInt(3);                 // 0 = A 班、1 = B 班、2 = C 班
            List<String> wish = new ArrayList<String>();
            Set<Integer> planned = new HashSet<Integer>();     // 已排進志願的時段（Set），用來避開衝堂

            for (List<Course> sections : sectionsBySubject.values()) {
                if (sections.get(0).getGrade() != s.getGrade() || random.nextDouble() >= REQUIRED_CHANCE) {
                    continue;                                  // 只修自己年級的必修；部分科目這學期不修
                }
                // 候選順序：自己班優先；跨班的人則先試隨機一班，再退回自己班，最後其他班
                List<Course> order = new ArrayList<Course>();
                Course home = sections.get(Math.min(homeClass, sections.size() - 1));
                if (random.nextDouble() >= HOME_CLASS_CHANCE) {
                    order.add(sections.get(random.nextInt(sections.size())));
                }
                order.add(home);
                order.addAll(sections);
                for (Course c : order) {
                    if (!overlaps(c, planned)) {               // 第一個不衝堂的班就選它
                        wish.add(c.getCode());
                        planned.addAll(c.getSlots());
                        break;
                    }
                }
            }

            // 選修：1 或 2 門，每門最多試 10 次，找不衝堂、還沒選過的
            int electiveCount = random.nextDouble() < ONE_ELECTIVE_CHANCE ? 1 : 2;
            for (int n = 0; n < electiveCount; n++) {
                for (int tries = 0; tries < 10 && !electives.isEmpty(); tries++) {
                    Course e = electives.get(random.nextInt(electives.size()));
                    if (!wish.contains(e.getCode()) && !overlaps(e, planned)) {
                        wish.add(e.getCode());
                        planned.addAll(e.getSlots());
                        break;
                    }
                }
            }

            Collections.shuffle(wish, random);
            s.getWishlist().clear();
            s.getWishlist().addAll(wish);
        }
    }

    /**
     * 判斷課程時段與一組時段有沒有重疊（集合交集不為空）。
     *
     * @param course 課程
     * @param slots  已佔用的時段
     * @return 有重疊時回傳 true
     */
    private static boolean overlaps(Course course, Set<Integer> slots) {
        Set<Integer> overlap = new HashSet<Integer>(course.getSlots());
        overlap.retainAll(slots);
        return !overlap.isEmpty();
    }

    /**
     * 讓所有模擬同學行動一天。實際的加退選透過 system 的方法進行，規則只寫在 CourseSystem 一處。
     *
     * <ol>
     *   <li>先把同學順序打亂（每天誰先搶不一樣），再一個一個處理。</li>
     *   <li>每人有 {@value #ACT_CHANCE} 的機率行動：依志願順序，還沒選上、也沒在候補的課就加選；
     *       額滿就排候補。衝堂或超學分的跳過。</li>
     *   <li>正在候補的必修課，有 {@value #SWITCH_SECTION_CHANCE} 的機率改加選同科目其他有名額的班，
     *       但不取消原本的候補（輪到時會被跳過）。</li>
     *   <li>行動的人另有 {@value #DROP_CHANCE} 的機率隨機退掉一門已選的課，並把它從志願刪掉（放棄這門課）。
     *       退選會立刻觸發遞補，可能補到玩家。</li>
     * </ol>
     *
     * @param system   選課系統
     * @param students 模擬同學
     * @return 第一行是當天統計，例如「同學加選 12 次、排候補 5 次、退選 1 次」；之後每行是一筆退選與遞補
     */
    public List<String> simulateDay(CourseSystem system, List<Student> students) {
        List<Student> order = new ArrayList<Student>(students);
        Collections.shuffle(order, random);

        int adds = 0;
        int waits = 0;
        int drops = 0;
        List<String> events = new ArrayList<String>();

        for (Student s : order) {
            if (random.nextDouble() >= ACT_CHANCE) {
                continue;                                      // 今天沒上選課系統
            }
            // 走訪志願清單的複本：迴圈中不改動原本的 List
            for (String code : new ArrayList<String>(s.getWishlist())) {
                if (s.getCourseCodes().contains(code) || s.getWaitingCodes().contains(code)) {
                    continue;                                  // 已選上或已在排隊
                }
                CourseSystem.Check result = system.simulateAdd(s, code);
                if (result == CourseSystem.Check.OK) {
                    adds++;
                } else if (result == CourseSystem.Check.FULL && system.simulateJoin(s, code)) {
                    waits++;
                }
            }
            // 等不及的人：候補中的必修課，改加選同科目其他還有名額的班，但不取消原本的候補
            for (String code : new ArrayList<String>(s.getWaitingCodes())) {
                Course waited = system.getCourse(code);
                List<Course> sections = sectionsBySubject.get(waited.getSubject());
                if (sections == null || random.nextDouble() >= SWITCH_SECTION_CHANCE) {
                    continue;
                }
                for (Course other : sections) {
                    if (other != waited && system.simulateAdd(s, other.getCode()) == CourseSystem.Check.OK) {
                        adds++;
                        break;
                    }
                }
            }
            if (!s.getCourseCodes().isEmpty() && random.nextDouble() < DROP_CHANCE) {
                List<String> taken = new ArrayList<String>(s.getCourseCodes());
                String code = taken.get(random.nextInt(taken.size()));
                s.getWishlist().remove(code);                  // List.remove(Object)：放棄後不再加選
                events.addAll(system.simulateDrop(s, code));
                drops++;
            }
        }

        List<String> report = new ArrayList<String>();
        report.add("同學加選 " + adds + " 次、排候補 " + waits + " 次、退選 " + drops + " 次");
        report.addAll(events);
        return report;
    }

    /** @return 共用的亂數產生器 */
    Random getRandom() {
        return random;
    }
}
