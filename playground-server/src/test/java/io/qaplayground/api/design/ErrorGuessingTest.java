package io.qaplayground.api.design;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.qaplayground.api.support.PlaygroundTest;
import io.qaplayground.api.support.RawHttp;
import io.qaplayground.testing.technique.ErrorGuessing;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.startsWith;

/**
 * Error guessing (docs/testing/TEST-DESIGN.md section 6): inputs chosen from a fault attack list.
 *
 * <p>Each test names the fault it guesses at. One oracle covers them all: whatever the input, the server answers
 * with a deliberate 4xx, never a 5xx and never a hang.
 */
@ErrorGuessing
class ErrorGuessingTest extends PlaygroundTest {

    /**
     * Fault guessed: number parsing overflows on huge or special values.
     *
     * <p>It did: {@code /delay/1e18} answered 500 before the fix (an overflow while formatting the error); see
     * GoDurationTest.
     */
    @ParameterizedTest(name = "/delay/{0} -> 400 without a server error")
    @ValueSource(strings = {"1e18", "Infinity", "-Infinity", "9223372036854775807", "1e309", "2562048h"})
    void hugeDurations(String value) {
        given().get("/delay/" + value).then().statusCode(400).body("status_code", equalTo(400));
    }

    @Test
    @DisplayName("Fault guessed: NaN slips through as a number; it means 0 s here, as in Go")
    void nanDelay() {
        given().get("/delay/NaN").then().statusCode(200);
    }

    /**
     * Fault guessed: counts that aren't plain decimals (int64 overflow, a sign, hex, an exponent, an encoded space)
     * are parsed anyway.
     */
    @ParameterizedTest(name = "/bytes/{0} -> 400")
    @ValueSource(strings = {"9223372036854775808", "-1", "0x10", "1e3", "%20"})
    void bytesCountParsing(String value) {
        given().urlEncodingEnabled(false).get("/bytes/" + value).then().statusCode(400);
    }

    /**
     * Fault guessed: a bad {@code %} escape crashes query parsing. REST Assured refuses to send these ("Malformed
     * escape
     * pair"), so a raw socket does.
     */
    @ParameterizedTest(name = "percent-decoding fault: /get?{0}")
    @ValueSource(strings = {"a=%", "a=%4", "a=%%", "a=%zz", "%zz=1"})
    void malformedEscapesAreSkippedNotFatal(String query) {
        RawHttp.Reply r = RawHttp.get("/get?" + query + "&ok=1");
        assertThat(r.status()).isEqualTo(200);
        assertThat(JsonPath.from(r.body()).getList("args.ok", String.class)).containsExactly("1");
    }

    @Test
    @DisplayName("Unicode faults: emoji, combining marks and CJK survive the round trip")
    void unicodeRoundTrip() {
        String value = "😀 é 漢字";
        given().queryParam("q", value).get("/get").then().body("args.q[0]", equalTo(value));
        given().contentType("application/json").body("{\"q\":\"" + value + "\"}").post("/post").then()
                .body("json.q", equalTo(value));
    }

    /**
     * Fault guessed: odd repetitions such as {@code ?a&a=} lose values. Sent over a raw socket, because REST Assured
     * would normalise them first.
     */
    @Test
    @DisplayName("Duplicate keys keep every value, in order (a bare key is an empty value)")
    void duplicateKeys() {
        RawHttp.Reply r = RawHttp.get("/get?a=1&a=2&a&a=");
        assertThat(JsonPath.from(r.body()).getList("args.a", String.class)).containsExactly("1", "2", "", "");
    }

    @Test
    @DisplayName("Empty JSON body with a JSON Content-Type is not a parse error")
    void emptyJsonBody() {
        given().contentType("application/json").post("/post").then().statusCode(200).body("json", org.hamcrest.Matchers.nullValue());
    }

    @Test
    @DisplayName("Range amplification: 1000 one-byte ranges ask for more than exists and are ignored")
    void manyRanges() {
        String ranges = IntStream.range(0, 1000).mapToObj(i -> (i % 26) + "-" + (i % 26)).collect(Collectors.joining(","));
        Response r = given().header("Range", "bytes=" + ranges).get("/range/26");
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.asString()).hasSize(26);
    }

    /**
     * Fault guessed: input that isn't base64 at all gets through to the decoder's internals.
     */
    @ParameterizedTest(name = "/base64/{0} -> 400")
    @CsvSource(delimiter = '|', value = {"%3D%3D%3D", "a", "aGVsbG8", "!!!!"})
    void base64Garbage(String data) {
        given().urlEncodingEnabled(false).get("/base64/" + data).then().statusCode(400)
                .body("detail", startsWith("base64 decode failed: illegal base64 data at input byte"));
    }

    @Test
    @DisplayName("Oversized header value: the container rejects it, the server stays up")
    void hugeHeader() {
        int status = given().header("X-Big", "x".repeat(20_000)).get("/headers").statusCode();
        assertThat(status).isBetween(400, 499);
        given().get("/get").then().statusCode(200);
    }

    @Test
    @DisplayName("Very long URL: rejected with a 4xx, not a 5xx")
    void hugeUrl() {
        given().get("/anything/" + "a".repeat(20_000)).then().statusCode(lessThan(500));
    }
}
