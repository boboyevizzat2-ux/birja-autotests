package uz.ebirja.base;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;
import java.time.Duration;

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
        return o;
    }

    /**
     * Chrome-ni ochadi.
     * Brauzerni ko'rsatmasdan (orqa fonda) ishga tushirish uchun: mvn test -Dheadless=true
     */
    protected WebDriver openBrowser() {
        driver = new ChromeDriver(chromeOptions());
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
