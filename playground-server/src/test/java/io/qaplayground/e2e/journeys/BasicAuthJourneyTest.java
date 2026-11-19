package io.qaplayground.e2e.journeys;

import io.qaplayground.e2e.pages.JsonPage;
import io.qaplayground.e2e.support.Browsers;
import io.qaplayground.e2e.support.E2eTest;
import io.qaplayground.e2e.support.Playground;
import io.qaplayground.testing.technique.AcceptanceTest;
import io.qaplayground.testing.technique.UseCase;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey 6: basic auth in a browser.
 *
 * <p>Main flow: <b>Given</b> the learner supplies credentials <b>When</b> they open {@code /basic-auth/user/passwd}
 * <b>Then</b> the page says authenticated. Alternative flows: no credentials (the browser keeps the page behind its
 * login prompt), and {@code /hidden-basic-auth}, which answers 404 so the browser never asks. The contrast between
 * the last two is the lesson.
 *
 * <p>Credentials go in the URL ({@code http://user:pass@host/...}), which Chromium honours for a top-level
 * navigation. WebDriver BiDi's {@code network.continueWithAuth} would work in every browser, but with ChromeDriver
 * 157 the command never completed (tried and diagnosed; see docs/testing/EXPLORATORY-CHARTERS.md). Firefox asks the
 * user to confirm credentials in a URL, so the credential test runs on Chromium only.
 */
@E2eTest
@UseCase
@AcceptanceTest
class BasicAuthJourneyTest {

    /**
     * Main flow: with the right credentials, the page says authenticated.
     */
    @Test
    @DisplayName("With credentials: authenticated")
    void withCredentials(WebDriver driver) {
        Assumptions.assumeTrue(Browsers.isChromium(), "URL credentials need confirmation in Firefox");
        driver.get(Playground.url("/basic-auth/user/passwd").replace("://", "://user:passwd@"));
        JsonPage page = new JsonPage(driver);
        assertThat(page.json().get("authenticated").asBoolean()).isTrue();
        assertThat(page.json().get("user").asString()).isEqualTo("user");
    }

    /**
     * No credentials: the browser stays behind its login prompt and never shows the page.
     */
    @Test
    @DisplayName("Without credentials: the browser holds the page behind its login prompt")
    void withoutCredentials(WebDriver driver) {
        Assumptions.assumeTrue(Browsers.isChromium(), "prompt handling differs per browser");
        driver.get(Playground.url("/basic-auth/user/passwd"));
        assertThat(new JsonPage(driver).bodyText()).as("nothing rendered while the prompt waits").isEmpty();
    }

    /**
     * The hidden variant answers 404, so the browser never even asks for credentials.
     */
    @Test
    @DisplayName("Hidden basic auth: a visible 404 and no login prompt")
    void hiddenBasicAuth(WebDriver driver) {
        JsonPage page = JsonPage.at(driver, "/hidden-basic-auth/user/passwd");
        assertThat(page.json().get("status_code").asInt()).isEqualTo(404);
    }
}
