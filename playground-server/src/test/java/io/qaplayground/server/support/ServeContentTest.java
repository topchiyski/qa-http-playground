package io.qaplayground.server.support;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import io.qaplayground.testing.technique.DecisionTable;
import io.qaplayground.testing.technique.EquivalencePartitioning;
import io.qaplayground.testing.technique.ErrorGuessing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The port of Go's {@code http.ServeContent}: preconditions (decision tables, TEST-DESIGN.md 3.2) and range serving
 * (risk R6).
 *
 * <p>It uses Spring's servlet test doubles: no server, no Spring context, so each case runs in microseconds.
 */
class ServeContentTest {

    /**
     * The resource every test serves: 26 bytes, so every byte shows its own position.
     */
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz";
    /**
     * The resource's current entity tag.
     */
    private static final String ETAG = "\"v1\"";
    /**
     * The resource's last-modified time.
     */
    private static final Instant MODIFIED = Instant.parse("2024-01-02T03:04:05Z");
    /**
     * {@link #MODIFIED} as an HTTP date.
     */
    private static final String MODIFIED_HTTP = "Tue, 02 Jan 2024 03:04:05 GMT";
    /**
     * A date before the resource last changed.
     */
    private static final String EARLIER = "Mon, 01 Jan 2024 00:00:00 GMT";
    /**
     * A date after the resource last changed.
     */
    private static final String LATER = "Wed, 03 Jan 2024 00:00:00 GMT";

    /**
     * Serves the alphabet for this request, with the given modification time.
     */
    private static MockHttpServletResponse serve(MockHttpServletRequest request, Instant modtime) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setHeader("ETag", ETAG);
        byte[] body = ALPHABET.getBytes(StandardCharsets.US_ASCII);
        ServeContent.serve(request, response, body.length, modtime, HttpbinResponses.TEXT,
                (out, offset, length) -> out.write(body, (int) offset, (int) length));
        return response;
    }

    /**
     * Serves the alphabet for a request with this method and these headers, given as name/value pairs.
     */
    private static MockHttpServletResponse serve(String method, String... headerPairs) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/etag/v1");
        for (int i = 0; i < headerPairs.length; i += 2) {
            request.addHeader(headerPairs[i], headerPairs[i + 1]);
        }
        return serve(request, MODIFIED);
    }

    // --- preconditions: decision table (If-Match evaluated before If-None-Match) ---------------

    @DecisionTable
    @ParameterizedTest(name = "R{index}: {0} If-Match={1} If-None-Match={2} -> {3}")
    @CsvSource(delimiter = '|', value = {
            "GET  | -            | -           | 200",
            "GET  | -            | \"v1\"      | 304",
            "GET  | -            | \"other\"   | 200",
            "GET  | \"v1\"       | -           | 200",
            "GET  | \"other\"    | -           | 412",
            "GET  | \"other\"    | \"v1\"      | 412",   // If-Match decides first
            "GET  | *            | -           | 200",
            "GET  | -            | *           | 304",
            "GET  | -            | W/\"v1\"    | 304",   // If-None-Match: weak comparison
            "GET  | W/\"v1\"     | -           | 412",   // If-Match: strong comparison, weak tag never matches
            "GET  | \"a\", \"v1\"| -           | 200",   // list
            "GET  | -            | \"a\",\"v1\"| 304",
            "GET  | garbage      | -           | 412",   // unparseable If-Match fails
            "GET  | -            | garbage     | 200",   // unparseable If-None-Match is ignored
            "HEAD | -            | \"v1\"      | 304",
            "PUT  | -            | \"v1\"      | 412",   // unsafe method: If-None-Match match fails
            "POST | \"v1\"       | -           | 200"})
    void entityTagPreconditions(String method, String ifMatch, String ifNoneMatch, int status) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/etag/v1");
        if (!ifMatch.equals("-")) {
            request.addHeader("If-Match", ifMatch);
        }
        if (!ifNoneMatch.equals("-")) {
            request.addHeader("If-None-Match", ifNoneMatch);
        }
        assertThat(serve(request, MODIFIED).getStatus()).isEqualTo(status);
    }

    @DecisionTable
    @ParameterizedTest(name = "{0} If-Unmodified-Since={1} If-Modified-Since={2} -> {3}")
    @CsvSource(delimiter = '|', value = {
            "GET  | " + EARLIER + " | -                   | 412",
            "GET  | " + MODIFIED_HTTP + " | -             | 200",   // equal is not modified since
            "GET  | " + LATER + "   | -                   | 200",
            "GET  | not a date      | -                   | 200",   // unparseable: ignored
            "GET  | -               | " + MODIFIED_HTTP + " | 304",
            "GET  | -               | " + LATER + "       | 304",
            "GET  | -               | " + EARLIER + "     | 200",
            "GET  | -               | not a date          | 200",
            "POST | -               | " + LATER + "       | 200"})  // If-Modified-Since only for GET/HEAD
    void datePreconditions(String method, String ius, String ims, int status) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/x");
        if (!ius.equals("-")) {
            request.addHeader("If-Unmodified-Since", ius);
        }
        if (!ims.equals("-")) {
            request.addHeader("If-Modified-Since", ims);
        }
        assertThat(serve(request, MODIFIED).getStatus()).isEqualTo(status);
    }

    /**
     * Without a modification time, date preconditions can't be evaluated, so they are ignored.
     */
    @Test
    void dateConditionsAreIgnoredWithoutAModificationTime() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.addHeader("If-Unmodified-Since", EARLIER);
        request.addHeader("If-Modified-Since", LATER);
        assertThat(serve(request, null).getStatus()).isEqualTo(200);
    }

    /**
     * A 304 drops the headers that describe a body, as Go does.
     */
    @Test
    void notModifiedDropsBodyHeaders() throws Exception {
        MockHttpServletResponse r = serve("GET", "If-None-Match", ETAG);
        assertThat(r.getStatus()).isEqualTo(304);
        assertThat(r.getContentAsByteArray()).isEmpty();
        assertThat(r.getHeader("Last-Modified")).as("dropped when an ETag is present").isNull();
        assertThat(r.getHeader("ETag")).isEqualTo(ETAG);
    }

    // --- If-Range -----------------------------------------------------------------------------

    @DecisionTable
    @ParameterizedTest(name = "If-Range {0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "\"v1\"            | 206",
            "\"v0\"            | 200",
            "W/\"v1\"          | 200",   // weak tags never match If-Range
            MODIFIED_HTTP + "  | 206",
            EARLIER + "        | 200",
            "garbage           | 200"})
    void ifRangeMakesTheRangeConditional(String ifRange, int status) throws Exception {
        MockHttpServletResponse r = serve("GET", "Range", "bytes=0-1", "If-Range", ifRange);
        assertThat(r.getStatus()).isEqualTo(status);
        assertThat(r.getContentAsString()).isEqualTo(status == 206 ? "ab" : ALPHABET);
    }

    /**
     * {@code If-Range} with a date can't be checked without a modification time, so the full resource is sent.
     */
    @Test
    void ifRangeWithADateButNoModificationTimeServesEverything() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.addHeader("Range", "bytes=0-1");
        request.addHeader("If-Range", MODIFIED_HTTP);
        assertThat(serve(request, null).getStatus()).isEqualTo(200);
    }

    /**
     * {@code If-Range} only applies to GET and HEAD.
     */
    @Test
    void ifRangeIsIgnoredForUnsafeMethods() throws Exception {
        assertThat(serve("POST", "Range", "bytes=0-1", "If-Range", "\"v0\"").getStatus()).isEqualTo(206);
    }

    // --- ranges -------------------------------------------------------------------------------

    @EquivalencePartitioning
    @Test
    void fullResponse() throws Exception {
        MockHttpServletResponse r = serve("GET");
        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(r.getContentAsString()).isEqualTo(ALPHABET);
        assertThat(r.getHeader("Accept-Ranges")).isEqualTo("bytes");
        assertThat(r.getContentLengthLong()).isEqualTo(26);
        assertThat(r.getHeader("Last-Modified")).isEqualTo(MODIFIED_HTTP);
    }

    @EquivalencePartitioning
    @Test
    void singleRange() throws Exception {
        MockHttpServletResponse r = serve("GET", "Range", "bytes=-3");
        assertThat(r.getStatus()).isEqualTo(206);
        assertThat(r.getHeader("Content-Range")).isEqualTo("bytes 23-25/26");
        assertThat(r.getContentAsString()).isEqualTo("xyz");
        assertThat(r.getContentLengthLong()).isEqualTo(3);
    }

    @EquivalencePartitioning
    @Test
    void multipleRangesUseMultipartByteranges() throws Exception {
        MockHttpServletResponse r = serve("GET", "Range", "bytes=0-1,24-");
        assertThat(r.getStatus()).isEqualTo(206);
        String contentType = r.getContentType();
        assertThat(contentType).startsWith("multipart/byteranges; boundary=");
        String boundary = contentType.substring(contentType.indexOf('=') + 1);
        assertThat(boundary).matches("[0-9a-f]{60}");
        assertThat(r.getContentAsString()).isEqualTo(
                "--" + boundary + "\r\nContent-Range: bytes 0-1/26\r\nContent-Type: text/plain; charset=utf-8\r\n\r\nab"
                        + "\r\n--" + boundary + "\r\nContent-Range: bytes 24-25/26\r\nContent-Type: text/plain; charset=utf-8\r\n\r\nyz"
                        + "\r\n--" + boundary + "--\r\n");
        assertThat(r.getContentLengthLong()).isEqualTo(r.getContentAsByteArray().length);
    }

    @Test
    void unsatisfiableRangeIs416WithContentRange() throws Exception {
        MockHttpServletResponse r = serve("GET", "Range", "bytes=30-");
        assertThat(r.getStatus()).isEqualTo(416);
        assertThat(r.getHeader("Content-Range")).isEqualTo("bytes */26");
        assertThat(r.getContentAsString()).isEqualTo("invalid range: failed to overlap\n");
        assertThat(r.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(r.getHeader("ETag")).as("caching headers are dropped on errors").isNull();
    }

    @ErrorGuessing
    @Test
    void malformedRangeIs416WithoutContentRange() throws Exception {
        MockHttpServletResponse r = serve("GET", "Range", "bytes=5-1");
        assertThat(r.getStatus()).isEqualTo(416);
        assertThat(r.getHeader("Content-Range")).isNull();
        assertThat(r.getContentAsString()).isEqualTo("invalid range\n");
    }

    /**
     * Error guessing, resource amplification: ranges that together ask for more bytes than exist are ignored, and the
     * full resource is sent once, as in Go.
     */
    @ErrorGuessing
    @Test
    void overlappingRangesLargerThanTheResourceAreIgnored() throws Exception {
        // Guessing at a resource-amplification attack: 0-20 twice asks for more bytes than exist.
        MockHttpServletResponse r = serve("GET", "Range", "bytes=0-20,0-20");
        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(r.getContentAsString()).isEqualTo(ALPHABET);
    }

    /**
     * An empty resource can't satisfy any range, so the range is ignored rather than answered with 416.
     */
    @Test
    void emptyResourceIgnoresUnsatisfiableRange() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/range/0");
        request.addHeader("Range", "bytes=0-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServeContent.serve(request, response, 0, null, HttpbinResponses.TEXT, (out, o, l) -> { });
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentLengthLong()).isZero();
    }

    @Test
    void headSendsHeadersOnly() throws Exception {
        MockHttpServletResponse r = serve("HEAD", "Range", "bytes=0-4");
        assertThat(r.getStatus()).isEqualTo(206);
        assertThat(r.getContentLengthLong()).isEqualTo(5);
        assertThat(r.getContentAsByteArray()).isEmpty();
    }

    // --- dates and entity tags ----------------------------------------------------------------

    @EquivalencePartitioning
    @ParameterizedTest(name = "\"{0}\" parses")
    @CsvSource(delimiter = '|', value = {
            "Tue, 02 Jan 2024 03:04:05 GMT",    // RFC 1123
            "Tuesday, 02-Jan-24 03:04:05 GMT",  // RFC 850
            "Tue Jan  2 03:04:05 2024"})        // ANSI C asctime
    void parsesTheThreeHttpDateFormats(String date) {
        assertThat(ServeContent.parseHttpDate(date)).isEqualTo(MODIFIED);
    }

    /**
     * Regression: one-digit days in asctime are padded with a space and were not parsed before.
     */
    @ErrorGuessing
    @Test
    void asctimeWithTwoDigitDay() {
        assertThat(ServeContent.parseHttpDate("Fri Jan 12 03:04:05 2024")).isEqualTo(Instant.parse("2024-01-12T03:04:05Z"));
    }

    @ErrorGuessing
    @ParameterizedTest(name = "\"{0}\" does not parse")
    @CsvSource(delimiter = '|', value = {"2024-01-02T03:04:05Z", "Tue, 02 Jan 2024 03:04:05 CET", "yesterday", "''"})
    void rejectsOtherDateFormats(String date) {
        assertThat(ServeContent.parseHttpDate(date)).isNull();
    }

    @Test
    void formatsHttpDates() {
        assertThat(ServeContent.formatHttpDate(MODIFIED)).isEqualTo(MODIFIED_HTTP);
    }

    @EquivalencePartitioning
    @ParameterizedTest(name = "scan \"{0}\" -> \"{1}\" + \"{2}\"")
    @CsvSource(delimiter = '|', value = {
            "\"abc\"|\"abc\"|''",
            "W/\"abc\", \"d\"|W/\"abc\"|, \"d\"",
            "  \"x\"|\"x\"|''",
            "\"\"|\"\"|''"})
    void scansEntityTags(String input, String tag, String rest) {
        assertThat(ServeContent.scanETag(input)).containsExactly(tag, rest);
    }

    @ErrorGuessing
    @ParameterizedTest(name = "\"{0}\" is not an entity tag")
    @CsvSource(delimiter = '|', value = {"abc", "\"abc", "\"a b\"", "W/abc", "\"", "''"})
    void rejectsInvalidEntityTags(String input) {
        assertThat(ServeContent.scanETag(input)).isNull();
    }
}
