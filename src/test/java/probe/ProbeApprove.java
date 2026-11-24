package probe;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.testng.annotations.Test;
import uz.ebirja.base.BaseTest;
import uz.ebirja.base.Eimzo;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** VAQTINCHALIK: moderator kontraktni tasdiqlash joyini o'rganish (ekranda). */
public class ProbeApprove {
    WebDriver d; JavascriptExecutor js; Properties p = new Properties(); String out; int n = 0;

    void shot(String name) throws Exception {
        File s = ((TakesScreenshot) d).getScreenshotAs(OutputType.FILE);
        Files.copy(s.toPath(), Paths.get(out, name + ".png"), StandardCopyOption.REPLACE_EXISTING);
    }

    String clean(String s) { return s == null ? "" : s.replace("\n", " | ").replaceAll("\\s+", " ").trim(); }

    void loginKey(String base, String jsh) throws Exception {
        d.get(base + "/uz/auth/loginkey");
        Thread.sleep(3000);
        d.manage().deleteAllCookies();
        js.executeScript("localStorage.clear(); sessionStorage.clear();");
        d.get(base + "/uz/auth/loginkey");
        Thread.sleep(6000);
        WebElement keySel = d.findElement(By.cssSelector("input[id^='react-select'], input#key"));
        boolean found = false;
        for (int t = 0; t < 10 && !found; t++) {
            try { keySel.click(); } catch (Exception e) { js.executeScript("arguments[0].focus()", keySel); }
            keySel.sendKeys(Keys.ARROW_DOWN);
            Thread.sleep(2000);
            for (WebElement opt : d.findElements(By.cssSelector("[id*='-option-']")))
                if (opt.isDisplayed() && opt.getText().contains(jsh)) {
                    js.executeScript("arguments[0].scrollIntoView({block:'center'})", opt);
                    opt.click(); found = true; break;
                }
            if (!found) { keySel.sendKeys(Keys.ESCAPE); Thread.sleep(1000); }
        }
        System.out.println("Kalit topildi: " + found);
        Thread.sleep(800);
        d.findElement(By.xpath("//button[@type='submit' and contains(normalize-space(.),'Tizimga kirish')]")).click();
        Eimzo.typePassword();
        for (int s = 0; s < 60 && d.getCurrentUrl().contains("/auth/"); s++) Thread.sleep(1000);
        Thread.sleep(3000);
        System.out.println("Kirildi: " + d.getCurrentUrl());
    }

    String currentRole() {
        for (WebElement e : d.findElements(By.xpath("//span[contains(@class,'w-fit')]")))
            try { if (e.isDisplayed() && e.getText().contains("MODERATOR")) return e.getText().split("\n")[0].trim(); } catch (Exception ignore) {}
        return "?";
    }

    void switchRole(String role) throws Exception {
        d.get(p.getProperty("admin.url") + "/uz/moderationv2/profile");
        Thread.sleep(5000);
        if (currentRole().equals(role)) return;
        boolean opened = false;
        for (int t = 0; t < 5 && !opened; t++) {
            for (WebElement e : d.findElements(By.xpath("//span[contains(@class,'w-fit')]")))
                if (e.isDisplayed() && e.getText().contains("MODERATOR")) { js.executeScript("arguments[0].click()", e); break; }
            Thread.sleep(1500);
            for (WebElement e : d.findElements(By.xpath("//span[normalize-space(.)='Rol almashtrish']")))
                if (e.isDisplayed()) { e.click(); opened = true; break; }
        }
        Thread.sleep(2000);
        WebElement opt = null;
        for (WebElement e : d.findElements(By.xpath("//div[normalize-space(text())='" + role + "']"))) if (e.isDisplayed()) opt = e;
        js.executeScript("arguments[0].click()", opt);
        Thread.sleep(700);
        js.executeScript("arguments[0].click()", d.findElement(By.xpath("//button[normalize-space(.)='Saqlash']")));
        Thread.sleep(5000);
        System.out.println("Rol: " + currentRole());
    }

    void dumpDialogs(String label) {
        for (WebElement m : d.findElements(By.cssSelector("[role=dialog], [class*=modal], [class*=Modal]")))
            try { if (m.isDisplayed() && !m.getText().isBlank()) System.out.println("  " + label + " OYNA: " + clean(m.getText()).substring(0, Math.min(700, clean(m.getText()).length()))); } catch (Exception ignore) {}
        for (WebElement e : d.findElements(By.cssSelector("[role=dialog] input, [role=dialog] textarea, [class*=modal] input, [class*=modal] textarea")))
            try { if (e.isDisplayed()) System.out.println("    maydon: type=" + e.getAttribute("type") + " name=" + e.getAttribute("name") + " id=" + e.getAttribute("id") + " ph=" + e.getAttribute("placeholder")); } catch (Exception ignore) {}
        for (WebElement e : d.findElements(By.cssSelector("[class*=toast], [class*=Toastify], [role=alert], [role=status]")))
            try { if (e.isDisplayed() && !e.getText().isBlank()) System.out.println("  XABAR: " + clean(e.getText())); } catch (Exception ignore) {}
    }

    WebElement findRow(String id) {
        for (WebElement r : d.findElements(By.cssSelector("main tbody tr"))) {
            List<WebElement> tds = r.findElements(By.cssSelector("td"));
            if (tds.size() > 4 && tds.get(4).getText().trim().equals(id)) return r;
        }
        return null;
    }

    /** Kontraktni avval joriy tabda, keyin boshqa tablarda qidiradi. */
    WebElement findRowInTabs(String id) throws Exception {
        WebElement r = findRow(id);
        if (r != null) { System.out.println("  (joriy tabda topildi)"); return r; }
        for (String tab : new String[]{"Yangi", "Ochilgan kontraktlar", "Rad etilgan", "Delisting"}) {
            for (WebElement b : d.findElements(By.xpath("//main//button[starts-with(normalize-space(.),'" + tab + "')]")))
                if (b.isDisplayed()) { b.click(); break; }
            Thread.sleep(4000);
            r = findRow(id);
            if (r != null) { System.out.println("  '" + tab + "' tabida topildi"); return r; }
        }
        return null;
    }

    /** Berilgan ID dagi kontraktni "⋮" -> "Tasdiqlash" orqali tasdiqlaydi. */
    void approve(String id) throws Exception {
        if (id.equals("latest")) {
            WebElement first = d.findElement(By.cssSelector("main tbody tr"));
            id = first.findElements(By.cssSelector("td")).get(4).getText().trim();
            System.setProperty("contractId", id);
            System.out.println("Eng yangi kontrakt: ID " + id + " | " + clean(first.getText()).substring(0, Math.min(160, clean(first.getText()).length())));
        }
        WebElement row = findRowInTabs(id);
        if (row == null) { System.out.println("ID " + id + " kontrakt hech qaysi tabda topilmadi"); return; }
        System.out.println("Kontrakt topildi: ID " + id + " | komissiya: " + clean(row.getText()).replaceAll(".*\\| (\\S+) \\| (\\S+) \\| (\\S+) \\| Hujjatlar.*", "rais=$1 kotib=$2 a'zo=$3"));
        List<WebElement> tds = row.findElements(By.cssSelector("td"));
        WebElement dots = tds.get(tds.size() - 1).findElement(By.cssSelector("button"));
        js.executeScript("arguments[0].scrollIntoView({block:'center'})", dots);
        dots.click();
        Thread.sleep(1200);
        WebElement ok = null;
        for (WebElement e : d.findElements(By.xpath("//*[normalize-space(text())='Tasdiqlash']"))) if (e.isDisplayed()) { ok = e; break; }
        ok.click();
        System.out.println(">>> 'Tasdiqlash' bosildi");
        Thread.sleep(2500);
        dumpDialogs("1");
        shot("tasdiqlash_oynasi");
        // Oynadagi tasdiqlash tugmasi
        WebElement confirm = null;
        for (WebElement b : d.findElements(By.xpath("//*[@role='dialog' or contains(@class,'modal')]//button")))
            try {
                String t = b.getText().trim();
                if (b.isDisplayed() && (t.equals("Tasdiqlash") || t.equals("Ha") || t.equals("Yuborish") || t.equals("Saqlash") || t.equals("Imzolash"))) confirm = b;
            } catch (Exception ignore) {}
        if (confirm == null) { System.out.println("Oynada tasdiqlash tugmasi topilmadi, to'xtayman"); return; }
        System.out.println(">>> Oynadagi '" + confirm.getText() + "' bosiladi");
        confirm.click();
        if (Boolean.parseBoolean(System.getProperty("sign", "true"))) Eimzo.typePassword();
        for (int s = 0; s < 6; s++) { Thread.sleep(1000); dumpDialogs("2." + s); }
        shot("tasdiqlash_natija");
        d.navigate().refresh();
        Thread.sleep(6000);
        for (WebElement r : d.findElements(By.cssSelector("main tbody tr"))) {
            List<WebElement> c = r.findElements(By.cssSelector("td"));
            if (c.size() > 4 && c.get(4).getText().trim().equals(id)) { System.out.println("Yangilangandan keyin: " + clean(r.getText()).replaceAll(".*\\| (\\S+) \\| (\\S+) \\| (\\S+) \\| Hujjatlar.*", "rais=$1 kotib=$2 a'zo=$3")); return; }
        }
        System.out.println("ID " + id + " endi 'Yangi' tabida yo'q (boshqa tabga o'tgan bo'lishi mumkin)");
    }

    @Test
    public void probe() throws Exception {
        try (Reader r = new InputStreamReader(new FileInputStream("login.properties"), StandardCharsets.UTF_8)) { p.load(r); }
        out = System.getProperty("probe.out");
        ChromeDriver cd = new ChromeDriver(BaseTest.chromeOptions());
        BaseTest.grantEimzo(cd, uz.ebirja.base.BaseTest.saytlar());
        d = cd; js = cd;
        String base = p.getProperty("admin.url");
        try {
            loginKey(base, p.getProperty("admin1.login"));
            String target0 = System.getProperty("contractId", "");
            String rolesProp = System.getProperty("roles", "");
            if (!target0.isEmpty() && !rolesProp.isEmpty()) {
                for (String role : rolesProp.split(",")) {
                    System.out.println("\n======== ROL: " + role);
                    switchRole(role);
                    d.get(base + "/uz/moderationv2/listing/contracts");
                    Thread.sleep(6000);
                    approve(System.getProperty("contractId"));
                }
                switchRole("MODERATOR_LISTING_MAIN");
                return;
            }
            switchRole(System.getProperty("role", "MODERATOR_LISTING_MAIN"));
            d.get(base + "/uz/moderationv2/listing/contracts");
            Thread.sleep(6000);
            List<String> tabs = new ArrayList<>();
            for (WebElement t : d.findElements(By.cssSelector("main button"))) try { if (t.isDisplayed() && !t.getText().isBlank() && t.getText().length() < 40) tabs.add(clean(t.getText())); } catch (Exception ignore) {}
            System.out.println("Tugmalar/tablar: " + tabs);
            List<String> th = new ArrayList<>();
            for (WebElement e : d.findElements(By.cssSelector("main thead th"))) th.add(clean(e.getText()));
            System.out.println("Ustunlar: " + th);
            List<WebElement> rows = d.findElements(By.cssSelector("main tbody tr"));
            System.out.println("Qatorlar: " + rows.size());
            for (int i = 0; i < Math.min(4, rows.size()); i++) {
                System.out.println("\n  QATOR " + (i + 1) + ": " + clean(rows.get(i).getText()).substring(0, Math.min(400, clean(rows.get(i).getText()).length())));
                List<WebElement> cells = rows.get(i).findElements(By.cssSelector("td"));
                WebElement last = cells.get(cells.size() - 1);
                String html = last.getAttribute("innerHTML").replaceAll("<svg.*?</svg>", "<svg/>").replaceAll("<path[^>]*>", "");
                System.out.println("  AMALLAR HTML: " + html.substring(0, Math.min(700, html.length())));
            }
            shot("moderator_kontraktlar");
            String target = System.getProperty("contractId", "");
            if (!target.isEmpty()) {
                approve(target);
                return;
            }
            // Birinchi qatordagi amal tugmasini bosib, nima ochilishini ko'ramiz
            if (!rows.isEmpty()) {
                List<WebElement> cells = rows.get(0).findElements(By.cssSelector("td"));
                List<WebElement> acts = cells.get(cells.size() - 1).findElements(By.cssSelector("button, a, svg"));
                System.out.println("\nAmallar ustunidagi elementlar: " + acts.size());
                if (!acts.isEmpty()) {
                    WebElement a = acts.get(0);
                    js.executeScript("arguments[0].scrollIntoView({block:'center'})", a);
                    try { a.click(); } catch (Exception e) { js.executeScript("arguments[0].dispatchEvent(new MouseEvent('click',{bubbles:true}))", a); }
                    Thread.sleep(2500);
                    for (WebElement m : d.findElements(By.cssSelector("[role=dialog], [role=menu], [class*=modal], [class*=dropdown], [class*=popover]")))
                        try { if (m.isDisplayed() && !m.getText().isBlank()) System.out.println("  OCHILDI: " + clean(m.getText()).substring(0, Math.min(600, clean(m.getText()).length()))); } catch (Exception ignore) {}
                    System.out.println("  URL: " + d.getCurrentUrl());
                    shot("moderator_amal_bosildi");
                }
            }
            Thread.sleep(2000);
        } finally {
            d.quit();
        }
    }
}
