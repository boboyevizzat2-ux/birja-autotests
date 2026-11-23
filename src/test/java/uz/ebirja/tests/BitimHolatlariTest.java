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

    static String bitim(String key, String def) { return Config.getOr("bitim." + key, def); }

    String base;

    @BeforeClass
    public void setUp() { base = Config.traderUrl(); }

    @AfterMethod(alwaysRun = true)
    public void yopish(ITestResult r) {
        if (r.getStatus() == ITestResult.FAILURE && driver != null) rasm("XATO_" + r.getMethod().getMethodName());
        closeBrowser();
    }

    // ================================================================== HOLATLAR

    @Test(description = "Xaridor to'lov qiladi -> bitim 'To'langan', xaridor balansi kamayadi")
    public void tolovQilish() {
        String no = bitim("tolov", "B26000256");
        tolov(no);
    }

    @Test(description = "Bekor qilish: KELISHUV. Sotuvchi so'rov yuboradi, xaridor tasdiqlaydi -> 'Bekor qilingan'")
    public void bekorKelishuv() {
        bekorQilish(bitim("kelishuv", "B26000242"), "Kelishuv", null);
    }

    @Test(description = "Bekor qilish: XARIDOR JARIMASI. Jarima = xaridor zakalati, xaridor balansidan yechiladi")
    public void bekorXaridorJarimasi() {
        bekorQilish(bitim("xaridorJarima", "B26000243"), "Xaridor jarimasi", XARIDOR_STIR);
    }

    @Test(description = "Bekor qilish: SOTUVCHI JARIMASI. Jarima = sotuvchi zakalati, sotuvchi balansidan yechiladi")
    public void bekorSotuvchiJarimasi() {
        bekorQilish(bitim("sotuvchiJarima", "B26000244"), "Sotuvchi jarimasi", SOTUVCHI_STIR);
    }

    @Test(description = "To'lov muddatini uzaytirish: sotuvchi so'raydi, xaridor qabul qiladi")
    public void tolovMuddatiniUzaytirish() {
        String no = bitim("tolovUzaytirish", "B26000247");
        muddatUzaytirish(no, "trader1", "Sotish", "To'lov muddatini uzaytirish", "payment-request", "trader2");
    }

    @Test(description = "Yetkazib berish muddatini uzaytirish (to'langan bitimda): sotuvchi so'raydi, xaridor qabul qiladi")
    public void yetkazibBerishMuddatiniUzaytirish() {
        String no = bitim("yetkazishUzaytirish", "B26000257");
        if (!holatQanday("trader2", "Sotib olish", no).contains("To'langan")) tolov(no);
        muddatUzaytirish(no, "trader1", "Sotish", "Yetkazib berish muddatini uzaytirish", "delivered-request", "trader2");
    }

    // ================================================================== AMALLAR

    void tolov(String no) {
        openBrowser();
        login("trader2");
        BigDecimal[] oldin = balans(XARIDOR_STIR);
        WebElement row = bitimQatori("Sotib olish", no);
        Assert.assertTrue(norm(row.getText()).contains("To'lov kutilmoqda"), no + " 'To'lov kutilmoqda' holatida emas: " + cut(row.getText(), 200));
        menyu(row, "To'lov qilish");
        dialogTugma("To'lov qilish");
        Eimzo.typePassword(10);
        sleep(2500);
        System.out.println("TO'LOV " + no + " xabarlar: " + toasts(driver));
        String h = holatQanday(null, "Sotib olish", no);
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

    /** Sotuvchi bekor qilish so'rovini yuboradi, xaridor "Bekor qilish so'rovi" sahifasida tasdiqlaydi. */
    void bekorQilish(String no, String tur, String jarimaStir) {
        openBrowser();
        BigDecimal[] oldin = null;
        String kimBalans = XARIDOR_STIR.equals(jarimaStir) ? "trader2" : "trader1";
        List<String> opOldin = null;
        if (jarimaStir != null) { login(kimBalans); oldin = balans(jarimaStir); opOldin = operatsiyalar(no); }
        login("trader1");
        WebElement row = bitimQatori("Sotish", no);
        String deposit = ustun(row, "zakalat");
        menyu(row, "Bitimni bekor qilish");
        dialogSelect(tur);
        WebElement reason = dialogEl(By.cssSelector("textarea[name=reason]"));
        reason.sendKeys("Avtotest: " + tur);
        rasm("bekor_" + no + "_forma");
        dialogTugma("Tasdiqlash");
        Eimzo.typePassword(10);
        sleep(2500);
        List<String> t = toasts(driver);
        System.out.println("BEKOR (" + tur + ") " + no + " so'rov yuborildi. Xabarlar: " + t);
        // xaridor tasdiqlaydi
        login("trader2");
        sorovniTasdiqlash("canceled-request", no);
        String h = holatQanday(null, "Sotib olish", no);
        System.out.println("BEKOR (" + tur + ") " + no + " holati: " + h);
        Assert.assertTrue(h.contains("Bekor qilingan"), no + ": bekor qilingandan keyin holat 'Bekor qilingan' emas: " + h);
        if (jarimaStir != null) {
            login(kimBalans);
            BigDecimal[] keyin = balans(jarimaStir);
            List<String> opKeyin = operatsiyalar(no);
            List<String> yangi = new ArrayList<>(opKeyin); yangi.removeAll(opOldin);
            System.out.println("JARIMA (" + tur + ") " + no + " yangi operatsiyalar: " + yangi);
            Assert.assertFalse(yangi.isEmpty(), no + ": bekor qilingandan keyin balans tarixida yangi operatsiya chiqmadi");
            // jarima = kontraktdagi zakalat (narx x lot x zakalat%), jarimaga tushgan tomondan ikkinchi tomonga
            String kutilgan = String.format("%,d", JARIMA).replaceAll("[^0-9]", " ");
            String boshqa = XARIDOR_STIR.equals(jarimaStir) ? SOTUVCHI_STIR : XARIDOR_STIR;
            boolean topildi = yangi.stream().anyMatch(op -> op.toLowerCase().contains("jarima") && op.contains("| " + jarimaStir + " | " + boshqa + " | " + kutilgan + " |"));
            Assert.assertTrue(topildi, no + ": " + kutilgan + " so'mlik jarima " + jarimaStir + " dan " + boshqa + " ga o'tmadi. Yangi operatsiyalar: " + yangi);
            System.out.println("JARIMA (" + tur + ") " + no + ": " + kutilgan + " so'm " + jarimaStir + " -> " + boshqa + " 2713");
            System.out.println("JARIMA (" + tur + ") STIR " + jarimaStir + " balans (barcha/erkin/band/qarz): "
                    + Arrays.toString(oldin) + " -> " + Arrays.toString(keyin) + " | bitimdagi zakalat ustuni: " + deposit);
        }
    }

    void muddatUzaytirish(String no, String kim, String tab, String band, String sorovSahifa, String tasdiqlovchi) {
        if (driver == null) openBrowser();
        login(kim);
        WebElement row = bitimQatori(tab, no);
        String sanalarOldin = sanalar(row.getText());
        menyu(row, band);
        WebElement kun = dialogEl(By.name("extensionDay"));
        kun.sendKeys("2");
        dialogEl(By.cssSelector("textarea[name=description]")).sendKeys("Avtotest: " + band);
        rasm("uzaytirish_" + no);
        dialogTugma("Saqlash");
        Eimzo.typePassword(10);
        sleep(2500);
        System.out.println(band.toUpperCase() + " " + no + " so'rov yuborildi. Xabarlar: " + toasts(driver));
        login(tasdiqlovchi);
        sorovniTasdiqlash(sorovSahifa, no);
        driver.get(base + "/uz/trader/contracts/" + sorovSahifa);
        sleep(5000);
        WebElement qabul = visible(driver, By.xpath("//main//*[starts-with(normalize-space(.),'Qabul qilingan')][not(*) or self::button]"));
        if (qabul != null) { jsClick(driver, qabul); sleep(4000); }
        Assert.assertNotNull(rowWith(no), no + ": '" + band + "' so'rovi 'Qabul qilingan' ro'yxatida yo'q");
        System.out.println(band.toUpperCase() + " " + no + ": qabul qilindi ✓");
        String sanalarKeyin = sanalar(bitimQatori(tab.equals("Sotish") ? "Sotib olish" : "Sotish", no).getText());
        System.out.println(band.toUpperCase() + " " + no + " bitimdagi sanalar: " + sanalarOldin + " -> " + sanalarKeyin);
        Assert.assertNotEquals(sanalarKeyin, sanalarOldin, no + ": so'rov qabul qilingandan keyin bitimdagi muddat o'zgarmadi");
    }

    /** So'rovlar sahifasida (Kutilmoqda) bitimni topib "Tasdiqlash" bosadi. */
    void sorovniTasdiqlash(String sahifa, String no) {
        driver.get(base + "/uz/trader/contracts/" + sahifa);
        sleep(6000);
        WebElement s = visible(driver, By.cssSelector("main input[placeholder='Qidirish']"));
        if (s != null) { s.sendKeys(no); sleep(4000); }
        WebElement row = waitFor(() -> rowWith(no), 30);
        Assert.assertNotNull(row, sahifa + ": " + no + " so'rovi 'Kutilmoqda' ro'yxatida yo'q");
        System.out.println(sahifa + " qatori: " + cut(row.getText(), 300));
        rasm(sahifa + "_" + no);
        WebElement btn = null;
        for (WebElement b : row.findElements(By.cssSelector("button")))
            try { if (norm(b.getText()).contains("Tasdiqlash") || "Tasdiqlash".equals(b.getAttribute("title"))) btn = b; } catch (Exception ignore) {}
        if (btn == null) {
            // "Tasdiqlash" ustunidagi tugma (matnsiz ikonka)
            List<String> heads = new ArrayList<>();
            for (WebElement th : driver.findElements(By.cssSelector("main thead th"))) heads.add(norm(th.getText()));
            int idx = heads.indexOf("Tasdiqlash");
            List<WebElement> tds = row.findElements(By.cssSelector("td"));
            if (idx >= 0 && idx < tds.size()) btn = tds.get(idx).findElements(By.cssSelector("button, svg")).stream().findFirst().orElse(null);
        }
        Assert.assertNotNull(btn, sahifa + ": " + no + " qatorida 'Tasdiqlash' tugmasi topilmadi");
        jsClick(driver, btn);
        sleep(1500);
        WebElement ok = lastVisible(driver, By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button[normalize-space(.)='Tasdiqlash' or normalize-space(.)='Ha' or normalize-space(.)='Qabul qilish']"));
        if (ok != null) jsClick(driver, ok);
        Eimzo.typePassword(10);
        sleep(3000);
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

    void login(String who) { loginByKey(driver, base, Config.get(who + ".login"), true); }

    static String norm(String s) { return s == null ? "" : s.replaceAll("[‘’`ʼ]", "'").replace(" ", " "); }

    WebElement rowWith(String text) {
        for (WebElement r : driver.findElements(By.cssSelector("main tbody tr"))) try { if (r.getText().contains(text)) return r; } catch (Exception ignore) {}
        return null;
    }

    WebElement bitimQatori(String tab, String no) {
        // xaridor mijoz nomidan (auction.buyerClient) bo'lsa, trader2 da bitim "Mijozimning shartnomalari" da
        boolean mijoz = tab.equals("Sotib olish") && !Config.getOr("auction.buyerClient", "").isEmpty();
        driver.get(base + (mijoz ? "/uz/trader/contracts/client-contracts" : CONTRACTS));
        sleep(6000);
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//main//*[normalize-space(text())='" + tab + "']")), 20));
        sleep(3000);
        WebElement s = visible(driver, By.cssSelector("main input[placeholder='Qidirish']"));
        if (s != null) { s.sendKeys(no); sleep(4000); }
        WebElement r = waitFor(() -> rowWith(no), 30);
        Assert.assertNotNull(r, no + " '" + tab + "' tabida topilmadi");
        return r;
    }

    /** Bitim holati (qator matni). who=null bo'lsa joriy foydalanuvchi. */
    String holatQanday(String who, String tab, String no) {
        if (driver == null) openBrowser();
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
        sleep(6000);
        for (String tab : new String[]{"Mijozlar balansi", "Broker balansi"}) {
            WebElement b = visible(driver, By.xpath("//main//*[normalize-space(text())='" + tab + "']"));
            if (b != null) { jsClick(driver, b); sleep(3500); }
            WebElement s = visible(driver, By.cssSelector("main input[placeholder='Qidirish']"));
            if (s != null) { s.sendKeys(Keys.CONTROL + "a"); s.sendKeys(stir); sleep(3500); }
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
        sleep(6000);
        WebElement b = visible(driver, By.xpath("//main//*[normalize-space(text())='Broker balansi']"));
        if (b != null) { jsClick(driver, b); sleep(3500); }
        WebElement s = visible(driver, By.cssSelector("main input[placeholder='Qidirish']"));
        if (s != null) { s.sendKeys(Keys.CONTROL + "a"); s.sendKeys(no); sleep(3500); }
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
