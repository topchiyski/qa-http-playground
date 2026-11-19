package io.qaplayground.api.design;

import java.util.stream.Stream;

import io.qaplayground.api.support.LimitedPlayground;
import io.qaplayground.api.support.PlaygroundTest;
import io.qaplayground.testing.Boundaries;
import io.qaplayground.testing.Boundaries.Point;
import io.qaplayground.testing.technique.BoundaryValue;
import io.restassured.config.RedirectConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import static io.qaplayground.api.support.LimitedPlayground.MAX_BODY_BYTES;
import static io.qaplayground.api.support.LimitedPlayground.MAX_LINKS;
import static io.qaplayground.api.support.LimitedPlayground.MAX_REDIRECTS;
import static io.qaplayground.api.support.LimitedPlayground.MAX_STREAM_LINES;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

/**
 * 3-value boundary value analysis of every configured limit (risk R3), against a server with lowered limits.
 * Derivation: docs/testing/TEST-DESIGN.md section 2.
 *
 * <p>Each test reads: below the boundary, on it, above it. Three limits behave in ways a tester wouldn't assume, and
 * the tests say so: {@code /stream} clamps instead of refusing, {@code /delay} includes its maximum, and oversized
 * bodies get 400 (not 413), as in the reference.
 */
@BoundaryValue
class LimitBoundaryTest extends PlaygroundTest {

    /**
     * A request to the server with lowered limits.
     */
    private static RequestSpecification limited() {
        return given().baseUri(LimitedPlayground.baseUri())
                .config(RestAssuredConfig.config().redirect(RedirectConfig.redirectConfig().followRedirects(false)));
    }

    /**
     * Below, on and above {@code limit}, with the status expected at each.
     */
    private static Stream<Arguments> around(long limit, int below, int on, int above) {
        var points = Boundaries.around(limit);
        return Stream.of(Arguments.of(points.get(0), below), Arguments.of(points.get(1), on), Arguments.of(points.get(2), above));
    }

    // --- durations (max 1 s) ------------------------------------------------------------------

    /**
     * The {@code /delay} maximum, in milliseconds, and its neighbours.
     */
    static Stream<Arguments> delayUpper() {
        return around(LimitedPlayground.MAX_DURATION_MS, 200, 200, 400);
    }

    /**
     * Exactly the maximum delay is allowed; one millisecond more is not.
     */
    @ParameterizedTest(name = "/delay/{0}ms -> {1}")
    @MethodSource("delayUpper")
    void delayUpperLimitIsInclusive(Point millis, int status) {
        limited().get("/delay/" + millis.text() + "ms").then().statusCode(status);
    }

    /**
     * Zero delay is allowed; anything below zero is not.
     */
    @ParameterizedTest(name = "/delay/{0} -> {1}")
    @CsvSource({"-1ms, 400", "0, 200", "1ms, 200"})
    void delayLowerLimit(String delay, int status) {
        limited().get("/delay/" + delay).then().statusCode(status);
    }

    /**
     * Plain seconds are converted with float arithmetic and truncated to milliseconds, exactly as in Go: 1.001 * 1000 =
     * 1000.999... becomes 1000 ms, which is within a 1 s limit.
     *
     * <p>Use Go duration syntax ({@code 1001ms}) when a boundary must be exact.
     */
    @Test
    @DisplayName("Boundary surprise: /delay/1.001 is accepted under a 1 s limit (float truncation, as in Go)")
    void floatSecondsLoseAMillisecond() {
        limited().get("/delay/1.001").then().statusCode(200);
        limited().get("/delay/1.002").then().statusCode(400);
    }

    /**
     * The {@code /drip} duration limit, in milliseconds, and its neighbours.
     */
    static Stream<Arguments> dripDuration() {
        return around(LimitedPlayground.MAX_DURATION_MS, 200, 200, 400);
    }

    /**
     * {@code /drip} allows exactly the maximum duration, and refuses one millisecond more.
     */
    @ParameterizedTest(name = "/drip?duration={0}ms -> {1}")
    @MethodSource("dripDuration")
    void dripDurationLimit(Point millis, int status) {
        limited().queryParam("duration", millis.text() + "ms").queryParam("delay", "0").queryParam("numbytes", "1")
                .get("/drip").then().statusCode(status);
    }

    // --- sizes (max 1 KiB) --------------------------------------------------------------------

    /**
     * The request body limit, in bytes, and its neighbours.
     */
    static Stream<Arguments> bodySize() {
        return around(MAX_BODY_BYTES, 200, 200, 400);
    }

    /**
     * A body of exactly the limit is accepted; one byte more gets 400.
     */
    @ParameterizedTest(name = "POST body of {0} bytes -> {1}")
    @MethodSource("bodySize")
    void requestBodyLimit(Point size, int status) {
        var response = limited().contentType("text/plain").body("x".repeat((int) size.asLong())).post("/post").then().statusCode(status);
        if (status == 400) {
            response.body("detail", equalTo("error parsing request body: http: request body too large"));
        }
    }

    /**
     * The lower boundary is 0, not 1. The design said "/bytes/0 -> 400"; the reference source says 0 is a valid empty
     * response and only negative counts fail (handlers.go, handleBytes).
     */
    static Stream<Arguments> generatedBytes() {
        return Stream.concat(around(0, 400, 200, 200), around(MAX_BODY_BYTES, 200, 200, 400));
    }

    /**
     * {@code /bytes} generates any count from 0 up to the body limit, and refuses the rest.
     */
    @ParameterizedTest(name = "/bytes/{0} -> {1}")
    @MethodSource("generatedBytes")
    void bytesCountLimits(Point n, int status) {
        limited().get("/bytes/" + n.text()).then().statusCode(status);
    }

    // --- counts -------------------------------------------------------------------------------

    /**
     * Rows: lower boundary 1 (0, 1, 2) and upper boundary max-stream-lines = 5 (4, 5, 6). Out-of-range counts are
     * clamped, not refused.
     */
    @ParameterizedTest(name = "/stream/{0} sends {1} lines")
    @CsvSource({"0, 1", "1, 1", "2, 2", "4, 4", "5, 5", "6, 5"})
    void streamClampsAtBothEnds(int n, int lines) {
        String body = limited().get("/stream/" + n).then().statusCode(200).extract().asString();
        org.assertj.core.api.Assertions.assertThat(body.lines().count()).isEqualTo(lines).isLessThanOrEqualTo(MAX_STREAM_LINES);
    }

    /**
     * A valid {@code /links/n} redirects to its first page, {@code /links/n/0} (302).
     */
    static Stream<Arguments> links() {
        return Stream.concat(around(0, 400, 302, 302), around(MAX_LINKS, 302, 302, 400));
    }

    /**
     * {@code /links} accepts counts up to the limit, and refuses the next one.
     */
    @ParameterizedTest(name = "/links/{0} -> {1}")
    @MethodSource("links")
    void linksLimits(Point n, int status) {
        limited().get("/links/" + n.text()).then().statusCode(status);
    }

    /**
     * The redirect count limit and its neighbours.
     */
    static Stream<Arguments> redirects() {
        return Stream.concat(around(1, 400, 302, 302), around(MAX_REDIRECTS, 302, 302, 400));
    }

    /**
     * A chain of exactly the maximum length is allowed; one more hop is refused.
     */
    @ParameterizedTest(name = "/redirect/{0} -> {1}")
    @MethodSource("redirects")
    void redirectCountLimits(Point n, int status) {
        var r = limited().get("/redirect/" + n.text()).then().statusCode(status);
        if (status == 400 && n.asLong() > 1) {
            r.body("detail", equalTo("redirect count must be <= " + MAX_REDIRECTS));
        }
    }

    // --- fixed ranges (no configuration involved, run against the limited server for speed) ---

    /**
     * {@code /status} accepts 100 to 599, and refuses the codes just outside.
     */
    @ParameterizedTest(name = "/status/{0} -> {1}")
    @CsvSource({"99, 400", "100, 200", "598, 598", "599, 599", "600, 400"})
    void statusCodeRange(int code, int status) {
        limited().get("/status/" + code).then().statusCode(status);
    }

    /**
     * 101 sits on the lower boundary too, but Apache HttpClient (inside REST Assured) treats a 101 without an upgrade
     * as interim, and waits for a final response that never comes. A raw socket shows what is really sent.
     *
     * <p>The lesson: know how your client handles 1xx before you test 1xx codes.
     */
    @Test
    @DisplayName("/status/101 -> final 101 (checked on the raw wire)")
    void status101OnTheWire() {
        org.assertj.core.api.Assertions.assertThat(io.qaplayground.api.support.RawHttp.get("/status/101").statusLine())
                .startsWith("HTTP/1.1 101");
    }

    /**
     * {@code /redirect-to} accepts status codes 300 to 399, and refuses the codes just outside.
     */
    @ParameterizedTest(name = "/redirect-to?status_code={0} -> {1}")
    @CsvSource({"299, 400", "300, 300", "301, 301", "398, 398", "399, 399", "400, 400"})
    void redirectToStatusRange(int code, int status) {
        var r = limited().queryParam("url", "/get").queryParam("status_code", code).get("/redirect-to").then().statusCode(status);
        if (status == 400) {
            r.body("detail", startsWith("invalid status code: " + code + " not in range [300, 399]"));
        }
    }
}
