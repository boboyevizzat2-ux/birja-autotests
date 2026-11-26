package uz.ebirja.pages;

import org.openqa.selenium.*;

import java.util.List;

import static uz.ebirja.base.Ui.*;

/** Saytdagi barcha ro'yxat (jadval) sahifalari uchun umumiy amallar: qator topish, ustun o'qish, qidirish. */
public final class Jadval {
    private Jadval() {}

    public static final By QATORLAR = By.cssSelector("main tbody tr");
    public static final By QIDIRISH = By.cssSelector("main input[placeholder='Qidirish']");

    /** Apostrof turlari va ajralmas bo'shliqlarni bir xil qiladi (To‘lov / To'lov). */
    public static String norm(String s) { return s == null ? "" : s.replaceAll("[‘’`ʼ]", "'").replace(' ', ' '); }

    /** Matni berilgan so'zni o'z ichiga olgan birinchi qator (yo'q bo'lsa null). */
    public static WebElement qator(WebDriver d, String matn) {
        for (WebElement r : d.findElements(QATORLAR))
            try { if (r.getText().contains(matn)) return r; } catch (StaleElementReferenceException e) { /* jadval qayta chizildi */ }
        return null;
    }

    /** Sarlavhasi AYNAN shu nomli ustun qiymati (tartib raqamiga bog'lanmaydi). Topilmasa bo'sh satr. */
    public static String ustun(WebDriver d, WebElement row, String sarlavha) {
        List<WebElement> th = d.findElements(By.cssSelector("main thead th")), td = row.findElements(By.cssSelector("td"));
        for (int i = 0; i < th.size() && i < td.size(); i++)
            if (norm(clean(th.get(i).getText())).equalsIgnoreCase(norm(sarlavha))) return clean(td.get(i).getText());
        return "";
    }

    /** Qidirish maydoniga yozadi va natijani kutadi. Maydon bo'lmasa hech narsa qilmaydi. */
    public static void qidir(WebDriver d, String matn) {
        WebElement s = visible(d, QIDIRISH);
        if (s == null) return;
        s.sendKeys(Keys.CONTROL + "a");
        s.sendKeys(matn);
        qidiruvKut(d, matn);
    }

    /** Qator oxiridagi ⋮ (Amallar) tugmasini bosadi. */
    public static void amallarniOchish(WebDriver d, WebElement row) {
        List<WebElement> tds = row.findElements(By.cssSelector("td"));
        jsClick(d, tds.get(tds.size() - 1).findElement(By.cssSelector("button")));
    }

    /** Jadval ustidagi tabni (matni shu so'z bilan boshlanadigan) bosadi. Topilmasa false. */
    public static boolean tab(WebDriver d, String nom) {
        WebElement t = waitFor(() -> visible(d, By.xpath("//main//*[starts-with(normalize-space(.),\"" + nom + "\")][not(*) or self::button]")), 10);
        if (t == null) return false;
        jsClick(d, t);
        jadvalKut(d);
        return true;
    }
}
