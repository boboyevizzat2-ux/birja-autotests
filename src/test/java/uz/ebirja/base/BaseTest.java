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
    public static ChromeOptions chromeOptions() {
        ChromeOptions o = new ChromeOptions();
        if (Boolean.getBoolean("headless")) {
            o.addArguments("--headless=new");
        }
        o.addArguments("--window-size=1600,1000");
        // Doimiy profil: E-IMZO va boshqa ruxsatlar bir marta berilsa, eslab qolinadi
        String profile = new File("chrome-profile").getAbsolutePath();
        o.addArguments("--user-data-dir=" + profile);
        // Saytga kompyuterdagi E-IMZO dasturiga (localhost) so'rovsiz ulanishga ruxsat
        o.addArguments("--disable-features=LocalNetworkAccessChecks,LocalNetworkAccessChecksWebSockets,BlockInsecurePrivateNetworkRequests,PrivateNetworkAccessSendPreflights");
        // "Доступ к другим приложениям и сервисам на этом устройстве" so'rovi chiqmasligi uchun
        // E-IMZO ruxsatini profil sozlamalariga doimiy yozib qo'yamiz ("Разрешить" bosilgandek)
        Map<String, Object> allow = new HashMap<>();
        for (String origin : new String[]{"https://test-app.ebirja.uz:443,*", "https://test-head.ebirja.uz:443,*"}) {
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
        grantEimzo(cd, "https://test-app.ebirja.uz", "https://test-head.ebirja.uz");
        driver = cd;
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
        return driver;
    }

    protected void closeBrowser() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }
}
