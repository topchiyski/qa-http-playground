package io.qaplayground.server.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import io.qaplayground.server.support.EchoResponses;
import io.qaplayground.server.support.GoJson;
import io.qaplayground.server.support.HttpbinResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Compressed responses.
 *
 * <p>The server compresses whatever the {@code Accept-Encoding} says. So you can check that your client decompresses,
 * or, with decompression off, that it doesn't.
 */
@Tag(name = "Compression", description = "gzip and deflate encoded responses")
@RestController
public class CompressionController {

    private final EchoResponses echo;

    public CompressionController(EchoResponses echo) {
        this.echo = echo;
    }

    /**
     * A gzip-compressed JSON body with {@code "gzipped": true}.
     *
     * <p><b>HTTP concept:</b> {@code Content-Encoding: gzip}. {@code Content-Length} counts the <em>compressed</em>
     * bytes.
     */
    @Operation(summary = "gzip-encoded response")
    @RequestMapping("/gzip")
    public ResponseEntity<byte[]> gzip(HttpServletRequest request) {
        byte[] json = GoJson.pretty(body(request, "gzipped"));
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(buf)) {
            gz.write(json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return encoded("gzip", buf.toByteArray());
    }

    /**
     * A deflate-compressed JSON body with {@code "deflated": true}.
     *
     * <p>As in go-httpbin, and as the HTTP spec says, "deflate" means the zlib format, not raw deflate.
     *
     * <p><b>HTTP concept:</b> {@code Content-Encoding: deflate}. It has a confusing history, because some servers sent
     * raw deflate instead.
     */
    @Operation(summary = "deflate (zlib) encoded response")
    @RequestMapping("/deflate")
    public ResponseEntity<byte[]> deflate(HttpServletRequest request) {
        byte[] json = GoJson.pretty(body(request, "deflated"));
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (DeflaterOutputStream z = new DeflaterOutputStream(buf)) {
            z.write(json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return encoded("deflate", buf.toByteArray());
    }

    /**
     * Not implemented: answers 501, exactly like go-httpbin.
     *
     * <p><b>HTTP concept:</b> 501 Not Implemented. The server doesn't support this at all, for any client.
     */
    @Operation(summary = "Not implemented (501), as upstream")
    @RequestMapping("/brotli")
    public ResponseEntity<byte[]> brotli() {
        return HttpbinResponses.error(501, null);
    }

    /**
     * The echoed body plus its flag. go-httpbin leaves {@code url} empty for these two endpoints, and so do we.
     */
    private Map<String, Object> body(HttpServletRequest request, String flag) {
        Map<String, Object> body = echo.noBody(request);
        body.put("url", "");
        body.put(flag, true);
        return body;
    }

    /**
     * A JSON response with already-compressed bytes and the matching {@code Content-Encoding}.
     */
    private static ResponseEntity<byte[]> encoded(String encoding, byte[] bytes) {
        return ResponseEntity.ok()
                .header("Content-Encoding", encoding)
                .header("Content-Type", HttpbinResponses.JSON)
                .contentLength(bytes.length)
                .body(bytes);
    }
}
