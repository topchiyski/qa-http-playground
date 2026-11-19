package io.qaplayground.api.design;

import io.qaplayground.api.support.PlaygroundTest;
import io.qaplayground.testing.technique.EquivalencePartitioning;
import io.restassured.config.RedirectConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

/**
 * Equivalence partitioning of {@code /status/{code}} (docs/testing/TEST-DESIGN.md section 1.1).
 *
 * <p>The lesson: partitioning by status class (1xx to 5xx) isn't enough. Codes that come with extra headers or a body
 * are handled differently, so each is a partition of its own. One representative per partition; the test name
 * names the partition.
 */
@EquivalencePartitioning
class StatusCodePartitionTest extends PlaygroundTest {

    /**
     * The response to {@code /status/{code}}, without following redirects.
     */
    private static ValidatableResponse status(String code) {
        return given().config(RestAssuredConfig.config().redirect(RedirectConfig.redirectConfig().followRedirects(false)))
                .get("/status/" + code).then();
    }

    /**
     * Partitions where the status code is the whole answer: no {@code Location}, no challenge.
     */
    @ParameterizedTest(name = "{0}: representative {1}")
    @CsvSource(delimiter = '|', value = {
            "V2  1xx except 101/103: final 200 only|102|200",
            "V4  2xx|201|201",
            "V7  3xx without special case|304|304",
            "V7  3xx without special case|306|306",
            "V12 other 4xx|404|404",
            "V12 other 5xx|503|503"})
    void statusOnlyPartitions(String partition, String code, int status) {
        status(code).statusCode(status).header("Location", nullValue()).header("WWW-Authenticate", nullValue());
    }

    /**
     * 3xx codes that come with {@code Location: /redirect/1}.
     */
    @ParameterizedTest(name = "{0}: representative {1}")
    @CsvSource(delimiter = '|', value = {
            "V5 3xx redirecting to /redirect/1|303",
            "V5 3xx redirecting to /redirect/1|307"})
    void redirectPartition(String partition, String code) {
        status(code).statusCode(Integer.parseInt(code)).header("Location", "/redirect/1").body(emptyString());
    }

    /**
     * 308, which comes with a different {@code Location} and an HTML body.
     */
    @ParameterizedTest(name = "{0}: representative {1}")
    @CsvSource(delimiter = '|', value = {"V6 3xx with HTML body|300", "V6 3xx with HTML body|308"})
    void redirectWithHtmlPartition(String partition, String code) {
        status(code).statusCode(Integer.parseInt(code)).header("Location", "/image/jpeg")
                .body(org.hamcrest.Matchers.containsString("<a href=\"/image/jpeg\">"));
    }

    /**
     * 401, which comes with a {@code WWW-Authenticate} challenge.
     */
    @ParameterizedTest(name = "V8 401 challenge")
    @CsvSource("401")
    void unauthorizedPartition(String code) {
        status(code).statusCode(401).header("WWW-Authenticate", "Basic realm=\"Fake Realm\"");
    }

    /**
     * 402 and 418, which come with a joke body and an {@code X-More-Info} link.
     */
    @ParameterizedTest(name = "V9 joke bodies: {0}")
    @CsvSource(delimiter = '|', value = {"402|Fuck you, pay me!|http://vimeo.com/22053820", "418|I'm a teapot!|http://tools.ietf.org/html/rfc2324"})
    void jokeBodyPartition(String code, String body, String moreInfo) {
        status(code).statusCode(Integer.parseInt(code)).header("X-More-Info", moreInfo).body(equalTo(body));
    }

    /**
     * 406, which comes with a JSON body.
     */
    @ParameterizedTest(name = "V10 406 JSON body replaces the content type")
    @CsvSource("406")
    void notAcceptablePartition(String code) {
        status(code).statusCode(406).header("Content-Type", startsWith("application/json"))
                .body("message", equalTo("Client did not request a supported media type"));
    }

    /**
     * 407, which comes with a {@code Proxy-Authenticate} challenge.
     */
    @ParameterizedTest(name = "V11 407 proxy challenge")
    @CsvSource("407")
    void proxyAuthPartition(String code) {
        status(code).statusCode(407).header("Proxy-Authenticate", "Basic realm=\"Fake Realm\"");
    }

    /**
     * Invalid input, one partition per reason: below the range, above it, not a number, a malformed weight.
     */
    @ParameterizedTest(name = "{0}: \"{1}\" -> 400 {2}")
    @CsvSource(delimiter = '|', value = {
            "I1 below range|99|invalid status code: 99 not in range [100, 599]",
            "I2 above range|600|invalid status code: 600 not in range [100, 599]",
            "I3 non-numeric|abc|invalid status code: \"abc\": strconv.Atoi: parsing \"abc\": invalid syntax",
            "I4 malformed weight|200:x,500|invalid weight value: \"x\""})
    void invalidPartitions(String partition, String code, String detail) {
        status(code).statusCode(400).body("detail", equalTo(detail));
    }
}
