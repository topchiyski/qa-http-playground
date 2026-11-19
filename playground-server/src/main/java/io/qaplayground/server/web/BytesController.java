package io.qaplayground.server.web;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.qaplayground.server.config.PlaygroundProperties;
import io.qaplayground.server.support.GoDuration;
import io.qaplayground.server.support.GoRandom;
import io.qaplayground.server.support.GoStrconv;
import io.qaplayground.server.support.HttpbinResponses;
import io.qaplayground.server.support.QueryString;
import io.qaplayground.server.support.ServeContent;
import io.qaplayground.server.support.StatusCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import static io.qaplayground.server.config.RawResponseEntityHandler.write;

/**
 * Binary payloads: random bytes (seedable), streamed bytes, and range requests.
 */
@Tag(name = "Bytes & ranges", description = "Random bytes, streamed bytes and HTTP range requests")
@RestController
public class BytesController {

    private final PlaygroundProperties properties;

    public BytesController(PlaygroundProperties properties) {
        this.properties = properties;
    }

    /**
     * {@code n} random bytes, with a {@code Content-Length}.
     *
     * <p>The same {@code seed} always gives the same bytes, and they are the bytes go-httpbin gives.
     *
     * <p><b>HTTP concept:</b> binary bodies and download size. Assert on a checksum, not on the bytes one by one.
     */
    @Operation(summary = "n random bytes (seedable)")
    @Parameter(name = "seed", in = ParameterIn.QUERY, description = "Integer seed for reproducible bytes")
    @RequestMapping("/bytes/{n}")
    public ResponseEntity<byte[]> bytes(@PathVariable String n, HttpServletRequest request) {
        Parsed p = parse(n, request);
        if (p.error() != null) {
            return p.error();
        }
        if (p.count() == 0) {
            return ResponseEntity.ok().contentLength(0).build();
        }
        byte[] body = new byte[p.count()];
        for (int i = 0; i < body.length; i++) {
            body[i] = (byte) p.rng().intn(256);
        }
        return ResponseEntity.ok().header("Content-Type", HttpbinResponses.BINARY).contentLength(body.length).body(body);
    }

    /**
     * {@code n} random bytes, sent in chunks of {@code chunk_size} (default 10240) with chunked transfer encoding and
     * no
     * {@code Content-Length}.
     *
     * <p><b>HTTP concept:</b> {@code Transfer-Encoding: chunked}. The client can't know the size in advance. Watch what
     * your tool's progress indicator does.
     */
    @Operation(summary = "n random bytes, streamed in chunks")
    @Parameter(name = "seed", in = ParameterIn.QUERY, description = "Integer seed for reproducible bytes")
    @Parameter(name = "chunk_size", in = ParameterIn.QUERY, description = "Bytes per chunk (default 10240)")
    @RequestMapping("/stream-bytes/{n}")
    public ResponseEntity<StreamingResponseBody> streamBytes(@PathVariable String n, HttpServletRequest request) {
        Parsed p = parse(n, request);
        if (p.error() != null) {
            return HttpbinResponses.asStream(p.error());
        }
        if (p.count() == 0) {
            return HttpbinResponses.asStream(ResponseEntity.ok().body(new byte[0]));
        }
        long chunkSize = 10 * 1024;
        String rawChunk = QueryString.first(p.args(), "chunk_size");
        if (!rawChunk.isEmpty()) {
            try {
                chunkSize = GoStrconv.atoi(rawChunk);
            } catch (GoStrconv.NumberFormatError e) {
                return HttpbinResponses.asStream(HttpbinResponses.error(400, "invalid chunk_size: " + e.getMessage()));
            }
        }
        int count = p.count();
        GoRandom rng = p.rng();
        // Like the reference, a chunk_size <= 0 means "everything in one chunk".
        int chunk = chunkSize <= 0 || chunkSize > count ? count : (int) chunkSize;
        StreamingResponseBody body = out -> {
            byte[] buf = new byte[chunk];
            int filled = 0;
            for (int i = 0; i < count; i++) {
                buf[filled++] = (byte) rng.intn(256);
                if (filled == chunk) {
                    out.write(buf, 0, filled);
                    out.flush(); // each flush becomes one HTTP chunk
                    filled = 0;
                }
            }
            if (filled > 0) {
                out.write(buf, 0, filled);
                out.flush();
            }
        };
        return ResponseEntity.ok().header("Content-Type", HttpbinResponses.BINARY).body(body);
    }

    /**
     * The checked input for {@code /bytes} and {@code /stream-bytes}: either an error, or a count and a generator.
     */
    private record Parsed(ResponseEntity<byte[]> error, int count, GoRandom rng, Map<String, List<String>> args) {
        /**
         * An input that failed a check, carrying the 400 response to send.
         */
        static Parsed failed(String detail) {
            return new Parsed(HttpbinResponses.error(400, detail), 0, null, Map.of());
        }
    }

    /**
     * Runs the same checks, in the same order, as go-httpbin's {@code handleBytes}.
     */
    private Parsed parse(String rawN, HttpServletRequest request) {
        Map<String, List<String>> args = QueryString.parse(request.getQueryString());
        long numBytes;
        try {
            numBytes = GoStrconv.atoi(rawN);
        } catch (GoStrconv.NumberFormatError e) {
            return Parsed.failed("invalid byte count: " + e.getMessage());
        }
        GoRandom rng;
        try {
            rng = StatusCodes.seededRandom(QueryString.first(args, "seed"));
        } catch (GoStrconv.NumberFormatError e) {
            return Parsed.failed("invalid seed: " + e.getMessage());
        }
        if (numBytes < 0) {
            return Parsed.failed("invalid byte count: " + numBytes + " must be greater than 0");
        }
        long max = properties.maxBodyBytes();
        if (numBytes > max) {
            return Parsed.failed("invalid byte count: " + numBytes + " not in range [1, " + max + "]");
        }
        return new Parsed(null, (int) numBytes, rng, args);
    }

    /**
     * {@code n} bytes of the alphabet ({@code abc...zabc...}), with full {@code Range} support.
     *
     * <p>Single ranges get 206. Several ranges get {@code multipart/byteranges}. Suffix ranges ({@code bytes=-10}) and
     * {@code If-Range} work, and ranges past the end get 416. {@code duration} spreads the transfer over time.
     *
     * <p><b>HTTP concept:</b> partial content, the mechanism behind resumable downloads.
     */
    @Operation(summary = "n bytes with HTTP Range support")
    @Parameter(name = "duration", in = ParameterIn.QUERY, description = "Spread the response over this long, e.g. `2s`")
    @RequestMapping("/range/{n}")
    public void range(@PathVariable String n, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        long numBytes;
        try {
            numBytes = GoStrconv.parseInt(n, 64);
        } catch (GoStrconv.NumberFormatError e) {
            write(response, HttpbinResponses.error(400, "invalid count: " + e.getMessage()));
            return;
        }
        Duration duration = Duration.ZERO;
        String rawDuration = QueryString.first(QueryString.parse(request.getQueryString()), "duration");
        if (!rawDuration.isEmpty()) {
            try {
                duration = GoDuration.parseBounded(rawDuration, Duration.ZERO, properties.maxDuration());
            } catch (GoDuration.DurationError e) {
                write(response, HttpbinResponses.error(400, "invalid duration: " + e.getMessage()));
                return;
            }
        }
        response.addHeader("ETag", "range" + numBytes);
        response.addHeader("Accept-Ranges", "bytes");
        long max = properties.maxBodyBytes();
        if (numBytes <= 0 || numBytes > max) {
            write(response, HttpbinResponses.error(400, "invalid count: " + numBytes + " not in range [1, " + max + "]"));
            return;
        }
        long pausePerByteNanos = duration.toNanos() / numBytes;
        ServeContent.serve(request, response, numBytes, null, HttpbinResponses.TEXT,
                (out, offset, length) -> writeAlphabet(out, offset, length, pausePerByteNanos));
    }

    /**
     * Writes the byte at offset i as {@code 'a' + i % 26}, in 32 KiB pieces, pausing between bytes like the reference.
     */
    private static void writeAlphabet(OutputStream out, long offset, long length, long pausePerByteNanos)
            throws IOException {
        byte[] buf = new byte[(int) Math.min(32 * 1024, length)];
        long written = 0;
        while (written < length) {
            int n = (int) Math.min(buf.length, length - written);
            for (int i = 0; i < n; i++) {
                buf[i] = (byte) ('a' + ((offset + written + i) % 26));
            }
            if (pausePerByteNanos > 0) {
                try {
                    Thread.sleep(Duration.ofNanos(pausePerByteNanos * n));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            out.write(buf, 0, n);
            written += n;
        }
    }
}
