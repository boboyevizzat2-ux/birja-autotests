package probe;

import org.openqa.selenium.*;
import org.testng.annotations.Test;
import uz.ebirja.base.BaseTest;
import uz.ebirja.config.Config;

import java.io.File;
import java.nio.file.*;
import java.util.*;

import static uz.ebirja.base.Ui.*;

/** VAQTINCHALIK: "Tartibga solinadigan mahsulotlar" sahifasini o'rganish. Hech narsa saqlamaydi. */
public class ProbeRegulated extends BaseTest {
    String out = System.getProperty("probe.out", "target/probe-regulated");

    void shot(String name) {
        try {
            new File(out).mkdirs();
            File s = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Files.copy(s.toPath(), Paths.get(out, name + ".png"), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignore) {}
    }

    void dump(String label) {
        System.out.println("\n===== " + label + " | " + driver.getCurrentUrl());
        String b = driver.findElement(By.tagName("main")).getText();
        System.out.println(b.length() > 4000 ? b.substring(0, 4000) + "…" : b);
        Set<String> btn = new LinkedHashSet<>();
        for (WebElement e : driver.findElements(By.cssSelector("main button, main [role=tab]")))
            try { if (e.isDisplayed()) { String t = clean(e.getText()); if (!t.isEmpty() && t.length() < 50) btn.add(t); } } catch (Exception ignore) {}
        System.out.println("TUGMALAR: " + btn);
        shot(label.replaceAll("[^A-Za-z0-9_]", "_"));
    }

    @Test
    public void probe() {
        openBrowser();
        try {
            String base = Config.adminUrl();
            loginByKey(driver, base, Config.get("admin2.login"), false);
            driver.get(base + "/uz/moderationv2/listing/regulated-products");
            sleep(6000);
            dump("royxat");
            String id = System.getProperty("probe.id", "61");
            driver.get(base + "/uz/moderationv2/listing/regulated-products/" + id);
            sleep(6000);
            dump("batafsil_" + id);
            // har bir tabni bosib ko'ramiz (faqat ko'rish)
            List<String> tabs = new ArrayList<>();
            for (WebElement e : driver.findElements(By.cssSelector("main button, main [role=tab]")))
                try { if (e.isDisplayed()) tabs.add(e.getText().trim()); } catch (Exception ignore) {}
            for (String t : new String[]{"Xaridorlar", "Ro'yxat", "Limit", "Spread", "Listinglar", "Basis koeffitsientlari", "Hujjatlar va tarix", "Arizalar"}) {
                for (String tab : tabs) {
                    if (!tab.startsWith(t)) continue;
                    WebElement el = visible(driver, By.xpath("//main//*[self::button or @role='tab'][starts-with(normalize-space(.),\"" + tab.split("\n")[0] + "\")]"));
                    if (el == null) continue;
                    jsClick(driver, el);
                    sleep(3500);
                    dump("tab_" + t);
                    break;
                }
            }
        } finally {
            closeBrowser();
        }
    }
}
