package uz.ebirja.tests;

import org.openqa.selenium.*;
import org.testng.Assert;
import org.testng.ITestResult;
import org.testng.annotations.*;
import uz.ebirja.base.BaseTest;
import uz.ebirja.base.Eimzo;
import uz.ebirja.config.Config;
import uz.ebirja.pages.BalansPage;
import uz.ebirja.pages.Jadval;

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
        // 'To'langan' bo'lgach shartnoma va to'lov cheki BIR MARTA yuklanadi (faqat shu bitimda)
        hujjatlarniYuklash("trader2", "Sotib olish", no, true);
        String miqdor = Config.getOr("bitim.lotHajmi", "1");
        ybhrYuborish(no, miqdor);
        ybhrTasdiqlash(no);
        String h = holatQanday("trader1", "Sotish", no);
        System.out.println("YBHR to'liq " + no + " holati: " + h);
        Assert.assertTrue(h.contains("Bajarildi"), no + ": to'liq YBHR tasdiqlangandan keyin holat 'Bajarildi' emas: " + h);
        hisobTekshir(no, X + ">" + X + " 900000 toliq tolovini blokirovkalash", X + ">" + S + " 1000000 yetkazilgan tovar uchun tolov");
        sotuvchiHisobTekshir(no, X + ">" + S + " 1000000 yetkazilgan tovar uchun tolov", S + ">" + S + " 100000 zakalat pulini blokirovkadan chiqarish");
    }

    @Test(priority = 22, description = "2-bitim: to'lov -> YBHR (yarmi) -> kelishuv bilan bekor -> 'Qisman bajarildi'")
    public void h2_ybhrYarimVaKelishuv() {
        String no = bitim("ybhrYarim", 1, "B26000257");
        if (!holatQanday("trader2", "Sotib olish", no).contains("To'langan")) tolov(no);
        String yarim = Config.getOr("bitim.yarimHajm", "0.5");
        ybhrYuborish(no, yarim);
        ybhrTasdiqlash(no);
        bekorQilish(no, "Kelishuv", null, "Qisman bajarildi");
        hisobTekshir(no, X + ">" + X + " 900000 toliq tolovini blokirovkalash", X + ">" + S + " 500000 yetkazilgan tovar uchun tolov",
                X + ">" + X + " 400000 toliq tolovini blokirovkadan chiqarish", X + ">" + X + " 100000 zakalat pulini blokirovkadan chiqarish");
        sotuvchiHisobTekshir(no, X + ">" + S + " 500000 yetkazilgan tovar uchun tolov", S + ">" + S + " 100000 zakalat pulini blokirovkadan chiqarish");
        bekorXatolarniTekshir(no);
    }

    // ------------------------------------------------------------------ BEKOR QILISH: 6 ta so'rov (har turdan 2 ta): 3 tasi rad etiladi, 3 tasi qabul qilinadi
    /** Rad etiladigan so'rovlar: bu bitimlar ochiq qoladi va keyin o'z stsenariysida (to'lov, muddat) davom etadi. */
    Map<String, String> radlar() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(bitim("ybhrToliq", 0, "B26000256"), "Kelishuv");
        m.put(bitim("tolovUzaytirish", 5, "B26000261"), "Sotuvchi jarimasi");
        m.put(bitim("yetkazishUzaytirish", 6, "B26000262"), "Xaridor jarimasi");
        return m;
    }

    Map<String, String> qabullar() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(bitim("kelishuv", 2, "B26000258"), "Kelishuv");
        m.put(bitim("xaridorJarima", 3, "B26000259"), "Xaridor jarimasi");
        m.put(bitim("sotuvchiJarima", 4, "B26000260"), "Sotuvchi jarimasi");
        return m;
    }

    @Test(priority = 11, description = "Bekor qilish: 6 ta so'rov (2 kelishuv, 2 xaridor jarimasi, 2 sotuvchi jarimasi); 3 tasi rad etiladi: holat va pul o'zgarmaydi")
    public void h0_bekorSorovlari() {
        Map<String, String> rad = radlar(), hammasi = new LinkedHashMap<>(qabullar());
        hammasi.putAll(rad);
        String birinchi = hammasi.keySet().iterator().next();
        bekorFormaSalbiy(birinchi);
        BigDecimal[] s0 = oldingiS(), x0 = oldingiX();
        // SOTUVCHI brauzeri: bitta sahifada ketma-ket 6 ta so'rov
        hammasi.forEach(this::bekorSorovi);
        // so'rov kutilayotganda sotuvchi ikkinchi marta so'rov yubora olmasligi kerak
        String m = menyuMatni(bitimQatori("Sotish", birinchi));
        System.out.println("BEKOR " + birinchi + " so'rov kutilayotganda sotuvchi menyusi: " + m);
        if (m.contains("Bitimni bekor qilish")) bekorXatolar.add(birinchi + ": so'rov kutilayotganda sotuvchi menyusida yana 'Bitimni bekor qilish' bor");
        // XARIDOR brauzeri: bitta sahifada 3 tasini rad etadi
        login("trader2");
        Map<String, String> kutilmoqda = sorovQatorlari(null);
        hammasi.forEach((no, tur) -> Assert.assertTrue(kutilmoqda.getOrDefault(no, "").contains(tur), no + ": '" + tur + "' so'rovi xaridorda 'Kutilmoqda' da yo'q: " + kutilmoqda.get(no)));
        String radBirinchi = rad.keySet().iterator().next();
        radSababsiz(radBirinchi);
        for (String no : rad.keySet()) sorovgaJavob("canceled-request", no, "Bekor qilish");
        Map<String, String> qoldi = sorovQatorlari(null), radTab = sorovQatorlari("Bekor qilingan");
        for (String no : qabullar().keySet()) Assert.assertTrue(qoldi.containsKey(no), no + ": rad etilmagan so'rov 'Kutilmoqda' dan yo'qoldi");
        rad.forEach((no, tur) -> {
            Assert.assertFalse(qoldi.containsKey(no), no + ": rad etilgan so'rov 'Kutilmoqda' da qoldi");
            Assert.assertTrue(radTab.getOrDefault(no, "").contains(tur), no + ": rad etilgan '" + tur + "' so'rovi 'Bekor qilingan' tabida yo'q: " + radTab.get(no));
            String h = norm(cut(bitimQatoriOchiq("Sotib olish", no).getText(), 200));
            Assert.assertTrue(h.contains("To'lov kutilmoqda"), no + ": so'rov rad etilgach bitim holati o'zgardi: " + h);
        });
        BigDecimal[] x1 = xaridorBalans(), s1 = sotuvchiBalans();
        pulFarqi(rad.keySet().toString(), "3 ta RAD ETILGAN", "xaridor", x0, x1, 0, 0);
        pulFarqi(rad.keySet().toString(), "3 ta RAD ETILGAN", "sotuvchi", s0, s1, 0, 0);
        saqla(s1, x1);
        System.out.println("BEKOR: 6 ta so'rov yuborildi, 3 tasi RAD ETILDI " + rad + ": holat o'zgarmadi, ortiqcha pul yechilmadi ✓");
        bekorXatolarniTekshir(birinchi);
    }

    @Test(priority = 12, dependsOnMethods = "h0_bekorSorovlari", description = "KELISHUV qabul qilinadi. Jarima yo'q, ikkala zakalat qaytadi")
    public void h3_bekorKelishuv() { bekorTasdiq(bitim("kelishuv", 2, "B26000258"), "Kelishuv", null, "Bekor qilingan"); }

    @Test(priority = 13, dependsOnMethods = "h0_bekorSorovlari", description = "XARIDOR JARIMASI qabul qilinadi. Xaridor zakalati sotuvchiga o'tadi, sotuvchining zakalati qaytadi")
    public void h4_bekorXaridorJarimasi() { bekorTasdiq(bitim("xaridorJarima", 3, "B26000259"), "Xaridor jarimasi", XARIDOR_STIR, "Bekor qilingan"); }

    @Test(priority = 14, dependsOnMethods = "h0_bekorSorovlari", description = "SOTUVCHI JARIMASI qabul qilinadi. Sotuvchi zakalati xaridorga o'tadi, xaridorning zakalati qaytadi")
    public void h5_bekorSotuvchiJarimasi() { bekorTasdiq(bitim("sotuvchiJarima", 4, "B26000260"), "Sotuvchi jarimasi", SOTUVCHI_STIR, "Bekor qilingan"); }

    @Test(priority = 15, dependsOnMethods = "h0_bekorSorovlari", description = "6 ta bitimning operatsiyalari ikkala tomon hisobida aynan mos (rad etilganlarida ortiqcha operatsiya yo'q); bekor bo'lgan bitim menyulari")
    public void h5_bekorYakuni() {
        Iterator<String> q = qabullar().keySet().iterator();
        String kel = q.next(), xj = q.next(), sj = q.next();
        // XARIDOR brauzeri
        for (String no : radlar().keySet()) hisobTekshir(no);
        hisobTekshir(kel, X + ">" + X + " 100000 zakalat pulini blokirovkadan chiqarish");
        hisobTekshir(xj, X + ">" + S + " 100000 xaridorning jarima");
        hisobTekshir(sj, S + ">" + X + " 100000 sotuvchining jarima", X + ">" + X + " 100000 zakalat pulini blokirovkadan chiqarish");
        tugaganMenyular("trader2", "Sotib olish");
        // SOTUVCHI brauzeri
        for (String no : radlar().keySet()) sotuvchiHisobTekshir(no);
        sotuvchiHisobTekshir(kel, S + ">" + S + " 100000 zakalat pulini blokirovkadan chiqarish");
        sotuvchiHisobTekshir(xj, X + ">" + S + " 100000 xaridorning jarima", S + ">" + S + " 100000 zakalat pulini blokirovkadan chiqarish");
        sotuvchiHisobTekshir(sj, S + ">" + X + " 100000 sotuvchining jarima");
        tugaganMenyular("trader1", "Sotish");
        saqla(null, null); // saqlangan balans faqat bekor qilish stsenariylari orasida ishlatiladi
        bekorXatolarniTekshir("bekor qilish");
    }

    void tugaganMenyular(String who, String tab) {
        login(who);
        ochiqTab.remove(driver); // holat yangi o'qilsin
        qabullar().forEach((no, tur) -> tugaganMenyu(no, tur, who, bitimQatoriOchiq(tab, no)));
    }

    @Test(priority = 26, description = "6-bitim: TO'LOV muddatini uzaytirish: qabul (sana + N ish kuni) va rad etish (sana o'zgarmaydi)")
    public void h6_tolovMuddatiniUzaytirish() {
        String no = bitim("tolovUzaytirish", 5, "B26000261");
        muddatUzaytirish(no, "To'lov muddatini uzaytirish", "payment-request", "To'lovning oxirgi muddati", true);
        muddatUzaytirish(no, "To'lov muddatini uzaytirish", "payment-request", "To'lovning oxirgi muddati", false);
        hisobTekshir(no); // muddat uzaytirishda pul harakati bo'lmasligi kerak
        sotuvchiHisobTekshir(no);
    }

    @Test(priority = 27, description = "7-bitim: to'lov -> YETKAZIB BERISH muddatini uzaytirish: qabul va rad etish")
    public void h7_yetkazibBerishMuddatiniUzaytirish() {
        String no = bitim("yetkazishUzaytirish", 6, "B26000262");
        if (!holatQanday("trader2", "Sotib olish", no).contains("To'langan")) tolov(no);
        muddatUzaytirish(no, "Yetkazib berish muddatini uzaytirish", "delivered-request", "Oxirgi yetkazib berish kuni", true);
        muddatUzaytirish(no, "Yetkazib berish muddatini uzaytirish", "delivered-request", "Oxirgi yetkazib berish kuni", false);
        hisobTekshir(no, X + ">" + X + " 900000 toliq tolovini blokirovkalash");
        sotuvchiHisobTekshir(no);
    }

    @Test(priority = 28, description = "8-bitim: TEGILMAYDI. Holati 'To'lov kutilmoqda' va hisobda faqat bitim tuzilgandagi operatsiyalar")
    public void h8_tegilmaganBitim() {
        String no = bitim("tegilmagan", 7, "");
        if (no.isEmpty()) throw new org.testng.SkipException("8-bitim berilmagan");
        String h = holatQanday("trader2", "Sotib olish", no);
        Assert.assertTrue(h.contains("To'lov kutilmoqda"), no + ": tegilmagan bitim 'To'lov kutilmoqda' emas: " + h);
        hisobTekshir(no);
        sotuvchiHisobTekshir(no);
        System.out.println("TEGILMAGAN BITIM: " + no + " ✓");
    }

    // ================================================================== XARIDOR HISOBI (HKKP -> Balans -> STIR ichi)
    static final String X = XARIDOR_STIR, S = SOTUVCHI_STIR, BIRJA_STIR = "310967190";
    static final long YIGIM = 800;

    BalansPage balansPage() { return new BalansPage(driver, base); }

    /** XARIDOR hisobining ichi (Balans -> STIR): shu bitim operatsiyalari kutilganlar bilan AYNAN solishtiriladi. */
    void hisobTekshir(String no, String... qoshimcha) {
        login("trader2");
        balansTabida(() -> { operatsiyalarMos("HISOB", X, no, () -> balansPage().hisobIchi(X, no), qoshimcha); return null; });
    }

    /** SOTUVCHI hisobi (trader1 -> Broker balansi): shu bitim operatsiyalari kutilganlar bilan AYNAN solishtiriladi. */
    void sotuvchiHisobTekshir(String no, String... qoshimcha) {
        login("trader1");
        balansTabida(() -> { operatsiyalarMos("SOTUVCHI HISOBI", S, no, () -> balansPage().brokerOperatsiyalari(no), qoshimcha); return null; });
    }

    /** Har brauzerda balans uchun alohida tab ochiq turadi: ish sahifasi (bitimlar, so'rovlar) qayta ochilmaydi. */
    private static final Map<WebDriver, String> BALANS_TAB = new HashMap<>();

    <T> T balansTabida(java.util.function.Supplier<T> amal) {
        String ish = driver.getWindowHandle(), tab = BALANS_TAB.get(driver);
        if (tab == null || !driver.getWindowHandles().contains(tab)) {
            driver.switchTo().newWindow(WindowType.TAB);
            BALANS_TAB.put(driver, driver.getWindowHandle());
        } else tabgaOtish(tab);
        try { return amal.get(); } finally { tabgaOtish(ish); }
    }

    private void tabgaOtish(String tab) {
        driver.switchTo().window(tab);
        ((org.openqa.selenium.chrome.ChromeDriver) driver).executeCdpCommand("Page.bringToFront", Map.of());
    }

    /**
     * Har bir operatsiya "to'lovchi>oluvchi summa maqsad". Ortiqcha yoki yetishmagan operatsiya - xato.
     * Bitim tuzilgandagi 4 ta operatsiya (zakalat bloki, 800 yig'im: hisoblandi/ushlab qolish/to'lash) har doim kutiladi.
     */
    void operatsiyalarMos(String nima, String stir, String no, java.util.function.Supplier<List<String>> oqish, String... qoshimcha) {
        List<String> kutilgan = new ArrayList<>(List.of(
                stir + ">" + stir + " 100000 zakalat pulini blokirovkalash",
                stir + ">" + BIRJA_STIR + " " + YIGIM + " vositachilik yigimini tolash",
                stir + ">" + stir + " " + YIGIM + " vositachilik yigimini ushlab qolish",
                stir + ">" + stir + " " + YIGIM + " vositachilik yigimini hisoblandi"));
        kutilgan.addAll(Arrays.asList(qoshimcha));
        List<String> qoldi = null, ortiqcha = null, topilgan = null;
        for (int u = 0; u < 3; u++) { // operatsiyalar biroz kechikib tushishi mumkin: 3 marta o'qiymiz
            if (u > 0) sleep(1500);
            topilgan = oqish.get();
            qoldi = new ArrayList<>(kutilgan);
            ortiqcha = new ArrayList<>(topilgan);
            juftlash(kutilgan, qoldi, ortiqcha);
            if (qoldi.isEmpty() && ortiqcha.isEmpty()) break;
        }
        boolean mos = qoldi.isEmpty() && ortiqcha.isEmpty();
        rasm(nima + "_" + no);
        System.out.println(nima + " " + stir + " " + no + ": " + topilgan.size() + " ta operatsiya, kutilgan " + kutilgan.size() + (mos ? " - hammasi mos ✓" : ""));
        topilgan.forEach(t -> System.out.println("     " + t));
        Assert.assertTrue(mos, no + ": " + nima + " mos emas. Yetishmaydi: " + qoldi + " | Ortiqcha: " + ortiqcha);
    }

    /** Kutilgan va topilgan operatsiyalarni juftlaydi: qoldi = yetishmaydiganlar, ortiqcha = kutilmaganlar. */
    static void juftlash(List<String> kutilgan, List<String> qoldi, List<String> ortiqcha) {
        for (String k : kutilgan) {
            String[] b = k.split(" ", 3);
            for (String t : ortiqcha) if (t.startsWith(b[0] + " " + b[1] + " ") && t.contains(b[2])) { ortiqcha.remove(t); qoldi.remove(k); break; }
        }
    }

    /** Sotuvchi (trader1) broker balansi: [barcha, erkin, band, qarzdorlik]. */
    BigDecimal[] sotuvchiBalans() {
        login("trader1");
        BigDecimal[] r = balansTabida(() -> balansPage().broker());
        Assert.assertNotNull(r, "Sotuvchining broker balansi o'qilmadi");
        System.out.println("BALANS (sotuvchi, broker) " + S + ": " + Arrays.toString(r));
        return r;
    }

    /** Xaridor balansi (trader2 dan). */
    BigDecimal[] xaridorBalans() {
        login("trader2");
        BigDecimal[] r = balansTabida(() -> balansPage().mijoz(XARIDOR_STIR));
        Assert.assertNotNull(r, "Xaridor balansi o'qilmadi");
        return r;
    }

    /**
     * "Shartnomani yuklab olish" oynasidan shartnoma (Shartnoma_uz.pdf) va to'lov chekini (contract_payment.pdf) haqiqatan yuklaydi.
     * Har bir fayl: diskka tushgan, bo'sh emas, haqiqiy PDF ("%PDF" bilan boshlanadi, "%%EOF" bilan tugaydi).
     * tolangan=false bo'lsa chek ko'rinmasligi kerak.
     */
    void hujjatlarniYuklash(String who, String tab, String no, boolean tolangan) {
        hujjatTekshirildi = true;
        try { hujjatlarniYuklashIchki(who, tab, no, tolangan); }
        catch (Throwable e) { hujjatXatolar.add(no + " " + who + ": " + cut(String.valueOf(e.getMessage()), 250)); System.out.println("YUKLASH XATO " + cut(String.valueOf(e.getMessage()), 250)); }
    }

    /** Hujjat yuklash xatolari: stsenariyni to'xtatmaydi, h9 da hammasi birga tekshiriladi. */
    List<String> hujjatXatolar = new ArrayList<>();
    boolean hujjatTekshirildi = false;

    @Test(priority = 29, description = "Shartnoma va to'lov chekini yuklab olish (bitta to'langan bitimda, bir marta)")
    public void h9_hujjatYuklash() {
        if (!hujjatTekshirildi) throw new org.testng.SkipException("Hujjat yuklanadigan bitim bo'lmadi");
        Assert.assertTrue(hujjatXatolar.isEmpty(), "Hujjat yuklanmadi: " + hujjatXatolar);
    }

    void hujjatlarniYuklashIchki(String who, String tab, String no, boolean tolangan) {
        login(who);
        String papka = "target/yuklangan/" + no + "_" + who + "_" + System.currentTimeMillis();
        menyu(bitimQatori(tab, no), "Shartnomani yuklab olish");
        Object ochildi = waitFor(() -> { WebElement d = dialog(); return d != null && d.getText().contains(".pdf") ? Boolean.TRUE : null; }, 15);
        rasm("yuklash_" + no + "_" + who);
        Assert.assertNotNull(ochildi, no + " " + who + ": yuklab olish oynasida fayl yo'q");
        String matn = clean(dialog().getText());
        System.out.println("YUKLASH " + no + " " + who + " oyna: " + cut(matn, 250));
        Assert.assertTrue(matn.contains("Shartnoma_"), no + " " + who + ": oynada shartnoma fayli yo'q: " + matn);
        Assert.assertEquals(matn.contains("contract_payment"), tolangan, no + " " + who + (tolangan ? ": to'langan, lekin to'lov cheki yo'q: " : ": to'lanmagan, lekin to'lov cheki bor: ") + matn);
        List<String> fayllar = new ArrayList<>(List.of("Shartnoma_"));
        if (tolangan) fayllar.add("contract_payment");
        for (String nom : fayllar) {
            // har fayl alohida papkaga: ikkalasi bir xil nom bilan tushsa ham aralashmaydi
            File dir = new File(papka + "/" + nom).getAbsoluteFile();
            dir.mkdirs();
            ((org.openqa.selenium.chrome.ChromeDriver) driver).executeCdpCommand("Browser.setDownloadBehavior", java.util.Map.of("behavior", "allow", "downloadPath", dir.getPath()));
            // fayl nomi turgan qatordagi ENG O'NGDAGI belgi (⬇)
            WebElement belgi = (WebElement) ((JavascriptExecutor) driver).executeScript(
                    "const els=[...arguments[0].querySelectorAll('*')].filter(e=>!e.children.length&&(e.innerText||'').includes(arguments[1])); if(!els.length) return null; const y=els[0].getBoundingClientRect();" +
                    "const c=[...arguments[0].querySelectorAll('svg')].filter(x=>{const r=x.getBoundingClientRect(); return r.width>0&&r.left>y.right&&Math.abs((r.top+r.height/2)-(y.top+y.height/2))<40;});" +
                    "c.sort((p,q)=>q.getBoundingClientRect().left-p.getBoundingClientRect().left); return c[0]||null;", dialog(), nom);
            Assert.assertNotNull(belgi, no + " " + who + ": '" + nom + "' yonida yuklash (⬇) belgisi topilmadi");
            File tushdi = null;
            Set<String> xabar = new LinkedHashSet<>();
            // server ba'zan vaqtincha 400 qaytaradi: xatoda bir marta qayta urinamiz (xato logda qoladi)
            for (int u = 0; u < 2 && tushdi == null; u++) {
                if (u > 0) {
                    System.out.println("YUKLASH " + no + " " + who + " '" + nom + "': sayt xabari " + xabar + ", qayta urinilmoqda");
                    toastlarYoqolsin();
                    xabar.clear();
                }
                new org.openqa.selenium.interactions.Actions(driver).moveToElement(belgi).click().perform();
                for (long end = System.currentTimeMillis() + 15_000; tushdi == null && System.currentTimeMillis() < end; sleep(200)) {
                    for (String x : toasts(driver)) if (!x.isBlank()) xabar.add(x);
                    File[] fs = dir.listFiles();
                    if (fs != null) for (File x : fs) if (!x.getName().endsWith(".crdownload") && !x.getName().endsWith(".tmp") && x.length() > 0) tushdi = x;
                    if (tushdi == null && serverRadEtdi(xabar)) break;
                }
            }
            if (tushdi == null) { // boshqa fayl ham tekshirilishi uchun to'xtamaymiz
                hujjatXatolar.add(no + " " + who + " '" + nom + "': yuklanmadi, sayt xabari " + xabar);
                System.out.println("YUKLASH " + no + " " + who + " '" + nom + "': YUKLANMADI, sayt xabari " + xabar);
                continue;
            }
            try {
                byte[] b = java.nio.file.Files.readAllBytes(tushdi.toPath());
                String bosh = new String(b, 0, Math.min(5, b.length), java.nio.charset.StandardCharsets.ISO_8859_1);
                String oxir = new String(b, Math.max(0, b.length - 1024), Math.min(1024, b.length), java.nio.charset.StandardCharsets.ISO_8859_1);
                boolean pdf = bosh.startsWith("%PDF") && oxir.contains("%%EOF") && b.length > 1000;
                System.out.println("YUKLASH " + no + " " + who + ": " + tushdi.getName() + ", " + b.length / 1024 + " KB" + (pdf ? ", haqiqiy PDF ✓" : " - PDF EMAS"));
                Assert.assertTrue(pdf, no + " " + who + ": " + tushdi.getName() + " butun PDF emas (" + b.length + " bayt, boshi '" + bosh + "')");
            } catch (java.io.IOException e) { throw new AssertionError("Fayl o'qilmadi: " + tushdi, e); }
        }
        barchasiniYuklash(who, tab, no, papka, fayllar.size());
    }

    static boolean serverRadEtdi(Set<String> xabar) {
        return xabar.stream().anyMatch(x -> x.contains("exception") || x.startsWith("4") || x.startsWith("5"));
    }

    /** Qayta urinishdan oldin: eski xato xabari yo'qolguncha (ko'pi bilan 8 s), keyin yana 2 s server tayyorlanishi uchun. */
    void toastlarYoqolsin() {
        waitFor(() -> toasts(driver).stream().allMatch(String::isBlank) ? Boolean.TRUE : null, 8);
        sleep(2000);
    }

    /** "Barchasini yuklab olish" tugmasi:hamma fayl (alohida PDF lar yoki bitta ZIP) diskka tushishi kerak. */
    void barchasiniYuklash(String who, String tab, String no, String papka, int kutilgan) {
        File dir = new File(papka + "/barchasi").getAbsoluteFile();
        dir.mkdirs();
        ((org.openqa.selenium.chrome.ChromeDriver) driver).executeCdpCommand("Browser.setDownloadBehavior", java.util.Map.of("behavior", "allow", "downloadPath", dir.getPath()));
        if (dialog() == null) { // oyna yopilgan bo'lsa qayta ochiladi
            menyu(bitimQatori(tab, no), "Shartnomani yuklab olish");
            waitFor(() -> { WebElement d = dialog(); return d != null && d.getText().contains(".pdf") ? Boolean.TRUE : null; }, 15);
        }
        WebElement d = dialog();
        WebElement btn = d == null ? null : d.findElements(By.xpath(".//*[self::button or @role='button' or self::a][contains(normalize-space(.),'Barchasini yuklab olish')]"))
                .stream().filter(WebElement::isDisplayed).findFirst().orElse(null);
        if (btn == null) {
            hujjatXatolar.add(no + " " + who + ": 'Barchasini yuklab olish' tugmasi topilmadi");
            System.out.println("YUKLASH " + no + " " + who + " 'Barchasini yuklab olish': TUGMA TOPILMADI");
            return;
        }
        List<File> tushgan = new ArrayList<>();
        Set<String> xabar = new LinkedHashSet<>();
        for (int u = 0; u < 2 && tushgan.isEmpty(); u++) {
            if (u > 0) {
                System.out.println("YUKLASH " + no + " " + who + " 'Barchasini yuklab olish': sayt xabari " + xabar + ", qayta urinilmoqda");
                toastlarYoqolsin();
                xabar.clear();
            }
            new org.openqa.selenium.interactions.Actions(driver).moveToElement(btn).click().perform();
            for (long end = System.currentTimeMillis() + 20_000; System.currentTimeMillis() < end; sleep(300)) {
                for (String x : toasts(driver)) if (!x.isBlank()) xabar.add(x);
                tushgan.clear();
                File[] fs = dir.listFiles();
                boolean chalaBor = false;
                if (fs != null) for (File x : fs) {
                    if (x.getName().endsWith(".crdownload") || x.getName().endsWith(".tmp")) chalaBor = true;
                    else if (x.length() > 0) tushgan.add(x);
                }
                boolean zip = tushgan.stream().anyMatch(x -> x.getName().toLowerCase().endsWith(".zip"));
                if (!chalaBor && (zip || tushgan.size() >= kutilgan)) break;
                if (tushgan.isEmpty() && serverRadEtdi(xabar)) break;
            }
        }
        rasm("yuklash_barchasi_" + no + "_" + who);
        if (tushgan.isEmpty()) {
            hujjatXatolar.add(no + " " + who + " 'Barchasini yuklab olish': yuklanmadi, sayt xabari " + xabar);
            System.out.println("YUKLASH " + no + " " + who + " 'Barchasini yuklab olish': YUKLANMADI, sayt xabari " + xabar);
            return;
        }
        for (File f : tushgan) {
            try {
                byte[] b = java.nio.file.Files.readAllBytes(f.toPath());
                String bosh = new String(b, 0, Math.min(5, b.length), java.nio.charset.StandardCharsets.ISO_8859_1);
                if (bosh.startsWith("PK")) { // ZIP: ichidagi fayllar sanaladi
                    List<String> ichida = new ArrayList<>();
                    try (java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(b))) {
                        for (java.util.zip.ZipEntry e; (e = z.getNextEntry()) != null; ) ichida.add(e.getName());
                    }
                    System.out.println("YUKLASH " + no + " " + who + " 'Barchasini yuklab olish': " + f.getName() + " (ZIP, " + b.length / 1024 + " KB), ichida: " + ichida
                            + (ichida.size() >= kutilgan ? " ✓" : ""));
                    if (ichida.size() < kutilgan) hujjatXatolar.add(no + " " + who + " 'Barchasini yuklab olish': ZIP ichida " + ichida.size() + " ta fayl, kutilgan " + kutilgan + ": " + ichida);
                } else {
                    String oxir = new String(b, Math.max(0, b.length - 1024), Math.min(1024, b.length), java.nio.charset.StandardCharsets.ISO_8859_1);
                    boolean pdf = bosh.startsWith("%PDF") && oxir.contains("%%EOF") && b.length > 1000;
                    System.out.println("YUKLASH " + no + " " + who + " 'Barchasini yuklab olish': " + f.getName() + ", " + b.length / 1024 + " KB" + (pdf ? ", haqiqiy PDF ✓" : " - PDF EMAS"));
                    if (!pdf) hujjatXatolar.add(no + " " + who + " 'Barchasini yuklab olish': " + f.getName() + " butun PDF emas");
                }
            } catch (java.io.IOException e) { hujjatXatolar.add(no + " " + who + ": " + f.getName() + " o'qilmadi: " + e.getMessage()); }
        }
        boolean zipBor = tushgan.stream().anyMatch(x -> x.getName().toLowerCase().endsWith(".zip"));
        if (!zipBor && tushgan.size() < kutilgan) hujjatXatolar.add(no + " " + who + " 'Barchasini yuklab olish': " + tushgan.size() + " ta fayl tushdi, kutilgan " + kutilgan);
    }

    /** Faqat hujjat yuklash (alohida): -Dbitim.yuklash=B... ; kim: -Dyuklash.kim=trader2 (xaridor, sukut) yoki trader1 (sotuvchi). */
    @Test(priority = 31, description = "Faqat hujjat yuklash: alohida fayllar va 'Barchasini yuklab olish'")
    public void faqatHujjatYuklash() {
        String no = System.getProperty("bitim.yuklash");
        if (no == null) throw new org.testng.SkipException("Faqat qo'lda: -Dbitim.yuklash=B... bilan");
        String who = System.getProperty("yuklash.kim", "trader2");
        hujjatlarniYuklash(who, who.equals("trader2") ? "Sotib olish" : "Sotish", no, true);
        Assert.assertTrue(hujjatXatolar.isEmpty(), "Hujjat yuklanmadi: " + hujjatXatolar);
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
                for (String op : balansPage().brokerQatorlari(no.trim())) System.out.println("   " + op);
            }
        }
    }

    // ================================================================== AMALLAR

    void tolov(String no) {
        login("trader2");
        BigDecimal[] oldin = balansPage().mijoz(XARIDOR_STIR);
        WebElement row = bitimQatori("Sotib olish", no);
        Assert.assertTrue(norm(row.getText()).contains("To'lov kutilmoqda"), no + " 'To'lov kutilmoqda' holatida emas: " + cut(row.getText(), 200));
        menyu(row, "To'lov qilish");
        dialogTugma("To'lov qilish");
        Eimzo.typePassword(10);
        if (xabarKut().stream().anyMatch(x -> x.contains("Parol"))) { // E-IMZO parolni qabul qilmasa bir marta qayta
            System.out.println("TO'LOV " + no + ": parol xatosi, qayta urinish");
            menyu(bitimQatori("Sotib olish", no), "To'lov qilish");
            dialogTugma("To'lov qilish");
            Eimzo.typePassword(10);
            xabarKut();
        }
        System.out.println("TO'LOV " + no + " xabarlar: " + toasts(driver));
        // holat jadvalda biroz kechikib yangilanadi: 15 s gacha kutamiz
        String h = "";
        for (int u = 0; u < 7 && !h.contains("To'langan"); u++) { if (u > 0) sleep(1500); h = holatQanday(null, "Sotib olish", no); }
        Assert.assertTrue(h.contains("To'langan"), no + ": to'lovdan keyin holat 'To'langan' emas: " + h);
        BigDecimal[] keyin = balansPage().mijoz(XARIDOR_STIR);
        System.out.println("TO'LOV " + no + " xaridor balansi (barcha/erkin/band/qarz): " + Arrays.toString(oldin) + " -> " + Arrays.toString(keyin));
        // to'lov = bitim summasi - oldindan band qilingan zakalat (1 000 000 - 100 000 = 900 000)
        if (oldin != null && keyin != null) {
            BigDecimal kamaydi = oldin[1].subtract(keyin[1]);
            System.out.println("TO'LOV " + no + ": erkin mablag' " + kamaydi + " ga kamaydi (kutilgan " + TOLOV + ")");
            Assert.assertEquals(kamaydi.longValue(), TOLOV, no + ": to'lovdan keyin xaridorning erkin mablag'i kutilgandek kamaymadi");
        }
    }

    /** Bekor qilishning salbiy holatlarida topilgan xatolar: stsenariy oxirida hammasi birga tekshiriladi. */
    List<String> bekorXatolar = new ArrayList<>();

    void bekorXatolarniTekshir(String no) {
        List<String> x = new ArrayList<>(bekorXatolar);
        bekorXatolar.clear();
        Assert.assertTrue(x.isEmpty(), no + ": bekor qilishning salbiy holatlari: " + x);
    }

    /** Oldingi bekor qilishdan keyingi balans: shu orada boshqa amal bo'lmaydi, shuning uchun keyingi stsenariyning boshlang'ich balansi bo'ladi. Bir marta ishlatiladi. */
    private BigDecimal[] saqlanganS, saqlanganX;

    void saqla(BigDecimal[] s, BigDecimal[] x) { saqlanganS = s; saqlanganX = x; }

    BigDecimal[] oldingiS() { BigDecimal[] r = saqlanganS; saqlanganS = null; return r != null ? r : sotuvchiBalans(); }

    BigDecimal[] oldingiX() { BigDecimal[] r = saqlanganX; saqlanganX = null; return r != null ? r : xaridorBalans(); }

    /** Imzodan keyingi xabar (toast) chiqishini kutadi (ko'pi bilan 5 s). */
    List<String> xabarKut() {
        waitFor(() -> toasts(driver).stream().anyMatch(x -> !x.isBlank() && !x.startsWith("Loading")) ? Boolean.TRUE : null, 5);
        return toasts(driver);
    }

    /** "Bitimni bekor qilish" formasi: to'ldirilmagan holatlar (bitta oynada). E-IMZO imzosi to'sib qo'yiladi, so'rov yuborilmaydi. */
    void bekorFormaSalbiy(String no) {
        login("trader1");
        menyu(bitimQatori("Sotish", no), "Bitimni bekor qilish");
        eimzoTosish(driver);
        By sabab = By.cssSelector("textarea[name=reason]");
        bekorFormaRad(no, "tur ham, sabab ham bo'sh", 2);
        dialogEl(sabab).sendKeys("Avtotest");
        bekorFormaRad(no, "faqat sabab", 1);
        dialogEl(sabab).sendKeys(Keys.CONTROL + "a");
        dialogEl(sabab).sendKeys(Keys.DELETE);
        dialogSelect("Kelishuv");
        bekorFormaRad(no, "faqat tur", 1);
        dialogEl(sabab).sendKeys("   ");
        bekorFormaRad(no, "sabab faqat probel", 1);
        ochiqTab.remove(driver); // keyingi safar sahifa qayta ochiladi (imzo to'sig'i olinadi)
        // so'rov yuborilmaganini tekshiramiz
        login("trader2");
        if (sorovQatorlari(null).containsKey(no)) bekorXatolar.add("salbiy holatlardan keyin 'Kutilmoqda' da so'rov paydo bo'ldi");
        login("trader1");
    }

    /** Formani yuboradi: aynan shuncha "Majburiy maydon" yozuvi chiqishi va imzo so'ralmasligi kerak. */
    void bekorFormaRad(String no, String holat, int majburiySoni) {
        long oldin = eimzoUrinishlari(driver);
        dialogTugma("Tasdiqlash");
        java.util.function.IntSupplier soni = () -> dialog() == null ? -1 : (dialog().getText() + " ").split("Majburiy maydon", -1).length - 1;
        sleep(400);
        waitFor(() -> eimzoUrinishlari(driver) > oldin || soni.getAsInt() == majburiySoni ? Boolean.TRUE : null, 4);
        boolean rad = eimzoUrinishlari(driver) == oldin && soni.getAsInt() == majburiySoni;
        System.out.println("BEKOR-SALBIY " + no + " (" + holat + "): " + (rad ? "rad etildi ✓" : "RAD ETILMADI, 'Majburiy maydon' " + soni.getAsInt() + "/" + majburiySoni + ", imzo so'rovlari " + (eimzoUrinishlari(driver) - oldin)));
        if (!rad) bekorXatolar.add("bekor qilish formasi qabul qilindi: " + holat);
    }

    /** "Bekor qilish so'rovi" sahifasidagi (xaridor) qatorlar: bitim raqami -> qator matni (birinchi sahifa, yangilari tepada). tab=null - Kutilmoqda. */
    Map<String, String> sorovQatorlari(String tab) {
        driver.get(base + "/uz/trader/contracts/canceled-request");
        jadvalKut(driver);
        if (tab != null) Assert.assertTrue(Jadval.tab(driver, tab), "'" + tab + "' tabi yo'q");
        sleep(700);
        Map<String, String> r = new LinkedHashMap<>();
        for (WebElement row : driver.findElements(Jadval.QATORLAR)) {
            String t = clean(row.getText());
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("[AB]\\d{8}").matcher(t);
            if (m.find()) r.putIfAbsent(m.group(), t);
        }
        return r;
    }

    /** Sotuvchi "Bitimni bekor qilish" so'rovini yuboradi va E-IMZO bilan imzolaydi. */
    List<String> bekorSorovi(String no, String tur) {
        List<String> x = bekorSoroviBirMarta(no, tur);
        if (parolXatosi(x)) { System.out.println("BEKOR " + no + ": E-IMZO paroli qabul qilinmadi, so'rov qayta yuboriladi"); sleep(3000); x = bekorSoroviBirMarta(no, tur); }
        Assert.assertFalse(parolXatosi(x), no + ": bekor qilish so'rovi imzolanmadi: " + x);
        return x;
    }

    /** E-IMZO parolni qabul qilmadi (hech narsa yuborilmagan, qayta urinish xavfsiz). */
    static boolean parolXatosi(List<String> xabar) { return xabar.stream().anyMatch(t -> t.contains("Parol") || t.contains("tasdiqlanmagan")); }

    List<String> bekorSoroviBirMarta(String no, String tur) {
        login("trader1");
        menyu(bitimQatoriOchiq("Sotish", no), "Bitimni bekor qilish");
        dialogSelect(tur);
        dialogEl(By.cssSelector("textarea[name=reason]")).sendKeys("Avtotest: " + tur);
        rasm("bekor_" + no + "_forma");
        dialogTugma("Tasdiqlash");
        Eimzo.typePassword(10);
        List<String> x = xabarKut();
        System.out.println("BEKOR (" + tur + ") " + no + " so'rov yuborildi. Xabarlar: " + x);
        return x;
    }

    /** Qator ⋮ menyusidagi bandlar. */
    String menyuMatni(WebElement row) {
        List<WebElement> tds = row.findElements(By.cssSelector("td"));
        jsClick(driver, tds.get(tds.size() - 1).findElement(By.cssSelector("button")));
        WebElement it = waitFor(() -> lastVisible(driver, By.xpath("//*[normalize-space(text())='Shartnomani yuklab olish']")), 5);
        Assert.assertNotNull(it, "⋮ menyusi ochilmadi");
        return norm(clean((String) ((JavascriptExecutor) driver).executeScript("return arguments[0].parentElement.parentElement.innerText", it)));
    }

    static long[] farq(BigDecimal[] a, BigDecimal[] b) { return new long[]{b[0].subtract(a[0]).longValueExact(), b[1].subtract(a[1]).longValueExact()}; }

    void pulFarqi(String no, String nima, String kim, BigDecimal[] oldin, BigDecimal[] keyin, long kBarcha, long kErkin) {
        long[] f = farq(oldin, keyin);
        boolean ok = f[0] == kBarcha && f[1] == kErkin;
        System.out.println("BALANS FARQI (" + nima + ") " + no + " " + kim + ": barcha " + f[0] + " (kutilgan " + kBarcha + "), erkin " + f[1] + " (kutilgan " + kErkin + ")" + (ok ? " ✓" : ""));
        Assert.assertTrue(ok, no + " " + nima + ": " + kim + " balansi noto'g'ri o'zgardi: barcha " + f[0] + "/" + kBarcha + ", erkin " + f[1] + "/" + kErkin);
    }

    /** Sotuvchi so'rov yuboradi va xaridor darhol TASDIQLAYDI (bitta bitim). */
    void bekorQilish(String no, String tur, String jarimaStir, String kutilganHolat) {
        // boshlang'ich balans YANGI o'qiladi: oldingi stsenariydan keyin to'lov va YBHR bo'lgan
        saqla(sotuvchiBalans(), xaridorBalans());
        bekorSorovi(no, tur);
        bekorTasdiq(no, tur, jarimaStir, kutilganHolat);
    }

    /**
     * Xaridor kutilayotgan bekor qilish so'rovini TASDIQLAYDI; bitim holati va pul IKKALA tomonda tekshiriladi.
     * jarimaStir: jarimaga tushgan tomon (null = kelishuv: hech kimdan jarima olinmaydi).
     */
    void bekorTasdiq(String no, String tur, String jarimaStir, String kutilganHolat) {
        BigDecimal[] s0 = oldingiS(), x0 = oldingiX();
        login("trader2");
        sorovgaJavob("canceled-request", no, "Tasdiqlash");
        boolean xJarima = XARIDOR_STIR.equals(jarimaStir);
        BigDecimal[] x1 = xaridorBalans(), s1 = sotuvchiBalans();
        // XARIDOR: kelishuv - zakalat (va to'langan qoldiq) qaytadi; xaridor jarimasi - 100 000 ketadi; sotuvchi jarimasi - 100 000 keladi va zakalat qaytadi
        pulFarqi(no, tur, "xaridor", x0, x1, jarimaStir == null ? 0 : xJarima ? -JARIMA : JARIMA,
                jarimaStir == null ? (kutilganHolat.startsWith("Qisman") ? 500_000 : JARIMA) : xJarima ? 0 : 2 * JARIMA);
        // SOTUVCHI: kelishuv - zakalat qaytadi; xaridor jarimasi - 100 000 keladi va zakalat qaytadi; sotuvchi jarimasi - 100 000 (zakalat) ketadi
        pulFarqi(no, tur, "sotuvchi", s0, s1, jarimaStir == null ? 0 : xJarima ? JARIMA : -JARIMA,
                jarimaStir == null ? JARIMA : xJarima ? 2 * JARIMA : 0);
        saqla(s1, x1);
        // holat sotuvchi brauzeridagi bitimlar sahifasidan o'qiladi (xaridorda so'rovlar sahifasi ochiq qoladi)
        login("trader1");
        ochiqTab.remove(driver);
        String h = norm(cut(bitimQatori("Sotish", no).getText(), 200));
        System.out.println("BEKOR (" + tur + ") " + no + " holati: " + h);
        Assert.assertTrue(h.contains(kutilganHolat), no + ": bekor qilingandan keyin holat '" + kutilganHolat + "' emas: " + h);
    }

    /** Bekor bo'lgan bitimda to'lash, qayta bekor qilish va muddat uzaytirish bo'lmasligi kerak. */
    void tugaganMenyu(String no, String tur, String kim, WebElement row) {
        String m = menyuMatni(row);
        System.out.println("BEKOR (" + tur + ") " + no + " tugagan bitim menyusi, " + kim + ": " + m);
        for (String band : new String[]{"To'lov qilish", "Bitimni bekor qilish", "muddatini uzaytirish"})
            if (m.contains(band)) bekorXatolar.add(no + " (" + tur + "): " + kim + " menyusida hali ham '" + band + "' bor");
    }

    /** Bitim holati yozuvi ("Bitim holati" ustuni). */
    String bitimHolati(String tab, String no) {
        String h = ustun(bitimQatori(tab, no), "bitim holati");
        Assert.assertTrue(!h.isBlank(), no + ": 'Bitim holati' ustuni o'qilmadi");
        return h;
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
        List<String> x = null;
        for (int u = 0; u < 2; u++) { // E-IMZO paroli ba'zan "noto'g'ri" deb qaytadi: so'rov bir marta qayta yuboriladi
            if (u > 0) { System.out.println(band.toUpperCase() + " " + no + ": E-IMZO paroli qabul qilinmadi, so'rov qayta yuboriladi"); sleep(3000); row = bitimQatori("Sotish", no); }
            menyu(row, band);
            dialogEl(By.name("extensionDay")).sendKeys(String.valueOf(n));
            dialogEl(By.cssSelector("textarea[name=description]")).sendKeys("Avtotest: " + band + (qabul ? "" : " (rad etiladi)"));
            dialogTugma("Saqlash");
            Eimzo.typePassword(10);
            x = xabarKut();
            System.out.println(band.toUpperCase() + " " + no + ": " + n + " ish kuni so'raldi. Xabarlar: " + x);
            if (!parolXatosi(x)) break;
        }
        Assert.assertFalse(parolXatosi(x), no + ": '" + band + "' so'rovi imzolanmadi: " + x);
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
        String kun = String.valueOf(java.time.LocalDate.now().getDayOfMonth());
        WebElement day = waitFor(() -> lastVisible(driver,By.xpath("//*[contains(@class,'calendar') or contains(@class,'picker') or contains(@class,'absolute')]//*[normalize-space(text())='" + kun + "']")), 3);
        Assert.assertNotNull(day, "YBHR: kalendarda bugungi kun topilmadi");
        jsClick(driver, day);
        sleep(600);
        dialogEl(By.name("amount")).sendKeys(miqdor);
        // hujjat (xlsx) "Ro'yxatga qo'shish" dan OLDIN yuklanadi (aks holda "Kamida bitta fayl kiritilishi kerak")
        for (WebElement f : driver.findElements(By.cssSelector("input[type=file]")))
            try { f.sendKeys(uz.ebirja.config.Config.resurs("test-ybhr.xlsx").getAbsolutePath()); } catch (Exception ignore) {}
        sleep(2000);
        // "Ro'yxatga qo'shish": haqiqiy bosish va ro'yxatga qator qo'shilganini tekshirish
        Object qoshildi = null;
        for (int u = 0; u < 3 && qoshildi == null; u++) {
            realClick(driver, lastVisible(driver, By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button[contains(normalize-space(.),'yxatga qo')]")));
            qoshildi = waitFor(() -> dialog().findElements(By.cssSelector("tbody tr td")).size() > 1 ? Boolean.TRUE : null, 4);
        }
        Assert.assertNotNull(qoshildi, "YBHR: 'Ro'yxatga qo'shish' dan keyin ro'yxatga qator qo'shilmadi");
        rasm("ybhr_" + no + "_forma");
        List<String> xabar = new ArrayList<>();
        for (int u = 0; u < 2; u++) {
            dialogTugma("Yuborish");
            Eimzo.typePassword(10);
            xabar = xabarKut();
            System.out.println("YBHR " + no + " (" + miqdor + ") yuborildi. Xabarlar: " + xabar);
            if (xabar.stream().noneMatch(x -> x.contains("Parol") || x.contains("tasdiqlanmagan"))) break;
            System.out.println("YBHR " + no + ": E-IMZO paroli noto'g'ri tushdi, qayta yuboriladi");
            sleep(3000); // xabarlar yo'qolsin
        }
        Assert.assertTrue(xabar.stream().noneMatch(x -> x.contains("Parol") || x.contains("tasdiqlanmagan")), "YBHR " + no + " imzolanmadi: " + xabar);
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
        WebElement ok = waitFor(() -> lastVisible(driver, By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button[normalize-space(.)='Tasdiqlash' or normalize-space(.)='Ha']")), 3);
        if (ok != null) jsClick(driver, ok);
        Eimzo.typePassword(10);
        System.out.println("YBHR " + no + " xaridor tasdiqladi. Xabarlar: " + xabarKut());
    }

    /** Xaridor bekor qilish so'rovini SABABSIZ rad etmoqchi bo'ladi: sayt qabul qilmasligi kerak (imzo to'sib qo'yilgan). */
    void radSababsiz(String no) {
        driver.get(base + "/uz/trader/contracts/canceled-request");
        jadvalKut(driver);
        WebElement s = visible(driver, By.cssSelector("main input[placeholder='Qidirish']"));
        if (s != null) { s.sendKeys(no); qidiruvKut(driver, no); }
        WebElement row = waitFor(() -> rowWith(no), 20);
        Assert.assertNotNull(row, no + ": bekor qilish so'rovi 'Kutilmoqda' da yo'q");
        jsClick(driver, sorovTugmasi(row, "canceled-request", no, "Bekor qilish"));
        waitFor(this::dialog, 3);
        sleep(300);
        eimzoTosish(driver);
        dialogTugma("Yuborish");
        waitFor(() -> eimzoUrinishlari(driver) > 0 || (dialog() != null && dialog().getText().contains("Majburiy")) ? Boolean.TRUE : null, 5);
        boolean rad = eimzoUrinishlari(driver) == 0 && dialog() != null && dialog().getText().contains("Majburiy");
        System.out.println("BEKOR-SALBIY " + no + " (so'rovni sababsiz rad etish): " + (rad ? "qabul qilinmadi ✓" : "QABUL QILINDI, imzo so'rovlari " + eimzoUrinishlari(driver)) + " " + toasts(driver));
        if (!rad) bekorXatolar.add("xaridor so'rovni sababsiz rad eta oldi (imzo so'raldi)");
        driver.navigate().refresh(); // imzo to'sig'i olinadi
        jadvalKut(driver);
    }

    /** So'rov qatoridagi "Tasdiqlash" / "Bekor qilish" tugmasi (matnli yoki shu nomli ustundagi ikonka). */
    WebElement sorovTugmasi(WebElement row, String sahifa, String no, String tugma) {
        WebElement btn = null;
        for (WebElement b : row.findElements(By.cssSelector("button")))
            try { if (norm(b.getText()).contains(tugma) || tugma.equals(b.getAttribute("title"))) btn = b; } catch (Exception ignore) {}
        if (btn == null) {
            List<String> heads = new ArrayList<>();
            for (WebElement th : driver.findElements(By.cssSelector("main thead th"))) heads.add(norm(th.getText()));
            int idx = heads.indexOf(tugma);
            List<WebElement> tds = row.findElements(By.cssSelector("td"));
            if (idx >= 0 && idx < tds.size()) btn = tds.get(idx).findElements(By.cssSelector("button, svg")).stream().findFirst().orElse(null);
        }
        Assert.assertNotNull(btn, sahifa + ": " + no + " qatorida '" + tugma + "' tugmasi topilmadi");
        return btn;
    }

    /** So'rovlar sahifasida (Kutilmoqda) bitimni topib "Tasdiqlash" yoki "Bekor qilish" (rad etish) bosadi. */
    void sorovgaJavob(String sahifa, String no, String tugma) {
        List<String> x = sorovgaJavobBirMarta(sahifa, no, tugma);
        if (parolXatosi(x)) { System.out.println(sahifa + " " + no + ": E-IMZO paroli qabul qilinmadi, qayta"); sleep(3000); x = sorovgaJavobBirMarta(sahifa, no, tugma); }
        Assert.assertFalse(parolXatosi(x), no + ": so'rovga javob imzolanmadi: " + x);
    }

    List<String> sorovgaJavobBirMarta(String sahifa, String no, String tugma) {
        List<String> xabar;
        // so'rovlar sahifasi ('Kutilmoqda') allaqachon ochiq bo'lsa, qayta ochilmaydi
        String u = driver.getCurrentUrl();
        if (!u.contains("/contracts/" + sahifa) || u.contains("state=")) { driver.get(base + "/uz/trader/contracts/" + sahifa); jadvalKut(driver); }
        Jadval.qidir(driver, no);
        WebElement row = waitFor(() -> rowWith(no), 4);
        if (row == null) {
            driver.get(base + "/uz/trader/contracts/" + sahifa);
            jadvalKut(driver);
            Jadval.qidir(driver, no);
            row = waitFor(() -> rowWith(no), 30);
        }
        Assert.assertNotNull(row, sahifa + ": " + no + " so'rovi 'Kutilmoqda' ro'yxatida yo'q");
        System.out.println(sahifa + " qatori: " + cut(row.getText(), 300));
        rasm(sahifa + "_" + no);
        WebElement btn = sorovTugmasi(row, sahifa, no, tugma);
        jsClick(driver, btn);
        waitFor(this::dialog, 3);
        sleep(300);
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
        System.out.println(sahifa + ": " + no + " '" + tugma + "' qilindi. Xabarlar: " + (xabar = xabarKut()));
        return xabar;
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

    static String norm(String s) { return Jadval.norm(s); }

    WebElement rowWith(String text) { return Jadval.qator(driver, text); }

    /** Brauzerda qaysi bitimlar tabi ochiq turibdi. */
    private final Map<WebDriver, String> ochiqTab = new HashMap<>();

    /** bitimQatori kabi, lekin bitimlar sahifasi shu tabda ochiq turgan bo'lsa qayta ochmaydi, faqat qidiradi. */
    WebElement bitimQatoriOchiq(String tab, String no) {
        if (tab.equals(ochiqTab.get(driver)) && driver.getCurrentUrl().matches(".*/contracts/(client|brokers)-contracts.*")) {
            Jadval.qidir(driver, no);
            WebElement r = waitFor(() -> rowWith(no), 5);
            if (r != null) return r;
        }
        return bitimQatori(tab, no);
    }

    WebElement bitimQatori(String tab, String no) {
        // xaridor mijoz nomidan (auction.buyerClient) bo'lsa, trader2 da bitim "Mijozimning shartnomalari" da
        boolean mijoz = tab.equals("Sotib olish") && !Config.getOr("auction.buyerClient", "").isEmpty();
        // sahifa bo'sh ochilib qolsa (sayt sekin/javob bermasa) bir marta qayta ochiladi
        WebElement t = null;
        for (int u = 0; u < 2 && t == null; u++) {
            if (u > 0) { System.out.println("DIQQAT: shartnomalar sahifasi ochilmadi, qayta ochiladi: " + driver.getCurrentUrl()); rasm("sahifa_ochilmadi"); }
            driver.get(base + (mijoz ? "/uz/trader/contracts/client-contracts" : CONTRACTS));
            jadvalKut(driver);
            t = waitFor(() -> visible(driver, By.xpath("//main//*[normalize-space(text())='" + tab + "']")), 20);
        }
        Assert.assertNotNull(t, "Shartnomalar sahifasi ochilmadi: " + driver.getCurrentUrl());
        jsClick(driver, t);
        jadvalKut(driver);
        Jadval.qidir(driver, no);
        WebElement r = waitFor(() -> rowWith(no), 30);
        ochiqTab.put(driver, tab);
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
        WebElement it = waitFor(() -> lastVisible(driver, By.xpath("//*[normalize-space(text())=\"" + band + "\"]")), 4);
        Assert.assertNotNull(it, "Menyuda '" + band + "' yo'q");
        jsClick(driver, it);
        waitFor(this::dialog, 5);
        sleep(400);
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
        WebElement op = waitFor(() -> lastVisible(driver, By.xpath("//*[contains(@id,'-option-')][normalize-space(.)=\"" + option + "\"]")), 4);
        Assert.assertNotNull(op, "Ro'yxatda '" + option + "' yo'q");
        jsClick(driver, op);
        sleep(300);
    }

    void dialogTugma(String text) {
        WebElement b = waitFor(() -> lastVisible(driver, By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button[normalize-space(.)=\"" + text + "\"]")), 10);
        Assert.assertNotNull(b, "Oynada '" + text + "' tugmasi yo'q");
        jsClick(driver, b);
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
