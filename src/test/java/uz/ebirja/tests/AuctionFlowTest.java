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
    String yuborilganVaqt;      // yangi kontrakt yuborilgan vaqt, soniyasigacha (treyder ro'yxatidan) - moderatorda shu bo'yicha topiladi
    String radVaqt, radId;      // ikkinchi kontrakt: moderator uni 1-rolda rad etadi
    String contractId;          // yangi kontrakt ID si (moderator ro'yxatidan)
    String savdoRaqami;         // shu kontraktning savdo raqami ("Kontrakt raqam") - savdo va bitimlar faqat shunda bo'lishi kerak
    LocalDateTime elonStart, savdoStart, savdoEnd;
    Map<String, Integer> bitimLotlari = new LinkedHashMap<>();
    /** Shu run'da auksionda hosil bo'lgan bitimlar (keyingi "Bitim holatlari" testi shulardan foydalanadi). */
    public static final List<String> OXIRGI_BITIMLAR = new ArrayList<>();
    /** Shu run'da savdo testi (auksion yoki qarama-qarshi) ishga tushgan bo'lsa true: bitim stsenariylari eski bitimlarga o'tmaydi. */
    public static volatile boolean SAVDO_ISHLADI = false;


    // ------------------------------------------------------------------ 1
    @Test(priority = 1, description = "Treyder 1 ikkita auksion kontrakti yaratadi va E-IMZO bilan yuboradi (biri rad etiladi, biri tasdiqlanadi)")
    public void step1_kontraktYaratish() {
        SAVDO_ISHLADI = true;
        String base = Config.traderUrl();
        kim("trader1");
        radVaqt = kontraktYubor(base, "R");
        yuborilganVaqt = kontraktYubor(base, "", radVaqt);
        System.out.println("1) Ikkita kontrakt yuborildi: rad etiladigani " + radVaqt + ", tasdiqlanadigani " + yuborilganVaqt);
    }

    /** Bitta kontrakt yaratadi, yuboradi va uning aniq yuborilgan vaqtini qaytaradi. */
    String kontraktYubor(String base, String qoshimcha, String... oldingilar) {
        LocalDateTime boshlandi = LocalDateTime.now();
        String belgi = "AT-" + boshlandi.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + qoshimcha;
        KontraktForma forma = new KontraktForma(driver, PRODUCT).ochish(base).togriToldirish(true);
        if (visible(driver, By.name("specialConditionUz")) != null) forma.set("specialConditionUz", belgi);
        forma.yuborishniBosish();
        Eimzo.typePassword();
        Object ok = waitFor(() -> driver.getCurrentUrl().contains("/create") ? null : Boolean.TRUE, 60);
        Assert.assertNotNull(ok, "Kontrakt yuborilmadi. Xabarlar: " + toasts(driver));
        String vaqt = yuborilganKontraktVaqti(base, boshlandi, oldingilar);
        System.out.println("1) Kontrakt yuborildi: " + PRODUCT + ", belgi " + belgi + ", vaqti " + vaqt + " (treyder -> Listingga yuborilgan)");
        return vaqt;
    }

    /** Jadvaldagi ustun qiymati (sarlavha nomi bo'yicha, tartib raqamiga bog'lanmaydi). */
    String ustun(WebElement row, String sarlavha) { return uz.ebirja.pages.Jadval.ustun(driver, row, sarlavha); }

    /** Treyder -> Listing -> "Listingga yuborilgan": shu run'da yuborilgan kontraktning aniq vaqti. Topilmasa test yiqiladi. */
    String yuborilganKontraktVaqti(String base, LocalDateTime boshlandi, String... bundanBoshqa) {
        driver.get(base + "/uz/trader/listing/sales");
        WebElement tab = waitFor(() -> visible(driver, By.xpath("//main//*[starts-with(normalize-space(.),'Listingga yuborilgan')][self::button or @role='tab' or not(*)]")), 20);
        Assert.assertNotNull(tab, "'Listingga yuborilgan' tabi yo'q");
        jsClick(driver, tab);
        String vaqt = waitFor(() -> {
            for (WebElement r : driver.findElements(By.cssSelector("main tbody tr"))) {
                String x = ustun(r, "Yuborilgan sana");
                if (!r.getText().contains(PRODUCT) || !x.matches("\\d{2}/\\d{2}/\\d{4} [|] \\d{2}:\\d{2}:\\d{2}")) continue;
                LocalDateTime t = LocalDateTime.parse(x.replace(" | ", " "), DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
                // oldingi run kontraktlari va shu run'dagi boshqa kontrakt olinmaydi
                if (!t.isBefore(boshlandi.minusSeconds(30)) && !Arrays.asList(bundanBoshqa).contains(x)) return x;
            }
            return null;
        }, 30);
        Assert.assertNotNull(vaqt, "Yuborilgan kontrakt treyderning 'Listingga yuborilgan' ro'yxatida ko'rinmadi");
        return vaqt;
    }

    /** Moderator -> Kontraktlar: aynan shu vaqtda, shu sotuvchi yuborgan shu mahsulotli kontraktning ID si (begona kontrakt tasdiqlanmasin). */
    String moderatordaKontraktId(String vaqt) {
        String stir = Config.getOr("trader1.stir", "311927414");
        String id = waitFor(() -> {
            for (WebElement r : driver.findElements(By.cssSelector("main tbody tr")))
                if (vaqt.equals(ustun(r, "Ariza yuborilgan sana")) && r.getText().contains(PRODUCT) && r.getText().contains(stir)) {
                    String x = ustun(r, "ID raqam");
                    if (x.matches("\\d+")) return x;
                }
            return null;
        }, 20);
        Assert.assertNotNull(id, "Moderator ro'yxatida " + vaqt + " da yuborilgan '" + PRODUCT + "' kontrakti (sotuvchi " + stir + ") topilmadi");
        return id;
    }

    /** Moderator -> "Ochilgan kontraktlar": ID bo'yicha kontraktning savdo raqami. */
    String ochilganKontraktRaqami(String base, String id) {
        driver.get(base + "/uz/moderationv2/listing/contracts");
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//main//button[starts-with(normalize-space(.),'Ochilgan kontraktlar')]")), 20));
        String raqam = waitFor(() -> {
            for (WebElement r : driver.findElements(By.cssSelector("main tbody tr")))
                if (id.equals(ustun(r, "ID raqam"))) { String x = ustun(r, "Kontrakt raqam"); if (x.matches("\\d+")) return x; }
            return null;
        }, 30);
        Assert.assertNotNull(raqam, "Tasdiqlangan kontrakt (ID " + id + ") 'Ochilgan kontraktlar' da topilmadi yoki savdo raqami yo'q");
        return raqam;
    }


    // ------------------------------------------------------------------ 2a
    boolean radEtildi = false;
    static final String RAD_SABAB = "Avtotest: rad etish";

    /** Moderatorning ochiq turgan ro'yxatida ID bo'yicha qator (sarlavha bo'yicha). */
    WebElement yangiQator(String id) {
        for (WebElement r : driver.findElements(By.cssSelector("main tbody tr"))) if (id.equals(ustun(r, "ID raqam"))) return r;
        return null;
    }

    /** Ro'yxat bo'limini (Yangi / Rad etilgan / Ochilgan kontraktlar) ochadi. */
    void moderatorTab(String tab) {
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//main//button[starts-with(normalize-space(.),'" + tab + "')]")), 20));
        jadvalKut(driver);
        sleep(1000);
    }

    @Test(priority = 2, dependsOnMethods = "step1_kontraktYaratish",
          description = "Moderator ikkinchi kontraktni BITTA rolda rad etadi: kontrakt darhol 'Rad etilgan' ga o'tadi")
    public void step2a_moderatorRadEtishi() {
        String base = Config.adminUrl();
        kim("admin1");
        switchRole("MODERATOR_LISTING_MAIN");
        String url = base + "/uz/moderationv2/listing/contracts";
        driver.get(url);
        waitFor(() -> visible(driver, By.cssSelector("main tbody tr")), 30);
        sleep(500);
        radId = moderatordaKontraktId(radVaqt);
        // --- SALBIY: sababsiz rad etish (imzo to'sib qo'yilgan)
        radOynasi();
        eimzoTosish(driver);
        jsClick(driver, radTugma());
        waitFor(() -> eimzoUrinishlari(driver) > 0 || lastVisible(driver, By.xpath("//*[@role='dialog']//*[contains(text(),'Majburiy maydon')]")) != null ? Boolean.TRUE : null, 5);
        boolean majburiy = lastVisible(driver, By.xpath("//*[@role='dialog']//*[contains(text(),'Majburiy maydon')]")) != null;
        System.out.println("2a) sababsiz rad etish -> " + (majburiy && eimzoUrinishlari(driver) == 0 ? "rad etildi ✓" : "QABUL QILINDI") + " " + toasts(driver) + ", imzo so'rovlari " + eimzoUrinishlari(driver));
        Assert.assertTrue(majburiy && eimzoUrinishlari(driver) == 0, "Moderator sababsiz rad eta oldi");
        // --- IJOBIY: sahifa qayta ochiladi (imzo to'sig'i olinadi)
        driver.get(url);
        waitFor(() -> visible(driver, By.cssSelector("main tbody tr")), 30);
        sleep(500);
        radOynasi();
        WebElement sabab = waitFor(() -> lastVisible(driver, By.cssSelector("[role=dialog] textarea")), 5);
        Assert.assertNotNull(sabab, "Rad etish oynasida 'Sababi' maydoni yo'q");
        sabab.sendKeys(RAD_SABAB);
        jsClick(driver, radTugma());
        Eimzo.typePassword(8);
        boolean toast = waitToast(driver, "Muvaffaqiyatli", 10);
        System.out.println("2a) MODERATOR_LISTING_MAIN rad etdi (ID " + radId + "). Xabarlar: " + toasts(driver) + (toast ? "" : " (muvaffaqiyat xabari ko'rinmadi)"));
        // faqat bitta rol rad etdi: kontrakt darhol 'Yangi' dan chiqib 'Rad etilgan' ga o'tishi kerak
        driver.get(url);
        waitFor(() -> visible(driver, By.cssSelector("main tbody tr")), 30);
        moderatorTab("Yangi");
        Assert.assertNull(yangiQator(radId), "Rad etilgan kontrakt (ID " + radId + ") hali ham 'Yangi' ro'yxatida");
        moderatorTab("Rad etilgan");
        WebElement r = waitFor(() -> yangiQator(radId), 10);
        Assert.assertNotNull(r, "Kontrakt (ID " + radId + ") 1 rolda rad etilgach 'Rad etilgan' ga o'tmadi");
        System.out.println("2a) moderator 'Rad etilgan' da: " + cut(clean(r.getText()), 400));
        radEtildi = true;
        Assert.assertTrue(r.getText().contains("Avtotest"), "Rad etish sababi moderator ro'yxatida yo'q: " + clean(r.getText()));
        // treyderda ham
        kim("trader1");
        driver.get(Config.traderUrl() + "/uz/trader/listing/sales");
        WebElement t = waitFor(() -> visible(driver, By.xpath("//main//*[starts-with(normalize-space(.),'Rad etilgan')][self::button or @role='tab' or not(*)]")), 20);
        Assert.assertNotNull(t, "Treyderda 'Rad etilgan' tabi yo'q");
        jsClick(driver, t);
        WebElement tr = waitFor(() -> {
            for (WebElement x : driver.findElements(By.cssSelector("main tbody tr"))) if (radVaqt.equals(ustun(x, "Yuborilgan sana"))) return x;
            return null;
        }, 15);
        Assert.assertNotNull(tr, "Rad etilgan kontrakt (" + radVaqt + ") treyderning 'Rad etilgan' ro'yxatida yo'q");
        System.out.println("2a) treyder 'Rad etilgan' da: " + cut(clean(tr.getText()), 300));
        Assert.assertTrue(tr.getText().contains("Avtotest"), "Treyderda rad etish sababi ko'rinmadi: " + clean(tr.getText()));
        System.out.println("2a) Rad etish: 1 rol yetarli, kontrakt darhol 'Rad etilgan' ga o'tdi (ID " + radId + ") ✓");
    }

    void radOynasi() {
        WebElement row = waitFor(() -> yangiQator(radId), 10);
        Assert.assertNotNull(row, "Kontrakt ID " + radId + " 'Yangi' ro'yxatida topilmadi");
        List<WebElement> tds = row.findElements(By.cssSelector("td"));
        WebElement dots = tds.get(tds.size() - 1).findElement(By.cssSelector("button"));
        js().executeScript("arguments[0].scrollIntoView({block:'center'})", dots);
        dots.click();
        WebElement it = waitFor(() -> lastVisible(driver, By.xpath("//*[normalize-space(text())='Rad etish']")), 10);
        Assert.assertNotNull(it, "'⋮' menyusida 'Rad etish' yo'q");
        it.click();
        Assert.assertNotNull(waitFor(this::radTugma, 10), "Rad etish oynasi ochilmadi");
    }

    WebElement radTugma() { return lastVisible(driver, By.xpath("//*[@role='dialog']//button[normalize-space(.)='Yuborish']")); }

    // ------------------------------------------------------------------ 2
    @Test(priority = 3, dependsOnMethods = "step1_kontraktYaratish",
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
                if (contractId == null) contractId = moderatordaKontraktId(yuborilganVaqt);
                if (radEtildi) Assert.assertNull(yangiQator(radId), role + ": rad etilgan kontrakt (ID " + radId + ") hali ham 'Yangi' ro'yxatida");
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
            savdoRaqami = ochilganKontraktRaqami(base, contractId);
            if (radEtildi) Assert.assertTrue(driver.findElements(By.cssSelector("main tbody tr")).stream().noneMatch(r -> radId.equals(ustun(r, "ID raqam"))), "Rad etilgan kontrakt (ID " + radId + ") 'Ochilgan kontraktlar' da ko'rindi");
            System.out.println("2) Kontrakt ochildi: ID " + contractId + " -> savdo raqami " + savdoRaqami);
        } finally {
            switchRole("MODERATOR_LISTING_MAIN");
        }
    }

    // ------------------------------------------------------------------ 3
    @Test(priority = 4, dependsOnMethods = "step2_moderatorTasdiqlashi",
          description = "Admin savdo vaqtlarini qo'yadi (oldingi sessiya tugagach)")
    public void step3_savdoVaqtiniQoyish() {
        String base = Config.adminUrl();
        kim("admin2");
        boolean kechki = LocalTime.now().isAfter(LocalTime.of(14, 0));
        String session = kechki ? "Kechki sessiya" : "Ertalabki sessiya";
        driver.get(base + "/uz/moderationv2/sales-schedule/auction-sales-2/trade-session");
        jadvalKut(driver);
        // Kutib o'tirmaymiz: jadvaldagi barcha savdo qatorlari o'chiriladi va o'zimiznikilar yaratiladi
        for (WebElement r : driver.findElements(By.cssSelector("main tbody tr"))) System.out.println("3) jadvalda turgan savdo: " + cut(clean(r.getText()), 160));
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
        // ijobiy tekshiruv: jadvalda aynan shu seksiya va vaqtli qator paydo bo'lishi kerak
        driver.navigate().refresh();
        Object qator = waitFor(() -> {
            for (WebElement r : driver.findElements(By.cssSelector("main tbody tr"))) {
                String x = norm(clean(r.getText()));
                if (x.contains(norm(mahsulotGuruhi != null ? mahsulotGuruhi : seksiya)) && x.contains(start.format(HM)) && x.contains(end.format(HM))) return Boolean.TRUE;
            }
            return null;
        }, 15);
        Assert.assertNotNull(qator, "Savdo vaqti jadvalda ko'rinmadi: " + seksiya + " " + start.format(HM) + "-" + end.format(HM));
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
    @Test(priority = 5, dependsOnMethods = "step3_savdoVaqtiniQoyish",
          description = "Treyder 1 e'lon vaqtida mahsulotni savdoga chiqaradi")
    public void step4_savdogaChiqarish() {
        String base = Config.traderUrl();
        // e'lon vaqtini kutish bekor ketmasin: xaridor shu orada kirib oladi (savdo sahifasini keyin darhol ochadi)
        try { kim("trader2"); } catch (RuntimeException e) { System.out.println("4) xaridor oldindan kira olmadi (keyin qayta urinadi): " + cut(String.valueOf(e.getMessage()), 120)); }
        kim("trader1");
        // --- SALBIY: minus va 0 lot (imzo to'sib qo'yilgan, hech narsa yuborilmaydi). E'lon boshlanishidan oldin qilinadi, bo'lmasa keyin
        boolean salbiyOldin = java.time.Duration.between(LocalDateTime.now(), elonStart).toSeconds() > 15;
        if (salbiyOldin) {
            try { lotSalbiy(base); } catch (Throwable e) { salbiyOldin = false; System.out.println("4-negativ) e'londan oldin oyna ochilmadi, e'lon vaqtida qilinadi: " + cut(String.valueOf(e.getMessage()), 100)); }
        }
        waitUntil(elonStart.plusSeconds(5), "e'lon berish vaqti");
        long qoyishBoshi = System.currentTimeMillis();
        if (!salbiyOldin) lotSalbiy(base);
        // --- IJOBIY
        WebElement lots = lotOynasi(base);
        lotYoz(lots, LOTS);
        jsClick(driver, lastVisible(driver, By.xpath("//button[normalize-space(.)='Saqlash']")));
        Set<String> yig = new LinkedHashSet<>(); // xabar tez yo'qoladi: yig'ib boramiz
        imzoYoki(() -> { yig.addAll(toasts(driver)); return yig.stream().anyMatch(x -> x.contains("Muvaffaqiyatli") || x.contains("avval taklif berilgan") || x.contains("Majburiy")); }, 12);
        List<String> t = new ArrayList<>(yig);
        Assert.assertTrue(t.stream().anyMatch(x -> x.contains("Muvaffaqiyatli")), "Savdoga chiqarilgani tasdiqlanmadi ('Muvaffaqiyatli' xabari yo'q): " + t);
        System.out.println("4) " + LOTS + " lot savdoga qo'yildi (" + (System.currentTimeMillis() - qoyishBoshi) / 1000.0 + " s). Xabarlar: " + t);
        // sotuvchi o'z mahsulotini tekshirishi xaridor oynalarni tayyorlagandan keyin (5-qadamda, savdo boshlanguncha) qilinadi
    }

    /** Savdoga chiqarish oynasida minus va 0 lot (imzo to'sib qo'yilgan, hech narsa yuborilmaydi). */
    void lotSalbiy(String base) {
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
    }

    /** Sotuvchi o'z mahsulotini "Sotib olish" da ko'rmasligi / taklif oynasini ocholmasligi kerak. */
    void ozMahsulotiTekshir(String base) {
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
                String[] sabab = {""};
                ozPanel = waitFor(() -> { try { return oynaTayyorla(BID_LOTS); } catch (Throwable e) { sabab[0] = String.valueOf(e.getMessage()); return null; } }, 6);
                if (ozPanel == null) {
                    // faqat bolg'acha bosilganda oyna CHIQMAGANI himoya hisoblanadi; boshqa har qanday sabab - tekshirilmadi
                    ozKorinmaydi = true;
                    if (sabab[0].contains("oynasi ochilmadi") || sabab[0].contains("kartochkasi chiqmadi")) System.out.println("4-negativ) sotuvchi o'z mahsulotiga taklif oynasini ocholmaydi (" + cut(sabab[0], 80) + ") -> taklif bera olmaydi ✓");
                    else { System.out.println("4-negativ) o'z mahsulotiga taklif TEKSHIRILMADI: " + cut(sabab[0], 150)); negativXatolar.add("Sotuvchi o'z mahsulotiga taklif: tekshirilmadi (" + cut(sabab[0], 120) + ")"); }
                }
                else System.out.println("4) sotuvchining o'z mahsulotiga taklif oynasi tayyor (sayt ruxsat beryapti - savdoda tekshiriladi)");
            }
        } finally { joriyMijoz = XARIDOR_MIJOZ; }
    }

    /** Treyder 1: Birja mahsulotlari -> mahsulot qatoridagi 🔨 -> "Savdoga qo'yish" oynasi (sahifa qayta ochiladi, imzo to'sig'i tushadi). */
    WebElement lotOynasi(String base) {
        driver.get(base + "/uz/trader/sales/auction-sales?tradeTab=SELL");
        WebElement search = waitFor(() -> visible(driver, By.cssSelector("main input[placeholder='Qidirish']")), 30);
        Set<String> korilgan = new LinkedHashSet<>();
        java.util.function.Supplier<WebElement> topish = () -> {
            for (WebElement r : driver.findElements(By.cssSelector("main tbody tr"))) {
                if (!r.isDisplayed() || !r.getText().contains(PRODUCT)) continue;
                String raqam = ustun(r, "Kontrakt raqami");
                if (!raqam.isEmpty()) korilgan.add(raqam);
                if (savdoRaqami == null || raqam.equals(savdoRaqami)) return r;
            }
            return null;
        };
        // avval kontrakt raqami bo'yicha qidiriladi; sayt raqam bo'yicha topmasa - mahsulot nomi + sahifada 100 qator
        search.sendKeys(savdoRaqami != null ? savdoRaqami : PRODUCT);
        WebElement row = waitFor(topish, 6);
        if (row == null) {
            search = waitFor(() -> visible(driver, By.cssSelector("main input[placeholder='Qidirish']")), 10);
            search.sendKeys(Keys.CONTROL + "a");
            search.sendKeys(PRODUCT);
            sleep(1500);
            sahifada100(driver);
            row = waitFor(topish, 10);
        }
        Assert.assertNotNull(row, "Savdoga chiqarish jadvalida yangi kontrakt " + savdoRaqami + " (ID " + contractId + ") yo'q. Jadvaldagi '" + PRODUCT + "' kontraktlari: " + korilgan);
        System.out.println("4) savdoga chiqariladigan kontrakt: " + ustun(row, "Kontrakt raqami") + " (yangi yaratilgan ID " + contractId + ")");
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
    @Test(priority = 10, dependsOnMethods = "step5_taklifBerish",
          description = "SALBIY holatlar natijasi: minus/0 lot, mavjuddan ko'p (101) lotga taklif")
    public void step9_negativNatijalar() {
        System.out.println("SALBIY holatlar: " + (negativXatolar.isEmpty() ? "hammasi rad etildi ✓" : negativXatolar));
        Assert.assertTrue(negativXatolar.isEmpty(), "Sayt quyidagilarni QABUL QILDI: " + negativXatolar);
    }

    // ------------------------------------------------------------------ 5
    @Test(priority = 6, dependsOnMethods = "step4_savdogaChiqarish",
          description = "Treyder 2 savdo vaqtida mahsulotga taklif beradi")
    public void step5_taklifBerish() {
        String base = Config.traderUrl();
        kim("trader2");
        // --- IJOBIY: bitta sahifada BIDS ta taklif OYNASI oldindan ochiladi (🔨 har bosilganda yangi oyna),
        // har birida mijoz va miqdor kiritiladi; savdo boshlanishi bilan oynalar ketma-ket yuboriladi, PAUZA oralig'i bilan.
        savdoSahifasi(base);
        // --- SALBIY: savdo boshlanmasdan taklif (oynalar tayyorlanishidan OLDIN: savdo ochilib qolmasin)
        if (qoldi(savdoStart) >= 10) boshlanmaganTaklif();
        else System.out.println("5n) savdo boshlanmasdan taklif: savdo boshlanishiga " + qoldi(savdoStart) + " s qoldi, TEKSHIRILMADI");
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
        // savdo tugagach bosiladigan oyna ham oldindan ochib qo'yiladi
        kechPanel = yangiOyna();
        // sotuvchi o'z mahsulotiga taklif bera olmasligi (vaqt yetmasa savdo oxirida tekshiriladi)
        if (qoldi(savdoStart) >= 15) { kim("trader1"); try { ozMahsulotiTekshir(base); } finally { kim("trader2"); } }
        // --- SALBIY (savdo boshlanguncha qolgan vaqtda): oynaning o'zi tekshiradigan holatlar
        savdoOldiSalbiy();
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
            if (bor >= BIDS) break;
            System.out.println("5) DIQQAT: saytda " + bor + " ta taklif, " + BIDS + " ta bo'lishi kerak - yetishmagani qayta beriladi");
            for (int i = Math.max(bor, 0); bor >= 0 && i < BIDS; i++) { WebElement p = yangiOyna(); if (p != null) yuborish(p, i + 1, BID_LOTS); }
            sleep(1500);
        }
        Assert.assertEquals(taklifSoni(), BIDS, "Takliflar 'Mening takliflarim' ro'yxatida to'liq ko'rinmadi");
        // --- bitta taklif o'chiriladi (Mening takliflarim -> 🗑)
        if (OCHIRISH > 0 && vaqtYetadimi("taklifni o'chirish")) taklifOchir();
        // --- 101 lot (qisman yutadi), keyin yana 1 lot (yutqazadi), so'ng savatcha ranglari savdo ICHIDA o'qiladi
        qismanTaklif(kopPanel, kop);
        if (vaqtYetadimi("yutqazadigan taklif")) yutqazadiganTaklif(yutqazPanel);
        if (vaqtYetadimi("savatcha ranglari")) savatchaTekshir(KUTILGAN, 1, 1);
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
        // server tekshiradigan holatlar (savdo ochiq bo'lishi kerak) birinchi; savdodan oldin tekshirilganlar qayta qilinmaydi
        List<String[]> tartib = new ArrayList<>();
        for (String[] h : holatlar) if (h[0].startsWith("juda")) tartib.add(0, h); else tartib.add(h);
        for (String[] h : tartib) {
            if (oldinTekshirildi.contains(h[0]) || !vaqtYetadimi(h[0])) continue;
            salbiyHolat(h[0], h[1], h[2], null);
        }
        if (vaqtYetadimi("past narx")) pastNarx();
        if (!oldinTekshirildi.contains("mijoz tanlanmagan") && vaqtYetadimi("mijozsiz taklif")) { mijozsiz = true; try { salbiyHolat("mijoz tanlanmagan", "1", NARX, yangiOyna()); } finally { mijozsiz = false; } }
        // treyder o'z nomidan (mijozsiz emas, ro'yxatdagi birinchi: o'z kompaniyasi): natija qayd qilinadi, qabul qilinsa o'chiriladi
        if (vaqtYetadimi("o'z nomidan taklif") && !XARIDOR_MIJOZ.isEmpty()) {
            joriyMijoz = "";
            try { salbiyHolat("treyder O'Z NOMIDAN taklif (ma'lumot uchun)", BID_LOTS, NARX, yangiOyna()); } finally { joriyMijoz = XARIDOR_MIJOZ; }
        }
        if (kechPanel == null && vaqtYetadimi("savdodan keyingi taklif oynasi")) kechPanel = yangiOyna();
    }

    /** Savdodan oldin to'liq tekshirilgan salbiy holatlar (savdo ichida qayta qilinmaydi). */
    Set<String> oldinTekshirildi = new LinkedHashSet<>();
    /** true: savdo hali boshlanmagan - yuborilgan holat faqat oynaning o'z xabari bilan rad etilsa hisoblanadi. */
    boolean savdoOldi = false;

    /** t gacha qolgan soniya. */
    static long qoldi(LocalDateTime t) { return java.time.Duration.between(LocalDateTime.now(), t).toSeconds(); }

    /** Savdo boshlanmasdan taklif: qabul qilinmasligi va "savdo vaqti emas / boshlanmagan" xabari chiqishi kerak. */
    void boshlanmaganTaklif() {
        try {
            WebElement p = yangiOyna();
            if (p == null) { negativXatolar.add("Savdo boshlanmasdan taklif: oyna ochilmadi, tekshirilmadi"); return; }
            int oldin = taklifSoni();
            jsClick(driver, p.findElement(By.xpath(".//button[normalize-space(.)='Sotib olish']")));
            Set<String> x = new LinkedHashSet<>();
            for (long end = System.currentTimeMillis() + 3000; System.currentTimeMillis() < end; sleep(200)) {
                toasts(driver).stream().filter(y -> !y.startsWith("Loading")).forEach(x::add);
                if (!Eimzo.windowTitle().isEmpty()) { Eimzo.closeIfShown(1); x.add("E-IMZO so'radi (parolsiz yopildi)"); }
                if (!x.isEmpty() && x.stream().anyMatch(BOSHLANMAGAN::test)) break;
            }
            int keyin = taklifSoni();
            boolean qabul = oldin >= 0 && keyin > oldin;
            boolean xabar = x.stream().anyMatch(BOSHLANMAGAN::test);
            System.out.println("5n) savdo boshlanmasdan taklif -> " + (qabul ? "QABUL QILINDI" : "qabul qilinmadi") + ", takliflar " + oldin + " -> " + keyin + ", xabarlar: " + x
                    + (!qabul && xabar ? " ✓" : ""));
            rasmOl("savdo_boshlanmagan");
            if (qabul) {
                negativXatolar.add("Savdo boshlanmasdan berilgan taklif QABUL QILINDI");
                System.out.println("5n)    qabul qilingan taklif o'chirildi: " + (engYangisiniOchir(meningTakliflarim()) ? "ha" : "YO'Q"));
            } else if (!xabar) negativXatolar.add("Savdo boshlanmasdan taklif: 'savdo vaqti boshlanmagan' xabari chiqmadi (xabarlar: " + x + ")");
        } catch (Throwable e) {
            rasmOl("savdo_boshlanmagan_xato");
            negativXatolar.add("Savdo boshlanmasdan taklif: tekshirib bo'lmadi: " + cut(String.valueOf(e.getMessage()), 120));
        }
    }

    static final java.util.function.Predicate<String> BOSHLANMAGAN = y -> { String t = norm(y).toLowerCase(); return t.contains("vaqti emas") || t.contains("boshlanmagan") || t.contains("vaqt emas"); };

    /** Savdo boshlanguncha qolgan vaqtda oynaning o'zi tekshiradigan salbiy holatlar (savdoga 6 s qolganda to'xtaydi). */
    void savdoOldiSalbiy() {
        String[][] holatlar = {
                {"minus lot", "-5", NARX}, {"minus pul", "1", "-" + NARX},
                {"kasr lot", "1.5", NARX}, {"harf lot", "abc", NARX}, {"harf pul", "1", "abc"},
                {"0 lot", "0", NARX}, {"0 pul", "1", "0"},
                {"bo'sh lot", "", NARX}, {"bo'sh pul", "1", ""},
        };
        savdoOldi = true;
        try {
            for (String[] h : holatlar) {
                if (java.time.Duration.between(LocalDateTime.now(), savdoStart).toSeconds() < 6) break;
                if (salbiyHolat(h[0], h[1], h[2], null)) oldinTekshirildi.add(h[0]);
            }
            if (java.time.Duration.between(LocalDateTime.now(), savdoStart).toSeconds() >= 6) {
                mijozsiz = true;
                try { if (salbiyHolat("mijoz tanlanmagan", "1", NARX, yangiOyna())) oldinTekshirildi.add("mijoz tanlanmagan"); } finally { mijozsiz = false; }
            }
        } finally { savdoOldi = false; salbiyPanel = null; }
        System.out.println("5n) savdodan oldin tekshirildi: " + oldinTekshirildi.size() + " ta " + oldinTekshirildi);
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

    /** @return hukm ishonchli bo'lsa true (savdodan oldin: faqat oynaning o'z xabari bilan rad etilgan yoki maydon qabul qilmagan bo'lsa). */
    boolean salbiyHolat(String nom, String lot, String pul, WebElement oyna) {
        boolean malumot = nom.contains("ma'lumot uchun");
        String bosh = savdoOldi ? "5n) [savdodan oldin] " : "5n) ";
        try {
            WebElement p = oyna;
            if (p == null) {
                boolean bor;
                try { bor = salbiyPanel != null && salbiyPanel.isDisplayed() && mijozTanlangan(salbiyPanel); } catch (Exception e) { bor = false; }
                if (!bor) salbiyPanel = yangiOyna();
                p = salbiyPanel;
            }
            if (p == null) { System.out.println(bosh + nom + ": oyna yo'q, TEKSHIRILMADI"); if (!savdoOldi) negativXatolar.add(nom + ": tekshirib bo'lmadi (oyna ochilmadi)"); return false; }
            String lv = maydonYoz(p.findElement(By.cssSelector("input[placeholder='Miqdor']")), lot);
            List<WebElement> narxlar = p.findElements(By.cssSelector("input[placeholder='Narx'], input[name='price']"));
            if (narxlar.isEmpty()) narxlar = p.findElements(By.xpath(".//*[normalize-space(text())='Narx']/following::input[1]"));
            String pv = narxlar.isEmpty() ? "?" : maydonYoz(narxlar.get(0), pul);
            String lt = lv.replaceAll("\\s", ""), pt = pv.replaceAll("\\s", "");
            if (!lt.equals(lot) || (!pv.equals("?") && !pt.equals(pul))) {
                System.out.println(bosh + nom + ": yozildi [" + lot + " x " + pul + "], maydonda [" + lv + " x " + pv + "] -> maydon qabul qilmadi ✓");
                return true;
            }
            if (pv.equals("?") && !pul.equals(NARX)) { System.out.println(bosh + nom + ": narx maydoni topilmadi, TEKSHIRILMADI"); if (!savdoOldi) negativXatolar.add(nom + ": narx maydoni topilmadi"); return false; }
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
            if (oldin < 0 || keyin < 0) { System.out.println(bosh + nom + ": takliflar soni o'qilmadi, TEKSHIRILMADI"); if (!savdoOldi) negativXatolar.add(nom + ": takliflar soni o'qilmadi"); return false; }
            boolean qabul = keyin > oldin;
            // savdodan oldin server baribir "vaqti emas" deydi: hukm faqat oynaning o'z xabari (Min / musbat / Majburiy) bo'lsa ishonchli
            boolean ozXabari = x.stream().anyMatch(y -> y.contains("Min") || y.contains("musbat") || y.contains("Majburiy")) && x.stream().noneMatch(BOSHLANMAGAN);
            if (savdoOldi && !qabul && !ozXabari) {
                System.out.println(bosh + nom + " [" + lv + " x " + pv + "] -> oyna o'zi to'xtatmadi (xabarlar: " + x + "), savdo ichida qayta tekshiriladi");
                salbiyPanel = null;
                return false;
            }
            System.out.println(bosh + nom + " [" + lv + " x " + pv + "] -> " + (qabul ? "QABUL QILINDI" : "rad etildi ✓") + ", takliflar " + oldin + " -> " + keyin + ", xabarlar: " + x
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
            return true;
        } catch (Throwable e) {
            rasmOl("salbiy_xato");
            System.out.println(bosh + nom + ": TEKSHIRILMADI: " + cut(String.valueOf(e.getMessage()), 150));
            if (!savdoOldi) negativXatolar.add(nom + ": tekshirib bo'lmadi");
            return false;
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
    boolean engYangisiniOchir(WebElement p) { return taklifQatoriniOchir(p, 0); }

    /** "Mening takliflarim" dagi idx-qatordagi taklifni 🗑 bilan o'chiradi. Qator yo'qolsa true. */
    boolean taklifQatoriniOchir(WebElement p, int idx) {
        if (p == null) return false;
        int oldin = rangliQatorlar(p).size();
        // 🗑 ustuni o'ng chetda: oyna tor bo'lsa, jadval surilmaguncha sahifada bo'lmaydi
        js().executeScript("for(const e of [arguments[0],...arguments[0].querySelectorAll('*')]) if(e.scrollWidth>e.clientWidth+5) e.scrollLeft=e.scrollWidth;", p);
        waitFor(() -> p.findElements(By.cssSelector("[role=row] svg, tbody tr svg")).isEmpty() ? null : Boolean.TRUE, 3);
        Object bosildi = js().executeScript(
                "const rows=[...arguments[0].querySelectorAll('[role=row],tbody tr')].filter(r=>!r.querySelector('[role=columnheader],th')); if(rows.length<=arguments[1]) return null;" +
                "const sv=[...rows[arguments[1]].querySelectorAll('svg')]; if(!sv.length) return null; const s=sv[sv.length-1]; const b=s.closest('button,[role=button],a')||s;" +
                "['mousedown','mouseup','click'].forEach(t=>b.dispatchEvent(new MouseEvent(t,{bubbles:true}))); return 'ok';", p, idx);
        if (bosildi == null) { rasmOl("ochirish_tugmasi_yoq"); return false; }
        WebElement ok = waitFor(() -> lastVisible(driver, By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button[contains(normalize-space(.),'chirish') or normalize-space(.)='Ha' or normalize-space(.)='Tasdiqlash']")), 2);
        if (ok != null) jsClick(driver, ok);
        imzoYoki(() -> rangliQatorlar(p).size() < oldin, 6);
        return rangliQatorlar(p).size() < oldin;
    }

    /** Qatorda shu lot soni bilan 1 000 000 narx bormi ("... 101 1 000 000 ..."). */
    static boolean lotliQator(String qator, int lot) { return qator.replace(' ', ' ').matches(".*\\b" + lot + "\\s+1 000 000\\b.*"); }

    boolean qismanOchirildi = false, yutqazadiganBerildi = false;

    /** 101 lotli (qisman yutgan) taklif 🗑 bilan o'chiriladi va ro'yxatdan yo'qolgani tekshiriladi. */
    void qismanTaklifniOchir() {
        int kop = Integer.parseInt(LOTS) + 1;
        try {
            WebElement p = meningTakliflarim();
            if (p == null) { savatchaXatolar.add("'Mening takliflarim' ochilmadi (" + kop + " lotli taklif o'chirilmadi)"); return; }
            List<String> oldin = rangliQatorlar(p);
            int idx = -1;
            for (int i = 0; i < oldin.size(); i++) if (lotliQator(oldin.get(i), kop)) { idx = i; break; }
            if (idx < 0) { savatchaXatolar.add(kop + " lotli taklif 'Mening takliflarim' da topilmadi: " + oldin); return; }
            boolean ochdi = taklifQatoriniOchir(p, idx);
            List<String> keyin = rangliQatorlar(p);
            boolean yoq = keyin.stream().noneMatch(q -> lotliQator(q, kop));
            System.out.println("5b) " + kop + " lotli taklif o'chirildi: " + oldin.size() + " -> " + keyin.size() + ", xabarlar: " + toasts(driver) + (ochdi && yoq ? " ✓" : ""));
            rasmOl("taklif_101_ochirildi");
            if (!ochdi || !yoq) { savatchaXatolar.add(kop + " lotli taklif o'chmadi: " + oldin.size() + " -> " + keyin.size()); return; }
            qismanOchirildi = true;
            // bo'shagan lotni 101 dan keyingi 1 lotli taklif oladi: savatchada hammasi yashil bo'lishi kerak
            if (vaqtYetadimi("o'chirilgandan keyingi savatcha")) savatchaTekshir(KUTILGAN + (yutqazadiganBerildi ? 1 : 0), 0, 0);
        } catch (Exception e) {
            rasmOl("ochirish_101_xato");
            savatchaXatolar.add(kop + " lotli taklifni o'chirib bo'lmadi: " + e.getMessage());
        }
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
            yutqazadiganBerildi = true;
        } catch (Throwable e) {
            savatchaXatolar.add("Yutqazadigan taklif berilmadi: " + e.getMessage());
        }
    }

    /** Kartochkadagi savatcha (bolg'acha yonidagi belgi) -> Narx / Miqdor oynasi, ranglar o'qiladi va kutilgani bilan solishtiriladi. */
    void savatchaTekshir(int kYashil, int kSariq, int kQizil) {
        try {
            WebElement mt = meningTakliflarim();
            List<String> taklif = mt == null ? new ArrayList<>() : rangliQatorlar(mt);
            System.out.println("5b) Mening takliflarim ranglari: " + sanaRang(taklif));
            taklif.forEach(x -> System.out.println("     " + x));
            By kitobBy = By.xpath("//*[@role='grid' or self::table][.//*[normalize-space(text())='Narx'] and .//*[normalize-space(text())='Miqdor']]");
            if (lastVisible(driver, kitobBy) == null) { // ochiq bo'lmasa savatcha belgisi bosiladi
                WebElement belgi = kartochka().findElements(By.cssSelector("svg")).get(3);
                js().executeScript("const b=arguments[0].closest('button,[role=button],a')||arguments[0]; b.dispatchEvent(new MouseEvent('click',{bubbles:true}));", belgi);
            }
            WebElement kitob = waitFor(() -> lastVisible(driver, kitobBy), 6);
            sleep(1000);
            rasmOl("savatcha");
            if (kitob == null) { savatchaXatolar.add("Savatcha oynasi ochilmadi"); return; }
            // o'chirishdan keyin savatcha yangilanishi uchun kutamiz (kutilgan ranglar chiqquncha, ko'pi bilan 5 s)
            waitFor(() -> { Map<String, Integer> r = sanaRang(rangliQatorlar(kitob)); return r.getOrDefault("yashil", 0) == kYashil && r.getOrDefault("sariq", 0) == kSariq && r.getOrDefault("qizil", 0) == kQizil ? Boolean.TRUE : null; }, 5);
            List<String> q = rangliQatorlar(kitob);
            Map<String, Integer> rang = sanaRang(q);
            System.out.println("5b) SAVATCHA ranglari: " + rang + " (kutilgan: yashil " + kYashil + ", sariq " + kSariq + ", qizil " + kQizil + ")");
            q.forEach(x -> System.out.println("     " + x));
            savatchaOqildi = true;
            int y = rang.getOrDefault("yashil", 0), s = rang.getOrDefault("sariq", 0), k = rang.getOrDefault("qizil", 0);
            if (y != kYashil) savatchaXatolar.add("Savatcha: yashil (yutgan) " + y + " ta, kutilgan " + kYashil);
            if (s != kSariq) savatchaXatolar.add("Savatcha: sariq (qisman) " + s + " ta, kutilgan " + kSariq);
            if (k != kQizil) savatchaXatolar.add("Savatcha: qizil (yutqizgan) " + k + " ta, kutilgan " + kQizil);
        } catch (Exception e) {
            rasmOl("savatcha_xato");
            savatchaXatolar.add("Savatchani o'qib bo'lmadi: " + e.getMessage());
        }
    }

    @Test(priority = 10, dependsOnMethods = "step6_bitimHosilBolishi",
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

    /** Taklif uchun qaysi mijoz tanlanadi (sotuvchi o'z mahsulotiga urinishida - birinchisi). */
    String joriyMijoz = XARIDOR_MIJOZ;

    boolean vaqtYetadimi(String nima) {
        if (LocalDateTime.now().isBefore(savdoEnd.minusSeconds(5))) return true;
        System.out.println("5-negativ) " + nima + ": savdo vaqti tugadi, tekshirilmadi");
        return false;
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

    /** Savdodagi mahsulot kartochkasi (belgilar: 0 hujjat, 1 yulduz, 2 bolg'acha, 3 savatcha). */
    WebElement kartochka() {
        // bir xil nomli bir nechta mahsulot bo'lishi mumkin: aynan shu kontrakt raqami ("136 / ...") bo'yicha
        for (WebElement nom : driver.findElements(By.xpath("//*[contains(text(),'" + PRODUCT + "')]"))) {
            try {
                if (!nom.isDisplayed()) continue;
                WebElement card = nom;
                for (int up = 0; up < 15; up++) {
                    card = card.findElement(By.xpath("./.."));
                    if (card.getText().contains(" lot") && card.findElements(By.cssSelector("svg")).size() >= 3) break;
                }
                if (savdoRaqami == null || java.util.regex.Pattern.compile("(^|[^0-9])" + savdoRaqami + "\\s*/").matcher(card.getText().split(PRODUCT, 2)[0]).find()) return card;
            } catch (StaleElementReferenceException | org.openqa.selenium.NoSuchElementException ignore) {}
        }
        return null;
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
    @Test(priority = 7, dependsOnMethods = "step5_taklifBerish",
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
        Set<String> begona = new LinkedHashSet<>(); // shu savdoda, lekin boshqa kontrakt raqamida tuzilgan bitimlar
        // bitimlar soni 2 marta ketma-ket o'zgarmasa (yoki kutilgan songa yetsa) kutish to'xtaydi
        int oldingiSoni = -1, barqaror = 0;
        for (int attempt = 0; attempt < 8 && dealNos.size() < jamiKutilgan && barqaror < 2; attempt++) {
            openContracts(base, "Sotish");
            for (WebElement r : driver.findElements(By.cssSelector("main tbody tr"))) {
                String tx = r.getText();
                if (tx.contains(today) && tx.contains(PRODUCT) && tx.contains("Imzolash kutilmoqda") && shuSavdodanmi(tx)) {
                    if (savdoRaqami != null && !clean(tx).contains("| " + savdoRaqami + " | ")) { begona.add(cut(tx, 120)); continue; }
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
            System.out.println("6) " + (attempt + 1) + "-o'qish: " + dealNos.size() + " ta bitim (kutilgan " + jamiKutilgan + ")");
            if (!dealNos.isEmpty() && dealNos.size() == oldingiSoni) barqaror++; else barqaror = 0;
            oldingiSoni = dealNos.size();
            if (dealNos.size() < jamiKutilgan && barqaror < 2) sleep(3000);
        }
        if (!begona.isEmpty()) System.out.println("6) DIQQAT: boshqa kontrakt raqamidagi bitimlar olinmadi: " + begona);
        Assert.assertFalse(dealNos.isEmpty(), savdoRaqami + "-kontrakt bo'yicha bugungi '" + PRODUCT + "' bitimi topilmadi (savdo " + time + " da tugagan)");
        dealNo = dealNos.get(0);
        // stsenariylarga faqat BID_LOTS (1) lotli bitimlar beriladi; 101 lotli (qisman) taklifdan chiqqan katta bitim berilmaydi
        OXIRGI_BITIMLAR.clear();
        for (String no : dealNos) if (String.valueOf(bitimLotlari.getOrDefault(no, 1)).equals(BID_LOTS)) OXIRGI_BITIMLAR.add(no);
        System.out.println("6) Lotlar: " + bitimLotlari + " | stsenariylar uchun: " + OXIRGI_BITIMLAR);
        System.out.println("6) Bitimlar hosil bo'ldi (" + dealNos.size() + " ta, kutilgan " + jamiKutilgan + "): " + dealNos);
        if (dealNos.size() != jamiKutilgan) negativXatolar.add("Bitimlar " + dealNos.size() + " ta, kutilgan " + jamiKutilgan + " (past narx, yutqazgan yoki o'chirilgan taklifdan bitim chiqmasligi kerak)");
        // 101 lotli taklif o'chirilgan bo'lsa: katta bitim bo'lmasligi, bo'shagan lotni 101 dan keyingi 1 lotli taklif olishi kerak
        int birLotli = KUTILGAN + (qismanOchirildi && yutqazadiganBerildi ? 1 : 0);
        if (OXIRGI_BITIMLAR.size() != birLotli) savatchaXatolar.add("1 lotli bitimlar " + OXIRGI_BITIMLAR.size() + " ta, kutilgan " + birLotli + " (o'chirilgan yoki yutqazgan taklif ham bitim bo'lgan bo'lishi mumkin)");
        int lotlar = Integer.parseInt(LOTS), qolgan = lotlar - KUTILGAN * Integer.parseInt(BID_LOTS);
        String katta = null;
        for (Map.Entry<String, Integer> e : bitimLotlari.entrySet()) if (e.getValue() > Integer.parseInt(BID_LOTS)) katta = e.getKey();
        if (qismanOchirildi) {
            if (katta != null) negativXatolar.add((lotlar + 1) + " lotli taklif o'chirilgan, lekin undan bitim chiqdi: " + katta + " = " + bitimLotlari.get(katta) + " lot");
            else System.out.println("6) o'chirilgan " + (lotlar + 1) + " lotli taklifdan bitim chiqmadi ✓; bo'shagan lotni keyingi 1 lotli taklif oldi: 1 lotli bitimlar " + OXIRGI_BITIMLAR.size() + " ta (kutilgan " + birLotli + ")");
        } else if (katta != null) {
            // o'chirilmagan bo'lsa (vaqt yetmadi): qolgan lotlarga bitim tuzilgan bo'lishi kerak -> qisman yutgan
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
    @Test(priority = 8, dependsOnMethods = "step6_bitimHosilBolishi",
          description = "Ikkala treyder bitimni 'Ommaviy imzolash' orqali imzolaydi")
    public void step7_treyderlarImzolashi() {
        String base = Config.traderUrl();
        signDeal(base, "trader1", "Sotish");
        signDeal(base, "trader2", "Sotib olish"); // mijoz nomidan bo'lsa "Mijozimning shartnomalari"
    }

    // ------------------------------------------------------------------ 8
    @Test(priority = 9, dependsOnMethods = "step7_treyderlarImzolashi",
          description = "Admin bitimni 'Tasdiqlanmagan' tabidan tasdiqlaydi")
    public void step8_adminTasdiqlashi() {
        String base = Config.adminUrl();
        kim("admin2");
        adminOmmaviyTasdiqla(base, dealNos);
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
