package probe;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.testng.annotations.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * VAQTINCHALIK: kabinetlarni to'liq o'rganish (sayt xaritasi uchun).
 * -Dwho=trader1,trader2,admin1,admin2   -Droles=true (moderator rollarini aylanish)
 */
public class ProbeExplore {
    WebDriver d; JavascriptExecutor js; Properties p = new Properties(); PrintWriter w;
    Set<String> seen = new HashSet<>();

    void log(String s) { System.out.println(s); w.println(s); w.flush(); }

    String clean(String s) { return s == null ? "" : s.replace("\n", " ").replaceAll("\\s+", " ").trim(); }

    boolean isTrader(String who) { return who.startsWith("trader"); }

    void login(String who) throws Exception {
        String base = isTrader(who) ? p.getProperty("trader.url") : p.getProperty("admin.url");
        d.get(base + "/uz/auth/login");
        Thread.sleep(5000);
        if (isTrader(who)) d.findElement(By.xpath("//button[contains(normalize-space(.),'Birja savdolari')]")).click();
        d.findElement(By.name("userName")).sendKeys(p.getProperty(who + ".login"));
        d.findElement(By.name("password")).sendKeys(p.getProperty(who + ".password"));
        d.findElement(By.xpath("//button[@type='submit' and normalize-space(.)='Kirish']")).click();
        for (int i = 0; i < 20 && d.getCurrentUrl().contains("/auth/"); i++) Thread.sleep(1000);
        Thread.sleep(3000);
    }

    /** Chap menyudagi barcha sahifalar (ochiladigan bo'limlar ham ochiladi). */
    LinkedHashMap<String, String> menu(String base) throws Exception {
        LinkedHashMap<String, String> links = new LinkedHashMap<>();
        String home = d.getCurrentUrl();
        collect(links, base, "");
        List<String> groups = new ArrayList<>();
        for (WebElement g : d.findElements(By.cssSelector("button[href]")))
            try { if (g.isDisplayed() && !g.getText().isBlank()) groups.add(clean(g.getText())); } catch (Exception ignore) {}
        for (String g : groups) {
            d.get(home); Thread.sleep(2500);
            try {
                String first = g.replaceAll(" \\d+$", "").replace("\"", "");
                WebElement b = d.findElement(By.xpath("//button[@href and starts-with(normalize-space(.),\"" + first + "\")]"));
                js.executeScript("arguments[0].scrollIntoView({block:'center'})", b);
                b.click(); Thread.sleep(1200);
                // ichki ochiladigan bo'limlar (masalan Listing > Mahsulotlar)
                for (WebElement sub : d.findElements(By.cssSelector("button[href]")))
                    try { if (sub.isDisplayed() && !groups.contains(clean(sub.getText())) && !sub.getText().isBlank()) { sub.click(); Thread.sleep(800); } } catch (Exception ignore) {}
            } catch (Exception ignore) {}
            collect(links, base, g + " / ");
        }
        return links;
    }

    void collect(Map<String, String> links, String base, String prefix) {
        for (WebElement a : d.findElements(By.cssSelector("a[href*='/uz/']:not(tbody a)")))
            try {
                String h = a.getAttribute("href").replace("#", "");
                if (a.isDisplayed() && h.startsWith(base) && !a.getText().isBlank() && !h.contains("/auth/"))
                    links.putIfAbsent(h, prefix + clean(a.getText()).replaceAll(" \\d+$", ""));
            } catch (Exception ignore) {}
    }

    void page(String base, String url, String name) throws Exception {
        d.get(url);
        Thread.sleep(4000);
        log("\n### " + name + "   (" + url.replace(base, "") + ")");
        if (!d.getCurrentUrl().replace("#", "").equals(url)) log("  yo'naltirildi: " + d.getCurrentUrl().replace(base, ""));
        Set<String> tabs = new LinkedHashSet<>(), btns = new LinkedHashSet<>(), inputs = new LinkedHashSet<>();
        Set<String> menuTexts = new HashSet<>();
        for (WebElement a : d.findElements(By.cssSelector("aside *, nav *"))) try { menuTexts.add(clean(a.getText())); } catch (Exception ignore) {}
        for (WebElement e : d.findElements(By.cssSelector("main button, main [role=tab], main a[href]:not(tbody a)"))) {
            try {
                if (!e.isDisplayed()) continue;
                String t = clean(e.getText());
                if (t.isEmpty() || t.length() > 45 || menuTexts.contains(t) || e.findElements(By.xpath("./ancestor::tbody")).size() > 0) continue;
                if (e.getAttribute("role") != null && e.getAttribute("role").equals("tab")) tabs.add(t); else btns.add(t);
            } catch (Exception ignore) {}
        }
        for (WebElement e : d.findElements(By.cssSelector("main input:not([type=checkbox]):not([type=file]), main textarea")))
            try { if (e.isDisplayed()) { String ph = e.getAttribute("placeholder"); if (ph != null && !ph.isBlank()) inputs.add(ph); } } catch (Exception ignore) {}
        List<String> th = new ArrayList<>();
        for (WebElement e : d.findElements(By.cssSelector("main thead th"))) try { String t = clean(e.getText()); if (!t.isEmpty()) th.add(t); } catch (Exception ignore) {}
        int rows = d.findElements(By.cssSelector("main tbody tr")).size();
        if (!tabs.isEmpty()) log("  Tablar: " + tabs);
        if (!btns.isEmpty()) log("  Tugmalar: " + btns);
        if (!inputs.isEmpty()) log("  Qidiruv/filtrlar: " + inputs);
        if (!th.isEmpty()) log("  Jadval (" + rows + " qator): " + (th.size() > 15 ? th.subList(0, 15) + " ... jami " + th.size() + " ustun" : th));
        if (tabs.isEmpty() && btns.isEmpty() && th.isEmpty()) {
            String b = clean(d.findElement(By.tagName("main")).getText());
            log("  Matn: " + b.substring(0, Math.min(250, b.length())));
        }
    }

    /** Moderator: rol almashtirish oynasidagi rollar ro'yxati. */
    List<String> roles() throws Exception {
        openRoleDialog();
        Set<String> r = new LinkedHashSet<>();
        for (WebElement e : d.findElements(By.xpath("//div[starts-with(normalize-space(text()),'MODERATOR_')]")))
            try { if (e.isDisplayed()) r.add(clean(e.getText())); } catch (Exception ignore) {}
        d.navigate().refresh();
        Thread.sleep(4000);
        return new ArrayList<>(r);
    }

    String currentRole() {
        for (WebElement e : d.findElements(By.xpath("//span[contains(@class,'w-fit')]")))
            try { if (e.isDisplayed() && (e.getText().contains("MODERATOR") || e.getText().contains("ADMIN"))) return e.getText().split("\n")[0].trim(); } catch (Exception ignore) {}
        return "?";
    }

    void openRoleDialog() throws Exception {
        for (WebElement e : d.findElements(By.xpath("//span[contains(@class,'w-fit')]")))
            if (e.isDisplayed() && e.getText().contains("MODERATOR")) { js.executeScript("arguments[0].click()", e); break; }
        Thread.sleep(1000);
        d.findElement(By.xpath("//span[normalize-space(.)='Rol almashtrish']")).click();
        Thread.sleep(2000);
    }

    void switchRole(String role) throws Exception {
        openRoleDialog();
        WebElement opt = null;
        for (WebElement e : d.findElements(By.xpath("//div[normalize-space(text())='" + role + "']")))
            if (e.isDisplayed()) opt = e;
        js.executeScript("arguments[0].click()", opt);
        Thread.sleep(700);
        js.executeScript("arguments[0].click()", d.findElement(By.xpath("//button[normalize-space(.)='Saqlash']")));
        Thread.sleep(5000);
        d.get(p.getProperty("admin.url") + "/uz/moderationv2/profile");
        Thread.sleep(4000);
    }

    @Test
    public void explore() throws Exception {
        try (Reader r = new InputStreamReader(new FileInputStream("login.properties"), StandardCharsets.UTF_8)) { p.load(r); }
        String out = System.getProperty("probe.out");
        // Oldin o'rganilgan sahifalar (qayta ochilmaydi)
        String skip = System.getProperty("skipFile", "");
        if (!skip.isEmpty() && new File(skip).exists())
            for (String line : Files.readAllLines(Paths.get(skip), StandardCharsets.UTF_8)) {
                int i = line.indexOf("(/uz/");
                if (line.startsWith("### ") && i > 0) seen.add(line.substring(i + 1, line.lastIndexOf(')')));
            }
        System.out.println("Oldin o'rganilgan sahifalar: " + seen.size());
        for (String who : System.getProperty("who", "trader1,trader2,admin1,admin2").split(",")) {
            w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(Paths.get(out, "xarita_" + who + ".txt").toFile()), StandardCharsets.UTF_8));
            ChromeOptions o = new ChromeOptions();
            o.addArguments("--window-size=1600,1000");
            d = new ChromeDriver(o);
            js = (JavascriptExecutor) d;
            try {
                login(who);
                String base = isTrader(who) ? p.getProperty("trader.url") : p.getProperty("admin.url");
                log("################ " + who + "  (" + p.getProperty(who + ".login") + ")");
                String body = clean(d.findElement(By.tagName("body")).getText());
                log("Bosh sahifa: " + d.getCurrentUrl().replace(base, ""));
                log("Yuqori qism: " + body.substring(0, Math.min(400, body.length())));

                if (!isTrader(who) && Boolean.getBoolean("roles") && currentRole().startsWith("MODERATOR")) {
                    String original = System.getProperty("restoreRole", currentRole());
                    log("Hozirgi rol: " + currentRole() + "; oxirida qaytariladigan rol: " + original);
                    List<String> rs = roles();
                    log("ROLLAR (" + rs.size() + "): " + rs);
                    for (String role : rs) {
                        try {
                            switchRole(role);
                            LinkedHashMap<String, String> m = menu(base);
                            log("\n=== ROL: " + role + "  -> " + m.size() + " ta sahifa");
                            m.forEach((u, t) -> log("   " + t + "  ->  " + u.replace(base, "")));
                            for (Map.Entry<String, String> e : m.entrySet()) {
                                String path = e.getKey().replace(base, "");
                                if (seen.contains(path)) continue;
                                seen.add(path);
                                try { page(base, e.getKey(), "[" + role + "] " + e.getValue()); } catch (Exception ex) { log("  SAHIFA XATO: " + ex.getClass().getSimpleName()); }
                            }
                        } catch (Exception e) { log("  ROL XATO " + role + ": " + e.getClass().getSimpleName()); }
                    }
                    switchRole(original);
                    log("\nAsl rolga qaytarildi: " + currentRole());
                } else {
                    if (!isTrader(who)) {
                        // Adminda rol almashtirish bormi?
                        try {
                            for (WebElement e : d.findElements(By.xpath("//span[contains(@class,'w-fit')]")))
                                if (e.isDisplayed() && e.getText().contains("ADMIN")) { js.executeScript("arguments[0].click()", e); break; }
                            Thread.sleep(1200);
                            boolean has = !d.findElements(By.xpath("//span[normalize-space(.)='Rol almashtrish']")).isEmpty();
                            log("Adminda rol almashtirish: " + (has ? "BOR" : "YO'Q"));
                            if (has) {
                                d.findElement(By.xpath("//span[normalize-space(.)='Rol almashtrish']")).click();
                                Thread.sleep(2000);
                                List<String> rr = new ArrayList<>();
                                for (WebElement e : d.findElements(By.xpath("//div[starts-with(normalize-space(text()),'MODERATOR_') or normalize-space(text())='ADMIN']")))
                                    if (e.isDisplayed()) rr.add(clean(e.getText()));
                                log("Admin rollari: " + rr);
                            }
                            d.navigate().refresh(); Thread.sleep(4000);
                        } catch (Exception e) { log("Admin rol tekshiruvi xato: " + e.getClass().getSimpleName()); }
                    }
                    LinkedHashMap<String, String> m = menu(base);
                    log("MENYU: " + m.size() + " ta sahifa");
                    m.forEach((u, t) -> log("   " + t + "  ->  " + u.replace(base, "")));
                    if (!Boolean.getBoolean("menuOnly"))
                        for (Map.Entry<String, String> e : m.entrySet()) {
                            String path = e.getKey().replace(base, "");
                            if (seen.contains(path)) continue;
                            seen.add(path);
                            try { page(base, e.getKey(), e.getValue()); } catch (Exception ex) { log("  SAHIFA XATO: " + ex.getClass().getSimpleName()); }
                        }
                }
            } catch (Exception e) {
                log("XATO: " + e);
            } finally {
                d.quit();
                w.close();
            }
        }
    }
}
