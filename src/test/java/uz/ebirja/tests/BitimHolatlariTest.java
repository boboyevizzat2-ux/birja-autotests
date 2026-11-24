package uz.ebirja.tests;

import org.openqa.selenium.*;
import org.testng.Assert;
import org.testng.ITestResult;
import org.testng.annotations.*;
import uz.ebirja.base.BaseTest;
import uz.ebirja.base.Eimzo;
import uz.ebirja.config.Config;

import java.io.File;
import java.math.BigDecimal;
import java.util.*;

import static uz.ebirja.base.Ui.*;

/**
 * Bitim tuzilgandan keyingi holatlar (har biri alohida bitimda, alohida ishga tushadi).
 * Bitimlar "To'lov kutilmoqda" holatida bo'lishi kerak (auksion testi + admin tasdig'idan keyin).
 * Bitim raqamlari: -Dbitim.tolov=B... -Dbitim.kelishuv=B... va h.k. (pastdagi standart qiymatlarga qarang).
 *
 * Ishlatish (masalan faqat to'lov): mvn test -Dsurefire.suiteXmlFiles= -Dtest=BitimHolatlariTest#tolovQilish
 */
public class BitimHolatlariTest extends BaseTest {
    static final String CONTRACTS = "/uz/trader/contracts/brokers-contracts";
    // Sotuvchi (trader1) va xaridor (trader2) tashkilotlarining STIR raqamlari
    static final String SOTUVCHI_STIR = Config.getOr("trader1.stir", "311927414");
    static final String XARIDOR_STIR = Config.getOr("auction.buyerClient", Config.getOr("trader2.stir", "300417856"));

    // Kutilgan jarima: kontrakt narxi (1 000 000) x 1 lot x zakalat 10% = 100 000
    static final long JARIMA = Long.parseLong(Config.getOr("jarima.summa", "100000"));
    // Kutilgan to'lov: bitim summasi 1 000 000 - zakalat 100 000
    static final long TOLOV = Long.parseLong(Config.getOr("tolov.summa", "900000"));

    String base;

    @BeforeClass
    public void setUp() { base = Config.traderUrl(); }

    @AfterMethod(alwaysRun = true)
    public void yopish(ITestResult r) {
        if (r.getStatus() == ITestResult.FAILURE && driver != null) rasm("XATO_" + r.getMethod().getMethodName());
        closeBrowser();
    }

    // ================================================================== HOLATLAR
    // Har bir holat o'z bitimida. Bitim tanlash tartibi: -Dbitim.<kalit>=B... -> shu run'dagi auksion bitimlari (tartib bo'yicha) -> standart.

    /** n-bitim: sozlamadan, bo'lmasa shu run'dagi auksion bitimlaridan (AuctionFlowTest.OXIRGI_BITIMLAR). */
    static String bitim(String key, int n, String def) {
        String v = Config.getOr("bitim." + key, "");
        if (!v.isEmpty()) return v;
        if (AuctionFlowTest.OXIRGI_BITIMLAR.size() > n) return AuctionFlowTest.OXIRGI_BITIMLAR.get(n);
        // shu run'da savdo testi ishlagan, lekin bitim bermagan bo'lsa — eski bitimlarda chalkash xato chiqarmaymiz
        if (AuctionFlowTest.SAVDO_ISHLADI)
            throw new org.testng.SkipException("Savdo testi bu stsenariy uchun bitim bermadi (" + (n + 1) + "-bitim yo'q) — o'tkazib yuborildi");
        return def;
    }

    @Test(priority = 21, description = "1-bitim: to'lov -> YBHR (to'liq) -> xaridor tasdiqlaydi -> 'Bajarildi'")
    public void h1_tolovVaYbhrToliq() {
        String no = bitim("ybhrToliq", 0, "B26000256");
        if (!holatQanday("trader2", "Sotib olish", no).contains("To'langan")) tolov(no);
        String miqdor = Config.getOr("bitim.lotHajmi", "1");
        ybhrYuborish(no, miqdor);
        ybhrTasdiqlash(no);
        String h = holatQanday("trader1", "Sotish", no);
        System.out.println("YBHR to'liq " + no + " holati: " + h);
        Assert.assertTrue(h.contains("Bajarildi"), no + ": to'liq YBHR tasdiqlangandan keyin holat 'Bajarildi' emas: " + h);
    }

    @Test(priority = 22, description = "2-bitim: to'lov -> YBHR (yarmi) -> kelishuv bilan bekor -> 'Qisman bajarildi'")
    public void h2_ybhrYarimVaKelishuv() {
        String no = bitim("ybhrYarim", 1, "B26000257");
        if (!holatQanday("trader2", "Sotib olish", no).contains("To'langan")) tolov(no);
        String yarim = Config.getOr("bitim.yarimHajm", "0.5");
        ybhrYuborish(no, yarim);
        ybhrTasdiqlash(no);
        bekorQilish(no, "Kelishuv", null, "Qisman bajarildi");
    }

    @Test(priority = 23, description = "3-bitim: KELISHUV bilan bekor. Jarima yo'q, zakalatlar qaytadi")
    public void h3_bekorKelishuv() {
        bekorQilish(bitim("kelishuv", 2, "B26000258"), "Kelishuv", null, "Bekor qilingan");
    }

    @Test(priority = 24, description = "4-bitim: XARIDOR JARIMASI. Xaridor zakalati sotuvchiga o'tadi")
    public void h4_bekorXaridorJarimasi() {
        bekorQilish(bitim("xaridorJarima", 3, "B26000259"), "Xaridor jarimasi", XARIDOR_STIR, "Bekor qilingan");
    }

    @Test(priority = 25, description = "5-bitim: SOTUVCHI JARIMASI. Sotuvchi zakalati xaridorga o'tadi")
    public void h5_bekorSotuvchiJarimasi() {
        bekorQilish(bitim("sotuvchiJarima", 4, "B26000260"), "Sotuvchi jarimasi", SOTUVCHI_STIR, "Bekor qilingan");
    }

    @Test(priority = 26, description = "6-bitim: TO'LOV muddatini uzaytirish: qabul (sana + N ish kuni) va rad etish (sana o'zgarmaydi)")
    public void h6_tolovMuddatiniUzaytirish() {
        String no = bitim("tolovUzaytirish", 5, "B26000261");
        muddatUzaytirish(no, "To'lov muddatini uzaytirish", "payment-request", "To'lovning oxirgi muddati", true);
        muddatUzaytirish(no, "To'lov muddatini uzaytirish", "payment-request", "To'lovning oxirgi muddati", false);
    }

    @Test(priority = 27, description = "7-bitim: to'lov -> YETKAZIB BERISH muddatini uzaytirish: qabul va rad etish")
    public void h7_yetkazibBerishMuddatiniUzaytirish() {
        String no = bitim("yetkazishUzaytirish", 6, "B26000262");
        if (!holatQanday("trader2", "Sotib olish", no).contains("To'langan")) tolov(no);
        muddatUzaytirish(no, "Yetkazib berish muddatini uzaytirish", "delivered-request", "Oxirgi yetkazib berish kuni", true);
        muddatUzaytirish(no, "Yetkazib berish muddatini uzaytirish", "delivered-request", "Oxirgi yetkazib berish kuni", false);
    }

    /** Faqat to'lov (alohida ishga tushirish uchun): -Dbitim.tolov=B... */
    @Test(priority = 30, enabled = false, description = "Faqat to'lov")
    public void tolovQilish() { tolov(bitim("tolov", 0, "B26000256")); }

    /** Faqat ko'rish: -Dbitimlar=B...,B... bo'yicha ikkala treyderning balans tarixidagi operatsiyalar. */
    @Test(description = "Bitimlar bo'yicha balans operatsiyalarini ko'rsatadi (hech narsa o'zgartirmaydi)")
    public void operatsiyalarniKorish() {
        if (System.getProperty("bitimlar") == null) throw new org.testng.SkipException("Faqat qo'lda: -Dbitimlar=B... bilan");
        for (String who : new String[]{"trader1", "trader2"}) {
            login(who);
            for (String no : System.getProperty("bitimlar", "B26000242").split(",")) {
                System.out.println("=== " + who + " " + no);
                for (String op : operatsiyalar(no.trim())) System.out.println("   " + op);
            }
        }
    }

    // ================================================================== AMALLAR

    void tolov(String no) {
        login("trader2");
        BigDecimal[] oldin = balans(XARIDOR_STIR);
        WebElement row = bitimQatori("Sotib olish", no);
        Assert.assertTrue(norm(row.getText()).contains("To'lov kutilmoqda"), no + " 'To'lov kutilmoqda' holatida emas: " + cut(row.getText(), 200));
        menyu(row, "To'lov qilish");
        dialogTugma("To'lov qilish");
        Eimzo.typePassword(10);
        sleep(1500);
        if (toasts(driver).stream().anyMatch(x -> x.contains("Parol"))) { // E-IMZO parolni qabul qilmasa bir marta qayta
            System.out.println("TO'LOV " + no + ": parol xatosi, qayta urinish");
            menyu(bitimQatori("Sotib olish", no), "To'lov qilish");
            dialogTugma("To'lov qilish");
            Eimzo.typePassword(10);
            sleep(1500);
        }
        System.out.println("TO'LOV " + no + " xabarlar: " + toasts(driver));
        // holat jadvalda biroz kechikib yangilanadi: 15 s gacha kutamiz
        String h = "";
        for (int u = 0; u < 4 && !h.contains("To'langan"); u++) { if (u > 0) sleep(3000); h = holatQanday(null, "Sotib olish", no); }
        Assert.assertTrue(h.contains("To'langan"), no + ": to'lovdan keyin holat 'To'langan' emas: " + h);
        BigDecimal[] keyin = balans(XARIDOR_STIR);
        System.out.println("TO'LOV " + no + " xaridor balansi (barcha/erkin/band/qarz): " + Arrays.toString(oldin) + " -> " + Arrays.toString(keyin));
        // to'lov = bitim summasi - oldindan band qilingan zakalat (1 000 000 - 100 000 = 900 000)
        if (oldin != null && keyin != null) {
            BigDecimal kamaydi = oldin[1].subtract(keyin[1]);
            System.out.println("TO'LOV " + no + ": erkin mablag' " + kamaydi + " ga kamaydi (kutilgan " + TOLOV + ")");
            Assert.assertEquals(kamaydi.longValue(), TOLOV, no + ": to'lovdan keyin xaridorning erkin mablag'i kutilgandek kamaymadi");
        }
    }

    /**
     * Sotuvchi bekor qilish so'rovini yuboradi, xaridor "Bekor qilish so'rovi" sahifasida tasdiqlaydi.
     * jarimaStir: jarimaga tushgan tomon (null = kelishuv: hech kimdan jarima olinmasligi kerak).
     */
    void bekorQilish(String no, String tur, String jarimaStir, String kutilganHolat) {
        // operatsiyalar sotuvchi (broker) tarixidan tekshiriladi: jarima ham, kelishuv ham shu yerda ko'rinadi
        String kimBalans = "trader1";
        login(kimBalans);
        List<String> opOldin = operatsiyalar(no);
        login("trader1");
        WebElement row = bitimQatori("Sotish", no);
        menyu(row, "Bitimni bekor qilish");
        dialogSelect(tur);
        dialogEl(By.cssSelector("textarea[name=reason]")).sendKeys("Avtotest: " + tur);
        rasm("bekor_" + no + "_forma");
        dialogTugma("Tasdiqlash");
        Eimzo.typePassword(10);
        sleep(1500);
        System.out.println("BEKOR (" + tur + ") " + no + " so'rov yuborildi. Xabarlar: " + toasts(driver));
        login("trader2");
        sorovgaJavob("canceled-request", no, "Tasdiqlash");
        String h = holatQanday(null, "Sotib olish", no);
        System.out.println("BEKOR (" + tur + ") " + no + " holati: " + h);
        Assert.assertTrue(h.contains(kutilganHolat), no + ": bekor qilingandan keyin holat '" + kutilganHolat + "' emas: " + h);
        login(kimBalans);
        List<String> yangi = new ArrayList<>(operatsiyalar(no));
        yangi.removeAll(opOldin);
        System.out.println("BEKOR (" + tur + ") " + no + " yangi operatsiyalar: " + yangi);
        if (jarimaStir == null) {
            // KELISHUV: hech kimdan jarima yo'q, zakalat blokdan chiqadi
            Assert.assertTrue(yangi.stream().noneMatch(op -> op.toLowerCase().contains("jarima")), no + ": kelishuvda jarima olindi: " + yangi);
            Assert.assertTrue(yangi.stream().anyMatch(op -> op.contains("blokirovkadan chiqarish")), no + ": kelishuvdan keyin zakalat blokdan chiqmadi: " + yangi);
            System.out.println("KELISHUV " + no + ": jarima yo'q, zakalat qaytdi ✓");
            return;
        }
        // JARIMA = kontraktdagi zakalat, jarimaga tushgan tomondan ikkinchi tomonga
        String kutilgan = String.format("%,d", JARIMA).replaceAll("[^0-9]", " ");
        String boshqa = XARIDOR_STIR.equals(jarimaStir) ? SOTUVCHI_STIR : XARIDOR_STIR;
        boolean topildi = yangi.stream().anyMatch(op -> op.toLowerCase().contains("jarima") && op.contains("| " + jarimaStir + " | " + boshqa + " | " + kutilgan + " |"));
        Assert.assertTrue(topildi, no + ": " + kutilgan + " so'mlik jarima " + jarimaStir + " dan " + boshqa + " ga o'tmadi. Yangi operatsiyalar: " + yangi);
        System.out.println("JARIMA (" + tur + ") " + no + ": " + kutilgan + " so'm " + jarimaStir + " -> " + boshqa + " ✓");
    }

    /**
     * Sotuvchi muddatni N ish kuniga uzaytirishni so'raydi, xaridor qabul qiladi yoki rad etadi.
     * qabul=true: yangi sana = eski sana + N ish kuni (shanba/yakshanba hisoblanmaydi).
     * qabul=false: sana o'zgarmaydi, so'rov "Bekor qilingan" tabida bo'ladi.
     */
    void muddatUzaytirish(String no, String band, String sorovSahifa, String sanaUstuni, boolean qabul) {
        int n = Integer.parseInt(Config.getOr("bitim.uzaytirishKun", "2"));
        login("trader1");
        WebElement row = bitimQatori("Sotish", no);
        java.time.LocalDate eski = sana(row, sanaUstuni);
        menyu(row, band);
        dialogEl(By.name("extensionDay")).sendKeys(String.valueOf(n));
        dialogEl(By.cssSelector("textarea[name=description]")).sendKeys("Avtotest: " + band + (qabul ? "" : " (rad etiladi)"));
        dialogTugma("Saqlash");
        Eimzo.typePassword(10);
        sleep(1500);
        System.out.println(band.toUpperCase() + " " + no + ": " + n + " ish kuni so'raldi. Xabarlar: " + toasts(driver));
        login("trader2");
        sorovgaJavob(sorovSahifa, no, qabul ? "Tasdiqlash" : "Bekor qilish");
        String tab = qabul ? "Qabul qilingan" : "Bekor qilingan";
        driver.get(base + "/uz/trader/contracts/" + sorovSahifa);
        jadvalKut(driver);
        WebElement t = visible(driver, By.xpath("//main//*[starts-with(normalize-space(.),'" + tab + "')][not(*) or self::button]"));
        if (t != null) { jsClick(driver, t); jadvalKut(driver); }
        Assert.assertNotNull(rowWith(no), no + ": '" + band + "' so'rovi '" + tab + "' ro'yxatida yo'q");
        login("trader1");
        java.time.LocalDate yangi = sana(bitimQatori("Sotish", no), sanaUstuni);
        java.time.LocalDate kutilgan = qabul ? ishKuniQoshish(eski, n) : eski;
        System.out.println(band.toUpperCase() + " " + no + (qabul ? " QABUL" : " RAD") + ": " + sanaUstuni + " " + eski + " -> " + yangi + " (kutilgan " + kutilgan + ")");
        Assert.assertEquals(yangi, kutilgan, no + ": '" + sanaUstuni + "' " + (qabul ? n + " ish kuniga to'g'ri uzaymadi" : "rad etilgandan keyin o'zgarib ketdi"));
    }

    static java.time.LocalDate ishKuniQoshish(java.time.LocalDate d, int n) {
        while (n > 0) {
            d = d.plusDays(1);
            if (d.getDayOfWeek() != java.time.DayOfWeek.SATURDAY && d.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) n--;
        }
        return d;
    }

    /** Qatordagi ustun (sarlavha bo'yicha) ichidagi sana (dd/MM/yyyy). */
    java.time.LocalDate sana(WebElement row, String ustunNomi) {
        List<WebElement> ths = driver.findElements(By.cssSelector("main thead th"));
        List<WebElement> tds = row.findElements(By.cssSelector("td"));
        for (int i = 0; i < ths.size() && i < tds.size(); i++) {
            if (!norm(ths.get(i).getText()).trim().equalsIgnoreCase(norm(ustunNomi))) continue;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9]{2})/([0-9]{2})/([0-9]{4})").matcher(tds.get(i).getText());
            if (!m.find()) break; // ustunlar siljigan bo'lishi mumkin -> qator matnidan olinadi
            return java.time.LocalDate.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
        }
        // zaxira: qator matnidan. To'lov muddati = "N kun qoldi" dan keyingi sana; yetkazib berish = eng oxirgi sana
        List<String> s = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[0-9]{2}/[0-9]{2}/[0-9]{4}").matcher(norm(row.getText()));
        while (m.find()) s.add(m.group());
        String tanlov = null;
        if (norm(ustunNomi).toLowerCase().contains("yetkazib")) tanlov = s.isEmpty() ? null : s.get(s.size() - 1);
        else {
            java.util.regex.Matcher q = java.util.regex.Pattern.compile("qoldi[^0-9]*([0-9]{2}/[0-9]{2}/[0-9]{4})").matcher(norm(row.getText()));
            if (q.find()) tanlov = q.group(1);
        }
        Assert.assertNotNull(tanlov, "'" + ustunNomi + "' sanasi topilmadi: " + cut(row.getText(), 300));
        String[] p = tanlov.split("/");
        return java.time.LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[1]), Integer.parseInt(p[0]));
    }

    /** YBHR: sotuvchi YBHR sahifasida shartnoma, faktura, sana (bugun), miqdor, xlsx hujjat bilan yuboradi. */
    void ybhrYuborish(String no, String miqdor) {
        login("trader1");
        driver.get(base + "/uz/trader/hkkp/ybhr");
        jadvalKut(driver);
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//main//button[contains(normalize-space(.),'YBHR yuborish')]")), 20));
        sleep(2500);
        WebElement sh = dialogEl(By.cssSelector("input[placeholder='Shartnoma raqamini kiriting']"));
        sh.sendKeys(no);
        sh.sendKeys(Keys.ENTER);
        Object yuklandi = waitFor(() -> {
            WebElement d = dialog();
            return d != null && d.getText().contains("Qoldiq") && !d.getText().contains("0 -") ? Boolean.TRUE : null;
        }, 20);
        Assert.assertNotNull(yuklandi, "YBHR: shartnoma ma'lumoti yuklanmadi");
        dialogEl(By.name("factureNumber")).sendKeys("AT-" + no);
        jsClick(driver, dialogEl(By.name("factureDate")));
        sleep(1000);
        String kun = String.valueOf(java.time.LocalDate.now().getDayOfMonth());
        WebElement day = lastVisible(driver, By.xpath("//*[contains(@class,'calendar') or contains(@class,'picker') or contains(@class,'absolute')]//*[normalize-space(text())='" + kun + "']"));
        Assert.assertNotNull(day, "YBHR: kalendarda bugungi kun topilmadi");
        jsClick(driver, day);
        sleep(600);
        dialogEl(By.name("amount")).sendKeys(miqdor);
        // hujjat (xlsx) "Ro'yxatga qo'shish" dan OLDIN yuklanadi (aks holda "Kamida bitta fayl kiritilishi kerak")
        for (WebElement f : driver.findElements(By.cssSelector("input[type=file]")))
            try { f.sendKeys(new File("src/test/resources/test-ybhr.xlsx").getAbsolutePath()); } catch (Exception ignore) {}
        sleep(2000);
        // "Ro'yxatga qo'shish": haqiqiy bosish va ro'yxatga qator qo'shilganini tekshirish
        Object qoshildi = null;
        for (int u = 0; u < 3 && qoshildi == null; u++) {
            realClick(driver, lastVisible(driver, By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button[contains(normalize-space(.),'yxatga qo')]")));
            qoshildi = waitFor(() -> dialog().findElements(By.cssSelector("tbody tr td")).size() > 1 ? Boolean.TRUE : null, 4);
        }
        Assert.assertNotNull(qoshildi, "YBHR: 'Ro'yxatga qo'shish' dan keyin ro'yxatga qator qo'shilmadi");
        rasm("ybhr_" + no + "_forma");
        dialogTugma("Yuborish");
        Eimzo.typePassword(10);
        sleep(1500);
        System.out.println("YBHR " + no + " (" + miqdor + ") yuborildi. Xabarlar: " + toasts(driver));
    }

    /** YBHR: xaridor (trader2) YBHR sahifasida (Sotib olish) shu bitimning YBHR'ini tasdiqlaydi. */
    void ybhrTasdiqlash(String no) {
        login("trader2");
        driver.get(base + "/uz/trader/hkkp/ybhr");
        jadvalKut(driver);
        WebElement tab = visible(driver, By.xpath("//main//*[starts-with(normalize-space(text()),'Sotib olish')]"));
        if (tab != null) { jsClick(driver, tab); jadvalKut(driver); }
        WebElement s = visible(driver, By.cssSelector("main input[placeholder='Qidirish']"));
        if (s != null) { s.sendKeys(no); qidiruvKut(driver, no); }
        WebElement row = waitFor(() -> rowWith(no), 30);
        Assert.assertNotNull(row, "YBHR: xaridorda " + no + " YBHR'i topilmadi");
        System.out.println("YBHR qatori: " + cut(row.getText(), 300));
        rasm("ybhr_" + no + "_xaridor");
        WebElement btn = null;
        for (WebElement b : row.findElements(By.cssSelector("button")))
            try { if (norm(b.getText()).contains("Tasdiqlash") || "Tasdiqlash".equals(b.getAttribute("title"))) btn = b; } catch (Exception ignore) {}
        if (btn == null) {
            List<WebElement> tds = row.findElements(By.cssSelector("td"));
            jsClick(driver, tds.get(tds.size() - 1).findElement(By.cssSelector("button")));
            sleep(1200);
            btn = lastVisible(driver, By.xpath("//*[normalize-space(text())='Tasdiqlash']"));
        }
        Assert.assertNotNull(btn, "YBHR: " + no + " uchun 'Tasdiqlash' topilmadi");
        jsClick(driver, btn);
        sleep(1500);
        WebElement ok = lastVisible(driver, By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button[normalize-space(.)='Tasdiqlash' or normalize-space(.)='Ha']"));
        if (ok != null) jsClick(driver, ok);
        Eimzo.typePassword(10);
        sleep(1500);
        System.out.println("YBHR " + no + " xaridor tasdiqladi. Xabarlar: " + toasts(driver));
    }

    /** So'rovlar sahifasida (Kutilmoqda) bitimni topib "Tasdiqlash" yoki "Bekor qilish" (rad etish) bosadi. */
    void sorovgaJavob(String sahifa, String no, String tugma) {
        driver.get(base + "/uz/trader/contracts/" + sahifa);
        jadvalKut(driver);
        WebElement s = visible(driver, By.cssSelector("main input[placeholder='Qidirish']"));
        if (s != null) { s.sendKeys(no); qidiruvKut(driver, no); }
        WebElement row = waitFor(() -> rowWith(no), 30);
        Assert.assertNotNull(row, sahifa + ": " + no + " so'rovi 'Kutilmoqda' ro'yxatida yo'q");
        System.out.println(sahifa + " qatori: " + cut(row.getText(), 300));
        rasm(sahifa + "_" + no);
        WebElement btn = null;
        for (WebElement b : row.findElements(By.cssSelector("button")))
            try { if (norm(b.getText()).contains(tugma) || tugma.equals(b.getAttribute("title"))) btn = b; } catch (Exception ignore) {}
        if (btn == null) {
            // tugma ustunidagi tugma (matnsiz ikonka)
            List<String> heads = new ArrayList<>();
            for (WebElement th : driver.findElements(By.cssSelector("main thead th"))) heads.add(norm(th.getText()));
            int idx = heads.indexOf(tugma);
            List<WebElement> tds = row.findElements(By.cssSelector("td"));
            if (idx >= 0 && idx < tds.size()) btn = tds.get(idx).findElements(By.cssSelector("button, svg")).stream().findFirst().orElse(null);
        }
        Assert.assertNotNull(btn, sahifa + ": " + no + " qatorida '" + tugma + "' tugmasi topilmadi");
        jsClick(driver, btn);
        sleep(1200);
        // tasdiqlash / rad etish oynasi: sabab maydoni bo'lsa to'ldiriladi, keyin oynadagi tasdiqlovchi tugma bosiladi
        WebElement oyna = dialog();
        if (oyna != null) {
            System.out.println(sahifa + " oynasi (" + tugma + "): " + cut(oyna.getText(), 200));
            rasm(sahifa + "_" + no + "_oyna");
            for (WebElement ta : oyna.findElements(By.cssSelector("textarea, input[type=text]")))
                try { if (ta.isDisplayed() && ta.isEnabled() && (ta.getAttribute("value") == null || ta.getAttribute("value").isEmpty())) ta.sendKeys("Avtotest"); } catch (Exception ignore) {}
            WebElement ok = null;
            for (String t : new String[]{"Tasdiqlash", "Ha", "Qabul qilish", "Yuborish", "Rad etish", "Saqlash", tugma}) {
                ok = lastVisible(driver, By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button[normalize-space(.)='" + t + "']"));
                if (ok != null) break;
            }
            if (ok != null) { System.out.println(sahifa + ": oynada '" + ok.getText() + "' bosildi"); jsClick(driver, ok); }
        }
        Eimzo.typePassword(10);
        sleep(1500);
        System.out.println(sahifa + ": " + no + " tasdiqlandi. Xabarlar: " + toasts(driver));
    }

    // ================================================================== YORDAMCHILAR

    /** Qator matnidagi barcha sanalar (dd/MM/yyyy), birinchisi (bitim sanasi)siz. */
    static String sanalar(String rowText) {
        List<String> r = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[0-9]{2}/[0-9]{2}/[0-9]{4}").matcher(rowText);
        while (m.find()) r.add(m.group());
        return r.size() > 1 ? r.subList(1, r.size()).toString() : r.toString();
    }

    void login(String who) { kim(who); }

    static String norm(String s) { return s == null ? "" : s.replaceAll("[‘’`ʼ]", "'").replace(" ", " "); }

    WebElement rowWith(String text) {
        for (WebElement r : driver.findElements(By.cssSelector("main tbody tr"))) try { if (r.getText().contains(text)) return r; } catch (Exception ignore) {}
        return null;
    }

    WebElement bitimQatori(String tab, String no) {
        // xaridor mijoz nomidan (auction.buyerClient) bo'lsa, trader2 da bitim "Mijozimning shartnomalari" da
        boolean mijoz = tab.equals("Sotib olish") && !Config.getOr("auction.buyerClient", "").isEmpty();
        driver.get(base + (mijoz ? "/uz/trader/contracts/client-contracts" : CONTRACTS));
        jadvalKut(driver);
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//main//*[normalize-space(text())='" + tab + "']")), 20));
        sleep(3000);
        WebElement s = visible(driver, By.cssSelector("main input[placeholder='Qidirish']"));
        if (s != null) { s.sendKeys(no); qidiruvKut(driver, no); }
        WebElement r = waitFor(() -> rowWith(no), 30);
        Assert.assertNotNull(r, no + " '" + tab + "' tabida topilmadi");
        return r;
    }

    /** Bitim holati (qator matni). who=null bo'lsa joriy foydalanuvchi. */
    String holatQanday(String who, String tab, String no) {
        if (who != null) login(who);
        return norm(cut(bitimQatori(tab, no).getText(), 200));
    }

    String ustun(WebElement row, String nomQismi) {
        List<WebElement> ths = driver.findElements(By.cssSelector("main thead th"));
        List<WebElement> tds = row.findElements(By.cssSelector("td"));
        StringBuilder r = new StringBuilder();
        for (int i = 0; i < ths.size() && i < tds.size(); i++)
            if (norm(ths.get(i).getText()).toLowerCase().contains(nomQismi)) r.append(clean(ths.get(i).getText())).append("=").append(clean(tds.get(i).getText())).append("; ");
        return r.toString();
    }

    void menyu(WebElement row, String band) {
        List<WebElement> tds = row.findElements(By.cssSelector("td"));
        jsClick(driver, tds.get(tds.size() - 1).findElement(By.cssSelector("button")));
        sleep(1200);
        WebElement it = lastVisible(driver, By.xpath("//*[normalize-space(text())=\"" + band + "\"]"));
        Assert.assertNotNull(it, "Menyuda '" + band + "' yo'q");
        jsClick(driver, it);
        sleep(2000);
    }

    WebElement dialog() {
        return lastVisible(driver, By.cssSelector("[role=dialog], [class*=modal]"));
    }

    WebElement dialogEl(By by) {
        WebElement d = waitFor(this::dialog, 10);
        Assert.assertNotNull(d, "Oyna ochilmadi");
        for (WebElement e : d.findElements(by)) if (e.isDisplayed()) return e;
        WebElement any = visible(driver, by);
        Assert.assertNotNull(any, "Oynada element yo'q: " + by);
        return any;
    }

    void dialogSelect(String option) {
        WebElement in = dialogEl(By.cssSelector("input[id^='react-select']"));
        in.sendKeys(Keys.ARROW_DOWN);
        sleep(1200);
        WebElement op = lastVisible(driver, By.xpath("//*[contains(@id,'-option-')][normalize-space(.)=\"" + option + "\"]"));
        Assert.assertNotNull(op, "Ro'yxatda '" + option + "' yo'q");
        jsClick(driver, op);
        sleep(800);
    }

    void dialogTugma(String text) {
        WebElement b = waitFor(() -> lastVisible(driver, By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button[normalize-space(.)=\"" + text + "\"]")), 10);
        Assert.assertNotNull(b, "Oynada '" + text + "' tugmasi yo'q");
        jsClick(driver, b);
    }

    /** HKKP -> Balans: STIR bo'yicha [barcha, erkin, band, qarzdorlik]. Avval "Mijozlar balansi", keyin "Broker balansi". */
    BigDecimal[] balans(String stir) {
        driver.get(base + "/uz/trader/hkkp/transaction");
        jadvalKut(driver);
        for (String tab : new String[]{"Mijozlar balansi", "Broker balansi"}) {
            WebElement b = visible(driver, By.xpath("//main//*[normalize-space(text())='" + tab + "']"));
            if (b != null) { jsClick(driver, b); jadvalKut(driver); }
            WebElement s = visible(driver, By.cssSelector("main input[placeholder='Qidirish']"));
            if (s != null) { s.sendKeys(Keys.CONTROL + "a"); s.sendKeys(stir); qidiruvKut(driver, stir); }
            WebElement r = rowWith(stir);
            if (r == null) {
                // broker balansi bitta karta ko'rinishida bo'lishi mumkin
                String m = driver.findElement(By.tagName("main")).getText();
                if (m.contains(stir)) System.out.println("BALANS (" + tab + ") matni: " + cut(m, 600));
                continue;
            }
            List<WebElement> tds = r.findElements(By.cssSelector("td"));
            List<BigDecimal> nums = new ArrayList<>();
            for (WebElement td : tds) {
                String v = td.getText().replace("UZS", "").replace(" ", "").replace(" ", "").trim();
                if (v.matches("-?\\d+(\\.\\d+)?") && !v.equals(stir) && v.length() < 20) nums.add(new BigDecimal(v));
                else if (v.isEmpty() && nums.size() > 0) nums.add(BigDecimal.ZERO);
            }
            System.out.println("BALANS (" + tab + ") " + stir + ": " + clean(r.getText()));
            while (nums.size() < 4) nums.add(BigDecimal.ZERO);
            return nums.subList(nums.size() - 4, nums.size()).toArray(new BigDecimal[0]);
        }
        System.out.println("BALANS: " + stir + " topilmadi");
        rasm("balans_" + stir);
        return null;
    }

    /** HKKP -> Balans -> Broker balansi: shu bitim raqami yozilgan operatsiyalar. */
    List<String> operatsiyalar(String no) {
        driver.get(base + "/uz/trader/hkkp/transaction");
        jadvalKut(driver);
        WebElement b = visible(driver, By.xpath("//main//*[normalize-space(text())='Broker balansi']"));
        if (b != null) { jsClick(driver, b); jadvalKut(driver); }
        WebElement s = visible(driver, By.cssSelector("main input[placeholder='Qidirish']"));
        if (s != null) { s.sendKeys(Keys.CONTROL + "a"); s.sendKeys(no); qidiruvKut(driver, no); }
        List<String> r = new ArrayList<>();
        for (WebElement tr : driver.findElements(By.cssSelector("main tbody tr")))
            try { String tx = clean(tr.getText()); if (tx.contains(no)) r.add(tx.replaceFirst("^[0-9]+ [|] ", "")); } catch (Exception ignore) {}
        System.out.println("OPERATSIYALAR " + no + ": " + r);
        return r;
    }

    void rasm(String name) {
        try {
            File dir = new File("target/bitim-holatlari");
            dir.mkdirs();
            File s = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            java.nio.file.Files.copy(s.toPath(), new File(dir, name.replaceAll("[^A-Za-z0-9_]", "_") + "_" + System.currentTimeMillis() + ".png").toPath());
        } catch (Exception ignore) {}
    }
}
