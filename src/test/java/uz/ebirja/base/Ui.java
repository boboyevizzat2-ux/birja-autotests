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
        sleep(2000);
        d.manage().deleteAllCookies();
        ((JavascriptExecutor) d).executeScript("localStorage.clear(); sessionStorage.clear();");
    }

    /** ERI kalit (E-IMZO) orqali kirish. trader=true bo'lsa "Birja savdolari" platformasi tanlanadi. */
    public static void loginByKey(WebDriver d, String base, String jshshir, boolean trader) {
        logout(d, base);
        d.get(base + "/uz/auth/loginkey");
        WebElement keySel = waitFor(() -> visible(d, By.cssSelector("input[id^='react-select'], input#key")), 30);
        if (keySel == null) throw new RuntimeException("Kirish sahifasi ochilmadi (VPN?): " + base);
        sleep(2500);
        if (trader) jsClick(d, d.findElement(By.xpath("//button[contains(normalize-space(.),'Birja savdolari')]")));
        sleep(800);
        boolean found = false;
        for (int t = 0; t < 10 && !found; t++) {
            ((JavascriptExecutor) d).executeScript("arguments[0].focus()", keySel);
            keySel.sendKeys(Keys.ARROW_DOWN);
            sleep(2000);
            for (WebElement opt : d.findElements(By.cssSelector("[id*='-option-']")))
                try { if (opt.isDisplayed() && opt.getText().contains(jshshir)) { jsClick(d, opt); found = true; break; } } catch (Exception ignore) {}
            if (!found) { keySel.sendKeys(Keys.ESCAPE); sleep(1000); }
        }
        if (!found) throw new RuntimeException("E-IMZO ro'yxatida kalit topilmadi: " + jshshir);
        sleep(800);
        jsClick(d, d.findElement(By.xpath("//button[@type='submit' and contains(normalize-space(.),'Tizimga kirish')]")));
        // E-IMZO parol oynasi kechikib chiqishi mumkin: sahifa ochilguncha kuzatib turamiz
        long end = System.currentTimeMillis() + 90_000;
        while (System.currentTimeMillis() < end && d.getCurrentUrl().contains("/auth/")) {
            if (!Eimzo.windowTitle().isEmpty()) Eimzo.typePassword(2);
            sleep(700);
        }
        if (d.getCurrentUrl().contains("/auth/"))
            throw new RuntimeException("ERI kalit bilan kirib bo'lmadi: " + jshshir);
        sleep(3000);
        System.out.println("Kirildi (" + jshshir + "): " + d.getCurrentUrl());
    }
}
