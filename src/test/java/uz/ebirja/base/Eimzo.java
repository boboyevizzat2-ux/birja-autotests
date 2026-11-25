package uz.ebirja.base;

import uz.ebirja.config.Config;

import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * E-IMZO dasturi parolni brauzerdan tashqarida, o'zining oynasida so'raydi.
 * Selenium unga yeta olmaydi, shuning uchun parol klaviatura orqali (java.awt.Robot) yoziladi.
 * Parol FAQAT E-IMZO oynasi ochilganda yoziladi (aks holda Enter brauzerga tushib, forma qayta yuborilishi mumkin).
 * Shart: brauzer ekranda bo'lishi va bu vaqtda sichqoncha/klaviaturaga tegilmasligi kerak.
 */
public final class Eimzo {
    private Eimzo() {}

    /** E-IMZO jarayonining oyna sarlavhasi (oyna yo'q bo'lsa bo'sh qator). */
    public static String windowTitle() {
        return runScript(false);
    }

    /** E-IMZO oynasini oldinga chiqaradi (fokus beradi) va sarlavhasini qaytaradi. */
    static String focusWindow() {
        return runScript(true);
    }

    // ---- doimiy kuzatuvchi jarayon (har tekshiruvda PowerShell ishga tushirmaslik uchun: ~1-2 s -> ~50 ms)
    private static Process srv;
    private static java.io.BufferedWriter srvIn;
    private static BufferedReader srvOut;

    private static synchronized String srvCmd(String cmd) {
        try {
            if (srv == null || !srv.isAlive()) {
                String script = Config.resurs("eimzo-server.ps1").getAbsolutePath();
                srv = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script).redirectErrorStream(true).start();
                srvIn = new java.io.BufferedWriter(new java.io.OutputStreamWriter(srv.getOutputStream()));
                srvOut = new BufferedReader(new InputStreamReader(srv.getInputStream()));
                Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { srv.destroy(); } catch (Exception ignore) {} }));
            }
            srvIn.write(cmd);
            srvIn.newLine();
            srvIn.flush();
            String line = srvOut.readLine();
            return line == null ? null : line.trim();
        } catch (Exception e) {
            System.out.println("E-IMZO kuzatuvchisi xatosi (" + cmd + "): " + e);
            try { if (srv != null) srv.destroy(); } catch (Exception ignore) {}
            srv = null;
            return null;
        }
    }

    /** Hozir oldinda turgan oyna E-IMZO nikimi. */
    static boolean oldinda() { return "1".equals(srvCmd("fg")); }

    /** E-IMZO oynasini oldinga chiqaradi; 3 soniyada chiqmasa xato beradi (parol begona oynaga yozilmasin). */
    private static void oldingaChiqar(Robot robot) {
        for (int i = 0; i < 10; i++) {
            focusWindow();
            robot.delay(300);
            if (oldinda()) return;
        }
        throw new IllegalStateException("E-IMZO oynasini oldinga chiqarib bo'lmadi, parol yozilmadi");
    }

    /** Clipboarddagi parolni o'chiradi. */
    private static void tozala() {
        try { java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(""), null); } catch (Exception e) { System.out.println("DIQQAT: clipboard tozalanmadi: " + e); }
    }

    /** E-IMZO oynasini parolsiz yopadi (WM_CLOSE). */
    static String closeWindow() {
        String r = srvCmd("close");
        return r != null ? r : "";
    }

    private static String runScript(boolean focus) {
        String fast = srvCmd(focus ? "focus" : "title");
        if (fast != null) return fast;
        try {
            String script = Config.resurs("eimzo-window.ps1").getAbsolutePath();
            ProcessBuilder pb = focus
                    ? new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script, "focus")
                    : new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script);
            Process pr = pb.redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(pr.getInputStream()))) {
                String line = r.readLine();
                pr.waitFor();
                return line == null ? "" : line.trim();
            }
        } catch (Exception e) {
            System.out.println("E-IMZO oynasini tekshirib bo'lmadi: " + e);
            return "";
        }
    }

    /**
     * E-IMZO parol oynasi chiqishini kutadi (ko'pi bilan timeoutSec soniya), parolni yozib Enter bosadi.
     * @return true - parol kiritildi; false - oyna chiqmadi (imzo so'ralmadi)
     */
    public static boolean typePassword(int timeoutSec) {
        long end = System.currentTimeMillis() + timeoutSec * 1000L;
        String title = "";
        while (System.currentTimeMillis() < end) {
            title = windowTitle();
            if (!title.isEmpty()) break;
            sleep(200);
        }
        if (title.isEmpty()) {
            System.out.println("E-IMZO: parol oynasi chiqmadi");
            return false;
        }
        try {
            System.setProperty("java.awt.headless", "false");
            Robot robot = new Robot();
            robot.delay(300);
            oldingaChiqar(robot); // parol FAQAT E-IMZO oynasi oldinda bo'lsa yoziladi
            // parol clipboard orqali qo'yiladi (tez, klaviatura tiliga bog'liq emas), keyin clipboard tozalanadi
            java.awt.datatransfer.Clipboard cb = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            cb.setContents(new java.awt.datatransfer.StringSelection(Config.get("eimzo.password")), null);
            if (!oldinda()) throw new IllegalStateException("E-IMZO oynasi oldinda emas, parol yozilmadi");
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_A);
            robot.keyPress(KeyEvent.VK_V);
            robot.keyRelease(KeyEvent.VK_V);
            robot.keyRelease(KeyEvent.VK_CONTROL);
            robot.delay(150);
            tozala();
            if (!oldinda()) throw new IllegalStateException("Parol qo'yilayotganda E-IMZO oynasi oldindan ketdi, Enter bosilmadi");
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
            System.out.println("E-IMZO: parol kiritildi (oyna: " + title + ")");
            // oyna yopilishini kutamiz
            for (int i = 0; i < 30 && !windowTitle().isEmpty(); i++) sleep(150);
            return true;
        } catch (Exception e) {
            throw new RuntimeException("E-IMZO parolini kiritib bo'lmadi: " + e.getMessage(), e);
        } finally {
            tozala();
        }
    }

    public static boolean typePassword() {
        return typePassword(20);
    }

    /**
     * Salbiy testlar uchun: E-IMZO parol oynasi timeoutSec ichida chiqsa, uni WM_CLOSE bilan PAROLSIZ yopadi
     * (ESC dan ishonchli). @return true - oyna chiqdi (ya'ni sayt so'rovni o'tkazib yubordi).
     */
    public static boolean closeIfShown(int timeoutSec) {
        long end = System.currentTimeMillis() + timeoutSec * 1000L;
        while (System.currentTimeMillis() < end) {
            if (!windowTitle().isEmpty()) {
                for (int i = 0; i < 5 && !windowTitle().isEmpty(); i++) {
                    closeWindow();
                    sleep(300);
                }
                System.out.println("E-IMZO: oyna chiqdi va parolsiz yopildi (WM_CLOSE)" + (windowTitle().isEmpty() ? "" : " — DIQQAT: hali ochiq!"));
                return true;
            }
            sleep(200);
        }
        return false;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignore) { Thread.currentThread().interrupt(); }
    }
}
