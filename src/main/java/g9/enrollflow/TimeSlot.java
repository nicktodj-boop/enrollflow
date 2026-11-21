package g9.enrollflow;

/**
 * 上課時段的編碼工具（只有靜態方法）。
 *
 * <p>時段用一個整數表示：{@code 星期 × 100 + 節次}。例如星期三第 4 節是 304。
 * 每位學生的已佔用時段放在 {@code HashSet<Integer>}，衝堂檢查就是看兩個集合有沒有共同元素。</p>
 */
public final class TimeSlot {

    /** 一週上課天數（星期一到五）。 */
    public static final int DAYS = 5;

    /**
     * 每天節數（第 1–10 節）。淡江的節次排到第 14 節（第 11–14 節是晚上），
     * 本作收錄的資管系大二、大三課程最晚到第 10 節，所以只做到第 10 節。
     */
    public static final int PERIODS = 10;

    private static final String[] DAY_NAMES = {"一", "二", "三", "四", "五"};

    private TimeSlot() {
        // 工具類別，不建立物件
    }

    /**
     * 把星期與節次編成一個整數。
     *
     * @param day    星期，1–5
     * @param period 節次，1–10
     * @return 編碼後的時段，例如 (3, 4) 回傳 304
     * @throws IllegalArgumentException 星期或節次超出範圍時
     */
    public static int encode(int day, int period) {
        if (day < 1 || day > DAYS || period < 1 || period > PERIODS) {
            throw new IllegalArgumentException("時段超出範圍：星期 " + day + "，第 " + period + " 節");
        }
        return day * 100 + period;
    }

    /** @return 時段的星期（1–5） */
    public static int dayOf(int slot) {
        return slot / 100;
    }

    /** @return 時段的節次（1–10） */
    public static int periodOf(int slot) {
        return slot % 100;
    }

    /**
     * 轉成畫面顯示用的中文，例如 304 → 「三4」。
     *
     * @param slot 編碼後的時段
     * @return 中文標籤
     */
    public static String toLabel(int slot) {
        return DAY_NAMES[dayOf(slot) - 1] + periodOf(slot);
    }
}
