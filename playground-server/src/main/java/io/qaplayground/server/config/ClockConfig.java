package io.qaplayground.server.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one source of "now" for every endpoint that puts the current time on the wire.
 *
 * <p>Controllers take this {@link Clock} instead of calling {@code Instant.now()}. A test can then swap in
 * {@code Clock.fixed(...)} and assert exact times ({@code Last-Modified}, cookie expiry, SSE timestamps) instead of
 * ranges.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /**
     * The system clock, in UTC, as HTTP dates are.
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
