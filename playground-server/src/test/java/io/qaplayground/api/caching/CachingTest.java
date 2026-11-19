package io.qaplayground.api.caching;

import io.qaplayground.api.support.PlaygroundTest;
import io.qaplayground.testing.technique.DecisionTable;
import io.qaplayground.testing.technique.StateTransition;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Tutorial: HTTP caching and conditional requests.
 *
 * <p>A typical flow: GET, remember the {@code ETag}, send it back in {@code If-None-Match}, expect 304.
 */
class CachingTest extends PlaygroundTest {

    /**
     * Both validators exactly as go-httpbin formats them (note: the ETag is not quoted).
     */
    @Test
    void cacheReturnsValidators() {
        when()
            .get("/cache")
        .then()
            .statusCode(200)
            .header("ETag", matchesPattern("[0-9a-f]+da39a3ee5e6b4b0d3255bfef95601890afd80709"))
            .header("Last-Modified", matchesPattern("\\w{3}, \\d{2} \\w{3} \\d{4} \\d{2}:\\d{2}:\\d{2} UTC"))
            .body("url", org.hamcrest.Matchers.endsWith("/cache"));
    }

    /**
     * Any If-None-Match or If-Modified-Since value gives 304 with an empty body.
     */
    @StateTransition
    @Test
    void cacheRevalidationGives304() {
        String etag = when().get("/cache").then().extract().header("ETag");
        given()
            .header("If-None-Match", etag)
        .when()
            .get("/cache")
        .then()
            .statusCode(304)
            .body(emptyString());

        given().header("If-Modified-Since", "anything").when().get("/cache").then().statusCode(304);
    }

    /**
     * {@code /cache/{n}} says how long a copy stays fresh: {@code Cache-Control: public, max-age=n}.
     */
    @Test
    void cacheControlMaxAge() {
        when()
            .get("/cache/60")
        .then()
            .statusCode(200)
            .header("Cache-Control", "public, max-age=60");
    }

    /**
     * A freshness time that isn't a number is a client error, 400.
     */
    @Test
    void cacheControlRejectsNonNumbers() {
        when()
            .get("/cache/abc")
        .then()
            .statusCode(400)
            .body("detail", equalTo("invalid seconds: strconv.ParseInt: parsing \"abc\": invalid syntax"));
    }

    /**
     * {@code /etag/{etag}} sends the tag quoted, as RFC 9110 requires.
     */
    @Test
    void etagIsQuoted() {
        when()
            .get("/etag/v1")
        .then()
            .statusCode(200)
            .header("ETag", "\"v1\"")
            .header("Accept-Ranges", "bytes");
    }

    /**
     * If-None-Match matching the ETag: 304, without body or Content-Type.
     */
    @Test
    void ifNoneMatchGives304() {
        given()
            .header("If-None-Match", "\"v1\"")
        .when()
            .get("/etag/v1")
        .then()
            .statusCode(304)
            .header("ETag", "\"v1\"")
            .header("Content-Type", nullValue())
            .body(emptyString());
    }

    /**
     * Weak comparison: W/"v1" matches "v1" for If-None-Match. Lists and * work too.
     */
    @DecisionTable
    @Test
    void ifNoneMatchIsWeakAndAcceptsLists() {
        given().header("If-None-Match", "W/\"v1\"").get("/etag/v1").then().statusCode(304);
        given().header("If-None-Match", "\"a\", \"v1\"").get("/etag/v1").then().statusCode(304);
        given().header("If-None-Match", "*").get("/etag/v1").then().statusCode(304);
        given().header("If-None-Match", "\"other\"").get("/etag/v1").then().statusCode(200);
    }

    /**
     * If-Match is how optimistic locking works: proceed only if nobody changed the resource.
     */
    @DecisionTable
    @Test
    void ifMatchMismatchGives412() {
        given().header("If-Match", "\"stale\"").get("/etag/v1").then().statusCode(412);
        given().header("If-Match", "\"v1\"").get("/etag/v1").then().statusCode(200);
        given().header("If-Match", "W/\"v1\"").get("/etag/v1").then().statusCode(412); // strong comparison
    }

    /**
     * For unsafe methods a matching If-None-Match is 412, not 304.
     */
    @DecisionTable
    @Test
    void ifNoneMatchOnPutIs412() {
        given().header("If-None-Match", "\"v1\"").put("/etag/v1").then().statusCode(412);
    }
}
