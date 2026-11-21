package g9.enrollflow;

/**
 * 程式進入點。IDE「Open Project → Run」執行的是這裡，啟動終端機版。
 * 網頁版不經過 Main，由 docs/app.js 直接建立 {@link CourseSystem}。
 */
public class Main {

    public static void main(String[] args) {
        new ConsoleUI(System.in, ConsoleUI.utf8Out()).run();
    }
}
