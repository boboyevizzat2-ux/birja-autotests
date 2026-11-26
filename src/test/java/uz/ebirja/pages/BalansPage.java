package uz.ebirja.pages;

import org.openqa.selenium.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static uz.ebirja.base.Ui.*;

/**
 * Treyder: HKKP → Balans (/uz/trader/hkkp/transaction). Faqat O'QIYDI, hech narsa tekshirmaydi (tekshiruvlar testda).
 * Balans tartibi hamma joyda: [barcha, erkin, band, qarzdorlik].
 * Operatsiya yozuvi hamma joyda: "to'lovchi>oluvchi summa maqsad".
 */
public class BalansPage {
    private final WebDriver d;
    private final String url;

    public BalansPage(WebDriver d, String traderBase) {
        this.d = d;
        this.url = traderBase + "/uz/trader/hkkp/transaction";
    }

    /** "Mijozlar balansi" (bo'lmasa "Broker balansi") jadvalidan STIR bo'yicha balans. Topilmasa null. */
    public BigDecimal[] mijoz(String stir) {
        d.get(url);
        jadvalKut(d);
        for (String tab : new String[]{"Mijozlar balansi", "Broker balansi"}) {
            tabniOchish(tab);
            Jadval.qidir(d, stir);
            WebElement r = Jadval.qator(d, stir);
            if (r == null) continue;
            List<BigDecimal> nums = new ArrayList<>();
            for (WebElement td : r.findElements(By.cssSelector("td"))) {
                String v = td.getText().replace("UZS", "").replaceAll("[\\s\\u00A0\\u202F]", "");
                if (v.matches("-?\\d+(\\.\\d+)?") && !v.equals(stir) && v.length() < 20) nums.add(new BigDecimal(v));
                else if (v.isEmpty() && !nums.isEmpty()) nums.add(BigDecimal.ZERO);
            }
            System.out.println("BALANS (" + tab + ") " + stir + ": " + clean(r.getText()));
            while (nums.size() < 4) nums.add(BigDecimal.ZERO);
            return nums.subList(nums.size() - 4, nums.size()).toArray(new BigDecimal[0]);
        }
        System.out.println("BALANS: " + stir + " topilmadi");
        return null;
    }

    /** "Broker balansi" kartochkasi (kirgan treyderning o'z kompaniyasi). O'qilmasa null. */
    public BigDecimal[] broker() {
        d.get(url + "?tab=broker-balance");
        jadvalKut(d);
        return waitFor(() -> {
            String m = Jadval.norm(clean(d.findElement(By.tagName("main")).getText()));
            BigDecimal[] x = new BigDecimal[4];
            String[] nom = {"Barcha mablag'lar:", "Erkin mablag'lar:", "Bandlangan mablag'lar:", "Qarzdorlik:"};
            for (int i = 0; i < 4; i++) {
                java.util.regex.Matcher q = java.util.regex.Pattern.compile(nom[i] + "[^0-9-]*(-?[0-9][0-9\\s\\u202F]*(?:\\.[0-9]+)?)").matcher(m);
                if (!q.find()) return null;
                x[i] = new BigDecimal(q.group(1).replaceAll("[\\s\\u202F]", ""));
            }
            return x[0].signum() == 0 ? null : x; // yuklanmaguncha 0.00 turadi
        }, 15);
    }

    /** Broker balansi tarixidagi shu bitim qatorlari (xom matn, tartib raqamisiz): "sana | vaqt | hujjat | bitim | to'lovchi | oluvchi | summa | UZS | maqsad". */
    public List<String> brokerQatorlari(String bitim) {
        // broker sahifasi allaqachon ochiq bo'lsa faqat qidiriladi (o'sha bitim qayta so'ralsa - yangilanadi)
        if (!ochiqmi("tab=broker-balance", bitim)) { d.get(url + "?tab=broker-balance"); jadvalKut(d); }
        Jadval.qidir(d, bitim);
        List<String> r = new ArrayList<>();
        for (WebElement tr : d.findElements(Jadval.QATORLAR)) {
            String tx = clean(tr.getText());
            if (tx.contains(bitim)) r.add(tx.replaceFirst("^[0-9]+ [|] ", ""));
        }
        System.out.println("OPERATSIYALAR " + bitim + ": " + r);
        return r;
    }

    /** Broker balansidagi shu bitim operatsiyalari. */
    public List<String> brokerOperatsiyalari(String bitim) {
        List<String> r = new ArrayList<>();
        for (String op : brokerQatorlari(bitim)) {
            String[] p = op.split(" [|] ");
            r.add(p.length >= 9 ? p[4] + ">" + p[5] + " " + p[6].replaceAll("[^0-9.]", "") + " " + tekis(p[8]) : "o'qilmadi: " + op);
        }
        return r;
    }

    /** Mijoz hisobining ICHI (STIR havolasi bosiladi): shu bitim operatsiyalari. */
    public List<String> hisobIchi(String stir, String bitim) {
        // hisob ichi allaqachon ochiq bo'lsa faqat qidiriladi (o'sha bitim qayta so'ralsa - yangilanadi)
        if (!ochiqmi("/" + stir + "/balance", bitim)) {
            d.get(url);
            jadvalKut(d);
            Jadval.qidir(d, stir);
            WebElement havola = waitFor(() -> visible(d, By.xpath("//main//tbody//a[normalize-space(.)='" + stir + "']")), 10);
            if (havola == null) throw new AssertionError("Balansda " + stir + " STIR havolasi yo'q");
            realClick(d, havola);
            if (waitFor(() -> d.getCurrentUrl().contains("/" + stir + "/balance") ? Boolean.TRUE : null, 10) == null)
                throw new AssertionError("STIR bosilganda hisob ichi ochilmadi: " + d.getCurrentUrl());
            jadvalKut(d);
            if (waitFor(() -> visible(d, Jadval.QIDIRISH), 10) == null) throw new AssertionError("Hisob ichida qidirish maydoni yo'q");
        }
        Jadval.qidir(d, bitim);
        List<String> r = new ArrayList<>();
        for (WebElement tr : d.findElements(Jadval.QATORLAR)) {
            List<WebElement> td = tr.findElements(By.cssSelector("td"));
            if (td.size() < 8 || !td.get(3).getText().contains(bitim)) continue;
            r.add(stirOl(td.get(4).getText()) + ">" + stirOl(td.get(5).getText()) + " " + td.get(6).getText().replaceAll("[^0-9.]", "") + " " + tekis(td.get(7).getText()));
        }
        return r;
    }

    /** Kerakli sahifa ochiqmi. Shu bitim allaqachon qidirilgan bo'lsa ma'lumot eskirgan bo'lishi mumkin - false (qayta ochiladi). */
    private boolean ochiqmi(String urlQismi, String bitim) {
        if (!d.getCurrentUrl().contains(urlQismi)) return false;
        WebElement s = visible(d, Jadval.QIDIRISH);
        return s != null && !bitim.equals(s.getAttribute("value"));
    }

    private void tabniOchish(String tab) {
        WebElement b = visible(d, By.xpath("//main//*[normalize-space(text())='" + tab + "']"));
        if (b != null) { jsClick(d, b); jadvalKut(d); }
    }

    /** Harf kattaligi va apostrof turlari farqini olib tashlaydi (to'lash / toʻlash / to’lash). */
    static String tekis(String t) { return t.toLowerCase().replaceAll("['`\\u02BB\\u02BC\\u2018\\u2019]", "").replaceAll("\\s+", " "); }

    static String stirOl(String t) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b([0-9]{9,14})\\b").matcher(t);
        String r = "?";
        while (m.find()) r = m.group(1);
        return r;
    }
}
