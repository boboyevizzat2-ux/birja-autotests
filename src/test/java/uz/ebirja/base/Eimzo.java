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

    private static String runScript(boolean focus) {
        try {
            String script = new java.io.File("src/test/resources/eimzo-window.ps1").getAbsolutePath();
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
            robot.delay(500);
            focusWindow(); // parol maydoni klaviaturani qabul qilishi uchun
            robot.delay(500);
            for (char c : Config.get("eimzo.password").toCharArray()) {
                int code = KeyEvent.getExtendedKeyCodeForChar(c);
                boolean upper = Character.isUpperCase(c);
                if (upper) robot.keyPress(KeyEvent.VK_SHIFT);
                robot.keyPress(code);
                robot.keyRelease(code);
                if (upper) robot.keyRelease(KeyEvent.VK_SHIFT);
                robot.delay(60);
            }
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
            System.out.println("E-IMZO: parol kiritildi (oyna: " + title + ")");
            // oyna yopilishini kutamiz
            for (int i = 0; i < 20 && !windowTitle().isEmpty(); i++) sleep(300);
            return true;
        } catch (Exception e) {
            throw new RuntimeException("E-IMZO parolini kiritib bo'lmadi", e);
        }
    }

    public static boolean typePassword() {
        return typePassword(20);
    }

    /**
     * Salbiy testlar uchun: E-IMZO oynasi timeoutSec ichida chiqsa, parol YOZMASDAN ESC bilan yopadi.
     * @return true - oyna chiqdi (ya'ni sayt formani o'tkazib yubordi); false - oyna chiqmadi
     */
    public static boolean cancelIfShown(int timeoutSec) {
        long end = System.currentTimeMillis() + timeoutSec * 1000L;
        while (System.currentTimeMillis() < end) {
            if (!windowTitle().isEmpty()) {
                try {
                    System.setProperty("java.awt.headless", "false");
                    Robot robot = new Robot();
                    focusWindow();
                    robot.delay(400);
                    robot.keyPress(KeyEvent.VK_ESCAPE);
                    robot.keyRelease(KeyEvent.VK_ESCAPE);
                    for (int i = 0; i < 10 && !windowTitle().isEmpty(); i++) sleep(300);
                } catch (Exception ignore) {}
                System.out.println("E-IMZO: oyna chiqdi va parolsiz yopildi");
                return true;
            }
            sleep(300);
        }
        return false;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignore) { Thread.currentThread().interrupt(); }
    }
}
