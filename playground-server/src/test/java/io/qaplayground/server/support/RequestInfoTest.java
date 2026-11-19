package io.qaplayground.server.support;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import io.qaplayground.server.TestProperties;
import io.qaplayground.testing.technique.EquivalencePartitioning;
import io.qaplayground.testing.technique.ErrorGuessing;
import io.qaplayground.testing.technique.SecurityTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the echo endpoints report about a request: headers, client IP and URL, as go-httpbin's {@code helpers.go}
 * works them out.
 */
class RequestInfoTest {

    private final RequestInfo info = new RequestInfo(TestProperties.defaults());

    /**
     * A GET request for {@code uri}, without a server.
     */
    private static MockHttpServletRequest get(String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", uri);
        r.setServerName("localhost");
        r.setServerPort(8080);
        return r;
    }

    @Test
    void headersAreCanonicalisedAndKeepAllValues() {
        MockHttpServletRequest r = get("/get");
        r.addHeader("x-multi", "1");
        r.addHeader("x-multi", "2");
        r.addHeader("accept-encoding", "gzip");
        r.addHeader("Host", "example.com:8080");
        Map<String, List<String>> headers = info.headers(r);
        assertThat(headers).containsEntry("X-Multi", List.of("1", "2"))
                .containsEntry("Accept-Encoding", List.of("gzip"))
                .containsEntry("Host", List.of("example.com:8080"));
        assertThat(new java.util.ArrayList<>(headers.keySet())).isSorted();
    }

    /**
     * {@code playground.exclude-headers} hides exactly the headers its patterns match, case-insensitively.
     */
    @EquivalencePartitioning
    @ParameterizedTest(name = "exclude \"{0}\" hides {1}: {2}")
    @CsvSource(delimiter = '|', value = {
            "x-fly-*|X-Fly-Region|true",
            "x-fly-*|X-Flyer|false",
            "via|Via|true",
            "VIA|Via|true",                   // case-insensitive
            "x-fly-*, via|Via|true",          // list with spaces
            "*-secret|X-Api-Secret|true",
            "' '|Via|false",                  // blank spec: nothing excluded
            "a.b|Axb|false"})                  // '.' is literal, not a regex wildcard
    void excludeHeaders(String spec, String header, boolean hidden) {
        RequestInfo filtered = new RequestInfo(TestProperties.with(Map.of("playground.exclude-headers", spec)));
        MockHttpServletRequest r = get("/get");
        r.addHeader(header, "v");
        assertThat(filtered.headers(r).containsKey(header)).isEqualTo(!hidden);
    }

    /**
     * Each proxy header, on its own, decides the reported client IP.
     */
    @SecurityTest
    @EquivalencePartitioning
    @ParameterizedTest(name = "{0}: {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "Fly-Client-IP|1.1.1.1|1.1.1.1",
            "CF-Connecting-IP|2.2.2.2|2.2.2.2",
            "Fastly-Client-IP|3.3.3.3|3.3.3.3",
            "True-Client-IP|4.4.4.4|4.4.4.4",
            "X-Forwarded-For|5.5.5.5, 10.0.0.1|5.5.5.5",
            "X-Forwarded-For|''|127.0.0.1"})
    void clientIpPrecedence(String header, String value, String expected) {
        MockHttpServletRequest r = get("/ip");
        r.setRemoteAddr("127.0.0.1");
        r.addHeader(header, value);
        assertThat(info.clientIp(r)).isEqualTo(expected);
    }

    /**
     * {@code Fly-Client-IP} wins over {@code X-Forwarded-For}, as in go-httpbin.
     */
    @Test
    void flyHeaderBeatsForwardedFor() {
        MockHttpServletRequest r = get("/ip");
        r.addHeader("X-Forwarded-For", "5.5.5.5");
        r.addHeader("Fly-Client-IP", "1.1.1.1");
        assertThat(info.clientIp(r)).isEqualTo("1.1.1.1");
    }

    @EquivalencePartitioning
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "0:0:0:0:0:0:0:1, ::1",
            "fe80:0:0:0:0:0:0:1, fe80::1",
            "2001:db8:0:0:1:0:0:1, 2001:db8::1:0:0:1",   // first of two equal runs wins (RFC 5952)
            "2001:db8:0:1:1:1:1:1, 2001:db8:0:1:1:1:1:1", // a single zero group is not shortened
            "0:0:0:0:0:0:0:0, ::",
            "1:0:0:0:0:0:0:0, 1::",
            "::1, ::1",
            "127.0.0.1, 127.0.0.1",
            "0001:00a0:0:0:0:0:0:0, 1:a0::"})
    void ipv6IsPrintedInShortForm(String full, String compact) {
        assertThat(RequestInfo.compressIpv6(full)).isEqualTo(compact);
    }

    @EquivalencePartitioning
    @ParameterizedTest(name = "{0}={1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "X-Forwarded-Proto|https|https://example.com/get?a=1",
            "X-Forwarded-Protocol|https|https://example.com/get?a=1",
            "X-Forwarded-Ssl|on|https://example.com/get?a=1",
            "X-Forwarded-Ssl|off|http://example.com/get?a=1",
            "X-Other|x|http://example.com/get?a=1"})
    void urlHonoursForwardedScheme(String header, String value, String url) {
        MockHttpServletRequest r = get("/get");
        r.setQueryString("a=1");
        r.addHeader("Host", "example.com");
        r.addHeader(header, value);
        assertThat(info.url(r)).isEqualTo(url);
    }

    @Test
    void secureRequestsAreHttpsAndMissingHostFallsBack() {
        MockHttpServletRequest r = get("/get");
        r.setSecure(true);
        assertThat(info.url(r)).isEqualTo("https://localhost:8080/get");
    }

    // --- EchoResponses ------------------------------------------------------------------------

    private final EchoResponses echo = new EchoResponses(info);

    /**
     * The no-body echo has go-httpbin's fields, in go-httpbin's order.
     */
    @Test
    void noBodyShapeHasTheReferenceFieldOrder() {
        assertThat(echo.noBody(get("/get")).keySet()).containsExactly("args", "headers", "method", "origin", "url");
    }

    /**
     * The with-body echo has the extra body fields, and a malformed body gets a 400 with the reference's message.
     */
    @Test
    void withBodyShapeAndParseErrors() {
        MockHttpServletRequest ok = get("/post");
        ok.setMethod("POST");
        ok.addHeader("Content-Type", "application/json");
        ok.setContent("{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        ResponseEntity<byte[]> response = echo.withBody(ok);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8))
                .contains("\"data\": \"{\\\"a\\\":1}\"").contains("\"json\": {\n    \"a\": 1\n  }");

        MockHttpServletRequest bad = get("/post");
        bad.setMethod("POST");
        bad.addHeader("Content-Type", "application/json");
        bad.setContent("{".getBytes(StandardCharsets.UTF_8));
        ResponseEntity<byte[]> error = echo.withBody(bad);
        assertThat(error.getStatusCode().value()).isEqualTo(400);
        assertThat(new String(error.getBody(), StandardCharsets.UTF_8)).contains("error parsing request body: ");
    }

    @ErrorGuessing
    @Test
    void discardedCountsBytesWithoutKeepingThem() {
        MockHttpServletRequest r = get("/upload");
        r.setMethod("POST");
        r.setContent(new byte[20_000]);
        assertThat(new String(echo.discarded(r).getBody(), StandardCharsets.UTF_8)).contains("\"bytes_received\": 20000");
    }
}
