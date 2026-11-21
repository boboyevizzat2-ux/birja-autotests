package probe;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.testng.annotations.Test;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** VAQTINCHALIK: moderator kabinetida rol almashtirishni o'rganish (brauzer ekranda ko'rinadi). */
public class ProbeRole {
    WebDriver d; String out; Properties p = new Properties();

    void shot(String name) throws Exception {
        File s = ((TakesScreenshot) d).getScreenshotAs(OutputType.FILE);
        Files.copy(s.toPath(), Paths.get(out, name + ".png"), StandardCopyOption.REPLACE_EXISTING);
    }

    Set<String> visibleTexts() {
        Set<String> s = new LinkedHashSet<>();
        for (WebElement e : d.findElements(By.cssSelector("button, a, li, [role=option], [role=menuitem], label, span, div[role=dialog] *"))) {
            try { if (e.isDisplayed()) { String t = e.getText().replace("\n", " ").trim(); if (!t.isEmpty() && t.length() < 100) s.add(e.getTagName() + ": " + t); } } catch (Exception ignore) {}
        }
        return s;
    }

    @Test
    public void probe() throws Exception {
        try (Reader r = new InputStreamReader(new FileInputStream("login.properties"), "UTF-8")) { p.load(r); }
        out = System.getProperty("probe.out");
        ChromeOptions o = new ChromeOptions();
        o.addArguments("--window-size=1600,1000");
        d = new ChromeDriver(o);
        try {
            d.get(p.getProperty("admin.url") + "/uz/auth/login");
            Thread.sleep(4000);
            d.findElement(By.name("userName")).sendKeys(p.getProperty("admin1.login"));
            d.findElement(By.name("password")).sendKeys(p.getProperty("admin1.password"));
            d.findElement(By.xpath("//button[@type='submit' and normalize-space(.)='Kirish']")).click();
            Thread.sleep(7000);

            System.out.println("URL: " + d.getCurrentUrl());
            WebElement roleSpan = null;
            for (int t = 0; t < 20 && roleSpan == null; t++) {
                for (WebElement e : d.findElements(By.xpath("//span[contains(@class,'w-fit')]")))
                    if (e.isDisplayed() && e.getText().contains("MODERATOR")) { roleSpan = e; break; }
                if (roleSpan == null) Thread.sleep(1000);
            }
            System.out.println("Hozirgi rol yozuvi: " + (roleSpan == null ? "TOPILMADI" : roleSpan.getText().replace("\n", " | ")));
            roleSpan.click();
            Thread.sleep(1500);
            Set<String> before = visibleTexts();
            d.findElement(By.xpath("//span[normalize-space(.)='Rol almashtrish']")).click();
            Thread.sleep(2500);
            shot("rol_almashtirish_oynasi");
            Set<String> after = visibleTexts();
            after.removeAll(before);
            System.out.println("'Rol almashtrish' bosilgandan keyin YANGI elementlar:");
            after.forEach(t -> System.out.println("   + " + t));
            Thread.sleep(3000);
        } finally {
            d.quit();
        }
    }
}
