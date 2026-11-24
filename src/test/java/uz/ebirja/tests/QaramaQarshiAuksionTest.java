package uz.ebirja.tests;

import org.openqa.selenium.*;
import org.testng.Assert;
import org.testng.annotations.Test;
import uz.ebirja.base.Eimzo;
import uz.ebirja.base.Moderator;
import uz.ebirja.config.Config;
import uz.ebirja.pages.KontraktForma;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static uz.ebirja.base.Ui.*;

/**
 * QARAMA-QARSHI AUKSION (buyurtmalar kitobi): sotuvchi va xaridor narx qo'yadi, narxlar mos kelsa bitim DARHOL tuziladi
 * (savdo tugashini kutmaydi, e'lon vaqti yo'q). Bitim raqamlari "A2600...." ko'rinishida.
 *
 * 1) Moderator (LISTING_MAIN) kontrakt formasida salbiy holatlar   2) shu rolda kontrakt yaratadi
 * 3) LISTING roli tasdiqlaydi -> "Ochilgan kontraktlar", savdo raqami (Kontrakt №) olinadi
 * 4) Admin sessiya vaqtini qo'yadi (hozirdan N daqiqa)
 * 5) Savdo vaqtidagi salbiy holatlar (minus/0 narx va miqdor, past narx -> bitim yo'q, o'ziga o'zi sotish)
 * 6) Ijobiy: 7 ta 1 lotli bitim, qisman bajarilish (miqdor yetmasa bor lot bilan), yuqori narxdagi xarid; balanslar
 * 7) Bitimlar yig'iladi  8) Ikkala treyder ommaviy imzolaydi  9) Admin ommaviy tasdiqlaydi  10) Salbiy holatlar yakuni
 * Keyin BitimHolatlariTest shu 1 lotli bitimlarda 7 ta stsenariyni (to'lov, YBHR, bekor qilish, muddatlar) bajaradi.
 */
public class QaramaQarshiAuksionTest extends SavdoAsos {
    static final String PRODUCT = Config.getOr("qarama.product", Config.getOr("auction.product", "Асбестовый шнур"));
    static final int SAVDO_DAQ = Integer.parseInt(Config.getOr("qarama.savdoMinutes", "30"));
    static final int BITIMLAR = Integer.parseInt(Config.getOr("qarama.bids", "7"));
    static final long NARX = 1_000_000L;               // 1 lot narxi (kontraktdagi bilan bir xil)
    static final long ZAKALAT = NARX * 10 / 100;       // 10% zakalat
    static final long KOMISSIYA = 800;                 // birja vositachilik yig'imi (0,08%)
    static final String SOTUVCHI_MIJOZ = Config.getOr("qarama.sellerClient", "NEXTGEN");
    static final DateTimeFormatter SANA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    String kontraktId;        // moderator ro'yxatidagi ID
    String savdoRaqami;       // treyder kartochkasidagi "Kontrakt №"
    LocalDateTime sessiyaBosh;
    Map<String, long[]> bitimlar = new LinkedHashMap<>(); // A26.. -> {narx, lot}

    // ================================================================== 1. kontrakt formasi: salbiy holatlar
    @Test(priority = 1, description = "Moderator qarama-qarshi auksion kontrakti formasida salbiy holatlar")
    public void q1_kontraktNegativ() {
        AuctionFlowTest.SAVDO_ISHLADI = true;
        kim("admin1");
        Moderator.rolAlmashtir(driver, "MODERATOR_LISTING_MAIN");
        KontraktForma f = forma();
        // minus qiymatlar
        for (String name : new String[]{"mainPrice", "amount", "sellerDepositPercentage", "buyerDepositPercentage", "deliveryTerm", "paymentTerm"}) {
            String v = f.set(name, "-500");
            System.out.println("1) " + name + " = -500 -> maydonda: '" + v + "'");
            if (v != null && v.contains("-")) negativXatolar.add("Kontrakt: " + name + " minus qiymatni qabul qildi: " + v);
            f.set(name, KontraktForma.TOGRI.getOrDefault(name, "10"));
        }
        // zakalat 0% va 15%
        for (String name : new String[]{"sellerDepositPercentage", "buyerDepositPercentage"})
            for (String q : new String[]{"0", "15"}) {
                String v = f.set(name, q);
                if (!q.equals(v == null ? "" : v.replace(" ", ""))) { System.out.println("1) " + name + " = " + q + " -> maydon qabul qilmadi ('" + v + "') ✓"); }
                else radEtilsin(f, "Kontrakt: " + name + " = " + q + "%");
                f.set(name, "10");
            }
        // bo'sh forma
        f = new KontraktForma(driver, PRODUCT).ochishUrl(formaUrl());
        radEtilsin(f, "Kontrakt: bo'sh forma");
        // quyidagilarni admin formasi imzodan keyin (serverda) tekshiradi -> parol kiritiladi, rad etilishi kutiladi
        f = new KontraktForma(driver, PRODUCT).ochishUrl(formaUrl());
        toldir(f, false);
        serverRadEtsin(f, "Kontrakt: hujjat yuklanmagan", null);
        f = forma();
        f.set("mainPrice", "0");
        serverRadEtsin(f, "Kontrakt: narx 0", "musbat");
    }

    /** Haqiqiy imzo bilan yuboradi; kontrakt yaratilmasligi (sahifa /create da qolishi) va xato xabari chiqishi kerak. */
    void serverRadEtsin(KontraktForma f, String holat, String kutilganXabar) {
        f.yuborishniBosish();
        Eimzo.typePassword(15);
        // xabarlar tez yo'qoladi: 12 s davomida yig'amiz
        Set<String> yig = new LinkedHashSet<>();
        Object ketdi = null;
        for (long end = System.currentTimeMillis() + 12_000; System.currentTimeMillis() < end; ) {
            yig.addAll(toasts(driver));
            if (!driver.getCurrentUrl().contains("/create")) { ketdi = Boolean.TRUE; break; }
            if (kutilganXabar != null && yig.stream().anyMatch(s -> s.contains(kutilganXabar))) break;
            sleep(300);
        }
        List<String> x = new ArrayList<>(yig);
        boolean xabar = kutilganXabar == null ? !x.isEmpty() : x.stream().anyMatch(s -> s.contains(kutilganXabar));
        System.out.println("1) " + holat + " (imzo bilan) -> " + x + (ketdi == null && xabar ? " ✓" : ""));
        if (ketdi != null) negativXatolar.add(holat + ": kontrakt YARATILDI (sayt rad etmadi)");
        else if (!xabar) negativXatolar.add(holat + ": rad xabari chiqmadi. Xabarlar: " + x);
    }

    String formaUrl() { return Config.adminUrl() + "/uz/moderationv2/listing/reverse-contracts/create"; }

    KontraktForma forma() {
        KontraktForma f = new KontraktForma(driver, PRODUCT).ochishUrl(formaUrl());
        toldir(f, true);
        return f;
    }

    void toldir(KontraktForma f, boolean fayl) {
        f.comboToldir(new HashMap<>());
        KontraktForma.TOGRI.forEach(f::typeIfEmpty);
        f.typeIfEmpty("productLocationUz", "Avtotest manzil");
        f.comboToldir(new HashMap<>());
        if (fayl) f.faylYuklash();
    }

    /** "Yuborish" bosilganda imzo so'ralmasligi (forma rad etishi) kerak. Imzo so'rovi to'siladi. */
    void radEtilsin(KontraktForma f, String holat) {
        f.eimzoImzoniTosish();
        long oldin = f.eimzoUrinishlari();
        f.yuborishniBosish();
        waitFor(() -> f.eimzoUrinishlari() > oldin ? Boolean.TRUE : null, 4);
        boolean oyna = Eimzo.closeIfShown(1);
        long urinish = f.eimzoUrinishlari() - oldin;
        System.out.println("1) " + holat + " -> " + f.xatolar() + " | imzo so'rovi: " + urinish + (urinish == 0 && !oyna ? " ✓" : ""));
        if (urinish > 0 || oyna) negativXatolar.add(holat + ": forma o'tdi va imzo so'radi. Xabarlar: " + f.xatolar());
    }

    // ================================================================== 2. kontrakt yaratish
    @Test(priority = 2, description = "Moderator (LISTING_MAIN) qarama-qarshi auksion kontraktini yaratadi")
    public void q2_kontraktYaratish() {
        kim("admin1");
        Moderator.rolAlmashtir(driver, "MODERATOR_LISTING_MAIN");
        KontraktForma f = forma();
        System.out.println("2) Forma: " + f.comboQiymatlari().get("mainProductId") + ", narx " + f.value("mainPrice"));
        f.yuborishniBosish();
        Eimzo.typePassword(15);
        Assert.assertNotNull(waitFor(() -> driver.getCurrentUrl().contains("/create") ? null : Boolean.TRUE, 30),
                "Kontrakt yuborilmadi. Xabarlar: " + toasts(driver) + " " + f.xatolar());
        String s = null;
        for (int i = 0; i < 3 && s == null; i++)
            try { WebElement r = kontraktQatori("Yangi"); s = r == null ? null : qator(r); } catch (StaleElementReferenceException e) { sleep(1000); }
        Assert.assertNotNull(s, "Bugungi kontrakt 'Yangi' tabida topilmadi");
        Matcher m = Pattern.compile("^\\S+ \\| \\S+ \\| \\S+ \\| \\S+ \\| (\\d+) \\|").matcher(s);
        Assert.assertTrue(m.find(), "Kontrakt ID o'qilmadi: " + s);
        kontraktId = m.group(1);
        System.out.println("2) Kontrakt yaratildi, ID " + kontraktId + " (LISTING_MAIN tasdig'i bilan)");
    }

    String qator(WebElement r) { return clean(r.getText()).replace("\n", " | "); }

    /** Admin ro'yxatidan (tab) bugungi, shu mahsulotli eng katta ID li qator. */
    WebElement kontraktQatori(String tab) {
        driver.get(Config.adminUrl() + "/uz/moderationv2/listing/reverse-contracts");
        WebElement t = waitFor(() -> visible(driver, By.xpath("//main//*[starts-with(normalize-space(.),'" + tab + "')][not(*) or self::button]")), 20);
        if (t != null) { jsClick(driver, t); sleep(2000); }
        WebElement q = waitFor(() -> visible(driver, By.cssSelector("main input[placeholder='Qidirish']")), 15);
        q.sendKeys(PRODUCT);
        q.sendKeys(Keys.ENTER);
        sleep(1500); // qidiruvdan keyin jadval qayta chiziladi
        String bugun = LocalDate.now().format(SANA);
        return waitFor(() -> {
            WebElement eng = null;
            int engId = -1;
            for (WebElement r : driver.findElements(By.cssSelector("main tbody tr"))) {
                String s;
                try { s = qator(r); } catch (StaleElementReferenceException e) { return null; }
                Matcher m = Pattern.compile("^\\S+ \\| \\S+ \\| \\S+ \\| \\S+ \\| (\\d+) \\|").matcher(s);
                if (s.startsWith(bugun) && m.find() && (kontraktId == null || m.group(1).equals(kontraktId)) && Integer.parseInt(m.group(1)) > engId) {
                    engId = Integer.parseInt(m.group(1));
                    eng = r;
                }
            }
            return eng;
        }, 20);
    }

    // ================================================================== 3. tasdiqlash
    @Test(priority = 3, dependsOnMethods = "q2_kontraktYaratish", description = "LISTING roli kontraktni tasdiqlaydi, savdo raqami olinadi")
    public void q3_tasdiqlash() {
        kim("admin1");
        try {
            Moderator.rolAlmashtir(driver, "MODERATOR_LISTING");
            WebElement r = kontraktQatori("Yangi");
            Assert.assertNotNull(r, "LISTING: kontrakt ID " + kontraktId + " 'Yangi' tabida yo'q");
            List<WebElement> tds = r.findElements(By.cssSelector("td"));
            WebElement dots = tds.get(tds.size() - 1).findElement(By.cssSelector("button"));
            js().executeScript("arguments[0].scrollIntoView({block:'center'})", dots);
            dots.click();
            WebElement item = waitFor(() -> visible(driver, By.xpath("//*[normalize-space(text())='Tasdiqlash']")), 10);
            Assert.assertNotNull(item, "LISTING: menyuda 'Tasdiqlash' yo'q");
            item.click();
            WebElement ok = waitFor(() -> lastVisible(driver, By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button[normalize-space(.)='Tasdiqlash']")), 10);
            if (ok != null) jsClick(driver, ok);
            Eimzo.typePassword(10);
            Assert.assertTrue(waitToast(driver, "Muvaffaqiyatli", 15), "LISTING: tasdiqlanmadi. Xabarlar: " + toasts(driver));
            String os = null;
            for (int i = 0; i < 3 && os == null; i++) try { WebElement o = kontraktQatori("Ochilgan"); os = o == null ? null : qator(o); } catch (StaleElementReferenceException e) { sleep(1000); }
            Assert.assertNotNull(os, "Kontrakt " + kontraktId + " 'Ochilgan kontraktlar' ga o'tmadi");
            Matcher m = Pattern.compile("\\| " + kontraktId + " \\| (\\d+) \\|").matcher(os);
            Assert.assertTrue(m.find(), "Savdo raqami o'qilmadi: " + os);
            savdoRaqami = m.group(1);
            System.out.println("3) LISTING tasdiqladi; kontrakt ID " + kontraktId + " -> savdo raqami (Kontrakt №) " + savdoRaqami);
        } finally {
            Moderator.rolAlmashtir(driver, "MODERATOR_LISTING_MAIN");
        }
    }

    // ================================================================== 4. sessiya
    @Test(priority = 4, dependsOnMethods = "q3_tasdiqlash", description = "Admin qarama-qarshi auksion sessiyasini hozirdan N daqiqaga qo'yadi")
    public void q4_sessiya() {
        kim("admin2");
        String sessiya = LocalTime.now().isAfter(LocalTime.of(14, 0)) ? "Kechki sessiya" : "Ertalabki sessiya";
        driver.get(Config.adminUrl() + "/uz/moderationv2/sales-schedule/reverse-auction-session");
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//main//button[normalize-space(.)='Savdo sessiyasi']")), 20));
        WebElement tur = waitFor(() -> visible(driver, By.id("sessionType")), 10);
        js().executeScript("arguments[0].focus()", tur);
        tur.sendKeys(Keys.ARROW_DOWN);
        WebElement op = waitFor(() -> visible(driver, By.xpath("//*[contains(@class,'frontend-library-select__option')][normalize-space(.)='" + sessiya + "']")), 5);
        Assert.assertNotNull(op, "Sessiya turi '" + sessiya + "' yo'q");
        jsClick(driver, op);
        sessiyaBosh = LocalDateTime.now().withSecond(0).withNano(0);
        String bosh = sessiyaBosh.format(HM), oxir = sessiyaBosh.plusMinutes(SAVDO_DAQ).format(HM);
        WebElement s = driver.findElement(By.id("start")), e = driver.findElement(By.id("end"));
        s.click(); s.sendKeys(bosh.replace(":", ""));
        e.click(); e.sendKeys(oxir.replace(":", ""));
        jsClick(driver, visible(driver, By.xpath("//button[normalize-space(.)='Saqlash']")));
        sleep(1500);
        driver.navigate().refresh();
        String kutilgan = sessiya + " | " + bosh + " | " + oxir;
        Object ok = waitFor(() -> { for (WebElement r : driver.findElements(By.cssSelector("main tbody tr"))) if (qator(r).contains(kutilgan)) return Boolean.TRUE; return null; }, 15);
        Assert.assertNotNull(ok, "Sessiya saqlanmadi: kutilgan " + kutilgan);
        System.out.println("4) " + sessiya + ": " + bosh + " - " + oxir);
    }

    // ================================================================== 5-6. savdo
    /** Treyder sahifasi va shu kontraktning buyurtma oynasi (har treyderning o'z brauzerida bir marta ochiladi). */
    WebElement panel(String who) {
        kim(who);
        WebElement p = shuKontraktOynasi();
        if (p != null) return p;
        driver.get(Config.traderUrl() + "/uz/trader/sales/reverse-auction-sales");
        WebElement q = waitFor(() -> visible(driver, By.cssSelector("main input[placeholder='Qidirish']")), 20);
        q.sendKeys(Keys.CONTROL + "a");
        q.sendKeys(Keys.DELETE);
        q.sendKeys(PRODUCT);
        q.sendKeys(Keys.ENTER);
        sleep(1500); // qidiruv natijasi chizilib bo'lsin
        // ro'yxat qayta chizilishi mumkin -> bolg'achani 3 martagacha bosamiz
        for (int urinish = 0; urinish < 3 && p == null; urinish++) {
            WebElement link = waitFor(() -> visible(driver, By.xpath("//main//*[normalize-space(text())='" + savdoRaqami + "']")), 20);
            Assert.assertNotNull(link, who + ": savdoda " + savdoRaqami + "-kontrakt kartochkasi yo'q");
            try {
                WebElement card = (WebElement) js().executeScript("let e=arguments[0]; while(e && !(e.innerText||'').includes('1 lot:')) e=e.parentElement; e.scrollIntoView({block:'center'}); return e;", link);
                sleep(400);
                List<WebElement> svgs = card.findElements(By.xpath(".//*[name()='svg']"));
                // belgilar: hujjat, yulduz, bolg'acha (buyurtma), qo'l (buyurtmalar kitobi)
                WebElement gavel = svgs.get(svgs.size() - 2);
                try { new org.openqa.selenium.interactions.Actions(driver).moveToElement(gavel).click().perform(); }
                catch (Exception e) {
                    js().executeScript("let r=arguments[0].getBoundingClientRect(); let e=document.elementFromPoint(r.x+r.width/2, r.y+r.height/2); let b=(e.closest&&e.closest('button,a,[role=button],svg'))||e;" +
                            "['mousedown','mouseup','click'].forEach(t=>b.dispatchEvent(new MouseEvent(t,{bubbles:true})));", gavel);
                }
            } catch (StaleElementReferenceException ignore) {}
            p = waitFor(this::shuKontraktOynasi, 6);
        }
        Assert.assertNotNull(p, who + ": " + savdoRaqami + "-kontrakt buyurtma oynasi ochilmadi");
        return p;
    }

    /**
     * Ochiq buyurtma oynalaridan aynan shu kontraktnikini ("Kontrakt №" = savdoRaqami) qaytaradi.
     * Sahifa avvalgi oynalarni (boshqa kontraktlarni) eslab qoladi — ularga buyurtma ketib qolmasin.
     */
    WebElement shuKontraktOynasi() {
        By pb = By.xpath("//*[.//input[@placeholder='Miqdor'] and .//button[normalize-space(.)='Sotish']][not(.//*[.//input[@placeholder='Miqdor'] and .//button[normalize-space(.)='Sotish']])]");
        for (WebElement p : driver.findElements(pb))
            try {
                if (p.isDisplayed() && Pattern.compile("Kontrakt №\\s+" + savdoRaqami + "\\b").matcher(p.getText()).find()) return p;
            } catch (StaleElementReferenceException ignore) {}
        return null;
    }

    /** Mijozni tanlaydi (matn bo'yicha; bo'sh bo'lsa birinchisi), erkin mablag'ni qaytaradi. */
    long mijozTanla(WebElement p, String mijoz) {
        WebElement in = p.findElement(By.xpath(".//*[normalize-space(text())='Mijoz']/following::input[1]"));
        js().executeScript("arguments[0].focus()", in);
        in.sendKeys(Keys.ARROW_DOWN);
        WebElement op = waitFor(() -> {
            for (WebElement o : driver.findElements(By.cssSelector("[id*='-option-']")))
                try { if (o.isDisplayed() && (mijoz.isEmpty() || o.getText().contains(mijoz))) return o; } catch (Exception ignore) {}
            return null;
        }, 8);
        Assert.assertNotNull(op, "Mijoz '" + mijoz + "' ro'yxatda yo'q");
        jsClick(driver, op);
        Object v = waitFor(() -> {
            Matcher m = Pattern.compile("Erkin mablag\\S*\\s*([0-9][0-9 ]*)").matcher(clean(p.getText()));
            return m.find() ? m.group(1).replace(" ", "") : null;
        }, 8);
        return v == null ? -1 : Long.parseLong(String.valueOf(v));
    }

    /** Buyurtma beradi. Qaytaradi: ekrandagi xabarlar. */
    List<String> buyurtma(String who, String mijoz, String tomon, String miqdor, String narx) {
        WebElement p = panel(who);
        mijozTanla(p, mijoz);
        WebElement m = p.findElement(By.cssSelector("input[placeholder='Miqdor']"));
        WebElement n = p.findElement(By.cssSelector("input[placeholder='Narx']"));
        m.sendKeys(Keys.CONTROL + "a"); m.sendKeys(Keys.DELETE); m.sendKeys(miqdor);
        n.sendKeys(Keys.CONTROL + "a"); n.sendKeys(Keys.DELETE); n.sendKeys(narx);
        String mv = m.getAttribute("value"), nv = n.getAttribute("value");
        if (Boolean.getBoolean("rasm")) { System.out.println("   OYNA: " + clean(p.getText()).replace("\n", " | ")); rasmOl("buyurtma_" + who + "_" + tomon); }
        // tugma mijoz balansi yuklanmaguncha o'chiq (xira) turadi — faol bo'lishini kutamiz
        By tb = By.xpath(".//button[normalize-space(.)='" + tomon + "']");
        WebElement tugma = waitFor(() -> {
            WebElement b = p.findElement(tb);
            return b.isEnabled() && b.getAttribute("disabled") == null ? b : null;
        }, 15);
        if (tugma == null) {
            String x = clean(p.getText()).replace("\n", " | ");
            System.out.println("   " + who + " " + tomon + ": tugma faol bo'lmadi (" + miqdor + " x " + narx + "): " + x);
            oxirgiMaydon = new String[]{mv, nv};
            return List.of("TUGMA FAOL EMAS: " + x);
        }
        jsClick(driver, tugma);
        sleep(1500);
        List<String> t = toasts(driver);
        System.out.println("   " + who + " " + tomon + " " + miqdor + " lot x " + narx + " (maydonda: " + mv + " x " + nv + ") -> " + t);
        oxirgiMaydon = new String[]{mv, nv};
        return t;
    }

    String[] oxirgiMaydon = {"", ""}; // oxirgi buyurtmada maydonlarda qolgan qiymatlar (miqdor, narx)

    /** Raqam maydoni minus belgisini olib tashladimi (himoya maydonning o'zida). */
    boolean minusOlibTashlandi(String yozilgan, String maydonda) {
        return yozilgan.startsWith("-") && maydonda != null && !maydonda.contains("-");
    }

    String sotuvchi() { return SOTUVCHI_MIJOZ; }
    String xaridor() { return XARIDOR_MIJOZ; }

    /** Ochiq buyurtmalar (qatorlar matni) — oyna o'ng tomondagi tugma bilan ochiladi. */
    List<String> ochiqBuyurtmalar(String who) {
        panel(who);
        WebElement p = ochiqOyna();
        List<String> r = new ArrayList<>();
        for (WebElement row : p.findElements(By.cssSelector("[role=row]")))
            try { if (!"1".equals(row.getAttribute("aria-rowindex"))) r.add(clean(row.getText()).replace("\n", " | ")); } catch (Exception ignore) {}
        ochiqYop(p);
        return r;
    }

    /** "Ochiq buyurtmalar" oynasini yopadi (buyurtma oynasini to'smasin). */
    void ochiqYop(WebElement p) {
        try { jsClick(driver, p.findElement(By.cssSelector("header button"))); sleep(400); } catch (Exception ignore) {}
    }

    WebElement ochiqOyna() {
        By ob = By.xpath("//*[normalize-space(text())='Ochiq buyurtmalar']/ancestor::*[.//*[normalize-space(text())='Ariza raqami']][1]");
        WebElement p = lastVisible(driver, ob);
        for (int i = 0; i < 2 && p == null; i++) {
            js().executeScript("let e=document.elementFromPoint(window.innerWidth-38,157); let b=(e.closest&&e.closest('button,svg'))||e; ['mousedown','mouseup','click'].forEach(t=>b.dispatchEvent(new MouseEvent(t,{bubbles:true})));");
            p = waitFor(() -> lastVisible(driver, ob), 5);
        }
        Assert.assertNotNull(p, "'Ochiq buyurtmalar' oynasi ochilmadi");
        sleep(800);
        return p;
    }

    /** Barcha ochiq buyurtmalarni o'chiradi (bekor qiladi). Nechta edi — qaytaradi. */
    int hammasiniBekorQil(String who) {
        List<String> oldin = ochiqBuyurtmalar(who);
        if (oldin.isEmpty()) return 0;
        WebElement p = ochiqOyna();
        WebElement h = p.findElement(By.cssSelector("[role=columnheader] input[type=checkbox]"));
        realClick(driver, h);
        sleep(500);
        if (!h.isSelected()) jsClick(driver, h);
        WebElement b = waitFor(() -> lastVisible(driver, By.xpath("//button[normalize-space(.)=\"O'chirish\"]")), 5);
        Assert.assertNotNull(b, who + ": 'O'chirish' tugmasi chiqmadi");
        jsClick(driver, b);
        WebElement ok = waitFor(() -> lastVisible(driver, By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button[normalize-space(.)=\"O'chirish\" or normalize-space(.)='Ha' or normalize-space(.)='Tasdiqlash']")), 3);
        if (ok != null) jsClick(driver, ok);
        ochiqYop(p);
        Object bosh = waitFor(() -> ochiqBuyurtmalar(who).isEmpty() ? Boolean.TRUE : null, 15);
        System.out.println("   " + who + ": " + oldin.size() + " ta ochiq buyurtma o'chirildi" + (bosh != null ? " ✓" : " — DIQQAT: hali bor " + ochiqBuyurtmalar(who)));
        if (bosh == null) negativXatolar.add(who + ": ochiq buyurtmalarni o'chirib bo'lmadi: " + ochiqBuyurtmalar(who));
        return oldin.size();
    }

    @Test(priority = 5, dependsOnMethods = "q4_sessiya", description = "Savdo vaqtidagi salbiy holatlar (bitim tuzilmasligi kerak)")
    public void q5_savdoNegativ() {
        hammasiniBekorQil("trader1");
        hammasiniBekorQil("trader2");
        String n = String.valueOf(NARX);
        // a) minus va 0 qiymatlar
        for (String[] c : new String[][]{{"-5", n, "minus miqdor"}, {"1", "-1000000", "minus narx"}, {"0", n, "0 miqdor"}, {"1", "0", "0 narx"}}) {
            System.out.println("5) " + c[2] + ":");
            buyurtma("trader1", sotuvchi(), "Sotish", c[0], c[1]);
            boolean himoya = minusOlibTashlandi(c[0], oxirgiMaydon[0]) || minusOlibTashlandi(c[1], oxirgiMaydon[1]);
            List<String> o = ochiqBuyurtmalar("trader1");
            if (himoya) System.out.println("   maydon minus belgisini qabul qilmadi ✓" + (o.isEmpty() ? "" : " (musbat qiymat bilan buyurtma qo'yildi, o'chiriladi)"));
            else if (!o.isEmpty()) negativXatolar.add("Savdo: " + c[2] + " bilan buyurtma QABUL QILINDI: " + o);
            else System.out.println("   rad etildi (ochiq buyurtma yo'q) ✓");
            if (!o.isEmpty()) hammasiniBekorQil("trader1");
        }
        // b) xaridor sotuvchi narxidan PAST narx beradi -> bitim yo'q, ikkala buyurtma ochiq qoladi
        System.out.println("5) xaridor sotuvchidan past narx beradi:");
        buyurtma("trader1", sotuvchi(), "Sotish", "1", n);
        buyurtma("trader2", xaridor(), "Sotib olish", "1", String.valueOf(NARX - 100_000));
        sleep(1500);
        int sot = ochiqBuyurtmalar("trader1").size(), xar = ochiqBuyurtmalar("trader2").size();
        System.out.println("   ochiq: sotuvchi " + sot + ", xaridor " + xar + (sot == 1 && xar == 1 ? " -> bitim tuzilmadi ✓" : ""));
        if (sot != 1 || xar != 1) negativXatolar.add("Savdo: past narxda buyurtmalar ochiq qolmadi (bitim tuzilgan bo'lishi mumkin): sotuvchi " + sot + ", xaridor " + xar);
        hammasiniBekorQil("trader1");
        hammasiniBekorQil("trader2");
        // c) o'ziga o'zi sotish: bitta mijoz ham sotadi, ham sotib oladi -> bitim bo'lmasligi kerak
        System.out.println("5) bitta mijoz o'zidan sotib oladi:");
        buyurtma("trader1", sotuvchi(), "Sotish", "1", n);
        List<String> t = buyurtma("trader1", sotuvchi(), "Sotib olish", "1", n);
        sleep(1500);
        int qoldi = ochiqBuyurtmalar("trader1").size();
        if (qoldi == 0) negativXatolar.add("Savdo: mijoz O'ZIDAN sotib oldi (ikkala buyurtma bajarildi). Xabarlar: " + t);
        else System.out.println("   bitim tuzilmadi (ochiq buyurtmalar: " + qoldi + ") ✓ " + t);
        hammasiniBekorQil("trader1");
    }

    /** HKKP -> Balans: STIR bo'yicha ERKIN mablag' (Mijozlar balansi, bo'lmasa Broker balansi). Topilmasa -1. */
    long erkin(String who, String stir) {
        kim(who);
        driver.get(Config.traderUrl() + "/uz/trader/hkkp/transaction");
        jadvalKut(driver);
        for (String tab : new String[]{"Mijozlar balansi", "Broker balansi"}) {
            WebElement b = visible(driver, By.xpath("//main//*[normalize-space(text())='" + tab + "']"));
            if (b != null) { jsClick(driver, b); jadvalKut(driver); }
            WebElement s = visible(driver, By.cssSelector("main input[placeholder='Qidirish']"));
            if (s != null) { s.sendKeys(Keys.CONTROL + "a"); s.sendKeys(stir); qidiruvKut(driver, stir); }
            WebElement r = rowWith(stir);
            if (r == null) {
                // broker balansi jadval emas, karta ko'rinishida bo'lishi mumkin: "Erkin ... 123 456"
                String m = clean(driver.findElement(By.tagName("main")).getText());
                Matcher em = Pattern.compile("Erkin[^0-9\\n]*\\n?\\s*([0-9][0-9 ]*)").matcher(m);
                if (tab.equals("Broker balansi") && em.find()) {
                    long e = Long.parseLong(em.group(1).replaceAll("\\s", ""));
                    System.out.println("   balans " + stir + " (" + tab + ", karta) -> erkin " + e);
                    return e;
                }
                if (tab.equals("Broker balansi")) System.out.println("   broker balansi matni: " + m.replace("\n", " | ").substring(0, Math.min(500, m.length())));
                continue;
            }
            List<Long> nums = new ArrayList<>();
            for (WebElement td : r.findElements(By.cssSelector("td"))) {
                String v = td.getText().replace("UZS", "").replaceAll("\\s", "").trim();
                if (v.matches("-?\\d+(\\.\\d+)?") && !v.equals(stir) && v.length() < 20) nums.add(Math.round(Double.parseDouble(v)));
            }
            if (nums.size() >= 4) {
                long e = nums.get(nums.size() - 3); // [barcha, erkin, band, qarzdorlik]
                System.out.println("   balans " + stir + " (" + tab + "): " + clean(r.getText()).replace("\n", " | ") + " -> erkin " + e);
                return e;
            }
        }
        System.out.println("   balans " + stir + ": topilmadi");
        return -1;
    }

    static final String SOTUVCHI_STIR = Config.getOr("qarama.sellerStir", "311927414");
    long sotuvchiBalans0, xaridorBalans0;

    @Test(priority = 6, dependsOnMethods = "q4_sessiya", description = "Ijobiy: 7 ta bitim, qisman bajarilish, yuqori narx, balanslar")
    public void q6_savdo() {
        hammasiniBekorQil("trader1");
        hammasiniBekorQil("trader2");
        String n = String.valueOf(NARX);
        sotuvchiBalans0 = erkin("trader1", SOTUVCHI_STIR);
        xaridorBalans0 = erkin("trader2", XARIDOR_MIJOZ);
        // a) 7 ta bitim: sotuvchi 7 lot qo'yadi, xaridor 1 lotdan 7 marta oladi
        System.out.println("6a) " + BITIMLAR + " ta bitim:");
        buyurtma("trader1", sotuvchi(), "Sotish", String.valueOf(BITIMLAR), n);
        for (int i = 0; i < BITIMLAR; i++) buyurtma("trader2", xaridor(), "Sotib olish", "1", n);
        // ochiq buyurtmalar ro'yxati biroz kechikib yangilanadi -> 15 s gacha kutamiz
        List<String> s1 = ochiqBuyurtmalar("trader1");
        for (long end = System.currentTimeMillis() + 15_000; !s1.isEmpty() && System.currentTimeMillis() < end; ) { sleep(2000); s1 = ochiqBuyurtmalar("trader1"); }
        List<String> x1 = ochiqBuyurtmalar("trader2");
        System.out.println("   ochiq qoldi: sotuvchi " + s1 + ", xaridor " + x1 + (s1.isEmpty() && x1.isEmpty() ? " -> hammasi bitimga aylandi ✓" : " (bitimlar soni 7-qadamda tekshiriladi)"));
        if (!s1.isEmpty()) hammasiniBekorQil("trader1");
        if (!x1.isEmpty()) hammasiniBekorQil("trader2");
        // balans: har bitimda zakalat (10%) + vositachilik yig'imi (800)
        long sot1 = erkin("trader1", SOTUVCHI_STIR), xar1 = erkin("trader2", XARIDOR_MIJOZ);
        long kutilgan = BITIMLAR * (ZAKALAT + KOMISSIYA);
        System.out.println("   erkin mablag' farqi: sotuvchi " + (sotuvchiBalans0 - sot1) + ", xaridor " + (xaridorBalans0 - xar1)
                + "; kutilgan " + kutilgan + " = " + BITIMLAR + " x (" + ZAKALAT + " zakalat + " + KOMISSIYA + " yig'im)");
        if (sotuvchiBalans0 < 0 || sot1 < 0 || xaridorBalans0 < 0 || xar1 < 0) negativXatolar.add("Balans o'qilmadi (HKKP)");
        else {
            if (sotuvchiBalans0 - sot1 != kutilgan) negativXatolar.add("Balans: sotuvchidan " + (sotuvchiBalans0 - sot1) + " yechildi, kutilgan " + kutilgan);
            if (xaridorBalans0 - xar1 != kutilgan) negativXatolar.add("Balans: xaridordan " + (xaridorBalans0 - xar1) + " yechildi, kutilgan " + kutilgan);
        }
        // b) qisman: sotuvchida 2 lot, xaridor 3 lot so'raydi -> 2 lotli bitim, xaridorda 1 lot ochiq qoladi
        System.out.println("6b) qisman bajarilish (2 lot bor, 3 lot so'raldi):");
        buyurtma("trader1", sotuvchi(), "Sotish", "2", n);
        buyurtma("trader2", xaridor(), "Sotib olish", "3", n);
        sleep(1500);
        List<String> s2 = ochiqBuyurtmalar("trader1"), x2 = ochiqBuyurtmalar("trader2");
        System.out.println("   ochiq: sotuvchi " + s2 + ", xaridor " + x2 + (s2.isEmpty() && x2.size() == 1 ? " ✓" : ""));
        if (!s2.isEmpty() || x2.size() != 1) negativXatolar.add("Qisman bajarilish: sotuvchi 2 lotni to'liq sotishi, xaridorda 1 lot ochiq qolishi kerak edi (sotuvchi " + s2 + ", xaridor " + x2 + ")");
        qismanKutildi = true;
        hammasiniBekorQil("trader2");
        // c) xaridor sotuvchidan YUQORI narx beradi -> qoida: narx bir xil bo'lishi kerak, bitim yo'q
        System.out.println("6c) xaridor yuqori narx beradi (sotuvchi " + NARX + ", xaridor " + (NARX + 100_000) + "):");
        buyurtma("trader1", sotuvchi(), "Sotish", "1", n);
        buyurtma("trader2", xaridor(), "Sotib olish", "1", String.valueOf(NARX + 100_000));
        sleep(1500);
        int s3 = ochiqBuyurtmalar("trader1").size(), x3 = ochiqBuyurtmalar("trader2").size();
        System.out.println("   ochiq: sotuvchi " + s3 + ", xaridor " + x3 + (s3 == 1 && x3 == 1 ? " -> narx bir xil emas, bitim tuzilmadi ✓" : ""));
        if (s3 != 1 || x3 != 1) yuqoriNarxBitim = true; // 7-qadamda bitim narxi tekshiriladi
        hammasiniBekorQil("trader1");
        hammasiniBekorQil("trader2");
    }

    boolean qismanKutildi, yuqoriNarxBitim;

    // ================================================================== 7. bitimlar
    /** trader1 -> Mening shartnomalarim -> Sotish: shu sessiyadagi "A..." bitimlar (narx, lot). */
    void yigBitimlar() {
        kim("trader1");
        openContracts(Config.traderUrl(), "Sotish");
        String bugun = LocalDate.now().format(SANA);
        Pattern np = Pattern.compile("([0-9][0-9 ]*) [|] UZS [|] ([0-9]+) [|]");
        for (WebElement r : driver.findElements(By.cssSelector("main tbody tr"))) {
            String s;
            try { s = clean(r.getText()).replace("\n", " | "); } catch (StaleElementReferenceException e) { continue; }
            Matcher a = Pattern.compile("^(\\S+) \\| (\\S+) \\| (A\\d{8})").matcher(s);
            if (!a.find() || !a.group(1).equals(bugun) || !s.contains(PRODUCT)) continue;
            LocalDateTime t = LocalDateTime.parse(a.group(1) + " " + a.group(2), DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            if (sessiyaBosh != null && t.isBefore(sessiyaBosh.minusMinutes(1))) continue;
            Matcher m = np.matcher(s);
            long narx = 0, lot = 0;
            if (m.find()) { narx = Long.parseLong(m.group(1).replace(" ", "")); lot = Long.parseLong(m.group(2)); }
            bitimlar.put(a.group(3), new long[]{narx, lot});
        }
    }

    @Test(priority = 7, dependsOnMethods = "q6_savdo", description = "Shu sessiyadagi bitimlar yig'iladi (1 lotli bitimlar stsenariylarga beriladi)")
    public void q7_bitimlar() {
        yigBitimlar();
        dealNos.clear();
        dealNos.addAll(bitimlar.keySet());
        Assert.assertFalse(dealNos.isEmpty(), "Shu sessiyada bitim yo'q");
        dealNo = dealNos.get(0);
        AuctionFlowTest.OXIRGI_BITIMLAR.clear();
        for (Map.Entry<String, long[]> e : bitimlar.entrySet())
            if (e.getValue()[1] == 1 && e.getValue()[0] == NARX) AuctionFlowTest.OXIRGI_BITIMLAR.add(e.getKey());
        StringBuilder sb = new StringBuilder();
        bitimlar.forEach((k, v) -> sb.append(k).append("=").append(v[1]).append("x").append(v[0]).append(" "));
        System.out.println("7) Bitimlar (" + dealNos.size() + "): " + sb + "| stsenariylar uchun: " + AuctionFlowTest.OXIRGI_BITIMLAR);
        long birLot = bitimlar.values().stream().filter(v -> v[1] == 1 && v[0] == NARX).count();
        long ikkiLot = bitimlar.values().stream().filter(v -> v[1] == 2).count();
        Assert.assertTrue(birLot >= BITIMLAR, "1 lotli bitimlar " + birLot + " ta, kutilgan " + BITIMLAR);
        if (qismanKutildi && ikkiLot < 1) negativXatolar.add("Qisman bajarilish: 2 lotli bitim topilmadi");
        for (Map.Entry<String, long[]> e : bitimlar.entrySet())
            if (e.getValue()[0] != NARX) negativXatolar.add("Bitim " + e.getKey() + " " + e.getValue()[0] + " so'mda tuzildi — narx sotuvchi narxi (" + NARX + ") bilan bir xil bo'lishi kerak");
        if (yuqoriNarxBitim) System.out.println("7) DIQQAT: yuqori narxdagi xarid buyurtmasi ochiq qolmadi (bitim tuzilgan bo'lishi mumkin)");
        System.out.println("7) 1 lotli: " + birLot + " ✓, 2 lotli (qisman): " + ikkiLot + (ikkiLot >= 1 ? " ✓" : ""));
    }

    // ================================================================== 8-9. imzolash va tasdiqlash
    @Test(priority = 8, dependsOnMethods = "q7_bitimlar", description = "Ikkala treyder bitimlarni ommaviy imzolaydi")
    public void q8_imzolash() {
        signDeal(Config.traderUrl(), "trader1", "Sotish");
        signDeal(Config.traderUrl(), "trader2", "Sotib olish");
    }

    @Test(priority = 9, dependsOnMethods = "q8_imzolash", description = "Admin bitimlarni ommaviy tasdiqlaydi")
    public void q9_adminTasdiqlash() {
        kim("admin2");
        adminOmmaviyTasdiqla(Config.adminUrl(), dealNos);
    }

    // ================================================================== 10. yakun
    @Test(priority = 10, description = "Salbiy holatlar yakuni: sayt qabul qilmasligi kerak bo'lganlar")
    public void q10_negativNatijalar() {
        System.out.println("SALBIY holatlar: " + (negativXatolar.isEmpty() ? "hammasi to'g'ri ✓" : negativXatolar));
        Assert.assertTrue(negativXatolar.isEmpty(), "Kutilmagan natijalar: " + negativXatolar);
    }
}
