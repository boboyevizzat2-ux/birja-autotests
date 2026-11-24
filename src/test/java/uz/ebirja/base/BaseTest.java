package uz.ebirja.base;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/** Brauzerni ochish va yopish uchun umumiy qism. */
public abstract class BaseTest {
    protected WebDriver driver;

    /** Brauzer sozlamalari: E-IMZO ruxsatlari eslab qolinadi. */
    public static ChromeOptions chromeOptions() { return chromeOptions("chrome-profile"); }

    public static ChromeOptions chromeOptions(String profileDir) {
        ChromeOptions o = new ChromeOptions();
        if (Boolean.getBoolean("headless")) {
            o.addArguments("--headless=new");
        }
        if (Boolean.getBoolean("headless")) o.addArguments("--window-size=1600,1000");
        else o.addArguments("--start-maximized"); // ekranda butun oynada ochiladi
        // "Chrome avtomatlashtirilgan dastur boshqaruvida" sariq qatori joy egallamasin
        o.setExperimentalOption("excludeSwitches", java.util.List.of("enable-automation"));
        // Doimiy profil: E-IMZO va boshqa ruxsatlar bir marta berilsa, eslab qolinadi
        String profile = new File(profileDir).getAbsolutePath();
        o.addArguments("--user-data-dir=" + profile);
        // Saytga kompyuterdagi E-IMZO dasturiga (localhost) so'rovsiz ulanishga ruxsat
        o.addArguments("--disable-features=LocalNetworkAccessChecks,LocalNetworkAccessChecksWebSockets,BlockInsecurePrivateNetworkRequests,PrivateNetworkAccessSendPreflights");
        // "Доступ к другим приложениям и сервисам на этом устройстве" so'rovi chiqmasligi uchun
        // E-IMZO ruxsatini profil sozlamalariga doimiy yozib qo'yamiz ("Разрешить" bosilgandek)
        Map<String, Object> allow = new HashMap<>();
        for (String s : saytlar()) {
            String origin = s + ":443,*";
            allow.put(origin, Map.of("setting", 1));
        }
        Map<String, Object> prefs = new HashMap<>();
        for (String type : new String[]{"local_network_access", "local_network", "loopback_network"}) {
            prefs.put("profile.content_settings.exceptions." + type, allow);
        }
        o.setExperimentalOption("prefs", prefs);
        return o;
    }

    /**
     * Chrome-ni ochadi.
     * Brauzerni ko'rsatmasdan (orqa fonda) ishga tushirish uchun: mvn test -Dheadless=true
     */
    /** Treyder va admin saytlari (login.properties: trader.url, admin.url) - E-IMZO ruxsatlari shular uchun beriladi. */
    public static String[] saytlar() {
        return new String[]{origin(uz.ebirja.config.Config.traderUrl()), origin(uz.ebirja.config.Config.adminUrl())};
    }

    private static String origin(String url) {
        java.net.URI u = java.net.URI.create(url.trim());
        return u.getScheme() + "://" + u.getHost();
    }

    /** Saytlarga kompyuterdagi E-IMZO dasturiga ulanish ruxsatini oldindan beradi. */
    public static void grantEimzo(ChromeDriver d, String... origins) {
        for (String origin : origins) {
            for (String perm : new String[]{"localNetworkAccess", "localNetwork", "loopbackNetwork"}) {
                try {
                    d.executeCdpCommand("Browser.grantPermissions",
                            java.util.Map.of("permissions", java.util.List.of(perm), "origin", origin));
                    System.out.println("E-IMZO ruxsati berildi: " + perm + " -> " + origin);
                } catch (Exception ignore) {
                    // bu Chrome versiyasida bunday ruxsat nomi yo'q
                }
            }
        }
    }

    protected WebDriver openBrowser() {
        ChromeDriver cd = new ChromeDriver(chromeOptions());
        grantEimzo(cd, saytlar());
        driver = cd;
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
        return driver;
    }

    /**
     * DOIMIY SESSIYALAR (tezlik uchun): har bir foydalanuvchi (trader1, trader2, admin1, admin2) uchun
     * alohida brauzer (o'z profili bilan), kirish run davomida BIR MARTA. Run oxirida hammasi yopiladi.
     */
    private static final java.util.Map<String, WebDriver> SESSIYALAR = new java.util.LinkedHashMap<>();

    protected WebDriver kim(String who) {
        WebDriver d = SESSIYALAR.get(who);
        if (d == null) {
            ChromeOptions o = chromeOptions("chrome-profile-" + who); // har bir foydalanuvchiga alohida profil
            ChromeDriver cd = new ChromeDriver(o);
            grantEimzo(cd, saytlar());
            cd.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
            d = cd;
            SESSIYALAR.put(who, d);
            boolean trader = who.startsWith("trader");
            uz.ebirja.base.Ui.loginByKey(d, trader ? uz.ebirja.config.Config.traderUrl() : uz.ebirja.config.Config.adminUrl(),
                    uz.ebirja.config.Config.get(who + ".login"), trader);
        }
        driver = d;
        return d;
    }

    @org.testng.annotations.AfterSuite(alwaysRun = true)
    public void hammaSessiyalarniYopish() {
        for (WebDriver d : SESSIYALAR.values()) try { d.quit(); } catch (Exception ignore) {}
        SESSIYALAR.clear();
    }

    /** Oddiy brauzerni yopadi; doimiy sessiya bo'lsa yopmaydi (keyingi test ishlatadi). */
    protected void closeBrowser() {
        if (driver != null) {
            if (!SESSIYALAR.containsValue(driver)) driver.quit();
            driver = null;
        }
    }
}
