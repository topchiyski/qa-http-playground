package io.qaplayground.server.web;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.qaplayground.server.config.PlaygroundProperties;
import io.qaplayground.server.support.ContentSafety;
import io.qaplayground.server.support.GoBase64;
import io.qaplayground.server.support.GoJson;
import io.qaplayground.server.support.GoStrconv;
import io.qaplayground.server.support.HeaderNames;
import io.qaplayground.server.support.HttpbinResponses;
import io.qaplayground.server.support.QueryString;
import io.qaplayground.server.support.RequestInfo;
import io.qaplayground.server.support.StaticAssets;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fixed documents in several formats, base64 helpers, link pages and custom response headers.
 */
@Tag(name = "Content", description = "JSON/XML/HTML samples, base64, links, custom response headers")
@RestController
public class ContentController {

    private final RequestInfo info;
    private final PlaygroundProperties properties;

    public ContentController(RequestInfo info, PlaygroundProperties properties) {
        this.info = info;
        this.properties = properties;
    }

    /**
     * A fixed JSON document: a slideshow.
     *
     * <p><b>HTTP concept:</b> assertions on nested data with JSON Schema and JSONPath or GPath.
     */
    @Operation(summary = "Sample JSON document")
    @RequestMapping("/json")
    public ResponseEntity<byte[]> json() {
        return HttpbinResponses.bytes(200, HttpbinResponses.JSON, StaticAssets.get("static-httpbin/sample.json"));
    }

    /**
     * A fixed XML document. Note that its {@code Content-Type} has no charset.
     *
     * <p><b>HTTP concept:</b> XML responses and XPath assertions.
     */
    @Operation(summary = "Sample XML document")
    @RequestMapping("/xml")
    public ResponseEntity<byte[]> xml() {
        return HttpbinResponses.bytes(200, "application/xml", StaticAssets.get("static-httpbin/sample.xml"));
    }

    /**
     * A fixed HTML page: an excerpt of Moby-Dick.
     *
     * <p><b>HTTP concept:</b> asserting on HTML content.
     */
    @Operation(summary = "Sample HTML page")
    @RequestMapping("/html")
    public ResponseEntity<byte[]> html() {
        return HttpbinResponses.html(StaticAssets.get("static-httpbin/moby.html"));
    }

    /**
     * A UTF-8 test page full of scripts and symbols.
     *
     * <p><b>HTTP concept:</b> character encodings. Does your tool decode the body with the charset from
     * {@code Content-Type}?
     */
    @Operation(summary = "UTF-8 encoding stress test page")
    @GetMapping("/encoding/utf8")
    public ResponseEntity<byte[]> utf8() {
        return HttpbinResponses.html(StaticAssets.get("static-httpbin/utf8.html"));
    }

    /**
     * Decodes base64, in the URL-safe or the standard alphabet.
     *
     * <p><b>HTTP concept:</b> path parameters and URL-safe encodings.
     */
    @Operation(summary = "Decode base64")
    @Parameter(name = "content-type", in = ParameterIn.QUERY, description = "Response Content-Type (default text/plain)")
    @RequestMapping("/base64/{data}")
    public ResponseEntity<byte[]> base64(@PathVariable String data, HttpServletRequest request) {
        return base64Transform("decode", data, request);
    }

    /**
     * {@code /base64/encode/{text}} or {@code /base64/decode/{base64}}.
     *
     * <p><b>HTTP concept:</b> base64 is an encoding, not encryption. URL-safe base64 uses {@code -_} instead of
     * {@code +/}.
     */
    @Operation(summary = "Encode or decode base64")
    @Parameter(name = "content-type", in = ParameterIn.QUERY, description = "Response Content-Type (default text/plain)")
    @RequestMapping("/base64/{operation}/{data}")
    public ResponseEntity<byte[]> base64Operation(@PathVariable String operation, @PathVariable String data,
                                                  HttpServletRequest request) {
        return base64Transform(operation, data, request);
    }

    /**
     * Encodes or decodes {@code data}, after checking the operation and the input size.
     */
    private ResponseEntity<byte[]> base64Transform(String operation, String data, HttpServletRequest request) {
        if (data.length() > properties.maxBodyBytes()) {
            return HttpbinResponses.error(400, "input data exceeds max length of " + properties.maxBodyBytes());
        }
        byte[] result;
        switch (operation) {
            case "encode" -> result = Base64.getUrlEncoder().encode(data.getBytes(StandardCharsets.UTF_8));
            case "decode" -> {
                try {
                    result = GoBase64.decode(data, GoBase64.URL);
                } catch (GoBase64.CorruptInputError urlSafeFailed) {
                    try {
                        result = GoBase64.decode(data, GoBase64.STD);
                    } catch (GoBase64.CorruptInputError e) {
                        return HttpbinResponses.error(400, "base64 decode failed: " + e.getMessage());
                    }
                }
            }
            default -> {
                return HttpbinResponses.error(400, "invalid operation: " + operation);
            }
        }
        String contentType = QueryString.first(info.args(request), "content-type");
        if (contentType.isEmpty()) {
            contentType = HttpbinResponses.TEXT;
        }
        if (!properties.unsafeAllowDangerousResponses() && ContentSafety.isDangerous(contentType)) {
            result = ContentSafety.escapeHtml(new String(result, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
        }
        return HttpbinResponses.bytes(200, contentType, result);
    }

    /**
     * Redirects to {@code /links/{n}/0}, the first page of the set.
     *
     * <p><b>HTTP concept:</b> crawling: follow every link on a page.
     */
    @Operation(summary = "Redirect to the first page of n links")
    @RequestMapping("/links/{n}")
    public ResponseEntity<byte[]> links(@PathVariable String n, HttpServletRequest request) {
        ResponseEntity<byte[]> invalid = validateLinkCount(n);
        if (invalid != null) {
            return invalid;
        }
        String query = request.getQueryString();
        String location = request.getRequestURI() + "/0" + (query == null ? "" : "?" + query);
        return ResponseEntity.status(302).header("Location", location).build();
    }

    /**
     * An HTML page with links to its {@code n} sibling pages. The current page is plain text, not a link.
     *
     * <p><b>HTTP concept:</b> link extraction, and crawl depth in load tests.
     */
    @Operation(summary = "HTML page with n links")
    @RequestMapping("/links/{n}/{offset}")
    public ResponseEntity<byte[]> linksPage(@PathVariable String n, @PathVariable String offset) {
        ResponseEntity<byte[]> invalid = validateLinkCount(n);
        if (invalid != null) {
            return invalid;
        }
        long current;
        try {
            current = GoStrconv.atoi(offset);
        } catch (GoStrconv.NumberFormatError e) {
            return HttpbinResponses.error(400, "invalid offset: " + e.getMessage());
        }
        long count = GoStrconv.atoi(n);
        StringBuilder html = new StringBuilder("<html><head><title>Links</title></head><body>");
        for (long i = 0; i < count; i++) {
            if (i == current) {
                html.append(i).append(' ');
            } else {
                html.append("<a href=\"/links/").append(count).append('/').append(i).append("\">").append(i).append("</a> ");
            }
        }
        html.append("</body></html>");
        return HttpbinResponses.html(html.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * A 400 response when {@code raw} isn't a link count between 0 and {@code playground.max-links}; null when it is.
     */
    private ResponseEntity<byte[]> validateLinkCount(String raw) {
        long n;
        try {
            n = GoStrconv.atoi(raw);
        } catch (GoStrconv.NumberFormatError e) {
            return HttpbinResponses.error(400, "invalid link count: " + e.getMessage());
        }
        if (n < 0 || n > properties.maxLinks()) {
            return HttpbinResponses.error(400,
                    "invalid link count: " + n + " must be in range [0, " + properties.maxLinks() + "]");
        }
        return null;
    }

    /**
     * Headers a client may not set. Framing the message is the server's job, and letting a client set these breaks the
     * connection.
     */
    private static final java.util.Set<String> FRAMING_HEADERS = java.util.Set.of("content-length", "transfer-encoding");

    /**
     * Turns every query parameter into a response header, and lists them as JSON in the body.
     *
     * <p>Set {@code Content-Type} the same way. With an HTML-like {@code Content-Type}, the JSON is HTML-escaped to
     * prevent reflected XSS.
     *
     * <p><b>HTTP concept:</b> asserting on response headers, and how clients treat duplicate or unusual ones.
     */
    @Operation(summary = "Set response headers from query params")
    @RequestMapping("/response-headers")
    public ResponseEntity<byte[]> responseHeaders(HttpServletRequest request) {
        Map<String, List<String>> args = new LinkedHashMap<>(info.args(request));
        if (QueryString.first(args, "Content-Type").isEmpty()) {
            args.put("Content-Type", new ArrayList<>(List.of(HttpbinResponses.JSON)));
        }
        String contentType = QueryString.first(args, "Content-Type");

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        // Headers are sent unescaped; Go canonicalises their names when adding them.
        // Framing headers are echoed in the body but never emitted: Tomcat keeps an application's
        // "Transfer-Encoding: chunked" next to its own Content-Length, which corrupts keep-alive
        // connections (found in exploratory session ES-01; docs/COMPATIBILITY.md).
        args.forEach((k, vs) -> {
            if (!FRAMING_HEADERS.contains(k.toLowerCase(java.util.Locale.ROOT))) {
                vs.forEach(v -> builder.header(HeaderNames.canonical(k), v));
            }
        });

        Map<String, List<String>> body = new TreeMap<>(args);
        if (!properties.unsafeAllowDangerousResponses() && ContentSafety.isDangerous(contentType)) {
            Map<String, List<String>> escaped = new TreeMap<>();
            args.forEach((k, vs) -> vs.forEach(v -> escaped
                    .computeIfAbsent(ContentSafety.escapeHtml(k), x -> new ArrayList<>())
                    .add(ContentSafety.escapeHtml(v))));
            body = escaped;
        }
        return builder.body(GoJson.pretty(body));
    }
}
