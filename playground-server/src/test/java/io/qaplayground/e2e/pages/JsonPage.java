package io.qaplayground.e2e.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Any endpoint that answers with JSON, as the browser shows it: raw text in a {@code <pre>}.
 */
public class JsonPage extends Page {

    /**
     * Parses the text the browser shows.
     */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public JsonPage(WebDriver driver) {
        super(driver);
    }

    /**
     * Opens {@code path} and treats the result as JSON.
     */
    public static JsonPage at(WebDriver driver, String path) {
        JsonPage page = new JsonPage(driver);
        page.open(path);
        return page;
    }

    /**
     * The document parsed as JSON. Waits until the browser has rendered it.
     */
    public JsonNode json() {
        String text = wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("pre"))).getText();
        return JSON.readTree(text);
    }
}
