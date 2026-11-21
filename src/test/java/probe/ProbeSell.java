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

/** VAQTINCHALIK: Treyder 1 mahsulotni auksion savdoga chiqaradi (ekranda). */
public class ProbeSell {
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
        d.findElement(By.xpath("//button[contains(normalize-space(.),'Birja savdolari')]")).click();
        Thread.sleep(800);
        WebElement keySel = d.findElement(By.cssSelector("input[id^='react-select']"));
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

    void dumpDialogs(String label) {
        for (WebElement m : d.findElements(By.cssSelector("[role=dialog], [class*=modal], [class*=Modal]")))
            try { if (m.isDisplayed() && !m.getText().isBlank()) System.out.println("  " + label + " OYNA: " + clean(m.getText()).substring(0, Math.min(900, clean(m.getText()).length()))); } catch (Exception ignore) {}
        for (WebElement e : d.findElements(By.cssSelector("[role=dialog] input, [role=dialog] textarea, [class*=modal] input, [class*=modal] textarea")))
            try { if (e.isDisplayed()) System.out.println("    maydon: type=" + e.getAttribute("type") + " name=" + e.getAttribute("name") + " id=" + e.getAttribute("id") + " ph=" + e.getAttribute("placeholder") + " val=" + e.getAttribute("value")); } catch (Exception ignore) {}
        for (WebElement e : d.findElements(By.cssSelector("[class*=toast], [class*=Toastify], [role=alert], [role=status]")))
            try { if (e.isDisplayed() && !e.getText().isBlank()) System.out.println("  XABAR: " + clean(e.getText())); } catch (Exception ignore) {}
    }

    @Test
    public void probe() throws Exception {
        try (Reader r = new InputStreamReader(new FileInputStream("login.properties"), StandardCharsets.UTF_8)) { p.load(r); }
        out = System.getProperty("probe.out");
        String product = new String(Files.readAllBytes(Paths.get(System.getProperty("productFile"))), StandardCharsets.UTF_8).trim();
        ChromeDriver cd = new ChromeDriver(BaseTest.chromeOptions());
        BaseTest.grantEimzo(cd, "https://test-app.ebirja.uz", "https://test-head.ebirja.uz");
        d = cd; js = cd;
        String base = p.getProperty("trader.url");
        try {
            loginKey(base, p.getProperty("trader1.login"));
            d.get(base + "/uz/trader/sales/auction-sales?tradeTab=SELL");
            Thread.sleep(7000);
            List<String> tabs = new ArrayList<>();
            for (WebElement t : d.findElements(By.cssSelector("main button"))) try { if (t.isDisplayed() && !t.getText().isBlank() && t.getText().length() < 40) tabs.add(clean(t.getText())); } catch (Exception ignore) {}
            System.out.println("Tugmalar: " + tabs);
            // Sessiya holati (yuqoridagi yozuv)
            for (WebElement e : d.findElements(By.xpath("//main//*[contains(text(),'Yakunlangan') or contains(text(),'davom') or contains(text(),'Faol') or contains(text(),'qoldi')]")))
                try { if (e.isDisplayed()) System.out.println("SESSIYA HOLATI: " + clean(e.getText())); } catch (Exception ignore) {}
            // Mijoz tanlash
            d.findElement(By.xpath("//button[contains(normalize-space(.),'Mijoz tanlash')]")).click();
            Thread.sleep(2500);
            dumpDialogs("MIJOZ");
            shot("mijoz_tanlash_oynasi");
            String client = System.getProperty("client", "NEXTGEN");
            WebElement cin = null;
            for (WebElement e : d.findElements(By.cssSelector("[role=dialog] input[id^='react-select'], [class*=modal] input[id^='react-select']"))) if (e.isDisplayed()) { cin = e; break; }
            if (cin != null) {
                cin.sendKeys(Keys.ARROW_DOWN);
                Thread.sleep(2000);
                List<String> opts = new ArrayList<>();
                WebElement pick = null;
                for (WebElement o : d.findElements(By.cssSelector("[id*='-option-']"))) if (o.isDisplayed()) { opts.add(clean(o.getText())); if (pick == null && o.getText().contains(client)) pick = o; }
                System.out.println("MIJOZLAR: " + opts);
                if (pick != null) { pick.click(); Thread.sleep(1000); System.out.println("Mijoz tanlandi: " + clean(pick.getText())); }
            } else {
                // jadval ko'rinishida: mijoz qatoridagi katakcha yoki "Tanlash" tugmasi
                for (WebElement tr : d.findElements(By.xpath("//*[@role='dialog' or contains(@class,'modal')]//tbody/tr")))
                    if (tr.isDisplayed() && tr.getText().contains(client)) {
                        System.out.println("Mijoz qatori: " + clean(tr.getText()));
                        List<WebElement> cb = tr.findElements(By.cssSelector("input[type=checkbox]"));
                        List<WebElement> rb = tr.findElements(By.cssSelector("button"));
                        for (WebElement b : rb) System.out.println("   qatordagi tugma: '" + clean(b.getText()) + "'");
                        if (!rb.isEmpty()) { js.executeScript("arguments[0].click()", rb.get(rb.size() - 1)); System.out.println("   qatordagi tugma bosildi"); }
                        else if (!cb.isEmpty()) { js.executeScript("arguments[0].click()", cb.get(0)); System.out.println("   katakcha belgilandi: " + cb.get(0).isSelected()); }
                        break;
                    }
                Thread.sleep(1500);
            }
            List<String> dlgBtns = new ArrayList<>();
            for (WebElement b : d.findElements(By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button"))) try { if (b.isDisplayed()) dlgBtns.add(clean(b.getText())); } catch (Exception ignore) {}
            System.out.println("Oynadagi tugmalar: " + dlgBtns);
            for (WebElement b : d.findElements(By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button")))
                try { String t = b.getText().trim(); if (b.isDisplayed() && (t.equals("Tanlash") || t.equals("Saqlash") || t.equals("Tasdiqlash") || t.equals("Davom etish") || t.equals("Tanlangan"))) { b.click(); System.out.println("'" + t + "' bosildi"); break; } } catch (Exception ignore) {}
            shot("mijoz_tanlangan");
            // "Mahsulotni tanlash" oynasi
            Thread.sleep(1500);
            boolean picked = false;
            for (WebElement tr : d.findElements(By.xpath("//*[@role='dialog' or contains(@class,'modal')]//tbody/tr")))
                try {
                    if (tr.isDisplayed() && tr.getText().contains(product)) {
                        List<WebElement> cb = tr.findElements(By.cssSelector("input[type=checkbox]"));
                        js.executeScript("arguments[0].click()", cb.get(0));
                        picked = true;
                        System.out.println("Mahsulot belgilandi: " + clean(tr.getText()) + " -> " + cb.get(0).isSelected());
                        break;
                    }
                } catch (Exception ignore) {}
            if (!picked) System.out.println("Mahsulot oynada topilmadi");
            Thread.sleep(3000);
            shot("mahsulot_tanlangan");
            // "Mijozni kontraktlarini tanlash" oynasi: kontrakt qatorini belgilab "Yuborish"
            for (WebElement tr : d.findElements(By.xpath("//*[@role='dialog' or contains(@class,'modal')]//tbody/tr")))
                try {
                    if (tr.isDisplayed() && tr.getText().contains(product)) {
                        System.out.println("Kontrakt qatori: " + clean(tr.getText()).substring(0, Math.min(200, clean(tr.getText()).length())));
                        WebElement cb = tr.findElements(By.cssSelector("input[type=checkbox]")).get(0);
                        js.executeScript("arguments[0].scrollIntoView({block:'center'})", cb);
                        new org.openqa.selenium.interactions.Actions(d).moveToElement(cb).click().perform();
                        Thread.sleep(600);
                        if (!cb.isSelected()) { cb.findElement(By.xpath("./..")).click(); Thread.sleep(600); }
                        System.out.println("  kontrakt belgilandi: " + cb.isSelected());
                        break;
                    }
                } catch (Exception ignore) {}
            Thread.sleep(1000);
            for (WebElement b : d.findElements(By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button[normalize-space(.)='Yuborish']")))
                if (b.isDisplayed()) { b.click(); System.out.println(">>> Kontraktlar oynasida 'Yuborish' bosildi"); break; }
            Eimzo.typePassword(5);
            for (int s = 0; s < 4; s++) { Thread.sleep(1000); dumpDialogs("K" + s); }
            shot("kontrakt_yuborildi_savdoga");
            Thread.sleep(5000);
            // Mahsulotimiz qatorini qidiramiz
            WebElement row = null;
            for (String tab : new String[]{""}) {
                if (!tab.isEmpty()) {
                    for (WebElement b : d.findElements(By.xpath("//main//button[normalize-space(.)=\"" + tab + "\"]"))) if (b.isDisplayed()) { b.click(); break; }
                    Thread.sleep(5000);
                }
                List<WebElement> rows = d.findElements(By.cssSelector("main tbody tr"));
                System.out.println("Tab '" + tab + "': " + rows.size() + " qator");
                for (int i = 0; i < Math.min(3, rows.size()); i++) System.out.println("   " + clean(rows.get(i).getText()).substring(0, Math.min(250, clean(rows.get(i).getText()).length())));
                for (WebElement r : rows) if (r.getText().contains(product)) { row = r; break; }
                if (row != null) { System.out.println(">>> Mahsulot topildi ('" + tab + "' tabida): " + clean(row.getText()).substring(0, Math.min(300, clean(row.getText()).length()))); break; }
            }
            shot("savdo_sahifa");
            if (row == null) { System.out.println("Mahsulot ro'yxatda topilmadi"); return; }
            // Qatordagi tugmalar
            List<WebElement> btns = row.findElements(By.cssSelector("button"));
            for (WebElement b : btns) System.out.println("  qator tugmasi: '" + clean(b.getText()) + "' disabled=" + b.getAttribute("disabled"));
            WebElement sell = null;
            for (WebElement b : btns) if (clean(b.getText()).contains("Savdoga")) sell = b;
            if (sell == null && !btns.isEmpty()) sell = btns.get(0);
            js.executeScript("arguments[0].scrollIntoView({block:'center'})", sell);
            sell.click();
            System.out.println(">>> '" + clean(sell.getText()) + "' bosildi");
            Thread.sleep(3000);
            dumpDialogs("1");
            shot("savdoga_chiqarish_oynasi");
            if (!Boolean.getBoolean("submit")) return;
            // Lot sonini kiritamiz
            String lots = System.getProperty("lots", "100");
            for (WebElement e : d.findElements(By.cssSelector("[role=dialog] input[type=text], [role=dialog] input[type=number], [class*=modal] input[type=text], [class*=modal] input[type=number]")))
                try {
                    String nm = (e.getAttribute("name") + e.getAttribute("placeholder") + e.getAttribute("id")).toLowerCase();
                    if (e.isDisplayed() && e.isEnabled() && (nm.contains("lot") || nm.contains("miqdor") || nm.contains("amount") || nm.contains("count") || nm.contains("hajm"))) {
                        e.sendKeys(Keys.CONTROL + "a"); e.sendKeys(lots);
                        System.out.println("  " + nm + " = " + lots);
                    }
                } catch (Exception ignore) {}
            shot("savdoga_chiqarish_toldirildi");
            WebElement ok = null;
            for (WebElement b : d.findElements(By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button")))
                try { String t = b.getText().trim(); if (b.isDisplayed() && (t.contains("Savdoga") || t.equals("Yuborish") || t.equals("Tasdiqlash") || t.equals("Saqlash"))) ok = b; } catch (Exception ignore) {}
            if (ok == null) { System.out.println("Oynada yuborish tugmasi topilmadi"); return; }
            System.out.println(">>> Oynadagi '" + ok.getText() + "' bosiladi");
            ok.click();
            Eimzo.typePassword(10);
            for (int s = 0; s < 6; s++) { Thread.sleep(1000); dumpDialogs("2." + s); }
            shot("savdoga_chiqarildi");
        } finally {
            d.quit();
        }
    }
}
