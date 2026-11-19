package io.qaplayground.api.status;

import io.qaplayground.api.support.PlaygroundTest;
import io.qaplayground.testing.technique.BoundaryValue;
import io.qaplayground.testing.technique.EquivalencePartitioning;
import io.qaplayground.testing.technique.ErrorGuessing;
import io.restassured.config.RedirectConfig;
import io.restassured.config.RestAssuredConfig;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

/**
 * Tutorial: asserting on status codes, including the ones your test framework might treat as failures before you
 * see them (redirects!).
 */
class StatusCodesTest extends PlaygroundTest {

    /**
     * Redirect following off, so 3xx responses can be inspected directly.
     */
    private static final RestAssuredConfig NO_REDIRECTS =
            RestAssuredConfig.config().redirect(RedirectConfig.redirectConfig().followRedirects(false));

    /**
     * {@code /status/{code}} answers with the code you ask for.
     */
    @EquivalencePartitioning
    @ParameterizedTest(name = "/status/{0}")
    @ValueSource(ints = {200, 201, 204, 400, 404, 500, 503, 599})
    void returnsTheRequestedCode(int code) {
        when()
            .get("/status/" + code)
        .then()
            .statusCode(code);
    }

    /**
     * 1xx codes are interim responses: the client gets them <em>before</em> the real response.
     * Most HTTP clients, REST Assured included, swallow them and show you only the final 200.
     */
    @ParameterizedTest(name = "/status/{0} ends in 200")
    @ValueSource(ints = {100, 102, 103})
    void informationalCodesEndInFinal200(int code) {
        when()
            .get("/status/" + code)
        .then()
            .statusCode(200);
    }

    /**
     * 418 comes with a body and a link: the only status code with a joke in an RFC.
     */
    @Test
    void teapot() {
        when()
            .get("/status/418")
        .then()
            .statusCode(418)
            .header("X-More-Info", "http://tools.ietf.org/html/rfc2324")
            .body(equalTo("I'm a teapot!"));
    }

    /**
     * 401 includes a challenge, which is what makes a browser show a login prompt.
     */
    @Test
    void unauthorizedHasChallenge() {
        when()
            .get("/status/401")
        .then()
            .statusCode(401)
            .header("WWW-Authenticate", "Basic realm=\"Fake Realm\"")
            .body(emptyString());
    }

    /**
     * A 3xx code comes with a {@code Location}, or it wouldn't be a usable redirect.
     */
    @Test
    void redirectStatusHasLocation() {
        given()
            .config(NO_REDIRECTS)
        .when()
            .get("/status/302")
        .then()
            .statusCode(302)
            .header("Location", "/redirect/1");
    }

    /**
     * 406 comes with a JSON body and a message.
     */
    @Test
    void notAcceptableHasJsonBody() {
        when()
            .get("/status/406")
        .then()
            .statusCode(406)
            .contentType("application/json")
            .body("message", equalTo("Client did not request a supported media type"));
    }

    /**
     * Weighted choices: here 200 or 503. Run it in a loop and count to see the distribution.
     */
    @RepeatedTest(5)
    void weightedRandomStatus() {
        when()
            .get("/status/200:0.5,503:0.5")
        .then()
            .statusCode(anyOf(is(200), is(503)));
    }

    /**
     * Codes that aren't valid numbers get 400.
     */
    @EquivalencePartitioning
    @ParameterizedTest(name = "/status/{0} is 400")
    @ValueSource(strings = {"3.14", "foo", "600", "1024", "99"})
    void invalidCodesAre400(String code) {
        when()
            .get("/status/" + code)
        .then()
            .statusCode(400)
            .body("error", equalTo("Bad Request"))
            .body("detail", startsWith("invalid status code: "));
    }

    /**
     * A code outside 100 to 599 gets 400, and the message says the allowed range.
     */
    @BoundaryValue
    @Test
    void outOfRangeMessage() {
        when()
            .get("/status/600")
        .then()
            .body("detail", equalTo("invalid status code: 600 not in range [100, 599]"));
    }

    /**
     * A weight that isn't a number is refused.
     */
    @ErrorGuessing
    @Test
    void badWeightIs400() {
        when()
            .get("/status/200:foo,500:1")
        .then()
            .statusCode(400)
            .body("detail", equalTo("invalid weight value: \"foo\""));
    }

    /**
     * A status URL with a segment missing or added doesn't match a route, so it is 404.
     */
    @EquivalencePartitioning
    @Test
    void missingOrExtraSegmentsAre404() {
        when().get("/status").then().statusCode(404);
        when().get("/status/").then().statusCode(404);
        when().get("/status/200/foo").then().statusCode(404);
    }

    /**
     * {@code /unstable} with a seed is deterministic, and gives the same answer as go-httpbin for the same seed.
     *
     * <p>Without a seed it fails at random, which is great for testing retry logic.
     */
    @Test
    void unstableIsReproducibleWithSeed() {
        when().get("/unstable?seed=1234567890").then().statusCode(500);
        when().get("/unstable?seed=1234567890&failure_rate=0.07").then().statusCode(200);
    }

    /**
     * {@code failure_rate=0} never fails, and {@code failure_rate=1} always does.
     */
    @BoundaryValue
    @Test
    void unstableExtremes() {
        when().get("/unstable?failure_rate=0").then().statusCode(200);
        when().get("/unstable?failure_rate=1").then().statusCode(500);
    }

    /**
     * A failure rate outside 0 to 1, or a seed that isn't a whole number, is refused.
     */
    @ErrorGuessing
    @ParameterizedTest(name = "/unstable?{0} is 400")
    @ValueSource(strings = {"seed=foo", "seed=3.14", "failure_rate=foo", "failure_rate=1.5", "failure_rate=-1"})
    void unstableRejectsBadParameters(String query) {
        when()
            .get("/unstable?" + query)
        .then()
            .statusCode(400);
    }
}
