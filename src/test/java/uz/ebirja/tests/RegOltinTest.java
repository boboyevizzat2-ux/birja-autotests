package uz.ebirja.tests;

import org.openqa.selenium.*;
import org.testng.annotations.Test;
import uz.ebirja.base.Moderator;

import java.util.*;

import static uz.ebirja.base.Ui.*;

/**
 * Tartibga solinadigan mahsulot — tovar turi OLTIN.
 * Test case: G-01..G-05, K-06 (+ B-03 4% belgisi).
 * Xaridorlar: A = X (4% belgisi bor), B = Z (4% belgisi yo'q), ikkalasi ro'yxatda.
 *   G1 — oltin turi 4%, "Ro'yxatga asosan" -> A = 2, B = 6; "Hammaga ochiq" tanlab bo'lmaydi (K-06)
 *   G2 — oltin turi Oddiy                  -> B = 2 (G-02); 15% tanlanganda "Hammaga ochiq" ruxsat (G-04)
 *   G3 — cheklov tayinlanmaydi             -> "Oltin turi" ustuni (G-03)
 */
public class RegOltinTest extends RegAsos {

    static final String T4 = "Foiz bo'yicha";   // Foiz bo'yicha tushirish (4%)
    static final String T15 = "Qisman";         // Qisman to'lov (15% oldindan)

    @Test(priority = 1, description = "Oltin mahsulot reestrga (standart turi Oddiy, Ro'yxat asosida)")
    public void g01_reestr() {
        moderatorKir();
        mahsulotTanla("oltin");
        reestrgaQosh("Oltin", new String[]{"Ro'yxat asosida"}, () -> belgila("radio", "Oddiy", "goldType", true));
        yakuniyTekshiruv();
    }

    @Test(priority = 2, dependsOnMethods = "g01_reestr", alwaysRun = true, description = "Dilshod 3 ta oltin kontrakti yaratadi")
    public void g02_kontraktlar() {
        shart(PRODUCT != null && mahsulotId != null, "mahsulot reestrda yo'q");
        kontraktlarYarat("G1", "G2", "G3");
    }

    @Test(priority = 3, dependsOnMethods = "g02_kontraktlar", alwaysRun = true, description = "A (4% bor) va B (4% yo'q) xaridorlar ro'yxatiga")
    public void g03_xaridorlar() {
        shart(mahsulotId != null, "mahsulot reestrda yo'q");
        moderatorKir();
        List<String> x = new ArrayList<>();
        kutilgan(xaridorQosh(X_STIR, null, true, x), "A (" + X_STIR + ") 4% belgisi bilan qo'shildi " + x);
        x.clear();
        kutilgan(xaridorQosh(Z_STIR, null, false, x), "B (" + Z_STIR + ") 4% belgisisiz qo'shildi " + x);
        String ua = belgi4(X_STIR), ub = belgi4(Z_STIR);
        kutilgan(ua.contains("Ha") && !ub.contains("Ha"), "'4% belgisi' ustuni: A='" + ua + "', B='" + ub + "'");
        yakuniyTekshiruv();
    }

    /** Xaridorlar ro'yxatidagi "4% belgisi" ustuni (jadval qayta chizilsa qayta o'qiladi). */
    String belgi4(String stir) {
        xaridorQatori(stir);
        String v = waitFor(() -> { WebElement r = uz.ebirja.pages.Jadval.qator(driver, stir); return r == null ? null : uz.ebirja.pages.Jadval.ustun(driver, r, "4% belgisi"); }, 8);
        return v == null ? "" : v;
    }

    @Test(priority = 4, dependsOnMethods = "g02_kontraktlar", alwaysRun = true, description = "Tasdiqlash: oltin turlari, K-06, G-04, G-03")
    public void g04_tasdiqlash() {
        shart(K.size() == 3, "kontraktlar yaratilmagan");
        Map<String, java.util.function.Consumer<String>> s = new LinkedHashMap<>();
        s.put("G1", m -> {
            belgila("radio", T4, "goldType", true);
            sleep(300);
            boolean[] h = belgiHolati("radio", "Hammaga ochiq", "buyers");
            kutilgan(h == null || h[1], "K-06: 4% kontraktda 'Hammaga ochiq' tanlab bo'lmaydi (" + (h == null ? "variant yo'q" : "disabled=" + h[1]) + ")");
            belgila("radio", "Ro'yxatga asosan", "buyers", true);
        });
        s.put("G2", m -> {
            belgila("radio", T15, "goldType", true);
            sleep(300);
            boolean[] h = belgiHolati("radio", "Hammaga ochiq", "buyers");
            kutilgan(h != null && !h[1], "G-04: 15% kontraktda 'Hammaga ochiq' ruxsat etiladi");
            belgila("radio", "Oddiy", "goldType", true);
            belgila("radio", "Ro'yxatga asosan", "buyers", true);
        });
        hammasiniTasdiqla(s);
        String t1 = listingUstun(K.get("G1").raqam, "Oltin turi"), t2 = listingUstun(K.get("G2").raqam, "Oltin turi"), t3 = listingUstun(K.get("G3").raqam, "Oltin turi");
        kutilgan(t1 != null && t1.contains("4%"), "G1 oltin turi: " + t1);
        kutilgan(t2 != null && t2.contains("Oddiy"), "G2 oltin turi: " + t2);
        kutilgan(t3 != null && !t3.contains("(standart)"), "G-03: G3 (cheklovsiz) oltin turi ustuni: '" + t3 + "' — '(standart)' bo'lmasligi kerak, kutilgan 'Tanlanmagan (amalda: Oddiy)'");
        yakuniyTekshiruv();
    }

    @Test(priority = 5, dependsOnMethods = "g04_tasdiqlash", alwaysRun = true, description = "G-01, G-02, G-05: takliflar")
    public void g05_takliflar() {
        shart(K.size() == 3 && K.values().stream().allMatch(k -> k.raqam != null), "kontraktlar tasdiqlanmagan");
        savdoniBoshla();
        Kontrakt g1 = K.get("G1"), g2 = K.get("G2");
        taklifKut(g1, X_STIR, "1", 2, "G-01: G1 (4%), A — 4% belgisi bor");
        taklifKut(g1, Z_STIR, "1", 6, "G-01: G1 (4%), B — 4% belgisi yo'q");
        taklifKut(g2, Z_STIR, "1", 2, "G-02: G2 (Oddiy), B");
        System.out.println("Mening takliflarim:\n" + takliflarMatn());
        // G-05: savdodagi kontraktda oltin turi qulflangan
        moderatorKir();
        if (listingAmali(g1.raqam, "Cheklov tayinlash")) {
            String m = dialogMatn();
            boolean[] a = belgiHolati("radio", T4, "goldType"), o = belgiHolati("radio", "Oddiy", "goldType");
            kutilgan(a != null && o != null && a[1] && o[1], "G-05: savdodagi kontraktda oltin turi qulflangan. Oyna: " + cut(m, 200));
            yop();
        } else kutilgan(false, "G-05: 'Cheklov tayinlash' oynasi ochilmadi");
        yakuniyTekshiruv();
    }
}
