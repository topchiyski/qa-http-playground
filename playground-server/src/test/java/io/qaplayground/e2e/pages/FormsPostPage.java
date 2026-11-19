package io.qaplayground.e2e.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * The pizza-order HTML form at {@code /forms/post}, which submits to {@code /post}.
 */
public class FormsPostPage extends Page {

    private static final By NAME = By.name("custname");
    private static final By TELEPHONE = By.name("custtel");
    private static final By EMAIL = By.name("custemail");
    private static final By DELIVERY = By.name("delivery");
    private static final By COMMENTS = By.name("comments");
    private static final By SUBMIT = By.cssSelector("form button");

    public FormsPostPage(WebDriver driver) {
        super(driver);
    }

    /**
     * Opens the form in the browser.
     */
    public static FormsPostPage open(WebDriver driver) {
        FormsPostPage page = new FormsPostPage(driver);
        page.open("/forms/post");
        page.wait.until(ExpectedConditions.visibilityOfElementLocated(NAME));
        return page;
    }

    /**
     * Types the customer name.
     */
    public FormsPostPage name(String value) {
        type(NAME, value);
        return this;
    }

    /**
     * Types the telephone number.
     */
    public FormsPostPage telephone(String value) {
        type(TELEPHONE, value);
        return this;
    }

    /**
     * Types the email address.
     */
    public FormsPostPage email(String value) {
        type(EMAIL, value);
        return this;
    }

    /**
     * Chooses a pizza size by its radio button value.
     */
    public FormsPostPage size(String value) {
        driver.findElement(By.cssSelector("input[name=size][value=" + value + "]")).click();
        return this;
    }

    /**
     * Ticks a topping by its checkbox value. Call it once per topping.
     */
    public FormsPostPage topping(String value) {
        driver.findElement(By.cssSelector("input[name=topping][value=" + value + "]")).click();
        return this;
    }

    /**
     * Sets the time input through its DOM value.
     *
     * <p>Typing into {@code <input type=time>} depends on the browser's locale and widget. Setting the value is what
     * the
     * user's choice amounts to.
     */
    public FormsPostPage delivery(String hhmm) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].value = arguments[1]", driver.findElement(DELIVERY), hhmm);
        return this;
    }

    /**
     * Types the delivery instructions.
     */
    public FormsPostPage comments(String value) {
        type(COMMENTS, value);
        return this;
    }

    /**
     * Whether the browser's own constraint validation passes ({@code min}, {@code max}, {@code step},
     * {@code type=email}...).
     */
    public boolean isValid() {
        return (Boolean) ((JavascriptExecutor) driver).executeScript("return document.querySelector('form').checkValidity()");
    }

    /**
     * The message the browser would show for this field, or an empty string when it is valid.
     */
    public String validationMessage(String fieldName) {
        return (String) ((JavascriptExecutor) driver)
                .executeScript("return document.querySelector('[name=' + arguments[0] + ']').validationMessage", fieldName);
    }

    /**
     * Submits the form and returns the {@code /post} response the browser lands on.
     */
    public JsonPage submit() {
        driver.findElement(SUBMIT).click();
        JsonPage result = new JsonPage(driver);
        wait.until(ExpectedConditions.urlContains("/post"));
        return result;
    }

    /**
     * Clears a field, then types into it like a user.
     */
    private void type(By field, String value) {
        WebElement e = driver.findElement(field);
        e.clear();
        e.sendKeys(value);
    }
}
