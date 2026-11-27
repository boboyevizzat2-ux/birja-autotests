package uz.ebirja.tests;

import org.openqa.selenium.*;
import org.testng.annotations.Test;
import uz.ebirja.config.Config;

import java.util.*;

import static uz.ebirja.base.Ui.*;

/**
 * Tartibga solinadigan mahsulot — tovar turi ODDIY.
 * Test case: R-01..R-03, R-05, R-08, A-01..A-03, A-05, A-12, A-15, K-01..K-03, K-07, B-01, B-02, CD-01..CD-02, T-01..T-07, H-01, H-05, H-07.
 *
 * Kontraktlar (Dilshod sotadi, Zarnigor mijozlari oladi):
 *   K1 — tasdiqlashda "Ro'yxatga asosan"  -> Y (ro'yxatda yo'q) = 6, X 6 lot (limit 5) = 4, X 1 lot = 2
 *   K2 — tasdiqlashda cheklov TAYINLANMAYDI -> Y = 2 ("Cheklov tayinlanmagan")
 *   K3 — tasdiqlashda "Hammaga ochiq"       -> Y = 2, X 10 lot = 2; narx/lot validatsiyalari
 *   K4 — "Ro'yxatga asosan", keyin "Cheklovlarni o'chirish" Ro'yxat -> Y = 2
 */
public class RegOddiyTest extends RegAsos {

    @Test(priority = 1, description = "R-01..R-03: M-mahsulotni reestrga qo'shish (Oddiy, Ro'yxat asosida), sabab va hujjatsiz")
    public void r01_mahsulotQoshish() {
        moderatorKir();
        PRODUCT = Config.getOr("reg.oddiy.product", null);
        if (PRODUCT == null) {
            List<String> nom = mNomzodlar();
            System.out.println("M-nomzodlar (reestrda yo'q): " + nom);
            for (String n : nom) if (reestrQatori(n) == null) { PRODUCT = n; break; }
        }
        org.testng.Assert.assertNotNull(PRODUCT, "Reestrda yo'q 'М' mahsulot topilmadi");
        System.out.println("MAHSULOT: " + PRODUCT);

        driver.get(reestrUrl() + "?status=all");
        jadvalKut(driver);
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//main//button[starts-with(normalize-space(.),\"Mahsulot qo\")]")), 15));
        org.testng.Assert.assertNotNull(dialog(), "'Mahsulot qo'shish' oynasi ochilmadi");
        String t = dlgSelect("Asosiy mahsulot", PRODUCT, s -> s.split("\n")[0].trim().equals(PRODUCT));
        org.testng.Assert.assertNotNull(t, "'Mahsulot qo'shish' ro'yxatida " + PRODUCT + " yo'q");

        // R-03: tur va cheklovsiz saqlab bo'lmaydi
        List<String> x = new ArrayList<>();
        WebElement qosh = dlgTugmaEl("Qo'shish");
        boolean yopiq = qosh != null && !qosh.isEnabled();
        if (!yopiq) {
            jsClick(driver, qosh);
            sleep(1500);
            x.addAll(dlgXatolar());
        }
        kutilgan(dialogOchiq(), "R-03: tur va cheklov tanlanmasa saqlanmaydi (" + (yopiq ? "tugma yopiq" : "xabarlar: " + x) + ")");

        // R-01
        org.testng.Assert.assertTrue(belgila("radio", "Oddiy", "productType", true), "Tovar turi 'Oddiy' tanlanmadi");
        org.testng.Assert.assertTrue(belgila("checkbox", "Ro'yxat asosida", null, true), "'Ro'yxat asosida' belgilanmadi");
        List<String> xab = new ArrayList<>();
        boolean ok = dlgSaqla("Qo'shish", xab);
        if (!ok) { dump("R01"); xab.addAll(dlgXatolar()); yop(); }
        kutilgan(ok, "R-01: mahsulot sabab va hujjatsiz qo'shildi " + xab);
        WebElement r = reestrQatori(PRODUCT);
        org.testng.Assert.assertNotNull(r, "R-01: " + PRODUCT + " reestrda ko'rinmadi");
        String qator = clean(r.getText());
        kutilgan(qator.contains("Oddiy") && qator.contains("Ro'yxat") && qator.contains("Faol"), "R-01: reestr qatori: " + qator);
        String href = r.findElement(By.cssSelector("a[href*='regulated-products/']")).getAttribute("href");
        mahsulotId = href.replaceAll(".*regulated-products/(\\d+).*", "$1");
        System.out.println("Reestr ID: " + mahsulotId);
        kutilgan(tarixdaBor("Reestrga qo'shildi", "ODDIY"), "H: 'Hujjatlar va tarix' da 'Reestrga qo'shildi' yozuvi bor");

        // R-02: yana qo'shib bo'lmaydi
        driver.get(reestrUrl() + "?status=all");
        jadvalKut(driver);
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//main//button[starts-with(normalize-space(.),\"Mahsulot qo\")]")), 15));
        dialog();
        WebElement in = waitFor(() -> lastVisible(driver, By.cssSelector("[role=dialog] input[id^='react-select'], [class*=modal] input[id^='react-select'], [role=dialog] input[role=combobox], [class*=modal] input[role=combobox]")), 10);
        org.testng.Assert.assertNotNull(in, "R-02: 'Mahsulot qo'shish' oynasida mahsulot maydoni yo'q");
        in.sendKeys(PRODUCT);
        sleep(2000);
        String holat = "yo'q";
        for (WebElement o : driver.findElements(By.cssSelector("[id*='-option-'],[class*=__option],[role=option]")))
            try {
                if (o.isDisplayed() && o.getText().trim().split("\n")[0].trim().equals(PRODUCT))
                    holat = clean(o.getText()) + ("true".equals(o.getAttribute("aria-disabled")) || String.valueOf(o.getAttribute("class")).contains("disabled") ? " [disabled]" : "");
            } catch (Exception ignore) {}
        in.sendKeys(Keys.ESCAPE);
        yop();
        kutilgan(holat.equals("yo'q") || holat.contains("disabled") || holat.contains("bor"), "R-02: qayta qo'shishda variant: " + holat);
        yakuniyTekshiruv();
    }

    @Test(priority = 2, dependsOnMethods = "r01_mahsulotQoshish", alwaysRun = true, description = "R-05, R-08, H-01: cheklovga Limit qo'shish")
    public void r02_limitQoshish() {
        shart(mahsulotId != null, "mahsulot reestrga qo'shilmagan");
        moderatorKir();
        karta(null);
        long tugma = driver.findElements(By.xpath("//main//button[contains(normalize-space(.),'Tahrirlash')]")).stream().filter(WebElement::isDisplayed).count();
        kutilgan(tugma == 0 && visible(driver, By.xpath("//main//button[normalize-space(.)=\"Tur va cheklovlarni o'zgartirish\"]")) != null,
                "R-08: bitta tahrirlash tugmasi ('Tur va cheklovlarni o'zgartirish'), 'Tahrirlash' tugmalari: " + tugma);
        jsClick(driver, waitFor(() -> visible(driver, By.xpath("//main//button[normalize-space(.)=\"Tur va cheklovlarni o'zgartirish\"]")), 10));
        // forma oyna emas - sahifaning o'zida ochiladi
        org.testng.Assert.assertNotNull(waitFor(() -> belgi("checkbox", "Limit", null), 10), "'Tur va cheklovlarni o'zgartirish' formasi ochilmadi");
        org.testng.Assert.assertTrue(belgila("checkbox", "Limit", null, true), "'Limit' belgilanmadi");
        List<String> x = new ArrayList<>();
        jsClick(driver, waitFor(() -> lastVisible(driver, By.xpath("//main//button[normalize-space(.)='Saqlash']")), 6));
        Set<String> yig = new LinkedHashSet<>();
        imzoYoki(() -> { yig.addAll(toasts(driver)); return yig.stream().anyMatch(t -> t.contains("Muvaffaqiyatli")); }, 12);
        x.addAll(yig);
        boolean ok = yig.stream().anyMatch(t -> t.contains("Muvaffaqiyatli"));
        if (!ok) dump("R05");
        kutilgan(ok, "R-05: Limit qo'shildi " + x);
        kutilgan(tarixdaBor("Tur va cheklovlar o'zgartirildi", "[LIST]", "LIMIT"), "H-01: tarixda 'ODDIY [LIST] → ODDIY [LIST, LIMIT]'");
        yakuniyTekshiruv();
    }

    @Test(priority = 3, dependsOnMethods = "r01_mahsulotQoshish", alwaysRun = true, description = "Dilshod 4 ta kontrakt yaratadi va yuboradi")
    public void r03_kontraktlar() {
        shart(PRODUCT != null, "mahsulot tanlanmagan");
        kim("trader2");
        List<String> oldingi = new ArrayList<>();
        for (String kod : new String[]{"K1", "K2", "K3", "K4"}) {
            Kontrakt k = new Kontrakt(kod);
            k.vaqt = kontraktYubor(kod, null, oldingi.toArray(new String[0]));
            oldingi.add(k.vaqt);
            K.put(kod, k);
        }
        System.out.println("Guruh: " + guruh + ", birlik: " + birlik);
    }

    @Test(priority = 4, dependsOnMethods = "r03_kontraktlar", alwaysRun = true, description = "B-01, B-02, H-07: xaridorlar ro'yxati (X limit 5, Z limit 199.03)")
    public void r04_xaridorlar() {
        shart(mahsulotId != null, "mahsulot reestrda yo'q");
        moderatorKir();
        List<String> x = new ArrayList<>();
        boolean ok = xaridorQosh(Z_STIR, "199.034", false, x);
        String qoldi = oxirgiLimitMaydoni;
        boolean kesildi = qoldi != null && !qoldi.replace(",", ".").equals("199.034");
        kutilgan(!ok || kesildi, "B-02: limit 199.034 (3 xona) qabul qilinmaydi (maydonda qoldi: '" + qoldi + "', saqlandi: " + ok + "). Xabarlar: " + x);
        if (ok) System.out.println("  (199.034 saqlanib ketdi)");
        x.clear();
        if (xaridorQatori(Z_STIR) == null) {
            ok = xaridorQosh(Z_STIR, "199.03", false, x);
            kutilgan(ok, "B-01: limit 199.03 saqlandi " + x);
        }
        WebElement z = xaridorQatori(Z_STIR);
        kutilgan(z != null && clean(z.getText()).contains("199.03") || z != null && clean(z.getText()).contains("199,03"), "B-01: ro'yxatda 199.03 — " + (z == null ? "qator yo'q" : clean(z.getText())));
        x.clear();
        ok = xaridorQosh(X_STIR, "5", false, x);
        kutilgan(ok && xaridorQatori(X_STIR) != null, "X (" + X_STIR + ") limit 5 " + birlik + " bilan qo'shildi " + x);
        kutilgan(tarixdaBor(X_STIR, "Qo'shildi"), "H-07: tarixda 'STIR " + X_STIR + ": Qo'shildi'");
        kutilgan(xaridorQatori(Y_STIR) == null, "Y (" + Y_STIR + ") ro'yxatda yo'q");
        yakuniyTekshiruv();
    }

    @Test(priority = 5, dependsOnMethods = "r03_kontraktlar", alwaysRun = true, description = "A-01, A-02, A-05: 3 rolda tasdiqlash, cheklov tasdiqlashda tayinlanadi")
    public void r05_tasdiqlash() {
        shart(K.size() == 4, "kontraktlar yaratilmagan");
        moderatorKir();
        Map<String, String> variant = new LinkedHashMap<>();
        variant.put("K1", "Ro'yxatga asosan");
        variant.put("K2", null);              // hozir tayinlanmaydi
        variant.put("K3", "Hammaga ochiq");
        variant.put("K4", "Ro'yxatga asosan");
        boolean[] blokKorindi = {false};
        try {
            for (String role : new String[]{"MODERATOR_LISTING_MAIN", "MODERATOR_LISTING", "MODERATOR_LISTING_MEMBER"}) {
                switchRole(role);
                for (Kontrakt k : K.values()) {
                    String v = variant.get(k.kod);
                    tasdiqla(k, matn -> {
                        if (!matn.contains("Cheklovlarni hozir tayinlash")) return;
                        blokKorindi[0] = true;
                        kutilgan(!matn.contains("Spirt uchun") && !matn.toLowerCase().contains("kumush uchun"), "A-05: reestr mahsulotida eski 4 checkbox yo'q, cheklov bloki bor");
                        belgila("checkbox", "Cheklovlarni hozir tayinlash", null, v != null);
                        if (v != null) {
                            belgila("radio", v, "buyers", true);
                            WebElement koef = lastVisible(driver, By.cssSelector("[role=dialog] [name='coefficient'], [class*=modal] [name='coefficient']"));
                            if (koef != null && (koef.getAttribute("value") == null || koef.getAttribute("value").isEmpty())) {
                                dlgYoz("coefficient", "1");
                                try { dlgSelect("Konvertatsiya", birlik, s -> s.equalsIgnoreCase(birlik)); } catch (Throwable ignore) {}
                            }
                        }
                        System.out.println("  " + k.kod + ": hozir tayinlash=" + (v != null) + ", variant=" + v);
                    });
                }
            }
        } finally {
            switchRole("MODERATOR_LISTING_MAIN");
        }
        kutilgan(blokKorindi[0], "A-01: tasdiqlash oynasida 'Cheklovlarni hozir tayinlash' bloki chiqdi");
        for (Kontrakt k : K.values()) k.raqam = ochilganRaqam(k.id);
        System.out.println("Kontraktlar: " + K.values());
        // Listinglar tabida ustunlar
        Kontrakt k1 = K.get("K1"), k2 = K.get("K2"), k3 = K.get("K3");
        String c1 = listingUstun(k1.raqam, "Kontrakt xaridorlari");
        kutilgan(c1 != null && c1.contains("Ro'yxatga asosan"), "A-01/K-07: K1 'Kontrakt xaridorlari' = " + c1);
        String ch1 = listingUstun(k1.raqam, "Cheklovlar");
        kutilgan(ch1 != null && ch1.contains("Ro'yxat") && ch1.contains("Limit"), "A-01: K1 cheklovlar = " + ch1);
        String c3 = listingUstun(k3.raqam, "Kontrakt xaridorlari");
        kutilgan(c3 != null && c3.contains("Hammaga ochiq"), "K-07: K3 'Kontrakt xaridorlari' = " + c3);
        WebElement r2 = listingQatori(k2.raqam);
        String t2 = r2 == null ? "qator yo'q" : clean(r2.getText());
        kutilgan(t2.contains("tayinlanmagan"), "A-02: K2 'Cheklov tayinlanmagan' — " + t2);
        kutilgan(visible(driver, By.xpath("//*[contains(normalize-space(.),'Kontrakt maydonlari')][not(*)]")) == null, "A-12: 'Kontrakt maydonlari' amali yo'q");
        yakuniyTekshiruv();
    }

    @Test(priority = 6, dependsOnMethods = "r05_tasdiqlash", alwaysRun = true, description = "CD-01, CD-02, H-05: K4 da Ro'yxat cheklovini o'chirish (sababsiz)")
    public void r06_cheklovOchirish() {
        shart(K.get("K4") != null && K.get("K4").raqam != null, "K4 tasdiqlanmagan");
        moderatorKir();
        Kontrakt k4 = K.get("K4");
        org.testng.Assert.assertTrue(listingAmali(k4.raqam, "Cheklovlarni o"), "'Cheklovlarni o'chirish / yoqish' oynasi ochilmadi");
        org.testng.Assert.assertTrue(belgila("checkbox", "Ro'yxat asosida", null, true), "Ro'yxat belgilanmadi");
        List<String> x = new ArrayList<>();
        boolean ok = dlgSaqla("Saqlash", x);
        if (!ok) { dump("CD01"); yop(); }
        kutilgan(ok, "CD-01: Ro'yxat cheklovi sabab va hujjatsiz o'chirildi " + x);
        String ch = listingUstun(k4.raqam, "Cheklovlar");
        // o'chirilgan cheklov "Cheklovlar" ustunidan yo'qoladi (yoki "o'chirilgan" belgisi bilan chiqadi)
        kutilgan(ch != null && (!ch.contains("Ro'yxat") || norm(ch).toLowerCase().contains("o'chirilgan")), "CD-02: K4 cheklovlar ustuni: " + ch);
        kutilgan(tarixdaBor("Listing " + k4.raqam, "o'chirilgan"), "H-05: tarixda 'Listing " + k4.raqam + ": ... (o'chirilgan)'");
        yakuniyTekshiruv();
    }

    @Test(priority = 7, dependsOnMethods = "r05_tasdiqlash", alwaysRun = true, description = "Admin savdo vaqtini ochadi, Dilshod 4 kontraktni savdoga chiqaradi")
    public void r07_savdo() {
        shart(K.size() == 4 && K.values().stream().allMatch(k -> k.raqam != null), "kontraktlar tasdiqlanmagan");
        savdoVaqtiniOch();
        kim("trader1"); // xaridor oldindan kirib oladi
        kim("trader2");
        waitUntil(elonStart.plusSeconds(5), "e'lon vaqti");
        for (Kontrakt k : K.values()) savdogaChiqar(k, LOTS);
        savdoQoyildi = true;
    }

    @Test(priority = 8, dependsOnMethods = "r07_savdo", alwaysRun = true, description = "K-01..K-03, A-02, CD-01, T-01..T-07: takliflar va rad etish kodlari")
    public void r08_takliflar() {
        shart(savdoEnd != null && savdoQoyildi, "savdo ochilmagan");
        kim("trader1");
        savdoSahifasi();
        waitUntil(savdoStart.plusSeconds(3), "savdo boshlanishi");
        savdoSahifasi();
        Kontrakt k1 = K.get("K1"), k2 = K.get("K2"), k3 = K.get("K3"), k4 = K.get("K4");
        taklifKut(k1, Y_STIR, "1", 6, "K-01: K1 (Ro'yxatga asosan), Y ro'yxatda yo'q, 1 lot");
        taklifKut(k1, X_STIR, "6", 4, "K-02: K1, X limit 5, 6 lot");
        taklifKut(k1, X_STIR, "1", 2, "K1, X 1 lot (limit ichida)");
        taklifKut(k2, Y_STIR, "1", 2, "A-02: K2 (cheklov tayinlanmagan), Y 1 lot");
        taklifKut(k3, Y_STIR, "1", 2, "K-03: K3 (Hammaga ochiq), Y 1 lot");
        taklifKut(k3, X_STIR, "10", 2, "K-03: K3 (Hammaga ochiq), X 10 lot (limitdan ko'p)");
        taklifKut(k4, Y_STIR, "1", 2, "CD-01: K4 (Ro'yxat o'chirilgan), Y 1 lot");
        // T: narx va lot validatsiyalari (K3, X)
        taklifRad(k3, X_STIR, "1", "0", "T-01: narx 0");
        taklifRad(k3, X_STIR, "1", "-1000", "T-01: manfiy narx");
        taklifRad(k3, X_STIR, "1", "10000000000001", "T-02: narx 10 trln + 1");
        taklifRad(k3, X_STIR, "1", "1000.555", "T-03: narx 1000.555 (3 xona)");
        taklifRad(k3, X_STIR, "0", null, "T-04: lot 0");
        taklifRad(k3, X_STIR, "1000000001", null, "T-05: lot 1 000 000 001");
        taklifRad(k3, X_STIR, "200", "9000000000000", "T-06: 9 trln × 200 lot");
        taklifRad(k3, X_STIR, "1", "99999999999999999999", "T-07: juda uzun narx");
        System.out.println("Mening takliflarim:\n" + takliflarMatn());
        yakuniyTekshiruv();
    }
}
