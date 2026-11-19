package io.qaplayground.server.web;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.qaplayground.server.config.PlaygroundProperties;
import io.qaplayground.server.support.EchoResponses;
import io.qaplayground.server.support.GoDuration;
import io.qaplayground.server.support.GoStrconv;
import io.qaplayground.server.support.HttpbinResponses;
import io.qaplayground.server.support.Pacing;
import io.qaplayground.server.support.Pacing.Timing;
import io.qaplayground.server.support.QueryString;
import io.qaplayground.server.support.StatusCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Slow responses.
 *
 * <p>With virtual threads, a sleeping request costs almost nothing. You can point hundreds of concurrent users at
 * these endpoints without exhausting the server.
 */
@Tag(name = "Timing", description = "Delayed and slowly trickled responses")
@RestController
public class TimingController {

    private final EchoResponses echo;
    private final PlaygroundProperties properties;

    public TimingController(EchoResponses echo, PlaygroundProperties properties) {
        this.echo = echo;
        this.properties = properties;
    }

    /**
     * Waits, then echoes the request like {@code /anything}.
     *
     * <p>Accepts seconds ({@code 1.5}) or a Go duration ({@code 1500ms}). The maximum is
     * {@code playground.max-duration} (10s).
     *
     * <p><b>HTTP concept:</b> timeouts and latency. Set your client's read timeout below the delay and watch it fail.
     * In
     * load tests, this is "a slow dependency".
     */
    @Operation(summary = "Respond after a delay (max 10s by default)")
    @RequestMapping("/delay/{duration}")
    public ResponseEntity<byte[]> delay(@Parameter(description = "Seconds (`1.5`) or Go duration (`300ms`)")
                                        @PathVariable String duration, HttpServletRequest request) {
        Duration delay;
        try {
            delay = GoDuration.parseBounded(duration, Duration.ZERO, properties.maxDuration());
        } catch (GoDuration.DurationError e) {
            return HttpbinResponses.error(400, "invalid duration: " + e.getMessage());
        }
        Pacing.sleep(delay);
        ResponseEntity<byte[]> body = echo.withBody(request);
        return ResponseEntity.status(body.getStatusCode())
                .headers(body.getHeaders())
                .header("Server-Timing", Pacing.serverTiming(List.of(new Timing("initial_delay", delay, "initial delay"))))
                .body(body.getBody());
    }

    /**
     * Sends {@code numbytes} asterisks spread evenly over {@code duration}, after an initial {@code delay}, with status
     * {@code code}.
     *
     * <p>It sends a {@code Content-Length}, not chunked encoding, to imitate a slow network rather than a streaming
     * API.
     *
     * <p><b>HTTP concept:</b> time to first byte versus total download time, and how clients handle a slow server and
     * read timeouts.
     */
    @Operation(summary = "Trickle bytes slowly")
    @Parameter(name = "numbytes", in = ParameterIn.QUERY, description = "Bytes to send (default 10)")
    @Parameter(name = "duration", in = ParameterIn.QUERY, description = "Time to spread the bytes over (default 2s)")
    @Parameter(name = "delay", in = ParameterIn.QUERY, description = "Wait before the first byte (default 2s)")
    @Parameter(name = "code", in = ParameterIn.QUERY, description = "Response status (default 200)")
    @RequestMapping("/drip")
    public ResponseEntity<StreamingResponseBody> drip(HttpServletRequest request) {
        Map<String, List<String>> q = QueryString.parse(request.getQueryString());
        PlaygroundProperties.Drip defaults = properties.drip();
        Duration duration = defaults.duration();
        Duration delay = defaults.delay();
        long numBytes = defaults.numBytes();
        int code = 200;
        Duration max = properties.maxDuration();
        try {
            if (!QueryString.first(q, "duration").isEmpty()) {
                duration = parse("invalid duration: ", QueryString.first(q, "duration"), max);
            }
            if (!QueryString.first(q, "delay").isEmpty()) {
                delay = parse("invalid delay: ", QueryString.first(q, "delay"), max);
            }
            String rawNumBytes = QueryString.first(q, "numbytes");
            if (!rawNumBytes.isEmpty()) {
                try {
                    numBytes = GoStrconv.parseInt(rawNumBytes, 64);
                } catch (GoStrconv.NumberFormatError e) {
                    throw new IllegalArgumentException("invalid numbytes: " + e.getMessage());
                }
                if (numBytes < 1 || numBytes > properties.maxBodyBytes()) {
                    throw new IllegalArgumentException("invalid numbytes: " + numBytes
                            + " not in range [1, " + properties.maxBodyBytes() + "]");
                }
            }
            String rawCode = QueryString.first(q, "code");
            if (!rawCode.isEmpty()) {
                code = StatusCodes.parse(rawCode, 100, 599);
            }
        } catch (IllegalArgumentException e) {
            return HttpbinResponses.asStream(HttpbinResponses.error(400, e.getMessage()));
        }
        if (duration.plus(delay).compareTo(max) > 0) {
            return HttpbinResponses.asStream(HttpbinResponses.error(400, "too much time: "
                    + GoDuration.format(duration) + "+" + GoDuration.format(delay) + " > " + GoDuration.format(max)));
        }

        Duration pause = Pacing.pausePerWrite(duration, numBytes);
        Pacing.sleep(delay); // before any header is sent, like the reference

        long count = numBytes;
        StreamingResponseBody body = out -> {
            if (pause.isZero()) {
                out.write("*".repeat((int) count).getBytes());
                return;
            }
            for (long i = 0; i < count; i++) {
                out.write('*');
                out.flush();
                if (i < count - 1 && !Pacing.sleep(pause)) {
                    return;
                }
            }
        };
        return ResponseEntity.status(code)
                .header("Content-Type", HttpbinResponses.TEXT)
                .contentLength(numBytes)
                .header("Server-Timing", Pacing.serverTiming(List.of(
                        new Timing("total_duration", delay.plus(duration), "total request duration"),
                        new Timing("initial_delay", delay, "initial delay"),
                        new Timing("write_duration", duration, "duration of writes after initial delay"),
                        new Timing("pause_per_write", pause, "computed pause between writes"))))
                .body(body);
    }

    /**
     * Parses a duration parameter within [0, {@code max}]. A bad value throws, with {@code prefix} in front of Go's
     * message.
     */
    private static Duration parse(String prefix, String raw, Duration max) {
        try {
            return GoDuration.parseBounded(raw, Duration.ZERO, max);
        } catch (GoDuration.DurationError e) {
            throw new IllegalArgumentException(prefix + e.getMessage());
        }
    }
}
