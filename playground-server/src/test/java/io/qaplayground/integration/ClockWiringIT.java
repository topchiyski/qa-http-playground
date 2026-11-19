package io.qaplayground.integration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every endpoint that puts the current time on the wire reads it from the {@link Clock} bean.
 *
 * <p>With the clock fixed, the time-dependent parts of a response become exact values, so a test can pin them
 * instead of asserting a range.
 */
@Import(ClockWiringIT.FixedClock.class) // ServerIT names its classes, so nested configs aren't picked up on their own
class ClockWiringIT extends ServerIT {

    private static final Instant NOW = Instant.parse("2001-02-03T04:05:06Z");

    /**
     * Replaces the system clock for this test's application context only.
     */
    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Test
    @DisplayName("/etag sends the clock's time as Last-Modified")
    void lastModifiedComesFromTheClock() {
        assertThat(get("/etag/x").header("Last-Modified")).isEqualTo("Sat, 03 Feb 2001 04:05:06 GMT");
    }

    @Test
    @DisplayName("/cookies/delete expires the cookie one year before the clock's time")
    void cookieExpiryComesFromTheClock() {
        assertThat(get("/cookies/delete?k=").header("Set-Cookie"))
                .contains("Expires=Fri, 04 Feb 2000 04:05:06 GMT");
    }

    @Test
    @DisplayName("/sse stamps each event with the clock's time")
    void sseTimestampsComeFromTheClock() {
        assertThat(get("/sse?count=1&duration=1ms").body())
                .contains("\"timestamp\":" + NOW.toEpochMilli());
    }
}
