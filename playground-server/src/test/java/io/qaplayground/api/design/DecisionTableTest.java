package io.qaplayground.api.design;

import io.qaplayground.api.support.PlaygroundTest;
import io.qaplayground.testing.technique.DecisionTable;
import io.restassured.config.RedirectConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * Decision tables (docs/testing/TEST-DESIGN.md section 3).
 *
 * <p>Each row is one rule: a combination of conditions and the actions that follow. Rules with identical outcomes
 * are merged only where the implementation provably ignores a condition; the CSV comments say which.
 */
@DecisionTable
class DecisionTableTest extends PlaygroundTest {

    /**
     * A request that doesn't follow redirects, so each rule's own status is visible.
     */
    private static RequestSpecification noFollow() {
        return given().config(RestAssuredConfig.config().redirect(RedirectConfig.redirectConfig().followRedirects(false)));
    }

    // --- 3.1 /redirect-to ---------------------------------------------------------------------

    /**
     * {@code /redirect-to}: target and status code decide between a redirect, 400 and 403.
     */
    @ParameterizedTest(name = "{0}")
    @CsvFileSource(resources = "/api/design/redirect-to.csv", numLinesToSkip = 1)
    void redirectTo(String rule, String url, String statusCode, int expectedStatus, String location, String detail) {
        RequestSpecification req = noFollow();
        if (url != null) {
            req.queryParam("url", url);
        }
        if (statusCode != null) {
            req.queryParam("status_code", statusCode);
        }
        ValidatableResponse r = req.get("/redirect-to").then().statusCode(expectedStatus);
        r.header("Location", location == null ? nullValue() : equalTo(location));
        if (detail != null) {
            r.body("detail", equalTo(detail));
        }
        if (expectedStatus == 403) {
            r.body(emptyString()); // empty unless an allow-list is configured, as upstream
        }
    }

    // --- 3.2 conditional requests on /etag (wire level; the full table is unit-tested) --------

    /**
     * {@code /etag/{etag}}: method, {@code If-Match} and {@code If-None-Match} together decide 200, 304 or 412.
     */
    @ParameterizedTest(name = "R{index}: {0} If-Match={1} If-None-Match={2} -> {3}")
    @CsvSource(delimiter = '|', value = {
            "GET | -         | -          | 200",
            "GET | -         | \"v1\"     | 304",
            "GET | \"v1\"    | -          | 200",
            "GET | \"other\" | -          | 412",
            "GET | \"other\" | \"v1\"     | 412",
            "GET | *         | -          | 200",
            "GET | -         | W/\"v1\"   | 304",
            "PUT | -         | \"v1\"     | 412"})
    void etagPreconditions(String method, String ifMatch, String ifNoneMatch, int status) {
        RequestSpecification req = given();
        if (!ifMatch.equals("-")) {
            req.header("If-Match", ifMatch);
        }
        if (!ifNoneMatch.equals("-")) {
            req.header("If-None-Match", ifNoneMatch);
        }
        req.request(method, "/etag/v1").then().statusCode(status);
    }

    // --- 3.3 basic auth vs hidden basic auth --------------------------------------------------

    /**
     * Basic and hidden basic auth side by side: the same credentials, two different answers to failure.
     */
    @ParameterizedTest(name = "{0}: /basic-auth -> {2}, /hidden-basic-auth -> {3}")
    @CsvSource(delimiter = '|', value = {
            "R1 no Authorization header|-|401|404",
            "R1 header not decodable|Basic !!!|401|404",
            "R2 wrong user|Basic d3Jvbmc6cGFzcw==|401|404",      // wrong:pass
            "R3 wrong password|Basic dXNlcjp3cm9uZw==|401|404",  // user:wrong
            "R4 both correct|Basic dXNlcjpwYXNz|200|200"})        // user:pass
    void basicAuth(String rule, String authorization, int basic, int hidden) {
        RequestSpecification b = given();
        RequestSpecification h = given();
        if (!authorization.equals("-")) {
            b.header("Authorization", authorization);
            h.header("Authorization", authorization);
        }
        ValidatableResponse basicResponse = b.get("/basic-auth/user/pass").then().statusCode(basic);
        ValidatableResponse hiddenResponse = h.get("/hidden-basic-auth/user/pass").then().statusCode(hidden);
        if (basic == 401) {
            basicResponse.header("WWW-Authenticate", "Basic realm=\"Fake Realm\"");
            hiddenResponse.header("WWW-Authenticate", nullValue()); // hidden: no challenge, so clients don't retry
        } else {
            basicResponse.body("authenticated", equalTo(true)).body("user", equalTo("user"));
        }
    }
}
