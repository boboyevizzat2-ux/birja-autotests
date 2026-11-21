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

/** VAQTINCHALIK: savdodan keyin hosil bo'lgan bitimni treyderlar kabinetida topish (ekranda). */
public class ProbeDeal {
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
        Thread.sleep(3000);
        js.executeScript("arguments[0].click()", d.findElement(By.xpath("//button[contains(normalize-space(.),'Birja savdolari')]")));
        Thread.sleep(800);
        WebElement keySel = d.findElement(By.cssSelector("input[id^='react-select']"));
        boolean found = false;
        for (int t = 0; t < 10 && !found; t++) {
            try { keySel.click(); } catch (Exception e) { js.executeScript("arguments[0].focus()", keySel); }
            keySel.sendKeys(Keys.ARROW_DOWN);
            Thread.sleep(2000);
            for (WebElement opt : d.findElements(By.cssSelector("[id*='-option-']")))
                if (opt.isDisplayed() && opt.getText().contains(jsh)) { js.executeScript("arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", opt); found = true; break; }
            if (!found) { keySel.sendKeys(Keys.ESCAPE); Thread.sleep(1000); }
        }
        Thread.sleep(800);
        js.executeScript("arguments[0].click()", d.findElement(By.xpath("//button[@type='submit' and contains(normalize-space(.),'Tizimga kirish')]")));
        Eimzo.typePassword();
        for (int s = 0; s < 60 && d.getCurrentUrl().contains("/auth/"); s++) Thread.sleep(1000);
        Thread.sleep(3000);
    }

    void contracts(String who, String path) throws Exception {
        String base = p.getProperty("trader.url");
        d.get(base + path);
        Thread.sleep(7000);
        String product = new String(Files.readAllBytes(Paths.get(System.getProperty("productFile"))), StandardCharsets.UTF_8).trim();
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        for (String tab : new String[]{"Sotish", "Sotib olish"}) {
            for (WebElement t : d.findElements(By.xpath("//main//*[normalize-space(text())='" + tab + "']")))
                try { if (t.isDisplayed()) { js.executeScript("arguments[0].click()", t); break; } } catch (Exception ignore) {}
            Thread.sleep(5000);
            List<WebElement> rows = d.findElements(By.cssSelector("main tbody tr"));
            int hit = 0;
            for (WebElement r : rows) {
                String tx = clean(r.getText());
                if (tx.contains(today) || tx.contains(product)) {
                    hit++;
                    System.out.println("  [" + who + " / " + tab + "] BUGUNGI: " + tx.substring(0, Math.min(450, tx.length())));
                }
            }
            System.out.println("  [" + who + " / " + tab + "] qatorlar: " + rows.size() + ", bugungi: " + hit);
            shot("bitim_" + who + "_" + (tab.equals("Sotish") ? "sotish" : "olish"));
        }
        if (true) return;
        System.out.println("\n=== " + who + "  " + path);
        List<String> tabs = new ArrayList<>();
        for (WebElement t : d.findElements(By.cssSelector("main button, main [role=tab]"))) try { if (t.isDisplayed() && !t.getText().isBlank() && t.getText().length() < 40) tabs.add(clean(t.getText())); } catch (Exception ignore) {}
        System.out.println("  Tugmalar: " + tabs);
        List<String> th = new ArrayList<>();
        for (WebElement e : d.findElements(By.cssSelector("main thead th"))) th.add(clean(e.getText()));
        System.out.println("  Ustunlar: " + th);
        List<WebElement> rows = d.findElements(By.cssSelector("main tbody tr"));
        for (int i = 0; i < Math.min(3, rows.size()); i++) System.out.println("  QATOR " + (i + 1) + ": " + clean(rows.get(i).getText()).substring(0, Math.min(500, clean(rows.get(i).getText()).length())));
        shot("bitim_" + who);
    }

    void toast(String label) {
        for (WebElement e : d.findElements(By.cssSelector("[class*=toast], [class*=Toastify], [role=alert], [role=status]")))
            try { if (e.isDisplayed() && !e.getText().isBlank()) System.out.println("  " + label + " XABAR: " + clean(e.getText())); } catch (Exception ignore) {}
        for (WebElement m : d.findElements(By.cssSelector("[role=dialog], [class*=modal], [class*=Modal]")))
            try { if (m.isDisplayed() && !m.getText().isBlank()) System.out.println("  " + label + " OYNA: " + clean(m.getText()).substring(0, Math.min(500, clean(m.getText()).length()))); } catch (Exception ignore) {}
    }

    /** Bitim qatorini belgilab "Ommaviy imzolash" bosadi. */
    void sign(String who, String path, String tab, String deal) throws Exception {
        d.get(p.getProperty("trader.url") + path);
        Thread.sleep(7000);
        for (WebElement t : d.findElements(By.xpath("//main//*[normalize-space(text())='" + tab + "']")))
            try { if (t.isDisplayed()) { js.executeScript("arguments[0].click()", t); break; } } catch (Exception ignore) {}
        Thread.sleep(5000);
        WebElement row = null;
        for (WebElement r : d.findElements(By.cssSelector("main tbody tr"))) if (r.getText().contains(deal)) row = r;
        if (row == null) { System.out.println("  [" + who + "] " + deal + " topilmadi"); return; }
        System.out.println("  [" + who + "] oldin: " + clean(row.getText()).substring(0, 90));
        WebElement cb = row.findElement(By.cssSelector("input[type=checkbox]"));
        js.executeScript("arguments[0].scrollIntoView({block:'center'})", cb);
        new org.openqa.selenium.interactions.Actions(d).moveToElement(cb).click().perform();
        Thread.sleep(600);
        if (!cb.isSelected()) js.executeScript("arguments[0].click()", cb);
        System.out.println("  [" + who + "] belgilandi: " + cb.isSelected());
        WebElement btn = d.findElement(By.xpath("//main//button[contains(normalize-space(.),'Ommaviy imzolash')]"));
        js.executeScript("arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", btn);
        System.out.println("  [" + who + "] 'Ommaviy imzolash' bosildi");
        Thread.sleep(2500);
        toast("1");
        shot("imzo_oyna_" + who);
        // tasdiqlash oynasi bo'lsa
        for (WebElement b : d.findElements(By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button")))
            try { String t = b.getText().trim(); if (b.isDisplayed() && t.matches("(?i).*(imzolash|tasdiqlash|ha|yuborish).*")) { System.out.println("  oynadagi '" + t + "' bosildi"); js.executeScript("arguments[0].click()", b); break; } } catch (Exception ignore) {}
        Eimzo.typePassword(10);
        for (int s = 0; s < 5; s++) { Thread.sleep(1000); toast("2." + s); }
        d.navigate().refresh();
        Thread.sleep(6000);
        for (WebElement t : d.findElements(By.xpath("//main//*[normalize-space(text())='" + tab + "']")))
            try { if (t.isDisplayed()) { js.executeScript("arguments[0].click()", t); break; } } catch (Exception ignore) {}
        Thread.sleep(5000);
        for (WebElement r : d.findElements(By.cssSelector("main tbody tr"))) if (r.getText().contains(deal)) System.out.println("  [" + who + "] keyin: " + clean(r.getText()).substring(0, 90));
        shot("imzo_natija_" + who);
    }

    @Test
    public void probe() throws Exception {
        try (Reader r = new InputStreamReader(new FileInputStream("login.properties"), StandardCharsets.UTF_8)) { p.load(r); }
        out = System.getProperty("probe.out");
        ChromeDriver cd = new ChromeDriver(BaseTest.chromeOptions());
        BaseTest.grantEimzo(cd, "https://test-app.ebirja.uz", "https://test-head.ebirja.uz");
        d = cd; js = cd;
        String base = p.getProperty("trader.url");
        try {
            String deal = System.getProperty("deal", "");
            if (!deal.isEmpty()) {
                loginKey(base, p.getProperty("trader1.login"));
                sign("treyder1", "/uz/trader/contracts/brokers-contracts", "Sotish", deal);
                loginKey(base, p.getProperty("trader2.login"));
                sign("treyder2", "/uz/trader/contracts/brokers-contracts", "Sotib olish", deal);
                return;
            }
            loginKey(base, p.getProperty("trader1.login"));
            contracts("treyder1_mijoz", "/uz/trader/contracts/client-contracts");
            contracts("treyder1_mening", "/uz/trader/contracts/brokers-contracts");
            loginKey(base, p.getProperty("trader2.login"));
            contracts("treyder2_mijoz", "/uz/trader/contracts/client-contracts");
            contracts("treyder2_mening", "/uz/trader/contracts/brokers-contracts");
        } finally {
            d.quit();
        }
    }
}
