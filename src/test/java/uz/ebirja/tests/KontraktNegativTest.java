package uz.ebirja.tests;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.testng.Assert;
import org.testng.ITestResult;
import org.testng.annotations.*;
import uz.ebirja.base.BaseTest;
import uz.ebirja.base.Eimzo;
import uz.ebirja.config.Config;
import uz.ebirja.pages.KontraktForma;

import java.io.File;
import java.util.List;

import static uz.ebirja.base.Ui.*;

/**
 * Kontrakt yaratish formasi: SALBIY (negativ) holatlar. Treyder 1 nomidan.
 * Har bir holatda kontrakt YUBORILMASLIGI kerak. Agar sayt formani qabul qilib, E-IMZO imzo so'rasa —
 * test parol yozmasdan oynani yopadi va xato deb belgilaydi (ya'ni noto'g'ri kontrakt yaratilmaydi).
 */
public class KontraktNegativTest extends BaseTest {
    static final String PRODUCT = Config.getOr("auction.product", "Асбестовый шнур");
    String base;
    KontraktForma forma;

    @BeforeClass
    public void kirish() {
        base = Config.traderUrl();
        kim("trader1");
        forma = new KontraktForma(driver, PRODUCT);
    }

    @AfterClass(alwaysRun = true)
    public void chiqish() { closeBrowser(); } // doimiy sessiya yopilmaydi

    @AfterMethod(alwaysRun = true)
    public void xatoRasmi(ITestResult r) {
        if (r.getStatus() != ITestResult.FAILURE || driver == null) return;
        try {
            File dir = new File("target/xato-rasmlar");
            dir.mkdirs();
            File shot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            File dst = new File(dir, "KontraktNegativ_" + r.getMethod().getMethodName() + "_" + System.currentTimeMillis() + ".png");
            java.nio.file.Files.copy(shot.toPath(), dst.toPath());
            System.out.println("XATO RASMI: " + dst.getAbsolutePath());
        } catch (Exception ignore) {}
    }

    /** To'g'ri to'ldirilgan forma (bir marta to'ldiriladi, keyingi holatlar shu formada tekshiriladi). */
    boolean toldirilgan = false;

    void tayyorForma() {
        if (toldirilgan && driver.getCurrentUrl().contains("/create")) return;
        forma.ochish(base).togriToldirish(true);
        toldirilgan = true;
    }

    /** Maydonni to'g'ri qiymatiga qaytaradi (keyingi holat uchun). */
    void qaytar(String name) { forma.set(name, KontraktForma.TOGRI.get(name)); }

    /** "Yuborish" bosiladi va kontrakt yuborilmaganini tekshiradi. */
    void radEtilishiKerak(String holat) {
        forma.eimzoImzoniTosish(); // xavfsizlik: imzo hech qachon qo'yilmaydi
        long oldin = forma.eimzoUrinishlari();
        forma.yuborishniBosish();
        waitFor(() -> forma.eimzoUrinishlari() > oldin ? Boolean.TRUE : null, 4);
        boolean oyna = Eimzo.cancelIfShown(1); // to'siq ishlamagan bo'lsa ham parol yozilmaydi
        long urinish = forma.eimzoUrinishlari() - oldin;
        if (urinish > 0) toldirilgan = false; // forma imzo kutish holatida qoldi -> keyingi holatda qayta ochiladi
        List<String> x = forma.xatolar();
        System.out.println(holat + " -> xabarlar: " + x + " | E-IMZO imzo so'rovlari: " + urinish + " " + forma.eimzoSorovlari());
        Assert.assertTrue(urinish == 0 && !oyna, holat + ": sayt formani QABUL QILDI va imzo so'radi (imzo to'sildi, kontrakt yuborilmadi). Xabarlar: " + x);
        Assert.assertTrue(driver.getCurrentUrl().contains("/create"), holat + ": kontrakt yuborilib ketdi. URL: " + driver.getCurrentUrl());
    }

    /** Maydonga qiymat yozadi. Maydon qiymatni o'zgartirib qo'ysa (masalan 15 -> 1) — bu ham himoya: true qaytaradi. */
    boolean maydonQabulQilmadi(String name, String qiymat, String holat) {
        String v = forma.set(name, qiymat);
        String norm = v == null ? "" : v.replace(" ", "").replace(",", ".");
        System.out.println(holat + ": '" + qiymat + "' yozildi, maydonda: '" + v + "'");
        if (!norm.equals(qiymat)) {
            System.out.println(holat + " -> maydon bu qiymatni qabul qilmadi (himoya maydonning o'zida) ✓");
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- 1. minus qiymat
    @DataProvider
    public Object[][] sonliMaydonlar() {
        return new Object[][]{
                {"mainPrice", "Narx"}, {"amount", "Bir lot hajmi"},
                {"sellerDepositPercentage", "Sotuvchidan zakalat %"}, {"buyerDepositPercentage", "Xaridordan zakalat %"},
                {"deliveryTerm", "Yetkazib berish muddati"}, {"paymentTerm", "To'lov muddati"},
        };
    }

    @Test(priority = 1, dataProvider = "sonliMaydonlar", description = "Sonli maydonga minus qiymat kiritib bo'lmaydi")
    public void minusQiymat(String name, String nomi) {
        forma.ochish(base);
        String v = forma.set(name, "-500");
        System.out.println("1) " + nomi + ": '-500' yozildi, maydonda: '" + v + "'");
        Assert.assertFalse(v != null && v.contains("-"), nomi + " maydoni minus qiymatni qabul qildi: '" + v + "'");
    }

    // ---------------------------------------------------------------- 2. narx 0
    /**
     * Narx 0 ni sayt SERVERDA, E-IMZO imzosidan keyin tekshiradi ("Narx qiymati musbat bo'lishi kerak").
     * Shuning uchun bu holatda imzo to'silmaydi: parol kiritiladi va server rad etishi kutiladi.
     */
    @Test(priority = 5, description = "Narx 0 bo'lsa kontrakt yuborilmaydi (server: 'Narx qiymati musbat bo'lishi kerak')")
    public void narxNol() {
        forma.ochish(base).togriToldirish(true); // yangi sahifa: oldingi holatlardagi imzo to'sig'i yo'q
        toldirilgan = false;
        if (maydonQabulQilmadi("mainPrice", "0", "2) Narx = 0")) return;
        forma.yuborishniBosish();
        boolean imzo = Eimzo.typePassword(15);
        boolean rad = waitToast(driver, "musbat", 15);
        List<String> x = forma.xatolar();
        System.out.println("2) Narx = 0 -> imzo: " + imzo + ", xabarlar: " + toasts(driver) + " " + x + (rad ? " ✓" : ""));
        Assert.assertTrue(driver.getCurrentUrl().contains("/create"), "2) Narx = 0: kontrakt yuborilib ketdi! URL: " + driver.getCurrentUrl());
        Assert.assertTrue(rad, "2) Narx = 0: sayt 'Narx qiymati musbat bo'lishi kerak' xabarini bermadi. Xabarlar: " + toasts(driver) + " " + x);
    }

    // ---------------------------------------------------------------- 3. zakalat chegaradan tashqari
    @DataProvider
    public Object[][] zakalatlar() {
        return new Object[][]{
                {"sellerDepositPercentage", "0"}, {"sellerDepositPercentage", "15"},
                {"buyerDepositPercentage", "0"}, {"buyerDepositPercentage", "15"},
        };
    }

    @Test(priority = 4, dataProvider = "zakalatlar", description = "Zakalat 0,1% - 10% oralig'idan tashqarida bo'lsa kontrakt yuborilmaydi")
    public void zakalatOraliqdanTashqari(String name, String qiymat) {
        tayyorForma();
        try {
            if (maydonQabulQilmadi(name, qiymat, "3) " + name + " = " + qiymat + "%")) return;
            radEtilishiKerak("3) " + name + " = " + qiymat + "%");
        } finally { qaytar(name); }
    }

    // ---------------------------------------------------------------- 4. majburiy maydonlar bo'sh
    @Test(priority = 6, description = "Majburiy maydonlar bo'sh bo'lsa kontrakt yuborilmaydi va xato ko'rsatiladi")
    public void majburiyMaydonlarBosh() {
        tayyorForma();
        toldirilgan = false;
        for (String name : KontraktForma.TOGRI.keySet())
            if (visible(driver, org.openqa.selenium.By.name(name)) != null) forma.set(name, "");
        radEtilishiKerak("4) Bo'sh forma");
        Assert.assertFalse(forma.xatolar().isEmpty(), "4) Bo'sh forma: ekranda hech qanday xato yozuvi chiqmadi");
    }

    // ---------------------------------------------------------------- 5. hujjatsiz
    @Test(priority = 2, description = "Hujjat yuklanmasa kontrakt yuborilmaydi")
    public void hujjatsiz() {
        forma.ochish(base).togriToldirish(false);
        radEtilishiKerak("5) Hujjat yuklanmagan");
        toldirilgan = false;
    }
}
