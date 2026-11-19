package io.qaplayground.server.support;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.qaplayground.testing.Generators;
import io.qaplayground.testing.technique.BoundaryValue;
import io.qaplayground.testing.technique.EquivalencePartitioning;
import io.qaplayground.testing.technique.PropertyBased;
import io.qaplayground.testing.technique.SecurityTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The small helpers that shape responses: content safety, status text, error bodies, pacing and bundled assets.
 */
class ResponseHelpersTest {

    // --- ContentSafety (risk R2, reflected XSS) -------------------------------------------------

    @SecurityTest
    @EquivalencePartitioning
    @ParameterizedTest(name = "\"{0}\" dangerous: {1}")
    @CsvSource(delimiter = '|', value = {
            "text/plain|false",
            "application/json; charset=utf-8|false",
            "TEXT/PLAIN|false",                       // media types are case-insensitive
            "application/octet-string|false",         // sic: the reference's list
            "text/html|true",
            "image/svg+xml|true",                     // SVG can carry script
            "application/xhtml+xml|true",
            "application/octet-stream|true",          // not on the safe list
            "''|true",                                 // missing type: assume the worst
            "not a media type|true",
            "text/plain<script>|true"})
    void onlyKnownSafeTypesAreServedRaw(String contentType, boolean dangerous) {
        assertThat(ContentSafety.isDangerous(contentType)).isEqualTo(dangerous);
    }

    @SecurityTest
    @Test
    void escapeHtmlNeutralisesMarkup() {
        assertThat(ContentSafety.escapeHtml("<img src=x onerror='a&b'>\""))
                .isEqualTo("&lt;img src=x onerror=&#39;a&amp;b&#39;&gt;&#34;");
    }

    // --- StatusText ---------------------------------------------------------------------------

    @EquivalencePartitioning
    @ParameterizedTest(name = "{0} -> \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "200|OK", "418|I'm a teapot", "413|Request Entity Too Large", "416|Requested Range Not Satisfiable",
            "103|Early Hints", "299|''", "600|''"})
    void reasonPhrasesUseGoSpelling(int status, String text) {
        assertThat(StatusText.of(status)).isEqualTo(text);
    }

    // --- HttpbinResponses ---------------------------------------------------------------------

    /**
     * The error body has go-httpbin's field order, and an empty {@code detail} is left out.
     */
    @Test
    void errorBodyFieldOrderAndOmitEmpty() {
        assertThat(HttpbinResponses.errorBody(400, "bad")).containsExactly(
                Map.entry("status_code", 400), Map.entry("error", "Bad Request"), Map.entry("detail", "bad"));
        assertThat(HttpbinResponses.errorBody(404, null)).containsOnlyKeys("status_code", "error");
        assertThat(HttpbinResponses.errorBody(404, "")).containsOnlyKeys("status_code", "error");
    }

    @Test
    void errorResponseIsPrettyJson() {
        ResponseEntity<byte[]> e = HttpbinResponses.error(400, "x");
        assertThat(e.getStatusCode().value()).isEqualTo(400);
        assertThat(e.getHeaders().getFirst("Content-Type")).isEqualTo(HttpbinResponses.JSON);
        assertThat(new String(e.getBody(), StandardCharsets.UTF_8))
                .isEqualTo("{\n  \"status_code\": 400,\n  \"error\": \"Bad Request\",\n  \"detail\": \"x\"\n}\n");
    }

    /**
     * Re-wrapping a response for a streaming handler changes nothing a client can see.
     */
    @Test
    void asStreamKeepsStatusHeadersAndBody() throws Exception {
        ResponseEntity<StreamingResponseBody> s = HttpbinResponses.asStream(HttpbinResponses.text(418, "tea"));
        assertThat(s.getStatusCode().value()).isEqualTo(418);
        assertThat(s.getHeaders().getContentLength()).isEqualTo(3);
        assertThat(s.getHeaders().getFirst("Content-Type")).isEqualTo(HttpbinResponses.TEXT);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        s.getBody().writeTo(out);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("tea");

        ResponseEntity<StreamingResponseBody> empty = HttpbinResponses.asStream(ResponseEntity.noContent().build());
        assertThat(empty.getHeaders().getContentLength()).isZero();
    }

    @Test
    void htmlAndJsonHelpers() {
        assertThat(HttpbinResponses.html("<p>".getBytes()).getHeaders().getFirst("Content-Type")).isEqualTo(HttpbinResponses.HTML);
        assertThat(HttpbinResponses.json(Map.of()).getStatusCode().value()).isEqualTo(200);
    }

    // --- Pacing -------------------------------------------------------------------------------

    @Test
    void serverTimingHeaderFormat() {
        assertThat(Pacing.serverTiming(List.of(
                new Pacing.Timing("total_duration", Duration.ofMillis(1500), "total request duration"),
                new Pacing.Timing("delay", Duration.ofNanos(1_005_000), "requested delay"))))
                .isEqualTo("total_duration;dur=1500.00;desc=\"total request duration\", "
                        + "delay;dur=1.00;desc=\"requested delay\""); // 1.005 rounds half-even to 1.00
    }

    @BoundaryValue
    @ParameterizedTest(name = "{1} writes over {0} ms -> pause {2} ns")
    @CsvSource({"1000, 0, 1000000000", "1000, 1, 1000000000", "1000, 2, 1000000000",
            "1000, 3, 500000000", "1000, 4, 333333334"})
    void pausePerWriteSpreadsWritesWithoutTrailingPause(long millis, long count, long expectedNanos) {
        assertThat(Pacing.pausePerWrite(Duration.ofMillis(millis), count)).isEqualTo(Duration.ofNanos(expectedNanos));
    }

    /**
     * Seeded random pairs of a duration and a number of writes.
     */
    static Stream<Arguments> durationsAndCounts() {
        return Generators.samples(r -> new long[]{r.nextLong(0, 10_000_000_000L), r.nextLong(2, 10_000)});
    }

    /**
     * The writes never finish early, and finish late by less than one nanosecond per gap.
     */
    @PropertyBased
    @ParameterizedTest
    @MethodSource("durationsAndCounts")
    void pausesCoverTheDurationTightly(long[] nanosAndCount) {
        long gaps = nanosAndCount[1] - 1;
        long pause = Pacing.pausePerWrite(Duration.ofNanos(nanosAndCount[0]), nanosAndCount[1]).toNanos();
        assertThat(pause * gaps).isGreaterThanOrEqualTo(nanosAndCount[0]).isLessThan(nanosAndCount[0] + gaps);
    }

    @Test
    void jitterStaysWithinItsBand() {
        Duration d = Duration.ofMillis(1000);
        assertThat(Pacing.withJitter(d, 0)).isEqualTo(d);
        assertThat(Pacing.withJitter(d, -1)).isEqualTo(d);
        for (int i = 0; i < 200; i++) {
            assertThat(Pacing.withJitter(d, 0.5)).isBetween(Duration.ofMillis(500), Duration.ofMillis(1500));
        }
    }

    /**
     * With the random draw passed in, jitter is exact: a draw of 0 gives the low edge of the band, 0.5 the middle,
     * and 0.75 halfway to the high edge.
     */
    @BoundaryValue
    @ParameterizedTest(name = "jitter 0.5, draw {0} -> {1} ms")
    @CsvSource({"0.0, 500", "0.5, 1000", "0.75, 1250"})
    void jitterIsExactForAGivenDraw(double draw, long expectedMillis) {
        assertThat(Pacing.withJitter(Duration.ofMillis(1000), 0.5, draw)).isEqualTo(Duration.ofMillis(expectedMillis));
    }

    /**
     * Zero or negative durations return at once. An interrupted sleep returns false and keeps the interrupt flag set.
     */
    @Test
    void sleepHandlesZeroNegativeAndInterruption() {
        assertThat(Pacing.sleep(Duration.ZERO)).isTrue();
        assertThat(Pacing.sleep(Duration.ofMillis(-1))).isTrue();
        assertThat(Pacing.sleep(Duration.ofMillis(1))).isTrue();
        Thread.currentThread().interrupt();
        try {
            assertThat(Pacing.sleep(Duration.ofSeconds(5))).isFalse();
            assertThat(Thread.currentThread().isInterrupted()).as("interrupt flag restored").isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    // --- StaticAssets -------------------------------------------------------------------------

    @Test
    void bundledAssetsAreFoundAndMissingOnesReported() {
        assertThat(StaticAssets.find("static-httpbin/sample.json")).isNotEmpty();
        assertThat(StaticAssets.get("static-httpbin/sample.json")).isSameAs(StaticAssets.find("static-httpbin/sample.json"));
        assertThat(StaticAssets.find("static-httpbin/missing")).isNull();
        assertThatThrownBy(() -> StaticAssets.get("static-httpbin/missing")).hasMessageContaining("Missing bundled asset");
    }

    // --- HeaderNames --------------------------------------------------------------------------

    @EquivalencePartitioning
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"content-type, Content-Type", "X-FORWARDED-FOR, X-Forwarded-For", "x-1-y, X-1-Y",
            "www-authenticate, Www-Authenticate", "a, A", "-x, -X"})
    void canonicalHeaderNames(String in, String out) {
        assertThat(HeaderNames.canonical(in)).isEqualTo(out);
    }

    /**
     * Header names with characters that aren't allowed in a token are returned unchanged, as in Go.
     */
    @Test
    void namesWithNonTokenCharactersAreLeftAlone() {
        assertThat(HeaderNames.canonical("bad header")).isEqualTo("bad header");
        assertThat(HeaderNames.canonical("x:y")).isEqualTo("x:y");
    }
}
