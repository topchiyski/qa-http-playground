package io.qaplayground.e2e.journeys;

import io.qaplayground.e2e.pages.JsonPage;
import io.qaplayground.e2e.support.E2eTest;
import io.qaplayground.e2e.support.Playground;
import io.qaplayground.testing.technique.AcceptanceTest;
import io.qaplayground.testing.technique.SecurityTest;
import io.qaplayground.testing.technique.UseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey 5: redirects, as a browser follows them.
 *
 * <p>A browser's URL parser has the final word on where a {@code Location} leads. These tests use it to confirm the
 * open-redirect analysis (risk R1). The playground is opened as {@code localhost}; {@code 127.0.0.1} plays
 * "another site".
 */
@E2eTest
@UseCase
@AcceptanceTest
class RedirectJourneyTest {

    /**
     * A three-hop redirect chain ends on {@code /get}.
     */
    @Test
    @DisplayName("/redirect/3 follows three hops and lands on /get")
    void redirectChain(WebDriver driver) {
        JsonPage page = JsonPage.at(driver, "/redirect/3");
        assertThat(page.currentPath()).isEqualTo("/get");
        assertThat(page.json().get("url").asString()).endsWith("/get");
    }

    /**
     * Absolute redirects also end on {@code /get}, on the same host.
     */
    @Test
    @DisplayName("/absolute-redirect/2 stays on the same host")
    void absoluteRedirect(WebDriver driver) {
        JsonPage page = JsonPage.at(driver, "/absolute-redirect/2");
        assertThat(page.currentHost()).isEqualTo("localhost");
        assertThat(page.currentPath()).isEqualTo("/get");
    }

    /**
     * Regression for the defect test design found: {@code Location: /\127.0.0.1:port/get} would take the browser to
     * 127.0.0.1.
     *
     * <p>With Go-style re-serialisation the backslash is escaped, so the browser stays on localhost (and gets a 404 for
     * that odd path).
     */
    @SecurityTest
    @Test
    @DisplayName("A backslash target cannot move the browser to another host (R1)")
    void backslashStaysOnHost(WebDriver driver) {
        JsonPage page = new JsonPage(driver);
        driver.get(Playground.url("/redirect-to?url=%2F%5C127.0.0.1%3A" + Playground.port() + "%2Fget"));
        assertThat(page.currentHost()).isEqualTo("localhost");
    }

    /**
     * Why the allowlist hardening exists: browsers treat {@code ///host/path} as {@code //host/path}.
     *
     * <p>Without an allowlist the playground allows absolute redirects anyway, so this is by design. With an
     * allowlist it is refused (RedirectAllowListIT).
     */
    @SecurityTest
    @Test
    @DisplayName("Browsers read ///host as another host (why the allow-list refuses it)")
    void tripleSlashLeavesTheHost(WebDriver driver) {
        driver.get(Playground.url("/redirect-to?url=%2F%2F%2F127.0.0.1%3A" + Playground.port() + "%2Fget"));
        JsonPage page = new JsonPage(driver);
        assertThat(page.currentHost()).isEqualTo("127.0.0.1");
        assertThat(page.currentPath()).isEqualTo("/get");
    }

    /**
     * {@code /redirect-to} with an absolute URL takes the browser to the other site, as asked.
     */
    @Test
    @DisplayName("/redirect-to an absolute URL goes there")
    void redirectToAbsolute(WebDriver driver) {
        driver.get(Playground.url("/redirect-to?url=" + Playground.otherHostUrl("/get").replace(":", "%3A").replace("/", "%2F")));
        assertThat(new JsonPage(driver).currentHost()).isEqualTo("127.0.0.1");
    }
}
