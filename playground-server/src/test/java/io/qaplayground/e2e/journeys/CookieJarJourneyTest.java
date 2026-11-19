package io.qaplayground.e2e.journeys;

import io.qaplayground.e2e.pages.JsonPage;
import io.qaplayground.e2e.support.Browsers;
import io.qaplayground.e2e.support.E2eTest;
import io.qaplayground.testing.technique.AcceptanceTest;
import io.qaplayground.testing.technique.StateTransition;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey 4: cookies in a real browser jar (state transition testing, TEST-DESIGN.md 4.3).
 *
 * <p>States: Empty → {a} → {a, b} → {b} → Empty. Only a real browser checks what a learner actually experiences:
 * {@code Path}, deletion by expiry, and cookies the browser refuses to store.
 */
@E2eTest
@StateTransition
class CookieJarJourneyTest {

    /**
     * The cookies the browser sends to {@code path}, as {@code /cookies} reports them.
     */
    private static JsonNode cookies(WebDriver driver, String path) {
        return JsonPage.at(driver, path).json().get("cookies"); // every /cookies/* redirects to /cookies
    }

    /**
     * Every transition in order: set a, set b, delete a, delete b, back to empty.
     */
    @Test
    @DisplayName("Empty -> {a} -> {a,b} -> {b} -> Empty, and the browser jar agrees with the page")
    void fullLifecycle(WebDriver driver) {
        assertThat(cookies(driver, "/cookies").size()).as("Empty").isZero();

        JsonNode afterA = cookies(driver, "/cookies/set?a=1");
        assertThat(afterA.get("a").asString()).as("{a}").isEqualTo("1");
        assertThat(driver.manage().getCookieNamed("a").isHttpOnly()).as("HttpOnly by default").isTrue();

        JsonNode afterB = cookies(driver, "/cookies/set?b=2");
        assertThat(afterB.propertyNames()).as("{a, b}").containsExactlyInAnyOrder("a", "b");

        JsonNode afterDeleteA = cookies(driver, "/cookies/delete?a");
        assertThat(afterDeleteA.propertyNames()).as("{b}").containsExactly("b");
        assertThat(driver.manage().getCookieNamed("a")).as("gone from the jar").isNull();

        assertThat(cookies(driver, "/cookies/delete?b").size()).as("Empty again").isZero();
    }

    /**
     * Setting a cookie again replaces its value instead of adding a second one.
     */
    @Test
    @DisplayName("Overwriting a cookie replaces its value (no duplicate state)")
    void overwrite(WebDriver driver) {
        cookies(driver, "/cookies/set?a=1");
        assertThat(cookies(driver, "/cookies/set?a=2").get("a").asString()).isEqualTo("2");
    }

    /**
     * Exploratory finding ES-01 F2: Chromium rejects {@code SameSite=None} without {@code Secure} over http, so the
     * learner's cookie quietly "disappears".
     *
     * <p>The server sends the cookie as asked, as upstream does. The browser decides.
     */
    @AcceptanceTest
    @Test
    @DisplayName("Chromium drops a SameSite=None cookie that is not Secure (ES-01 F2)")
    void sameSiteNoneNeedsSecure(WebDriver driver) {
        Assumptions.assumeTrue(Browsers.isChromium(), "Chromium-specific cookie policy");
        JsonNode jar = cookies(driver, "/cookies/set?c=3&attr[SameSite]=None");
        assertThat(jar.has("c")).isFalse();
        assertThat(cookies(driver, "/cookies/set?c=3&attr[SameSite]=Lax").has("c")).isTrue();
    }
}
