package io.qaplayground.e2e.pages;

import java.net.URI;
import java.time.Duration;

import io.qaplayground.e2e.support.Playground;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Base for page objects: navigation, explicit waits and the current location.
 *
 * <p>Page objects show what a user can see and do; assertions stay in the tests. Only explicit waits: a
 * {@code Thread.sleep} in a UI test is either too short (flaky) or too long (slow). An architecture rule forbids it
 * in this module.
 */
public abstract class Page {

    protected final WebDriver driver;
    protected final WebDriverWait wait;

    protected Page(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    /**
     * Navigates to a path on the playground.
     */
    protected void open(String path) {
        driver.get(Playground.url(path));
    }

    /**
     * The path the browser is on now, after any redirects.
     */
    public String currentPath() {
        return URI.create(driver.getCurrentUrl()).getRawPath();
    }

    /**
     * The host the browser is on now. The redirect journey compares it with the one it started on.
     */
    public String currentHost() {
        return URI.create(driver.getCurrentUrl()).getHost();
    }

    /**
     * The page title.
     */
    public String title() {
        return driver.getTitle();
    }

    /**
     * All visible text of the page.
     */
    public String bodyText() {
        return wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body"))).getText();
    }
}
