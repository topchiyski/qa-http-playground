package io.qaplayground.server.support;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import io.qaplayground.server.support.RequestBodyParser.Body;
import io.qaplayground.server.support.RequestBodyParser.BodyParseException;
import io.qaplayground.testing.technique.EquivalencePartitioning;
import io.qaplayground.testing.technique.ErrorGuessing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * How {@code /post}, {@code /anything} and friends turn a body into {@code data}, {@code form}, {@code files} and
 * {@code json}.
 *
 * <p>The equivalence partitions are the {@code Content-Type} classes: each class is handled differently, so each
 * needs its own test.
 */
class RequestBodyParserTest {

    /**
     * A request with this content type and body, without a server.
     */
    private static MockHttpServletRequest request(String contentType, String body) {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/post");
        if (contentType != null) {
            r.addHeader("Content-Type", contentType);
        }
        r.setContent(body.getBytes(StandardCharsets.UTF_8));
        return r;
    }

    /**
     * Parses a body as the echo endpoints would.
     */
    private static Body parse(String contentType, String body) throws BodyParseException {
        return RequestBodyParser.parse(request(contentType, body));
    }

    @EquivalencePartitioning
    @ParameterizedTest(name = "{0}: body goes to data")
    @CsvSource({"text/plain", "text/html", "text/plain; charset=latin1"})
    void textTypesFillData(String contentType) throws Exception {
        Body b = parse(contentType, "héllo");
        assertThat(b.data()).isEqualTo("héllo");
        assertThat(b.form()).isEmpty();
        assertThat(b.json()).isNull();
    }

    @EquivalencePartitioning
    @Test
    void formFieldsAreParsedAndSorted() throws Exception {
        Body b = parse("application/x-www-form-urlencoded", "z=1&a=2&a=3&sp=a+b");
        assertThat(b.form()).containsExactly(
                Map.entry("a", List.of("2", "3")), Map.entry("sp", List.of("a b")), Map.entry("z", List.of("1")));
        assertThat(b.data()).isEqualTo("z=1&a=2&a=3&sp=a+b");
    }

    @ErrorGuessing
    @Test
    void malformedFormBodiesAreRejectedLikeParseForm() {
        assertThatThrownBy(() -> parse("application/x-www-form-urlencoded", "a=%zz"))
                .isInstanceOf(BodyParseException.class).hasMessage("invalid URL escape \"%zz\"");
    }

    /**
     * A bad query string fails a form request too, because Go's {@code ParseForm} reads both.
     */
    @ErrorGuessing
    @Test
    void formParsingAlsoValidatesTheQueryString() {
        MockHttpServletRequest r = request("application/x-www-form-urlencoded", "a=1");
        r.setQueryString("b=%zz");
        assertThatThrownBy(() -> RequestBodyParser.parse(r)).hasMessage("invalid URL escape \"%zz\"");
    }

    /**
     * JSON comes back as Go decodes into {@code any}: keys sorted, every number a float64.
     */
    @EquivalencePartitioning
    @Test
    void jsonIsDecodedLikeGoAny() throws Exception {
        Body b = parse("application/json", "{\"b\": [1, 2.5, true, null], \"a\": {\"y\": \"x\"}}");
        assertThat(b.json()).isEqualTo(Map.of("a", Map.of("y", "x"), "b", java.util.Arrays.asList(1.0, 2.5, true, null)));
        assertThat(new java.util.ArrayList<Object>(((Map<?, ?>) b.json()).keySet())).containsExactly("a", "b"); // sorted, as Go maps print
    }

    @ErrorGuessing
    @ParameterizedTest(name = "invalid JSON \"{0}\" is a parse error")
    @CsvSource(delimiter = '|', value = {"{", "{\"a\":}", "[1,]", "nul"})
    void invalidJsonIsRejected(String body) {
        assertThatThrownBy(() -> parse("application/json", body)).isInstanceOf(BodyParseException.class);
    }

    @EquivalencePartitioning
    @ParameterizedTest(name = "\"{0}\" -> data URL")
    @CsvSource(delimiter = '|', value = {
            "application/octet-stream|data:application/octet-stream;base64,",
            "image/png|data:image/png;base64,",
            "Application/JSON|data:Application/JSON;base64,",   // compared case-sensitively, like go-httpbin
            "application/json5|data:application/json5;base64,"})
    void otherTypesBecomeDataUrls(String contentType, String prefix) throws Exception {
        Body b = parse(contentType, "ÿ?>");
        assertThat(b.data()).startsWith(prefix);
        assertThat(b.json()).isNull();
    }

    /**
     * A body without {@code Content-Type} is treated as {@code application/octet-stream}.
     */
    @Test
    void missingContentTypeIsOctetStream() throws Exception {
        assertThat(parse(null, "x").data()).isEqualTo("data:application/octet-stream;base64,eA==");
    }

    @Test
    void dataUrlUsesTheUrlSafeAlphabetWithPadding() {
        assertThat(RequestBodyParser.dataUrl(new byte[]{(byte) 0xfb, (byte) 0xff}, "x/y")).isEqualTo("data:x/y;base64,-_8=");
    }

    @Test
    void emptyBodyIsEmptyWhateverTheType() throws Exception {
        Body b = parse("application/json", "");
        assertThat(b.data()).isEmpty();
        assertThat(b.json()).isNull();
    }

    // --- multipart ----------------------------------------------------------------------------

    /**
     * The multipart boundary every body in this class uses.
     */
    private static final String BOUNDARY = "XyZ";

    /**
     * One multipart part with this {@code Content-Disposition} and content.
     */
    private static String part(String disposition, String content) {
        return "--" + BOUNDARY + "\r\nContent-Disposition: " + disposition + "\r\n\r\n" + content + "\r\n";
    }

    @EquivalencePartitioning
    @Test
    void multipartSeparatesFieldsAndFiles() throws Exception {
        String body = part("form-data; name=\"a\"", "1")
                + part("form-data; name=\"a\"", "2")
                + part("form-data; name=\"f\"; filename=\"x.txt\"", "file\r\ncontent")
                + part("attachment; name=\"ignored\"", "x")
                + part("form-data; filename=\"nameless\"", "x")
                + "--" + BOUNDARY + "--\r\n";
        Body b = parse("multipart/form-data; boundary=" + BOUNDARY, body);
        assertThat(b.form()).containsExactly(Map.entry("a", List.of("1", "2")));
        assertThat(b.files()).containsExactly(Map.entry("f", List.of("file\r\ncontent")));
    }

    /**
     * Multipart bodies with LF instead of CRLF still parse, as they do in Go.
     */
    @ErrorGuessing
    @Test
    void multipartWithBareLineFeeds() throws Exception {
        String body = "--" + BOUNDARY + "\nContent-Disposition: form-data; name=\"a\"\n\nv\n--" + BOUNDARY + "--\n";
        assertThat(parse("multipart/form-data; boundary=" + BOUNDARY, body).form()).containsEntry("a", List.of("v"));
    }

    @ErrorGuessing
    @Test
    void quotedBoundaryAndEscapedQuotesInNames() throws Exception {
        String body = part("form-data; name=\"say \\\"hi\\\"\"", "v") + "--" + BOUNDARY + "--";
        assertThat(parse("multipart/form-data; boundary=\"" + BOUNDARY + "\"", body).form())
                .containsEntry("say \"hi\"", List.of("v"));
    }

    /**
     * Each guessed fault in a multipart body fails with Go's message.
     */
    @ErrorGuessing
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "no boundary parameter|multipart/form-data|x|no multipart boundary param in Content-Type",
            "boundary never appears|multipart/form-data; boundary=XyZ|just text|multipart: NextPart: EOF"})
    void malformedMultipart(String guess, String contentType, String body, String message) {
        assertThatThrownBy(() -> parse(contentType, body)).as(guess).hasMessage(message);
    }

    @ErrorGuessing
    @Test
    void multipartWithoutClosingDelimiter() {
        assertThatThrownBy(() -> parse("multipart/form-data; boundary=XyZ", "--XyZ\r\nA: b\r\n\r\nv"))
                .hasMessage("multipart: NextPart: EOF");
    }

    @Test
    void headerParameterParsing() {
        assertThat(RequestBodyParser.parameter("form-data; name=\"a\"; filename=b.txt", "filename")).isEqualTo("b.txt");
        assertThat(RequestBodyParser.parameter("form-data; NAME=\"a\"", "name")).isEqualTo("a");
        assertThat(RequestBodyParser.parameter("form-data", "name")).isNull();
        assertThat(RequestBodyParser.parameter(null, "name")).isNull();
    }
}
