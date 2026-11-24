package uz.ebirja.base;

import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Sahifalar bilan ishlash uchun umumiy yordamchi usullar. */
public final class Ui {
    private Ui() {}

    public static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public static String clean(String s) {
        return s == null ? "" : s.replace("\n", " | ").replaceAll("\\s+", " ").trim();
    }

    public static String cut(String s, int n) {
        String c = clean(s);
        return c.length() > n ? c.substring(0, n) + "…" : c;
    }

    public static void jsClick(WebDriver d, WebElement e) {
        ((JavascriptExecutor) d).executeScript("arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", e);
    }

    /** Haqiqiy sichqoncha bosishi (React katakchalari uchun), kerak bo'lsa JS bilan takrorlaydi. */
    public static void realClick(WebDriver d, WebElement e) {
        ((JavascriptExecutor) d).executeScript("arguments[0].scrollIntoView({block:'center'})", e);
        try { new Actions(d).moveToElement(e).click().perform(); } catch (Exception ex) { jsClick(d, e); }
    }

    /** Element paydo bo'lguncha kutadi (null qaytarsa — kutishda davom etadi). */
    public static <T> T waitFor(Supplier<T> s, int seconds) {
        long end = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < end) {
            try { T v = s.get(); if (v != null && !Boolean.FALSE.equals(v)) return v; } catch (Exception ignore) {}
            sleep(500);
        }
        return null;
    }

    public static WebElement visible(WebDriver d, By by) {
        for (WebElement e : d.findElements(by)) try { if (e.isDisplayed()) return e; } catch (Exception ignore) {}
        return null;
    }

    public static WebElement lastVisible(WebDriver d, By by) {
        WebElement r = null;
        for (WebElement e : d.findElements(by)) try { if (e.isDisplayed()) r = e; } catch (Exception ignore) {}
        return r;
    }

    /** Ekrandagi qalqib chiquvchi xabarlar (toast). */
    public static List<String> toasts(WebDriver d) {
        List<String> r = new ArrayList<>();
        for (WebElement e : d.findElements(By.cssSelector("[class*=toast], [class*=Toastify], [role=alert], [role=status]")))
            try { if (e.isDisplayed() && !e.getText().isBlank()) r.add(clean(e.getText())); } catch (Exception ignore) {}
        return r;
    }

    /** Berilgan matnli xabar chiqishini kutadi. */
    public static boolean waitToast(WebDriver d, String text, int seconds) {
        return waitFor(() -> toasts(d).stream().anyMatch(t -> t.contains(text)) ? Boolean.TRUE : null, seconds) != null;
    }

    /** Berilgan vaqtgacha kutadi. */
    public static void waitUntil(LocalDateTime t, String why) {
        long ms = Duration.between(LocalDateTime.now(), t).toMillis();
        if (ms > 0) {
            System.out.println("Kutilmoqda " + (ms / 1000) + " soniya: " + why);
            sleep(ms);
        }
    }

    /** Saytdan chiqish (doimiy profilda cookie va localStorage tozalanadi). */
    public static void logout(WebDriver d, String base) {
        d.get(base + "/uz/auth/loginkey");
        sleep(500);
        d.manage().deleteAllCookies();
        ((JavascriptExecutor) d).executeScript("localStorage.clear(); sessionStorage.clear();");
    }

    /** Sahifadagi jadval yuklanishini kutadi: qatorlar chiqsa yoki "mavjud emas/topilmadi" yozuvi chiqsa darhol qaytadi (ko'pi bilan 20 s). */
    public static void jadvalKut(WebDriver d) {
        sleep(700);
        waitFor(() -> {
            if (!d.findElements(By.cssSelector("main tbody tr td")).isEmpty()) return Boolean.TRUE;
            String t = d.findElement(By.tagName("main")).getText();
            return t.contains("mavjud emas") || t.contains("topilmadi") ? Boolean.TRUE : null;
        }, 20);
        sleep(300);
    }

    /** Qidiruv natijasini kutadi: jadvalda faqat matn bor qatorlar yoki bo'sh natija. */
    public static void qidiruvKut(WebDriver d, String matn) {
        sleep(500);
        waitFor(() -> {
            List<WebElement> rows = d.findElements(By.cssSelector("main tbody tr"));
            if (rows.isEmpty()) return d.findElement(By.tagName("main")).getText().contains("mavjud emas") ? Boolean.TRUE : null;
            for (WebElement r : rows) try { if (!r.getText().contains(matn)) return null; } catch (Exception e) { return null; }
            return Boolean.TRUE;
        }, 15);
    }

    /** Jadval pastidagi "Ko'rsatish" (10/20/50/100) ro'yxatidan 100 ni tanlaydi (bo'lsa). */
    public static void sahifada100(WebDriver d) {
        for (WebElement in : d.findElements(By.cssSelector("main input[id^='react-select']"))) {
            try {
                Object txt = ((JavascriptExecutor) d).executeScript("let c=arguments[0].closest('[class*=\"-container\"]'); return c? c.innerText.trim() : '';", in);
                if (!"10".equals(String.valueOf(txt))) continue;
                ((JavascriptExecutor) d).executeScript("arguments[0].scrollIntoView({block:'center'}); arguments[0].focus();", in);
                in.sendKeys(Keys.ARROW_DOWN);
                sleep(1000);
                for (WebElement op : d.findElements(By.cssSelector("[id*='-option-']")))
                    if (op.isDisplayed() && op.getText().trim().equals("100")) { jsClick(d, op); sleep(4000); return; }
                in.sendKeys(Keys.ESCAPE);
            } catch (Exception ignore) {}
        }
    }

    /**
     * SALBIY testlar uchun: brauzerdan E-IMZO dasturiga ketadigan IMZO so'rovlarini to'sadi (sahifa qayta yuklanguncha).
     * Sayt imzo so'rashga uringanini eimzoUrinishlari() ko'rsatadi; imzo qo'yilmaydi, hech narsa yuborilmaydi.
     */
    public static void eimzoTosish(WebDriver d) {
        ((JavascriptExecutor) d).executeScript(
                "window.__eimzoImzo = 0; window.__eimzoData = [];" +
                "if (!window.__eimzoPatched) { window.__eimzoPatched = true;" +
                "  const s = WebSocket.prototype.send;" +
                "  WebSocket.prototype.send = function(x) {" +
                "    if (/127.0.0.1|localhost/.test(this.url)) {" +
                "      const t = String(x); window.__eimzoData.push(t.slice(0, 150));" +
                "      if (/pkcs7|sign|load_key|create/i.test(t)) { window.__eimzoImzo++; return; }" +
                "    }" +
                "    return s.call(this, x);" +
                "  };" +
                "}");
    }

    public static long eimzoUrinishlari(WebDriver d) {
        Object v = ((JavascriptExecutor) d).executeScript("return window.__eimzoImzo || 0;");
        return v instanceof Number ? ((Number) v).longValue() : 0;
    }

    /** ERI kalit (E-IMZO) orqali kirish. trader=true bo'lsa "Birja savdolari" platformasi tanlanadi. */
    public static void loginByKey(WebDriver d, String base, String jshshir, boolean trader) {
        logout(d, base);
        d.get(base + "/uz/auth/loginkey");
        WebElement keySel = waitFor(() -> visible(d, By.cssSelector("input[id^='react-select'], input#key")), 30);
        if (keySel == null) throw new RuntimeException("Kirish sahifasi ochilmadi (VPN?): " + base);
        sleep(600);
        if (trader) jsClick(d, waitFor(() -> visible(d, By.xpath("//button[contains(normalize-space(.),'Birja savdolari')]")), 10));
        sleep(300);
        boolean found = false;
        for (int t = 0; t < 10 && !found; t++) {
            ((JavascriptExecutor) d).executeScript("arguments[0].focus()", keySel);
            keySel.sendKeys(Keys.ARROW_DOWN);
            waitFor(() -> d.findElements(By.cssSelector("[id*='-option-']")).stream().anyMatch(o -> o.getText().contains(jshshir)) ? Boolean.TRUE : null, 3);
            for (WebElement opt : d.findElements(By.cssSelector("[id*='-option-']")))
                try { if (opt.isDisplayed() && opt.getText().contains(jshshir)) { jsClick(d, opt); found = true; break; } } catch (Exception ignore) {}
            if (!found) { keySel.sendKeys(Keys.ESCAPE); sleep(500); }
        }
        if (!found) throw new RuntimeException("E-IMZO ro'yxatida kalit topilmadi: " + jshshir);
        sleep(300);
        jsClick(d, d.findElement(By.xpath("//button[@type='submit' and contains(normalize-space(.),'Tizimga kirish')]")));
        // E-IMZO parol oynasi kechikib chiqishi mumkin: sahifa ochilguncha kuzatib turamiz
        long end = System.currentTimeMillis() + 90_000;
        while (System.currentTimeMillis() < end && d.getCurrentUrl().contains("/auth/")) {
            if (!Eimzo.windowTitle().isEmpty()) Eimzo.typePassword(2);
            sleep(250);
        }
        if (d.getCurrentUrl().contains("/auth/")) {
            String matn = "";
            try {
                matn = d.findElement(By.tagName("body")).getText().replace('\n', ' ');
                java.io.File f = ((TakesScreenshot) d).getScreenshotAs(OutputType.FILE);
                java.nio.file.Files.createDirectories(java.nio.file.Paths.get("target/xato-rasmlar"));
                java.nio.file.Files.copy(f.toPath(), java.nio.file.Paths.get("target/xato-rasmlar/login_" + jshshir + "_" + System.currentTimeMillis() + ".png"));
            } catch (Exception ignore) {}
            throw new RuntimeException("ERI kalit bilan kirib bo'lmadi: " + jshshir + " | sahifa: " + (matn.length() > 300 ? matn.substring(0, 300) : matn));
        }
        sleep(1000);
        System.out.println("Kirildi (" + jshshir + "): " + d.getCurrentUrl());
    }
}
