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
    static final int MIN = Integer.parseInt(Config.getOr("auction.minutes", "2"));
    static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    // qadamlar orasida uzatiladigan ma'lumotlar
    String contractId;          // moderator ro'yxatidagi ID
    LocalDateTime elonStart, savdoStart, savdoEnd;
    String dealNo;              // birinchi bitim raqami (B2600....)
    List<String> dealNos = new ArrayList<>(); // shu savdodagi barcha bitimlar
    List<String> negativXatolar = new ArrayList<>(); // salbiy holatlarda sayt qabul qilib yuborganlari

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
        openBrowser();
        String base = Config.traderUrl();
        loginByKey(driver, base, Config.get("trader1.login"), true);
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
        openBrowser();
        String base = Config.adminUrl();
        loginByKey(driver, base, Config.get("admin1.login"), false);
        try {
            for (String role : new String[]{"MODERATOR_LISTING_MAIN", "MODERATOR_LISTING", "MODERATOR_LISTING_MEMBER"}) {
                switchRole(role);
                driver.get(base + "/uz/moderationv2/listing/contracts");
                waitFor(() -> visible(driver, By.cssSelector("main tbody tr")), 30);
                sleep(2000);
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
                Assert.assertTrue(waitToast(driver, "Muvaffaqiyatli", 20), role + ": tasdiqlanmadi. Xabarlar: " + toasts(driver));
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
        openBrowser();
        String base = Config.adminUrl();
        loginByKey(driver, base, Config.get("admin2.login"), false);
        String session = LocalTime.now().isAfter(LocalTime.of(14, 0)) ? "Kechki sessiya" : "Ertalabki sessiya";
        driver.get(base + "/uz/moderationv2/sales-schedule/auction-sales-2/trade-session");
        waitFor(() -> visible(driver, By.cssSelector("main tbody tr")), 30);
        sleep(2000);
        // Qoida: joriy sessiya ochiq bo'lsa, to'liq tugashini kutamiz
        LocalTime[] range = sessionRange(session);
        LocalTime now = LocalTime.now();
        if (range != null && !now.isBefore(range[0]) && now.isBefore(range[1])) {
            waitUntil(LocalDate.now().atTime(range[1]).plusSeconds(10), "oldingi sessiya tugashi");
            driver.navigate().refresh();
            sleep(4000);
        }
        LocalDateTime base0 = LocalDateTime.now().withSecond(0).withNano(0).plusMinutes(2);
        elonStart = base0;
        savdoStart = base0.plusMinutes(MIN);
        savdoEnd = savdoStart.plusMinutes(MIN);
        editRow(session, "e'lon", elonStart, savdoStart);
        editRow(session, "Boshqalar", savdoStart, savdoEnd);
        if (session.startsWith("Kechki")) editRow(session, "^", savdoEnd, savdoEnd.plusMinutes(1));
        System.out.println("3) Savdo vaqtlari: e'lon " + elonStart.format(HM) + "-" + savdoStart.format(HM)
                + ", Boshqalar " + savdoStart.format(HM) + "-" + savdoEnd.format(HM));
    }

    // ------------------------------------------------------------------ 4
    @Test(priority = 4, dependsOnMethods = "step3_savdoVaqtiniQoyish",
          description = "Treyder 1 e'lon vaqtida mahsulotni savdoga chiqaradi")
    public void step4_savdogaChiqarish() {
        openBrowser();
        String base = Config.traderUrl();
        loginByKey(driver, base, Config.get("trader1.login"), true);
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
        openBrowser();
        String base = Config.traderUrl();
        loginByKey(driver, base, Config.get("trader2.login"), true);
        // --- IJOBIY: BIDS ta tabda taklif oynalari OLDINDAN tayyorlanadi, keyin PAUZA oralig'i bilan ketma-ket yuboriladi
        List<String> tablar = new ArrayList<>();
        List<WebElement> panellar = new ArrayList<>();
        for (int i = 0; i < BIDS; i++) {
            if (i > 0) driver.switchTo().newWindow(WindowType.TAB);
            tablar.add(driver.getWindowHandle());
            savdoSahifasi(base);
            panellar.add(waitFor(() -> { try { return oynaTayyorla(BID_LOTS); } catch (Throwable e) { System.out.println("5) oyna hali tayyor emas: " + e.getMessage()); savdoSahifasi(base); return null; } }, 150));
            Assert.assertNotNull(panellar.get(i), (i + 1) + "-tabda taklif oynasi tayyorlanmadi");
            System.out.println("5) " + (i + 1) + "-oyna tayyor");
        }
        waitUntil(savdoStart.plusSeconds(2), "savdo vaqti");
        for (int i = 0; i < BIDS; i++) {
            driver.switchTo().window(tablar.get(i));
            long t0 = System.currentTimeMillis();
            yuborish(panellar.get(i), i + 1, BID_LOTS);
            System.out.println("5) " + (i + 1) + "-taklif " + (System.currentTimeMillis() - t0) / 1000.0 + " s");
            sleep(PAUZA_MS);
        }
        // --- SALBIY: savdodagidan ko'p lotga taklif. O'CHIRILGAN (auction.negativ101=true bilan yoqiladi):
        // 23.11 da imzo to'sig'i bu sahifada ishlamadi va 101 lotli taklif haqiqatan yuborilib, 100 lotni sotib oldi (B26000255).
        if (!Boolean.parseBoolean(Config.getOr("auction.negativ101", "false"))) return;
        driver.switchTo().window(tablar.get(0));
        savdoSahifasi(base);
        eimzoTosish(driver);
        String kop = String.valueOf(Integer.parseInt(LOTS) + 1);
        taklifBer(base, 0, kop);
        rasmOl("101_lot_taklif");
        System.out.println("5-negativ) " + kop + " lotga taklif -> xabarlar: " + toasts(driver) + ", imzo so'rovlari: " + eimzoUrinishlari(driver));
        if (eimzoUrinishlari(driver) > 0) negativXatolar.add("Taklif: savdodagi " + LOTS + " lotdan ko'p (" + kop + ") lotga taklif formasi o'tdi va imzo so'radi (imzo to'sildi)");
    }

    /** Treyder 2: "Sotib olish" tabini ochib, mahsulotni qidiradi. */
    void savdoSahifasi(String base) {
        driver.get(base + "/uz/trader/sales/auction-sales?tradeTab=SELL");
        sleep(4000);
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//*[normalize-space(text())='Sotib olish']")), 20));
        sleep(3000); // tab almashgach sahifa qayta chiziladi
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
                if (!XARIDOR_MIJOZ.isEmpty() && op.getText().contains(XARIDOR_MIJOZ)) return op;
            } catch (Exception ignore) {}
        }
        return XARIDOR_MIJOZ.isEmpty() ? first : null;
    }

    /** Treyder 2: ochiq sahifadagi kartochkadan oynani ochib, BID_LOTS lotga bitta taklif beradi (sahifa qayta ochilmaydi). */
    void taklifBer(String base, int b, String lotlar) {
        yuborish(oynaTayyorla(lotlar), b, lotlar);
    }

    /** Kartochkadan taklif oynasini ochadi, mijozni tanlaydi, miqdorni yozadi (YUBORMAYDI). Panelni qaytaradi. */
    WebElement oynaTayyorla(String lotlar) {
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
        WebElement buyBtn = waitFor(() -> lastVisible(driver, By.xpath("//button[normalize-space(.)='Sotib olish']")), 15);
        Assert.assertNotNull(buyBtn, "'Ariza yuborish' paneli ochilmadi");
        WebElement panel = buyBtn.findElement(By.xpath("./ancestor::*[.//input[@placeholder='Miqdor']][1]"));
        // Mijoz
        for (WebElement in : panel.findElements(By.cssSelector("input[id^='react-select']"))) {
            Object txt = js().executeScript("let c=arguments[0].closest('[class*=\"-container\"]'); return c? c.innerText : '';", in);
            if (!String.valueOf(txt).contains("Mijoz")) continue;
            in.sendKeys(Keys.ARROW_DOWN);
            sleep(800);
            WebElement tanlov = mijozVarianti();
            if (tanlov == null && !XARIDOR_MIJOZ.isEmpty()) { in.sendKeys(XARIDOR_MIJOZ); sleep(1500); tanlov = mijozVarianti(); }
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

    /** Tayyor oynada "Sotib olish" ni bosib, imzolaydi (b=0: salbiy urinish, imzo to'silgan). */
    void yuborish(WebElement panel, int b, String lotlar) {
        jsClick(driver, panel.findElement(By.xpath(".//button[normalize-space(.)='Sotib olish']")));
        if (b == 0) { sleep(3000); return; } // salbiy urinish: imzo to'silgan, parol yozilmaydi
        Eimzo.typePassword(6);
        sleep(1000);
        List<String> t = toasts(driver);
        Assert.assertFalse(t.stream().anyMatch(x -> x.contains("Majburiy")), "Taklif yuborilmadi: " + t);
        System.out.println("5) " + b + "-taklif: " + lotlar + " lotga taklif berildi. Xabarlar: " + t);
    }

    // ------------------------------------------------------------------ 6
    @Test(priority = 6, dependsOnMethods = "step5_taklifBerish",
          description = "Savdo tugagach bitim hosil bo'ladi (Treyder 1 -> Mening shartnomalarim -> Sotish)")
    public void step6_bitimHosilBolishi() {
        waitUntil(savdoEnd.plusSeconds(30), "savdo tugashi va bitim yaratilishi");
        openBrowser();
        String base = Config.traderUrl();
        loginByKey(driver, base, Config.get("trader1.login"), true);
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String time = savdoEnd.format(HM);
        for (int attempt = 0; attempt < 6 && dealNos.size() < BIDS; attempt++) {
            openContracts(base, "Sotish");
            for (WebElement r : driver.findElements(By.cssSelector("main tbody tr"))) {
                String tx = r.getText();
                if (tx.contains(today) && tx.contains(PRODUCT) && tx.contains("Imzolash kutilmoqda")) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("B\\d{8}").matcher(tx);
                    if (m.find() && !dealNos.contains(m.group())) dealNos.add(m.group());
                }
            }
            if (dealNos.size() < BIDS) sleep(10000);
        }
        Assert.assertFalse(dealNos.isEmpty(), "Bugungi '" + PRODUCT + "' bitimi topilmadi (savdo " + time + " da tugagan)");
        dealNo = dealNos.get(0);
        System.out.println("6) Bitimlar hosil bo'ldi (" + dealNos.size() + " ta, kutilgan " + BIDS + "): " + dealNos);
    }

    // ------------------------------------------------------------------ 7
    @Test(priority = 7, dependsOnMethods = "step6_bitimHosilBolishi",
          description = "Ikkala treyder bitimni 'Ommaviy imzolash' orqali imzolaydi")
    public void step7_treyderlarImzolashi() {
        openBrowser();
        String base = Config.traderUrl();
        signDeal(base, "trader1", "Sotish");
        signDeal(base, "trader2", "Sotib olish"); // mijoz nomidan bo'lsa "Mijozimning shartnomalari"
    }

    // ------------------------------------------------------------------ 8
    @Test(priority = 8, dependsOnMethods = "step7_treyderlarImzolashi",
          description = "Admin bitimni 'Tasdiqlanmagan' tabidan tasdiqlaydi")
    public void step8_adminTasdiqlashi() {
        openBrowser();
        String base = Config.adminUrl();
        loginByKey(driver, base, Config.get("admin2.login"), false);
        adminOmmaviyTasdiqla(base, dealNos);
    }

    /** Admin: "Tasdiqlanmagan" tabida bitimlarni belgilab, "Ommaviy tasdiqlash" bilan bitta imzoda tasdiqlaydi. Tugma bo'lmasa bittalab. */
    void adminOmmaviyTasdiqla(String base, List<String> nos) {
        driver.get(base + "/uz/moderationv2/contracts");
        sleep(6000);
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//main//button[starts-with(normalize-space(.),'Tasdiqlanmagan')]")), 20));
        Assert.assertNotNull(waitFor(() -> rowWith(nos.get(0)), 60), "Admin 'Tasdiqlanmagan' ro'yxatida " + nos.get(0) + " yo'q");
        sahifada100(driver);
        WebElement ommaviy = visible(driver, By.xpath("//main//button[contains(normalize-space(.),'Ommaviy tasdiqlash')]"));
        if (ommaviy == null) {
            System.out.println("8) 'Ommaviy tasdiqlash' tugmasi yo'q — bittalab tasdiqlanadi");
            for (String no : nos) adminTasdiqla(base, no);
            return;
        }
        for (String no : nos) {
            WebElement row = rowWith(no);
            Assert.assertNotNull(row, "Admin 'Tasdiqlanmagan' ro'yxatida " + no + " yo'q");
            WebElement cb = row.findElement(By.cssSelector("input[type=checkbox]"));
            realClick(driver, cb);
            sleep(400);
            if (!cb.isSelected()) jsClick(driver, cb);
        }
        jsClick(driver, ommaviy);
        sleep(1500);
        WebElement ok = waitFor(() -> lastVisible(driver, By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button[normalize-space(.)='Tasdiqlash' or normalize-space(.)='Imzolash' or normalize-space(.)='Ha']")), 10);
        if (ok != null) jsClick(driver, ok);
        Eimzo.typePassword(10);
        sleep(4000);
        System.out.println("8) Ommaviy tasdiqlash xabarlari: " + toasts(driver));
        driver.navigate().refresh();
        sleep(5000);
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//main//button[starts-with(normalize-space(.),'Tasdiqlanmagan')]")), 20));
        sleep(5000);
        sahifada100(driver);
        List<String> qoldi = new ArrayList<>();
        for (String no : nos) if (rowWith(no) != null) qoldi.add(no);
        Assert.assertTrue(qoldi.isEmpty(), "Ommaviy tasdiqlashdan keyin hali 'Tasdiqlanmagan'da: " + qoldi);
        System.out.println("8) Admin ommaviy tasdiqladi: " + nos);
    }

    /** Admin: bitta bitimni 'Tasdiqlanmagan' tabidan tasdiqlaydi. */
    void adminTasdiqla(String base, String dealNo) {
        driver.get(base + "/uz/moderationv2/contracts");
        sleep(6000);
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
        sleep(6000);
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//main//*[normalize-space(text())='" + tab + "']")), 20));
        sleep(4000);
        sahifada100(driver);
    }

    void signDeal(String base, String who, String tab) {
        loginByKey(driver, base, Config.get(who + ".login"), true);
        openContracts(base, tab);
        Assert.assertNotNull(waitFor(() -> rowWith(dealNo), 20), who + ": " + dealNo + " '" + tab + "' tabida yo'q");
        for (String no : dealNos) {
            WebElement row = rowWith(no);
            Assert.assertNotNull(row, who + ": " + no + " '" + tab + "' tabida yo'q");
            WebElement cb = row.findElement(By.cssSelector("input[type=checkbox]"));
            realClick(driver, cb);
            sleep(600);
            if (!cb.isSelected()) jsClick(driver, cb);
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
            sleep(1500);
            WebElement item = visible(driver, By.xpath("//span[normalize-space(.)='Rol almashtrish']"));
            if (item != null) { item.click(); break; }
        }
        sleep(2000);
        jsClick(driver, lastVisible(driver, By.xpath("//div[normalize-space(text())='" + role + "']")));
        sleep(700);
        jsClick(driver, lastVisible(driver, By.xpath("//button[normalize-space(.)='Saqlash']")));
        sleep(5000);
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
        WebElement title = visible(driver, By.xpath("//*[normalize-space(text())='Savdoni tahrirlash']"));
        if (title != null) { title.click(); sleep(400); }
        Assert.assertEquals(driver.findElement(By.id(id)).getAttribute("value"), hhmm, id + " vaqti o'rnatilmadi");
    }
}
