package uz.ebirja.tests;

import org.openqa.selenium.*;
import org.testng.Assert;
import uz.ebirja.base.Eimzo;
import uz.ebirja.config.Config;
import uz.ebirja.pages.KontraktForma;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static uz.ebirja.base.Ui.*;

/**
 * "Tartibga solinadigan mahsulotlar" testlari uchun umumiy qism (har bir tovar turi o'z klassida: RegOddiyTest, RegSpirtTest ...).
 * Rollar: Dilshod (trader2) — sotuvchi, Zarnigor (trader1) — xaridor, o'z mijozlari nomidan;
 * admin1 — moderator (MODERATOR_LISTING_MAIN), admin2 — savdo vaqtini ochadi.
 * Mahsulot: reestrda yo'q, ruscha "М" harfi bilan boshlanadigan birinchi mahsulot (yoki -Dreg.product=...).
 */
public abstract class RegAsos extends SavdoAsos {

    // ---- Zarnigorning mijozlari (xaridorlar)
    static final String X_STIR = Config.getOr("reg.x", "306842474");   // EUROASIAN TIGER'S MCHJ — ro'yxatda, limit bilan
    static final String Y_STIR = Config.getOr("reg.y", "312056026");   // TRIPLE SEVEN MCHJ — ro'yxatda YO'Q
    // Y roli uchun nomzodlar (Zarnigorning mijozlari, hech biri reestr ro'yxatlariga qo'shilmaydi): pul tugasa keyingisiga o'tiladi
    static final List<String> Y_NOMZODLAR = Arrays.asList(Config.getOr("reg.yNomzodlar", "312056026,312578380,311259794,309598468,800000060,800000061,800000053").split(","));
    String yStir = Y_STIR;
    int yIndeks = Math.max(0, Y_NOMZODLAR.indexOf(Y_STIR));
    final Set<String> pulsizMijozlar = new LinkedHashSet<>();
    static final String Z_STIR = Config.getOr("reg.z", "312654674");   // JASUR MADIYOR SHAHRIYOR MCHJ — qo'shimcha
    static final String W_STIR = Config.getOr("reg.w", "311872706");   // NAVOIY GEOFIZIK MCHJ — qo'shimcha
    static final String SOTUVCHI_STIR = Config.getOr("trader2.stir", "300417856"); // IBOD-AZIZ (Dilshod)

    static final int ELON_MIN = Integer.parseInt(Config.getOr("reg.elonMinutes", "2"));
    static final int SAVDO_MIN = Integer.parseInt(Config.getOr("reg.savdoMinutes", "5"));
    static final String LOTS = Config.getOr("reg.lots", "20");

    String PRODUCT;           // tanlangan M-mahsulot
    String mahsulotId;        // reestrdagi ID (/regulated-products/{id})
    String guruh;             // mahsulot guruhi (savdo vaqti shu guruhga ochiladi)
    String birlik;            // kontrakt o'lchov birligi

    /** Bitta kontrakt: belgi -> ma'lumotlar. */
    static class Kontrakt {
        final String kod;           // K1, K2 ...
        String vaqt, id, raqam;     // yuborilgan vaqt, moderator ID, savdo/listing raqami
        Kontrakt(String kod) { this.kod = kod; }
        @Override public String toString() { return kod + "(ID " + id + ", №" + raqam + ")"; }
    }
    final Map<String, Kontrakt> K = new LinkedHashMap<>();

    LocalDateTime elonStart, savdoStart, savdoEnd;
    final List<String> xatolar = new ArrayList<>(); // kutilgandan farq qilgan holatlar (oxirida bitta assert)

    // ====================================================================== umumiy
    void kutilgan(boolean ok, String nima) {
        System.out.println((ok ? "  ✓ " : "  ✗ XATO: ") + nima);
        if (!ok) { xatolar.add(nima); rasmOl("xato_" + xatolar.size()); }
    }

    boolean savdoQoyildi = false;

    void shart(boolean ok, String sabab) { if (!ok) throw new org.testng.SkipException("O'tkazildi: " + sabab); }

    int belgi = 0;

    /** Shu test (bosqich) davomida yig'ilgan xatolar bo'lsa, test yiqiladi (keyingi bosqichlar baribir davom etadi). */
    void yakuniyTekshiruv() {
        List<String> yangi = new ArrayList<>(xatolar.subList(belgi, xatolar.size()));
        belgi = xatolar.size();
        Assert.assertTrue(yangi.isEmpty(), "Kutilgan natijaga mos kelmagan holatlar (" + yangi.size() + "):\n - " + String.join("\n - ", yangi));
    }

    @org.testng.annotations.AfterClass(alwaysRun = true)
    public void xulosa() {
        System.out.println("===== XULOSA (" + getClass().getSimpleName() + "): mahsulot " + PRODUCT + " (ID " + mahsulotId + "), kontraktlar " + K.values());
        if (!pulsizMijozlar.isEmpty()) System.out.println("Hisobida pul yetarli bo'lmagan mijozlar: " + pulsizMijozlar);
        System.out.println(xatolar.isEmpty() ? "Hamma tekshiruv kutilganidek" : "Mos kelmaganlar (" + xatolar.size() + "):\n - " + String.join("\n - ", xatolar));
    }

    void dump(String nom) {
        try {
            File dir = new File("target/reg-dump");
            dir.mkdirs();
            Object t = js().executeScript(
                "const out=[location.href];" +
                "[...document.querySelectorAll('[role=dialog],[class*=modal]')].filter(e=>e.offsetParent||getComputedStyle(e).position==='fixed').forEach(d=>{ if(d.innerText.trim()) out.push('=== DIALOG:\\n'+d.innerText); });" +
                "const m=document.querySelector('main'); out.push('=== MAIN:\\n'+(m?m.innerText:document.body.innerText).slice(0,12000));" +
                "out.push('=== TOAST: '+[...document.querySelectorAll('[class*=toast],[class*=Toastify],[role=alert]')].map(t=>t.innerText.trim()).filter(x=>x).join(' | '));" +
                "return out.join('\\n\\n');");
            String f = nom + "_" + System.currentTimeMillis();
            Files.writeString(new File(dir, f + ".txt").toPath(), String.valueOf(t), StandardCharsets.UTF_8);
            File s = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Files.copy(s.toPath(), new File(dir, f + ".png").toPath());
            System.out.println("  (dump: target/reg-dump/" + f + ")");
        } catch (Exception ignore) {}
    }

    boolean imzoYoki(java.util.function.Supplier<Boolean> tayyor, int sec) {
        boolean imzo = false;
        for (long end = System.currentTimeMillis() + sec * 1000L; System.currentTimeMillis() < end; sleep(150)) {
            if (!Eimzo.windowTitle().isEmpty()) imzo |= Eimzo.typePassword(1);
            try { if (Boolean.TRUE.equals(tayyor.get())) break; } catch (Exception ignore) {}
        }
        return imzo;
    }

    // ---------------------------------------------------------------- oynalar (dialog)
    static final String DLG = "[role=dialog],[class*=modal]";
    static final String DLG_X = "//*[@role='dialog' or contains(@class,'modal')]";

    WebElement dialog() {
        return waitFor(() -> (WebElement) js().executeScript("return [...document.querySelectorAll(arguments[0])].filter(e=>(e.offsetParent||getComputedStyle(e).position==='fixed') && e.innerText.trim()).pop() || null;", DLG), 8);
    }

    String dialogMatn() {
        Object t = js().executeScript("const d=[...document.querySelectorAll(arguments[0])].filter(e=>(e.offsetParent||getComputedStyle(e).position==='fixed') && e.innerText.trim()).pop(); return d? d.innerText : '';", DLG);
        return t == null ? "" : String.valueOf(t);
    }

    boolean dialogOchiq() { return !dialogMatn().isBlank(); }

    /** Oynadagi radio/checkbox: yonidagi matn shu so'z bilan boshlanadigani (name berilsa — faqat shu guruhdan). */
    WebElement belgi(String tur, String boshi, String name) {
        return (WebElement) js().executeScript(
            "const d=[...document.querySelectorAll(arguments[0])].filter(e=>(e.offsetParent||getComputedStyle(e).position==='fixed') && e.innerText.trim()).pop() || document.querySelector('main'); if(!d) return null;" +
            "const norm=s=>s.replace(/[‘’`ʼ]/g,\"'\").trim();" +
            "for(const i of d.querySelectorAll('input[type='+arguments[1]+']')){ if(arguments[3] && i.name!==arguments[3]) continue; let e=i;" +
            "  for(let k=0;k<5&&e;k++){ e=e.parentElement; const t=norm(e.innerText||''); if(t){ if(t.startsWith(norm(arguments[2]))) return i; break; } } }" +
            "return null;", DLG, tur, boshi, name);
    }

    /** Checkbox / radio ni kerakli holatga keltiradi. */
    boolean belgila(String tur, String boshi, String name, boolean kerak) {
        WebElement i = belgi(tur, boshi, name);
        if (i == null) { System.out.println("  (oynada '" + boshi + "' " + tur + " yo'q)"); return false; }
        for (int t = 0; t < 3 && i.isSelected() != kerak; t++) {
            WebElement lbl = (WebElement) js().executeScript("return arguments[0].closest('label') || arguments[0].parentElement;", i);
            if (t == 0) realClick(driver, lbl); else js().executeScript("arguments[0].click()", i);
            sleep(300);
        }
        return i.isSelected() == kerak;
    }

    boolean disabled(WebElement i) { return i != null && (!i.isEnabled() || "true".equals(i.getAttribute("aria-disabled"))); }

    void dlgTugma(String matn) {
        WebElement b = waitFor(() -> lastVisible(driver, By.xpath(DLG_X + "//button[normalize-space(.)=\"" + matn + "\"]")), 6);
        Assert.assertNotNull(b, "Oynada '" + matn + "' tugmasi yo'q. Oyna: " + cut(dialogMatn(), 300));
        jsClick(driver, b);
    }

    WebElement dlgTugmaEl(String matn) { return lastVisible(driver, By.xpath(DLG_X + "//button[normalize-space(.)=\"" + matn + "\"]")); }

    /** Oynani saqlamasdan yopadi. */
    void yop() {
        for (int t = 0; t < 3 && dialogOchiq(); t++) {
            WebElement b = lastVisible(driver, By.xpath(DLG_X + "//button[normalize-space(.)='Bekor qilish' or normalize-space(.)='Yopish']"));
            if (b != null) jsClick(driver, b); else new org.openqa.selenium.interactions.Actions(driver).sendKeys(Keys.ESCAPE).perform();
            sleep(600);
        }
    }

    /** Oynadagi react-select: yorlig'i (label) shu so'z bilan boshlanadigan ro'yxatga yozib, variantni tanlaydi. */
    String dlgSelect(String label, String yoz, java.util.function.Predicate<String> mos) {
        WebElement in = (WebElement) js().executeScript(
            "const d=[...document.querySelectorAll(arguments[0])].filter(e=>(e.offsetParent||getComputedStyle(e).position==='fixed') && e.innerText.trim()).pop(); if(!d) return null;" +
            "for(const i of d.querySelectorAll('input[id^=react-select], input[role=combobox]')){ let e=i; for(let k=0;k<8&&e;k++){ e=e.parentElement; const l=e.querySelector('label'); if(l){ if(l.innerText.trim().startsWith(arguments[1])) return i; break; } } }" +
            "return null;", DLG, label);
        Assert.assertNotNull(in, "Oynada '" + label + "' ro'yxati yo'q. Oyna: " + cut(dialogMatn(), 300));
        js().executeScript("arguments[0].scrollIntoView({block:'center'}); arguments[0].focus();", in);
        WebElement ctrl = (WebElement) js().executeScript("return arguments[0].closest('[class*=\"control\"]') || arguments[0];", in);
        try { ctrl.click(); } catch (Exception e) { jsClick(driver, ctrl); }
        if (yoz != null && !yoz.isEmpty()) in.sendKeys(yoz); else in.sendKeys(Keys.ARROW_DOWN);
        WebElement op = waitFor(() -> {
            for (WebElement o : driver.findElements(By.cssSelector("[id*='-option-'],[class*=__option],[role=option]")))
                try { if (o.isDisplayed() && mos.test(o.getText().trim())) return o; } catch (Exception ignore) {}
            return null;
        }, 8);
        if (op == null) { in.sendKeys(Keys.ESCAPE); return null; }
        String t = op.getText().trim();
        jsClick(driver, op);
        sleep(500);
        return t;
    }

    /** Oynadagi name=... ro'yxatga yozib, matni aynan shu variantni tanlaydi. */
    String nomSelect(String name, String v) {
        // name=... yashirin qiymat maydoni bo'lishi mumkin: shu joydagi (yoki undan keyingi) ko'rinadigan react-select kiritish maydoni olinadi
        WebElement in = waitFor(() -> (WebElement) js().executeScript(
            "const d=[...document.querySelectorAll(arguments[0])].filter(e=>(e.offsetParent||getComputedStyle(e).position==='fixed') && e.innerText.trim()).pop(); if(!d) return null;" +
            "const all=[...d.querySelectorAll('input')]; const h=d.querySelector('input[name=\"'+arguments[1]+'\"]'); if(!h) return null;" +
            "const c=h.closest('[class*=\"container\"]'); const v=c && c.querySelector('input[id^=react-select],input[role=combobox]'); if(v) return v;" +
            "for(let i=all.indexOf(h)+1;i<all.length;i++){ if(/react-select/.test(all[i].id)||all[i].getAttribute('role')==='combobox') return all[i]; } return null;", DLG, name), 5);
        if (in == null) { System.out.println("  ('" + name + "' ro'yxati topilmadi)"); return null; }
        WebElement ctrl = (WebElement) js().executeScript("return arguments[0].closest('[class*=\"control\"]') || arguments[0];", in);
        realClick(driver, ctrl);
        try { in.sendKeys(v); } catch (Exception e) { new org.openqa.selenium.interactions.Actions(driver).sendKeys(v).perform(); }
        WebElement op = waitFor(() -> { for (WebElement o : driver.findElements(By.cssSelector("[id*='-option-'],[class*=__option],[role=option]"))) try { if (o.isDisplayed() && o.getText().trim().equalsIgnoreCase(v)) return o; } catch (Exception ignore) {} return null; }, 6);
        if (op == null) return null;
        jsClick(driver, op);
        sleep(400);
        return v;
    }

    /** Oynadagi fayl maydoniga test hujjatini yuklaydi. */
    String oxirgiLimitMaydoni; // limit yozilgandan keyin maydonda qolgan qiymat

    void dlgFayl() {
        for (WebElement f : driver.findElements(By.cssSelector("[role=dialog] input[type=file], [class*=modal] input[type=file]")))
            try { f.sendKeys(Config.resurs("test-hujjat.pdf").getAbsolutePath()); } catch (Exception ignore) {}
        sleep(1500);
    }

    void dlgYoz(String name, String v) {
        WebElement e = waitFor(() -> lastVisible(driver, By.cssSelector("[role=dialog] [name='" + name + "'], [class*=modal] [name='" + name + "']")), 6);
        Assert.assertNotNull(e, "Oynada '" + name + "' maydoni yo'q");
        js().executeScript("arguments[0].scrollIntoView({block:'center'}); arguments[0].focus();", e);
        e.sendKeys(Keys.CONTROL + "a");
        e.sendKeys(Keys.DELETE);
        e.sendKeys(v);
        sleep(300);
    }

    /** Saqlash/Qo'shish ni bosadi, E-IMZO so'rasa imzolaydi; oyna yopilsa true. Xabarlarni yig'adi. */
    boolean dlgSaqla(String tugma, List<String> xabarlar) {
        dlgTugma(tugma);
        Set<String> yig = new LinkedHashSet<>();
        imzoYoki(() -> { yig.addAll(toasts(driver)); return !dialogOchiq(); }, 15);
        yig.addAll(toasts(driver));
        if (xabarlar != null) xabarlar.addAll(yig);
        return !dialogOchiq();
    }

    /** Oynadagi qizil xato yozuvlari. */
    List<String> dlgXatolar() {
        List<?> o = (List<?>) js().executeScript(
            "const d=[...document.querySelectorAll(arguments[0])].filter(e=>(e.offsetParent||getComputedStyle(e).position==='fixed') && e.innerText.trim()).pop(); if(!d) return [];" +
            "return [...d.querySelectorAll('*')].filter(e=>e.children.length===0 && e.innerText && e.innerText.trim() && /red|error|danger|destructive/.test(e.className||'') ).map(e=>e.innerText.trim());", DLG);
        List<String> r = new ArrayList<>();
        for (Object x : o) r.add(String.valueOf(x));
        r.addAll(toasts(driver));
        return r;
    }

    // ====================================================================== moderator: reestr
    String reestrUrl() { return Config.adminUrl() + "/uz/moderationv2/listing/regulated-products"; }
    String kartaUrl(String tab) { return reestrUrl() + "/" + mahsulotId + (tab == null ? "" : "?tab=" + tab); }

    void moderatorKir() {
        kim("admin1");
        if (!"MODERATOR_LISTING_MAIN".equals(currentRole())) switchRole("MODERATOR_LISTING_MAIN");
    }

    /** Reestrda shu nomdagi mahsulot qatori (aniq nom bo'yicha). */
    WebElement reestrQatori(String nom) {
        driver.get(reestrUrl() + "?status=all&search=" + java.net.URLEncoder.encode(nom, StandardCharsets.UTF_8));
        jadvalKut(driver);
        return waitFor(() -> {
            for (WebElement r : driver.findElements(By.cssSelector("main tbody tr"))) {
                List<WebElement> td = r.findElements(By.cssSelector("td"));
                if (!td.isEmpty() && td.get(0).getText().trim().equals(nom)) return r;
            }
            return null;
        }, 4);
    }

    /** "Mahsulot qo'shish" oynasidagi M-mahsulotlar (reestrda yo'qlari). */
    List<String> mNomzodlar() {
        driver.get(reestrUrl() + "?status=all");
        jadvalKut(driver);
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//main//button[starts-with(normalize-space(.),\"Mahsulot qo\")]")), 15));
        Assert.assertNotNull(dialog(), "'Mahsulot qo'shish' oynasi ochilmadi");
        WebElement in = waitFor(() -> lastVisible(driver, By.cssSelector("[role=dialog] input[id^='react-select'], [class*=modal] input[id^='react-select'], [role=dialog] input[role=combobox]")), 6);
        Set<String> r = new LinkedHashSet<>();
        for (String p : new String[]{"Ма", "Ме", "Ми", "Мо", "Му"}) {
            js().executeScript("arguments[0].focus()", in);
            in.sendKeys(Keys.CONTROL + "a");
            in.sendKeys(Keys.DELETE);
            in.sendKeys(p);
            sleep(1500);
            for (WebElement o : driver.findElements(By.cssSelector("[id*='-option-'],[class*=__option],[role=option]")))
                try {
                    String t = o.getText().trim().split("\n")[0].trim();
                    boolean dis = "true".equals(o.getAttribute("aria-disabled")) || String.valueOf(o.getAttribute("class")).contains("disabled");
                    if (o.isDisplayed() && t.startsWith("М") && !dis && !o.getText().contains("bor")) r.add(t);
                } catch (Exception ignore) {}
        }
        in.sendKeys(Keys.ESCAPE);
        yop();
        return new ArrayList<>(r);
    }

    /** Mahsulot kartasidagi tabni ochadi. */
    void karta(String tab) {
        driver.get(kartaUrl(tab));
        sleep(1200);
        jadvalKut(driver);
    }

    /** "Hujjatlar va tarix" dagi qatorlar (yangisi birinchi). */
    List<String> tarix() {
        karta("history");
        List<String> r = new ArrayList<>();
        for (WebElement row : driver.findElements(By.cssSelector("main tbody tr"))) r.add(clean(row.getText()));
        return r;
    }

    boolean tarixdaBor(String... sozlar) {
        for (String q : tarix()) {
            boolean hammasi = true;
            for (String s : sozlar) if (!norm(q).contains(norm(s))) { hammasi = false; break; }
            if (hammasi) return true;
        }
        return false;
    }

    /** "Listinglar" tabida listing raqami bo'yicha qator. */
    WebElement listingQatori(String raqam) {
        karta("listings");
        sahifada100(driver);
        return waitFor(() -> {
            for (WebElement r : driver.findElements(By.cssSelector("main tbody tr")))
                if (uz.ebirja.pages.Jadval.ustun(driver, r, "Listing №").matches(raqam + "(\\D.*)?")) return r; // katakda "Subsidiya so'ralgan" ham bo'lishi mumkin
            return null;
        }, 10);
    }

    String listingUstun(String raqam, String ustun) {
        WebElement r = listingQatori(raqam);
        return r == null ? null : uz.ebirja.pages.Jadval.ustun(driver, r, ustun);
    }

    /** Listing qatoridagi "Amallar" menyusidan amalni tanlaydi. */
    boolean listingAmali(String raqam, String amal) {
        WebElement r = listingQatori(raqam);
        Assert.assertNotNull(r, "Listinglar tabida №" + raqam + " yo'q");
        List<WebElement> td = r.findElements(By.cssSelector("td"));
        List<WebElement> b = td.get(td.size() - 1).findElements(By.cssSelector("button"));
        jsClick(driver, b.get(b.size() - 1));
        WebElement item = waitFor(() -> lastVisible(driver, By.xpath("//*[not(self::main)][starts-with(normalize-space(.),\"" + amal + "\")][not(*) or self::button or self::li or @role='menuitem']")), 5);
        if (item == null) { new org.openqa.selenium.interactions.Actions(driver).sendKeys(Keys.ESCAPE).perform(); return false; }
        jsClick(driver, item);
        return dialog() != null;
    }

    /** Listinglar → "Cheklov tayinlash": kontrakt xaridorlari varianti (+ qo'shimcha belgilar). */
    boolean cheklovTayinla(String raqam, String variant, java.util.function.Consumer<Void> qoshimcha) {
        Assert.assertTrue(listingAmali(raqam, "Cheklov tayinlash"), "'Cheklov tayinlash' oynasi ochilmadi (№" + raqam + ")");
        if (variant != null) Assert.assertTrue(belgila("radio", variant, "buyers", true), "'" + variant + "' tanlanmadi");
        if (qoshimcha != null) qoshimcha.accept(null);
        List<String> x = new ArrayList<>();
        boolean ok = dlgSaqla("Saqlash", x);
        System.out.println("  Cheklov tayinlash №" + raqam + " -> " + variant + ": " + (ok ? "saqlandi" : "SAQLANMADI") + " " + x);
        if (!ok) { dump("cheklov_tayinlash_" + raqam); yop(); }
        return ok;
    }

    /** Xaridorlar ro'yxatiga korxona qo'shadi. Limit null bo'lsa limitsiz. 4% faqat oltinda. */
    boolean xaridorQosh(String stir, String limit, boolean foiz4, List<String> xabarlar) {
        karta("buyers");
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//main//button[starts-with(normalize-space(.),\"Xaridor qo\")]")), 15));
        Assert.assertNotNull(dialog(), "'Xaridor qo'shish' oynasi ochilmadi");
        WebElement tin = waitFor(() -> lastVisible(driver, By.cssSelector("[role=dialog] [name='tin'], [class*=modal] [name='tin']")), 6);
        if (tin != null) {
            js().executeScript("arguments[0].focus()", tin);
            tin.sendKeys(stir);
            sleep(1500);
        } else {
            String t = dlgSelect("Korxona", stir, s -> s.contains(stir));
            Assert.assertNotNull(t, "Korxona " + stir + " ro'yxatda topilmadi");
        }
        if (limit != null) {
            dlgYoz("limit1", limit);
            WebElement lf = lastVisible(driver, By.cssSelector("[role=dialog] [name='limit1'], [class*=modal] [name='limit1']"));
            oxirgiLimitMaydoni = lf == null ? null : lf.getAttribute("value");
            if (birlik != null) {
                String u = nomSelect("unitType1", birlik);
                if (u == null) System.out.println("  (limit birligi '" + birlik + "' ro'yxatda yo'q)");
            }
        }
        if (foiz4) belgila("checkbox", "4% belgisi", null, true);
        dlgFayl(); // sayt xaridor qo'shishda hujjatni majburiy qilgan ("Kamida bitta fayl yuklashingiz kerak")
        boolean ok = dlgSaqla("Saqlash", xabarlar);
        if (!ok) { if (xabarlar != null) xabarlar.addAll(dlgXatolar()); dump("xaridor_qoshish_" + stir); yop(); }
        return ok;
    }

    /** Xaridorlar ro'yxatidagi qator (STIR bo'yicha). */
    WebElement xaridorQatori(String stir) {
        karta("buyers");
        sahifada100(driver);
        return waitFor(() -> uz.ebirja.pages.Jadval.qator(driver, stir), 6);
    }

    // ====================================================================== treyder: kontrakt
    /** Dilshod: bitta kontrakt yaratib yuboradi; yuborilgan vaqtini qaytaradi. */
    String kontraktYubor(String kod, java.util.function.Consumer<KontraktForma> qoshimcha, String... oldingilar) {
        String base = Config.traderUrl();
        LocalDateTime boshlandi = LocalDateTime.now();
        String belgi = "REG-" + kod + "-" + boshlandi.format(DateTimeFormatter.ofPattern("MMddHHmmss"));
        KontraktForma f = new KontraktForma(driver, PRODUCT).ochish(base);
        // eski qoralama qolgan bo'lsa tozalanadi
        WebElement toz = visible(driver, By.xpath("//main//button[normalize-space(.)='Tozalash']"));
        if (toz != null && driver.getCurrentUrl().contains("draftId")) { jsClick(driver, toz); sleep(1500); }
        formaMahsulot(); // aniq nom bo'yicha (masalan "Мастика" - "Битумная мастика" emas)
        f.togriToldirish(true);
        for (String m : new String[]{"productLocationUz", "productLocationUzc", "productLocationRu"}) f.typeIfEmpty(m, "Avtotest manzil");
        if (guruh == null) guruh = formaQiymat("Guruhi");
        if (birlik == null) birlik = formaQiymat("O'lchov birligi");
        if (qoshimcha != null) qoshimcha.accept(f);
        if (visible(driver, By.name("specialConditionUz")) != null) f.set("specialConditionUz", belgi);
        Assert.assertEquals(formaQiymat("Mahsulot"), PRODUCT, "Formada mahsulot tanlanmadi");
        f.yuborishniBosish();
        Eimzo.typePassword();
        Object ok = waitFor(() -> driver.getCurrentUrl().contains("/create") ? null : Boolean.TRUE, 45);
        if (ok == null) {
            // ba'zan imzodan keyin forma jim turib qoladi (xato yo'q): "Yuborish" bir marta qayta bosiladi
            dump("kontrakt_yuborilmadi_" + kod + "_1");
            System.out.println("1) " + kod + ": forma yuborilmadi, qayta yuborilmoqda. Xabarlar: " + toasts(driver));
            f.yuborishniBosish();
            Eimzo.typePassword();
            ok = waitFor(() -> driver.getCurrentUrl().contains("/create") ? null : Boolean.TRUE, 60);
        }
        if (ok == null) dump("kontrakt_yuborilmadi_" + kod);
        Assert.assertNotNull(ok, "Kontrakt " + kod + " yuborilmadi. Xabarlar: " + toasts(driver) + " " + f.xatolar());
        String vaqt = yuborilganVaqt(base, boshlandi, oldingilar);
        System.out.println("1) " + kod + ": kontrakt yuborildi (" + PRODUCT + ", " + belgi + ", " + vaqt + ")");
        return vaqt;
    }

    /** Formadagi "Mahsulot" ro'yxatidan AYNAN shu nomli variantni tanlaydi. */
    void formaMahsulot() {
        WebElement in = (WebElement) js().executeScript(
            "for(const i of document.querySelectorAll('main input[id^=react-select]')){ let e=i; for(let k=0;k<8&&e;k++){ e=e.parentElement; const l=e.querySelector('label'); if(l){ if(l.innerText.trim().startsWith('Mahsulot')) return i; break; } } } return null;");
        Assert.assertNotNull(in, "Formada 'Mahsulot' maydoni yo'q");
        js().executeScript("arguments[0].scrollIntoView({block:'center'}); arguments[0].focus();", in);
        in.sendKeys(PRODUCT);
        WebElement op = waitFor(() -> { for (WebElement o : driver.findElements(By.cssSelector("[id*='-option-']"))) try { if (o.isDisplayed() && o.getText().trim().equals(PRODUCT)) return o; } catch (Exception ignore) {} return null; }, 8);
        Assert.assertNotNull(op, "Formada '" + PRODUCT + "' mahsuloti yo'q");
        jsClick(driver, op);
        sleep(1200);
    }

    /** Treyder formasidagi ro'yxat qiymati (yorlig'i bo'yicha). */
    String formaQiymat(String label) {
        Object v = js().executeScript(
            "for(const i of document.querySelectorAll('main input[id^=react-select]')){ let e=i; for(let k=0;k<8&&e;k++){ e=e.parentElement; const l=e.querySelector('label'); if(l){ if(l.innerText.trim().replace(/[‘’`ʼ]/g,\"'\").startsWith(arguments[0])){ const c=i.closest('[class*=\"-container\"]'); const s=c&&c.querySelector('[class*=singleValue],[class*=single-value]'); return s? s.innerText.trim() : ''; } break; } } } return null;", label);
        return v == null ? null : String.valueOf(v);
    }

    String yuborilganVaqt(String base, LocalDateTime boshlandi, String... bundanBoshqa) {
        driver.get(base + "/uz/trader/listing/sales");
        WebElement tab = waitFor(() -> visible(driver, By.xpath("//main//*[starts-with(normalize-space(.),'Listingga yuborilgan')][self::button or @role='tab' or not(*)]")), 20);
        Assert.assertNotNull(tab, "'Listingga yuborilgan' tabi yo'q");
        jsClick(driver, tab);
        String vaqt = waitFor(() -> {
            for (WebElement r : driver.findElements(By.cssSelector("main tbody tr"))) {
                String x = uz.ebirja.pages.Jadval.ustun(driver, r, "Yuborilgan sana");
                if (!r.getText().contains(PRODUCT) || !x.matches("\\d{2}/\\d{2}/\\d{4} [|] \\d{2}:\\d{2}:\\d{2}")) continue;
                LocalDateTime t = LocalDateTime.parse(x.replace(" | ", " "), DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
                if (!t.isBefore(boshlandi.minusSeconds(30)) && !Arrays.asList(bundanBoshqa).contains(x)) return x;
            }
            return null;
        }, 30);
        Assert.assertNotNull(vaqt, "Yuborilgan kontrakt 'Listingga yuborilgan' ro'yxatida ko'rinmadi");
        return vaqt;
    }

    // ====================================================================== moderator: kontrakt tasdiqlash
    String kontraktIdTop(String vaqt) {
        String id = waitFor(() -> {
            for (WebElement r : driver.findElements(By.cssSelector("main tbody tr")))
                if (vaqt.equals(uz.ebirja.pages.Jadval.ustun(driver, r, "Ariza yuborilgan sana")) && r.getText().contains(PRODUCT)) {
                    String x = uz.ebirja.pages.Jadval.ustun(driver, r, "ID raqam");
                    if (x.matches("\\d+")) return x;
                }
            return null;
        }, 20);
        Assert.assertNotNull(id, "Moderator ro'yxatida " + vaqt + " da yuborilgan '" + PRODUCT + "' kontrakti topilmadi");
        return id;
    }

    WebElement idQatori(String id) {
        for (WebElement r : driver.findElements(By.cssSelector("main tbody tr")))
            if (id.equals(uz.ebirja.pages.Jadval.ustun(driver, r, "ID raqam"))) return r;
        return null;
    }

    /**
     * Kontraktni joriy rolda tasdiqlaydi. Oynada cheklov bloki bo'lsa: sozla.accept(dialog matni) chaqiriladi
     * (masalan "Cheklovlarni hozir tayinlash" belgisi va variant).
     */
    void tasdiqla(Kontrakt k, java.util.function.Consumer<String> sozla) {
        driver.get(Config.adminUrl() + "/uz/moderationv2/listing/contracts");
        waitFor(() -> visible(driver, By.cssSelector("main tbody tr")), 30);
        sleep(500);
        if (k.id == null) k.id = kontraktIdTop(k.vaqt);
        WebElement row = waitFor(() -> idQatori(k.id), 6);
        if (row == null) { // ro'yxatda boshqa testerlarning kontraktlari ko'p: 100 qator + ID bo'yicha qidiruv
            sahifada100(driver);
            row = waitFor(() -> idQatori(k.id), 4);
        }
        if (row == null) {
            WebElement s = visible(driver, By.cssSelector("main input[placeholder='Qidirish']"));
            if (s != null) { s.sendKeys(Keys.CONTROL + "a"); s.sendKeys(k.id); }
            row = waitFor(() -> idQatori(k.id), 10);
        }
        if (row == null) dump("tasdiqlash_qator_yoq_" + k.kod);
        Assert.assertNotNull(row, currentRole() + ": kontrakt ID " + k.id + " 'Yangi' ro'yxatida yo'q");
        List<WebElement> tds = row.findElements(By.cssSelector("td"));
        WebElement dots = tds.get(tds.size() - 1).findElement(By.cssSelector("button"));
        js().executeScript("arguments[0].scrollIntoView({block:'center'})", dots);
        dots.click();
        WebElement item = waitFor(() -> visible(driver, By.xpath("//*[normalize-space(text())='Tasdiqlash']")), 10);
        Assert.assertNotNull(item, currentRole() + ": '⋮' menyusida 'Tasdiqlash' yo'q");
        item.click();
        Assert.assertNotNull(dialog(), "Tasdiqlash oynasi ochilmadi");
        sleep(800);
        String matn = dialogMatn();
        if (sozla != null) sozla.accept(matn);
        WebElement ok = waitFor(() -> lastVisible(driver, By.xpath(DLG_X + "//button[normalize-space(.)='Tasdiqlash']")), 10);
        jsClick(driver, ok);
        Eimzo.typePassword(8);
        boolean muv = waitToast(driver, "Muvaffaqiyatli", 12);
        if (!muv) dump("tasdiqlanmadi_" + k.kod);
        Assert.assertTrue(muv, currentRole() + ": " + k + " tasdiqlanmadi. Xabarlar: " + toasts(driver) + " Oyna: " + cut(dialogMatn(), 300));
        System.out.println("2) " + currentRole() + " tasdiqladi: " + k);
    }

    String ochilganRaqam(String id) {
        driver.get(Config.adminUrl() + "/uz/moderationv2/listing/contracts?state=APPROVED");
        jadvalKut(driver);
        String r = waitFor(() -> {
            WebElement q = idQatori(id);
            if (q == null) { WebElement s = visible(driver, By.cssSelector("main input[placeholder='Qidirish']")); if (s != null) { s.sendKeys(Keys.CONTROL + "a"); s.sendKeys(id); sleep(1500); } return null; }
            String x = uz.ebirja.pages.Jadval.ustun(driver, q, "Kontrakt raqam");
            return x.matches("\\d+") ? x : null;
        }, 30);
        Assert.assertNotNull(r, "Kontrakt ID " + id + " 'Ochilgan kontraktlar' da yo'q");
        return r;
    }

    // ====================================================================== savdo vaqti (admin2)
    /** Savdo vaqtini ochadi; sayt yaratmasa (oldingi savdo endigina tugagan bo'lsa) 1 daqiqadan keyin qayta uriniladi (3 martagacha). */
    void savdoVaqtiniOch() {
        for (int u = 1; ; u++) {
            try { savdoVaqtiniOchBir(); return; }
            catch (AssertionError e) {
                System.out.println("3) savdo vaqti yaratilmadi (" + u + "-urinish): " + cut(e.getMessage(), 150) + " | xabarlar: " + toasts(driver));
                dump("savdo_vaqti_" + u);
                if (u >= 3) throw e;
                sleep(60_000);
            }
        }
    }

    void savdoVaqtiniOchBir() {
        kim("admin2");
        boolean kechki = LocalTime.now().isAfter(LocalTime.of(14, 0));
        String session = kechki ? "Kechki sessiya" : "Ertalabki sessiya";
        driver.get(Config.adminUrl() + "/uz/moderationv2/sales-schedule/auction-sales-2/trade-session");
        jadvalKut(driver);
        int n = 0;
        for (int t = 0; t < 20 && n < driver.findElements(By.cssSelector("main tbody tr")).size(); t++) if (!qatorniOchir(n)) n++;
        LocalDateTime b = LocalDateTime.now().withSecond(0).withNano(0).plusMinutes(1);
        elonStart = b;
        savdoStart = b.plusMinutes(ELON_MIN);
        savdoEnd = savdoStart.plusMinutes(SAVDO_MIN);
        String elonSeksiya = kechki ? "Sotish uchun e'lon berish (Kechki sessiya)" : "Sotish uchun e'lon berish (ertalabki sessiya)";
        savdoYarat(session, elonSeksiya, null, elonStart, savdoStart);
        savdoYarat(session, session, guruh == null || guruh.isBlank() ? "Boshqalar" : guruh, savdoStart, savdoEnd);
        System.out.println("3) Savdo: e'lon " + elonStart.format(HM) + "-" + savdoStart.format(HM) + ", savdo (" + guruh + ") " + savdoStart.format(HM) + "-" + savdoEnd.format(HM));
    }

    boolean qatorniOchir(int n) {
        List<WebElement> rows = driver.findElements(By.cssSelector("main tbody tr"));
        if (rows.size() <= n) return false;
        int oldin = rows.size();
        List<WebElement> tds = rows.get(n).findElements(By.cssSelector("td"));
        if (tds.isEmpty()) return false;
        realClick(driver, tds.get(tds.size() - 1).findElement(By.cssSelector("button")));
        WebElement del = waitFor(() -> lastVisible(driver, By.xpath("//*[not(*) and (contains(normalize-space(text()),'chirish') or normalize-space(text())='Удалить')]")), 4);
        if (del == null) { driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE); return false; }
        try { del.click(); } catch (Exception e) { jsClick(driver, del); }
        WebElement ok = waitFor(() -> lastVisible(driver, By.xpath(DLG_X + "//button[contains(normalize-space(.),'chirish') or normalize-space(.)='Ha' or normalize-space(.)='Tasdiqlash']")), 4);
        if (ok != null) jsClick(driver, ok);
        imzoYoki(() -> driver.findElements(By.cssSelector("main tbody tr")).size() < oldin, 8);
        return driver.findElements(By.cssSelector("main tbody tr")).size() < oldin;
    }

    void savdoYarat(String sessiya, String seksiya, String mahsulotGuruhi, LocalDateTime start, LocalDateTime end) {
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//main//button[contains(normalize-space(.),\"Savdo uchun e'lon berish\")]")), 15));
        Assert.assertNotNull(dialog(), "'Savdo uchun e'lon berish' oynasi ochilmadi");
        dialogTanlov("Nazorat turi", "Mahsulot guruhi");
        dialogTanlov("Sessiya turi", sessiya);
        dialogTanlov("Seksiya", seksiya);
        if (mahsulotGuruhi != null) dialogTanlov("Mahsulot guruhi", mahsulotGuruhi);
        setTime("startTime", start.format(HM));
        setTime("endTime", end.format(HM));
        jsClick(driver, lastVisible(driver, By.xpath(DLG_X + "//button[normalize-space(.)='Saqlash']")));
        imzoYoki(() -> lastVisible(driver, By.xpath(DLG_X + "//button[normalize-space(.)='Saqlash']")) == null, 12);
        driver.navigate().refresh();
        Object q = waitFor(() -> {
            for (WebElement r : driver.findElements(By.cssSelector("main tbody tr"))) {
                String x = norm(clean(r.getText()));
                if (x.contains(norm(mahsulotGuruhi != null ? mahsulotGuruhi : seksiya)) && x.contains(start.format(HM)) && x.contains(end.format(HM))) return Boolean.TRUE;
            }
            return null;
        }, 15);
        Assert.assertNotNull(q, "Savdo vaqti jadvalda ko'rinmadi: " + seksiya + " " + start.format(HM) + "-" + end.format(HM));
    }

    void dialogTanlov(String label, String variant) {
        String lbl = "//*[self::label or self::span or self::p][contains(normalize-space(.),'" + label + "')][not(ancestor::*[contains(@class,'option')])]";
        WebElement ctl = waitFor(() -> lastVisible(driver, By.xpath(lbl + "/following::*[contains(@class,'control') or contains(@class,'select') or self::input][1]")), 6);
        Assert.assertNotNull(ctl, "Oynada '" + label + "' ro'yxati yo'q");
        realClick(driver, ctl);
        String opt = "//*[contains(@class,'option') or contains(@id,'-option-') or @role='option'][normalize-space(.)=\"" + variant + "\"]";
        WebElement op = waitFor(() -> lastVisible(driver, By.xpath(opt)), 4);
        if (op == null) {
            WebElement in = ctl.getTagName().equals("input") ? ctl : ctl.findElements(By.cssSelector("input")).stream().findFirst().orElse(null);
            if (in != null) { in.sendKeys(variant.substring(0, Math.min(6, variant.length()))); op = waitFor(() -> lastVisible(driver, By.xpath(opt)), 4); }
        }
        Assert.assertNotNull(op, "'" + label + "' ro'yxatida '" + variant + "' yo'q");
        jsClick(driver, op);
        sleep(300);
    }

    void setTime(String id, String hhmm) {
        driver.findElement(By.id(id)).findElement(By.xpath("./..")).click();
        sleep(1200);
        WebElement popup = null;
        for (WebElement e : driver.findElements(By.cssSelector("div.fixed")))
            try { if (e.isDisplayed() && e.getAttribute("class").contains("z-[9999]")) popup = e; } catch (Exception ignore) {}
        Assert.assertNotNull(popup, "Vaqt tanlagich ochilmadi");
        List<WebElement> cols = popup.findElements(By.cssSelector("div.flex-col"));
        String[] hm = hhmm.split(":");
        for (int c = 0; c < 2; c++) {
            for (WebElement b : cols.get(c).findElements(By.cssSelector("button")))
                if (b.getText().trim().equals(hm[c])) { jsClick(driver, b); break; }
            sleep(500);
        }
        WebElement title = lastVisible(driver, By.xpath(DLG_X + "//*[normalize-space(text())='Savdoni tahrirlash' or normalize-space(text())=\"Savdo uchun e'lon berish\"][not(ancestor::button)]"));
        if (title != null) { realClick(driver, title); sleep(300); }
        Assert.assertEquals(driver.findElement(By.id(id)).getAttribute("value"), hhmm, id + " vaqti o'rnatilmadi");
    }

    // ====================================================================== savdoga chiqarish (Dilshod)
    void savdogaChiqar(Kontrakt k, String lots) {
        String base = Config.traderUrl();
        driver.get(base + "/uz/trader/sales/auction-sales?tradeTab=SELL");
        WebElement search = waitFor(() -> visible(driver, By.cssSelector("main input[placeholder='Qidirish']")), 30);
        search.sendKeys(k.raqam);
        WebElement row = waitFor(() -> {
            for (WebElement r : driver.findElements(By.cssSelector("main tbody tr")))
                if (r.isDisplayed() && k.raqam.equals(uz.ebirja.pages.Jadval.ustun(driver, r, "Kontrakt raqami"))) return r;
            return null;
        }, 10);
        if (row == null) {
            search.sendKeys(Keys.CONTROL + "a");
            search.sendKeys(PRODUCT);
            sleep(1500);
            sahifada100(driver);
            row = waitFor(() -> {
                for (WebElement r : driver.findElements(By.cssSelector("main tbody tr")))
                    if (r.isDisplayed() && k.raqam.equals(uz.ebirja.pages.Jadval.ustun(driver, r, "Kontrakt raqami"))) return r;
                return null;
            }, 10);
        }
        Assert.assertNotNull(row, "Savdoga chiqarish jadvalida " + k + " yo'q");
        WebElement gavel = row.findElements(By.cssSelector("td")).get(2).findElements(By.cssSelector("button, svg")).get(0);
        try { jsClick(driver, gavel); } catch (Exception e) { js().executeScript("arguments[0].dispatchEvent(new MouseEvent('click',{bubbles:true}))", gavel); }
        WebElement lot = waitFor(() -> visible(driver, By.name("graphicLot")), 15);
        Assert.assertNotNull(lot, "'Savdoga qo'yish' oynasi ochilmadi (" + k + ")");
        lot.click();
        lot.sendKeys(Keys.CONTROL + "a");
        lot.sendKeys(Keys.DELETE);
        lot.sendKeys(lots);
        jsClick(driver, lastVisible(driver, By.xpath("//button[normalize-space(.)='Saqlash']")));
        Set<String> yig = new LinkedHashSet<>();
        imzoYoki(() -> { yig.addAll(toasts(driver)); return yig.stream().anyMatch(x -> x.contains("Muvaffaqiyatli") || x.contains("Majburiy") || x.contains("xato") || x.contains("mumkin emas")); }, 12);
        Assert.assertTrue(yig.stream().anyMatch(x -> x.contains("Muvaffaqiyatli")), k + " savdoga chiqarilmadi: " + yig);
        System.out.println("4) " + k + ": " + lots + " lot savdoga qo'yildi");
    }

    // ====================================================================== taklif (Zarnigor)
    void savdoSahifasi() {
        driver.get(Config.traderUrl() + "/uz/trader/sales/auction-sales?tradeTab=SELL");
        sleep(1500);
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//*[normalize-space(text())='Sotib olish']")), 20));
        sleep(1500);
        Object typed = waitFor(() -> {
            WebElement s = visible(driver, By.cssSelector("main input[placeholder='Qidirish']"));
            if (s == null) return null;
            s.sendKeys(Keys.CONTROL + "a");
            s.sendKeys(PRODUCT);
            return Boolean.TRUE;
        }, 20);
        Assert.assertNotNull(typed, "'Sotib olish' tabida qidiruv yo'q");
        sleep(2000);
    }

    WebElement kartochka(String raqam) {
        for (WebElement nom : driver.findElements(By.xpath("//*[contains(text(),'" + PRODUCT + "')]"))) {
            try {
                if (!nom.isDisplayed()) continue;
                WebElement card = nom;
                for (int up = 0; up < 15; up++) {
                    card = card.findElement(By.xpath("./.."));
                    if (card.getText().contains(" lot") && card.findElements(By.cssSelector("svg")).size() >= 3) break;
                }
                if (java.util.regex.Pattern.compile("(^|[^0-9])" + raqam + "\\s*/").matcher(card.getText().split(PRODUCT, 2)[0]).find()) return card;
            } catch (StaleElementReferenceException | org.openqa.selenium.NoSuchElementException ignore) {}
        }
        return null;
    }

    List<WebElement> ochiqOynalar() {
        List<WebElement> r = new ArrayList<>();
        for (WebElement b : driver.findElements(By.xpath("//button[normalize-space(.)='Sotib olish']")))
            try { if (b.isDisplayed()) { WebElement p = b.findElement(By.xpath("./ancestor::*[.//input[@placeholder='Miqdor']][1]")); if (!r.contains(p)) r.add(p); } } catch (Exception ignore) {}
        return r;
    }

    /** Kartochkadan taklif oynasini ochib, mijozni tanlaydi. */
    WebElement taklifOynasi(Kontrakt k, String mijozStir) {
        List<WebElement> oldingi = ochiqOynalar();
        Object bosildi = waitFor(() -> {
            WebElement card = kartochka(k.raqam);
            if (card == null) return null;
            WebElement gavel = card.findElements(By.cssSelector("svg")).get(2);
            try { jsClick(driver, gavel.findElement(By.xpath("./ancestor-or-self::*[self::button or @role='button' or contains(@class,'cursor-pointer')][1]"))); }
            catch (org.openqa.selenium.NoSuchElementException e) { js().executeScript("arguments[0].dispatchEvent(new MouseEvent('click',{bubbles:true}))", gavel); }
            return Boolean.TRUE;
        }, 30);
        if (bosildi == null) dump("kartochka_yoq_" + k.kod);
        Assert.assertNotNull(bosildi, "Savdoda " + k + " kartochkasi chiqmadi");
        // aynan shu kontrakt oynasi ("<raqam> - <mahsulot>"): profilda oldingi run'dan qolgan eski oynalar ham bo'lishi mumkin
        WebElement panel = waitFor(() -> {
            for (WebElement p : ochiqOynalar()) try { if (java.util.regex.Pattern.compile("Kontrakt №\\s*" + k.raqam + "([^0-9]|$)").matcher(p.getText()).find()) return p; } catch (Exception ignore) {}
            return null;
        }, 15);
        if (panel == null) dump("taklif_oynasi_yoq_" + k.kod);
        Assert.assertNotNull(panel, "'Ariza yuborish' oynasi ochilmadi (" + k + ")");
        WebElement mijozIn = (WebElement) js().executeScript(
            "const p=arguments[0]; const lab=[...p.querySelectorAll('*')].find(e=>e.children.length===0 && e.innerText && e.innerText.trim()==='Mijoz');" +
            "if(!lab) return null; let row=lab.parentElement; for(let i=0;i<4&&row;i++){ const inp=row.querySelector('input[id^=react-select]'); if(inp) return inp; row=row.parentElement; } return null;", panel);
        Assert.assertNotNull(mijozIn, "Taklif oynasida 'Mijoz' maydoni topilmadi (" + k + ")");
        for (WebElement in : mijozIn == null ? List.<WebElement>of() : List.of(mijozIn)) {
            js().executeScript("arguments[0].focus()", in);
            in.sendKeys(mijozStir);
            WebElement op = waitFor(() -> {
                for (WebElement o : driver.findElements(By.cssSelector("[id*='-option-']"))) try { if (o.isDisplayed() && o.getText().contains(mijozStir)) return o; } catch (Exception ignore) {}
                return null;
            }, 6);
            Assert.assertNotNull(op, "Zarnigorning mijozlari orasida " + mijozStir + " yo'q");
            jsClick(driver, op);
            sleep(600);
        }
        return panel;
    }

    String maydonYoz(WebElement in, String v) {
        js().executeScript("arguments[0].focus()", in);
        in.sendKeys(Keys.CONTROL + "a");
        in.sendKeys(Keys.DELETE);
        if (!v.isEmpty()) in.sendKeys(v);
        String r = in.getAttribute("value");
        return r == null ? "" : r;
    }

    WebElement meningTakliflarim() {
        By by = By.xpath("//*[normalize-space(text())='Mening takliflarim']/ancestor::*[.//*[normalize-space(text())='Holati']][1]");
        WebElement p = lastVisible(driver, by);
        if (p != null) return p;
        List<?> btns = (List<?>) js().executeScript(
                "return [...document.querySelectorAll('button,[role=button],div')].filter(e=>{const r=e.getBoundingClientRect();" +
                "return r.width>20&&r.width<70&&r.height>20&&r.height<70&&r.right>innerWidth-90&&r.top>120&&e.querySelector('svg');}).sort((a,b)=>a.getBoundingClientRect().top-b.getBoundingClientRect().top);");
        if (!btns.isEmpty()) jsClick(driver, (WebElement) btns.get(0));
        return waitFor(() -> lastVisible(driver, by), 5);
    }

    /** "Mening takliflarim" qatorlari: har biri ustun nomi -> qiymat. */
    @SuppressWarnings("unchecked")
    List<Map<String, String>> takliflar() {
        WebElement p = meningTakliflarim();
        if (p == null) return List.of();
        Object o = js().executeScript(
            "const p=arguments[0]; const hs=[...p.querySelectorAll('[role=columnheader],th')].map(h=>h.innerText.trim());" +
            "const rows=[...p.querySelectorAll('[role=row],tbody tr')].filter(r=>!r.querySelector('[role=columnheader],th'));" +
            "return rows.map(r=>{ const c=[...r.querySelectorAll('[role=gridcell],[role=cell],td')]; const m={}; c.forEach((x,i)=>m[hs[i]||('c'+i)]=x.innerText.trim()); m['_text']=r.innerText.replace(/\\n/g,' | '); return m; });", p);
        List<Map<String, String>> r = new ArrayList<>();
        for (Object x : (List<Object>) o) r.add((Map<String, String>) x);
        return r;
    }

    String takliflarMatn() {
        StringBuilder s = new StringBuilder();
        for (Map<String, String> m : takliflar()) s.append(m.get("_text")).append("\n");
        return s.toString();
    }

    /**
     * Taklif beradi va "Mening takliflarim" dagi yangi qatorning "Holati" ni qaytaradi.
     * Yangi qator chiqmasa — "QABUL QILINMADI: <xabarlar>" qaytadi.
     */
    boolean maydonOzgardi;

    String taklifBer(Kontrakt k, String mijoz, String lot, String narx, String nima) {
        maydonOzgardi = false;
        int oldin = takliflar().size();
        WebElement p = taklifOynasi(k, mijoz);
        String lv = maydonYoz(p.findElement(By.cssSelector("input[placeholder='Miqdor']")), lot);
        String pv = "";
        if (narx != null) {
            List<WebElement> nx = p.findElements(By.cssSelector("input[placeholder='Narx'], input[name='price']"));
            if (nx.isEmpty()) nx = p.findElements(By.xpath(".//*[normalize-space(text())='Narx']/following::input[1]"));
            if (!nx.isEmpty()) pv = maydonYoz(nx.get(0), narx);
            if (!pv.replaceAll("\\s", "").equals(narx)) { maydonOzgardi = true; System.out.println("  narx maydoni '" + narx + "' ni qabul qilmadi, maydonda: '" + pv + "'"); }
        }
        Set<String> yig = new LinkedHashSet<>();
        jsClick(driver, p.findElement(By.xpath(".//button[normalize-space(.)='Sotib olish']")));
        imzoYoki(() -> { yig.addAll(toasts(driver)); return takliflar().size() > oldin; }, 8);
        yig.addAll(toasts(driver));
        List<Map<String, String>> t = takliflar();
        if (t.size() <= oldin) {
            String r = "QABUL QILINMADI (maydon: lot='" + lv + "' narx='" + pv + "') " + yig;
            if (yig.isEmpty()) dump("taklif_qabul_qilinmadi_" + k.kod);
            System.out.println("5) " + nima + " -> " + r);
            yopOyna(p);
            return r;
        }
        // yangi qator: holat (kod) chiqquncha biroz kutiladi
        String[] h = {""};
        waitFor(() -> {
            List<Map<String, String>> tt = takliflar();
            Map<String, String> m = tt.get(0);
            String x = holat(m);
            h[0] = x + " || " + m.get("_text");
            return x.isEmpty() ? null : Boolean.TRUE;
        }, 6);
        System.out.println("5) " + nima + " -> " + h[0] + " " + yig);
        yopOyna(p);
        return h[0];
    }

    String holat(Map<String, String> m) {
        for (Map.Entry<String, String> e : m.entrySet()) if (norm(e.getKey()).startsWith("Holat")) return e.getValue();
        return "";
    }

    void yopOyna(WebElement p) {
        try {
            WebElement x = p.findElement(By.xpath(".//button[not(normalize-space(.))][.//svg]"));
            if (x.isDisplayed()) jsClick(driver, x);
        } catch (Exception ignore) {}
    }

    /** Holati ustunidagi matn kodga mos keladimi (raqam yoki ma'nosi bo'yicha). */
    static boolean kodMi(String holat, int kod) {
        if (holat == null) return false;
        String b = holat.split(" [|][|] ")[0].toLowerCase();
        if (java.util.regex.Pattern.compile("(^|[^0-9])" + kod + "([^0-9]|$)").matcher(b).find()) return true;
        return switch (kod) {
            case 2 -> b.contains("qabul qilindi");
            case 4 -> b.contains("limit") || b.contains("kvota") || b.contains("oshib") || b.contains("oshdi") || b.contains("hajm");
            case 5 -> b.contains("spread") || b.contains("qimmat") || (b.contains("narx") && b.contains("oshmasligi"));
            case 6 -> b.contains("sotib olish mumkin emas") || b.contains("ruxsat");
            case 7 -> b.contains("koeffitsient");
            default -> false;
        };
    }

    void taklifKut(Kontrakt k, String mijoz, String lot, int kod, String nima) {
        if (LocalDateTime.now().isAfter(savdoEnd.minusSeconds(5))) { kutilgan(false, nima + ": savdo vaqti tugadi, tekshirilmadi"); return; }
        try {
            boolean yRoli = mijoz.equals(Y_STIR);
            if (yRoli) mijoz = yStir;
            String h = taklifBer(k, mijoz, lot, null, nima);
            // Y (ro'yxatda yo'q xaridor) ning hisobida pul tugasa — Zarnigorning keyingi mijozi bilan qayta (ular ham ro'yxatda yo'q)
            while (yRoli && h.toLowerCase().contains("yetarli emas") && yIndeks + 1 < Y_NOMZODLAR.size()) {
                pulsizMijozlar.add(yStir);
                yStir = Y_NOMZODLAR.get(++yIndeks);
                System.out.println("  Y o'rniga keyingi mijoz: " + yStir + " (oldingisida pul yetarli emas)");
                h = taklifBer(k, yStir, lot, null, nima + " [Y=" + yStir + "]");
            }
            if (h.toLowerCase().contains("yetarli emas")) pulsizMijozlar.add(mijoz);
            kutilgan(kodMi(h, kod), nima + " — kutilgan kod " + kod + ", natija: " + h);
        } catch (Throwable e) {
            dump("taklif_xato");
            kutilgan(false, nima + ": " + cut(String.valueOf(e.getMessage()), 200));
        }
    }

    /** Validatsiya: noto'g'ri narx/lot bilan taklif QABUL QILINMASLIGI kerak. */
    void taklifRad(Kontrakt k, String mijoz, String lot, String narx, String nima) {
        if (LocalDateTime.now().isAfter(savdoEnd.minusSeconds(5))) { kutilgan(false, nima + ": savdo vaqti tugadi, tekshirilmadi"); return; }
        try {
            String h = taklifBer(k, mijoz, lot, narx, nima);
            boolean rad = h.startsWith("QABUL QILINMADI") || !kodMi(h, 2) || maydonOzgardi;
            kutilgan(rad, nima + " — taklif qabul qilinmasligi kerak, natija: " + h);
        } catch (Throwable e) {
            kutilgan(false, nima + ": " + cut(String.valueOf(e.getMessage()), 200));
        }
    }

    // ====================================================================== tur testlari uchun umumiy qadamlar

    /** Reestrda yo'q M-mahsulotni tanlaydi (yoki -Dreg.<tur>.product=...). */
    void mahsulotTanla(String turKalit) {
        PRODUCT = Config.getOr("reg." + turKalit + ".product", null);
        if (PRODUCT == null) {
            List<String> nom = mNomzodlar();
            for (String n : nom) if (reestrQatori(n) == null) { PRODUCT = n; break; }
        }
        Assert.assertNotNull(PRODUCT, "Reestrda yo'q 'М' mahsulot topilmadi");
        System.out.println("MAHSULOT (" + turKalit + "): " + PRODUCT);
    }

    /**
     * Mahsulotni reestrga qo'shadi. tur: "Oddiy" / "Spirt" / "Oltin" / "Kumush"; cheklovlar: "Ro'yxat asosida", "Limit" ...
     * qoshimcha: oynada tur tanlangandan keyin bajariladi (masalan standart oltin turi).
     */
    void reestrgaQosh(String tur, String[] cheklovlar, Runnable qoshimcha) {
        driver.get(reestrUrl() + "?status=all");
        jadvalKut(driver);
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//main//button[starts-with(normalize-space(.),\"Mahsulot qo\")]")), 15));
        Assert.assertNotNull(dialog(), "'Mahsulot qo'shish' oynasi ochilmadi");
        Assert.assertNotNull(dlgSelect("Asosiy mahsulot", PRODUCT, s -> s.split("\n")[0].trim().equals(PRODUCT)), "Ro'yxatda " + PRODUCT + " yo'q");
        Assert.assertTrue(belgila("radio", tur, "productType", true), "Tovar turi '" + tur + "' tanlanmadi");
        sleep(500);
        if (qoshimcha != null) qoshimcha.run();
        for (String c : cheklovlar) Assert.assertTrue(belgila("checkbox", c, null, true), "'" + c + "' belgilanmadi");
        List<String> x = new ArrayList<>();
        boolean ok = dlgSaqla("Qo'shish", x);
        if (!ok) { dump("reestrga_qosh"); x.addAll(dlgXatolar()); yop(); }
        kutilgan(ok, "Reestrga qo'shildi: " + PRODUCT + " — " + tur + " " + Arrays.toString(cheklovlar) + " " + x);
        WebElement r = reestrQatori(PRODUCT);
        Assert.assertNotNull(r, PRODUCT + " reestrda ko'rinmadi");
        mahsulotId = r.findElement(By.cssSelector("a[href*='regulated-products/']")).getAttribute("href").replaceAll(".*regulated-products/(\\d+).*", "$1");
        System.out.println("Reestr: " + clean(r.getText()) + " (ID " + mahsulotId + ")");
    }

    /** Dilshod kontraktlarni yaratadi. qoshimcha: kod -> formadagi qo'shimcha amal (masalan "Subsidiya uchun"). */
    void kontraktlarYarat(Map<String, java.util.function.Consumer<KontraktForma>> kodlar) {
        kim("trader2");
        List<String> oldingi = new ArrayList<>();
        for (Map.Entry<String, java.util.function.Consumer<KontraktForma>> e : kodlar.entrySet()) {
            Kontrakt k = new Kontrakt(e.getKey());
            k.vaqt = kontraktYubor(e.getKey(), e.getValue(), oldingi.toArray(new String[0]));
            oldingi.add(k.vaqt);
            K.put(e.getKey(), k);
        }
        System.out.println("Guruh: " + guruh + ", birlik: " + birlik);
    }

    void kontraktlarYarat(String... kodlar) {
        Map<String, java.util.function.Consumer<KontraktForma>> m = new LinkedHashMap<>();
        for (String k : kodlar) m.put(k, null);
        kontraktlarYarat(m);
    }

    /**
     * Uch rolda tasdiqlaydi. sozla: kod -> (oyna matni) cheklov blokida bajariladigan amal;
     * kod uchun sozla yo'q bo'lsa "Cheklovlarni hozir tayinlash" olib tashlanadi.
     */
    void hammasiniTasdiqla(Map<String, java.util.function.Consumer<String>> sozla) {
        moderatorKir();
        Set<String> dumpQilindi = new HashSet<>();
        try {
            for (String role : new String[]{"MODERATOR_LISTING_MAIN", "MODERATOR_LISTING", "MODERATOR_LISTING_MEMBER"}) {
                switchRole(role);
                for (Kontrakt k : K.values()) {
                    tasdiqla(k, matn -> {
                        if (!matn.contains("Cheklovlarni hozir tayinlash")) return;
                        if (dumpQilindi.add(k.kod)) dump("tasdiqlash_oyna_" + k.kod);
                        java.util.function.Consumer<String> s = sozla.get(k.kod);
                        belgila("checkbox", "Cheklovlarni hozir tayinlash", null, s != null);
                        sleep(400);
                        if (s != null) s.accept(dialogMatn());
                        WebElement koef = lastVisible(driver, By.cssSelector("[role=dialog] [name='coefficient'], [class*=modal] [name='coefficient']"));
                        if (s != null && koef != null && (koef.getAttribute("value") == null || koef.getAttribute("value").isEmpty())) {
                            dlgYoz("coefficient", "1");
                            try { dlgSelect("Konvertatsiya", birlik, x -> x.equalsIgnoreCase(birlik)); } catch (Throwable ignore) {}
                        }
                    });
                }
            }
        } finally {
            switchRole("MODERATOR_LISTING_MAIN");
        }
        for (Kontrakt k : K.values()) k.raqam = ochilganRaqam(k.id);
        System.out.println("Kontraktlar: " + K.values());
    }

    /** Admin savdo vaqtini ochadi, Dilshod hamma kontraktni savdoga chiqaradi, Zarnigor savdo sahifasini ochib kutadi. */
    void savdoniBoshla() {
        savdoVaqtiniOch();
        kim("trader1");
        kim("trader2");
        waitUntil(elonStart.plusSeconds(5), "e'lon vaqti");
        for (Kontrakt k : K.values()) savdogaChiqar(k, LOTS);
        savdoQoyildi = true;
        kim("trader1");
        savdoSahifasi();
        waitUntil(savdoStart.plusSeconds(3), "savdo boshlanishi");
        savdoSahifasi();
    }

    /** Oynadagi radio yoki checkbox holati: null = yo'q, aks holda [tanlangan, o'chirilgan(disabled)]. */
    boolean[] belgiHolati(String tur, String boshi, String name) {
        WebElement i = belgi(tur, boshi, name);
        if (i == null) return null;
        return new boolean[]{i.isSelected(), disabled(i)};
    }
}
