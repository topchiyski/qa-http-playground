package io.qaplayground.e2e.journeys;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import io.qaplayground.e2e.pages.IndexPage;
import io.qaplayground.e2e.pages.JsonPage;
import io.qaplayground.e2e.support.E2eTest;
import io.qaplayground.e2e.support.Playground;
import io.qaplayground.testing.technique.AcceptanceTest;
import io.qaplayground.testing.technique.UseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey 1: a learner discovers the endpoints.
 *
 * <p><b>Given</b> a learner opens the playground's home page <b>When</b> they look for an endpoint and follow its
 * link <b>Then</b> the page groups endpoints by topic, every link works, and a link opens a live response.
 */
@E2eTest
@AcceptanceTest
@UseCase
class DiscoverEndpointsJourneyTest {

    /**
     * The home page groups the endpoints under topic headings.
     */
    @Test
    @DisplayName("The home page groups endpoints by topic")
    void groupsEndpointsByTopic(WebDriver driver) {
        IndexPage index = IndexPage.open(driver);
        assertThat(index.title()).contains("qa-http-playground");
        assertThat(index.groupNames()).contains("Request inspection", "HTTP methods", "Redirects", "Authentication");
    }

    /**
     * A crawl: every same-origin link on the page resolves, with no 404 and no server error.
     */
    @Test
    @DisplayName("Every link on the home page works")
    void everyLinkWorks(WebDriver driver) throws Exception {
        List<String> broken = new ArrayList<>();
        for (String link : IndexPage.open(driver).internalLinks()) {
            // HttpURLConnection, not the JDK HttpClient: /sse and /trailers send HTTP trailers, which
            // HttpClient cannot parse ("CR expected"). The status line is all we need here.
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) URI.create(Playground.url(link)).toURL().openConnection();
            c.setInstanceFollowRedirects(true);
            int status = c.getResponseCode();
            c.disconnect();
            boolean serverErrorByDesign = link.startsWith("/unstable") || link.startsWith("/status/5") || link.equals("/brotli");
            boolean notFoundByDesign = link.startsWith("/hidden-basic-auth"); // 404 without credentials is the point
            if ((status == 404 && !notFoundByDesign) || (status >= 500 && !serverErrorByDesign)) {
                broken.add(link + " -> " + status);
            }
        }
        assertThat(broken).isEmpty();
    }

    /**
     * Following a link opens a live response, not a description of one.
     */
    @Test
    @DisplayName("Following a link shows the live JSON response")
    void followALink(WebDriver driver) {
        JsonPage page = IndexPage.open(driver).follow("Try /get");
        assertThat(page.json().at("/args/hello/0").asString()).isEqualTo("world");
        assertThat(page.json().at("/headers/User-Agent/0").asString()).isNotBlank();
    }
}
