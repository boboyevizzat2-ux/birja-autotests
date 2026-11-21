package probe;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.testng.annotations.Test;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** VAQTINCHALIK: admin saytidagi har bir rolni o'rganish uchun. */
public class ProbeAdmin {
    WebDriver d; String out; Properties p = new Properties(); int n = 0;

    String body() { return d.findElement(By.tagName("body")).getText(); }

    void shot(String name) throws Exception {
        File s = ((TakesScreenshot) d).getScreenshotAs(OutputType.FILE);
        Files.copy(s.toPath(), Paths.get(out, name + ".png"), StandardCopyOption.REPLACE_EXISTING);
    }

    /** Sahifaning qisqa tavsifi: sarlavha, tablar, filtrlar, tugmalar, jadval ustunlari. */
    void describe(String url, String label) throws Exception {
        d.get(url);
        Thread.sleep(4500);
        System.out.println("\n  --- " + label + "  (" + url.replace(p.getProperty("admin.url"), "") + ")");
        if (!d.getCurrentUrl().equals(url)) System.out.println("     YO'NALTIRILDI: " + d.getCurrentUrl());
        Set<String> btn = new LinkedHashSet<>();
        for (WebElement e : d.findElements(By.cssSelector("main button, main [role=tab], main a[href]"))) {
            try { if (e.isDisplayed()) { String t = e.getText().replace("\n", " ").trim(); if (!t.isEmpty() && t.length() < 40) btn.add(t); } } catch (Exception ignore) {}
        }
        Set<String> inp = new LinkedHashSet<>();
        for (WebElement e : d.findElements(By.cssSelector("main input, main select"))) {
            try { if (e.isDisplayed()) { String ph = e.getAttribute("placeholder"); inp.add((ph == null || ph.isEmpty()) ? e.getAttribute("type") : ph); } } catch (Exception ignore) {}
        }
        List<String> th = new ArrayList<>();
        for (WebElement e : d.findElements(By.cssSelector("main th"))) {
            try { String t = e.getText().replace("\n", " ").trim(); if (!t.isEmpty()) th.add(t); } catch (Exception ignore) {}
        }
        int rows = d.findElements(By.cssSelector("main tbody tr")).size();
        System.out.println("     Tugmalar: " + btn);
        System.out.println("     Maydonlar: " + inp);
        System.out.println("     Jadval: " + rows + " qator; ustunlar: " + th);
        String b = body();
        if (b.contains("Xatolik") || b.contains("Error") || b.contains("500") && b.contains("Server")) System.out.println("     !!! XATO MATNI BOR");
        if (rows == 0 && th.isEmpty()) System.out.println("     MATN: " + b.replace("\n", " | ").substring(Math.max(0, b.indexOf("Xabarnomalar")), Math.min(b.length(), Math.max(0, b.indexOf("Xabarnomalar")) + 400)));
        shot("rol_" + (++n));
    }

    void explore(String who) throws Exception {
        System.out.println("\n\n################ AKKAUNT: " + who + " ################");
        d.manage().deleteAllCookies();
        d.get(p.getProperty("admin.url") + "/uz/auth/login");
        Thread.sleep(4000);
        d.findElement(By.name("userName")).sendKeys(p.getProperty(who + ".login"));
        d.findElement(By.name("password")).sendKeys(p.getProperty(who + ".password"));
        d.findElement(By.xpath("//button[@type='submit' and normalize-space(.)='Kirish']")).click();
        for (int t = 0; t < 14; t++) {
            Thread.sleep(500);
            String bb = body();
            if (t >= 2 && t <= 7) System.out.println("Kirish bosilgandan 2 soniya keyin ekrandagi matn: " + bb.replace("\n", " | ").substring(0, Math.min(500, bb.length())));
        }
        System.out.println("Kirgandan keyin URL: " + d.getCurrentUrl());
        String b = body();
        int i = b.indexOf("Rol:");
        System.out.println("Rol: " + (i >= 0 ? b.substring(i, Math.min(b.length(), i + 60)).replace("\n", " ") : "topilmadi"));
        shot(who + "_profil");

        // Rol almashtirish menyusi bormi? (yuqoridagi rol nomini bosib ko'ramiz)
        try {
            WebElement role = d.findElement(By.xpath("//header//*[contains(text(),'MODERATOR') or contains(text(),'ADMIN') or contains(text(),'_')]"));
            System.out.println("Yuqoridagi rol yozuvi: " + role.getText());
            role.click();
            Thread.sleep(1500);
            List<String> opts = new ArrayList<>();
            for (WebElement o : d.findElements(By.cssSelector("[role=menu] *, [role=listbox] *, ul li")))
                if (o.isDisplayed() && !o.getText().isBlank()) opts.add(o.getText().replace("\n", " "));
            System.out.println("Rol bosilgandan keyin chiqqan variantlar: " + new LinkedHashSet<>(opts));
            shot(who + "_rol_menyu");
        } catch (Exception e) {
            System.out.println("Rol almashtirish menyusi topilmadi");
        }

        // Menyu: har bir ochiladigan bo'limni alohida ochib, havolalarni yig'amiz
        String base = p.getProperty("admin.url");
        String home = d.getCurrentUrl();
        Map<String, String> links = new LinkedHashMap<>();
        String sel = "a[href*='/uz/']:not(tbody a)";
        d.get(home); Thread.sleep(3000);
        for (WebElement a : d.findElements(By.cssSelector(sel)))
            if (a.isDisplayed() && a.getAttribute("href").startsWith(base) && !a.getText().isBlank()) links.putIfAbsent(a.getAttribute("href").replace("#", ""), a.getText().replace("\n", " "));
        List<String> groups = new ArrayList<>();
        for (WebElement g : d.findElements(By.cssSelector("button[href]")))
            if (g.isDisplayed() && !g.getText().isBlank()) groups.add(g.getText().replace("\n", " ").trim());
        System.out.println("Ochiladigan bo'limlar: " + groups);
        for (String g : groups) {
            d.get(home); Thread.sleep(3000);
            try {
                d.findElement(By.xpath("//button[@href and normalize-space(.)=\"" + g + "\"]")).click();
                Thread.sleep(1000);
            } catch (Exception ignore) {}
            for (WebElement a : d.findElements(By.cssSelector(sel)))
                if (a.isDisplayed() && a.getAttribute("href").startsWith(base) && !a.getText().isBlank()) links.putIfAbsent(a.getAttribute("href").replace("#", ""), g + " / " + a.getText().replace("\n", " "));
        }
        System.out.println("\nMENYU (" + links.size() + " ta sahifa):");
        links.forEach((u, t) -> System.out.println("  " + t + "  ->  " + u.replace(base, "")));
        for (Map.Entry<String, String> e : links.entrySet()) describe(e.getKey(), e.getValue());
    }

    @Test
    public void probe() throws Exception {
        try (Reader r = new InputStreamReader(new FileInputStream("login.properties"), "UTF-8")) { p.load(r); }
        out = System.getProperty("probe.out");
        for (String who : System.getProperty("who", "admin2,admin1").split(",")) {
            ChromeOptions o = new ChromeOptions();
            o.addArguments("--headless=new", "--window-size=1600,1000");
            d = new ChromeDriver(o);
            try { explore(who); } catch (Exception e) { System.out.println("XATO (" + who + "): " + e); }
            finally { d.quit(); }
        }
    }
}
