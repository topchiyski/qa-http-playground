package io.qaplayground.server.support;

import io.qaplayground.testing.technique.BoundaryValue;
import io.qaplayground.testing.technique.EquivalencePartitioning;
import io.qaplayground.testing.technique.ErrorGuessing;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Go's {@code strconv}: accept and reject rules, and the exact messages.
 *
 * <p>go-httpbin puts {@code strconv}'s error text into its responses. The expected values come from Go's
 * {@code strconv} documentation and tests.
 */
class GoStrconvTest {

    @EquivalencePartitioning
    @ParameterizedTest(name = "Atoi({0}) = {1}")
    @CsvSource({
            "0, 0",
            "42, 42",
            "+5, 5",          // explicit plus sign is valid in Go
            "-7, -7",
            "-0, 0",
            "007, 7"})        // leading zeros are decimal, not octal
    void atoiAcceptsDecimalIntegers(String input, long expected) {
        assertThat(GoStrconv.atoi(input)).isEqualTo(expected);
    }

    @ErrorGuessing
    @ParameterizedTest(name = "Atoi(\"{0}\") is invalid syntax")
    @ValueSource(strings = {"", "+", "-", " 5", "5 ", "3.14", "0x10", "1e3", "1_000", "abc", "٣"})
    void atoiRejectsNonDecimalSyntax(String input) {
        assertThatThrownBy(() -> GoStrconv.atoi(input))
                .isInstanceOf(GoStrconv.NumberFormatError.class)
                .hasMessage("strconv.Atoi: parsing " + GoStrconv.quote(input) + ": invalid syntax");
    }

    /**
     * {@code Atoi} accepts exactly the int64 range; one step outside it is "value out of range".
     */
    @BoundaryValue
    @ParameterizedTest(name = "Atoi({0}) in range: {1}")
    @CsvSource({
            "9223372036854775807, true",
            "9223372036854775808, false",
            "-9223372036854775808, true",
            "-9223372036854775809, false"})
    void atoiRangeIsInt64(String input, boolean inRange) {
        if (inRange) {
            assertThat(GoStrconv.atoi(input)).isEqualTo(Long.parseLong(input));
        } else {
            assertThatThrownBy(() -> GoStrconv.atoi(input))
                    .hasMessage("strconv.Atoi: parsing \"" + input + "\": value out of range");
        }
    }

    /**
     * {@code ParseInt} checks the range of the bit size it is given, not always 64 bits.
     */
    @BoundaryValue
    @ParameterizedTest(name = "ParseInt({0}, bitSize {1}) in range: {2}")
    @CsvSource({
            "127, 8, true", "128, 8, false", "-128, 8, true", "-129, 8, false",
            "2147483647, 32, true", "2147483648, 32, false"})
    void parseIntRespectsBitSize(String input, int bitSize, boolean inRange) {
        if (inRange) {
            assertThat(GoStrconv.parseInt(input, bitSize)).isEqualTo(Long.parseLong(input));
        } else {
            assertThatThrownBy(() -> GoStrconv.parseInt(input, bitSize))
                    .hasMessage("strconv.ParseInt: parsing \"" + input + "\": value out of range");
        }
    }

    @EquivalencePartitioning
    @ParameterizedTest(name = "ParseFloat({0}) = {1}")
    @CsvSource({
            "1.5, 1.5", ".5, 0.5", "5., 5.0", "-2.25, -2.25", "1e3, 1000.0", "1E-2, 0.01", "+3, 3.0"})
    void parseFloatAcceptsDecimalForms(String input, double expected) {
        assertThat(GoStrconv.parseFloat(input)).isEqualTo(expected);
    }

    @EquivalencePartitioning
    @ParameterizedTest(name = "ParseFloat({0}) is special")
    @ValueSource(strings = {"inf", "+Inf", "INFINITY", "-inf", "NaN", "nan"})
    void parseFloatAcceptsInfinityAndNan(String input) {
        double d = GoStrconv.parseFloat(input);
        assertThat(Double.isNaN(d) || Double.isInfinite(d)).isTrue();
        if (input.startsWith("-")) {
            assertThat(d).isEqualTo(Double.NEGATIVE_INFINITY);
        }
    }

    @ErrorGuessing
    @ParameterizedTest(name = "ParseFloat(\"{0}\") is invalid syntax")
    @ValueSource(strings = {"", ".", "e5", "1.5.5", "1,5", " 1", "infinit", "--1"})
    void parseFloatRejectsMalformedInput(String input) {
        assertThatThrownBy(() -> GoStrconv.parseFloat(input))
                .hasMessage("strconv.ParseFloat: parsing " + GoStrconv.quote(input) + ": invalid syntax");
    }

    /**
     * Floats too large for a double are "value out of range", not infinity.
     */
    @BoundaryValue
    @ParameterizedTest(name = "ParseFloat({0}) overflows: {1}")
    @CsvSource({"1.7976931348623157e308, false", "1e309, true", "-1e309, true"})
    void parseFloatOverflowIsOutOfRange(String input, boolean overflows) {
        if (overflows) {
            assertThatThrownBy(() -> GoStrconv.parseFloat(input)).hasMessageEndingWith("value out of range");
        } else {
            assertThat(GoStrconv.parseFloat(input)).isEqualTo(Double.MAX_VALUE);
        }
    }

    @EquivalencePartitioning
    @ParameterizedTest(name = "quote {0}")
    @CsvSource(delimiter = '|', value = {
            "plain|\"plain\"",
            "say \"hi\"|\"say \\\"hi\\\"\"",
            "back\\slash|\"back\\\\slash\"",
            "café|\"café\""})
    void quoteEscapesLikeGo(String input, String expected) {
        assertThat(GoStrconv.quote(input)).isEqualTo(expected);
    }

    @ErrorGuessing
    @org.junit.jupiter.api.Test
    void quoteEscapesControlCharacters() {
        assertThat(GoStrconv.quote("a\nb\rc\td")).isEqualTo("\"a\\nb\\rc\\td\"");
        assertThat(GoStrconv.quote("\u0000\u0001\u007f")).isEqualTo("\"\\x00\\x01\\x7f\"");
    }
}
