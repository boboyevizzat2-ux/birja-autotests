package uz.ebirja.tests;

import org.openqa.selenium.*;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import uz.ebirja.base.BaseTest;
import uz.ebirja.base.Eimzo;
import uz.ebirja.base.Ui;
import uz.ebirja.config.Config;

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
 * Sozlamalar (login.properties): auction.product, auction.lots, auction.bidLots, auction.minutes
 */
public class AuctionFlowTest extends BaseTest {

    static final String PRODUCT = Config.getOr("auction.product", "Асбестовый шнур");
    static final String LOTS = Config.getOr("auction.lots", "10");
    static final String BID_LOTS = Config.getOr("auction.bidLots", "1");
    static final int MIN = Integer.parseInt(Config.getOr("auction.minutes", "2"));
    static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    // qadamlar orasida uzatiladigan ma'lumotlar
    static String contractId;          // moderator ro'yxatidagi ID
    static LocalDateTime elonStart, savdoStart, savdoEnd;
    static String dealNo;              // bitim raqami (B2600....)

    JavascriptExecutor js() { return (JavascriptExecutor) driver; }

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
        driver.get(base + "/uz/trader/listing/sales/create");
        waitFor(() -> visible(driver, By.name("amount")), 30);
        sleep(3000);
        fillSelects();
        type("amount", "1");
        type("mainPrice", "1000000");
        type("deliveryBasisDetail", "Avtotest");
        type("deliveryTerm", "10");
        type("sellerDepositPercentage", "10");
        type("paymentTerm", "3");
        type("buyerDepositPercentage", "10");
        type("specialConditionUz", "Avtotest");
        type("specialConditionUzc", "Avtotest");
        type("specialConditionRu", "Avtotest");
        fillSelects();
        for (WebElement f : driver.findElements(By.cssSelector("input[type=file]")))
            f.sendKeys(new File("src/test/resources/test-hujjat.pdf").getAbsolutePath());
        sleep(2500);
        jsClick(driver, driver.findElement(By.xpath("//button[contains(normalize-space(.),'Yuborish')]")));
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
        lots.click();
        lots.sendKeys(Keys.CONTROL + "a");
        lots.sendKeys(LOTS);
        jsClick(driver, lastVisible(driver, By.xpath("//button[normalize-space(.)='Saqlash']")));
        Eimzo.typePassword(6);
        sleep(3000);
        List<String> t = toasts(driver);
        Assert.assertFalse(t.stream().anyMatch(x -> x.contains("avval taklif berilgan") || x.contains("Majburiy")),
                "Savdoga chiqarilmadi: " + t);
        System.out.println("4) " + LOTS + " lot savdoga qo'yildi. Xabarlar: " + t);
    }

    // ------------------------------------------------------------------ 5
    @Test(priority = 5, dependsOnMethods = "step4_savdogaChiqarish",
          description = "Treyder 2 savdo vaqtida mahsulotga taklif beradi")
    public void step5_taklifBerish() {
        openBrowser();
        String base = Config.traderUrl();
        loginByKey(driver, base, Config.get("trader2.login"), true);
        waitUntil(savdoStart.plusSeconds(5), "savdo vaqti");
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
        sleep(3000);
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
            sleep(1500);
            WebElement first = visible(driver, By.cssSelector("[id*='-option-']"));
            Assert.assertNotNull(first, "Mijozlar ro'yxati bo'sh");
            System.out.println("5) Mijoz: " + clean(first.getText()));
            jsClick(driver, first);
            sleep(800);
        }
        WebElement miqdor = panel.findElement(By.cssSelector("input[placeholder='Miqdor']"));
        js().executeScript("arguments[0].focus()", miqdor);
        miqdor.sendKeys(Keys.CONTROL + "a");
        miqdor.sendKeys(BID_LOTS);
        jsClick(driver, panel.findElement(By.xpath(".//button[normalize-space(.)='Sotib olish']")));
        Eimzo.typePassword(6);
        sleep(3000);
        List<String> t = toasts(driver);
        Assert.assertFalse(t.stream().anyMatch(x -> x.contains("Majburiy")), "Taklif yuborilmadi: " + t);
        System.out.println("5) " + BID_LOTS + " lotga taklif berildi. Xabarlar: " + t);
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
        for (int attempt = 0; attempt < 6 && dealNo == null; attempt++) {
            openContracts(base, "Sotish");
            for (WebElement r : driver.findElements(By.cssSelector("main tbody tr"))) {
                String tx = r.getText();
                if (tx.contains(today) && tx.contains(PRODUCT) && tx.contains("Imzolash kutilmoqda")) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("B\\d{8}").matcher(tx);
                    if (m.find()) { dealNo = m.group(); break; }
                }
            }
            if (dealNo == null) sleep(10000);
        }
        Assert.assertNotNull(dealNo, "Bugungi '" + PRODUCT + "' bitimi topilmadi (savdo " + time + " da tugagan)");
        System.out.println("6) Bitim hosil bo'ldi: " + dealNo);
    }

    // ------------------------------------------------------------------ 7
    @Test(priority = 7, dependsOnMethods = "step6_bitimHosilBolishi",
          description = "Ikkala treyder bitimni 'Ommaviy imzolash' orqali imzolaydi")
    public void step7_treyderlarImzolashi() {
        openBrowser();
        String base = Config.traderUrl();
        signDeal(base, "trader1", "Sotish");
        signDeal(base, "trader2", "Sotib olish");
    }

    // ------------------------------------------------------------------ 8
    @Test(priority = 8, dependsOnMethods = "step7_treyderlarImzolashi",
          description = "Admin bitimni 'Tasdiqlanmagan' tabidan tasdiqlaydi")
    public void step8_adminTasdiqlashi() {
        openBrowser();
        String base = Config.adminUrl();
        loginByKey(driver, base, Config.get("admin2.login"), false);
        driver.get(base + "/uz/moderationv2/contracts");
        sleep(6000);
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//main//button[starts-with(normalize-space(.),'Tasdiqlanmagan')]")), 20));
        WebElement row = waitFor(() -> rowWith(dealNo), 20);
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
        driver.get(base + "/uz/trader/contracts/brokers-contracts");
        sleep(6000);
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//main//*[normalize-space(text())='" + tab + "']")), 20));
        sleep(4000);
    }

    void signDeal(String base, String who, String tab) {
        loginByKey(driver, base, Config.get(who + ".login"), true);
        openContracts(base, tab);
        WebElement row = waitFor(() -> rowWith(dealNo), 20);
        Assert.assertNotNull(row, who + ": " + dealNo + " '" + tab + "' tabida yo'q");
        WebElement cb = row.findElement(By.cssSelector("input[type=checkbox]"));
        realClick(driver, cb);
        sleep(600);
        if (!cb.isSelected()) jsClick(driver, cb);
        jsClick(driver, driver.findElement(By.xpath("//main//button[contains(normalize-space(.),'Ommaviy imzolash')]")));
        WebElement sign = waitFor(() -> lastVisible(driver, By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button[normalize-space(.)='Imzolash']")), 15);
        Assert.assertNotNull(sign, who + ": imzolash oynasi ochilmadi");
        jsClick(driver, sign);
        Eimzo.typePassword(10);
        Assert.assertTrue(waitToast(driver, "Muvaffaqiyatli", 20), who + ": imzolanmadi. Xabarlar: " + toasts(driver));
        System.out.println("7) " + who + " " + dealNo + " ni imzoladi");
    }

    /** Kontrakt formasi: bo'sh ochiladigan ro'yxatlardan birinchisini (mahsulotni nomi bo'yicha) tanlaydi. */
    void fillSelects() {
        Set<String> done = new HashSet<>();
        for (int pass = 0; pass < 5; pass++) {
            boolean changed = false;
            for (WebElement in : driver.findElements(By.cssSelector("main input[id^='react-select-']"))) {
                try {
                    String id = in.getAttribute("id");
                    if (done.contains(id)) continue;
                    Object dis = js().executeScript("let c=arguments[0].closest('[class*=\"-container\"]'); return !c || c.className.includes('disabled') || arguments[0].disabled;", in);
                    if (Boolean.TRUE.equals(dis)) continue;
                    String val = String.valueOf(js().executeScript("let c=arguments[0].closest('[class*=\"-container\"]'); return c? c.innerText : '';", in)).trim();
                    if (!val.isEmpty() && !val.toLowerCase().contains("tanlang")) { done.add(id); continue; }
                    String label = String.valueOf(js().executeScript("let el=arguments[0]; for(let i=0;i<8 && el;i++){ el=el.parentElement; if(!el) break; let l=el.querySelector('label'); if(l) return l.innerText; } return '';", in));
                    js().executeScript("arguments[0].scrollIntoView({block:'center'})", in);
                    if (label.startsWith("Mahsulot")) { in.sendKeys(PRODUCT); sleep(3000); }
                    else { in.sendKeys(Keys.ARROW_DOWN); sleep(1800); }
                    if (driver.findElements(By.cssSelector("[id^='" + id.replace("-input", "") + "-option-']")).isEmpty()) { in.sendKeys(Keys.ESCAPE); done.add(id); continue; }
                    in.sendKeys(Keys.ENTER);
                    sleep(1200);
                    done.add(id);
                    changed = true;
                } catch (StaleElementReferenceException e) { changed = true; }
            }
            if (!changed) break;
        }
    }

    void type(String name, String value) {
        for (WebElement e : driver.findElements(By.name(name))) {
            if (!e.isDisplayed() || !e.isEnabled()) continue;
            String v = e.getAttribute("value");
            if (v != null && !v.isEmpty()) return;
            js().executeScript("arguments[0].scrollIntoView({block:'center'})", e);
            e.sendKeys(value);
            return;
        }
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
