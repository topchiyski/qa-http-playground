package io.qaplayground.server.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;

import io.qaplayground.server.support.EchoResponses;
import io.qaplayground.server.support.GoJson;
import io.qaplayground.server.support.GoStrconv;
import io.qaplayground.server.support.HttpbinResponses;
import io.qaplayground.server.support.ServeContent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP caching: validators ({@code ETag}, {@code Last-Modified}), conditional requests and {@code Cache-Control}.
 *
 * <p>Caching is how the web stays fast. These endpoints let you see each mechanism on its own.
 */
@Tag(name = "Caching", description = "Conditional requests, ETags and Cache-Control")
@RestController
public class CachingController {

    /**
     * Go's {@code time.RFC1123} layout. The server runs in UTC, so the zone prints as "UTC".
     */
    private static final DateTimeFormatter RFC1123_GO =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'UTC'", Locale.US);

    /**
     * The SHA-1 of the empty string, which go-httpbin's {@code sha1hash()} appends to its ETags.
     */
    private static final String SHA1_OF_EMPTY = "da39a3ee5e6b4b0d3255bfef95601890afd80709";

    private final EchoResponses echo;
    private final Clock clock;

    public CachingController(EchoResponses echo, Clock clock) {
        this.echo = echo;
        this.clock = clock;
    }

    /**
     * The {@code /get} response with fresh {@code Last-Modified} and {@code ETag} headers, or 304 Not Modified when the
     * request has {@code If-Modified-Since} or {@code If-None-Match} (any value).
     *
     * <p><b>HTTP concept:</b> revalidation. Send back the validators you received, and the server can skip the body.
     */
    @Operation(summary = "304 if a conditional header is present, else 200 with validators")
    @RequestMapping("/cache")
    public ResponseEntity<byte[]> cache(HttpServletRequest request) {
        if (request.getHeader("If-Modified-Since") != null || request.getHeader("If-None-Match") != null) {
            return ResponseEntity.status(304).build();
        }
        String lastModified = RFC1123_GO.format(ZonedDateTime.now(ZoneOffset.UTC));
        // go-httpbin computes this with h.Sum([]byte(input)), which *appends* SHA-1("") to the
        // input bytes rather than hashing them. We reproduce the exact value.
        String etag = HexFormat.of().formatHex(lastModified.getBytes(StandardCharsets.UTF_8)) + SHA1_OF_EMPTY;
        return ResponseEntity.ok()
                .header("Last-Modified", lastModified)
                .header("ETag", etag)
                .header("Content-Type", HttpbinResponses.JSON)
                .body(GoJson.pretty(echo.noBody(request)));
    }

    /**
     * The {@code /get} response with {@code Cache-Control: public, max-age=N}.
     *
     * <p><b>HTTP concept:</b> freshness. For N seconds, a cache may answer without asking the server at all.
     */
    @Operation(summary = "Response with Cache-Control: public, max-age=N")
    @RequestMapping("/cache/{seconds}")
    public ResponseEntity<byte[]> cacheControl(@PathVariable String seconds, HttpServletRequest request) {
        long n;
        try {
            n = GoStrconv.parseInt(seconds, 64);
        } catch (GoStrconv.NumberFormatError e) {
            return HttpbinResponses.error(400, "invalid seconds: " + e.getMessage());
        }
        return ResponseEntity.ok()
                .header("Cache-Control", "public, max-age=" + n)
                .header("Content-Type", HttpbinResponses.JSON)
                .body(GoJson.pretty(echo.noBody(request)));
    }

    /**
     * A resource whose ETag is {@code "<etag>"}.
     *
     * <p>{@code If-None-Match} with that tag gives 304. {@code If-Match} with another tag gives 412. Range requests
     * work
     * too.
     *
     * <p><b>HTTP concept:</b> strong and weak validators, and optimistic concurrency. Send {@code If-Match} with a
     * write,
     * and it only succeeds if nobody changed the resource in the meantime.
     */
    @Operation(summary = "Resource with a fixed ETag; honours If-None-Match / If-Match")
    @RequestMapping("/etag/{etag}")
    public void etag(@PathVariable String etag, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setHeader("ETag", "\"" + etag + "\"");
        byte[] body = GoJson.pretty(echo.noBody(request));
        ServeContent.serve(request, response, body.length, Instant.now(clock), HttpbinResponses.TEXT,
                (out, offset, length) -> out.write(body, (int) offset, (int) length));
    }
}
