package io.qaplayground.e2e.pages;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.qaplayground.e2e.support.Playground;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * The landing page: the endpoint list a learner starts from.
 */
public class IndexPage extends Page {

    private static final By GROUP_HEADINGS = By.cssSelector("section h2");
    private static final By LINKS = By.cssSelector("a[href]");

    public IndexPage(WebDriver driver) {
        super(driver);
    }

    /**
     * Opens the landing page.
     */
    public static IndexPage open(WebDriver driver) {
        IndexPage page = new IndexPage(driver);
        page.open("/");
        return page;
    }

    /**
     * The topic headings, in page order.
     */
    public List<String> groupNames() {
        return driver.findElements(GROUP_HEADINGS).stream().map(WebElement::getText).toList();
    }

    /**
     * Same-origin link targets (path and query), in page order, without duplicates.
     */
    public Set<String> internalLinks() {
        Set<String> links = new LinkedHashSet<>();
        for (WebElement a : driver.findElements(LINKS)) {
            String href = a.getDomProperty("href"); // resolved absolute URL
            if (href != null && href.startsWith(Playground.baseUri() + "/")) {
                links.add(href.substring(Playground.baseUri().length()));
            }
        }
        return links;
    }

    /**
     * Clicks the link with this text and returns the page it opens.
     */
    public JsonPage follow(String linkText) {
        driver.findElement(By.linkText(linkText)).click();
        return new JsonPage(driver);
    }
}
