package uz.ebirja.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/** Login va parol orqali kirish sahifasi (/uz/auth/login). */
public class LoginPage {
    private final WebDriver d;
    private final WebDriverWait wait;

    private final By birjaPlatform = By.xpath("//button[contains(normalize-space(.),'Birja savdolari')]");
    private final By loginInput    = By.name("userName");
    private final By passwordInput = By.name("password");
    private final By kirishButton  = By.xpath("//button[@type='submit' and normalize-space(.)='Kirish']");

    public LoginPage(WebDriver d) {
        this.d = d;
        this.wait = new WebDriverWait(d, Duration.ofSeconds(30));
    }

    public LoginPage open(String baseUrl) {
        // doimiy brauzer profilida avvalgi seans qolgan bo'lishi mumkin — chiqib olamiz
        uz.ebirja.base.Ui.logout(d, baseUrl);
        d.get(baseUrl + "/uz/auth/login");
        wait.until(ExpectedConditions.visibilityOfElementLocated(loginInput));
        return this;
    }

    /** "Birja savdolari" platformasini tanlaydi (aynan shu sahifada tanlash shart). */
    public LoginPage chooseBirja() {
        wait.until(ExpectedConditions.elementToBeClickable(birjaPlatform)).click();
        return this;
    }

    public LoginPage typeLogin(String login) {
        WebElement e = d.findElement(loginInput);
        e.clear();
        e.sendKeys(login);
        return this;
    }

    public LoginPage typePassword(String password) {
        WebElement e = d.findElement(passwordInput);
        e.clear();
        e.sendKeys(password);
        return this;
    }

    public void clickKirish() {
        d.findElement(kirishButton).click();
    }

    /** To'liq kirish: platforma + login + parol + Kirish tugmasi. */
    public void loginAs(String baseUrl, String login, String password) {
        open(baseUrl).chooseBirja().typeLogin(login).typePassword(password).clickKirish();
    }

    public boolean isTextShown(String text) {
        return d.findElement(By.tagName("body")).getText().contains(text);
    }
}
