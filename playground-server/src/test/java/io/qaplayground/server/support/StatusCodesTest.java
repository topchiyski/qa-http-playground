package io.qaplayground.server.support;

import io.qaplayground.testing.technique.BoundaryValue;
import io.qaplayground.testing.technique.EquivalencePartitioning;
import io.qaplayground.testing.technique.ErrorGuessing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Status-code parsing and weighted selection, behind {@code /status}, {@code /redirect-to} and {@code /drip}.
 */
class StatusCodesTest {

    /**
     * Both ends of the allowed range are accepted; one step outside is not.
     */
    @BoundaryValue
    @ParameterizedTest(name = "parse({0}, 100..599) valid: {1}")
    @CsvSource({"99, false", "100, true", "101, true", "598, true", "599, true", "600, false"})
    void rangeIsInclusive(String code, boolean valid) {
        if (valid) {
            assertThat(StatusCodes.parse(code, 100, 599)).isEqualTo(Integer.parseInt(code));
        } else {
            assertThatThrownBy(() -> StatusCodes.parse(code, 100, 599))
                    .hasMessage("invalid status code: " + code + " not in range [100, 599]");
        }
    }

    /**
     * A code that isn't a number gets Go's {@code strconv} error, quoted inside the endpoint's message.
     */
    @ErrorGuessing
    @Test
    void nonNumericCodesQuoteTheStrconvError() {
        assertThatThrownBy(() -> StatusCodes.parse("abc", 100, 599))
                .hasMessage("invalid status code: \"abc\": strconv.Atoi: parsing \"abc\": invalid syntax");
    }

    /**
     * Each code owns an interval of [0, 1) in proportion to its weight. The edges of each interval are tested.
     */
    @BoundaryValue
    @ParameterizedTest(name = "random {1} over \"{0}\" -> {2}")
    @CsvSource({
            "'200:0.7,500:0.3', 0.0, 200",
            "'200:0.7,500:0.3', 0.6999, 200",
            "'200:0.7,500:0.3', 0.7, 500",
            "'200:0.7,500:0.3', 0.9999, 500",
            "'200,500', 0.4999, 200",          // missing weight counts as 1
            "'200,500', 0.5, 500",
            "'200:2,500:2', 0.5, 500",         // weights need not sum to 1
            "'418', 0.3, 418"})
    void weightedChoiceIntervals(String spec, double random, int expected) {
        assertThat(StatusCodes.weightedChoice(spec, random)).isEqualTo(expected);
    }

    @ErrorGuessing
    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', value = {
            "200:x|invalid weight value: \"x\"",
            "abc:1|invalid choice value: \"abc\"",
            "200:0|failed to select a weighted random choice",          // all weights zero
            "200:-1,500:-1|failed to select a weighted random choice",  // negative weights
            "200:0.5,|invalid choice value: \"\""})                       // trailing comma
    void weightedChoiceErrors(String spec, String message) {
        assertThatThrownBy(() -> StatusCodes.weightedChoice(spec, 0.5)).hasMessage(message);
    }

    /**
     * The same seed gives the same sequence of choices.
     */
    @Test
    void seededRandomIsReproducible() {
        assertThat(StatusCodes.seededRandom("42").int63()).isEqualTo(StatusCodes.seededRandom("42").int63());
        assertThat(StatusCodes.seededRandom("")).isNotNull();
        assertThatThrownBy(() -> StatusCodes.seededRandom("x")).isInstanceOf(GoStrconv.NumberFormatError.class);
    }
}
