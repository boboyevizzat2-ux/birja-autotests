package uz.ebirja.tests;

import org.openqa.selenium.*;
import org.testng.annotations.Test;
import uz.ebirja.pages.KontraktForma;

import java.util.*;

import static uz.ebirja.base.Ui.*;

/**
 * Tartibga solinadigan mahsulot — SUBSIDIYA (tur Oddiy, cheklovlar Subsidiya to'lovi + Subsidiya limiti).
 * Test case: SB-01, SB-06, SB-09, SB-10, A-13, A-14.
 *   SB1 — treyder "Subsidiya uchun" ✓ (SB-06); tasdiqlashda subsidiya turi "Mineral o'g'itlar"; A-13 (turi tanlanmasa Tasdiqlash yopiq)
 *         -> Y (subsidiya ro'yxatida yo'q) = 6 (SB-01)
 *   SB2 — tasdiqlashda cheklov tayinlanmaydi -> Listinglar: subsidiya turi "Tanlanmagan" (SB-09);
 *         keyin "Cheklov tayinlash" -> "Dizel yoqilg'isi" (A-14, SB-10)
 * SB-02, SB-07, SB-08 — xaridorning subsidiya hisobida mablag' kerak, bu testda yo'q.
 */
public class RegSubsidiyaTest extends RegAsos {

    @Test(priority = 1, description = "Subsidiya mahsuloti reestrga")
    public void sb01_reestr() {
        moderatorKir();
        mahsulotTanla("subsidiya");
        reestrgaQosh("Oddiy", new String[]{"Subsidiya to'lovi", "Subsidiya limiti"}, null);
        karta("subsidy");
        String m = clean(driver.findElement(By.tagName("main")).getText());
        System.out.println("  Subsidiya ro'yxati tabi: " + cut(m, 300));
        yakuniyTekshiruv();
    }

    @Test(priority = 2, dependsOnMethods = "sb01_reestr", alwaysRun = true, description = "SB-06: Dilshod SB1 ni 'Subsidiya uchun' bilan yaratadi")
    public void sb02_kontraktlar() {
        shart(PRODUCT != null && mahsulotId != null, "mahsulot reestrda yo'q");
        Map<String, java.util.function.Consumer<KontraktForma>> m = new LinkedHashMap<>();
        m.put("SB1", f -> {
            WebElement cb = driver.findElement(By.name("isSubsidyRequested"));
            if (!cb.isSelected()) { realClick(driver, (WebElement) js().executeScript("return arguments[0].closest('label') || arguments[0].parentElement;", cb)); sleep(300); }
            if (!cb.isSelected()) js().executeScript("arguments[0].click()", cb);
            kutilgan(cb.isSelected(), "SB-06: formada 'Subsidiya uchun' belgilandi");
        });
        m.put("SB2", null);
        kontraktlarYarat(m);
    }

    /** Oynadagi subsidiya turini tanlaydi. */
    boolean subsidiyaTuri(String boshi) {
        try {
            String t = dlgSelect("Subsidiya turi", "", s -> s.startsWith(boshi));
            if (t == null) t = dlgSelect("Subsidiya turi", boshi.substring(0, 4), s -> s.startsWith(boshi));
            System.out.println("  subsidiya turi: " + t);
            return t != null;
        } catch (Throwable e) {
            System.out.println("  subsidiya turi tanlanmadi: " + cut(String.valueOf(e.getMessage()), 200));
            dump("subsidiya_turi");
            return false;
        }
    }

    @Test(priority = 3, dependsOnMethods = "sb02_kontraktlar", alwaysRun = true, description = "Tasdiqlash: A-13, SB-06, SB-09")
    public void sb03_tasdiqlash() {
        shart(K.size() == 2, "kontraktlar yaratilmagan");
        Map<String, java.util.function.Consumer<String>> s = new LinkedHashMap<>();
        s.put("SB1", m -> {
            kutilgan(m.toLowerCase().contains("subsidiya so") || m.toLowerCase().contains("subsidiya uchun"), "SB-06: tasdiqlash oynasida eslatma ('Mijoz subsidiya uchun deb belgilagan')");
            belgila("checkbox", "Subsidiya to'lovi", null, true);
            belgila("checkbox", "Subsidiya limiti", null, true);
            sleep(300);
            WebElement ok = dlgTugmaEl("Tasdiqlash");
            boolean turBor = !dialogMatn().contains("Tanlang") ;
            kutilgan(ok != null && (!ok.isEnabled() || "true".equals(ok.getAttribute("aria-disabled"))) || turBor,
                    "A-13: subsidiya turi tanlanmaguncha 'Tasdiqlash' yopiq (enabled=" + (ok != null && ok.isEnabled()) + ")");
            kutilgan(subsidiyaTuri("Mineral"), "SB1: subsidiya turi 'Mineral o'g'itlar' tanlandi");
        });
        hammasiniTasdiqla(s);
        // SB-06: listingda "Subsidiya so'ralgan"
        WebElement r1 = listingQatori(K.get("SB1").raqam);
        kutilgan(r1 != null && clean(r1.getText()).contains("Subsidiya so'ralgan"), "SB-06: Listinglar'da SB1 'Subsidiya so'ralgan' — " + (r1 == null ? "yo'q" : clean(r1.getText())));
        String t1 = listingUstun(K.get("SB1").raqam, "Subsidiya turi");
        kutilgan(t1 != null && t1.contains("Mineral"), "SB1 subsidiya turi: " + t1);
        // SB-09
        String t2 = listingUstun(K.get("SB2").raqam, "Subsidiya turi");
        kutilgan(t2 != null && t2.contains("Tanlanmagan"), "SB-09: SB2 subsidiya turi ustunida 'Tanlanmagan' — " + t2);
        // A-14 / SB-10: SB2 ga "Cheklov tayinlash" orqali subsidiya turi
        if (listingAmali(K.get("SB2").raqam, "Cheklov tayinlash")) {
            belgila("checkbox", "Subsidiya to'lovi", null, true);
            belgila("checkbox", "Subsidiya limiti", null, true);
            boolean tur = subsidiyaTuri("Dizel");
            List<String> x = new ArrayList<>();
            boolean ok = tur && dlgSaqla("Saqlash", x);
            if (!ok) { dump("SB10"); yop(); }
            kutilgan(ok, "A-14: SB2 ga 'Dizel yoqilg'isi' saqlandi " + x);
            String t2b = listingUstun(K.get("SB2").raqam, "Subsidiya turi");
            kutilgan(t2b != null && t2b.contains("Dizel"), "A-14/SB-10: SB2 subsidiya turi ustuni: " + t2b);
            if (listingAmali(K.get("SB2").raqam, "Cheklov tayinlash")) {
                kutilgan(dialogMatn().contains("Dizel"), "A-14: oynani qayta ochganda tur saqlangan");
                yop();
            }
        } else kutilgan(false, "A-14: 'Cheklov tayinlash' oynasi ochilmadi");
        yakuniyTekshiruv();
    }

    @Test(priority = 4, dependsOnMethods = "sb03_tasdiqlash", alwaysRun = true, description = "SB-01: subsidiya ro'yxatida yo'q xaridor")
    public void sb04_takliflar() {
        shart(K.size() == 2 && K.values().stream().allMatch(k -> k.raqam != null), "kontraktlar tasdiqlanmagan");
        savdoniBoshla();
        taklifKut(K.get("SB1"), Y_STIR, "1", 6, "SB-01: SB1, Y subsidiya ro'yxatida yo'q");
        System.out.println("Mening takliflarim:\n" + takliflarMatn());
        yakuniyTekshiruv();
    }
}
