package uz.ebirja.tests;

import org.openqa.selenium.*;
import org.testng.annotations.Test;

import java.util.*;

import static uz.ebirja.base.Ui.*;

/**
 * Tartibga solinadigan mahsulot — tovar turi SPIRT.
 * Test case: R-07, S-03, S-04, S-06, S-09 (+ A-05, K-07).
 * Mahsulot Spirt, cheklov faqat "Ro'yxat asosida" (Limitsiz — ariza kvotasi tekshirilmaydi, R-07).
 *   S1 — "Ro'yxatga asosan", iste'mol spirti -> Y (ro'yxatda yo'q) = 6; X (ro'yxatda, arizasiz) 5 lot = 2 (kvota tekshirilmaydi)
 *   S2 — "Hammaga ochiq", iste'mol spirti   -> Y = 2; oynada "kvota tekshirilmaydi" ogohlantirishi
 * S-01, S-02, S-05 (ariza kvotasi) — tasdiqlangan etil spirti arizasi kerak, bu testda yo'q.
 */
public class RegSpirtTest extends RegAsos {

    static final String SPIRT_TURI = "Iste";   // "Isteʼmol etil spirti"

    @Test(priority = 1, description = "R-07: Spirt mahsulot, faqat Ro'yxat (Limitsiz)")
    public void s01_reestr() {
        moderatorKir();
        mahsulotTanla("spirt");
        reestrgaQosh("Spirt", new String[]{"Ro'yxat asosida"}, null);
        karta(null);
        String p = clean(driver.findElement(By.tagName("main")).getText());
        kutilgan(p.contains("Spirt") && !p.contains("Limit\n"), "R-07: kartada tur Spirt, cheklov faqat Ro'yxat");
        yakuniyTekshiruv();
    }

    @Test(priority = 2, dependsOnMethods = "s01_reestr", alwaysRun = true, description = "Dilshod 2 ta spirt kontrakti yaratadi")
    public void s02_kontraktlar() {
        shart(PRODUCT != null && mahsulotId != null, "mahsulot reestrda yo'q");
        kontraktlarYarat("S1", "S2");
    }

    @Test(priority = 3, dependsOnMethods = "s02_kontraktlar", alwaysRun = true, description = "X xaridorlar ro'yxatiga (spirtda limit kiritilmaydi)")
    public void s03_xaridor() {
        shart(mahsulotId != null, "mahsulot reestrda yo'q");
        moderatorKir();
        List<String> x = new ArrayList<>();
        boolean ok = xaridorQosh(X_STIR, null, false, x);
        kutilgan(ok && xaridorQatori(X_STIR) != null, "X (" + X_STIR + ") spirt xaridorlar ro'yxatiga qo'shildi " + x);
        WebElement r = xaridorQatori(X_STIR);
        if (r != null) System.out.println("  X qatori: " + clean(r.getText()));
        kutilgan(xaridorQatori(Y_STIR) == null, "Y ro'yxatda yo'q");
        yakuniyTekshiruv();
    }

    @Test(priority = 4, dependsOnMethods = "s02_kontraktlar", alwaysRun = true, description = "Tasdiqlash: spirt turi, S-09 ogohlantirish, S-06 qulf")
    public void s04_tasdiqlash() {
        shart(K.size() == 2, "kontraktlar yaratilmagan");
        Map<String, java.util.function.Consumer<String>> s = new LinkedHashMap<>();
        s.put("S1", m -> { belgila("radio", "Ro'yxatga asosan", "buyers", true); belgila("radio", SPIRT_TURI, "alcoholType", true); });
        s.put("S2", m -> {
            belgila("radio", "Hammaga ochiq", "buyers", true);
            belgila("radio", SPIRT_TURI, "alcoholType", true);
            String t = dialogMatn().toLowerCase();
            System.out.println("  S2 oyna (Hammaga ochiq): " + cut(dialogMatn(), 400));
            kutilgan(t.contains("kvota") || t.contains("tekshirilmaydi"), "S-09: 'Hammaga ochiq' tanlanganda kvota tekshirilmasligi haqida yozuv bor");
        });
        hammasiniTasdiqla(s);
        String t1 = listingUstun(K.get("S1").raqam, "Spirt turi");
        kutilgan(t1 != null && t1.contains("Iste"), "S1 spirt turi: " + t1);
        String c2 = listingUstun(K.get("S2").raqam, "Kontrakt xaridorlari");
        kutilgan(c2 != null && c2.contains("Hammaga ochiq"), "K-07: S2 kontrakt xaridorlari: " + c2);
        // S-06: tasdiqlangan kontraktda spirt turi o'zgarmaydi
        org.testng.Assert.assertTrue(listingAmali(K.get("S1").raqam, "Cheklov tayinlash"), "'Cheklov tayinlash' oynasi ochilmadi");
        boolean[] ist = belgiHolati("radio", "Iste", "alcoholType"), tex = belgiHolati("radio", "Texnik", "alcoholType");
        kutilgan(ist != null && tex != null && ist[1] && tex[1], "S-06: tasdiqlangan kontraktda spirt turi qulflangan (disabled)");
        yop();
        yakuniyTekshiruv();
    }

    @Test(priority = 5, dependsOnMethods = "s04_tasdiqlash", alwaysRun = true, description = "S-03, S-04, S-09: takliflar")
    public void s05_takliflar() {
        shart(K.size() == 2 && K.values().stream().allMatch(k -> k.raqam != null), "kontraktlar tasdiqlanmagan");
        savdoniBoshla();
        Kontrakt s1 = K.get("S1"), s2 = K.get("S2");
        taklifKut(s1, Y_STIR, "1", 6, "S-04: S1, Y ro'yxatda yo'q");
        taklifKut(s1, X_STIR, "5", 2, "S-03: S1, X ro'yxatda, arizasiz, Limit yo'q -> kvota tekshirilmaydi");
        taklifKut(s2, Y_STIR, "1", 2, "S-09: S2 Hammaga ochiq, Y ro'yxatda yo'q");
        System.out.println("Mening takliflarim:\n" + takliflarMatn());
        yakuniyTekshiruv();
    }
}
