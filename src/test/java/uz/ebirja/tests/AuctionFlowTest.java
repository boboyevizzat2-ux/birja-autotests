package uz.ebirja.tests;

import org.openqa.selenium.*;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import uz.ebirja.base.BaseTest;
import uz.ebirja.base.Eimzo;
import uz.ebirja.base.Ui;
import uz.ebirja.config.Config;
import uz.ebirja.pages.KontraktForma;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static uz.ebirja.base.Ui.*;

/**
 * To'liq auksion jarayoni (boshidan oxirigacha):
 * 1) Treyder 1 kontrakt yaratadi  2) Moderator 3 rolda tasdiqlaydi  3) Admin savdo vaqtini qo'yadi
 * 4) Treyder 1 mahsulotni savdoga chiqaradi  5) Treyder 2 taklif beradi  6) Bitim hosil bo'ladi
 * 7) Ikkala treyder imzolaydi  8) Admin tasdiqlaydi.
 *
 * Qoidalar: moderator tasdig'i savdo vaqtidan OLDIN; yangi sessiya oldingisi to'liq tugagach;
 * bugun savdo kuni bo'lishi kerak (kalendarda ish kuni).
 * Bu umumiy (abstrakt) klass: har bir savdo turi alohida klassda ishga tushadi —
 * AuksionOddiyTest, AuksionSpirtTest, AuksionOltinTest, AuksionKumushTest, AuksionSubsidiyaTest.
 * Har bir turning o'z mahsuloti bor: login.properties dagi auction.&lt;tur&gt;.product (masalan auction.spirt.product).
 * Sozlamalar (login.properties): auction.product, auction.lots, auction.bidLots, auction.minutes
 */
public abstract class AuctionFlowTest extends SavdoAsos {

    /** Savdo turi: har bir tur o'z klassida (AuksionSpirtTest va h.k.) beriladi. */
    protected abstract String tur();

    final String TUR = tur();
    final String PRODUCT = mahsulot(TUR);

    /** Savdo turiga mos mahsulot: avval login.properties, bo'lmasa ma'lum test mahsuloti. */
    static String mahsulot(String tur) {
        String def = switch (tur) {
            case "oddiy" -> Config.getOr("auction.product", "Асбестовый шнур");
            case "spirt" -> "Бентонит";
            case "oltin" -> "Барда";
            default -> null;
        };
        String p = Config.getOr("auction." + tur + ".product", def);
        if (p == null) throw new RuntimeException("'" + tur + "' savdo turi uchun mahsulot yo'q: login.properties ga auction." + tur + ".product=... yozing");
        System.out.println("Savdo turi: " + tur + ", mahsulot: " + p);
        return p;
    }
    static final String LOTS = Config.getOr("auction.lots", "10");
    static final String BID_LOTS = Config.getOr("auction.bidLots", "1");
    // nechta alohida taklif berilsin (har biri alohida bitim bo'ladi)
    static final int BIDS = Integer.parseInt(Config.getOr("auction.bids", "1"));
    // shu takliflardan nechtasi savdo ichida o'chiriladi (🗑) va shundan keyin nechta bitim kutiladi
    static final int OCHIRISH = BIDS > 1 ? Integer.parseInt(Config.getOr("auction.deleteBids", "1")) : 0;
    static final int KUTILGAN = BIDS - OCHIRISH;
    // takliflar orasidagi pauza (ms)
    static final int PAUZA_MS = Integer.parseInt(Config.getOr("auction.bidPauseMs", "2000"));
    // xaridor qaysi mijoz nomidan taklif beradi (STIR yoki nom qismi); bo'sh bo'lsa ro'yxatdagi birinchisi
    static final int MIN = Integer.parseInt(Config.getOr("auction.minutes", "1"));          // e'lon davomiyligi
    static final int SAVDO_MIN = Integer.parseInt(Config.getOr("auction.savdoMinutes", "2")); // savdo davomiyligi

    // qadamlar orasida uzatiladigan ma'lumotlar
    String contractId;          // moderator ro'yxatidagi ID
    LocalDateTime elonStart, savdoStart, savdoEnd;
    Map<String, Integer> bitimLotlari = new LinkedHashMap<>();
    /** Shu run'da auksionda hosil bo'lgan bitimlar (keyingi "Bitim holatlari" testi shulardan foydalanadi). */
    public static final List<String> OXIRGI_BITIMLAR = new ArrayList<>();
    /** Shu run'da savdo testi (auksion yoki qarama-qarshi) ishga tushgan bo'lsa true: bitim stsenariylari eski bitimlarga o'tmaydi. */
    public static volatile boolean SAVDO_ISHLADI = false;


    // ------------------------------------------------------------------ 1
    @Test(priority = 1, description = "Treyder 1 auksion kontraktini yaratadi va E-IMZO bilan yuboradi")
    public void step1_kontraktYaratish() {
        SAVDO_ISHLADI = true;
        String base = Config.traderUrl();
        kim("trader1");
        KontraktForma forma = new KontraktForma(driver, PRODUCT).ochish(base).togriToldirish(true);
        forma.yuborishniBosish();
        Eimzo.typePassword();
        Object ok = waitFor(() -> driver.getCurrentUrl().contains("/create") ? null : Boolean.TRUE, 60);
        Assert.assertNotNull(ok, "Kontrakt yuborilmadi. Xabarlar: " + toasts(driver));
        System.out.println("1) Kontrakt yuborildi: " + PRODUCT);
    }

    // ------------------------------------------------------------------ 2
    @Test(priority = 2, dependsOnMethods = "step1_kontraktYaratish",
          description = "Moderator kontraktni LISTING_MAIN -> LISTING -> LISTING_MEMBER rollarida tasdiqlaydi")
    public void step2_moderatorTasdiqlashi() {
        String base = Config.adminUrl();
        kim("admin1");
        try {
            for (String role : new String[]{"MODERATOR_LISTING_MAIN", "MODERATOR_LISTING", "MODERATOR_LISTING_MEMBER"}) {
                switchRole(role);
                driver.get(base + "/uz/moderationv2/listing/contracts");
                waitFor(() -> visible(driver, By.cssSelector("main tbody tr")), 30);
                sleep(500);
                if (contractId == null) contractId = findNewContractId();
                WebElement row = findContractRow(contractId);
                Assert.assertNotNull(row, role + ": kontrakt ID " + contractId + " ro'yxatda topilmadi");
                List<WebElement> tds = row.findElements(By.cssSelector("td"));
                WebElement dots = tds.get(tds.size() - 1).findElement(By.cssSelector("button"));
                js().executeScript("arguments[0].scrollIntoView({block:'center'})", dots);
                dots.click();
                WebElement item = waitFor(() -> visible(driver, By.xpath("//*[normalize-space(text())='Tasdiqlash']")), 10);
                Assert.assertNotNull(item, role + ": '⋮' menyusida 'Tasdiqlash' yo'q");
                item.click(); // haqiqiy bosish (JS bosishga sayt javob bermaydi)
                WebElement confirm = waitFor(() -> lastVisible(driver, By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button[normalize-space(.)='Tasdiqlash']")), 10);
                Assert.assertNotNull(confirm, role + ": tasdiqlash oynasi ochilmadi");
                jsClick(driver, confirm);
                Eimzo.typePassword(8);
                Assert.assertTrue(waitToast(driver, "Muvaffaqiyatli", 10), role + ": tasdiqlanmadi. Xabarlar: " + toasts(driver));
                System.out.println("2) " + role + " tasdiqladi (ID " + contractId + ")");
            }
        } finally {
            switchRole("MODERATOR_LISTING_MAIN");
        }
    }

    // ------------------------------------------------------------------ 3
    @Test(priority = 3, dependsOnMethods = "step2_moderatorTasdiqlashi",
          description = "Admin savdo vaqtlarini qo'yadi (oldingi sessiya tugagach)")
    public void step3_savdoVaqtiniQoyish() {
        String base = Config.adminUrl();
        kim("admin2");
        boolean kechki = LocalTime.now().isAfter(LocalTime.of(14, 0));
        String session = kechki ? "Kechki sessiya" : "Ertalabki sessiya";
        driver.get(base + "/uz/moderationv2/sales-schedule/auction-sales-2/trade-session");
        jadvalKut(driver);
        // Kutib o'tirmaymiz: jadvaldagi barcha savdo qatorlari o'chiriladi va o'zimiznikilar yaratiladi
        int ochirildi = 0, n = 0;
        for (int t = 0; t < 20 && n < driver.findElements(By.cssSelector("main tbody tr")).size(); t++) {
            if (qatorniOchir(n)) ochirildi++; else n++; // o'chmagan qator o'tkazib yuboriladi
        }
        System.out.println("3) Eski savdo qatorlari o'chirildi: " + ochirildi);
        LocalDateTime base0 = LocalDateTime.now().withSecond(0).withNano(0).plusMinutes(1);
        elonStart = base0;
        savdoStart = base0.plusMinutes(MIN);
        savdoEnd = savdoStart.plusMinutes(SAVDO_MIN);
        String elonSeksiya = kechki ? "Sotish uchun e'lon berish (Kechki sessiya)" : "Sotish uchun e'lon berish (ertalabki sessiya)";
        savdoYarat(session, elonSeksiya, null, elonStart, savdoStart);
        savdoYarat(session, session, "Boshqalar", savdoStart, savdoEnd);
        System.out.println("3) Savdo vaqtlari: e'lon " + elonStart.format(HM) + "-" + savdoStart.format(HM)
                + ", Boshqalar " + savdoStart.format(HM) + "-" + savdoEnd.format(HM));
    }

    /** Jadvaldagi n-qatorni ⋮ -> "O'chirish" orqali o'chiradi. O'chsa true. */
    boolean qatorniOchir(int n) {
        List<WebElement> rows = driver.findElements(By.cssSelector("main tbody tr"));
        if (rows.size() <= n) return false;
        int oldin = rows.size();
        List<WebElement> tds = rows.get(n).findElements(By.cssSelector("td"));
        if (tds.isEmpty()) return false;
        realClick(driver, tds.get(tds.size() - 1).findElement(By.cssSelector("button")));
        // apostrof turlicha bo'lishi mumkin (O'chirish / O‘chirish / Oʻchirish)
        WebElement del = waitFor(() -> lastVisible(driver, By.xpath("//*[not(*) and (contains(normalize-space(text()),'chirish') or normalize-space(text())='Удалить')]")), 4);
        if (del == null) { System.out.println("3) " + (n + 1) + "-qator: ⋮ menyusida 'O'chirish' yo'q"); driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE); return false; }
        try { del.click(); } catch (Exception e) { jsClick(driver, del); }
        WebElement ok = waitFor(() -> lastVisible(driver, By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button[contains(normalize-space(.),'chirish') or normalize-space(.)='Ha' or normalize-space(.)='Tasdiqlash']")), 4);
        if (ok != null) jsClick(driver, ok);
        imzoYoki(() -> driver.findElements(By.cssSelector("main tbody tr")).size() < oldin, 8);
        return driver.findElements(By.cssSelector("main tbody tr")).size() < oldin;
    }

    /** "Savdo uchun e'lon berish": Nazorat turi = Mahsulot guruhi, sessiya, seksiya, (mahsulot guruhi), vaqt. */
    void savdoYarat(String sessiya, String seksiya, String mahsulotGuruhi, LocalDateTime start, LocalDateTime end) {
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//main//button[contains(normalize-space(.),\"Savdo uchun e'lon berish\")]")), 15));
        WebElement d = waitFor(() -> lastVisible(driver, By.cssSelector("[role=dialog], [class*=modal]")), 10);
        Assert.assertNotNull(d, "'Savdo uchun e'lon berish' oynasi ochilmadi");
        dialogTanlov("Nazorat turi", "Mahsulot guruhi");
        dialogTanlov("Sessiya turi", sessiya);
        dialogTanlov("Seksiya", seksiya);
        if (mahsulotGuruhi != null) dialogTanlov("Mahsulot guruhi", mahsulotGuruhi);
        setTime("startTime", start.format(HM));
        setTime("endTime", end.format(HM));
        jsClick(driver, lastVisible(driver, By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button[normalize-space(.)='Saqlash']")));
        imzoYoki(() -> lastVisible(driver, By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button[normalize-space(.)='Saqlash']")) == null, 12);
        System.out.println("3) Yaratildi: " + seksiya + (mahsulotGuruhi != null ? " / " + mahsulotGuruhi : "") + " " + start.format(HM) + "-" + end.format(HM) + " " + toasts(driver));
    }

    /** Ochiq oynada yorlig'i (label) berilgan ro'yxatni bosib, variantni matni bo'yicha tanlaydi. */
    void dialogTanlov(String label, String variant) {
        String lbl = "//*[self::label or self::span or self::p][contains(normalize-space(.),'" + label + "')][not(ancestor::*[contains(@class,'option')])]";
        WebElement ctl = waitFor(() -> lastVisible(driver, By.xpath(lbl + "/following::*[contains(@class,'control') or contains(@class,'select') or self::input][1]")), 6);
        Assert.assertNotNull(ctl, "Oynada '" + label + "' ro'yxati yo'q");
        realClick(driver, ctl);
        String opt = "//*[contains(@class,'option') or contains(@id,'-option-') or @role='option'][normalize-space(.)=\"" + variant + "\"]";
        WebElement op = waitFor(() -> lastVisible(driver, By.xpath(opt)), 4);
        if (op == null) { // zaxira: menyu ichidagi aniq matnli element
            WebElement in = ctl.getTagName().equals("input") ? ctl : ctl.findElements(By.cssSelector("input")).stream().findFirst().orElse(null);
            if (in != null) { in.sendKeys(Keys.ARROW_DOWN); op = waitFor(() -> lastVisible(driver, By.xpath(opt)), 3); }
        }
        Assert.assertNotNull(op, "'" + label + "' ro'yxatida '" + variant + "' yo'q");
        jsClick(driver, op);
        sleep(300);
    }

    // ------------------------------------------------------------------ 4
    @Test(priority = 4, dependsOnMethods = "step3_savdoVaqtiniQoyish",
          description = "Treyder 1 e'lon vaqtida mahsulotni savdoga chiqaradi")
    public void step4_savdogaChiqarish() {
        String base = Config.traderUrl();
        kim("trader1");
        waitUntil(elonStart.plusSeconds(5), "e'lon berish vaqti");
        // --- SALBIY: minus va 0 lot (imzo to'sib qo'yilgan, hech narsa yuborilmaydi)
        WebElement lots = lotOynasi(base);
        lotYoz(lots, "-5");
        String v = lots.getAttribute("value");
        System.out.println("4-negativ) '-5' lot yozildi, maydonda: '" + v + "'");
        if (v != null && v.contains("-")) negativXatolar.add("Savdoga chiqarish: lot maydoni minus qiymatni qabul qildi: " + v);
        eimzoTosish(driver);
        lotYoz(lots, "0");
        jsClick(driver, lastVisible(driver, By.xpath("//button[normalize-space(.)='Saqlash']")));
        waitFor(() -> !toasts(driver).isEmpty() || eimzoUrinishlari(driver) > 0 ? Boolean.TRUE : null, 4);
        System.out.println("4-negativ) 0 lot -> xabarlar: " + toasts(driver) + ", imzo so'rovlari: " + eimzoUrinishlari(driver));
        if (eimzoUrinishlari(driver) > 0) negativXatolar.add("Savdoga chiqarish: 0 lot bilan forma o'tdi va imzo so'radi (imzo to'sildi)");
        // --- IJOBIY
        lots = lotOynasi(base);
        lotYoz(lots, LOTS);
        jsClick(driver, lastVisible(driver, By.xpath("//button[normalize-space(.)='Saqlash']")));
        imzoYoki(() -> visible(driver, By.name("graphicLot")) == null || !toasts(driver).isEmpty(), 9);
        sleep(500);
        List<String> t = toasts(driver);
        Assert.assertFalse(t.stream().anyMatch(x -> x.contains("avval taklif berilgan") || x.contains("Majburiy")),
                "Savdoga chiqarilmadi: " + t);
        System.out.println("4) " + LOTS + " lot savdoga qo'yildi. Xabarlar: " + t);
        // salbiy holat uchun: sotuvchi o'z mahsulotiga taklif oynasini OLDINDAN ochib qo'yadi (savdoda faqat bosiladi)
        try {
            joriyMijoz = "";
            savdoSahifasi(base);
            // sotuvchi o'z mahsulotini "Sotib olish" da ko'rmasligi kerak (4 s ichida tekshiriladi)
            boolean korinadi = waitFor(() -> visible(driver, By.xpath("//main//*[contains(text(),'" + PRODUCT + "')][not(ancestor::*[contains(@class,'highlight')])][ancestor::*[contains(.,' lot')]]")) != null ? Boolean.TRUE : null, 4) != null;
            if (!korinadi) {
                ozKorinmaydi = true;
                System.out.println("4-negativ) sotuvchi o'z mahsulotini 'Sotib olish' da ko'rmaydi -> o'z mahsulotiga taklif bera olmaydi ✓");
            } else {
                ozPanel = waitFor(() -> { try { return oynaTayyorla(BID_LOTS); } catch (Throwable e) { return null; } }, 6);
                if (ozPanel == null) { ozKorinmaydi = true; System.out.println("4-negativ) sotuvchi o'z mahsulotiga taklif oynasini ocholmaydi -> taklif bera olmaydi ✓"); }
                else System.out.println("4) sotuvchining o'z mahsulotiga taklif oynasi tayyor (sayt ruxsat beryapti - savdoda tekshiriladi)");
            }
        } finally { joriyMijoz = XARIDOR_MIJOZ; }
    }

    /** Treyder 1: Birja mahsulotlari -> mahsulot qatoridagi 🔨 -> "Savdoga qo'yish" oynasi (sahifa qayta ochiladi, imzo to'sig'i tushadi). */
    WebElement lotOynasi(String base) {
        driver.get(base + "/uz/trader/sales/auction-sales?tradeTab=SELL");
        WebElement search = waitFor(() -> visible(driver, By.cssSelector("main input[placeholder='Qidirish']")), 30);
        search.sendKeys(PRODUCT);
        WebElement row = waitFor(() -> {
            for (WebElement r : driver.findElements(By.cssSelector("main tbody tr"))) if (r.isDisplayed() && r.getText().contains(PRODUCT)) return r;
            return null;
        }, 20);
        Assert.assertNotNull(row, "Birja mahsulotlarida '" + PRODUCT + "' topilmadi");
        WebElement gavel = row.findElements(By.cssSelector("td")).get(2).findElements(By.cssSelector("button, svg")).get(0);
        try { jsClick(driver, gavel); } catch (Exception e) { js().executeScript("arguments[0].dispatchEvent(new MouseEvent('click',{bubbles:true}))", gavel); }
        WebElement lots = waitFor(() -> visible(driver, By.name("graphicLot")), 15);
        Assert.assertNotNull(lots, "'Savdoga qo'yish' oynasi ochilmadi");
        return lots;
    }

    void lotYoz(WebElement lots, String v) {
        lots.click();
        lots.sendKeys(Keys.CONTROL + "a");
        lots.sendKeys(Keys.DELETE);
        lots.sendKeys(v);
        sleep(400);
    }

    // ------------------------------------------------------------------ 4b
    @Test(priority = 9, dependsOnMethods = "step5_taklifBerish",
          description = "SALBIY holatlar natijasi: minus/0 lot, mavjuddan ko'p (101) lotga taklif")
    public void step9_negativNatijalar() {
        System.out.println("SALBIY holatlar: " + (negativXatolar.isEmpty() ? "hammasi rad etildi ✓" : negativXatolar));
        Assert.assertTrue(negativXatolar.isEmpty(), "Sayt quyidagilarni QABUL QILDI: " + negativXatolar);
    }

    // ------------------------------------------------------------------ 5
    @Test(priority = 5, dependsOnMethods = "step4_savdogaChiqarish",
          description = "Treyder 2 savdo vaqtida mahsulotga taklif beradi")
    public void step5_taklifBerish() {
        String base = Config.traderUrl();
        kim("trader2");
        // --- IJOBIY: bitta sahifada BIDS ta taklif OYNASI oldindan ochiladi (🔨 har bosilganda yangi oyna),
        // har birida mijoz va miqdor kiritiladi; savdo boshlanishi bilan oynalar ketma-ket yuboriladi, PAUZA oralig'i bilan.
        savdoSahifasi(base);
        List<WebElement> oynalar = new ArrayList<>();
        long tayyorlashBoshi = System.currentTimeMillis();
        for (int i = 0; i < BIDS; i++) {
            WebElement p = waitFor(() -> { try { return oynaTayyorla(BID_LOTS); } catch (Throwable e) { System.out.println("5) oyna hali ochilmadi: " + e.getMessage()); sleep(1000); return null; } }, 60);
            Assert.assertNotNull(p, (i + 1) + "-taklif oynasi ochilmadi");
            oynalar.add(p);
        }
        System.out.println("5) " + BIDS + " ta oyna " + (System.currentTimeMillis() - tayyorlashBoshi) / 1000.0 + " s da tayyorlandi");
        // 101 lotlik (qisman yutadigan) taklif oynasi ham oldindan
        String kop = String.valueOf(Integer.parseInt(LOTS) + 1);
        WebElement kopPanel = waitFor(() -> { try { return oynaTayyorla(kop); } catch (Throwable e) { sleep(1000); return null; } }, 20);
        // 101 lotdan KEYIN beriladigan 1 lotli taklif: lot qolmagani uchun yutqazishi kerak (savatchada qizil)
        WebElement yutqazPanel = waitFor(() -> { try { return oynaTayyorla(BID_LOTS); } catch (Throwable e) { sleep(1000); return null; } }, 20);
        waitUntil(savdoStart, "savdo vaqti");
        // kompyuter va sayt soati farq qiladi: saytning o'zi 'AMALDAGI ... HH:mm:00' deb ko'rsatishini kutamiz
        java.util.regex.Pattern amaldagi = java.util.regex.Pattern.compile("(?s)AMALDAGI(?:(?!KEYINGI).)*" + savdoStart.format(HM) + ":00", java.util.regex.Pattern.CASE_INSENSITIVE);
        Object ochildi = waitFor(() -> amaldagi.matcher(driver.findElement(By.tagName("body")).getText()).find() ? Boolean.TRUE : null, 20);
        System.out.println("5) saytda savdo " + (ochildi != null ? "ochildi" : "ochilgani KO'RINMADI (baribir davom etiladi)") + ", kompyuter soati " + LocalTime.now());
        sleep(3500); // server soati 2-3 s orqada: darhol berilgan taklif 'Seksiya vaqti emas' bo'ladi
        long boshlandi = System.currentTimeMillis();
        WebElement panel = null;
        long oxirgiYuborish = 0;
        for (int i = 0; i < BIDS; i++) {
            panel = oynalar.get(i);
            try { panel.isDisplayed(); } catch (Exception stale) { System.out.println("5) " + (i + 1) + "-oyna eskirgan, qayta ochiladi"); panel = oynaTayyorla(BID_LOTS); }
            // takliflar orasi KAMIDA PAUZA_MS: imzo shundan uzoq davom etsa, qo'shimcha kutilmaydi
            long kut = oxirgiYuborish + PAUZA_MS - System.currentTimeMillis();
            if (kut > 0) sleep(kut);
            long t0 = System.currentTimeMillis();
            oxirgiYuborish = t0;
            yuborish(panel, i + 1, BID_LOTS);
            System.out.println("5) " + (i + 1) + "-taklif " + (System.currentTimeMillis() - t0) / 1000.0 + " s");
        }
        System.out.println("5) " + BIDS + " ta taklif " + (System.currentTimeMillis() - boshlandi) / 1000.0 + " s da berildi");
        // sayt qabul qilmagan (masalan 'Seksiya vaqti emas') takliflar qayta beriladi
        for (int u = 0; u < 3; u++) {
            int bor = taklifSoni();
            if (bor < 0 || bor >= BIDS) break;
            System.out.println("5) DIQQAT: saytda " + bor + " ta taklif, " + BIDS + " ta bo'lishi kerak - yetishmagani qayta beriladi");
            for (int i = bor; i < BIDS; i++) { WebElement p = yangiOyna(); if (p != null) yuborish(p, i + 1, BID_LOTS); }
            sleep(1500);
        }
        // --- bitta taklif o'chiriladi (Mening takliflarim -> 🗑)
        if (OCHIRISH > 0 && vaqtYetadimi("taklifni o'chirish")) taklifOchir();
        // --- 101 lot (qisman yutadi), keyin yana 1 lot (yutqazadi), so'ng savatcha ranglari savdo ICHIDA o'qiladi
        qismanTaklif(kopPanel, kop);
        if (vaqtYetadimi("yutqazadigan taklif")) yutqazadiganTaklif(yutqazPanel);
        if (vaqtYetadimi("savatcha ranglari")) savatchaTekshir();
        savdoSalbiyHolatlar();
        ozMahsulotigaTaklif(base);
        System.out.println("5) hamma ish tugaganda savdoga qolgan vaqt: " + java.time.Duration.between(LocalDateTime.now(), savdoEnd).toSeconds() + " s");
    }

    // ------------------------------------------------------------------ 5n: savdo ichidagi salbiy holatlar
    static final String NARX = Config.getOr("auction.price", "1000000");
    /** true bo'lsa oynaTayyorla mijozni tanlamaydi (mijozsiz taklif holati uchun). */
    boolean mijozsiz = false;
    /** Savdo tugagandan keyin bosiladigan, oldindan tayyorlangan oyna. */
    WebElement kechPanel;

    /**
     * Har bir holatda: maydonga yozilgan qiymat o'qiladi. Maydon noto'g'ri qiymatni o'zi o'zgartirsa - himoya, yuborilmaydi.
     * Qolsa - "Sotib olish" bosiladi; hukm "Mening takliflarim" dagi qatorlar soniga qarab: ko'paysa sayt QABUL QILGAN (xato, darhol o'chiriladi).
     */
    void savdoSalbiyHolatlar() {
        // {nomi, miqdor, narx}
        String[][] holatlar = {
                {"minus lot", "-5", NARX}, {"minus pul", "1", "-" + NARX},
                {"0 lot", "0", NARX}, {"0 pul", "1", "0"},
                {"bo'sh lot", "", NARX}, {"bo'sh pul", "1", ""},
                {"kasr lot", "1.5", NARX}, {"harf lot", "abc", NARX}, {"harf pul", "1", "abc"},
                {"juda ko'p lot (mablag'dan katta)", "999999999", NARX},
        };
        for (String[] h : holatlar) {
            if (!vaqtYetadimi(h[0])) continue;
            salbiyHolat(h[0], h[1], h[2], null);
        }
        if (vaqtYetadimi("past narx")) pastNarx();
        if (vaqtYetadimi("mijozsiz taklif")) { mijozsiz = true; try { salbiyHolat("mijoz tanlanmagan", "1", NARX, yangiOyna()); } finally { mijozsiz = false; } }
        // treyder o'z nomidan (mijozsiz emas, ro'yxatdagi birinchi: o'z kompaniyasi): natija qayd qilinadi, qabul qilinsa o'chiriladi
        if (vaqtYetadimi("o'z nomidan taklif") && !XARIDOR_MIJOZ.isEmpty()) {
            joriyMijoz = "";
            try { salbiyHolat("treyder O'Z NOMIDAN taklif (ma'lumot uchun)", BID_LOTS, NARX, yangiOyna()); } finally { joriyMijoz = XARIDOR_MIJOZ; }
        }
        if (vaqtYetadimi("savdodan keyingi taklif oynasi")) kechPanel = yangiOyna();
    }

    /** Boshlang'ichdan PAST narx: taklif berish mumkin, lekin savatchada ko'rinmasligi va kontrakt tuzilmasligi kerak (o'chirilmaydi). */
    void pastNarx() {
        String past = String.valueOf(Long.parseLong(NARX) - 100_000);
        try {
            WebElement p = yangiOyna();
            if (p == null) { negativXatolar.add("Past narx: oyna ochilmadi, tekshirilmadi"); return; }
            maydonYoz(p.findElement(By.cssSelector("input[placeholder='Miqdor']")), BID_LOTS);
            List<WebElement> n = p.findElements(By.cssSelector("input[placeholder='Narx'], input[name='price']"));
            if (n.isEmpty()) n = p.findElements(By.xpath(".//*[normalize-space(text())='Narx']/following::input[1]"));
            String pv = maydonYoz(n.get(0), past).replaceAll("\\s", "");
            if (!pv.equals(past)) { negativXatolar.add("Past narx: maydonga " + past + " yozilmadi (" + pv + "), tekshirilmadi"); return; }
            int oldin = taklifSoni();
            jsClick(driver, p.findElement(By.xpath(".//button[normalize-space(.)='Sotib olish']")));
            waitFor(() -> taklifSoni() > oldin ? Boolean.TRUE : null, 3);
            boolean berildi = taklifSoni() > oldin;
            sleep(3000); // savatcha yangilanishi uchun
            WebElement kitob = lastVisible(driver, By.xpath("//*[@role='grid' or self::table][.//*[normalize-space(text())='Narx'] and .//*[normalize-space(text())='Miqdor']]"));
            rasmOl("past_narx");
            if (kitob == null) { negativXatolar.add("Past narx: savatcha oynasi yo'q, tekshirilmadi"); return; }
            boolean korinadi = rangliQatorlar(kitob).stream().anyMatch(q -> q.replaceAll("\\s", "").contains(past));
            System.out.println("5n) past narx [" + past + "]: taklif " + (berildi ? "berildi" : "berilmadi") + ", savatchada " + (korinadi ? "KO'RINDI - XATO" : "ko'rinmadi ✓") + " (kontrakt tuzilmagani 6-qadamda tekshiriladi)");
            if (korinadi) negativXatolar.add("Past narxdagi (" + past + ") taklif savatchada ko'rindi");
        } catch (Throwable e) {
            rasmOl("past_narx_xato");
            negativXatolar.add("Past narx: tekshirib bo'lmadi: " + cut(String.valueOf(e.getMessage()), 120));
        }
    }

    WebElement salbiyPanel;

    WebElement yangiOyna() {
        try { return oynaTayyorla(BID_LOTS); } catch (Throwable e) { System.out.println("5n) oyna ochilmadi: " + e.getMessage()); return null; }
    }

    String maydonYoz(WebElement in, String v) {
        js().executeScript("arguments[0].focus()", in);
        in.sendKeys(Keys.CONTROL + "a");
        in.sendKeys(Keys.DELETE);
        if (!v.isEmpty()) in.sendKeys(v);
        String r = in.getAttribute("value");
        return r == null ? "" : r;
    }

    /** Oynada mijoz tanlanganmi (yuborilgach oyna tozalanadi - keyingi holat mijozsiz ketib, yolg'on 'rad etildi' chiqmasin). */
    boolean mijozTanlangan(WebElement p) {
        for (WebElement in : p.findElements(By.cssSelector("input[id^='react-select']"))) {
            Object t = js().executeScript("let c=arguments[0].closest('[class*=\"-container\"]'); return c? c.innerText.trim() : '';", in);
            if ("Mijoz".equals(String.valueOf(t))) return false;
        }
        return true;
    }

    int taklifSoni() {
        WebElement p = meningTakliflarim();
        return p == null ? -1 : rangliQatorlar(p).size();
    }

    void salbiyHolat(String nom, String lot, String pul, WebElement oyna) {
        boolean malumot = nom.contains("ma'lumot uchun");
        try {
            WebElement p = oyna;
            if (p == null) {
                boolean bor;
                try { bor = salbiyPanel != null && salbiyPanel.isDisplayed() && mijozTanlangan(salbiyPanel); } catch (Exception e) { bor = false; }
                if (!bor) salbiyPanel = yangiOyna();
                p = salbiyPanel;
            }
            if (p == null) { System.out.println("5n) " + nom + ": oyna yo'q, TEKSHIRILMADI"); negativXatolar.add(nom + ": tekshirib bo'lmadi (oyna ochilmadi)"); return; }
            String lv = maydonYoz(p.findElement(By.cssSelector("input[placeholder='Miqdor']")), lot);
            List<WebElement> narxlar = p.findElements(By.cssSelector("input[placeholder='Narx'], input[name='price']"));
            if (narxlar.isEmpty()) narxlar = p.findElements(By.xpath(".//*[normalize-space(text())='Narx']/following::input[1]"));
            String pv = narxlar.isEmpty() ? "?" : maydonYoz(narxlar.get(0), pul);
            String lt = lv.replaceAll("\\s", ""), pt = pv.replaceAll("\\s", "");
            if (!lt.equals(lot) || (!pv.equals("?") && !pt.equals(pul))) {
                System.out.println("5n) " + nom + ": yozildi [" + lot + " x " + pul + "], maydonda [" + lv + " x " + pv + "] -> maydon qabul qilmadi ✓");
                return;
            }
            if (pv.equals("?") && !pul.equals(NARX)) { System.out.println("5n) " + nom + ": narx maydoni topilmadi, TEKSHIRILMADI"); negativXatolar.add(nom + ": narx maydoni topilmadi"); return; }
            int oldin = taklifSoni();
            jsClick(driver, p.findElement(By.xpath(".//button[normalize-space(.)='Sotib olish']")));
            Set<String> x = new LinkedHashSet<>();
            for (long end = System.currentTimeMillis() + 2000; System.currentTimeMillis() < end; sleep(200)) {
                x.addAll(toasts(driver));
                if (!Eimzo.windowTitle().isEmpty()) { Eimzo.closeIfShown(1); x.add("E-IMZO so'radi (parolsiz yopildi)"); }
                if (taklifSoni() > oldin) break; // xabarga qarab to'xtamaymiz: oldingi holatdan qolgan bo'lishi mumkin
            }
            String oynaMatni = "";
            try { oynaMatni = clean(p.getText()); } catch (Exception ignore) {}
            int keyin = taklifSoni();
            if (oldin < 0 || keyin < 0) { System.out.println("5n) " + nom + ": takliflar soni o'qilmadi, TEKSHIRILMADI"); negativXatolar.add(nom + ": takliflar soni o'qilmadi"); return; }
            boolean qabul = keyin > oldin;
            System.out.println("5n) " + nom + " [" + lv + " x " + pv + "] -> " + (qabul ? "QABUL QILINDI" : "rad etildi ✓") + ", takliflar " + oldin + " -> " + keyin + ", xabarlar: " + x
                    + (x.isEmpty() && !qabul ? " | oyna: " + cut(oynaMatni, 160) : ""));
            if (qabul) {
                salbiyPanel = null; // qabul qilingach oyna tozalanadi
                rasmOl("salbiy_qabul");
                if (!malumot) negativXatolar.add("Taklif: " + nom + " (" + lv + " x " + pv + ") QABUL QILINDI");
                WebElement mt = meningTakliflarim();
                boolean ochdi = engYangisiniOchir(mt);
                System.out.println("5n)    qabul qilingan taklif o'chirildi: " + (ochdi ? "ha" : "YO'Q"));
                if (!ochdi) negativXatolar.add(nom + ": qabul qilingan taklif o'chmadi, bitimga aylanishi mumkin");
            }
        } catch (Throwable e) {
            rasmOl("salbiy_xato");
            System.out.println("5n) " + nom + ": TEKSHIRILMADI: " + cut(String.valueOf(e.getMessage()), 150));
            negativXatolar.add(nom + ": tekshirib bo'lmadi");
        }
    }

    /** Savdo tugagach oldindan ochilgan oynadan taklif: qabul qilinmasligi kerak (bitimlar soni ham 6-qadamda tekshiriladi). */
    void savdodanKeyinTaklif() {
        if (kechPanel == null) return;
        try {
            kim("trader2");
            boolean ochiq;
            try { ochiq = kechPanel.isDisplayed(); } catch (Exception e) { ochiq = false; }
            if (!ochiq) { System.out.println("5n) savdo tugagach: taklif oynasi yopilgan, taklif berib bo'lmaydi ✓"); return; }
            WebElement b = kechPanel.findElement(By.xpath(".//button[normalize-space(.)='Sotib olish']"));
            if (!b.isEnabled()) { System.out.println("5n) savdo tugagach: 'Sotib olish' o'chiq ✓"); return; }
            jsClick(driver, b);
            Set<String> x = new LinkedHashSet<>();
            for (long end = System.currentTimeMillis() + 2500; System.currentTimeMillis() < end; sleep(200)) x.addAll(toasts(driver));
            rasmOl("savdodan_keyin");
            boolean aniq = x.stream().anyMatch(y -> y.contains("vaqti emas"));
            System.out.println("5n) savdo tugagach taklif -> xabarlar: " + x + (aniq ? " -> rad etildi ✓" : " -> ANIQLANMADI (oyna yopilgan yoki tozalangan bo'lishi mumkin); bitim chiqmaganini 6-qadam tekshiradi"));
        } catch (Throwable e) {
            System.out.println("5n) savdo tugagach: tugma topilmadi (oyna yopilgan): " + cut(String.valueOf(e.getMessage()), 100));
        }
    }

    // ------------------------------------------------------------------ 5b: savatcha va taklifni o'chirish
    /** Savatcha / o'chirish bo'yicha topilgan nomuvofiqliklar (step10 da tekshiriladi, boshqa qadamlarni to'xtatmaydi). */
    List<String> savatchaXatolar = new ArrayList<>();
    boolean savatchaOqildi = false;

    /** Ro'yxat qatorlari: [rang | matn]. Rang fon bo'yicha: yashil - yutgan, qizil - yutqizgan, sariq - qisman. */
    List<String> rangliQatorlar(WebElement oyna) {
        List<?> o = (List<?>) js().executeScript(
                "const rows=[...arguments[0].querySelectorAll('[role=row],tbody tr')].filter(r=>!r.querySelector('[role=columnheader],th'));" +
                "return rows.map(r=>{ let c=null; for(const e of [r,...r.querySelectorAll('*')]){ const s=getComputedStyle(e).backgroundColor, m=s.match(/rgba?[(]([0-9]+), ([0-9]+), ([0-9]+)(?:, ([0-9.]+))?/);" +
                "  if(!m||m[4]==='0') continue; const v=[+m[1],+m[2],+m[3]]; if(Math.max(...v)-Math.min(...v)<8) continue; c=v; break; }" +
                "  let t='rangsiz'; if(c){ const [a,g,b]=c; if(g>=a&&g>=b) t='yashil'; else if(a>=g&&g>=b&&(g-b)>(a-g)) t='sariq'; else if(a>=g&&a>=b) t='qizil'; else t='boshqa'; }" +
                "  return t+(c?' rgb('+c.join(',')+')':'')+' | '+r.innerText.split(String.fromCharCode(10)).join(' ').trim(); });", oyna);
        List<String> r = new ArrayList<>();
        for (Object x : o) r.add(String.valueOf(x));
        return r;
    }

    Map<String, Integer> sanaRang(List<String> qatorlar) {
        Map<String, Integer> r = new LinkedHashMap<>();
        for (String s : qatorlar) r.merge(s.split(" ")[0], 1, Integer::sum);
        return r;
    }

    /** "Mening takliflarim" oynasi: ochiq bo'lmasa o'ng chetdagi 1-tugma (bolg'acha) bosiladi. */
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

    /** "Mening takliflarim" dagi eng yangi (1-qator) taklifni 🗑 bilan o'chiradi. Qator yo'qolsa true. */
    boolean engYangisiniOchir(WebElement p) {
        if (p == null) return false;
        int oldin = rangliQatorlar(p).size();
        // 🗑 ustuni o'ng chetda: oyna tor bo'lsa, jadval surilmaguncha sahifada bo'lmaydi
        js().executeScript("for(const e of [arguments[0],...arguments[0].querySelectorAll('*')]) if(e.scrollWidth>e.clientWidth+5) e.scrollLeft=e.scrollWidth;", p);
        waitFor(() -> p.findElements(By.cssSelector("[role=row] svg, tbody tr svg")).isEmpty() ? null : Boolean.TRUE, 3);
        Object bosildi = js().executeScript(
                "const rows=[...arguments[0].querySelectorAll('[role=row],tbody tr')].filter(r=>!r.querySelector('[role=columnheader],th')); if(!rows.length) return null;" +
                "const sv=[...rows[0].querySelectorAll('svg')]; if(!sv.length) return null; const s=sv[sv.length-1]; const b=s.closest('button,[role=button],a')||s;" +
                "['mousedown','mouseup','click'].forEach(t=>b.dispatchEvent(new MouseEvent(t,{bubbles:true}))); return 'ok';", p);
        if (bosildi == null) { rasmOl("ochirish_tugmasi_yoq"); return false; }
        WebElement ok = waitFor(() -> lastVisible(driver, By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button[contains(normalize-space(.),'chirish') or normalize-space(.)='Ha' or normalize-space(.)='Tasdiqlash']")), 2);
        if (ok != null) jsClick(driver, ok);
        imzoYoki(() -> rangliQatorlar(p).size() < oldin, 6);
        return rangliQatorlar(p).size() < oldin;
    }

    /** Eng yangi 1 lotli taklifni 🗑 bilan o'chiradi va ro'yxatdan yo'qolganini tekshiradi. */
    void taklifOchir() {
        try {
            WebElement p = meningTakliflarim();
            if (p == null) { rasmOl("takliflarim_yoq"); savatchaXatolar.add("'Mening takliflarim' oynasi ochilmadi"); return; }
            List<String> oldin = rangliQatorlar(p);
            System.out.println("5b) Mening takliflarim (" + oldin.size() + "): " + sanaRang(oldin) + " | 1-qator: " + (oldin.isEmpty() ? "-" : oldin.get(0)));
            if (oldin.size() != BIDS) savatchaXatolar.add("Berilgan takliflar " + BIDS + " ta, 'Mening takliflarim' da " + oldin.size() + " ta");
            if (!engYangisiniOchir(p)) savatchaXatolar.add("Taklif 🗑 bilan o'chmadi");
            List<String> keyin = rangliQatorlar(p);
            boolean kamaydi = keyin.size() == oldin.size() - 1;
            System.out.println("5b) taklif o'chirildi: " + oldin.size() + " -> " + keyin.size() + ", xabarlar: " + toasts(driver) + (kamaydi ? " ✓" : ""));
            rasmOl("taklif_ochirildi");
            if (!kamaydi) savatchaXatolar.add("O'chirilgan taklif 'Mening takliflarim' dan yo'qolmadi: " + oldin.size() + " -> " + keyin.size());
        } catch (Exception e) {
            rasmOl("ochirish_xato");
            savatchaXatolar.add("Taklifni o'chirib bo'lmadi: " + e.getMessage());
        }
    }

    /** 101 lotli taklifdan keyin yana 1 lot: lot qolmagan, yutqazishi kerak (qizil). */
    void yutqazadiganTaklif(WebElement panel) {
        try {
            boolean ochiq;
            try { ochiq = panel != null && panel.isDisplayed(); } catch (Exception e) { ochiq = false; }
            if (!ochiq) panel = oynaTayyorla(BID_LOTS);
            yuborish(panel, BIDS + 2, BID_LOTS);
        } catch (Throwable e) {
            savatchaXatolar.add("Yutqazadigan taklif berilmadi: " + e.getMessage());
        }
    }

    /** Kartochkadagi savatcha (bolg'acha yonidagi belgi) -> Narx / Miqdor oynasi, ranglar o'qiladi. */
    void savatchaTekshir() {
        try {
            WebElement mt = meningTakliflarim();
            List<String> taklif = mt == null ? new ArrayList<>() : rangliQatorlar(mt);
            System.out.println("5b) Mening takliflarim ranglari: " + sanaRang(taklif));
            taklif.forEach(x -> System.out.println("     " + x));
            WebElement belgi = kartochka().findElements(By.cssSelector("svg")).get(3);
            js().executeScript("const b=arguments[0].closest('button,[role=button],a')||arguments[0]; b.dispatchEvent(new MouseEvent('click',{bubbles:true}));", belgi);
            WebElement kitob = waitFor(() -> lastVisible(driver, By.xpath("//*[@role='grid' or self::table][.//*[normalize-space(text())='Narx'] and .//*[normalize-space(text())='Miqdor']]")), 6);
            sleep(1000);
            rasmOl("savatcha");
            if (kitob == null) { savatchaXatolar.add("Savatcha oynasi ochilmadi"); return; }
            List<String> q = rangliQatorlar(kitob);
            Map<String, Integer> rang = sanaRang(q);
            System.out.println("5b) SAVATCHA ranglari: " + rang + " (kutilgan: yashil " + KUTILGAN + ", sariq 1, qizil 1)");
            q.forEach(x -> System.out.println("     " + x));
            savatchaOqildi = true;
            int y = rang.getOrDefault("yashil", 0), s = rang.getOrDefault("sariq", 0), k = rang.getOrDefault("qizil", 0);
            if (y != KUTILGAN) savatchaXatolar.add("Savatcha: yashil (yutgan) " + y + " ta, kutilgan " + KUTILGAN);
            if (s != 1) savatchaXatolar.add("Savatcha: sariq (qisman) " + s + " ta, kutilgan 1");
            if (k != 1) savatchaXatolar.add("Savatcha: qizil (yutqizgan) " + k + " ta, kutilgan 1");
        } catch (Exception e) {
            rasmOl("savatcha_xato");
            savatchaXatolar.add("Savatchani o'qib bo'lmadi: " + e.getMessage());
        }
    }

    @Test(priority = 9, dependsOnMethods = "step6_bitimHosilBolishi",
          description = "Savatcha ranglari (yashil/sariq/qizil) va taklifni o'chirish")
    public void step10_savatcha() {
        if (!savatchaOqildi && savatchaXatolar.isEmpty()) savatchaXatolar.add("Savatcha o'qilmadi (savdo vaqti yetmadi)");
        Assert.assertTrue(savatchaXatolar.isEmpty(), "Savatcha / o'chirish: " + savatchaXatolar);
        System.out.println("10) Savatcha ranglari va taklifni o'chirish ✓");
    }

    /** tayyor() bajarilguncha kutadi; shu orada E-IMZO oynasi chiqsa parol kiritadi. Oyna chiqmasa bekorga kutmaydi. */
    boolean imzoYoki(java.util.function.Supplier<Boolean> tayyor, int sec) {
        boolean imzo = false;
        for (long end = System.currentTimeMillis() + sec * 1000L; System.currentTimeMillis() < end; sleep(150)) {
            if (!Eimzo.windowTitle().isEmpty()) imzo |= Eimzo.typePassword(1);
            try { if (Boolean.TRUE.equals(tayyor.get())) break; } catch (Exception ignore) {}
        }
        return imzo;
    }

    /** Sotuvchining (trader1) oldindan ochilgan o'z mahsulotiga taklif oynasi. */
    WebElement ozPanel;
    boolean ozKorinmaydi = false;

    /** 0 lotga taklif: yangi oyna, "Sotib olish". Sayt rad etishi kerak ("Min 1"). */
    void nolLotTekshir() {
        try {
            WebElement p = oynaTayyorla("0");
            jsClick(driver, p.findElement(By.xpath(".//button[normalize-space(.)='Sotib olish']")));
            boolean oyna = Eimzo.closeIfShown(3);
            List<String> t = toasts(driver);
            System.out.println("5-negativ) 0 lotga taklif -> xabarlar: " + t + (oyna ? " | E-IMZO oynasi chiqdi (parolsiz yopildi)" : ""));
            if (oyna) negativXatolar.add("Taklif: 0 lotga taklif formasi o'tdi va imzo so'radi (imzo qo'yilmadi)");
            try { WebElement x = p.findElement(By.xpath(".//*[name()='svg' or self::button][1]")); } catch (Exception ignore) {}
        } catch (Throwable e) {
            System.out.println("5-negativ) 0 lot: tekshirib bo'lmadi: " + e.getMessage());
        }
    }

    /** Taklif uchun qaysi mijoz tanlanadi (sotuvchi o'z mahsulotiga urinishida - birinchisi). */
    String joriyMijoz = XARIDOR_MIJOZ;

    boolean vaqtYetadimi(String nima) {
        if (LocalDateTime.now().isBefore(savdoEnd.minusSeconds(5))) return true;
        System.out.println("5-negativ) " + nima + ": savdo vaqti tugadi, tekshirilmadi");
        return false;
    }

    /** Xaridor (trader2): noto'g'ri miqdor bilan taklif. Imzo to'siladi, oyna chiqsa parolsiz yopiladi. */
    void salbiyTaklif(WebElement panel, String miqdor) {
        String nima = miqdor + " lotga taklif";
        if (!vaqtYetadimi(nima)) return;
        try {
            eimzoTosish(driver);
            boolean ochiq;
            try { ochiq = panel != null && panel.isDisplayed(); } catch (Exception e) { ochiq = false; }
            if (!ochiq) panel = oynaTayyorla(miqdor);
            WebElement m = panel.findElement(By.cssSelector("input[placeholder='Miqdor']"));
            js().executeScript("arguments[0].focus()", m);
            m.sendKeys(Keys.CONTROL + "a");
            m.sendKeys(miqdor);
            String v = m.getAttribute("value");
            jsClick(driver, panel.findElement(By.xpath(".//button[normalize-space(.)='Sotib olish']")));
            boolean oyna = Eimzo.closeIfShown(3);
            long urinish = eimzoUrinishlari(driver);
            List<String> t = toasts(driver);
            System.out.println("5-negativ) " + nima + " (maydonda '" + v + "') -> xabarlar: " + t + ", imzo so'rovi: " + (urinish + (oyna ? 1 : 0)));
            if (oyna || urinish > 0) negativXatolar.add("Taklif: " + nima + " formasi o'tdi va imzo so'radi (imzo qo'yilmadi)");
        } catch (Exception e) {
            System.out.println("5-negativ) " + nima + ": tekshirib bo'lmadi: " + e.getMessage());
        }
    }

    /**
     * 101 lotga taklif (savdoda 100 lot): sayt QABUL QILADI, lekin xaridor bor lotdan ko'p ololmaydi -> qisman yutadi
     * (savatchada sariq). Haqiqiy imzo bilan yuboriladi. Undan chiqqan katta bitim stsenariylarga berilmaydi.
     */
    void qismanTaklif(WebElement panel, String miqdor) {
        String nima = miqdor + " lotga taklif (qisman yutishi kerak)";
        if (!vaqtYetadimi(nima)) return;
        try {
            boolean ochiq;
            try { ochiq = panel != null && panel.isDisplayed(); } catch (Exception e) { ochiq = false; }
            if (!ochiq) panel = oynaTayyorla(miqdor);
            WebElement m = panel.findElement(By.cssSelector("input[placeholder='Miqdor']"));
            js().executeScript("arguments[0].focus()", m);
            m.sendKeys(Keys.CONTROL + "a");
            m.sendKeys(miqdor);
            jsClick(driver, panel.findElement(By.xpath(".//button[normalize-space(.)='Sotib olish']")));
            imzoYoki(() -> false, 1); // taklifda E-IMZO so'ralmaydi; chiqib qolsa parol kiritiladi
            System.out.println("5) " + nima + " yuborildi, xabarlar: " + toasts(driver));
            qismanTaklifBerildi = true; // natija 6-qadamda tuzilgan bitimdan tekshiriladi
        } catch (Exception e) {
            System.out.println("5) " + nima + ": berib bo'lmadi: " + e.getMessage());
        }
    }

    boolean qismanTaklifBerildi = false;

    /** Sotuvchi (trader1) o'z mahsulotiga taklif beradi: sayt rad etishi kerak (server tekshiradi, haqiqiy imzo). */
    void ozMahsulotigaTaklif(String base) {
        String nima = "sotuvchi o'z mahsulotiga taklif";
        if (ozKorinmaydi) { System.out.println("5-negativ) " + nima + ": mahsulot sotuvchiga ko'rinmaydi (tekshirildi, ✓)"); return; }
        if (!vaqtYetadimi(nima)) return;
        try {
            kim("trader1");
            joriyMijoz = "";
            WebElement p = ozPanel;
            boolean ochiq;
            try { ochiq = p != null && p.isDisplayed(); } catch (Exception e) { ochiq = false; }
            if (!ochiq) { savdoSahifasi(base); if (!vaqtYetadimi(nima)) return; p = oynaTayyorla(BID_LOTS); }
            jsClick(driver, p.findElement(By.xpath(".//button[normalize-space(.)='Sotib olish']")));
            Eimzo.typePassword(6);
            sleep(800);
            ozTaklifXabarlari = toasts(driver);
            System.out.println("5-negativ) " + nima + " -> xabarlar: " + ozTaklifXabarlari + " (bitim chiqsa 6-qadamda aniqlanadi)");
        } catch (Throwable e) {
            System.out.println("5-negativ) " + nima + ": oyna ochilmadi yoki rad etildi: " + e.getMessage());
        } finally {
            joriyMijoz = XARIDOR_MIJOZ;
        }
    }

    List<String> ozTaklifXabarlari = new ArrayList<>();

    /** Treyder 2: "Sotib olish" tabini ochib, mahsulotni qidiradi. */
    void savdoSahifasi(String base) {
        driver.get(base + "/uz/trader/sales/auction-sales?tradeTab=SELL");
        sleep(1500);
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//*[normalize-space(text())='Sotib olish']")), 20));
        sleep(1500); // tab almashgach sahifa qayta chiziladi
        Object typed = waitFor(() -> {
            WebElement s = visible(driver, By.cssSelector("main input[placeholder='Qidirish']"));
            if (s == null) return null;
            s.sendKeys(PRODUCT);
            return Boolean.TRUE;
        }, 20);
        Assert.assertNotNull(typed, "'Sotib olish' tabida qidiruv maydoni topilmadi");
        sleep(2000);
    }

    /** Ochiq "Mijoz" ro'yxatidan kerakli mijoz (XARIDOR_MIJOZ) yoki birinchisi. */
    WebElement mijozVarianti() {
        WebElement first = null;
        for (WebElement op : driver.findElements(By.cssSelector("[id*='-option-']"))) {
            try {
                if (!op.isDisplayed()) continue;
                if (first == null) first = op;
                if (!joriyMijoz.isEmpty() && op.getText().contains(joriyMijoz)) return op;
            } catch (Exception ignore) {}
        }
        return joriyMijoz.isEmpty() ? first : null;
    }

    /** Treyder 2: ochiq sahifadagi kartochkadan oynani ochib, BID_LOTS lotga bitta taklif beradi (sahifa qayta ochilmaydi). */
    void taklifBer(String base, int b, String lotlar) {
        yuborish(oynaTayyorla(lotlar), b, lotlar);
    }

    /** Savdodagi mahsulot kartochkasi (belgilar: 0 hujjat, 1 yulduz, 2 bolg'acha, 3 savatcha). */
    WebElement kartochka() {
        WebElement card = visible(driver, By.xpath("//*[contains(text(),'" + PRODUCT + "')]"));
        if (card == null) return null;
        for (int up = 0; up < 15; up++) {
            card = card.findElement(By.xpath("./.."));
            if (card.getText().contains(" lot") && card.findElements(By.cssSelector("svg")).size() >= 3) break;
        }
        return card;
    }

    /** Kartochkadan taklif oynasini ochadi, mijozni tanlaydi, miqdorni yozadi (YUBORMAYDI). Panelni qaytaradi. */
    WebElement oynaTayyorla(String lotlar) {
        List<WebElement> oldingi = ochiqOynalar();
        // Kartochkadagi 🔨 (Ariza yuborish, F4) — sahifa qayta chizilishi mumkin, shuning uchun qayta urinamiz
        Object clicked = waitFor(() -> {
            WebElement card = kartochka();
            if (card == null) return null;
            WebElement gavel = card.findElements(By.cssSelector("svg")).get(2);
            try { jsClick(driver, gavel.findElement(By.xpath("./ancestor-or-self::*[self::button or @role='button' or contains(@class,'cursor-pointer')][1]"))); }
            catch (org.openqa.selenium.NoSuchElementException e) { js().executeScript("arguments[0].dispatchEvent(new MouseEvent('click',{bubbles:true}))", gavel); }
            return Boolean.TRUE;
        }, 30);
        Assert.assertNotNull(clicked, "Savdoda '" + PRODUCT + "' kartochkasi chiqmadi");
        // 🔨 har bosilganda yangi oyna ochiladi: yangisi - oldin bo'lmagani
        WebElement panel = waitFor(() -> { for (WebElement p : ochiqOynalar()) if (!oldingi.contains(p)) return p; return null; }, 15);
        Assert.assertNotNull(panel, "'Ariza yuborish' oynasi ochilmadi");
        // Mijoz
        for (WebElement in : panel.findElements(By.cssSelector("input[id^='react-select']"))) {
            Object txt = js().executeScript("let c=arguments[0].closest('[class*=\"-container\"]'); return c? c.innerText : '';", in);
            if (mijozsiz || !String.valueOf(txt).contains("Mijoz")) continue;
            // mijoz STIR'i yoziladi va chiqqan variant tanlanadi (ro'yxat aylantirilmaydi)
            if (joriyMijoz.isEmpty()) in.sendKeys(Keys.ARROW_DOWN); else in.sendKeys(joriyMijoz);
            WebElement tanlov = waitFor(this::mijozVarianti, 6);
            Assert.assertNotNull(tanlov, "Mijozlar ro'yxatida '" + XARIDOR_MIJOZ + "' yo'q");
            System.out.println("5) Mijoz: " + clean(tanlov.getText()));
            jsClick(driver, tanlov);
            sleep(800);
        }
        WebElement miqdor = panel.findElement(By.cssSelector("input[placeholder='Miqdor']"));
        js().executeScript("arguments[0].focus()", miqdor);
        miqdor.sendKeys(Keys.CONTROL + "a");
        miqdor.sendKeys(lotlar);
        return panel;
    }

    /** Sahifada ochiq turgan taklif oynalari ("Sotib olish" tugmasi va "Miqdor" maydoni borlari). */
    List<WebElement> ochiqOynalar() {
        List<WebElement> r = new ArrayList<>();
        for (WebElement b : driver.findElements(By.xpath("//button[normalize-space(.)='Sotib olish']")))
            try { if (b.isDisplayed()) { WebElement p = b.findElement(By.xpath("./ancestor::*[.//input[@placeholder='Miqdor']][1]")); if (!r.contains(p)) r.add(p); } } catch (Exception ignore) {}
        return r;
    }

    /** Tayyor oynada "Sotib olish" ni bosib, imzolaydi (b=0: salbiy urinish, imzo to'silgan). */
    void yuborish(WebElement panel, int b, String lotlar) {
        jsClick(driver, panel.findElement(By.xpath(".//button[normalize-space(.)='Sotib olish']")));
        if (b == 0) { sleep(3000); return; } // salbiy urinish: imzo to'silgan, parol yozilmaydi
        imzoYoki(() -> false, 1); // taklifda E-IMZO so'ralmaydi; chiqib qolsa parol kiritiladi
        List<String> t = toasts(driver);
        Assert.assertFalse(t.stream().anyMatch(x -> x.contains("Majburiy")), "Taklif yuborilmadi: " + t);
        System.out.println("5) " + b + "-taklif: " + lotlar + " lotga taklif berildi. Xabarlar: " + t);
    }

    // ------------------------------------------------------------------ 6
    @Test(priority = 6, dependsOnMethods = "step5_taklifBerish",
          description = "Savdo tugagach bitim hosil bo'ladi (Treyder 1 -> Mening shartnomalarim -> Sotish)")
    public void step6_bitimHosilBolishi() {
        waitUntil(savdoEnd.plusSeconds(2), "savdo tugashi");
        savdodanKeyinTaklif();
        waitUntil(savdoEnd.plusSeconds(6), "bitim yaratilishi");
        int jamiKutilgan = KUTILGAN + (qismanTaklifBerildi ? 1 : 0); // 1 lotlilar + 101 lotdan qolgani
        String base = Config.traderUrl();
        kim("trader1");
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String time = savdoEnd.format(HM);
        // bitimlar soni 2 marta ketma-ket o'zgarmasa (yoki kutilgan songa yetsa) kutish to'xtaydi
        int oldingiSoni = -1, barqaror = 0;
        for (int attempt = 0; attempt < 8 && dealNos.size() < jamiKutilgan && barqaror < 2; attempt++) {
            openContracts(base, "Sotish");
            for (WebElement r : driver.findElements(By.cssSelector("main tbody tr"))) {
                String tx = r.getText();
                if (tx.contains(today) && tx.contains(PRODUCT) && tx.contains("Imzolash kutilmoqda") && shuSavdodanmi(tx)) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("B\\d{8}").matcher(tx);
                    // sotuvchi o'zidan sotib olgan bitim (STIR ikki marta) = salbiy holat xatosi
                    if (m.find() && tx.split("311927414", -1).length > 2) {
                        String x = "Sotuvchi o'z mahsulotiga taklif bera oldi va bitim tuzildi: " + m.group();
                        if (!negativXatolar.contains(x)) negativXatolar.add(x);
                        continue;
                    }
                    if (m.start() >= 0 && !dealNos.contains(m.group())) dealNos.add(m.group());
                    java.util.regex.Matcher lm = java.util.regex.Pattern.compile("UZS [|] ([0-9]+) [|]").matcher(clean(tx));
                    if (lm.find()) bitimLotlari.put(m.group(), Integer.parseInt(lm.group(1)));
                }
            }
            if (!dealNos.isEmpty() && dealNos.size() == oldingiSoni) barqaror++; else barqaror = 0;
            oldingiSoni = dealNos.size();
            if (dealNos.size() < jamiKutilgan && barqaror < 2) sleep(3000);
        }
        Assert.assertFalse(dealNos.isEmpty(), "Bugungi '" + PRODUCT + "' bitimi topilmadi (savdo " + time + " da tugagan)");
        dealNo = dealNos.get(0);
        // stsenariylarga faqat BID_LOTS (1) lotli bitimlar beriladi; 101 lotli (qisman) taklifdan chiqqan katta bitim berilmaydi
        OXIRGI_BITIMLAR.clear();
        for (String no : dealNos) if (String.valueOf(bitimLotlari.getOrDefault(no, 1)).equals(BID_LOTS)) OXIRGI_BITIMLAR.add(no);
        System.out.println("6) Lotlar: " + bitimLotlari + " | stsenariylar uchun: " + OXIRGI_BITIMLAR);
        System.out.println("6) Bitimlar hosil bo'ldi (" + dealNos.size() + " ta, kutilgan " + jamiKutilgan + "): " + dealNos);
        if (dealNos.size() != jamiKutilgan) negativXatolar.add("Bitimlar " + dealNos.size() + " ta, kutilgan " + jamiKutilgan + " (past narx, yutqazgan yoki o'chirilgan taklifdan bitim chiqmasligi kerak)");
        if (OXIRGI_BITIMLAR.size() != KUTILGAN) savatchaXatolar.add("1 lotli bitimlar " + OXIRGI_BITIMLAR.size() + " ta, kutilgan " + KUTILGAN + " (o'chirilgan yoki yutqazgan taklif ham bitim bo'lgan bo'lishi mumkin)");
        // 101 lotli taklif natijasi bitimdan aniqlanadi: qolgan lotlarga (100 - 7) bitim tuzilgan bo'lsa -> qisman yutgan ✓
        int lotlar = Integer.parseInt(LOTS), qolgan = lotlar - KUTILGAN * Integer.parseInt(BID_LOTS);
        String katta = null;
        for (Map.Entry<String, Integer> e : bitimLotlari.entrySet()) if (e.getValue() > Integer.parseInt(BID_LOTS)) katta = e.getKey();
        if (katta != null) {
            int lot = bitimLotlari.get(katta);
            System.out.println("6) " + (lotlar + 1) + " lotli taklif QABUL QILINDI va qisman yutdi: " + katta + " = " + lot + " lot (kutilgan " + qolgan + ")"
                    + (lot == qolgan ? " ✓" : " — DIQQAT: soni mos emas"));
            if (lot != qolgan) negativXatolar.add("101 lotli taklif: " + lot + " lot berildi, kutilgan " + qolgan);
        } else if (qismanTaklifBerildi) {
            negativXatolar.add("101 lotli taklif yuborildi, lekin qolgan " + qolgan + " lotga bitim tuzilmadi");
        }
    }

    /** Bitim qatoridagi vaqt shu savdo vaqtiga to'g'ri keladimi (avvalgi run'lardan qolgan bitimlar olinmasin). */
    boolean shuSavdodanmi(String tx) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{2}/\\d{2}/\\d{4}) [|] (\\d{2}:\\d{2}:\\d{2})").matcher(clean(tx));
        if (!m.find() || savdoStart == null) return true;
        try {
            LocalDateTime t = LocalDateTime.parse(m.group(1) + " " + m.group(2), DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            return !t.isBefore(savdoStart.minusMinutes(1)) && !t.isAfter(savdoEnd.plusMinutes(5));
        } catch (Exception e) { return true; }
    }

    // ------------------------------------------------------------------ 7
    @Test(priority = 7, dependsOnMethods = "step6_bitimHosilBolishi",
          description = "Ikkala treyder bitimni 'Ommaviy imzolash' orqali imzolaydi")
    public void step7_treyderlarImzolashi() {
        String base = Config.traderUrl();
        signDeal(base, "trader1", "Sotish");
        signDeal(base, "trader2", "Sotib olish"); // mijoz nomidan bo'lsa "Mijozimning shartnomalari"
    }

    // ------------------------------------------------------------------ 8
    @Test(priority = 8, dependsOnMethods = "step7_treyderlarImzolashi",
          description = "Admin bitimni 'Tasdiqlanmagan' tabidan tasdiqlaydi")
    public void step8_adminTasdiqlashi() {
        String base = Config.adminUrl();
        kim("admin2");
        adminOmmaviyTasdiqla(base, dealNos);
    }


    /** "Yangi" tabidagi eng yangi, bugungi va shu mahsulotli kontraktning ID raqami. */
    String findNewContractId() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        for (WebElement r : driver.findElements(By.cssSelector("main tbody tr"))) {
            List<WebElement> tds = r.findElements(By.cssSelector("td"));
            if (tds.size() > 6 && r.getText().contains(today) && r.getText().contains(PRODUCT)) {
                String id = tds.get(4).getText().trim();
                System.out.println("2) Yangi kontrakt ID: " + id);
                return id;
            }
        }
        throw new AssertionError("Moderatorda bugungi '" + PRODUCT + "' kontrakti topilmadi");
    }

    WebElement findContractRow(String id) {
        for (String tab : new String[]{null, "Yangi", "Ochilgan kontraktlar"}) {
            if (tab != null) {
                WebElement b = visible(driver, By.xpath("//main//button[starts-with(normalize-space(.),'" + tab + "')]"));
                if (b != null) { jsClick(driver, b); sleep(4000); }
            }
            for (WebElement r : driver.findElements(By.cssSelector("main tbody tr"))) {
                List<WebElement> tds = r.findElements(By.cssSelector("td"));
                if (tds.size() > 4 && tds.get(4).getText().trim().equals(id)) return r;
            }
        }
        return null;
    }

    /** Sessiyaning eng erta boshlanishi va eng kech tugashi. */
    LocalTime[] sessionRange(String session) {
        LocalTime min = null, max = null;
        for (WebElement tr : driver.findElements(By.cssSelector("main tbody tr"))) {
            List<WebElement> tds = tr.findElements(By.cssSelector("td"));
            if (tds.size() < 5 || !tds.get(1).getText().contains(session)) continue;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{2}:\\d{2}).*?(\\d{2}:\\d{2})").matcher(tds.get(4).getText().replace("\n", " "));
            if (!m.find()) continue;
            LocalTime s = LocalTime.parse(m.group(1)), e = LocalTime.parse(m.group(2));
            if (min == null || s.isBefore(min)) min = s;
            if (max == null || e.isAfter(max)) max = e;
        }
        return min == null ? null : new LocalTime[]{min, max};
    }

    /** Sessiya jadvalidagi qatorni (seksiya matni bo'yicha) tahrirlab, vaqtini o'rnatadi. */
    void editRow(String session, String section, LocalDateTime start, LocalDateTime end) {
        driver.navigate().refresh();
        waitFor(() -> visible(driver, By.cssSelector("main tbody tr")), 30);
        sleep(2000);
        WebElement row = null;
        for (WebElement tr : driver.findElements(By.cssSelector("main tbody tr"))) {
            List<WebElement> tds = tr.findElements(By.cssSelector("td"));
            if (tds.size() > 3 && tds.get(1).getText().contains(session) && tds.get(2).getText().contains(section)) { row = tr; break; }
        }
        if (row == null) { System.out.println("Sessiya qatori yo'q: " + session + " / " + section); return; }
        List<WebElement> tds = row.findElements(By.cssSelector("td"));
        WebElement dots = tds.get(tds.size() - 1).findElement(By.cssSelector("button"));
        boolean opened = false;
        for (int t = 0; t < 3 && !opened; t++) {
            jsClick(driver, dots);
            sleep(1500);
            WebElement edit = lastVisible(driver, By.xpath("//*[contains(text(),'zgartirish') or contains(text(),'ahrirlash')]"));
            if (edit != null) { try { edit.click(); } catch (Exception e) { jsClick(driver, edit); } }
            opened = waitFor(() -> driver.findElements(By.id("startTime")).isEmpty() ? null : Boolean.TRUE, 6) != null;
        }
        Assert.assertTrue(opened, "Sessiya tahrirlash oynasi ochilmadi: " + section);
        setTime("startTime", start.format(HM));
        setTime("endTime", end.format(HM));
        jsClick(driver, lastVisible(driver, By.xpath("//button[normalize-space(.)='Saqlash']")));
        Eimzo.typePassword(5);
        sleep(2500);
    }

    /** Vaqt tanlagich: maydon atrofini bosib, soat va daqiqa ustunlaridan tanlaydi. */
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
        // vaqt tanlagichni yopish: OYNA ichidagi sarlavha bosiladi (orqadagi yo'lakdagi shu nomli yozuv emas)
        WebElement title = lastVisible(driver, By.xpath("//*[@role='dialog' or contains(@class,'modal')]//*[normalize-space(text())='Savdoni tahrirlash' or normalize-space(text())=\"Savdo uchun e'lon berish\"][not(ancestor::button)]"));
        if (title != null) { realClick(driver, title); sleep(300); }
        Assert.assertEquals(driver.findElement(By.id(id)).getAttribute("value"), hhmm, id + " vaqti o'rnatilmadi");
    }
}
