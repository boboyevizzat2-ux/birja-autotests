package uz.ebirja.tests;

import org.openqa.selenium.*;
import org.testng.annotations.Test;

import java.util.*;

import static uz.ebirja.base.Ui.*;

/**
 * Tartibga solinadigan mahsulot — tovar turi KUMUSH.
 * Test case: K-01, K-03, K-07, A-17, A-03 (kumush uchun).
 *   KM1 — "Ro'yxatga asosan" -> X (ro'yxatda) = 2, Y = 6
 *   KM2 — "Hammaga ochiq"    -> Y = 2
 *   KM3 — tasdiqlashda tayinlanmaydi, keyin "Cheklov tayinlash" -> "Ro'yxatga asosan" (A-03) -> Y = 6
 */
public class RegKumushTest extends RegAsos {

    @Test(priority = 1, description = "Kumush mahsulot reestrga (Ro'yxat asosida)")
    public void km01_reestr() {
        moderatorKir();
        mahsulotTanla("kumush");
        reestrgaQosh("Kumush", new String[]{"Ro'yxat asosida"}, null);
        yakuniyTekshiruv();
    }

    @Test(priority = 2, dependsOnMethods = "km01_reestr", alwaysRun = true, description = "Dilshod 3 ta kumush kontrakti yaratadi")
    public void km02_kontraktlar() {
        shart(PRODUCT != null && mahsulotId != null, "mahsulot reestrda yo'q");
        kontraktlarYarat("KM1", "KM2", "KM3");
    }

    @Test(priority = 3, dependsOnMethods = "km02_kontraktlar", alwaysRun = true, description = "X xaridorlar ro'yxatiga")
    public void km03_xaridor() {
        shart(mahsulotId != null, "mahsulot reestrda yo'q");
        moderatorKir();
        List<String> x = new ArrayList<>();
        kutilgan(xaridorQosh(X_STIR, null, false, x) && xaridorQatori(X_STIR) != null, "X (" + X_STIR + ") qo'shildi " + x);
        yakuniyTekshiruv();
    }

    @Test(priority = 4, dependsOnMethods = "km02_kontraktlar", alwaysRun = true, description = "Tasdiqlash, A-03, A-17, K-07")
    public void km04_tasdiqlash() {
        shart(K.size() == 3, "kontraktlar yaratilmagan");
        Map<String, java.util.function.Consumer<String>> s = new LinkedHashMap<>();
        s.put("KM1", m -> belgila("radio", "Ro'yxatga asosan", "buyers", true));
        s.put("KM2", m -> belgila("radio", "Hammaga ochiq", "buyers", true));
        hammasiniTasdiqla(s);
        String c1 = listingUstun(K.get("KM1").raqam, "Kontrakt xaridorlari"), c2 = listingUstun(K.get("KM2").raqam, "Kontrakt xaridorlari");
        kutilgan(c1 != null && c1.contains("Ro'yxatga asosan"), "K-07: KM1 = " + c1);
        kutilgan(c2 != null && c2.contains("Hammaga ochiq"), "K-07: KM2 = " + c2);
        WebElement r3 = listingQatori(K.get("KM3").raqam);
        kutilgan(r3 != null && clean(r3.getText()).contains("tayinlanmagan"), "KM3 'Cheklov tayinlanmagan' — " + (r3 == null ? "yo'q" : clean(r3.getText())));
        // A-17: savdoda bo'lmagan kontraktda joriy holat oldindan to'ldirilgan
        org.testng.Assert.assertTrue(listingAmali(K.get("KM1").raqam, "Cheklov tayinlash"), "'Cheklov tayinlash' oynasi ochilmadi");
        String m = dialogMatn();
        boolean[] h = belgiHolati("radio", "Ro'yxatga asosan", "buyers");
        kutilgan(!m.contains("yuklab bo'lmadi") && h != null && h[0], "A-17: KM1 oynasi joriy holat bilan to'ldirilgan ('Ro'yxatga asosan' tanlangan). Oyna: " + cut(m, 250));
        yop();
        // A-03: KM3 ga keyin cheklov tayinlash
        kutilgan(cheklovTayinla(K.get("KM3").raqam, "Ro'yxatga asosan", null), "A-03: KM3 ga 'Ro'yxatga asosan' tayinlandi");
        String c3 = listingUstun(K.get("KM3").raqam, "Kontrakt xaridorlari");
        kutilgan(c3 != null && c3.contains("Ro'yxatga asosan"), "KM3 = " + c3);
        yakuniyTekshiruv();
    }

    @Test(priority = 5, dependsOnMethods = "km04_tasdiqlash", alwaysRun = true, description = "Takliflar")
    public void km05_takliflar() {
        shart(K.size() == 3 && K.values().stream().allMatch(k -> k.raqam != null), "kontraktlar tasdiqlanmagan");
        savdoniBoshla();
        taklifKut(K.get("KM1"), X_STIR, "1", 2, "KM1 Ro'yxatga asosan, X ro'yxatda");
        taklifKut(K.get("KM1"), Y_STIR, "1", 6, "K-01: KM1, Y ro'yxatda yo'q");
        taklifKut(K.get("KM2"), Y_STIR, "1", 2, "K-03: KM2 Hammaga ochiq, Y");
        taklifKut(K.get("KM3"), Y_STIR, "1", 6, "A-03: KM3 (keyin tayinlangan Ro'yxat), Y");
        System.out.println("Mening takliflarim:\n" + takliflarMatn());
        yakuniyTekshiruv();
    }
}
