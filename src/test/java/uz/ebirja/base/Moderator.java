package uz.ebirja.base;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.testng.Assert;
import uz.ebirja.config.Config;

import static uz.ebirja.base.Ui.*;

/** Moderator kabineti: joriy rolni o'qish va rolni almashtirish (Profil → ism → "Rol almashtrish"). */
public final class Moderator {
    private Moderator() {}

    public static String joriyRol(WebDriver d) {
        for (WebElement e : d.findElements(By.xpath("//span[contains(@class,'w-fit')]")))
            try { if (e.isDisplayed() && e.getText().contains("MODERATOR")) return e.getText().split("\n")[0].trim(); } catch (Exception ignore) {}
        return "?";
    }

    public static void rolAlmashtir(WebDriver d, String role) {
        d.get(Config.adminUrl() + "/uz/moderationv2/profile");
        waitFor(() -> "?".equals(joriyRol(d)) ? null : Boolean.TRUE, 30);
        if (joriyRol(d).equals(role)) return;
        for (int t = 0; t < 5; t++) {
            for (WebElement e : d.findElements(By.xpath("//span[contains(@class,'w-fit')]")))
                try { if (e.isDisplayed() && e.getText().contains("MODERATOR")) { jsClick(d, e); break; } } catch (Exception ignore) {}
            WebElement item = waitFor(() -> visible(d, By.xpath("//span[normalize-space(.)='Rol almashtrish' or normalize-space(.)='Rol almashtirish']")), 3);
            if (item != null) { item.click(); break; }
        }
        jsClick(d, waitFor(() -> lastVisible(d, By.xpath("//div[normalize-space(text())='" + role + "']")), 5));
        sleep(300);
        jsClick(d, lastVisible(d, By.xpath("//button[normalize-space(.)='Saqlash']")));
        sleep(1200);
        d.get(Config.adminUrl() + "/uz/moderationv2/profile");
        waitFor(() -> role.equals(joriyRol(d)) ? Boolean.TRUE : null, 20);
        Assert.assertEquals(joriyRol(d), role, "Rol almashmadi");
    }
}
