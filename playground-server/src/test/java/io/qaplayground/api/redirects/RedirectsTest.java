package io.qaplayground.api.redirects;

import io.qaplayground.api.support.PlaygroundTest;
import io.qaplayground.testing.technique.EquivalencePartitioning;
import io.qaplayground.testing.technique.ErrorGuessing;
import io.qaplayground.testing.technique.SecurityTest;
import io.qaplayground.testing.technique.StateTransition;
import io.restassured.config.RedirectConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;

/**
 * Tutorial: redirects.
 *
 * <p>REST Assured follows redirects by default, which hides the interesting part. Most tests here switch following
 * off and assert on {@code Location} instead.
 */
class RedirectsTest extends PlaygroundTest {

    /**
     * A request that doesn't follow redirects, so each hop can be inspected.
     */
    private static RequestSpecification noFollow() {
        return given().config(RestAssuredConfig.config()
                .redirect(RedirectConfig.redirectConfig().followRedirects(false)));
    }

    /**
     * Following the chain lands on /get.
     */
    @StateTransition
    @Test
    void followingTheChainEndsAtGet() {
        given()
        .when()
            .get("/redirect/3")
        .then()
            .statusCode(200)
            .body("url", endsWith("/get"));
    }

    /**
     * The first hop of /redirect/n is a relative /relative-redirect/(n-1).
     */
    @Test
    void redirectUsesRelativeLocation() {
        noFollow()
            .get("/redirect/3")
        .then()
            .statusCode(302)
            .header("Location", "/relative-redirect/2")
            .body(emptyString());
    }

    /**
     * A redirect has no body; like the reference, it says so with Content-Length: 0.
     */
    @Test
    void redirectHasEmptyBodyWithContentLength() {
        noFollow()
            .get("/redirect/1")
        .then()
            .header("Content-Length", "0")
            .header("Transfer-Encoding", org.hamcrest.Matchers.nullValue());
    }

    /**
     * {@code ?absolute=true} switches the chain to absolute {@code Location} headers.
     */
    @Test
    void redirectAbsoluteParameter() {
        noFollow()
            .get("/redirect/2?absolute=TRUE")
        .then()
            .header("Location", matchesPattern("http://[^/]+/absolute-redirect/1"));
    }

    /**
     * The last hop of a chain points to {@code /get}.
     */
    @Test
    void lastHopGoesToGet() {
        noFollow().get("/relative-redirect/1").then().header("Location", "/get");
        noFollow().get("/absolute-redirect/1").then().header("Location", matchesPattern("http://[^/]+/get"));
    }

    /**
     * Absolute redirects use the scheme the client saw, so X-Forwarded-Proto changes them.
     */
    @Test
    void absoluteRedirectHonoursForwardedProto() {
        noFollow()
            .header("X-Forwarded-Proto", "https")
            .get("/absolute-redirect/2")
        .then()
            .header("Location", matchesPattern("https://[^/]+/absolute-redirect/1"));
    }

    /**
     * A redirect limit lower than the chain length makes the client give up.
     *
     * <p>REST Assured (through Apache HttpClient) doesn't return the last 3xx: it throws. Know how your tool behaves.
     */
    @Test
    void clientRedirectLimit() {
        var limited = given().config(RestAssuredConfig.config().redirect(RedirectConfig.redirectConfig()
                .followRedirects(true).maxRedirects(2)));
        Exception e = org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> limited.get("/redirect/5"));
        org.hamcrest.MatcherAssert.assertThat(e.getClass().getSimpleName(), equalTo("ClientProtocolException"));
    }

    /**
     * Redirect counts that aren't positive whole numbers are refused.
     */
    @EquivalencePartitioning
    @ParameterizedTest(name = "/redirect/{0} is 400")
    @ValueSource(strings = {"0", "-1", "foo", "3.14"})
    void invalidCountsAre400(String n) {
        noFollow()
            .get("/redirect/" + n)
        .then()
            .statusCode(400);
    }

    /**
     * {@code /redirect-to} uses the status code you ask for.
     */
    @Test
    void redirectToAbsoluteUrlWithStatus() {
        noFollow()
            .queryParam("url", "http://www.example.com/")
            .queryParam("status_code", 307)
            .get("/redirect-to")
        .then()
            .statusCode(307)
            .header("Location", "http://www.example.com/");
    }

    /**
     * A root-relative target ({@code /get}) is allowed.
     */
    @Test
    void redirectToRootRelativePath() {
        noFollow()
            .queryParam("url", "/get")
            .get("/redirect-to")
        .then()
            .statusCode(302)
            .header("Location", "/get");
    }

    /**
     * Without {@code url}, there is nowhere to go: 400.
     */
    @Test
    void redirectToRequiresUrl() {
        noFollow()
            .get("/redirect-to")
        .then()
            .statusCode(400)
            .body("detail", equalTo("missing required query parameter: url"));
    }

    /**
     * A status that isn't a 3xx code is refused: a redirect needs a redirect status.
     */
    @EquivalencePartitioning
    @ParameterizedTest(name = "status_code={0} is 400")
    @ValueSource(strings = {"201", "418", "/get"})
    void redirectToRejectsNonRedirectStatus(String status) {
        noFollow()
            .queryParam("url", "/get")
            .queryParam("status_code", status)
            .get("/redirect-to")
        .then()
            .statusCode(400);
    }

    /**
     * Open-redirect protection: targets must be absolute URLs or start with "/".
     */
    @EquivalencePartitioning
    @SecurityTest
    @ParameterizedTest(name = "url={0} is 403")
    @ValueSource(strings = {"foo", "//evil.com", "http%253A%252F%252Fevil.com"})
    void redirectToRefusesRelativeAndSchemeRelativeTargets(String target) {
        noFollow()
            .urlEncodingEnabled(false)
            .get("/redirect-to?url=" + target)
        .then()
            .statusCode(403);
    }

    /**
     * A URL that can't be parsed gets 400 with Go's message.
     */
    @ErrorGuessing
    @Test
    void redirectToRejectsMalformedUrl() {
        noFollow()
            .urlEncodingEnabled(false)
            .get("/redirect-to?url=http%3A%2F%2Ffoo%25%25bar")
        .then()
            .statusCode(400)
            .body("detail", equalTo("invalid url: parse \"http://foo%%bar\": invalid URL escape \"%%b\""));
    }
}
