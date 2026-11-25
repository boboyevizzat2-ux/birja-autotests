package uz.ebirja.pages;

import org.openqa.selenium.*;

import java.io.File;
import java.util.*;

import static uz.ebirja.base.Ui.*;

/** Treyder: Listing → Auksion → Kontrakt yaratish (/uz/trader/listing/sales/create) formasi. */
public class KontraktForma {
    private final WebDriver d;
    private final String product;

    /** Formadagi asosiy matn maydonlari (input name) va ularning to'g'ri qiymatlari. */
    public static final Map<String, String> TOGRI = new LinkedHashMap<>();
    static {
        TOGRI.put("amount", "1");
        TOGRI.put("mainPrice", "1000000");
        TOGRI.put("deliveryBasisDetail", "Avtotest");
        TOGRI.put("deliveryTerm", "10");
        TOGRI.put("sellerDepositPercentage", "10");
        TOGRI.put("paymentTerm", "3");
        TOGRI.put("buyerDepositPercentage", "10");
        TOGRI.put("specialConditionUz", "Avtotest");
        TOGRI.put("specialConditionUzc", "Avtotest");
        TOGRI.put("specialConditionRu", "Avtotest");
    }

    public KontraktForma(WebDriver d, String product) {
        this.d = d;
        this.product = product;
    }

    JavascriptExecutor js() { return (JavascriptExecutor) d; }

    /** Formani ochadi va yuklanishini kutadi. */
    public KontraktForma ochish(String traderBase) {
        return ochishUrl(traderBase + "/uz/trader/listing/sales/create");
    }

    /** Istalgan kontrakt formasini ochadi (masalan, admin: qarama-qarshi auksion kontrakti). */
    public KontraktForma ochishUrl(String url) {
        d.get(url);
        if (waitFor(() -> visible(d, By.name("amount")), 30) == null)
            throw new AssertionError("Kontrakt yaratish formasi ochilmadi: " + d.getCurrentUrl());
        sleep(1500);
        return this;
    }

    /** Formani to'g'ri qiymatlar bilan to'ldiradi (bo'sh maydonlarni). fayl=true bo'lsa hujjat ham yuklanadi. */
    public KontraktForma togriToldirish(boolean fayl) {
        fillSelects();
        TOGRI.forEach(this::typeIfEmpty);
        fillSelects();
        if (fayl) faylYuklash();
        return this;
    }

    public void faylYuklash() {
        for (WebElement f : d.findElements(By.cssSelector("input[type=file]")))
            f.sendKeys(uz.ebirja.config.Config.resurs("test-hujjat.pdf").getAbsolutePath());
        sleep(2500);
    }

    /** Maydon bo'sh bo'lsa qiymat yozadi (qoralamadagi qiymatga tegmaydi). */
    public void typeIfEmpty(String name, String value) {
        for (WebElement e : d.findElements(By.name(name))) {
            if (!e.isDisplayed() || !e.isEnabled()) continue;
            String v = e.getAttribute("value");
            if (v != null && !v.isEmpty()) return;
            js().executeScript("arguments[0].scrollIntoView({block:'center'})", e);
            e.sendKeys(value);
            return;
        }
    }

    /** Maydondagi eski qiymatni o'chirib, yangisini yozadi. Maydonda qolgan qiymatni qaytaradi. */
    public String set(String name, String value) {
        WebElement e = visible(d, By.name(name));
        if (e == null) throw new AssertionError("Formada '" + name + "' maydoni yo'q");
        js().executeScript("arguments[0].scrollIntoView({block:'center'}); arguments[0].focus();", e);
        e.sendKeys(Keys.CONTROL + "a");
        e.sendKeys(Keys.DELETE);
        if (!value.isEmpty()) e.sendKeys(value);
        e.sendKeys(Keys.TAB);
        sleep(500);
        return value(name);
    }

    public String value(String name) {
        WebElement e = visible(d, By.name(name));
        return e == null ? null : e.getAttribute("value");
    }

    /**
     * SALBIY testlar uchun: brauzerdan E-IMZO dasturiga (localhost) ketadigan IMZO so'rovlarini to'sadi.
     * Sayt imzo so'rashga uringanini window.__eimzoImzo hisoblagichi ko'rsatadi; imzo qo'yilmaydi, kontrakt yuborilmaydi.
     */
    public void eimzoImzoniTosish() { eimzoTosish(d); }

    /** Sayt E-IMZO imzosini so'rashga necha marta urindi (eimzoImzoniTosish dan keyin). */
    public long eimzoUrinishlari() { return uz.ebirja.base.Ui.eimzoUrinishlari(d); }

    public Object eimzoSorovlari() {
        return js().executeScript("return window.__eimzoData || [];");
    }

    public void yuborishniBosish() {
        jsClick(d, d.findElement(By.xpath("//button[contains(normalize-space(.),'Yuborish')]")));
    }

    /** Ekrandagi xato yozuvlari va toastlar (qizil matnlar). */
    public List<String> xatolar() {
        Set<String> r = new LinkedHashSet<>(toasts(d));
        // faqat maydonlar ostidagi qizil yozuvlar (menyu matnlari kirmasin)
        for (WebElement e : d.findElements(By.xpath("//main//form//*[not(*) and (contains(@class,'error') or contains(@class,'red') or contains(@class,'danger'))]")))
            try { if (e.isDisplayed() && !e.getText().isBlank() && e.getText().length() < 120) r.add(clean(e.getText())); } catch (Exception ignore) {}
        if (r.isEmpty())
            for (String t : new String[]{"Majburiy maydon", "oralig", "kamida 1 ta fayl"})
                if (!d.findElements(By.xpath("//main//*[not(*) and contains(text(),\"" + t + "\")]")).isEmpty()) r.add(t);
        return new ArrayList<>(r);
    }

    /**
     * Admin formalaridagi ro'yxatlar ("frontend-library-select", input[role=combobox], id = maydon nomi).
     * Bo'sh va faol ro'yxatlardan birinchi variantni tanlaydi; "mainProductId" da mahsulot nomi bo'yicha qidiradi.
     * tanlov: maydon id -> kerakli variant matni (bo'lsa shu tanlanadi).
     */
    public void comboToldir(Map<String, String> tanlov) {
        Set<String> done = new HashSet<>();
        for (int pass = 0; pass < 6; pass++) {
            boolean changed = false;
            for (WebElement in : d.findElements(By.cssSelector("input.frontend-library-select__input[role=combobox]"))) {
                try {
                    String id = in.getAttribute("id");
                    if (done.contains(id) || !in.isEnabled()) continue;
                    String val = String.valueOf(js().executeScript(
                            "let c=arguments[0].closest('[class*=\"__control\"]'); if(!c) return ''; let v=c.querySelector('[class*=\"single-value\"],[class*=\"multi-value\"]'); return v? v.innerText : '';", in)).trim();
                    if (!val.isEmpty()) { done.add(id); continue; }
                    js().executeScript("arguments[0].scrollIntoView({block:'center'})", in);
                    String kerak = tanlov.getOrDefault(id, "mainProductId".equals(id) ? product : null);
                    WebElement ctrl = (WebElement) js().executeScript("return arguments[0].closest('[class*=\"__control\"]') || arguments[0];", in);
                    try { ctrl.click(); } catch (Exception e) { jsClick(d, ctrl); }
                    if (kerak != null) in.sendKeys(kerak);
                    By opt = By.cssSelector("[class*='frontend-library-select__option']");
                    Object bor = waitFor(() -> d.findElements(opt).stream().anyMatch(WebElement::isDisplayed) ? Boolean.TRUE : null, 5);
                    if (bor == null) { in.sendKeys(Keys.ESCAPE); done.add(id); continue; }
                    WebElement tanla = null;
                    for (WebElement o : d.findElements(opt))
                        if (o.isDisplayed() && (kerak == null || o.getText().trim().equals(kerak))) { tanla = o; break; }
                    if (tanla == null) for (WebElement o : d.findElements(opt)) if (o.isDisplayed()) { tanla = o; break; }
                    jsClick(d, tanla);
                    sleep(500); // bog'liq maydonlar (guruh, bo'lim...) avtomatik to'ladi
                    done.add(id);
                    changed = true;
                } catch (StaleElementReferenceException e) { changed = true; }
            }
            if (!changed) break;
        }
    }

    /** Admin formasidagi ro'yxatlarning hozirgi qiymatlari (id -> matn). */
    public Map<String, String> comboQiymatlari() {
        Map<String, String> m = new LinkedHashMap<>();
        for (WebElement in : d.findElements(By.cssSelector("input.frontend-library-select__input[role=combobox]")))
            try {
                m.put(in.getAttribute("id"), String.valueOf(js().executeScript(
                        "let c=arguments[0].closest('[class*=\"__control\"]'); if(!c) return ''; let v=c.querySelector('[class*=\"single-value\"]'); return v? v.innerText : '';", in)).trim());
            } catch (Exception ignore) {}
        return m;
    }

    /** Bo'sh ochiladigan ro'yxatlardan birinchisini (mahsulotni nomi bo'yicha) tanlaydi. */
    public void fillSelects() {
        Set<String> done = new HashSet<>();
        for (int pass = 0; pass < 5; pass++) {
            boolean changed = false;
            for (WebElement in : d.findElements(By.cssSelector("main input[id^='react-select-']"))) {
                try {
                    String id = in.getAttribute("id");
                    if (done.contains(id)) continue;
                    Object dis = js().executeScript("let c=arguments[0].closest('[class*=\"-container\"]'); return !c || c.className.includes('disabled') || arguments[0].disabled;", in);
                    if (Boolean.TRUE.equals(dis)) continue;
                    String val = String.valueOf(js().executeScript("let c=arguments[0].closest('[class*=\"-container\"]'); return c? c.innerText : '';", in)).trim();
                    if (!val.isEmpty() && !val.toLowerCase().contains("tanlang")) { done.add(id); continue; }
                    String label = String.valueOf(js().executeScript("let el=arguments[0]; for(let i=0;i<8 && el;i++){ el=el.parentElement; if(!el) break; let l=el.querySelector('label'); if(l) return l.innerText; } return '';", in));
                    js().executeScript("arguments[0].scrollIntoView({block:'center'})", in);
                    if (label.startsWith("Mahsulot")) in.sendKeys(product);
                    else in.sendKeys(Keys.ARROW_DOWN);
                    // qat'iy kutish o'rniga: variantlar chiqishini kutamiz (ko'pi bilan 4 s)
                    String optSel = "[id^='" + id.replace("-input", "") + "-option-']";
                    Object bor = waitFor(() -> d.findElements(By.cssSelector(optSel)).isEmpty() ? null : Boolean.TRUE, 4);
                    if (bor == null) { in.sendKeys(Keys.ESCAPE); done.add(id); continue; }
                    in.sendKeys(Keys.ENTER);
                    sleep(400); // tanlovdan keyin bog'liq maydonlar (marka, sinf...) avtomatik to'ladi
                    done.add(id);
                    changed = true;
                } catch (StaleElementReferenceException e) { changed = true; }
            }
            if (!changed) break;
        }
    }
}
