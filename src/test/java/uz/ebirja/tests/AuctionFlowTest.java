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
public abstract class AuctionFlowTest extends BaseTest {

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
    // takliflar orasidagi pauza (ms)
    static final int PAUZA_MS = Integer.parseInt(Config.getOr("auction.bidPauseMs", "2000"));
    // xaridor qaysi mijoz nomidan taklif beradi (STIR yoki nom qismi); bo'sh bo'lsa ro'yxatdagi birinchisi
    static final String XARIDOR_MIJOZ = Config.getOr("auction.buyerClient", "");
    static final int MIN = Integer.parseInt(Config.getOr("auction.minutes", "1"));          // e'lon davomiyligi
    static final int SAVDO_MIN = Integer.parseInt(Config.getOr("auction.savdoMinutes", "2")); // savdo davomiyligi
    static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    // qadamlar orasida uzatiladigan ma'lumotlar
    String contractId;          // moderator ro'yxatidagi ID
    LocalDateTime elonStart, savdoStart, savdoEnd;
    String dealNo;              // birinchi bitim raqami (B2600....)
    List<String> dealNos = new ArrayList<>(); // shu savdodagi barcha bitimlar
    List<String> negativXatolar = new ArrayList<>(); // salbiy holatlarda sayt qabul qilib yuborganlari
    Map<String, Integer> bitimLotlari = new LinkedHashMap<>();
    /** Shu run'da auksionda hosil bo'lgan bitimlar (keyingi "Bitim holatlari" testi shulardan foydalanadi). */
    public static final List<String> OXIRGI_BITIMLAR = new ArrayList<>();

    JavascriptExecutor js() { return (JavascriptExecutor) driver; }

    void rasmOl(String nom) {
        try {
            File dir = new File("target/auksion-rasmlar");
            dir.mkdirs();
            File s = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            java.nio.file.Files.copy(s.toPath(), new File(dir, nom + "_" + System.currentTimeMillis() + ".png").toPath());
        } catch (Exception ignore) {}
    }

    @AfterMethod(alwaysRun = true)
    public void yopish(org.testng.ITestResult result) {
        if (result.getStatus() == org.testng.ITestResult.FAILURE && driver != null) {
            try {
                File dir = new File("target/xato-rasmlar");
                dir.mkdirs();
                File shot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                File dst = new File(dir, result.getMethod().getMethodName() + "_" + System.currentTimeMillis() + ".png");
                java.nio.file.Files.copy(shot.toPath(), dst.toPath());
                System.out.println("XATO RASMI: " + dst.getAbsolutePath());
            } catch (Exception ignore) {}
        }
        closeBrowser();
    }

    // ------------------------------------------------------------------ 1
    @Test(priority = 1, description = "Treyder 1 auksion kontraktini yaratadi va E-IMZO bilan yuboradi")
    public void step1_kontraktYaratish() {
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
        Eimzo.typePassword(3);
        waitFor(() -> driver.findElements(By.cssSelector("main tbody tr")).size() < oldin ? Boolean.TRUE : null, 8);
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
        Eimzo.typePassword(5);
        waitFor(() -> lastVisible(driver, By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button[normalize-space(.)='Saqlash']")) == null ? Boolean.TRUE : null, 10);
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
        sleep(4000);
        System.out.println("4-negativ) 0 lot -> xabarlar: " + toasts(driver) + ", imzo so'rovlari: " + eimzoUrinishlari(driver));
        if (eimzoUrinishlari(driver) > 0) negativXatolar.add("Savdoga chiqarish: 0 lot bilan forma o'tdi va imzo so'radi (imzo to'sildi)");
        // --- IJOBIY
        lots = lotOynasi(base);
        lotYoz(lots, LOTS);
        jsClick(driver, lastVisible(driver, By.xpath("//button[normalize-space(.)='Saqlash']")));
        Eimzo.typePassword(6);
        sleep(3000);
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
        waitUntil(savdoStart.plusSeconds(1), "savdo vaqti");
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
        // --- SALBIY: savdoning qolgan vaqtida (ijobiy takliflardan keyin)
        ozMahsulotigaTaklif(base);
        kim("trader2");
        qismanTaklif(kopPanel, kop);
        // SALBIY: 0 lot (maydon tekshiruvi, imzo so'ralmaydi; E-IMZO oynasi chiqsa parolsiz yopiladi)
        if (vaqtYetadimi("0 lotga taklif")) nolLotTekshir();
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
            boolean imzo = Eimzo.typePassword(15); // oyna ba'zan kech chiqadi
            sleep(800);
            System.out.println("5) " + nima + ": imzo " + (imzo ? "qo'yildi" : "so'ralmadi") + ", xabarlar: " + toasts(driver));
            // yakuniy hukm 6-qadamda bitimdan chiqariladi (93 lotli bitim tuzilsa -> qabul qilingan)
            if (!imzo) negativXatolar.add("Taklif: " + nima + " - sayt taklifni qabul qilmadi (imzo so'ralmadi)");
            qismanTaklifBerildi = imzo;
        } catch (Exception e) {
            System.out.println("5) " + nima + ": berib bo'lmadi: " + e.getMessage());
        }
    }

    boolean qismanTaklifBerildi = false;

    /** Savatcha: takliflar rangi (yashil - yutgan, qizil - yutqizgan, sariq - qisman). Natijani logga yozadi va sanaydi. */
    Map<String, Integer> savatcha(String base) {
        Map<String, Integer> r = new LinkedHashMap<>();
        try {
            kim("trader2");
            savdoSahifasi(base);
            // o'ng chetdagi suzuvchi tugmalar: oxirgisi - savatcha
            List<?> btns = (List<?>) js().executeScript(
                    "return [...document.querySelectorAll('button, [role=button], div')].filter(e => { const r = e.getBoundingClientRect();" +
                    " return r.width > 20 && r.width < 70 && r.height > 20 && r.height < 70 && r.right > innerWidth - 90 && r.top > 150 && e.querySelector('svg'); });");
            System.out.println("6-savatcha) o'ng chetdagi tugmalar: " + btns.size());
            if (btns.isEmpty()) return r;
            jsClick(driver, (WebElement) btns.get(btns.size() - 1));
            sleep(2500);
            rasmOl("savatcha");
            // "Mening takliflarim" jadvali: har bir qatorning rangi (fon) bo'yicha: yashil - yutgan, qizil - yutqizgan, sariq - qisman
            List<?> rang = (List<?>) js().executeScript(
                    "const out=[]; for (const tr of document.querySelectorAll('tbody tr')) {" +
                    "  let el=tr, c=''; for (let i=0;i<3 && el;i++){ const s=getComputedStyle(el); c+=s.backgroundColor+' '+s.color+' '+el.className+' '; el=el.firstElementChild; }" +
                    "  const m=c.match(/rgba?[(]([0-9]+), ([0-9]+), ([0-9]+)/); let t='nomalum';" +
                    "  if (m) { const r=+m[1], g=+m[2], b=+m[3];" +
                    "    if (g>r+20 && g>b) t='yashil'; else if (r>200 && g>170 && b<160) t='sariq'; else if (r>g+40 && r>b+40) t='qizil'; }" +
                    "  if (/green|success/i.test(c)) t='yashil'; if (/yellow|warning|amber/i.test(c)) t='sariq'; if (/red|danger|error/i.test(c)) t='qizil';" +
                    "  out.push(t+' | '+tr.innerText.split(String.fromCharCode(10)).join(' ').slice(0,140)); } return out;");
            for (Object o : rang) {
                String s = String.valueOf(o);
                r.merge(s.substring(0, s.indexOf(" | ")), 1, Integer::sum);
            }
            System.out.println("6-savatcha) ranglar: " + r);
            rang.stream().limit(15).forEach(o -> System.out.println("   " + o));
        } catch (Exception e) {
            System.out.println("6-savatcha) o'qib bo'lmadi: " + e.getMessage());
        }
        return r;
    }

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

    /** Kartochkadan taklif oynasini ochadi, mijozni tanlaydi, miqdorni yozadi (YUBORMAYDI). Panelni qaytaradi. */
    WebElement oynaTayyorla(String lotlar) {
        List<WebElement> oldingi = ochiqOynalar();
        // Kartochkadagi 🔨 (Ariza yuborish, F4) — sahifa qayta chizilishi mumkin, shuning uchun qayta urinamiz
        Object clicked = waitFor(() -> {
            WebElement name = visible(driver, By.xpath("//*[contains(text(),'" + PRODUCT + "')]"));
            if (name == null) return null;
            WebElement card = name;
            for (int up = 0; up < 15; up++) {
                card = card.findElement(By.xpath("./.."));
                if (card.getText().contains(" lot") && card.findElements(By.cssSelector("svg")).size() >= 3) break;
            }
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
            if (!String.valueOf(txt).contains("Mijoz")) continue;
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
        Eimzo.typePassword(6);
        sleep(500);
        List<String> t = toasts(driver);
        Assert.assertFalse(t.stream().anyMatch(x -> x.contains("Majburiy")), "Taklif yuborilmadi: " + t);
        System.out.println("5) " + b + "-taklif: " + lotlar + " lotga taklif berildi. Xabarlar: " + t);
    }

    // ------------------------------------------------------------------ 6
    @Test(priority = 6, dependsOnMethods = "step5_taklifBerish",
          description = "Savdo tugagach bitim hosil bo'ladi (Treyder 1 -> Mening shartnomalarim -> Sotish)")
    public void step6_bitimHosilBolishi() {
        waitUntil(savdoEnd.plusSeconds(10), "savdo tugashi va bitim yaratilishi");
        Map<String, Integer> rang = savatcha(Config.traderUrl());
        if (qismanTaklifBerildi && !rang.isEmpty() && !rang.containsKey("sariq"))
            System.out.println("6-savatcha) DIQQAT: 101 lotli taklif sariq (qisman) ko'rinmadi: " + rang);
        String base = Config.traderUrl();
        kim("trader1");
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String time = savdoEnd.format(HM);
        // bitimlar soni 2 marta ketma-ket o'zgarmasa (yoki BIDS ga yetsa) kutish to'xtaydi
        int oldingiSoni = -1, barqaror = 0;
        for (int attempt = 0; attempt < 8 && dealNos.size() < BIDS && barqaror < 2; attempt++) {
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
            if (dealNos.size() < BIDS && barqaror < 2) sleep(3000);
        }
        Assert.assertFalse(dealNos.isEmpty(), "Bugungi '" + PRODUCT + "' bitimi topilmadi (savdo " + time + " da tugagan)");
        dealNo = dealNos.get(0);
        // stsenariylarga faqat BID_LOTS (1) lotli bitimlar beriladi; 101 lotli (qisman) taklifdan chiqqan katta bitim berilmaydi
        OXIRGI_BITIMLAR.clear();
        for (String no : dealNos) if (String.valueOf(bitimLotlari.getOrDefault(no, 1)).equals(BID_LOTS)) OXIRGI_BITIMLAR.add(no);
        System.out.println("6) Lotlar: " + bitimLotlari + " | stsenariylar uchun: " + OXIRGI_BITIMLAR);
        System.out.println("6) Bitimlar hosil bo'ldi (" + dealNos.size() + " ta, kutilgan " + BIDS + "): " + dealNos);
        // 101 lotli taklif natijasi bitimdan aniqlanadi: qolgan lotlarga (100 - 7) bitim tuzilgan bo'lsa -> qisman yutgan ✓
        int lotlar = Integer.parseInt(LOTS), qolgan = lotlar - BIDS * Integer.parseInt(BID_LOTS);
        String katta = null;
        for (Map.Entry<String, Integer> e : bitimLotlari.entrySet()) if (e.getValue() > Integer.parseInt(BID_LOTS)) katta = e.getKey();
        String xato = "Taklif: " + (lotlar + 1) + " lotga taklif (qisman yutishi kerak) - sayt taklifni qabul qilmadi (imzo so'ralmadi)";
        if (katta != null) {
            negativXatolar.remove(xato);
            int lot = bitimLotlari.get(katta);
            System.out.println("6) " + (lotlar + 1) + " lotli taklif QABUL QILINDI va qisman yutdi: " + katta + " = " + lot + " lot (kutilgan " + qolgan + ")"
                    + (lot == qolgan ? " ✓" : " — DIQQAT: soni mos emas"));
            if (lot != qolgan) negativXatolar.add("101 lotli taklif: " + lot + " lot berildi, kutilgan " + qolgan);
        } else if (qismanTaklifBerildi) {
            negativXatolar.add("101 lotli taklif imzolandi, lekin qolgan " + qolgan + " lotga bitim tuzilmadi");
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

    /** Admin: "Tasdiqlanmagan" tabida bitimlarni belgilab, "Ommaviy tasdiqlash" bilan bitta imzoda tasdiqlaydi. Tugma bo'lmasa bittalab. */
    void adminOmmaviyTasdiqla(String base, List<String> nos) {
        driver.get(base + "/uz/moderationv2/contracts");
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//main//button[starts-with(normalize-space(.),'Tasdiqlanmagan')]")), 20));
        Assert.assertNotNull(waitFor(() -> { for (String x : nos) if (rowWith(x) != null) return Boolean.TRUE; return null; }, 60), "Admin 'Tasdiqlanmagan' ro'yxatida bitimlar yo'q: " + nos);
        sahifada100(driver);
        // avval sarlavhadagi "hammasini tanlash" (ommaviy tugma ko'pincha shundan keyin chiqadi)
        WebElement hammasi = visible(driver, By.cssSelector("main thead input[type=checkbox]"));
        if (hammasi != null) { realClick(driver, hammasi); sleep(500); }
        WebElement ommaviy = visible(driver, By.xpath("//main//button[contains(normalize-space(.),'Ommaviy')]"));
        if (ommaviy != null) System.out.println("8) Ommaviy tugma: '" + clean(ommaviy.getText()) + "'");
        if (ommaviy == null) {
            System.out.println("8) 'Ommaviy tasdiqlash' tugmasi yo'q — bittalab tasdiqlanadi");
            for (String no : nos) adminTasdiqla(base, no);
            return;
        }
        for (String no : nos) {
            WebElement row = rowWith(no);
            if (row == null) { System.out.println("8) DIQQAT: " + no + " 'Tasdiqlanmagan' da yo'q (treyder imzolamagan bo'lishi mumkin)"); continue; }
            WebElement cb = row.findElement(By.cssSelector("input[type=checkbox]"));
            if (cb.isSelected()) continue; // "hammasini tanlash" allaqachon belgilagan
            realClick(driver, cb);
            sleep(300);
            if (!cb.isSelected()) jsClick(driver, cb);
        }
        jsClick(driver, ommaviy);
        sleep(1500);
        WebElement ok = waitFor(() -> lastVisible(driver, By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button[normalize-space(.)='Tasdiqlash' or normalize-space(.)='Imzolash' or normalize-space(.)='Ha']")), 10);
        if (ok != null) jsClick(driver, ok);
        Eimzo.typePassword(10);
        sleep(1500);
        System.out.println("8) Ommaviy tasdiqlash xabarlari: " + toasts(driver));
        // tekshiruv: bitimlar "Tasdiqlanmagan" dan chiqib ketishini kutamiz (ko'pi bilan ~30 s)
        List<String> qoldi = new ArrayList<>(nos);
        for (int urinish = 0; urinish < 6 && !qoldi.isEmpty(); urinish++) {
            driver.navigate().refresh();
            jsClick(driver, waitFor(() -> visible(driver, By.xpath("//main//button[starts-with(normalize-space(.),'Tasdiqlanmagan')]")), 20));
            sleep(2500);
            qoldi.clear();
            for (String no : nos) if (rowWith(no) != null) qoldi.add(no);
        }
        Assert.assertTrue(qoldi.isEmpty(), "Ommaviy tasdiqlashdan keyin hali 'Tasdiqlanmagan'da: " + qoldi);
        System.out.println("8) Admin ommaviy tasdiqladi: " + nos);
    }

    /** Admin: bitta bitimni 'Tasdiqlanmagan' tabidan tasdiqlaydi. */
    void adminTasdiqla(String base, String dealNo) {
        driver.get(base + "/uz/moderationv2/contracts");
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//main//button[starts-with(normalize-space(.),'Tasdiqlanmagan')]")), 20));
        WebElement row = waitFor(() -> rowWith(dealNo), 60);
        Assert.assertNotNull(row, "Admin 'Tasdiqlanmagan' ro'yxatida " + dealNo + " yo'q");
        List<WebElement> tds = row.findElements(By.cssSelector("td"));
        List<WebElement> btns = tds.get(tds.size() - 1).findElements(By.cssSelector("button"));
        jsClick(driver, btns.get(btns.size() - 1));
        sleep(1200);
        jsClick(driver, lastVisible(driver, By.xpath("//*[normalize-space(text())='Tasdiqlash']")));
        sleep(2000);
        jsClick(driver, lastVisible(driver, By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button[normalize-space(.)='Tasdiqlash']")));
        Eimzo.typePassword(6);
        sleep(3000);
        driver.navigate().refresh();
        sleep(5000);
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//main//button[starts-with(normalize-space(.),'Tasdiqlanmagan')]")), 20));
        sleep(4000);
        Assert.assertNull(rowWith(dealNo), dealNo + " hali ham 'Tasdiqlanmagan' ro'yxatida");
        System.out.println("8) Admin " + dealNo + " bitimini tasdiqladi");
    }

    // ================================================================== yordamchilar

    WebElement rowWith(String text) {
        for (WebElement r : driver.findElements(By.cssSelector("main tbody tr"))) try { if (r.getText().contains(text)) return r; } catch (Exception ignore) {}
        return null;
    }

    void openContracts(String base, String tab) {
        // xaridor mijoz (masalan TESTIVEST) nomidan sotib olgan bo'lsa, bitim treyderda "Mijozimning shartnomalari" da turadi
        boolean mijoz = tab.equals("Sotib olish") && !XARIDOR_MIJOZ.isEmpty();
        driver.get(base + (mijoz ? "/uz/trader/contracts/client-contracts" : "/uz/trader/contracts/brokers-contracts"));
        sleep(500);
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//main//*[normalize-space(text())='" + tab + "']")), 20));
        sleep(2000);
        sahifada100(driver);
    }

    /** "Bitim holati" filtridan holatni tanlaydi; jadvalda faqat shu holatdagilar qolguncha kutadi. */
    boolean holatFiltri(String holat) {
        for (WebElement in : driver.findElements(By.cssSelector("main input[id^='react-select']"))) {
            try {
                Object txt = js().executeScript("let c=arguments[0].closest('[class*=\"-container\"]'); return c? c.innerText : '';", in);
                if (!String.valueOf(txt).contains("Bitim holati")) continue;
                in.sendKeys(Keys.ARROW_DOWN);
                WebElement op = waitFor(() -> lastVisible(driver, By.xpath("//*[contains(@id,'-option-')][normalize-space(.)='" + holat + "']")), 5);
                if (op == null) { in.sendKeys(Keys.ESCAPE); return false; }
                jsClick(driver, op);
                waitFor(() -> {
                    List<WebElement> rows = driver.findElements(By.cssSelector("main tbody tr"));
                    if (rows.isEmpty()) return null;
                    for (WebElement r : rows) if (!norm(r.getText()).contains(norm(holat).replace("Imzolanish", "Imzolash"))) return null;
                    return Boolean.TRUE;
                }, 10);
                return true;
            } catch (Exception ignore) {}
        }
        return false;
    }

    static String norm(String s) { return s == null ? "" : s.replaceAll("[‘’`ʼ]", "'"); }

    void signDeal(String base, String who, String tab) {
        kim(who);
        openContracts(base, tab);
        // filtr: "Bitim holati" = "Imzolanish kutilmoqda" -> sarlavhadagi "hammasini tanlash" -> "Ommaviy imzolash"
        boolean filtr = holatFiltri("Imzolanish kutilmoqda");
        sahifada100(driver); // filtrdan keyin sayt sahifani yana 10 qatorga qaytaradi
        Assert.assertNotNull(waitFor(() -> rowWith(dealNo), 20), who + ": " + dealNo + " '" + tab + "' tabida yo'q");
        WebElement hammasi = filtr ? visible(driver, By.cssSelector("main thead input[type=checkbox]")) : null;
        if (hammasi != null) {
            realClick(driver, hammasi);
            sleep(400);
            if (!hammasi.isSelected()) jsClick(driver, hammasi);
        } else {
            // zaxira: bittalab belgilash
            for (String no : dealNos) {
                WebElement row = rowWith(no);
                Assert.assertNotNull(row, who + ": " + no + " '" + tab + "' tabida yo'q");
                WebElement cb = row.findElement(By.cssSelector("input[type=checkbox]"));
                realClick(driver, cb);
                sleep(300);
                if (!cb.isSelected()) jsClick(driver, cb);
            }
        }
        jsClick(driver, driver.findElement(By.xpath("//main//button[contains(normalize-space(.),'Ommaviy imzolash')]")));
        WebElement sign = waitFor(() -> lastVisible(driver, By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button[normalize-space(.)='Imzolash']")), 15);
        Assert.assertNotNull(sign, who + ": imzolash oynasi ochilmadi");
        jsClick(driver, sign);
        Eimzo.typePassword(10);
        Assert.assertTrue(waitToast(driver, "Muvaffaqiyatli", 20), who + ": imzolanmadi. Xabarlar: " + toasts(driver));
        System.out.println("7) " + who + " imzoladi: " + dealNos);
    }


    String currentRole() {
        for (WebElement e : driver.findElements(By.xpath("//span[contains(@class,'w-fit')]")))
            try { if (e.isDisplayed() && e.getText().contains("MODERATOR")) return e.getText().split("\n")[0].trim(); } catch (Exception ignore) {}
        return "?";
    }

    void switchRole(String role) {
        driver.get(Config.adminUrl() + "/uz/moderationv2/profile");
        waitFor(() -> "?".equals(currentRole()) ? null : Boolean.TRUE, 30);
        if (currentRole().equals(role)) return;
        for (int t = 0; t < 5; t++) {
            for (WebElement e : driver.findElements(By.xpath("//span[contains(@class,'w-fit')]")))
                try { if (e.isDisplayed() && e.getText().contains("MODERATOR")) { jsClick(driver, e); break; } } catch (Exception ignore) {}
            WebElement item = waitFor(() -> visible(driver, By.xpath("//span[normalize-space(.)='Rol almashtrish']")), 3);
            if (item != null) { item.click(); break; }
        }
        jsClick(driver, waitFor(() -> lastVisible(driver, By.xpath("//div[normalize-space(text())='" + role + "']")), 5));
        sleep(300);
        jsClick(driver, lastVisible(driver, By.xpath("//button[normalize-space(.)='Saqlash']")));
        sleep(1200);
        driver.get(Config.adminUrl() + "/uz/moderationv2/profile");
        waitFor(() -> role.equals(currentRole()) ? Boolean.TRUE : null, 20);
        Assert.assertEquals(currentRole(), role, "Rol almashmadi");
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
