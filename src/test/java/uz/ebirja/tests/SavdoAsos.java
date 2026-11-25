package uz.ebirja.tests;

import org.openqa.selenium.*;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import uz.ebirja.base.BaseTest;
import uz.ebirja.base.Eimzo;
import uz.ebirja.config.Config;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static uz.ebirja.base.Ui.*;

/**
 * Savdo turlari (auksion, qarama-qarshi auksion) uchun umumiy qism: bitimlarni treyderlar ommaviy imzolashi,
 * admin ommaviy tasdiqlashi, shartnomalar sahifasi, moderator rollari, xato rasmlari.
 */
public abstract class SavdoAsos extends BaseTest {
    // xaridor qaysi mijoz nomidan taklif beradi (STIR yoki nom qismi); bo'sh bo'lsa ro'yxatdagi birinchisi
    static final String XARIDOR_MIJOZ = Config.getOr("auction.buyerClient", "");
    static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    String dealNo;              // birinchi bitim raqami (B2600.... / A2600....)
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

    /** Admin: "Tasdiqlanmagan" tabida bitimlarni belgilab, "Ommaviy tasdiqlash" bilan bitta imzoda tasdiqlaydi. Tugma bo'lmasa bittalab. */
    void adminOmmaviyTasdiqla(String base, List<String> nos) {
        driver.get(base + "/uz/moderationv2/contracts");
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//main//button[starts-with(normalize-space(.),'Tasdiqlanmagan')]")), 20));
        Assert.assertNotNull(waitFor(() -> { for (String x : nos) if (rowWith(x) != null) return Boolean.TRUE; return null; }, 60), "Admin 'Tasdiqlanmagan' ro'yxatida bitimlar yo'q: " + nos);
        sahifada100(driver);
        // FAQAT shu savdodan chiqqan bitimlar belgilanadi (boshqa testerlarniki tasdiqlanib ketmasin)
        List<String> belgilandi = faqatShularniBelgila(nos, "Admin");
        for (String no : nos) if (!belgilandi.contains(no)) System.out.println("8) DIQQAT: " + no + " 'Tasdiqlanmagan' da yo'q (treyder imzolamagan bo'lishi mumkin)");
        WebElement ommaviy = waitFor(() -> visible(driver, By.xpath("//main//button[contains(normalize-space(.),'Ommaviy')]")), 5);
        if (ommaviy == null) {
            System.out.println("8) 'Ommaviy tasdiqlash' tugmasi yo'q — bittalab tasdiqlanadi");
            for (String no : nos) adminTasdiqla(base, no);
            return;
        }
        System.out.println("8) Ommaviy tugma: '" + clean(ommaviy.getText()) + "', belgilangan: " + belgilandi);
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
        // ijobiy tekshiruv: har bir bitim "Tasdiqlangan" tabida turishi kerak
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//main//button[starts-with(normalize-space(.),'Tasdiqlangan')]")), 20));
        sahifada100(driver);
        List<String> yoq = new ArrayList<>();
        for (String no : nos) if (waitFor(() -> rowWith(no), 5) == null) yoq.add(no);
        for (String no : new ArrayList<>(yoq)) { // sahifada ko'rinmasa - qidiruv orqali
            WebElement q = visible(driver, By.cssSelector("main input[placeholder='Qidirish']"));
            if (q == null) break;
            q.sendKeys(Keys.CONTROL + "a");
            q.sendKeys(no);
            if (waitFor(() -> rowWith(no), 8) != null) yoq.remove(no);
        }
        Assert.assertTrue(yoq.isEmpty(), "Tasdiqlangandan keyin 'Tasdiqlangan' tabida topilmadi: " + yoq);
        System.out.println("8) 'Tasdiqlangan' tabida: " + nos.size() + " ta bitim ✓");
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

    /**
     * Jadvalda FAQAT berilgan bitimlarni belgilaydi va boshqa birorta qator belgilanmaganini tekshiradi (belgilangan bo'lsa test yiqiladi).
     * Qaytaradi: topilib belgilangan bitimlar.
     */
    List<String> faqatShularniBelgila(List<String> nos, String who) {
        List<String> ok = new ArrayList<>();
        for (String no : nos) {
            WebElement row = waitFor(() -> rowWith(no), 5);
            if (row == null) continue;
            WebElement cb = row.findElement(By.cssSelector("input[type=checkbox]"));
            if (!cb.isSelected()) { realClick(driver, cb); sleep(200); }
            if (!cb.isSelected()) { jsClick(driver, cb); sleep(200); }
            if (cb.isSelected()) ok.add(no);
        }
        List<String> begona = new ArrayList<>();
        int jami = 0;
        for (WebElement r : driver.findElements(By.cssSelector("main tbody tr"))) {
            List<WebElement> cb = r.findElements(By.cssSelector("input[type=checkbox]"));
            if (cb.isEmpty() || !cb.get(0).isSelected()) continue;
            jami++;
            String tx = r.getText();
            if (nos.stream().noneMatch(tx::contains)) begona.add(cut(tx, 80));
        }
        Assert.assertTrue(begona.isEmpty(), who + ": begona bitimlar ham belgilangan, imzolanmadi: " + begona);
        System.out.println(who + ": faqat o'zimizning bitimlar belgilandi: " + ok.size() + " ta (jadvalda jami belgilangan " + jami + ")");
        return ok;
    }

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
        // filtr: "Bitim holati" = "Imzolanish kutilmoqda" -> faqat shu savdo bitimlari belgilanadi -> "Ommaviy imzolash"
        boolean filtr = holatFiltri("Imzolanish kutilmoqda");
        sahifada100(driver); // filtrdan keyin sayt sahifani yana 10 qatorga qaytaradi
        if (filtr && waitFor(() -> rowWith(dealNo), 8) == null) {
            // ba'zi saytlarda (monolit) holat filtri bo'sh qaytaradi -> filtrsiz, bittalab belgilaymiz
            System.out.println("7) " + who + ": holat filtri bitimni ko'rsatmadi, filtrsiz belgilanadi");
            filtr = false;
            openContracts(base, tab);
            sahifada100(driver);
        }
        Assert.assertNotNull(waitFor(() -> rowWith(dealNo), 20), who + ": " + dealNo + " '" + tab + "' tabida yo'q");
        // FAQAT shu savdodan chiqqan bitimlar imzolanadi (boshqa imzolanmagan bitimlarga tegilmaydi)
        List<String> belgilandi = faqatShularniBelgila(dealNos, who);
        Assert.assertEquals(belgilandi.size(), dealNos.size(), who + ": hamma bitim topilmadi. Belgilandi: " + belgilandi + ", kerak: " + dealNos);
        jsClick(driver, driver.findElement(By.xpath("//main//button[contains(normalize-space(.),'Ommaviy imzolash')]")));
        WebElement sign = waitFor(() -> lastVisible(driver, By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button[normalize-space(.)='Imzolash']")), 15);
        Assert.assertNotNull(sign, who + ": imzolash oynasi ochilmadi");
        jsClick(driver, sign);
        Eimzo.typePassword(10);
        Assert.assertTrue(waitToast(driver, "Muvaffaqiyatli", 20), who + ": imzolanmadi. Xabarlar: " + toasts(driver));
        System.out.println("7) " + who + " imzoladi: " + dealNos);
    }


    String currentRole() { return uz.ebirja.base.Moderator.joriyRol(driver); }

    void switchRole(String role) { uz.ebirja.base.Moderator.rolAlmashtir(driver, role); }
}
