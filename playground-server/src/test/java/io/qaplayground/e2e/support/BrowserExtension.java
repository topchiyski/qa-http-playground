package io.qaplayground.e2e.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import io.qaplayground.testing.trace.Trace;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestWatcher;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.support.events.EventFiringDecorator;
import org.openqa.selenium.support.events.WebDriverListener;

/**
 * Owns the browser for a test class:
 * <ul>
 *   <li>one browser per class, with a clean state (cookies, page) before every test;</li>
 *   <li>skips the class when no browser can be started, so a JDK-only machine still builds;</li>
 *   <li>on failure, saves a screenshot and the page source to {@code target/e2e-artifacts};</li>
 *   <li>records every URL the test opens, for the traceability matrix.</li>
 * </ul>
 * Inject the browser as a test or constructor parameter of type {@link WebDriver}.
 */
public class BrowserExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, TestWatcher,
        ParameterResolver {

    /**
     * Where the extension keeps the browser for each test class.
     */
    private static final ExtensionContext.Namespace NS = ExtensionContext.Namespace.create(BrowserExtension.class);

    /**
     * Feeds navigation to the traceability recorder.
     */
    // Public: EventFiringDecorator calls the listener reflectively; a private class fails silently (logged only).
    /**
     * Feeds every navigation to the traceability recorder.
     */
    public static final class TracingListener implements WebDriverListener {
        @Override
        public void beforeGet(WebDriver driver, String url) {
            Trace.path(url);
        }

        @Override
        public void afterGet(WebDriver driver, String url) {
            Trace.path(driver.getCurrentUrl()); // where redirects ended up
        }
    }

    /**
     * Starts the playground, then one browser for the class, or skips the class when no browser can start.
     */
    @Override
    public void beforeAll(ExtensionContext context) {
        Playground.baseUri(); // start the server before the browser, so start-up errors are clear
        WebDriver driver;
        long t0 = System.nanoTime();
        try {
            driver = Browsers.start();
            System.err.printf("[e2e] %s started in %d ms for %s%n", Browsers.NAME, (System.nanoTime() - t0) / 1_000_000,
                    context.getDisplayName());
        } catch (WebDriverException e) {
            // Surefire does not print class-level skips, so say it on the console as well.
            String reason = "No browser could be started (" + Browsers.NAME + "): " + firstLine(e.getMessage())
                    + ". E2E tests skipped.";
            System.err.println("[e2e] " + reason);
            Assumptions.abort(reason);
            return;
        }
        WebDriver decorated = new EventFiringDecorator<>(new TracingListener()).decorate(driver);
        context.getStore(NS).put("driver", decorated);
        context.getStore(NS).put("raw", driver);
    }

    /**
     * Clears the cookies on both host names the journeys use. It uses the untraced browser, so this housekeeping
     * doesn't count as a test visit.
     */
    @Override
    public void beforeEach(ExtensionContext context) {
        WebDriver raw = context.getStore(NS).get("raw", WebDriver.class);
        if (raw != null) {
            // Undecorated driver: housekeeping is not a test visit. A neutral page on each origin the
            // journeys use, so their cookies can be cleared.
            for (String origin : new String[]{Playground.url("/robots.txt"), Playground.otherHostUrl("/robots.txt")}) {
                raw.get(origin);
                raw.manage().deleteAllCookies();
            }
        }
    }

    /**
     * Closes the class's browser.
     */
    @Override
    public void afterAll(ExtensionContext context) {
        WebDriver raw = context.getStore(NS).get("raw", WebDriver.class);
        if (raw != null) {
            raw.quit();
        }
    }

    /**
     * Saves a screenshot and the page source, so a failure can be understood without running it again.
     */
    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        WebDriver driver = driver(context);
        if (driver == null) {
            return;
        }
        String name = context.getRequiredTestClass().getSimpleName() + "-" + context.getDisplayName()
                .replaceAll("[^A-Za-z0-9._-]+", "_");
        Path dir = Path.of(System.getProperty("e2e.artifacts", "target/e2e-artifacts"));
        try {
            Files.createDirectories(dir);
            byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Files.write(dir.resolve(name + ".png"), png);
            Files.writeString(dir.resolve(name + ".html"), "<!-- " + driver.getCurrentUrl() + " -->\n"
                    + driver.getPageSource(), StandardCharsets.UTF_8);
        } catch (IOException | WebDriverException e) {
            System.err.println("Could not save failure artifacts: " + e);
        }
    }

    /**
     * Nothing to clean up: a disabled test never used the browser.
     */
    @Override
    public void testDisabled(ExtensionContext context, Optional<String> reason) {
        // nothing to clean up
    }

    /**
     * Supplies parameters of type {@link WebDriver}.
     */
    @Override
    public boolean supportsParameter(ParameterContext p, ExtensionContext e) {
        return p.getParameter().getType() == WebDriver.class;
    }

    /**
     * The class's browser, wrapped so every navigation is traced.
     */
    @Override
    public Object resolveParameter(ParameterContext p, ExtensionContext e) {
        return driver(e);
    }

    /**
     * The traced browser for this test class.
     */
    private static WebDriver driver(ExtensionContext context) {
        ExtensionContext classContext = context;
        while (classContext.getParent().isPresent() && classContext.getTestMethod().isPresent()) {
            classContext = classContext.getParent().get();
        }
        return classContext.getStore(NS).get("driver", WebDriver.class);
    }

    /**
     * The first line of a message; driver errors can run to many lines.
     */
    private static String firstLine(String s) {
        return s == null ? "" : s.lines().findFirst().orElse("");
    }
}
