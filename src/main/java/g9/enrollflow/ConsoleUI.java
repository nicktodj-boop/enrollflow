package g9.enrollflow;

import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Scanner;

/**
 * 最簡文字介面：開發時測試規則，也是「Open Project → Run」的保底執行方式。主要介面是網頁版。
 *
 * <p>只透過 {@link CourseSystem} 的 public 方法操作，與網頁版走同一套規則。
 * 輸入錯誤時提示並重新輸入，不讓例外中斷程式。</p>
 *
 * <p>負責：B 選課規則。</p>
 */
public class ConsoleUI {

    private final CourseSystem system = new CourseSystem();
    private final Scanner in;
    private final PrintStream out;

    /**
     * @param input  輸入來源（一般是 System.in）
     * @param output 輸出目的地（一般是 System.out）
     */
    public ConsoleUI(InputStream input, PrintStream output) {
        Scanner scanner;
        try {
            scanner = new Scanner(input, "UTF-8");
        } catch (IllegalArgumentException e) {
            scanner = new Scanner(input);
        }
        this.in = scanner;
        this.out = output;
    }

    /** 主迴圈：顯示狀態與選單，讀一個指令，執行，重複到使用者輸入 Q。 */
    public void run() {
        out.println("===== EnrollFlow：資料結構選課候補模擬器 =====");
        while (true) {
            printStatus();
            out.println(" C 課程列表  A 加選  D 退選  W 排候補  L 取消候補  U 復原  N 下一天  Q 離開");
            out.print("> ");
            if (!in.hasNextLine()) {
                return; // 輸入結束（例如管線輸入讀完）
            }
            String cmd = in.nextLine().trim().toUpperCase();
            if (cmd.isEmpty()) {
                continue;
            }
            switch (cmd.charAt(0)) {
                case 'C':
                    out.print("關鍵字（直接 Enter 顯示全部）> ");
                    out.print(system.getCourseListText(in.hasNextLine() ? in.nextLine() : ""));
                    break;
                case 'A':
                    show(system.addCourse(askCode()));
                    break;
                case 'D':
                    show(system.dropCourse(askCode()));
                    break;
                case 'W':
                    show(system.joinWaitlist(askCode()));
                    break;
                case 'L':
                    show(system.leaveWaitlist(askCode()));
                    break;
                case 'U':
                    show(system.undo());
                    break;
                case 'N':
                    show(system.nextDay());
                    break;
                case 'Q':
                    out.println("再見！");
                    return;
                default:
                    out.println("看不懂「" + cmd + "」，請輸入選單上的字母。");
            }
        }
    }

    /** 顯示天數、學分、已選課程與候補順位。 */
    private void printStatus() {
        out.println("------------------------------------------------");
        out.println(system.getStatusText());
    }

    /**
     * 印出操作結果。CourseSystem 回傳 JSON，這裡只取出 msg 欄位，前面標成功或失敗。
     *
     * @param json 例如 {"ok":true,"msg":"加選成功"}
     */
    private void show(String json) {
        String mark = json.startsWith("{\"ok\":true") ? "[OK] " : "[!!] ";
        out.println(mark + messageOf(json));
    }

    /**
     * 從 JSON 字串取出 msg 欄位並還原跳脫字元。格式固定由 {@link JsonWriter} 產生，所以不需要完整的 JSON 解析器。
     *
     * @param json JSON 字串
     * @return msg 的內容；找不到時回傳原字串
     */
    static String messageOf(String json) {
        String key = "\"msg\":\"";
        int start = json.indexOf(key);
        if (start < 0) {
            return json;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start + key.length(); i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                break;                                         // 未跳脫的引號就是字串結尾
            }
            if (c == '\\' && i + 1 < json.length()) {
                char n = json.charAt(++i);
                sb.append(n == 'n' ? '\n' : n);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 詢問課號，轉成大寫。 */
    private String askCode() {
        out.print("課號 > ");
        return in.hasNextLine() ? in.nextLine().trim().toUpperCase() : "";
    }

    /**
     * 建立使用 UTF-8 輸出的 PrintStream，降低 Windows 終端機中文亂碼的機率。
     *
     * @return UTF-8 的標準輸出；不支援時退回 System.out
     */
    static PrintStream utf8Out() {
        try {
            return new PrintStream(System.out, true, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return System.out;
        }
    }
}
