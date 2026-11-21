package uz.ebirja.tests;

import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import uz.ebirja.base.BaseTest;
import uz.ebirja.config.Config;
import uz.ebirja.pages.LoginPage;

import java.time.Duration;

/** Treyder saytiga kirish testlari. */
public class TraderLoginTest extends BaseTest {
    private LoginPage login;

    @BeforeMethod
    public void setUp() {
        openBrowser();
        login = new LoginPage(driver);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        closeBrowser();
    }

    @DataProvider
    public Object[][] treyderlar() {
        return new Object[][]{{"trader1"}, {"trader2"}};
    }

    @Test(dataProvider = "treyderlar", description = "To'g'ri login va parol bilan treyder profiliga kiradi")
    public void togriLoginBilanKirish(String kim) {
        String jshshir = Config.get(kim + ".login");
        login.loginAs(Config.traderUrl(), jshshir, Config.get(kim + ".password"));
        new WebDriverWait(driver, Duration.ofSeconds(30))
                .until(ExpectedConditions.urlContains("/trader/profile"));
        new WebDriverWait(driver, Duration.ofSeconds(20))
                .until(d -> login.isTextShown(jshshir));
    }

    @Test(description = "Platforma tanlanmasa, 'Platformani tanlang' xatosi chiqadi")
    public void platformaTanlanmasaXato() {
        login.open(Config.traderUrl())
                .typeLogin(Config.get("trader1.login"))
                .typePassword(Config.get("trader1.password"))
                .clickKirish();
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(d -> d.getPageSource().contains("Platformani tanlang") && d.getCurrentUrl().contains("/auth/login"));
        Assert.assertTrue(driver.getCurrentUrl().contains("/auth/login"), "Platforma tanlanmasdan tizimga kirib ketdi!");
    }

    @Test(description = "Login va parol bo'sh bo'lsa, 'Majburiy maydon' xatosi chiqadi")
    public void boshMaydonlarXato() {
        login.open(Config.traderUrl()).chooseBirja().clickKirish();
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(d -> login.isTextShown("Majburiy maydon"));
        Assert.assertTrue(driver.getCurrentUrl().contains("/auth/login"));
    }

    @Test(description = "Mavjud bo'lmagan login bilan tizimga kirib bo'lmaydi")
    public void notogriLoginBilanKiraolmaydi() throws InterruptedException {
        // Haqiqiy akkaunt bloklanib qolmasligi uchun soxta login ishlatiladi
        login.loginAs(Config.traderUrl(), "00000000000000", "NotogriParol123!");
        Thread.sleep(5000);
        Assert.assertTrue(driver.getCurrentUrl().contains("/auth/login"),
                "Noto'g'ri login bilan tizimga kirib ketdi!");
    }
}
