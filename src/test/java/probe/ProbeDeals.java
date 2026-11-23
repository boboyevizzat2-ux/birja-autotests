package probe;

import org.openqa.selenium.*;
import org.testng.annotations.Test;
import uz.ebirja.base.BaseTest;
import uz.ebirja.base.Eimzo;
import uz.ebirja.config.Config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static uz.ebirja.base.Ui.*;

/**
 * VAQTINCHALIK o'rganish: bitimlar, bekor qilish, muddat uzaytirish, YBHR, balans, moderator kontrakt sahifalari.
 * HECH NARSANI TASDIQLAMAYDI: faqat menyu va oynalarni ochib, yopadi (sahifa qayta yuklanadi).
 */
public class ProbeDeals extends BaseTest {
    static final String OUT = "target/probe-deals";
    static final String[] XAVFLI = {"Tasdiqlash", "Imzolash", "Rad etish", "O'chirish", "Qabul qilish", "Yuborish", "Saqlash", "To'lash", "Ommaviy"};
    PrintWriter log;
    int n = 0;

    void p(String s) { System.out.println(s); log.println(s); log.flush(); }

    void shot(String name) {
        try {
            File s = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Files.copy(s.toPath(), Paths.get(OUT, String.format("%02d_%s.png", ++n, name.replaceAll("[^A-Za-z0-9_]", "_"))), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignore) {}
    }

    JavascriptExecutor js() { return (JavascriptExecutor) driver; }

    String mainText() {
        try { return driver.findElement(By.tagName("main")).getText(); } catch (Exception e) { return driver.findElement(By.tagName("body")).getText(); }
    }

    /** E-IMZO oynasi tasodifan ochilsa — parol yozmasdan yopamiz. */
    void eimzoYop() {
        if (Eimzo.windowTitle().isEmpty()) return;
        p("!!! E-IMZO oynasi ochildi — ESC bilan yopilyapti");
        try {
            java.awt.Robot r = new java.awt.Robot();
            new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                    new File("src/test/resources/eimzo-window.ps1").getAbsolutePath(), "focus").start().waitFor();
            r.delay(400);
            r.keyPress(java.awt.event.KeyEvent.VK_ESCAPE);
            r.keyRelease(java.awt.event.KeyEvent.VK_ESCAPE);
            r.delay(800);
        } catch (Exception ignore) {}
    }

    void page(String url, String label) {
        driver.get(url);
        sleep(6000);
        p("\n================ " + label + " | " + driver.getCurrentUrl());
        String t = mainText();
        p(t.length() > 3500 ? t.substring(0, 3500) + " …" : t);
        p("INPUTLAR: " + inputs(By.cssSelector("main input, main select, main textarea")));
        shot(label);
    }

    String inputs(By by) {
        List<String> r = new ArrayList<>();
        for (WebElement e : driver.findElements(by))
            try {
                if (!e.isDisplayed() && !"file".equals(e.getAttribute("type"))) continue;
                r.add(e.getTagName() + "[" + e.getAttribute("type") + " name=" + e.getAttribute("name") + " ph=" + e.getAttribute("placeholder") + "]");
            } catch (Exception ignore) {}
        return r.toString();
    }

    void tab(String name) {
        WebElement b = visible(driver, By.xpath("//main//*[self::button or @role='tab' or self::span or self::div][normalize-space(text())='" + name + "']"));
        if (b == null) { p("TAB yo'q: " + name); return; }
        jsClick(driver, b);
        sleep(4000);
        p("\n---- TAB: " + name);
        String t = mainText();
        p(t.length() > 3000 ? t.substring(0, 3000) + " …" : t);
        shot("tab_" + name);
    }

    List<String> dialogs() {
        List<String> r = new ArrayList<>();
        for (WebElement e : driver.findElements(By.cssSelector("[role=dialog], .modal, [class*=modal], [class*=Modal], [class*=drawer]")))
            try { if (e.isDisplayed() && !e.getText().isBlank()) r.add(clean(e.getText())); } catch (Exception ignore) {}
        return r;
    }

    /** Jadvaldagi qatorning amallar tugmalarini bosib, chiqqan menyu bandlarini va (xavfsiz bo'lsa) ularning oynalarini yozadi. */
    String search = null;

    void openWithSearch(String url, String tabName) {
        driver.get(url);
        sleep(6000);
        tosish();
        if (tabName != null) { WebElement b = visible(driver, By.xpath("//main//*[normalize-space(text())='" + tabName + "']")); if (b != null) { jsClick(driver, b); sleep(4000); } }
        if (search != null) { WebElement s = visible(driver, By.cssSelector("main input[placeholder='Qidirish']")); if (s != null) { s.sendKeys(search); sleep(4000); } }
    }

    /** E-IMZO imzo so'rovlarini to'sadi: o'rganish vaqtida hech narsa imzolanmaydi. */
    void tosish() {
        js().executeScript("if(!window.__p){window.__p=true;const s=WebSocket.prototype.send;WebSocket.prototype.send=function(d){if(/127.0.0.1|localhost/.test(this.url)&&/pkcs7|sign|load_key|create/i.test(String(d))){window.__imzo=(window.__imzo||0)+1;return;}return s.call(this,d);};}");
    }

    void rowActions(String url, String tabName, int rowIdx, String label) {
        openWithSearch(url, tabName);
        List<WebElement> rows = driver.findElements(By.cssSelector("main tbody tr"));
        if (rows.size() <= rowIdx) { p("QATOR yo'q: " + label + " #" + rowIdx); return; }
        WebElement row = rows.get(rowIdx);
        p("\n######## QATOR " + label + " #" + rowIdx + ": " + clean(row.getText()));
        List<WebElement> tds = row.findElements(By.cssSelector("td"));
        List<WebElement> btns = tds.get(tds.size() - 1).findElements(By.cssSelector("button, a, svg"));
        p("Oxirgi ustundagi elementlar: " + btns.size());
        List<String> items = new ArrayList<>();
        WebElement menuBtn = null;
        for (WebElement b : tds.get(tds.size() - 1).findElements(By.cssSelector("button"))) {
            Set<String> before = new HashSet<>(visibleTexts());
            jsClick(driver, b);
            sleep(1500);
            List<String> after = visibleTexts();
            after.removeAll(before);
            p("  tugma (title=" + b.getAttribute("title") + " aria=" + b.getAttribute("aria-label") + ") -> yangi: " + after + " | oynalar: " + dialogs());
            eimzoYop();
            if (!after.isEmpty() && dialogs().isEmpty()) { menuBtn = b; items.addAll(after); }
            driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
            sleep(500);
            if (!dialogs().isEmpty()) { shot(label + "_dialog"); driver.navigate().refresh(); sleep(5000); break; }
        }
        shot(label + "_qator");
        // menyu bandlarini birma-bir ochib ko'ramiz
        for (String it : items) {
            if (it.length() > 60) continue;
            boolean xavfli = false;
            for (String x : XAVFLI) if (it.startsWith(x) || it.equals(x)) xavfli = true;
            if (xavfli) { p("    [o'tkazildi, xavfli] " + it); continue; }
            openWithSearch(url, tabName);
            try {
                WebElement r2 = driver.findElements(By.cssSelector("main tbody tr")).get(rowIdx);
                List<WebElement> t2 = r2.findElements(By.cssSelector("td"));
                for (WebElement b : t2.get(t2.size() - 1).findElements(By.cssSelector("button"))) {
                    jsClick(driver, b); sleep(1200);
                    WebElement mi = lastVisible(driver, By.xpath("//*[normalize-space(text())=\"" + it + "\"]"));
                    if (mi == null) { driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE); continue; }
                    String urlBefore = driver.getCurrentUrl();
                    jsClick(driver, mi); sleep(2500);
                    eimzoYop();
                    p("    BAND '" + it + "' -> url=" + (driver.getCurrentUrl().equals(urlBefore) ? "(o'sha)" : driver.getCurrentUrl()));
                    p("      oyna: " + dialogs());
                    p("      maydonlar: " + inputs(By.cssSelector("[role=dialog] input, [role=dialog] textarea, [class*=modal] input, [class*=modal] textarea")));
                    if (!driver.getCurrentUrl().equals(urlBefore)) { String t = mainText(); p("      sahifa: " + cut(t, 2500)); }
                    shot(label + "_" + it);
                    break;
                }
            } catch (Exception e) { p("    xato: " + e.getMessage().split("\n")[0]); }
        }
    }

    List<String> visibleTexts() {
        List<String> r = new ArrayList<>();
        for (WebElement e : driver.findElements(By.xpath("//body//*[not(*) and string-length(normalize-space(text()))>1]")))
            try { if (e.isDisplayed()) r.add(e.getText().trim()); } catch (Exception ignore) {}
        return r;
    }

    void sidebar(String label) {
        p("\n---- MENYU HAVOLALARI (" + label + ")");
        // barcha yopiq bo'limlarni ochamiz
        for (WebElement e : driver.findElements(By.cssSelector("aside button, nav button")))
            try { if (e.isDisplayed()) { jsClick(driver, e); sleep(150); } } catch (Exception ignore) {}
        Set<String> s = new LinkedHashSet<>();
        for (WebElement a : driver.findElements(By.cssSelector("a[href]")))
            try { String h = a.getAttribute("href"); String t = clean(a.getText()); if (h.contains("ebirja") && !t.isEmpty()) s.add(t + " -> " + h); } catch (Exception ignore) {}
        s.forEach(this::p);
    }

    /** B26000255: sotuvchi va xaridor (mijoz shartnomalari) tomonidan. */
    @Test
    public void probe5() throws Exception {
        new File(OUT).mkdirs();
        log = new PrintWriter(new OutputStreamWriter(new FileOutputStream(OUT + "/log5.txt"), StandardCharsets.UTF_8));
        openBrowser();
        try {
            String t = Config.traderUrl();
            String no = System.getProperty("bitim", "B26000255");
            loginByKey(driver, t, Config.get("trader1.login"), true);
            search = no;
            openWithSearch(t + "/uz/trader/contracts/brokers-contracts", "Sotish");
            List<String> th = new ArrayList<>();
            for (WebElement e : driver.findElements(By.cssSelector("main thead th"))) th.add(clean(e.getText()));
            p("USTUNLAR: " + th);
            for (WebElement r : driver.findElements(By.cssSelector("main tbody tr"))) p("T1 Sotish: " + clean(r.getText()));
            loginByKey(driver, t, Config.get("trader2.login"), true);
            openWithSearch(t + "/uz/trader/contracts/client-contracts", null);
            p("T2 client-contracts tablari: " + cut(mainText(), 600));
            for (WebElement r : driver.findElements(By.cssSelector("main tbody tr"))) p("T2 mijoz: " + clean(r.getText()));
            shot("T2_client_contracts");
            search = null;
            driver.get(t + "/uz/trader/sales/auction-sales?tradeTab=SELL"); sleep(5000);
            WebElement h = visible(driver, By.xpath("//*[normalize-space(text())='Arizalar tarixi']"));
            if (h != null) { jsClick(driver, h); sleep(5000); p("ARIZALAR TARIXI: " + cut(mainText(), 3000)); shot("T2_arizalar_tarixi"); }
        } finally { log.close(); closeBrowser(); }
    }

    /** Bugungi bitimlarni sanash (100 qatorli sahifa). */
    @Test
    public void probe4() throws Exception {
        new File(OUT).mkdirs();
        log = new PrintWriter(new OutputStreamWriter(new FileOutputStream(OUT + "/log4.txt"), StandardCharsets.UTF_8));
        openBrowser();
        try {
            String t = Config.traderUrl();
            loginByKey(driver, t, Config.get("trader1.login"), true);
            openWithSearch(t + "/uz/trader/contracts/brokers-contracts", "Sotish");
            sahifada100(driver);
            int n = 0;
            for (WebElement r : driver.findElements(By.cssSelector("main tbody tr"))) {
                String tx = clean(r.getText());
                if (tx.startsWith("23/11/2026") && tx.contains("Асбестовый")) { n++; p(cut(tx, 90)); }
            }
            p("Jami bugungi Асбестовый шнур bitimlari: " + n + ", jadvaldagi qatorlar: " + driver.findElements(By.cssSelector("main tbody tr")).size());
        } finally { log.close(); closeBrowser(); }
    }

    /** 3-bosqich: bekor qilish turlari, balans, so'rovlar sahifalari. */
    @Test
    public void probe3() throws Exception {
        new File(OUT).mkdirs();
        log = new PrintWriter(new OutputStreamWriter(new FileOutputStream(OUT + "/log3.txt"), StandardCharsets.UTF_8));
        openBrowser();
        try {
            String t = Config.traderUrl();
            loginByKey(driver, t, Config.get("trader2.login"), true);
            page(t + "/uz/trader/hkkp/transaction", "T2_balans");
            for (String r : new String[]{"payment-request", "delivered-request", "canceled-request"}) page(t + "/uz/trader/contracts/" + r, "T2_" + r);
            search = "B26000243";
            openWithSearch(t + "/uz/trader/contracts/brokers-contracts", "Sotib olish");
            WebElement row = driver.findElements(By.cssSelector("main tbody tr")).get(0);
            List<WebElement> tds = row.findElements(By.cssSelector("td"));
            jsClick(driver, tds.get(tds.size() - 1).findElement(By.cssSelector("button"))); sleep(1200);
            jsClick(driver, lastVisible(driver, By.xpath("//*[normalize-space(text())='Bitimni bekor qilish']"))); sleep(2500);
            for (WebElement in : driver.findElements(By.cssSelector("[role=dialog] input[id^='react-select'], [class*=modal] input[id^='react-select']"))) {
                try { in.sendKeys(Keys.ARROW_DOWN); sleep(1500); List<String> o = new ArrayList<>();
                    for (WebElement op : driver.findElements(By.cssSelector("[id*='-option-']"))) if (op.isDisplayed()) o.add(clean(op.getText()));
                    p("BEKOR QILISH TURI variantlari: " + o); shot("bekor_turlari"); in.sendKeys(Keys.ESCAPE); } catch (Exception ignore) {}
            }
            p("Bekor qilish oynasi HTML: " + cut(String.valueOf(js().executeScript("let d=document.querySelector('[role=dialog]')||document.querySelector('[class*=modal]'); return d? d.innerText : '';")), 800));
            search = null;
            loginByKey(driver, t, Config.get("trader1.login"), true);
            page(t + "/uz/trader/hkkp/transaction", "T1_balans");
        } finally { log.close(); closeBrowser(); }
    }

    /** 2-bosqich: bugungi faol bitimlar menyulari, filtrlar, HKKP bo'limi. */
    @Test
    public void probe2() throws Exception {
        new File(OUT).mkdirs();
        log = new PrintWriter(new OutputStreamWriter(new FileOutputStream(OUT + "/log2.txt"), StandardCharsets.UTF_8));
        openBrowser();
        try {
            String t = Config.traderUrl();
            String c = t + "/uz/trader/contracts/brokers-contracts";
            loginByKey(driver, t, Config.get("trader1.login"), true);
            menyuGuruhlari();
            search = "B26000241";
            rowActions(c, "Sotish", 0, "T1_B241");
            search = null;
            filtrVariantlari(c, "Sotish");
            page(t + "/uz/trader/hkkp/ybhr", "T1_ybhr");
            tosish();
            WebElement yb = visible(driver, By.xpath("//main//button[contains(normalize-space(.),'YBHR yuborish')]"));
            if (yb != null) { jsClick(driver, yb); sleep(3000); p("YBHR yuborish oynasi: " + dialogs()); p("  maydonlar: " + inputs(By.cssSelector("[role=dialog] input, [role=dialog] textarea, [class*=modal] input, [class*=modal] textarea"))); shot("T1_ybhr_oyna"); }
            loginByKey(driver, t, Config.get("trader2.login"), true);
            search = "B26000242";
            rowActions(c, "Sotib olish", 0, "T2_B242");
            search = null;
        } finally {
            log.close();
            closeBrowser();
        }
    }

    /** Treyder menyusidagi guruhlarni (Shartnomalar, HKKP, Savdolar) ochib, ichidagi havolalarni yozadi. */
    void menyuGuruhlari() {
        for (String g : new String[]{"Shartnomalar", "HKKP", "Savdolar"}) {
            WebElement e = visible(driver, By.xpath("//aside//*[normalize-space(text())='" + g + "'] | //nav//*[normalize-space(text())='" + g + "']"));
            if (e == null) { p("Menyu guruhi topilmadi: " + g); continue; }
            jsClick(driver, e); sleep(1500);
        }
        sidebar("guruhlar ochilgan");
    }

    /** "Bitim holati" va "Shartnoma turi" filtrlarining variantlari. */
    void filtrVariantlari(String url, String tab) {
        openWithSearch(url, tab);
        for (WebElement in : driver.findElements(By.cssSelector("main input[id^='react-select']"))) {
            try {
                String lbl = String.valueOf(js().executeScript("let c=arguments[0].closest('[class*=\"-container\"]'); return c? c.innerText : '';", in));
                in.sendKeys(Keys.ARROW_DOWN); sleep(1500);
                List<String> o = new ArrayList<>();
                for (WebElement op : driver.findElements(By.cssSelector("[id*='-option-']"))) if (op.isDisplayed()) o.add(clean(op.getText()));
                p("FILTR '" + clean(lbl) + "': " + o);
                in.sendKeys(Keys.ESCAPE); sleep(500);
            } catch (Exception ignore) {}
        }
    }

    @Test
    public void probe() throws Exception {
        new File(OUT).mkdirs();
        log = new PrintWriter(new OutputStreamWriter(new FileOutputStream(OUT + "/log.txt"), StandardCharsets.UTF_8));
        openBrowser();
        try {
            String t = Config.traderUrl();
            // ================= TREYDER 1 (sotuvchi)
            loginByKey(driver, t, Config.get("trader1.login"), true);
            sidebar("treyder");
            String c = t + "/uz/trader/contracts/brokers-contracts";
            page(c, "T1_mening_shartnomalarim");
            tab("Sotish");
            for (int i = 0; i < 3; i++) rowActions(c, "Sotish", i, "T1_sotish" + i);
            // statuslar ro'yxati (filtr)
            page(t + "/uz/trader/hkkp/balance", "T1_balans");
            page(t + "/uz/trader/hkkp/ybhr", "T1_ybhr");
            // kontrakt formasi: maydon cheklovlari (faqat ko'rish, yuborilmaydi)
            page(t + "/uz/trader/listing/sales/create", "T1_kontrakt_forma");
            for (String nm : new String[]{"mainPrice", "amount", "sellerDepositPercentage", "buyerDepositPercentage"}) {
                WebElement e = visible(driver, By.name(nm));
                if (e == null) continue;
                p("  " + nm + ": type=" + e.getAttribute("type") + " min=" + e.getAttribute("min") + " max=" + e.getAttribute("max") + " pattern=" + e.getAttribute("pattern"));
                e.sendKeys("-5");
                sleep(400);
                p("    '-5' yozilgandan keyin qiymat: '" + e.getAttribute("value") + "'");
                e.sendKeys(Keys.TAB);
                sleep(600);
            }
            p("  forma xabarlari: " + cut(mainText(), 3000));
            shot("T1_forma_minus");

            // ================= TREYDER 2 (xaridor)
            loginByKey(driver, t, Config.get("trader2.login"), true);
            page(c, "T2_mening_shartnomalarim");
            tab("Sotib olish");
            for (int i = 0; i < 3; i++) rowActions(c, "Sotib olish", i, "T2_sotibolish" + i);
            page(t + "/uz/trader/hkkp/balance", "T2_balans");

            // ================= ADMIN
            String a = Config.adminUrl();
            loginByKey(driver, a, Config.get("admin2.login"), false);
            page(a + "/uz/moderationv2/contracts", "A_bitimlar");
            for (int i = 0; i < 2; i++) rowActions(a + "/uz/moderationv2/contracts", null, i, "A_bitim" + i);
            page(a + "/uz/moderationv2/listing/contracts", "A_kontraktlar");
            rowActions(a + "/uz/moderationv2/listing/contracts", null, 0, "A_kontrakt0");
        } finally {
            log.close();
            closeBrowser();
        }
    }
}
