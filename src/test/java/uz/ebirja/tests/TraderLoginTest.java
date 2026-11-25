package uz.ebirja.tests;

import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import uz.ebirja.base.BaseTest;
import uz.ebirja.base.Ui;
import uz.ebirja.config.Config;

import java.time.Duration;

/** Treyder saytiga kirish testlari (faqat E-IMZO kaliti bilan). */
public class TraderLoginTest extends BaseTest {
    @BeforeMethod
    public void setUp() {
        openBrowser();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        closeBrowser();
    }

    @DataProvider
    public Object[][] treyderlar() {
        return new Object[][]{{"trader1"}, {"trader2"}};
    }

    @Test(dataProvider = "treyderlar", description = "E-IMZO kaliti bilan treyder profiliga kiradi")
    public void eimzoKalitiBilanKirish(String kim) {
        String jshshir = Config.get(kim + ".login");
        Ui.loginByKey(driver, Config.traderUrl(), jshshir, true);
        new WebDriverWait(driver, Duration.ofSeconds(30))
                .until(ExpectedConditions.urlContains("/trader/profile"));
        new WebDriverWait(driver, Duration.ofSeconds(20))
                .until(d -> d.findElement(org.openqa.selenium.By.tagName("body")).getText().contains(jshshir));
    }
}
