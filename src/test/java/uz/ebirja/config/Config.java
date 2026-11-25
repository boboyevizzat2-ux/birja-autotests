package uz.ebirja.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Sozlamalar. O'qish tartibi: -Dkalit=qiymat -> muhit o'zgaruvchisi (TRADER_URL) -> login.properties.
 * login.properties GitHub-ga yuklanmaydi (namunasi: login.properties.example).
 * Bir nechta sayt: -Denv=app berilsa, avval "kalit.app" (masalan trader.url.app) qidiriladi, bo'lmasa "kalit".
 */
public final class Config {
    private static final Properties P = new Properties();
    private static String faylXatosi = null;

    static {
        try (Reader r = new InputStreamReader(new FileInputStream("login.properties"), StandardCharsets.UTF_8)) {
            P.load(r);
        } catch (Exception e) {
            faylXatosi = "login.properties o'qilmadi (" + new File("login.properties").getAbsolutePath() + "): " + e.getMessage();
        }
    }

    private Config() {}

    private static String qidir(String key) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) v = System.getenv(key.toUpperCase().replace('.', '_'));
        if (v == null || v.isBlank()) v = P.getProperty(key);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String topish(String key) {
        String env = System.getProperty("env", "");
        String v = env.isBlank() ? null : qidir(key + "." + env.trim());
        return v != null ? v : qidir(key);
    }

    public static String get(String key) {
        String v = topish(key);
        if (v == null) throw new RuntimeException("'" + key + "' sozlamasi yo'q: -D" + key + "=..., " + key.toUpperCase().replace('.', '_')
                + " muhit o'zgaruvchisi yoki login.properties da yozing." + (faylXatosi != null ? " " + faylXatosi : ""));
        return v;
    }

    /** Ixtiyoriy sozlama: topilmasa standart qiymat. */
    public static String getOr(String key, String def) {
        String v = topish(key);
        return v == null ? def : v;
    }

    public static String traderUrl() { return get("trader.url"); }
    public static String adminUrl()  { return get("admin.url"); }

    /** src/test/resources dagi fayl (classpath orqali, qaysi papkadan ishga tushirilganiga bog'liq emas). */
    public static File resurs(String nom) {
        URL u = Config.class.getClassLoader().getResource(nom);
        if (u == null) throw new RuntimeException("Resurs topilmadi: " + nom);
        try { return new File(u.toURI()); } catch (Exception e) { throw new RuntimeException("Resurs ochilmadi: " + u, e); }
    }
}
