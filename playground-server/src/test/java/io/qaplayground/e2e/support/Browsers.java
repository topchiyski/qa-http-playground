package io.qaplayground.e2e.support;

import java.time.Duration;

import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeDriverService;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.GeckoDriverService;
import org.openqa.selenium.remote.http.ClientConfig;

/**
 * Creates the browser chosen with {@code -Dbrowser=chrome|firefox|edge} (default Chrome), headless unless
 * {@code -Dheadless=false}.
 *
 * <p>Selenium Manager finds or downloads a matching driver, and the browser itself if it is missing, so there is
 * nothing to install by hand. Every browser runs with a fixed window size, so layouts and screenshots are comparable
 * between runs.
 *
 * <p>Selenium talks to the driver over HTTP with the JDK client. Unless told otherwise, that client tries an HTTP/2
 * ({@code h2c}) upgrade on every POST. With an HTTP-filtering proxy on the machine, the new-session POST
 * sometimes never reached ChromeDriver and timed out after 180 s. Pinning HTTP/1.1 fixed it
 * (docs/testing/EXPLORATORY-CHARTERS.md, Q1).
 */
public final class Browsers {

    /**
     * The chosen browser: {@code chrome}, {@code firefox} or {@code edge}.
     */
    public static final String NAME = System.getProperty("browser", "chrome").toLowerCase();
    /**
     * Whether the browser runs without a window. True unless {@code -Dheadless=false}.
     */
    private static final boolean HEADLESS = Boolean.parseBoolean(System.getProperty("headless", "true"));

    /**
     * The driver connection, pinned to HTTP/1.1 for the reason in the class comment.
     */
    private static final ClientConfig HTTP_1_1 = ClientConfig.defaultConfig()
            .version("HTTP_1_1")
            .readTimeout(Duration.ofSeconds(90));

    private Browsers() {
    }

    /**
     * Starts the chosen browser with this project's settings.
     */
    public static WebDriver start() {
        WebDriver driver = switch (NAME) {
            case "firefox" -> {
                FirefoxOptions o = new FirefoxOptions();
                o.addPreference("devtools.jsonview.enabled", false); // show JSON as text, like Chrome
                if (HEADLESS) {
                    o.addArguments("-headless");
                }
                yield new FirefoxDriver(GeckoDriverService.createDefaultService(), o, HTTP_1_1);
            }
            case "edge" -> {
                EdgeOptions o = new EdgeOptions();
                if (HEADLESS) {
                    o.addArguments("--headless=new");
                }
                yield new EdgeDriver(EdgeDriverService.createDefaultService(), o, HTTP_1_1);
            }
            default -> {
                ChromeOptions o = new ChromeOptions();
                if (HEADLESS) {
                    o.addArguments("--headless=new");
                }
                o.addArguments("--no-first-run", "--no-default-browser-check", "--disable-search-engine-choice-screen");
                yield new ChromeDriver(ChromeDriverService.createDefaultService(), o, HTTP_1_1);
            }
        };
        driver.manage().window().setSize(new Dimension(1280, 900));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30)).scriptTimeout(Duration.ofSeconds(15));
        return driver;
    }

    /**
     * Whether the browser is Chrome or Edge, for the few checks only Chromium supports.
     */
    public static boolean isChromium() {
        return NAME.equals("chrome") || NAME.equals("edge");
    }
}
