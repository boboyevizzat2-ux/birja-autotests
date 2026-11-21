package uz.ebirja.tests;

import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import uz.ebirja.base.BaseTest;
import uz.ebirja.config.Config;
import uz.ebirja.pages.LoginPage;

import java.time.Duration;

/** Treyder menyusidagi sahifalar ochilishini tekshiradi (bir marta kirib, hammasini aylanib chiqadi). */
public class TraderMenuTest extends BaseTest {

    @BeforeClass
    public void kirish() {
        openBrowser();
        new LoginPage(driver).loginAs(Config.traderUrl(), Config.get("trader1.login"), Config.get("trader1.password"));
        new WebDriverWait(driver, Duration.ofSeconds(30)).until(ExpectedConditions.urlContains("/trader/profile"));
    }

    @AfterClass(alwaysRun = true)
    public void chiqish() {
        closeBrowser();
    }

    @DataProvider
    public Object[][] sahifalar() {
        return new Object[][]{
                // manzil,                      sahifada ko'rinishi kerak bo'lgan matn
                {"/uz/trader/profile",          "JSHSHIR"},
                {"/uz/trader/plan-graph",       "Grafiklarni ko"},
                {"/uz/trader/arbitration",      "Ariza yaratish"},
                {"/uz/common/chat",             "Bitim bo'yicha"},
                {"/uz/common/security",         "Faol seanslar"},
                {"/uz/common/price-level-info", "Viloyatni tanlang"},
        };
    }

    @Test(dataProvider = "sahifalar", description = "Menyu sahifasi ochiladi va kerakli matn ko'rinadi")
    public void sahifaOchiladi(String path, String kutilganMatn) {
        driver.get(Config.traderUrl() + path);
        new WebDriverWait(driver, Duration.ofSeconds(20)).until(d ->
                d.findElement(By.tagName("body")).getText().contains(kutilganMatn));
        Assert.assertFalse(driver.getCurrentUrl().contains("/auth/"), "Tizimdan chiqarib yuborildi: " + path);
    }
}
