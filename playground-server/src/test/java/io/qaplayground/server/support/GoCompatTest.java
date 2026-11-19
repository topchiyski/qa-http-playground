package io.qaplayground.server.support;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the Go ports against values taken from go-httpbin's and Go's own tests.
 *
 * <p>One class per port tests the details; this one is the quick cross-check that the ports agree with the
 * reference.
 */
class GoCompatTest {

    /**
     * The bytes for a fixed seed match the ones in go-httpbin's tests, byte for byte.
     */
    @Test
    void goRandomReproducesGoHttpbinSeedVector() {
        // handlers_test.go: /bytes/16?seed=1234567890
        byte[] want = {(byte) 0xbf, (byte) 0xcd, '*', (byte) 0xfa, 0x15, (byte) 0xa2, (byte) 0xb3, 'r',
                (byte) 0xc7, 0x07, (byte) 0x98, 'Z', '"', 0x02, 'J', (byte) 0x8e};
        GoRandom rng = new GoRandom(1234567890L);
        byte[] got = new byte[16];
        for (int i = 0; i < got.length; i++) {
            got[i] = (byte) rng.intn(256);
        }
        assertArrayEquals(want, got);
    }

    /**
     * {@code /unstable} with a seed picks the same outcomes as go-httpbin.
     */
    @Test
    void goRandomUnstableVectors() {
        // handlers_test.go: /unstable?seed=1234567890 -> 500 (default rate 0.5); with 0.07 -> 200
        double f = new GoRandom(1234567890L).float64();
        assertTrue(f < 0.5, "expected failure with rate 0.5, got " + f);
        assertTrue(f >= 0.07, "expected success with rate 0.07, got " + f);
    }

    @Test
    void parsesGoDurationsAndFloatSeconds() {
        assertEquals(Duration.ofMillis(1500), GoDuration.parse("1.5s"));
        assertEquals(Duration.ofMillis(1500), GoDuration.parse("1.5"));
        assertEquals(Duration.ofSeconds(90), GoDuration.parse("1m30s"));
        assertEquals(Duration.ofMillis(300), GoDuration.parse("300ms"));
        assertEquals(Duration.ofNanos(1500), GoDuration.parse("1.5us"));
        assertEquals(Duration.ZERO, GoDuration.parse("0"));
        assertEquals(Duration.ofSeconds(3), GoDuration.parse("3"));
    }

    @Test
    void durationErrorsUseGoMessages() {
        var e = assertThrows(GoDuration.DurationError.class, () -> GoDuration.parse("abc"));
        assertEquals("strconv.ParseFloat: parsing \"abc\": invalid syntax", e.getMessage());

        var bounded = assertThrows(GoDuration.DurationError.class,
                () -> GoDuration.parseBounded("11", Duration.ZERO, Duration.ofSeconds(10)));
        assertEquals("duration 11s longer than 10s", bounded.getMessage());

        var negative = assertThrows(GoDuration.DurationError.class,
                () -> GoDuration.parseBounded("-1s", Duration.ZERO, Duration.ofSeconds(10)));
        assertEquals("duration -1s shorter than 0s", negative.getMessage());
    }

    @Test
    void formatsDurationsLikeGo() {
        assertEquals("0s", GoDuration.format(Duration.ZERO));
        assertEquals("10s", GoDuration.format(Duration.ofSeconds(10)));
        assertEquals("1.5s", GoDuration.format(Duration.ofMillis(1500)));
        assertEquals("1m30s", GoDuration.format(Duration.ofSeconds(90)));
        assertEquals("1h0m0s", GoDuration.format(Duration.ofHours(1)));
        assertEquals("500ms", GoDuration.format(Duration.ofMillis(500)));
        assertEquals("1.5µs", GoDuration.format(Duration.ofNanos(1500)));
        assertEquals("999ns", GoDuration.format(Duration.ofNanos(999)));
        assertEquals("2h3m4.005s", GoDuration.format(Duration.ofMillis(7_384_005)));
    }

    @Test
    void strconvMessages() {
        var e = assertThrows(GoStrconv.NumberFormatError.class, () -> GoStrconv.atoi("3.14"));
        assertEquals("strconv.Atoi: parsing \"3.14\": invalid syntax", e.getMessage());
        var range = assertThrows(GoStrconv.NumberFormatError.class,
                () -> GoStrconv.parseInt("12345678901234567890", 64));
        assertEquals("strconv.ParseInt: parsing \"12345678901234567890\": value out of range", range.getMessage());
        assertEquals(-12345, GoStrconv.parseInt("-12345", 64));
    }

    @Test
    void prettyJsonMatchesGoEncoder() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("args", Map.of());
        body.put("headers", Map.of("B", List.of("2"), "A", List.of("1")));
        body.put("list", new ArrayList<>());
        body.put("html", "<a&b>");
        body.put("json", null);
        String expected = """
                {
                  "args": {},
                  "headers": {
                    "A": [
                      "1"
                    ],
                    "B": [
                      "2"
                    ]
                  },
                  "list": [],
                  "html": "<a&b>",
                  "json": null
                }
                """;
        assertEquals(expected, new String(GoJson.pretty(body)));
    }

    @Test
    void compactJsonEscapesHtmlLikeJsonMarshal() {
        assertEquals("{\"a\":\"\\u003cb\\u003e\\u0026\"}", GoJson.compact(Map.of("a", "<b>&")));
        assertEquals("\"tab\\tctl\\u0001\"", GoJson.compact("tab\tctl\u0001"));
    }

    @Test
    void floatsFormatLikeGo() {
        assertEquals("1", GoJson.formatFloat(1.0));
        assertEquals("1.5", GoJson.formatFloat(1.5));
        assertEquals("12345678901234567000", GoJson.formatFloat(12345678901234567890d));
        assertEquals("1e+21", GoJson.formatFloat(1e21));
        assertEquals("1e-7", GoJson.formatFloat(1e-7));
        assertEquals("0.000001", GoJson.formatFloat(1e-6));
        assertEquals("-2.5e-8", GoJson.formatFloat(-2.5e-8));
    }

    @Test
    void canonicalHeaderNames() {
        assertEquals("Accept-Encoding", HeaderNames.canonical("accept-encoding"));
        assertEquals("X-Request-Id", HeaderNames.canonical("X-REQUEST-ID"));
        assertEquals("Www-Authenticate", HeaderNames.canonical("WWW-Authenticate"));
        assertEquals("bad header", HeaderNames.canonical("bad header"));
    }

    @Test
    void queryStringFollowsGoRules() {
        Map<String, List<String>> q = QueryString.parse("a=1&b=x+y&a=2&bad=%zz&semi=1;2&empty&=v");
        assertEquals(List.of("1", "2"), q.get("a"));
        assertEquals(List.of("x y"), q.get("b"));
        assertEquals(List.of(""), q.get("empty"));
        assertEquals(List.of("v"), q.get(""));
        assertTrue(!q.containsKey("bad") && !q.containsKey("semi"));
    }

    @Test
    void ipv6IsPrintedInShortForm() {
        assertEquals("::1", RequestInfo.compressIpv6("0:0:0:0:0:0:0:1"));
        assertEquals("2001:db8::1", RequestInfo.compressIpv6("2001:db8:0:0:0:0:0:1"));
        assertEquals("127.0.0.1", RequestInfo.compressIpv6("127.0.0.1"));
    }
}
