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

/** VAQTINCHALIK: Treyder 2 savdo vaqtida mahsulotga taklif beradi (ekranda). */
public class ProbeBuy {
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
        for (WebElement e : d.findElements(By.cssSelector("[role=dialog] input, [class*=modal] input")))
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
        BaseTest.grantEimzo(cd, uz.ebirja.base.BaseTest.saytlar());
        d = cd; js = cd;
        String base = p.getProperty("trader.url");
        try {
            loginKey(base, p.getProperty("trader2.login"));
            d.get(base + "/uz/trader/sales/auction-sales?tradeTab=SELL");
            Thread.sleep(6000);
            for (WebElement t : d.findElements(By.xpath("//*[normalize-space(text())='Sotib olish']")))
                try { if (t.isDisplayed()) { t.click(); System.out.println("'Sotib olish' tabi bosildi"); break; } } catch (Exception ignore) {}
            Thread.sleep(5000);
            // sessiya holati
            for (WebElement e : d.findElements(By.xpath("//*[contains(text(),'AMALDAGI') or contains(text(),'KEYINGI')]/..")))
                try { if (e.isDisplayed()) System.out.println("SESSIYA: " + clean(e.getText())); } catch (Exception ignore) {}
            // qidiruv
            for (WebElement s : d.findElements(By.cssSelector("main input[placeholder='Qidirish']")))
                try { if (s.isDisplayed()) { s.sendKeys(product); System.out.println("Qidiruvga yozildi"); Thread.sleep(3000); break; } } catch (Exception ignore) {}
            System.out.println("URL: " + d.getCurrentUrl() + "  soat " + java.time.LocalTime.now());
            List<String> btn = new ArrayList<>();
            for (WebElement t : d.findElements(By.cssSelector("main button"))) try { if (t.isDisplayed() && !t.getText().isBlank() && t.getText().length() < 40) btn.add(clean(t.getText())); } catch (Exception ignore) {}
            System.out.println("Tugmalar: " + btn);
            List<String> th = new ArrayList<>();
            for (WebElement e : d.findElements(By.cssSelector("main thead th"))) th.add(clean(e.getText()));
            System.out.println("Ustunlar: " + th);
            WebElement row = null;
            List<WebElement> rows = d.findElements(By.cssSelector("main tbody tr"));
            System.out.println("Qatorlar: " + rows.size());
            for (WebElement r : rows) {
                if (rows.indexOf(r) < 5) System.out.println("   " + clean(r.getText()).substring(0, Math.min(250, clean(r.getText()).length())));
                if (r.getText().contains(product)) row = r;
            }
            shot("xarid_sahifa");
            if (row == null) {
                // Kartochka ko'rinishi: mahsulot nomi yozilgan element -> kartochka -> belgilar
                WebElement name = null;
                for (WebElement e : d.findElements(By.xpath("//*[contains(text(),'" + product + "')]"))) try { if (e.isDisplayed()) { name = e; break; } } catch (Exception ignore) {}
                if (name == null) { System.out.println("Mahsulot savdoda topilmadi"); return; }
                WebElement card = name;
                for (int up = 0; up < 15; up++) {
                    card = card.findElement(By.xpath("./.."));
                    if (card.getText().contains(" lot") && card.findElements(By.cssSelector("svg")).size() >= 3) break;
                }
                System.out.println(">>> Kartochka: " + clean(card.getText()));
                List<WebElement> icons = new ArrayList<>();
                for (WebElement e : card.findElements(By.cssSelector("svg"))) try { if (e.isDisplayed()) icons.add(e); } catch (Exception ignore) {}
                System.out.println("  belgilar soni: " + icons.size());
                if (Boolean.getBoolean("submit")) {
                    // 🔨 = "Ariza yuborish (F4)": o'ngda panel ochiladi
                    WebElement ic = icons.get(2);
                    WebElement clickable;
                    try { clickable = ic.findElement(By.xpath("./ancestor-or-self::*[self::button or @role='button' or contains(@class,'cursor-pointer')][1]")); }
                    catch (Exception e) { clickable = ic.findElement(By.xpath("./..")); }
                    try { clickable.click(); } catch (Exception e) { js.executeScript("arguments[0].dispatchEvent(new MouseEvent('click',{bubbles:true}))", ic); }
                    System.out.println(">>> 'Ariza yuborish' bosildi");
                    Thread.sleep(2500);
                    // Bir nechta panel bo'lishi mumkin: "Sotib olish" tugmasi bor eng oxirgi panelni olamiz
                    WebElement panel = null;
                    for (WebElement b : d.findElements(By.xpath("//button[normalize-space(.)='Sotib olish']")))
                        try { if (b.isDisplayed()) panel = b.findElement(By.xpath("./ancestor::*[.//input[@placeholder='Miqdor']][1]")); } catch (Exception ignore) {}
                    System.out.println("  Panel: " + clean(panel.getText()).substring(0, Math.min(150, clean(panel.getText()).length())));
                    // Mijoz: "Mijoz" yozuvli react-select
                    WebElement mijoz = null;
                    for (WebElement in : panel.findElements(By.cssSelector("input[id^='react-select']")))
                        try {
                            Object txt = js.executeScript("let c=arguments[0].closest('[class*=\"-container\"]'); return c? c.innerText : '';", in);
                            if (in.isDisplayed() && String.valueOf(txt).contains("Mijoz")) mijoz = in;
                        } catch (Exception ignore) {}
                    if (mijoz != null) {
                        mijoz.sendKeys(Keys.ARROW_DOWN);
                        Thread.sleep(1500);
                        List<WebElement> opts = new ArrayList<>();
                        for (WebElement o : d.findElements(By.cssSelector("[id*='-option-']"))) if (o.isDisplayed()) opts.add(o);
                        List<String> names = new ArrayList<>();
                        for (WebElement o : opts) names.add(clean(o.getText()));
                        System.out.println("  Mijozlar: " + names);
                        WebElement pick = opts.get(0);
                        for (WebElement o : opts) if (o.getText().contains("IBOD-AZIZ")) pick = o;
                        System.out.println("  Mijoz tanlandi: " + clean(pick.getText()));
                        pick.click();
                        Thread.sleep(800);
                    } else System.out.println("  Mijoz maydoni topilmadi");
                    WebElement miqdor = panel.findElement(By.cssSelector("input[placeholder='Miqdor']"));
                    js.executeScript("arguments[0].scrollIntoView({block:'center'}); arguments[0].focus();", miqdor);
                    miqdor.sendKeys(Keys.CONTROL + "a"); miqdor.sendKeys(System.getProperty("lots", "1"));
                    System.out.println("  Miqdor = " + miqdor.getAttribute("value"));
                    shot("taklif_toldirildi");
                    WebElement buy = panel.findElement(By.xpath(".//button[normalize-space(.)='Sotib olish']"));
                    js.executeScript("arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", buy);
                    System.out.println(">>> 'Sotib olish' bosildi");
                    Eimzo.typePassword(8);
                    for (int s = 0; s < 6; s++) {
                        Thread.sleep(800);
                        for (WebElement e : d.findElements(By.cssSelector("[class*=toast], [class*=Toastify], [role=alert], [role=status]")))
                            try { if (e.isDisplayed() && !e.getText().isBlank()) System.out.println("  XABAR: " + clean(e.getText())); } catch (Exception ignore) {}
                    }
                    // takliflar daftarini ko'ramiz (✋)
                    try { icons.get(3).findElement(By.xpath("./..")).click(); } catch (Exception ignore) {}
                    Thread.sleep(2000);
                    shot("taklif_natija");
                    String mt = clean(d.findElement(By.tagName("main")).getText());
                    int k = mt.indexOf("Talab");
                    System.out.println("  TAKLIFLAR: " + (k >= 0 ? mt.substring(k, Math.min(mt.length(), k + 400)) : mt.substring(Math.max(0, mt.length() - 400))));
                    return;
                }
                boolean dlg = false;
                for (int idx : new int[]{2, 3}) {
                    WebElement ic = icons.get(idx);
                    WebElement clickable;
                    try { clickable = ic.findElement(By.xpath("./ancestor-or-self::*[self::button or @role='button' or contains(@class,'cursor-pointer')][1]")); }
                    catch (Exception e) { clickable = ic.findElement(By.xpath("./..")); }
                    js.executeScript("arguments[0].scrollIntoView({block:'center'})", clickable);
                    try { clickable.click(); } catch (Exception e) { js.executeScript("arguments[0].dispatchEvent(new MouseEvent('click',{bubbles:true}))", ic); }
                    System.out.println(">>> " + idx + "-belgi bosildi");
                    Thread.sleep(2500);
                    dumpDialogs("B" + idx);
                    shot("taklif_belgi_" + idx);
                    for (WebElement m : d.findElements(By.cssSelector("[role=dialog] input, [class*=modal] input"))) try { if (m.isDisplayed()) dlg = true; } catch (Exception ignore) {}
                    if (dlg) break;
                }
                if (dlg && Boolean.getBoolean("submit")) {
                    for (WebElement e : d.findElements(By.cssSelector("[role=dialog] input, [class*=modal] input")))
                        try {
                            String t = e.getAttribute("type");
                            if (e.isDisplayed() && e.isEnabled() && e.getAttribute("readonly") == null && ("text".equals(t) || "number".equals(t)) && !e.getAttribute("id").startsWith("react-select")) {
                                String nm = (e.getAttribute("name") + "|" + e.getAttribute("placeholder")).toLowerCase();
                                if (nm.contains("narx") || nm.contains("price") || nm.contains("summa")) continue;
                                e.click(); e.sendKeys(Keys.CONTROL + "a"); e.sendKeys(System.getProperty("lots", "1"));
                                System.out.println("  " + nm + " = " + e.getAttribute("value"));
                            }
                        } catch (Exception ignore) {}
                    WebElement ok = null;
                    for (WebElement b : d.findElements(By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button")))
                        try { String t = b.getText().trim(); if (b.isDisplayed() && t.matches("(?i).*(yuborish|tasdiqlash|taklif|sotib|saqlash|ariza).*")) ok = b; } catch (Exception ignore) {}
                    if (ok != null) {
                        System.out.println(">>> '" + clean(ok.getText()) + "' bosiladi");
                        ok.click();
                        Eimzo.typePassword(8);
                        for (int s = 0; s < 5; s++) { Thread.sleep(1000); dumpDialogs("T" + s); }
                        shot("taklif_natija");
                    } else System.out.println("Oynada yuborish tugmasi topilmadi");
                }
                String rightPanel = clean(d.findElement(By.tagName("main")).getText());
                System.out.println("  SAHIFA: " + rightPanel.substring(0, Math.min(1200, rightPanel.length())));
                for (WebElement e : d.findElements(By.cssSelector("main input")))
                    try { if (e.isDisplayed()) System.out.println("    sahifa maydoni: name=" + e.getAttribute("name") + " id=" + e.getAttribute("id") + " ph=" + e.getAttribute("placeholder") + " val=" + e.getAttribute("value")); } catch (Exception ignore) {}
                List<String> bb = new ArrayList<>();
                for (WebElement b : d.findElements(By.cssSelector("main button"))) try { if (b.isDisplayed() && !b.getText().isBlank()) bb.add(clean(b.getText())); } catch (Exception ignore) {}
                System.out.println("  sahifa tugmalari: " + bb);
                shot("taklif_oynasi");
                return;
            }
            System.out.println(">>> Mahsulot topildi: " + clean(row.getText()));
            List<WebElement> rb = row.findElements(By.cssSelector("button"));
            for (WebElement b : rb) System.out.println("  qator tugmasi: '" + clean(b.getText()) + "'");
            WebElement bid = rb.isEmpty() ? row : rb.get(rb.size() - 1);
            for (WebElement b : rb) if (clean(b.getText()).matches("(?i).*(taklif|sotib|xarid|ariza).*")) bid = b;
            js.executeScript("arguments[0].scrollIntoView({block:'center'})", bid);
            bid.click();
            Thread.sleep(3000);
            dumpDialogs("1");
            shot("taklif_oynasi");
            if (!Boolean.getBoolean("submit")) return;
            // lot soni
            for (WebElement e : d.findElements(By.cssSelector("[role=dialog] input[type=text], [class*=modal] input[type=text], [role=dialog] input[type=number], [class*=modal] input[type=number]")))
                try {
                    String nm = (e.getAttribute("name") + "|" + e.getAttribute("placeholder")).toLowerCase();
                    if (e.isDisplayed() && e.isEnabled() && e.getAttribute("readonly") == null && (nm.contains("lot") || nm.contains("miqdor") || nm.contains("amount") || nm.contains("count"))) {
                        e.click(); e.sendKeys(Keys.CONTROL + "a"); e.sendKeys(System.getProperty("lots", "1"));
                        System.out.println("  " + nm + " = " + e.getAttribute("value"));
                    }
                } catch (Exception ignore) {}
            WebElement ok = null;
            for (WebElement b : d.findElements(By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button")))
                try { String t = b.getText().trim(); if (b.isDisplayed() && t.matches("(?i).*(yuborish|tasdiqlash|taklif|sotib olish|saqlash).*")) ok = b; } catch (Exception ignore) {}
            if (ok == null) { System.out.println("Oynada yuborish tugmasi topilmadi"); return; }
            System.out.println(">>> '" + ok.getText() + "' bosiladi");
            ok.click();
            Eimzo.typePassword(8);
            for (int s = 0; s < 5; s++) { Thread.sleep(1000); dumpDialogs("2." + s); }
            shot("taklif_berildi");
        } finally {
            d.quit();
        }
    }
}
