package io.qaplayground.e2e.pages;

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * Swagger UI at {@code /swagger-ui}.
 *
 * <p>It is third-party markup, so its locators are confined to this class. A Swagger UI upgrade that changes them
 * breaks one file, not the tests.
 */
public class SwaggerUiPage extends Page {

    private static final By OPERATION = By.cssSelector(".opblock");
    private static final By OPERATION_PATH = By.cssSelector(".opblock-summary-path");
    private static final By TRY_IT_OUT = By.cssSelector("button.try-out__btn");
    private static final By EXECUTE = By.cssSelector("button.execute");
    private static final By RESPONSE_STATUS = By.cssSelector(".live-responses-table tbody .response-col_status");
    private static final By RESPONSE_BODY = By.cssSelector(".live-responses-table tbody .response-col_description pre");

    private WebElement operation;

    public SwaggerUiPage(WebDriver driver) {
        super(driver);
    }

    /**
     * Opens Swagger UI and waits until the operations are listed.
     */
    public static SwaggerUiPage open(WebDriver driver) {
        SwaggerUiPage page = new SwaggerUiPage(driver);
        page.open("/swagger-ui");
        page.wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(OPERATION));
        return page;
    }

    /**
     * Scrolls down a screen at a time, like a user, collecting the operations as they render. Stops at {@code target}
     * ("METHOD /path") or at the bottom.
     *
     * <p>Swagger UI renders tag sections lazily, as they scroll into view: a fresh 900-pixel window shows only the
     * first
     * tag. It also re-renders while scrolling, so element references go stale quickly. Each step therefore reads the
     * visible operations in one atomic script call.
     */
    private java.util.Set<String> scrollThrough(String target) {
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        JavascriptExecutor js = (JavascriptExecutor) driver;
        while (true) {
            List<String> visible = settledSnapshot(js);
            seen.addAll(visible);
            if (target != null && visible.contains(target)) {
                return seen;
            }
            long before = ((Number) js.executeScript("return document.documentElement.scrollHeight")).longValue();
            boolean atBottom = (Boolean) js.executeScript(
                    "window.scrollBy(0, window.innerHeight / 2); return window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 2");
            if (atBottom) {
                try { // one last chance for a section that renders on arrival at the bottom
                    new org.openqa.selenium.support.ui.WebDriverWait(driver, java.time.Duration.ofSeconds(2))
                            .until(d -> ((Number) js.executeScript("return document.documentElement.scrollHeight")).longValue() > before);
                } catch (org.openqa.selenium.TimeoutException noMoreContent) {
                    return seen;
                }
            }
        }
    }

    /**
     * The operations currently rendered, once two reads in a row agree, so rendering has caught up.
     */
    private List<String> settledSnapshot(JavascriptExecutor js) {
        String script = """
                return Array.from(document.querySelectorAll('.opblock')).map(op =>
                  (op.className.match(/opblock-(get|post|put|patch|delete|head|options|trace)/) || ['', '?'])[1].toUpperCase()
                  + ' ' + op.querySelector('.opblock-summary-path').innerText.replaceAll('\u200b', ''))""";
        @SuppressWarnings("unchecked")
        List<String>[] last = new List[]{null};
        return new org.openqa.selenium.support.ui.WebDriverWait(driver, java.time.Duration.ofSeconds(5))
                .pollingEvery(java.time.Duration.ofMillis(150))
                .until(d -> {
                    @SuppressWarnings("unchecked")
                    List<String> now = (List<String>) js.executeScript(script);
                    boolean stable = now.equals(last[0]);
                    last[0] = now;
                    return stable ? now : null;
                });
    }

    /**
     * Every documented path, collected while scrolling through the whole page.
     */
    public java.util.Set<String> documentedPaths() {
        return scrollThrough(null).stream().map(s -> s.substring(s.indexOf(' ') + 1))
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
    }

    /**
     * Scrolls to and expands the operation with this method and path, such as {@code GET /status/{code}}.
     */
    public SwaggerUiPage expand(String method, String path) {
        String target = method + " " + path;
        scrollThrough(target);
        operation = wait.until(d -> (WebElement) ((JavascriptExecutor) d).executeScript("""
                return Array.from(document.querySelectorAll('.opblock.opblock-' + arguments[0])).find(op =>
                  op.querySelector('.opblock-summary-path').innerText.replaceAll('\u200b', '') === arguments[1])""",
                method.toLowerCase(), path));
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'})", operation);
        operation.findElement(By.cssSelector(".opblock-summary")).click();
        wait.until(d -> !operation.findElements(TRY_IT_OUT).isEmpty());
        return this;
    }

    /**
     * Enters try-out mode, unless the server configured Swagger UI to start in it ({@code try-it-out-enabled}).
     */
    public SwaggerUiPage tryItOut() {
        WebElement toggle = operation.findElement(TRY_IT_OUT);
        if (toggle.getText().trim().equalsIgnoreCase("Try it out")) {
            toggle.click();
        }
        wait.until(d -> !operation.findElements(EXECUTE).isEmpty());
        return this;
    }

    /**
     * Types a value into the named parameter field.
     */
    public SwaggerUiPage parameter(String name, String value) {
        WebElement input = operation.findElement(By.cssSelector("tr[data-param-name='" + name + "'] input"));
        input.clear();
        input.sendKeys(value);
        return this;
    }

    /**
     * Sends the request and waits for the live response.
     */
    public SwaggerUiPage execute() {
        operation.findElement(EXECUTE).click();
        wait.until(d -> !operation.findElements(RESPONSE_STATUS).isEmpty());
        return this;
    }

    /**
     * The status code shown for the live response.
     *
     * <p>Swagger UI adds "Undocumented" on a second line when the code isn't in the spec, as for most codes
     * {@code /status/{code}} can return.
     */
    public String responseStatus() {
        return operation.findElement(RESPONSE_STATUS).getText().trim().lines().findFirst().orElse("");
    }

    /**
     * The body shown for the live response.
     */
    public String responseBody() {
        return operation.findElement(RESPONSE_BODY).getText();
    }
}
