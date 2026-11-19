package io.qaplayground.server.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Timing helpers for the slow and streaming endpoints, ported from go-httpbin.
 *
 * <p>A slow endpoint is only useful if it is slow in a predictable way. These helpers spread writes evenly over a
 * requested time and report where the time went.
 */
public final class Pacing {

    private Pacing() {
    }

    /**
     * One entry of a {@code Server-Timing} header: a name, a duration and a description.
     */
    public record Timing(String name, Duration duration, String description) {
    }

    /**
     * {@code Server-Timing: total_duration;dur=1500.00;desc="total request duration", ...}.
     *
     * <p>Browsers show these in the timing view of the network panel, so you can see the delay you asked for.
     */
    public static String serverTiming(List<Timing> timings) {
        return timings.stream()
                .map(t -> t.name() + ";dur=" + millis(t.duration()) + ";desc=\"" + t.description() + "\"")
                .collect(Collectors.joining(", "));
    }

    /**
     * A duration in milliseconds with two decimals, as Go prints it in {@code Server-Timing}.
     */
    private static String millis(Duration d) {
        return BigDecimal.valueOf(d.toNanos()).movePointLeft(6).setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }

    /**
     * How long to pause between {@code count} writes so that they span {@code duration}.
     *
     * <p>There is no pause after the last write, so there are {@code count - 1} gaps (rounded up).
     */
    public static Duration pausePerWrite(Duration duration, long count) {
        if (count <= 1) {
            return duration;
        }
        long n = count - 1;
        return Duration.ofNanos((duration.toNanos() + n - 1) / n);
    }

    /**
     * Randomly scales {@code d} by up to ±{@code jitter} (0 to 1), so clients see realistic variation.
     */
    public static Duration withJitter(Duration d, double jitter) {
        if (jitter <= 0) {
            return d;
        }
        return withJitter(d, jitter, ThreadLocalRandom.current().nextDouble());
    }

    /**
     * {@link #withJitter(Duration, double)} with the random draw passed in, so the result is exact.
     *
     * @param draw a number in [0, 1): 0 gives the low edge of the band, 0.5 the middle
     */
    public static Duration withJitter(Duration d, double jitter, double draw) {
        if (jitter <= 0) {
            return d;
        }
        double scale = 1.0 + jitter * (2 * draw - 1);
        long nanos = (long) (d.toNanos() * scale);
        return Duration.ofNanos(Math.max(0, nanos));
    }

    /**
     * Sleeps for {@code d}. Returns false if interrupted, for example because the client went away.
     */
    public static boolean sleep(Duration d) {
        if (d.isZero() || d.isNegative()) {
            return true;
        }
        try {
            Thread.sleep(d);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
