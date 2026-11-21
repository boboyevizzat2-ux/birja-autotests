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

/** VAQTINCHALIK: admin savdo e'lon qilish sahifalarini o'rganish (ekranda). */
public class ProbeSession {
    WebDriver d; JavascriptExecutor js; Properties p = new Properties(); String out;

    void shot(String name) throws Exception {
        File s = ((TakesScreenshot) d).getScreenshotAs(OutputType.FILE);
        Files.copy(s.toPath(), Paths.get(out, name + ".png"), StandardCopyOption.REPLACE_EXISTING);
    }

    String clean(String s) { return s == null ? "" : s.replace("\n", " | ").replaceAll("\\s+", " ").trim(); }

    void loginKey(String base, String jsh, boolean trader) throws Exception {
        d.get(base + "/uz/auth/loginkey");
        Thread.sleep(3000);
        d.manage().deleteAllCookies();
        js.executeScript("localStorage.clear(); sessionStorage.clear();");
        d.get(base + "/uz/auth/loginkey");
        Thread.sleep(6000);
        if (trader) { d.findElement(By.xpath("//button[contains(normalize-space(.),'Birja savdolari')]")).click(); Thread.sleep(800); }
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

    void describe(String url, String label) throws Exception {
        d.get(url);
        Thread.sleep(6000);
        System.out.println("\n### " + label + "  " + d.getCurrentUrl());
        Set<String> btn = new LinkedHashSet<>();
        for (WebElement e : d.findElements(By.cssSelector("main button, main [role=tab]")))
            try { if (e.isDisplayed()) { String t = clean(e.getText()); if (!t.isEmpty() && t.length() < 50) btn.add(t); } } catch (Exception ignore) {}
        System.out.println("  Tugmalar: " + btn);
        for (WebElement e : d.findElements(By.cssSelector("main label")))
            try { if (e.isDisplayed() && !e.getText().isBlank()) System.out.println("  label: " + clean(e.getText())); } catch (Exception ignore) {}
        for (WebElement e : d.findElements(By.cssSelector("main input, main select, main textarea")))
            try { if (e.isDisplayed()) System.out.println("  input: type=" + e.getAttribute("type") + " name=" + e.getAttribute("name") + " id=" + e.getAttribute("id") + " ph=" + e.getAttribute("placeholder") + " val=" + e.getAttribute("value")); } catch (Exception ignore) {}
        List<String> th = new ArrayList<>();
        for (WebElement e : d.findElements(By.cssSelector("main thead th"))) th.add(clean(e.getText()));
        if (!th.isEmpty()) System.out.println("  Ustunlar: " + th);
        List<WebElement> rows = d.findElements(By.cssSelector("main tbody tr"));
        for (int i = 0; i < Math.min(5, rows.size()); i++) System.out.println("  QATOR " + (i + 1) + ": " + clean(rows.get(i).getText()).substring(0, Math.min(300, clean(rows.get(i).getText()).length())));
        String m = clean(d.findElement(By.tagName("main")).getText());
        System.out.println("  MATN: " + m.substring(0, Math.min(900, m.length())));
        shot(label.replaceAll("[^A-Za-z0-9]", "_"));
    }

    void setTime(String id, String hhmm) throws Exception {
        WebElement in = d.findElement(By.id(id));
        WebElement wrap = in.findElement(By.xpath("./.."));
        wrap.click();
        Thread.sleep(1200);
        // Vaqt tanlagich: z-[9999] oynasi, ichida 2 ta ustun (soat, daqiqa)
        WebElement popup = null;
        for (WebElement e : d.findElements(By.cssSelector("div.fixed")))
            if (e.isDisplayed() && e.getAttribute("class").contains("z-[9999]")) popup = e;
        List<WebElement> cols = popup.findElements(By.cssSelector("div.flex-col"));
        String[] hm = hhmm.split(":");
        for (int c = 0; c < 2; c++) {
            WebElement btn = null;
            for (WebElement b : cols.get(c).findElements(By.cssSelector("button")))
                if (b.getText().trim().equals(hm[c])) { btn = b; break; }
            js.executeScript("arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", btn);
            Thread.sleep(500);
        }
        if (d.findElements(By.cssSelector("div.fixed")).stream().anyMatch(e -> { try { return e.isDisplayed() && e.getAttribute("class").contains("z-[9999]"); } catch (Exception x) { return false; } })) {
            // tanlagich yopilmagan bo'lsa, sarlavhani bosib yopamiz
            d.findElement(By.xpath("//*[normalize-space(text())='Savdoni tahrirlash']")).click();
            Thread.sleep(500);
        }
        System.out.println("    " + id + " = " + d.findElement(By.id(id)).getAttribute("value"));
    }

    /** Sessiya turi va seksiya matni bo'yicha qatorni topib, vaqtini o'zgartiradi. */
    void editRow(String session, String section, String range) throws Exception {
        d.navigate().refresh();
        Thread.sleep(5000);
        WebElement row = null;
        for (WebElement tr : d.findElements(By.cssSelector("main tbody tr"))) {
            List<WebElement> tds = tr.findElements(By.cssSelector("td"));
            if (tds.size() > 3 && tds.get(1).getText().contains(session) && tds.get(2).getText().contains(section)) { row = tr; break; }
        }
        if (row == null) { System.out.println("Qator topilmadi: " + session + " / " + section); return; }
        System.out.println("TAHRIR: " + clean(row.getText()) + "  ->  " + range);
        List<WebElement> tds = row.findElements(By.cssSelector("td"));
        WebElement dots = tds.get(tds.size() - 1).findElement(By.cssSelector("button"));
        js.executeScript("arguments[0].scrollIntoView({block:'center'})", dots);
        boolean opened = false;
        for (int t = 0; t < 3 && !opened; t++) {
            js.executeScript("arguments[0].click()", dots);
            Thread.sleep(1500);
            List<WebElement> cands = new ArrayList<>();
            for (WebElement e : d.findElements(By.xpath("//*[contains(text(),'ahrirlash') or contains(text(),'zgartirish')]")))
                try { if (e.isDisplayed()) cands.add(e); } catch (Exception ignore) {}
            System.out.println("  menyu bandlari: " + cands.size());
            for (int i = cands.size() - 1; i >= 0 && !opened; i--) {
                js.executeScript("arguments[0].click()", cands.get(i));
                for (int w = 0; w < 12 && !opened; w++) {
                    Thread.sleep(500);
                    opened = !d.findElements(By.id("startTime")).isEmpty();
                }
            }
        }
        if (!opened) { shot("tahrir_ochilmadi"); System.out.println("Tahrirlash oynasi ochilmadi"); return; }
        String[] se = range.split("-");
        setTime("startTime", se[0]);
        setTime("endTime", se[1]);
        WebElement save = null;
        for (WebElement b : d.findElements(By.xpath("//button[normalize-space(.)='Saqlash']"))) if (b.isDisplayed()) save = b;
        save.click();
        Eimzo.typePassword(6);
        for (int s = 0; s < 5; s++) {
            Thread.sleep(800);
            for (WebElement e : d.findElements(By.cssSelector("[class*=toast], [class*=Toastify], [role=alert], [role=status]")))
                try { if (e.isDisplayed() && !e.getText().isBlank()) System.out.println("  XABAR: " + clean(e.getText())); } catch (Exception ignore) {}
        }
    }

    @Test
    public void probe() throws Exception {
        try (Reader r = new InputStreamReader(new FileInputStream("login.properties"), StandardCharsets.UTF_8)) { p.load(r); }
        out = System.getProperty("probe.out");
        ChromeDriver cd = new ChromeDriver(BaseTest.chromeOptions());
        BaseTest.grantEimzo(cd, "https://test-app.ebirja.uz", "https://test-head.ebirja.uz");
        d = cd; js = cd;
        try {
            String ab = p.getProperty("admin.url");
            loginKey(ab, p.getProperty("admin2.login"), false);
            d.get(ab + "/uz/moderationv2/sales-schedule/auction-sales-2/trade-session");
            Thread.sleep(6000);
            String setElon = System.getProperty("elon", "");     // masalan 18:05-18:17
            String setSavdo = System.getProperty("savdo", "");   // masalan 18:17-18:22
            if (!setElon.isEmpty()) {
                editRow("Kechki sessiya", "e'lon", setElon);
                editRow("Kechki sessiya", "Boshqalar", setSavdo);
                d.navigate().refresh();
                Thread.sleep(5000);
                for (WebElement tr : d.findElements(By.cssSelector("main tbody tr"))) System.out.println("JADVAL: " + clean(tr.getText()));
                shot("sessiya_yangi_vaqtlar");
                return;
            }
            // 1-qator (Ertalabki e'lon) "⋮" menyusi
            for (int row = 1; row <= 2; row++) {
                WebElement tr = d.findElements(By.cssSelector("main tbody tr")).get(row - 1);
                List<WebElement> tds = tr.findElements(By.cssSelector("td"));
                tds.get(tds.size() - 1).findElement(By.cssSelector("button")).click();
                Thread.sleep(1200);
                List<String> items = new ArrayList<>();
                for (WebElement e : d.findElements(By.cssSelector("[role=menu] *, [role=menuitem], ul li, div[class*=dropdown] *")))
                    try { if (e.isDisplayed() && !e.getText().isBlank() && e.getText().length() < 40) items.add(clean(e.getText())); } catch (Exception ignore) {}
                System.out.println("QATOR " + row + " menyusi: " + new LinkedHashSet<>(items));
                shot("sessiya_menyu_" + row);
                // Tahrirlash bandi
                WebElement edit = null;
                for (WebElement e : d.findElements(By.xpath("//*[contains(normalize-space(text()),'Tahrirlash') or contains(normalize-space(text()),'O‘zgartirish') or contains(normalize-space(text()),\"O'zgartirish\")]")))
                    if (e.isDisplayed()) { edit = e; break; }
                if (edit == null) { d.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE); continue; }
                edit.click();
                Thread.sleep(2500);
                for (WebElement m : d.findElements(By.cssSelector("[role=dialog], [class*=modal], [class*=Modal]")))
                    try { if (m.isDisplayed()) System.out.println("  TAHRIR OYNASI: " + clean(m.getText())); } catch (Exception ignore) {}
                for (WebElement e : d.findElements(By.cssSelector("[role=dialog] input, [class*=modal] input")))
                    try { if (e.isDisplayed()) System.out.println("    maydon: type=" + e.getAttribute("type") + " name=" + e.getAttribute("name") + " id=" + e.getAttribute("id") + " ph=" + e.getAttribute("placeholder") + " val=" + e.getAttribute("value")); } catch (Exception ignore) {}
                shot("sessiya_tahrir_" + row);
                d.navigate().refresh();
                Thread.sleep(5000);
            }
            // "Savdo uchun e'lon berish +" tugmasi formasi
            d.findElement(By.xpath("//button[contains(normalize-space(.),\"Savdo uchun e'lon berish\")]")).click();
            Thread.sleep(2500);
            for (WebElement m : d.findElements(By.cssSelector("[role=dialog], [class*=modal], [class*=Modal]")))
                try { if (m.isDisplayed()) System.out.println("YANGI E'LON OYNASI: " + clean(m.getText())); } catch (Exception ignore) {}
            for (WebElement e : d.findElements(By.cssSelector("[role=dialog] input, [class*=modal] input")))
                try { if (e.isDisplayed()) System.out.println("    maydon: type=" + e.getAttribute("type") + " name=" + e.getAttribute("name") + " id=" + e.getAttribute("id") + " ph=" + e.getAttribute("placeholder")); } catch (Exception ignore) {}
            shot("sessiya_yangi_elon");
        } finally {
            d.quit();
        }
    }
}
