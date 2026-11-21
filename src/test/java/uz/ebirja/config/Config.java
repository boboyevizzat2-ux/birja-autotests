package uz.ebirja.config;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * login.properties faylidan sozlamalarni o'qiydi.
 * Bu fayl GitHub-ga yuklanmaydi: har bir kompyuterda alohida yaratiladi
 * (namunasi: login.properties.example).
 */
public final class Config {
    private static final Properties P = new Properties();

    static {
        try (Reader r = new InputStreamReader(new FileInputStream("login.properties"), StandardCharsets.UTF_8)) {
            P.load(r);
        } catch (Exception e) {
            throw new RuntimeException(
                    "login.properties fayli topilmadi! login.properties.example faylidan nusxa olib, parollarni yozing.", e);
        }
    }

    private Config() {}

    public static String get(String key) {
        String v = P.getProperty(key);
        if (v == null || v.isBlank()) {
            throw new RuntimeException("login.properties faylida '" + key + "' to'ldirilmagan");
        }
        return v.trim();
    }

    /** Ixtiyoriy sozlama: faylda bo'lmasa standart qiymat qaytariladi. */
    public static String getOr(String key, String def) {
        String v = P.getProperty(key);
        return (v == null || v.isBlank()) ? def : v.trim();
    }

    public static String traderUrl() { return get("trader.url"); }
    public static String adminUrl()  { return get("admin.url"); }
}
