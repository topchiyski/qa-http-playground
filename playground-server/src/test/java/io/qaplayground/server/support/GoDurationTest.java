package io.qaplayground.server.support;

import java.time.Duration;
import java.util.stream.Stream;

import io.qaplayground.testing.Boundaries;
import io.qaplayground.testing.Generators;
import io.qaplayground.testing.technique.BoundaryValue;
import io.qaplayground.testing.technique.EquivalencePartitioning;
import io.qaplayground.testing.technique.ErrorGuessing;
import io.qaplayground.testing.technique.PropertyBased;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Go durations: parsing, bounds and printing.
 *
 * <p>Durations drive {@code /delay}, {@code /drip}, {@code /sse} and friends. The configured maximum is the main
 * defence against resource exhaustion (risk R3), so its edges are tested the hardest.
 */
class GoDurationTest {

    /**
     * The default {@code playground.max-duration}.
     */
    private static final Duration MAX = Duration.ofSeconds(10);

    @EquivalencePartitioning
    @ParameterizedTest(name = "{0} = {1} ns")
    @CsvSource({
            "1h, 3600000000000",
            "1m30s, 90000000000",
            "1.5s, 1500000000",
            ".5s, 500000000",
            "5.s, 5000000000",
            "300ms, 300000000",
            "1.5us, 1500",
            "1.5µs, 1500",     // micro sign U+00B5
            "1.5μs, 1500",     // Greek mu U+03BC, also accepted by Go
            "7ns, 7",
            "-1.5s, -1500000000",
            "+2s, 2000000000",
            "0, 0",
            "-0, 0"})
    void parsesGoDurationSyntax(String input, long nanos) {
        assertThat(GoDuration.parseGo(input)).isEqualTo(Duration.ofNanos(nanos));
    }

    @ErrorGuessing
    @ParameterizedTest(name = "parseGo(\"{0}\") -> {1}")
    @CsvSource(delimiter = '|', value = {
            "''|time: invalid duration \"\"",
            "-|time: invalid duration \"-\"",
            ".|time: invalid duration \".\"",
            "s|time: invalid duration \"s\"",
            "5|time: missing unit in duration \"5\"",
            "1.5.5s|time: missing unit in duration \"1.5.5s\"",
            "5ss|time: unknown unit \"ss\" in duration \"5ss\"",
            "1e3s|time: unknown unit \"e\" in duration \"1e3s\"",
            "5S|time: unknown unit \"S\" in duration \"5S\""})
    void rejectsMalformedGoDurations(String input, String message) {
        assertThatThrownBy(() -> GoDuration.parseGo(input)).isInstanceOf(GoDuration.DurationError.class).hasMessage(message);
    }

    /**
     * Durations too big for Go's int64 nanoseconds are invalid, exactly where Go draws the line.
     */
    @BoundaryValue
    @ParameterizedTest(name = "{0} overflows int64 nanoseconds: {1}")
    @CsvSource({
            "9223372036854775807ns, false",
            "9223372036854775808ns, true",
            "2562047h, false",
            "2562048h, true",
            "2562047h47m16.854775807s, false",
            "2562047h47m16.854775808s, true"})
    void overflowIsInvalid(String input, boolean overflows) {
        if (overflows) {
            assertThatThrownBy(() -> GoDuration.parseGo(input)).hasMessageStartingWith("time: invalid duration");
        } else {
            assertThat(GoDuration.parseGo(input)).isPositive();
        }
    }

    /**
     * A plain number means seconds, truncated to whole milliseconds as Go does.
     */
    @EquivalencePartitioning
    @ParameterizedTest(name = "parse({0}) = {1} ms")
    @CsvSource({"1.5, 1500", "0.0015, 1", "0.0009, 0", "3, 3000", "1e1, 10000"})
    void plainNumbersAreSecondsTruncatedToMilliseconds(String input, long millis) {
        assertThat(GoDuration.parse(input)).isEqualTo(Duration.ofMillis(millis));
    }

    /**
     * Input that is neither syntax gets the float-parsing error, as in go-httpbin.
     */
    @Test
    void parseReportsTheFloatErrorWhenNeitherSyntaxApplies() {
        assertThatThrownBy(() -> GoDuration.parse("abc")).hasMessage("strconv.ParseFloat: parsing \"abc\": invalid syntax");
        assertThatThrownBy(() -> GoDuration.parse("")).hasMessage("strconv.ParseFloat: parsing \"\": invalid syntax");
    }

    // --- bounds (the inclusive check is deliberate: go-httpbin's parseBoundedDuration) --------

    /**
     * Just below, on and just above the 10-second limit.
     */
    static Stream<Arguments> upperBoundary() {
        return Boundaries.around("10", "0.001").stream()
                .map(p -> Arguments.of(p, p.side() != Boundaries.Side.ABOVE));
    }

    /**
     * Exactly the maximum is allowed; the smallest step above it is not.
     */
    @BoundaryValue
    @ParameterizedTest(name = "{0}s accepted: {1}")
    @MethodSource("upperBoundary")
    void upperLimitIsInclusive(Boundaries.Point seconds, boolean accepted) {
        if (accepted) {
            assertThat(GoDuration.parseBounded(seconds.text(), Duration.ZERO, MAX)).isLessThanOrEqualTo(MAX);
        } else {
            assertThatThrownBy(() -> GoDuration.parseBounded(seconds.text(), Duration.ZERO, MAX))
                    .hasMessage("duration 10.001s longer than 10s");
        }
    }

    /**
     * Exactly zero is allowed; anything below it is not.
     */
    @BoundaryValue
    @ParameterizedTest(name = "{0} accepted: {1}")
    @CsvSource({"-1ms, false", "0s, true", "1ms, true"})
    void lowerLimitIsInclusive(String input, boolean accepted) {
        if (accepted) {
            assertThat(GoDuration.parseBounded(input, Duration.ZERO, MAX)).isGreaterThanOrEqualTo(Duration.ZERO);
        } else {
            assertThatThrownBy(() -> GoDuration.parseBounded(input, Duration.ZERO, MAX))
                    .hasMessage("duration -1ms shorter than 0s");
        }
    }

    /**
     * Huge numbers saturate and are then rejected by the bound. Go wraps them to 0 instead (docs/COMPATIBILITY.md).
     */
    @ErrorGuessing
    @ParameterizedTest(name = "parseBounded(\"{0}\") -> {1}")
    @CsvSource(delimiter = '|', value = {
            "Infinity|duration 2562047h47m16.854775807s longer than 10s",
            "1e18|duration 2562047h47m16.854775807s longer than 10s",
            "-Infinity|duration -2562047h47m16.854775807s shorter than 0s"})
    void outOfRangeSecondsSaturateInsteadOfOverflowing(String input, String message) {
        assertThatThrownBy(() -> GoDuration.parseBounded(input, Duration.ZERO, MAX)).hasMessage(message);
    }

    /**
     * NaN parses as zero, as in Go.
     */
    @Test
    void nanSecondsAreZeroLikeGo() {
        assertThat(GoDuration.parse("NaN")).isEqualTo(Duration.ZERO);
    }

    /**
     * Fault guessed: special float values slip past the bound.
     *
     * <p>Whatever they parse to, the result must never exceed the limit. That is the property that protects the server.
     */
    @ErrorGuessing
    @ParameterizedTest(name = "parseBounded(\"{0}\") stays within [0, 10s] or fails")
    @ValueSource(strings = {"NaN", "Infinity", "-Infinity", "+Inf", "1e309", "1e18", "9223372036854775807"})
    void specialNumbersNeverEscapeTheBounds(String input) {
        try {
            Duration d = GoDuration.parseBounded(input, Duration.ZERO, MAX);
            assertThat(d).isBetween(Duration.ZERO, MAX);
        } catch (GoDuration.DurationError expected) {
            assertThat(expected).hasMessageMatching("(duration .*|strconv.ParseFloat: .*)");
        }
    }

    // --- formatting ---------------------------------------------------------------------------

    @EquivalencePartitioning
    @ParameterizedTest(name = "{0} ns -> {1}")
    @CsvSource({
            "0, 0s", "1, 1ns", "999, 999ns", "1000, 1µs", "1500, 1.5µs", "1000000, 1ms",
            "1500000, 1.5ms", "999999999, 999.999999ms", "1000000000, 1s", "60000000000, 1m0s",
            "3600000000000, 1h0m0s", "-1500000000, -1.5s", "1000000001, 1.000000001s"})
    void formatsLikeDurationString(long nanos, String expected) {
        assertThat(GoDuration.format(Duration.ofNanos(nanos))).isEqualTo(expected);
    }

    /**
     * Seeded random durations across every unit, for the round-trip property.
     */
    static Stream<Arguments> randomDurations() {
        return Generators.samples(r -> switch (r.nextInt(4)) {
            case 0 -> Duration.ofNanos(r.nextLong(-1_000_000, 1_000_000));           // sub-millisecond
            case 1 -> Duration.ofNanos(r.nextLong(-60_000_000_000L, 60_000_000_000L)); // around a minute
            case 2 -> Duration.ofNanos(r.nextLong(-1L << 50, 1L << 50));             // days
            default -> Duration.ofNanos(r.nextLong(-Long.MAX_VALUE, Long.MAX_VALUE)); // anything representable
        });
    }

    /**
     * Formatting a duration and parsing the text gives back the same duration.
     */
    @PropertyBased
    @ParameterizedTest
    @MethodSource("randomDurations")
    void formatAndParseAreInverse(Duration d) {
        assertThat(GoDuration.parseGo(GoDuration.format(d))).isEqualTo(d);
    }
}
