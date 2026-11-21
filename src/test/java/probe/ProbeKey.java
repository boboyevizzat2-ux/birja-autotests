package probe;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.testng.annotations.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** VAQTINCHALIK: ERI kalit orqali kirishni o'rganish (ekranda). */
public class ProbeKey {
    WebDriver d; JavascriptExecutor js; Properties p = new Properties(); String out;

    void shot(String name) throws Exception {
        File s = ((TakesScreenshot) d).getScreenshotAs(OutputType.FILE);
        Files.copy(s.toPath(), Paths.get(out, name + ".png"), StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    public void probe() throws Exception {
        try (Reader r = new InputStreamReader(new FileInputStream("login.properties"), StandardCharsets.UTF_8)) { p.load(r); }
        out = System.getProperty("probe.out");
        ChromeOptions co = uz.ebirja.base.BaseTest.chromeOptions();
        org.openqa.selenium.logging.LoggingPreferences lp = new org.openqa.selenium.logging.LoggingPreferences();
        lp.enable(org.openqa.selenium.logging.LogType.BROWSER, java.util.logging.Level.ALL);
        co.setCapability("goog:loggingPrefs", lp);
        ChromeDriver cd = new ChromeDriver(co);
        uz.ebirja.base.BaseTest.grantEimzo(cd, "https://test-app.ebirja.uz", "https://test-head.ebirja.uz");
        d = cd; js = cd;
        String site = System.getProperty("site", "trader");
        String base = site.equals("trader") ? p.getProperty("trader.url") : p.getProperty("admin.url");
        try {
            // Avvalgi seansdan chiqamiz (doimiy profil)
            d.get(base + "/uz/auth/loginkey");
            Thread.sleep(3000);
            d.manage().deleteAllCookies();
            js.executeScript("localStorage.clear(); sessionStorage.clear();");
            d.get(base + "/uz/auth/loginkey");
            Thread.sleep(6000);
            if (site.equals("trader")) {
                d.findElement(By.xpath("//button[contains(normalize-space(.),'Birja savdolari')]")).click();
                Thread.sleep(1000);
            }
            shot("key_1_sahifa");
            WebElement sel = d.findElement(By.cssSelector("input[id^='react-select'], input#key"));
            System.out.println("Kalit maydoni: id=" + sel.getAttribute("id"));
            js.executeScript("arguments[0].scrollIntoView({block:'center'})", sel);
            sel.click();
            sel.sendKeys(Keys.ARROW_DOWN);
            Thread.sleep(3000);
            List<String> keys = new ArrayList<>();
            for (WebElement o : d.findElements(By.cssSelector("[id*='-option-']")))
                if (o.isDisplayed()) keys.add(o.getText().replace("\n", " | "));
            System.out.println("KALITLAR (" + keys.size() + "):");
            keys.forEach(k -> System.out.println("   " + k));
            shot("key_2_royxat");
            String want = System.getProperty("pick", "");
            if (!want.isEmpty()) {
                WebElement chosen = null;
                for (WebElement o : d.findElements(By.cssSelector("[id*='-option-']")))
                    if (o.isDisplayed() && o.getText().contains(want)) { chosen = o; break; }
                if (chosen == null) { System.out.println("'" + want + "' kaliti topilmadi"); return; }
                System.out.println("Tanlandi: " + chosen.getText().replace("\n", " | "));
                chosen.click();
                Thread.sleep(1000);
                d.findElement(By.xpath("//button[@type='submit' and contains(normalize-space(.),'Tizimga kirish')]")).click();
                System.out.println(">>> 'Tizimga kirish' bosildi. Parol oynasi chiqsa, sahifa ichidami tekshiraman...");
                for (int s = 0; s < 8; s++) {
                    Thread.sleep(1000);
                    for (WebElement e : d.findElements(By.cssSelector("input[type=password]")))
                        if (e.isDisplayed()) { System.out.println("  Sahifada parol maydoni bor -> parolni yozaman"); e.sendKeys(p.getProperty("eimzo.password")); e.sendKeys(Keys.ENTER); }
                }
                shot("key_3_keyin");
                // E-IMZO o'z oynasida parol so'raydi: butun ekranni rasmga olib, parolni klaviaturadan yozamiz
                java.awt.Robot robot = new java.awt.Robot();
                java.awt.Rectangle screen = new java.awt.Rectangle(java.awt.Toolkit.getDefaultToolkit().getScreenSize());
                javax.imageio.ImageIO.write(robot.createScreenCapture(screen), "png", new File(out, "ekran_parol_oynasi.png"));
                System.out.println("Butun ekran rasmga olindi");
                if (Boolean.getBoolean("robot")) {
                    for (char c : p.getProperty("eimzo.password").toCharArray()) {
                        int code = java.awt.event.KeyEvent.getExtendedKeyCodeForChar(c);
                        robot.keyPress(code); robot.keyRelease(code); robot.delay(80);
                    }
                    robot.keyPress(java.awt.event.KeyEvent.VK_ENTER); robot.keyRelease(java.awt.event.KeyEvent.VK_ENTER);
                    System.out.println("Parol klaviaturadan yozildi va Enter bosildi");
                }
                System.out.println(">>> Agar parol oynasi E-IMZO dasturida chiqqan bo'lsa, '1' ni kiriting. 90 soniya kutaman...");
                for (int s = 0; s < 90 && d.getCurrentUrl().contains("/auth/"); s++) Thread.sleep(1000);
                Thread.sleep(3000);
                System.out.println("Natija URL: " + d.getCurrentUrl());
                String b = d.findElement(By.tagName("body")).getText();
                System.out.println("Ekran: " + b.replace("\n", " | ").substring(0, Math.min(300, b.length())));
                shot("key_4_natija");
            }
            for (org.openqa.selenium.logging.LogEntry le : d.manage().logs().get(org.openqa.selenium.logging.LogType.BROWSER))
                if (le.getLevel().intValue() >= java.util.logging.Level.WARNING.intValue())
                    System.out.println("  KONSOL: " + le.getMessage().substring(0, Math.min(250, le.getMessage().length())));
            Thread.sleep(2000);
        } finally {
            d.quit();
        }
    }
}
