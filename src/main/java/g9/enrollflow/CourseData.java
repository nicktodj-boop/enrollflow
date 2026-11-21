package g9.enrollflow;

import java.util.ArrayList;
import java.util.List;

/**
 * 寫死在程式裡的課程資料。CheerpJ 環境下讀檔要另外處理，所以不從檔案載入。
 *
 * <p>資料來源：淡江大學課程查詢系統，資管系（日）115 學年度第 1 學期，大二與大三的課程
 * （2026-11-21 抄錄）。每個班別（A、B、C、P）各算一門課。</p>
 *
 * <p>抄錄規則：</p>
 * <ul>
 *   <li>課號用「開課序號」；同一科目有多個班時，課名後面加班別。</li>
 *   <li>課表下方標「助教」的時段要隨班上課，所以併入該門課的上課時段，衝堂檢查也會算進去。</li>
 *   <li>大三「專題製作」（2238–2240）沒有固定上課時間，排課也不會衝堂，所以不收錄。</li>
 *   <li>真實名額 50–110 人，本系統只有 61 位學生，照原樣不會額滿；
 *       依 {@link #seats(int)} 等比例縮成 5–8 人，熱門大班仍然比較大。</li>
 * </ul>
 *
 * <p>負責：D 模擬與文件。</p>
 */
public final class CourseData {

    /** 星期的中文，索引 0–4 對應星期一到五；{@link #at(String)} 用來把「三」轉成 3。 */
    private static final String WEEKDAYS = "一二三四五";

    private CourseData() {
        // 工具類別，不建立物件
    }

    /**
     * 建立一份新的課程清單。每次開新局都重新建立，避免上一局的選課狀態殘留。
     *
     * @return 課程清單（依開課序號排列）
     */
    public static List<Course> createCourses() {
        List<Course> list = new ArrayList<Course>();
        // 欄位：開課序號, 科目編號, 課名, 教師, 學分, 真實名額, 年級, 必修, 上課時段（含助教課）

        // ===== 大二 必修 =====
        add(list, "2210", "B0302", "經濟學 A", "林士全", 2, 75, 2, true, "三1,2 一2");
        add(list, "2211", "B0302", "經濟學 B", "林士全", 2, 75, 2, true, "三3,4 二8");
        add(list, "2212", "B0302", "經濟學 C", "陳炤良", 2, 75, 2, true, "四1,2 三1");
        add(list, "2213", "E0175", "作業系統 A", "周清江", 2, 75, 2, true, "一3,4 二9");
        add(list, "2214", "E0175", "作業系統 B", "周清江", 2, 75, 2, true, "一8,9 二4");
        add(list, "2215", "E0175", "作業系統 C", "魏世杰", 2, 75, 2, true, "三8,9 三2");
        add(list, "2216", "M0490", "網路與通訊 A", "鄭培宇", 2, 80, 2, true, "一6,7 一8");
        add(list, "2217", "M0490", "網路與通訊 B", "鄭培宇", 2, 80, 2, true, "四3,4 四2");
        add(list, "2218", "M0490", "網路與通訊 C", "鄭培宇", 2, 80, 2, true, "三3,4 三5");
        add(list, "2219", "M1326", "程式設計與資料結構 A", "張昭憲", 3, 70, 2, true, "三3,4 五5 一10");
        add(list, "2220", "M1326", "程式設計與資料結構 B", "魏世杰", 3, 70, 2, true, "二6,7 二9 二10");
        add(list, "2221", "M1326", "程式設計與資料結構 C", "鄭元成", 3, 70, 2, true, "四8 五3,4 一4");
        // ===== 大二 選修 =====
        add(list, "2222", "M1606", "企業電子化", "朱志平", 2, 50, 2, false, "五6,7");
        add(list, "2223", "S0252", "基礎數學", "廖賀田", 2, 110, 2, false, "五8,9");

        // ===== 大三 必修 =====
        add(list, "2224", "A2087", "進階程式設計 A", "鄭元成", 2, 70, 3, true, "五6,7 五8");
        add(list, "2225", "A2087", "進階程式設計 B", "林仁祥", 2, 70, 3, true, "五6,7 三2");
        add(list, "2226", "A2087", "進階程式設計 C", "林仁祥", 2, 70, 3, true, "三9,10 五2");
        add(list, "2227", "E1827", "網路程式設計 A", "徐煥智", 3, 70, 3, true, "二3,4 三8 五2");
        add(list, "2228", "E1827", "網路程式設計 B", "徐煥智", 3, 70, 3, true, "二8 四3,4 五1");
        add(list, "2229", "E1827", "網路程式設計 C", "陳有科", 3, 70, 3, true, "二9,10 四10 二5");
        add(list, "2230", "M0171", "系統分析與設計 A", "徐煥智", 3, 75, 3, true, "二6 三6,7 三9");
        add(list, "2231", "M0171", "系統分析與設計 B", "游佳萍", 3, 75, 3, true, "二3,4 三1 四10");
        add(list, "2232", "M0171", "系統分析與設計 C", "游佳萍", 3, 75, 3, true, "三2,3,4 三1");
        add(list, "2233", "M0271", "財務管理 A", "張應華", 3, 75, 3, true, "三1,2 三5");
        add(list, "2234", "M0271", "財務管理 B", "張應華", 3, 75, 3, true, "二6,7 三3");
        add(list, "2235", "M0271", "財務管理 C", "鄭元成", 3, 75, 3, true, "二3,4 二7");
        // ===== 大三 選修 =====
        add(list, "2236", "M0504", "組織管理", "廖崇硯", 2, 110, 3, false, "四6,7");
        add(list, "2237", "M1894", "數位化公司管理", "傅大煜", 2, 110, 3, false, "一3,4");
        add(list, "2241", "M2235", "專業排版與設計", "林慶昌", 2, 70, 3, false, "五9,10");
        add(list, "2242", "M2236", "簡報設計技巧", "彭宗仁", 2, 70, 3, false, "二1,2");
        add(list, "2243", "M2314", "網路規劃與實作", "魏世杰", 2, 70, 3, false, "一6,7");
        add(list, "2244", "M2688", "人工智慧創新與應用", "傅大煜", 2, 70, 3, false, "四1,2");
        add(list, "2245", "M2772", "Web3的應用與實務", "李正冬", 2, 110, 3, false, "五3,4");
        add(list, "2246", "V0024", "LINUX作業系統", "陳孟勛", 2, 70, 3, false, "四8,9");
        return list;
    }

    /** 建立一門課並加入清單；名額先經 {@link #seats(int)} 縮小，時段由 {@link #at(String)} 解析。 */
    private static void add(List<Course> list, String code, String subject, String name, String teacher,
                            int credits, int realCapacity, int grade, boolean required, String time) {
        list.add(new Course(code, subject, name, teacher, credits, seats(realCapacity),
                grade, required, at(time)));
    }

    /**
     * 把真實名額等比例縮小成本系統用的名額。
     *
     * <p>50 人以下 → 5、51–75 人 → 6、76–80 人 → 7、80 人以上 → 8。
     * 60 位模擬同學分到 34 門課，這個大小會讓必修班額滿、出現候補，選修則有滿有不滿。</p>
     *
     * @param realCapacity 課程查詢系統上的「人數設限」
     * @return 縮小後的名額
     */
    static int seats(int realCapacity) {
        if (realCapacity <= 50) {
            return 5;
        } else if (realCapacity <= 75) {
            return 6;
        } else if (realCapacity <= 80) {
            return 7;
        }
        return 8;
    }

    /**
     * 把淡江課表的時間寫法轉成時段編碼。
     *
     * <p>格式：「星期＋節次」，同一天多節用逗號，不同天用空白隔開。
     * 例如 {@code "三3,4 五5"} → 星期三第 3、4 節與星期五第 5 節 → {303, 304, 505}。</p>
     *
     * @param time 時間字串
     * @return 時段編碼陣列（編碼方式見 {@link TimeSlot}）
     * @throws IllegalArgumentException 星期寫錯或節次超出範圍時
     */
    static int[] at(String time) {
        List<Integer> slots = new ArrayList<Integer>();
        for (String part : time.trim().split("\\s+")) {
            int day = WEEKDAYS.indexOf(part.charAt(0)) + 1;
            if (day == 0) {
                throw new IllegalArgumentException("看不懂星期：" + part);
            }
            for (String period : part.substring(1).split(",")) {
                slots.add(TimeSlot.encode(day, Integer.parseInt(period)));
            }
        }
        int[] result = new int[slots.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = slots.get(i);
        }
        return result;
    }
}
