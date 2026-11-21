package g9.enrollflow;

/**
 * 手寫的 JSON 組字串工具。Java 8 沒有內建 JSON 函式庫，網頁版又只能接收 String，
 * 所以 Java 端把畫面狀態組成 JSON 字串，JS 端用 {@code JSON.parse} 解析。
 *
 * <p>用法（方法可串接）：</p>
 * <pre>
 * String json = new JsonWriter().beginObject()
 *         .field("ok", true)
 *         .field("msg", "加選成功")
 *         .endObject().toString();
 * </pre>
 *
 * <p>逗號由本類別自動處理：同一層第二個以後的元素前面會補逗號。</p>
 */
public class JsonWriter {

    private final StringBuilder sb = new StringBuilder();

    /** 目前這一層是否還沒有任何元素（用來決定要不要補逗號）。 */
    private boolean first = true;

    public JsonWriter beginObject() {
        comma();
        sb.append('{');
        first = true;
        return this;
    }

    public JsonWriter endObject() {
        sb.append('}');
        first = false;
        return this;
    }

    /**
     * 開始一個陣列欄位，之後用 {@link #value(String)}、{@link #value(int)} 或 {@link #beginObject()} 放元素。
     *
     * @param name 欄位名稱
     */
    public JsonWriter beginArray(String name) {
        key(name);
        sb.append('[');
        first = true;
        return this;
    }

    public JsonWriter endArray() {
        sb.append(']');
        first = false;
        return this;
    }

    /** 物件欄位：字串值。null 會輸出成 JSON 的 null。 */
    public JsonWriter field(String name, String value) {
        key(name);
        appendString(value);
        first = false;
        return this;
    }

    /** 物件欄位：整數值。 */
    public JsonWriter field(String name, int value) {
        key(name);
        sb.append(value);
        first = false;
        return this;
    }

    /** 物件欄位：布林值。 */
    public JsonWriter field(String name, boolean value) {
        key(name);
        sb.append(value);
        first = false;
        return this;
    }

    /** 陣列元素：字串。 */
    public JsonWriter value(String value) {
        comma();
        appendString(value);
        first = false;
        return this;
    }

    /** 陣列元素：整數。 */
    public JsonWriter value(int value) {
        comma();
        sb.append(value);
        first = false;
        return this;
    }

    /**
     * 產生只有 ok 與 msg 兩個欄位的回傳結果，給 CourseSystem 的操作方法共用。
     *
     * @param ok  操作是否成功
     * @param msg 顯示給玩家的訊息
     * @return JSON 字串，例如 {@code {"ok":false,"msg":"衝堂"}}
     */
    public static String result(boolean ok, String msg) {
        return new JsonWriter().beginObject().field("ok", ok).field("msg", msg).endObject().toString();
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    // ---- 內部工具 ----

    private void key(String name) {
        comma();
        appendString(name);
        sb.append(':');
    }

    private void comma() {
        if (!first) {
            sb.append(',');
        }
    }

    /** 加上引號並跳脫特殊字元；中文直接輸出，不轉 \\u 編碼。 */
    private void appendString(String s) {
        if (s == null) {
            sb.append("null");
            return;
        }
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}
