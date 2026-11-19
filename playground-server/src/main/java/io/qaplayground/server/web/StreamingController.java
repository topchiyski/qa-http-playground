package io.qaplayground.server.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import io.qaplayground.server.config.PlaygroundProperties;
import io.qaplayground.server.support.EchoResponses;
import io.qaplayground.server.support.GoDuration;
import io.qaplayground.server.support.GoJson;
import io.qaplayground.server.support.GoStrconv;
import io.qaplayground.server.support.HeaderNames;
import io.qaplayground.server.support.HttpbinResponses;
import io.qaplayground.server.support.Pacing;
import io.qaplayground.server.support.Pacing.Timing;
import io.qaplayground.server.support.QueryString;
import io.qaplayground.server.support.RequestInfo;
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
 * Responses that arrive piece by piece: JSON lines, Server-Sent Events and HTTP trailers.
 *
 * <p>Bodies come from a {@link StreamingResponseBody}, which runs on a virtual thread of its own and flushes after
 * every piece. The client sees each piece as soon as it is written.
 */
@Tag(name = "Streaming", description = "Chunked JSON lines, JSONL, Server-Sent Events, trailers")
@RestController
public class StreamingController {

    /**
     * Header names a server must not send as trailers (MDN, RFC 9110 §6.5.1).
     */
    private static final Set<String> FORBIDDEN_TRAILERS = Set.of("Authorization", "Cache-Control",
            "Content-Encoding", "Content-Length", "Content-Range", "Content-Type", "Host", "Max-Forwards",
            "Set-Cookie", "Te", "Trailer", "Transfer-Encoding");

    private final EchoResponses echo;
    private final RequestInfo info;
    private final PlaygroundProperties properties;
    private final Clock clock;
    private final long maxJsonlCount;
    private final long maxSseCount;

    public StreamingController(EchoResponses echo, RequestInfo info, PlaygroundProperties properties, Clock clock) {
        this.echo = echo;
        this.info = info;
        this.properties = properties;
        this.clock = clock;
        // Like go-httpbin: cap counts so the stream cannot exceed max-body-size.
        this.maxJsonlCount = properties.maxBodyBytes() / jsonlSampleLength();
        this.maxSseCount = properties.maxBodyBytes() / sseEvent(999, Instant.now(clock)).length();
    }

    /**
     * {@code n} lines of JSON (clamped to 1..100), each flushed on its own with chunked encoding.
     *
     * <p><b>HTTP concept:</b> {@code Transfer-Encoding: chunked}, and reading a body bit by bit.
     */
    @Operation(summary = "n newline-delimited JSON objects (max 100)")
    @RequestMapping("/stream/{n}")
    public ResponseEntity<StreamingResponseBody> stream(@PathVariable String n, HttpServletRequest request) {
        long count;
        try {
            count = GoStrconv.atoi(n);
        } catch (GoStrconv.NumberFormatError e) {
            return HttpbinResponses.asStream(HttpbinResponses.error(400, "invalid count: " + e.getMessage()));
        }
        long lines = Math.max(1, Math.min(count, properties.maxStreamLines()));
        Map<String, Object> line = streamLine(request);
        StreamingResponseBody body = out -> {
            for (int i = 0; i < lines; i++) {
                line.put("id", i);
                out.write((GoJson.compact(line) + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        };
        // go-httpbin sets no Content-Type; Go sniffs the first bytes and picks text/plain.
        return ResponseEntity.ok().header("Content-Type", HttpbinResponses.TEXT).body(body);
    }

    /**
     * JSON Lines ({@code application/x-ndjson}): {@code count} lines spread over {@code duration}, after
     * {@code delay}, with optional random {@code jitter} (0..1) between lines.
     *
     * <p><b>HTTP concept:</b> streaming APIs, such as log tails or LLM token streams, and parsing records as they
     * arrive.
     */
    @Operation(summary = "JSON Lines stream")
    @Parameter(name = "count", in = ParameterIn.QUERY, description = "Lines (default 10)")
    @Parameter(name = "duration", in = ParameterIn.QUERY, description = "Spread lines over this long, e.g. `5s`")
    @Parameter(name = "delay", in = ParameterIn.QUERY, description = "Wait before the first line")
    @Parameter(name = "jitter", in = ParameterIn.QUERY, description = "Randomise pauses by ±fraction, 0..1")
    @RequestMapping("/jsonl")
    public ResponseEntity<StreamingResponseBody> jsonl(HttpServletRequest request) {
        Map<String, List<String>> q = QueryString.parse(request.getQueryString());
        PlaygroundProperties.Jsonl defaults = properties.jsonl();
        long count = defaults.count();
        String rawCount = QueryString.first(q, "count");
        if (!rawCount.isEmpty()) {
            try {
                count = GoStrconv.atoi(rawCount);
            } catch (GoStrconv.NumberFormatError e) {
                return badRequest("invalid count: " + e.getMessage());
            }
            count = Math.max(1, Math.min(count, maxJsonlCount)); // clamped, not rejected
        }
        Timings t;
        try {
            t = timings(q, defaults.duration(), defaults.delay());
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
        if (t == null) {
            return tooMuchTime();
        }
        Pacing.sleep(t.delay());

        Map<String, Object> line = streamLine(request);
        Duration pause = Pacing.pausePerWrite(t.duration(), count);
        long lines = count;
        StreamingResponseBody body = out -> {
            for (int i = 0; i < lines; i++) {
                if (i > 0 && !pause.isZero() && !Pacing.sleep(Pacing.withJitter(pause, t.jitter()))) {
                    return;
                }
                line.put("id", i);
                out.write((GoJson.compact(line) + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        };
        return ResponseEntity.ok().header("Content-Type", HttpbinResponses.JSONL).body(body);
    }

    /**
     * Server-Sent Events: {@code count} {@code ping} events spread over {@code duration}, each
     * {@code data: {"id":0,"timestamp":...}}. A {@code Server-Timing} <em>trailer</em> follows the last event.
     *
     * <p><b>HTTP concept:</b> {@code text/event-stream}, the browser's {@code EventSource} API, and long-lived
     * responses.
     */
    @Operation(summary = "Server-Sent Events stream")
    @Parameter(name = "count", in = ParameterIn.QUERY, description = "Events (default 10)")
    @Parameter(name = "duration", in = ParameterIn.QUERY, description = "Spread events over this long (default 5s)")
    @Parameter(name = "delay", in = ParameterIn.QUERY, description = "Wait before the first event")
    @Parameter(name = "jitter", in = ParameterIn.QUERY, description = "Randomise pauses by ±fraction, 0..1")
    @RequestMapping("/sse")
    public ResponseEntity<StreamingResponseBody> sse(HttpServletRequest request, HttpServletResponse response) {
        Instant start = Instant.now(clock);
        Map<String, List<String>> q = QueryString.parse(request.getQueryString());
        PlaygroundProperties.Sse defaults = properties.sse();
        long count = defaults.count();
        String rawCount = QueryString.first(q, "count");
        if (!rawCount.isEmpty()) {
            try {
                count = GoStrconv.atoi(rawCount);
            } catch (GoStrconv.NumberFormatError e) {
                return badRequest("invalid count: " + e.getMessage());
            }
            if (count < 1 || count > maxSseCount) {
                return badRequest("invalid count: must in range [1, " + maxSseCount + "]");
            }
        }
        Timings t;
        try {
            t = timings(q, defaults.duration(), defaults.delay());
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
        if (t == null) {
            return tooMuchTime();
        }
        Duration pause = Pacing.pausePerWrite(t.duration(), count);
        Pacing.sleep(t.delay());

        // The trailer is computed when the body is complete, so it can report the real total time.
        response.setTrailerFields(() -> Map.of("Server-Timing", Pacing.serverTiming(List.of(
                new Timing("total_duration", Duration.between(start, Instant.now(clock)), "total request duration"),
                new Timing("initial_delay", t.delay(), "initial delay"),
                new Timing("write_duration", t.duration(), "duration of writes after initial delay"),
                new Timing("pause_per_write", pause, "computed pause between writes")))));

        long events = count;
        StreamingResponseBody body = out -> {
            for (int i = 0; i < events; i++) {
                if (i > 0 && !pause.isZero() && !Pacing.sleep(Pacing.withJitter(pause, t.jitter()))) {
                    return;
                }
                out.write(sseEvent(i, Instant.now(clock)).getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        };
        return ResponseEntity.ok()
                .header("Trailer", "Server-Timing")
                .header("Content-Type", HttpbinResponses.SSE)
                .body(body);
    }

    /**
     * Echoes the request like {@code /anything}, then sends every query parameter as an HTTP <em>trailer</em>: a header
     * that arrives after the body.
     *
     * <p>Names that may not be trailers ({@code Content-Length}, {@code Authorization}, ...) get 400.
     *
     * <p><b>HTTP concept:</b> trailers carry values known only once the body is done, such as checksums and timings.
     * Most tools hide them. Find out whether yours shows them.
     */
    @Operation(summary = "Send query params as HTTP trailers")
    @RequestMapping("/trailers")
    public void trailers(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, List<String>> q = info.args(request);
        for (String key : q.keySet()) {
            if (FORBIDDEN_TRAILERS.contains(HeaderNames.canonical(key))) {
                write(response, HttpbinResponses.error(400, "forbidden trailer: " + key));
                return;
            }
        }
        q.keySet().forEach(k -> response.addHeader("Trailer", k));
        Map<String, String> trailers = new TreeMap<>();
        q.forEach((k, vs) -> trailers.put(HeaderNames.canonical(k), vs.getLast())); // last value wins, as with Go's Header.Set
        response.setTrailerFields(() -> trailers);

        ResponseEntity<byte[]> body = echo.withBody(request);
        response.setStatus(body.getStatusCode().value());
        response.setHeader("Content-Type", HttpbinResponses.JSON);
        response.getOutputStream().write(body.getBody());
        response.flushBuffer(); // forces chunked encoding even without trailers, like the reference
    }

    // --- helpers ---------------------------------------------------------------------------------

    /**
     * The checked timing parameters of a streaming request.
     */
    private record Timings(Duration duration, Duration delay, double jitter) {
    }

    /**
     * Parses duration (at least 1ns), delay and jitter. Returns null when duration plus delay is too long.
     */
    private Timings timings(Map<String, List<String>> q, Duration duration, Duration delay) {
        Duration max = properties.maxDuration();
        if (!QueryString.first(q, "duration").isEmpty()) {
            try {
                duration = GoDuration.parseBounded(QueryString.first(q, "duration"), Duration.ofNanos(1), max);
            } catch (GoDuration.DurationError e) {
                throw new IllegalArgumentException("invalid duration: " + e.getMessage());
            }
        }
        if (!QueryString.first(q, "delay").isEmpty()) {
            try {
                delay = GoDuration.parseBounded(QueryString.first(q, "delay"), Duration.ZERO, max);
            } catch (GoDuration.DurationError e) {
                throw new IllegalArgumentException("invalid delay: " + e.getMessage());
            }
        }
        double jitter = 0;
        String rawJitter = QueryString.first(q, "jitter");
        if (!rawJitter.isEmpty()) {
            try {
                jitter = GoStrconv.parseFloat(rawJitter);
            } catch (GoStrconv.NumberFormatError e) {
                jitter = -1;
            }
            if (jitter < 0 || jitter > 1) { // written this way on purpose: NaN passes, as in Go
                throw new IllegalArgumentException("invalid jitter: must be a float in [0, 1]");
            }
        }
        if (duration.plus(delay).compareTo(max) > 0) {
            return null;
        }
        return new Timings(duration, delay, jitter);
    }

    /**
     * The JSON object every {@code /stream} and {@code /jsonl} line carries. The caller sets each line's id.
     */
    private Map<String, Object> streamLine(HttpServletRequest request) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("id", 0);
        line.put("args", info.args(request));
        line.put("headers", info.headers(request));
        line.put("origin", info.clientIp(request));
        line.put("url", info.url(request));
        return line;
    }

    /**
     * One event in the stream: {@code event: ping\ndata: {"id":0,"timestamp":1700000000000}\n\n}.
     */
    static String sseEvent(int id, Instant at) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("timestamp", at.toEpochMilli());
        return "event: ping\ndata: " + GoJson.compact(data) + "\n\n";
    }

    /**
     * The length of a typical JSON line. Dividing the body limit by it gives the most lines {@code /jsonl} allows.
     */
    private static long jsonlSampleLength() {
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("id", 999);
        sample.put("args", Map.of("sample", List.of("value")));
        sample.put("headers", Map.of("Host", List.of("example.com"), "User-Agent", List.of("sample-agent/1.0")));
        sample.put("origin", "127.0.0.1");
        sample.put("url", "http://example.com/jsonl?count=100");
        return GoJson.compact(sample).length() + 1;
    }

    /**
     * A 400 with go-httpbin's JSON error, in the type a streaming handler must return.
     */
    private static ResponseEntity<StreamingResponseBody> badRequest(String detail) {
        return HttpbinResponses.asStream(HttpbinResponses.error(400, detail));
    }

    /**
     * "Too much time" as plain text. go-httpbin uses Go's {@code http.Error} here, not its JSON error shape.
     */
    private static ResponseEntity<StreamingResponseBody> tooMuchTime() {
        return HttpbinResponses.asStream(ResponseEntity.status(400)
                .header("Content-Type", HttpbinResponses.TEXT)
                .header("X-Content-Type-Options", "nosniff")
                .body("Too much time\n".getBytes(StandardCharsets.UTF_8)));
    }
}
