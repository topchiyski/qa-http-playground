package io.qaplayground.server.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Every limit and switch that matters for abuse, bound from {@code playground.*} in {@code application.yml}.
 *
 * <p>The defaults equal go-httpbin's. Out of the box, the playground behaves like go-httpbin; you only change what
 * you need.
 *
 * @param maxBodySize                   largest request body accepted and response body generated
 *                                      (go-httpbin {@code -max-body-size}, default 1 MiB)
 * @param maxDuration                   longest time a client may ask a response to take, for
 *                                      {@code /delay}, {@code /drip}, {@code /sse}, ... (default 10s)
 * @param maxStreamLines                upper clamp for {@code /stream/{n}} (hard-coded 100 upstream)
 * @param maxLinks                      upper bound for {@code /links/{n}} (hard-coded 256 upstream)
 * @param maxRedirects                  upper bound for the redirect endpoints; 0 = unlimited, which
 *                                      is the reference behaviour
 * @param allowedRedirectDomains        allowlist for {@code /redirect-to}; empty = any absolute URL
 * @param excludeHeaders                comma-separated request headers (wildcards allowed) to hide
 *                                      from echo endpoints
 * @param unsafeAllowDangerousResponses disable HTML-escaping when clients choose the response
 *                                      Content-Type (enables reflected XSS; leave false)
 * @param hostname                      value reported by {@code /hostname}
 * @param useRealHostname               report the machine's real hostname instead
 * @param envPrefix                     only environment variables with this prefix appear in {@code /env}
 * @param fullVersion                   include build details in {@code /version}
 * @param drip                          defaults for {@code /drip}
 * @param sse                           defaults for {@code /sse}
 * @param jsonl                         defaults for {@code /jsonl}
 */
@ConfigurationProperties("playground")
public record PlaygroundProperties(
        @DefaultValue("1MB") DataSize maxBodySize,
        @DefaultValue("10s") Duration maxDuration,
        @DefaultValue("100") int maxStreamLines,
        @DefaultValue("256") int maxLinks,
        @DefaultValue("0") int maxRedirects,
        @DefaultValue List<String> allowedRedirectDomains,
        @DefaultValue("") String excludeHeaders,
        @DefaultValue("false") boolean unsafeAllowDangerousResponses,
        @DefaultValue("go-httpbin") String hostname,
        @DefaultValue("false") boolean useRealHostname,
        @DefaultValue("HTTPBIN_ENV_") String envPrefix,
        @DefaultValue("false") boolean fullVersion,
        @DefaultValue Drip drip,
        @DefaultValue Sse sse,
        @DefaultValue Jsonl jsonl) {

    /**
     * The maximum body size in bytes, the unit the limit checks use.
     */
    public long maxBodyBytes() {
        return maxBodySize.toBytes();
    }

    /**
     * Defaults for {@code /drip} when the client leaves a parameter out: total time, initial delay and bytes sent.
     */
    public record Drip(@DefaultValue("2s") Duration duration,
                       @DefaultValue("2s") Duration delay,
                       @DefaultValue("10") long numBytes) {
    }

    /**
     * Defaults for {@code /sse}: how many events, over how long, after which initial delay.
     */
    public record Sse(@DefaultValue("10") int count,
                      @DefaultValue("5s") Duration duration,
                      @DefaultValue("0s") Duration delay) {
    }

    /**
     * Defaults for {@code /jsonl}: how many lines, over how long, after which initial delay.
     */
    public record Jsonl(@DefaultValue("10") int count,
                        @DefaultValue("0s") Duration duration,
                        @DefaultValue("0s") Duration delay) {
    }
}
