package probe;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.testng.annotations.Test;
import uz.ebirja.base.BaseTest;
import uz.ebirja.base.Eimzo;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** VAQTINCHALIK: admin savdo kalendarini o'rganish va kun qo'shish (ekranda). */
public class ProbeCalendar {
    WebDriver d; JavascriptExecutor js; Properties p = new Properties(); String out;

    void shot(String name) throws Exception {
        File s = ((TakesScreenshot) d).getScreenshotAs(OutputType.FILE);
        Files.copy(s.toPath(), Paths.get(out, name + ".png"), StandardCopyOption.REPLACE_EXISTING);
    }

    String clean(String s) { return s == null ? "" : s.replace("\n", " | ").replaceAll("\\s+", " ").trim(); }

    void loginKey(String base, String jsh) throws Exception {
        d.get(base + "/uz/auth/loginkey");
        Thread.sleep(3000);
        d.manage().deleteAllCookies();
        js.executeScript("localStorage.clear(); sessionStorage.clear();");
        d.get(base + "/uz/auth/loginkey");
        Thread.sleep(6000);
        WebElement keySel = d.findElement(By.cssSelector("input[id^='react-select'], input#key"));
        boolean found = false;
        for (int t = 0; t < 10 && !found; t++) {
            try { keySel.click(); } catch (Exception e) { js.executeScript("arguments[0].focus()", keySel); }
            keySel.sendKeys(Keys.ARROW_DOWN);
            Thread.sleep(2000);
            for (WebElement opt : d.findElements(By.cssSelector("[id*='-option-']")))
                if (opt.isDisplayed() && opt.getText().contains(jsh)) { js.executeScript("arguments[0].scrollIntoView({block:'center'})", opt); opt.click(); found = true; break; }
            if (!found) { keySel.sendKeys(Keys.ESCAPE); Thread.sleep(1000); }
        }
        Thread.sleep(800);
        d.findElement(By.xpath("//button[@type='submit' and contains(normalize-space(.),'Tizimga kirish')]")).click();
        Eimzo.typePassword();
        for (int s = 0; s < 60 && d.getCurrentUrl().contains("/auth/"); s++) Thread.sleep(1000);
        Thread.sleep(3000);
        System.out.println("Kirildi: " + d.getCurrentUrl());
    }

    @Test
    public void probe() throws Exception {
        try (Reader r = new InputStreamReader(new FileInputStream("login.properties"), StandardCharsets.UTF_8)) { p.load(r); }
        out = System.getProperty("probe.out");
        ChromeDriver cd = new ChromeDriver(BaseTest.chromeOptions());
        BaseTest.grantEimzo(cd, uz.ebirja.base.BaseTest.saytlar());
        d = cd; js = cd;
        String base = p.getProperty("admin.url");
        try {
            loginKey(base, p.getProperty("admin2.login"));
            d.get(base + "/uz/moderationv2/sales-schedule/trading-calendar");
            Thread.sleep(10000);
            String m = clean(d.findElement(By.tagName("main")).getText());
            System.out.println("MATN: " + m.substring(0, Math.min(2500, m.length())));
            Set<String> btn = new LinkedHashSet<>();
            for (WebElement e : d.findElements(By.cssSelector("main button, main [role=button], main [role=tab]")))
                try { if (e.isDisplayed()) btn.add(clean(e.getText()) + (e.getAttribute("class").contains("disabled") ? "(o'chiq)" : "")); } catch (Exception ignore) {}
            System.out.println("Tugmalar: " + btn);
            for (WebElement e : d.findElements(By.cssSelector("main input, main select")))
                try { if (e.isDisplayed()) System.out.println("input: type=" + e.getAttribute("type") + " id=" + e.getAttribute("id") + " ph=" + e.getAttribute("placeholder") + " val=" + e.getAttribute("value")); } catch (Exception ignore) {}
            shot("savdo_kalendari");
            String daysProp = System.getProperty("days", "");
            if (!daysProp.isEmpty()) {
                for (String dd : daysProp.split(",")) {
                    d.navigate().refresh();
                    Thread.sleep(6000);
                    WebElement tile = null;
                    for (WebElement b : d.findElements(By.cssSelector("button.react-calendar__tile")))
                        if (b.isDisplayed() && b.getText().trim().equals(dd) && !b.getAttribute("class").contains("neighboringMonth")) tile = b;
                    js.executeScript("arguments[0].scrollIntoView({block:'center'})", tile);
                    tile.click();
                    Thread.sleep(2000);
                    WebElement sel = null;
                    for (WebElement e : d.findElements(By.cssSelector("input[id^='react-select']"))) try { if (e.isDisplayed()) sel = e; } catch (Exception ignore) {}
                    if (sel == null) { shot("kalendar_oyna_" + dd); System.out.println("  ro'yxat topilmadi"); continue; }
                    js.executeScript("arguments[0].focus()", sel);
                    sel.sendKeys(Keys.ARROW_DOWN);
                    Thread.sleep(1500);
                    List<String> opts = new ArrayList<>();
                    WebElement work = null;
                    for (WebElement o : d.findElements(By.cssSelector("[id*='-option-']"))) if (o.isDisplayed()) { opts.add(clean(o.getText())); if (o.getText().toLowerCase().contains("ish kuni")) work = o; }
                    System.out.println(dd + "-kun variantlari: " + opts);
                    if (work == null) { System.out.println("  'Ish kuni' varianti topilmadi"); continue; }
                    work.click();
                    Thread.sleep(800);
                    for (WebElement b : d.findElements(By.xpath("//button[normalize-space(.)='Saqlash']"))) if (b.isDisplayed()) { b.click(); break; }
                    Eimzo.typePassword(5);
                    for (int s = 0; s < 4; s++) {
                        Thread.sleep(700);
                        for (WebElement e : d.findElements(By.cssSelector("[class*=toast], [class*=Toastify], [role=alert], [role=status]")))
                            try { if (e.isDisplayed() && !e.getText().isBlank()) System.out.println("  XABAR: " + clean(e.getText())); } catch (Exception ignore) {}
                    }
                }
                d.navigate().refresh();
                Thread.sleep(6000);
                for (WebElement b : d.findElements(By.cssSelector("button.react-calendar__tile")))
                    if (b.isDisplayed() && Arrays.asList(daysProp.split(",")).contains(b.getText().trim()) && !b.getAttribute("class").contains("neighboringMonth"))
                        System.out.println("NATIJA " + b.getText().trim() + "-kun: class=" + b.getAttribute("class").replace("react-calendar__", "") + " rang=" + b.getCssValue("color") + " fon=" + b.getCssValue("background-color"));
                shot("savdo_kalendari_natija");
                return;
            }
            // 21-sana katagini bosib ko'ramiz (nima ochilishini bilish uchun)
            List<WebElement> days = new ArrayList<>();
            for (WebElement e : d.findElements(By.xpath("//main//*[normalize-space(text())='21']"))) if (e.isDisplayed()) days.add(e);
            System.out.println("'21' yozuvli elementlar: " + days.size());
            if (!days.isEmpty()) {
                WebElement day = days.get(0);
                System.out.println("  21-kun HTML: " + day.findElement(By.xpath("./..")).getAttribute("outerHTML").replaceAll("<svg.*?</svg>", "").substring(0, Math.min(600, day.findElement(By.xpath("./..")).getAttribute("outerHTML").length())));
                js.executeScript("arguments[0].scrollIntoView({block:'center'})", day);
                day.click();
                Thread.sleep(2500);
                for (WebElement mo : d.findElements(By.cssSelector("[role=dialog], [class*=modal], [class*=Modal], [role=menu]")))
                    try { if (mo.isDisplayed() && !mo.getText().isBlank()) System.out.println("  21 BOSILGANDA OYNA: " + clean(mo.getText())); } catch (Exception ignore) {}
                shot("savdo_kalendari_21");
            }
        } finally {
            d.quit();
        }
    }
}
