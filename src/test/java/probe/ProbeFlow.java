package probe;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.testng.annotations.Test;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** VAQTINCHALIK: Treyder 1 auksion kontraktini yaratadi (ekranda). */
public class ProbeFlow {
    WebDriver d; String out; Properties p = new Properties(); int n = 0;
    JavascriptExecutor js;

    void shot(String name) throws Exception {
        File s = ((TakesScreenshot) d).getScreenshotAs(OutputType.FILE);
        Files.copy(s.toPath(), Paths.get(out, name + ".png"), StandardCopyOption.REPLACE_EXISTING);
    }

    String labelOf(WebElement input) {
        try {
            Object t = js.executeScript(
                "let el=arguments[0]; for(let i=0;i<8 && el;i++){ el=el.parentElement; if(!el) break; let l=el.querySelector('label'); if(l) return l.innerText; } return '?';", input);
            return String.valueOf(t).replace("\n", " ").trim();
        } catch (Exception e) { return "?"; }
    }

    String selectValue(WebElement input) {
        Object t = js.executeScript(
            "let c=arguments[0].closest('[class*=\"-container\"]'); return c? c.innerText : '';", input);
        return String.valueOf(t).trim();
    }

    boolean selectDisabled(WebElement input) {
        Object t = js.executeScript(
            "let c=arguments[0].closest('[class*=\"-container\"]'); if(!c) return true; return c.className.includes('disabled') || c.querySelector('[aria-disabled=\"true\"]')!=null || arguments[0].disabled;", input);
        return Boolean.TRUE.equals(t);
    }

    /** Bo'sh react-select ro'yxatlaridan birinchi variantni tanlaydi. Bir necha marta aylanadi (bog'liq maydonlar uchun). */
    void fillSelects() throws Exception {
        Set<String> done = new HashSet<>();
        for (int pass = 0; pass < 5; pass++) {
            boolean changed = false;
            for (WebElement in : d.findElements(By.cssSelector("main input[id^='react-select-']"))) {
                try {
                    String id = in.getAttribute("id");
                    if (done.contains(id) || selectDisabled(in)) continue;
                    String val = selectValue(in);
                    if (!val.isEmpty() && !val.toLowerCase().contains("tanlang")) { done.add(id); continue; }
                    js.executeScript("arguments[0].scrollIntoView({block:'center'})", in);
                    String label = labelOf(in);
                    String product = System.getProperty("product", "");
                    String pf = System.getProperty("productFile", "");
                    if (product.isEmpty() && !pf.isEmpty())
                        product = new String(Files.readAllBytes(Paths.get(pf)), java.nio.charset.StandardCharsets.UTF_8).trim();
                    if (label.startsWith("Mahsulot") && !product.isEmpty()) {
                        in.sendKeys(product);
                        Thread.sleep(3000);
                    } else {
                        in.sendKeys(Keys.ARROW_DOWN);
                        Thread.sleep(1800);
                    }
                    List<WebElement> opts = d.findElements(By.cssSelector("[id^='" + id.replace("-input", "") + "-option-']"));
                    if (opts.isEmpty()) { System.out.println("  [" + label + "] variant yo'q"); in.sendKeys(Keys.ESCAPE); done.add(id); continue; }
                    String first = opts.get(0).getText();
                    in.sendKeys(Keys.ENTER);
                    Thread.sleep(1200);
                    System.out.println("  [" + label + "] tanlandi: " + first + "   (jami " + opts.size() + " variant)");
                    done.add(id);
                    changed = true;
                } catch (StaleElementReferenceException ignore) { changed = true; }
            }
            if (!changed) break;
        }
    }

    void type(String name, String value) {
        for (WebElement e : d.findElements(By.name(name))) {
            if (!e.isDisplayed() || !e.isEnabled()) continue;
            if (e.getAttribute("value") != null && !e.getAttribute("value").isEmpty()) { System.out.println("  " + name + " allaqachon: " + e.getAttribute("value")); return; }
            js.executeScript("arguments[0].scrollIntoView({block:'center'})", e);
            e.sendKeys(value);
            System.out.println("  " + name + " = " + value);
            return;
        }
    }

    @Test
    public void probe() throws Exception {
        try (Reader r = new InputStreamReader(new FileInputStream("login.properties"), "UTF-8")) { p.load(r); }
        out = System.getProperty("probe.out");
        ChromeOptions co = uz.ebirja.base.BaseTest.chromeOptions();
        org.openqa.selenium.logging.LoggingPreferences lp = new org.openqa.selenium.logging.LoggingPreferences();
        lp.enable(org.openqa.selenium.logging.LogType.BROWSER, java.util.logging.Level.ALL);
        co.setCapability("goog:loggingPrefs", lp);
        ChromeDriver cd = new ChromeDriver(co);
        uz.ebirja.base.BaseTest.grantEimzo(cd, "https://test-app.ebirja.uz", "https://test-head.ebirja.uz");
        d = cd;
        js = (JavascriptExecutor) d;
        try {
            String base = p.getProperty("trader.url");
            d.get(base + "/uz/auth/loginkey");
            int pw = Integer.getInteger("permWait", 0);
            if (pw > 0) {
                System.out.println(">>> " + pw + " soniya kutaman: E-IMZO ruxsatini so'rasa, 'Ruxsat berish'ni bosing");
                Thread.sleep(pw * 1000L);
            }
            // ERI kalit orqali kirish (kontrakt yuborish uchun shart)
            d.manage().deleteAllCookies();
            js.executeScript("localStorage.clear(); sessionStorage.clear();");
            d.get(base + "/uz/auth/loginkey");
            Thread.sleep(6000);
            d.findElement(By.xpath("//button[contains(normalize-space(.),'Birja savdolari')]")).click();
            Thread.sleep(800);
            WebElement keySel = d.findElement(By.cssSelector("input[id^='react-select']"));
            keySel.click();
            keySel.sendKeys(Keys.ARROW_DOWN);
            Thread.sleep(3000);
            String jsh = p.getProperty("trader1.login");
            for (WebElement opt : d.findElements(By.cssSelector("[id*='-option-']")))
                if (opt.isDisplayed() && opt.getText().contains(jsh)) { opt.click(); break; }
            Thread.sleep(800);
            d.findElement(By.xpath("//button[@type='submit' and contains(normalize-space(.),'Tizimga kirish')]")).click();
            uz.ebirja.base.Eimzo.typePassword();
            for (int s = 0; s < 60 && d.getCurrentUrl().contains("/auth/"); s++) Thread.sleep(1000);
            System.out.println("ERI kalit bilan kirildi: " + d.getCurrentUrl());
            Thread.sleep(3000);

            d.get(base + "/uz/trader/listing/sales/create" + System.getProperty("draft", ""));
            Thread.sleep(6000);
            System.out.println("1-aylanish: ro'yxatlar");
            fillSelects();
            System.out.println("Matnli maydonlar:");
            type("amount", "1");
            type("mainPrice", "1000000");
            type("deliveryBasisDetail", "Avtotest");
            type("productLocationUz", "Toshkent sh., Avtotest ko'chasi 1");
            type("productLocationUzc", "Toshkent sh., Avtotest 1");
            type("productLocationRu", "Tashkent, Avtotest 1");
            type("deliveryTerm", "10");
            type("sellerDepositPercentage", "10");
            type("paymentTerm", "3");
            type("buyerDepositPercentage", "10");
            type("specialConditionUz", "Avtotest");
            type("specialConditionUzc", "Avtotest");
            type("specialConditionRu", "Avtotest");
            System.out.println("2-aylanish: ro'yxatlar (yangi ochilganlari)");
            fillSelects();

            System.out.println("3-aylanish: ombordan keyin ochilgan ro'yxatlar");
            fillSelects();
            type("productLocationUz", "Toshkent sh., Avtotest ko'chasi 1");
            type("productLocationUzc", "Toshkent sh., Avtotest 1");
            type("productLocationRu", "Tashkent, Avtotest 1");

            // Hujjat yuklash
            String pdf = new File("src/test/resources/test-hujjat.pdf").getAbsolutePath();
            List<WebElement> files = d.findElements(By.cssSelector("input[type=file]"));
            System.out.println("Fayl maydonlari: " + files.size());
            for (WebElement f : files) { f.sendKeys(pdf); Thread.sleep(2500); }

            shot("kontrakt_toldirildi");
            WebElement send = d.findElement(By.xpath("//button[contains(normalize-space(.),'Yuborish')]"));
            js.executeScript("arguments[0].scrollIntoView({block:'center'})", send);
            send.click();
            System.out.println("\n>>> 'Yuborish' bosildi");
            uz.ebirja.base.Eimzo.typePassword();
            for (int t = 0; t < 4; t++) {
                Thread.sleep(400);
                for (WebElement e : d.findElements(By.cssSelector("[class*=toast], [class*=Toastify], [role=alert], [role=status]")))
                    try { if (e.isDisplayed() && !e.getText().isBlank()) System.out.println("  TEZ XABAR: " + e.getText().replace("\n", " | ")); } catch (Exception ignore) {}
                String b = d.findElement(By.tagName("body")).getText();
                for (String key : new String[]{"muvaffaq", "Muvaffaq", "xato", "Xato", "majburiy", "Majburiy", "yuborildi", "saqlandi"}) {
                    int i = b.indexOf(key);
                    if (i >= 0) System.out.println("  XABAR: ..." + b.substring(Math.max(0, i - 60), Math.min(b.length(), i + 120)).replace("\n", " | ") + "...");
                }
            }
            Thread.sleep(3000);
            for (WebElement m : d.findElements(By.cssSelector("[role=dialog], [class*=modal], [class*=Modal]")))
                try { if (m.isDisplayed()) System.out.println("  IMZO OYNASI: " + m.getText().replace("\n", " | ")); } catch (Exception ignore) {}
            for (WebElement e : d.findElements(By.cssSelector("[role=dialog] input, [class*=modal] input, [role=dialog] select")))
                try { if (e.isDisplayed()) System.out.println("  OYNADAGI MAYDON: type=" + e.getAttribute("type") + " id=" + e.getAttribute("id") + " ph=" + e.getAttribute("placeholder")); } catch (Exception ignore) {}
            shot("eimzo_oynasi");
            System.out.println("\n>>> E-IMZO: kalitni tanlab tasdiqlang. Kutaman...");
            for (int s = 0; s < Integer.getInteger("wait", 180); s++) {
                Thread.sleep(1000);
                if (!d.getCurrentUrl().contains("/create")) { System.out.println("  Sahifa o'zgardi (" + s + " soniyada)"); break; }
                String b = d.findElement(By.tagName("body")).getText();
                if (b.contains("muvaffaqiyatli") || b.contains("Muvaffaqiyatli")) { System.out.println("  Muvaffaqiyat xabari chiqdi (" + s + " soniyada)"); break; }
            }
            Thread.sleep(3000);
            System.out.println("Yuborishdan keyin URL: " + d.getCurrentUrl());
            if (!d.getCurrentUrl().contains("/create")) {
                Thread.sleep(3000);
                List<WebElement> rows = d.findElements(By.cssSelector("main tbody tr"));
                for (int i = 0; i < Math.min(3, rows.size()); i++)
                    System.out.println("  RO'YXAT " + (i + 1) + ": " + rows.get(i).getText().replace("\n", " | "));
            }
            try {
                for (org.openqa.selenium.logging.LogEntry le : d.manage().logs().get(org.openqa.selenium.logging.LogType.BROWSER))
                    if (le.getLevel().intValue() >= java.util.logging.Level.WARNING.intValue())
                        System.out.println("  KONSOL: " + le.getMessage().substring(0, Math.min(300, le.getMessage().length())));
            } catch (Exception e) { System.out.println("  konsol o'qilmadi: " + e.getMessage()); }
            Thread.sleep(1500);
            for (WebElement m : d.findElements(By.cssSelector("[role=dialog], .modal, [class*=modal], [data-testid*=modal]")))
                try { if (m.isDisplayed()) System.out.println("  OYNA: " + m.getText().replace("\n", " | ")); } catch (Exception ignore) {}
            for (WebElement e : d.findElements(By.xpath("//*[contains(text(),'Majburiy') or contains(text(),'kerak') or contains(text(),'noto')]")))
                try { if (e.isDisplayed()) System.out.println("  OGOHLANTIRISH: [" + labelOf(e) + "] " + e.getText()); } catch (Exception ignore) {}
            for (WebElement e : d.findElements(By.cssSelector("[class*=toast], [class*=Toastify], [class*=notification], [role=alert]")))
                try { if (e.isDisplayed() && !e.getText().isBlank()) System.out.println("  TOAST: " + e.getText().replace("\n", " | ")); } catch (Exception ignore) {}
            shot("kontrakt_yuborilgandan_keyin");
            // Validatsiya xatolari (qizil yozuvlar)
            for (WebElement e : d.findElements(By.cssSelector("main .text-red-500, main [class*=error], main [class*=danger]")))
                try { if (e.isDisplayed() && !e.getText().isBlank()) System.out.println("  QIZIL: " + labelOf(e) + " -> " + e.getText()); } catch (Exception ignore) {}
            shot("kontrakt_yuborildi");
            Thread.sleep(4000);
        } finally {
            d.quit();
        }
    }
}
