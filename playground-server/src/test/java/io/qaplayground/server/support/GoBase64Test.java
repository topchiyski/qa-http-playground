package io.qaplayground.server.support;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Stream;

import io.qaplayground.testing.Generators;
import io.qaplayground.testing.technique.EquivalencePartitioning;
import io.qaplayground.testing.technique.ErrorGuessing;
import io.qaplayground.testing.technique.PropertyBased;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Go's padded base64 decoder: stricter than Java's about padding, lenient about CR/LF, and it reports the byte
 * offset of the first problem.
 *
 * <p>{@code /base64} shows these messages to users, so they must match Go's exactly.
 */
class GoBase64Test {

    /**
     * Decodes and returns UTF-8 text, to keep the expected values readable.
     */
    private static String decode(String s, String alphabet) throws GoBase64.CorruptInputError {
        return new String(GoBase64.decode(s, alphabet), StandardCharsets.UTF_8);
    }

    @EquivalencePartitioning
    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "''|''",
            "aA==|h",               // 2 padding characters
            "aGk=|hi",              // 1 padding character
            "aGVs|hel",             // no padding needed
            "aGVsbG8=|hello"})
    void decodesValidInput(String input, String expected) throws Exception {
        assertThat(decode(input, GoBase64.STD)).isEqualTo(expected);
    }

    /**
     * CR and LF are ignored wherever they appear, as Go ignores them.
     */
    @EquivalencePartitioning
    @Test
    void lineBreaksAreSkippedAnywhere() throws Exception {
        assertThat(decode("aGVs\r\nbG8=", GoBase64.STD)).isEqualTo("hello");
        assertThat(decode("aGVsbG8=\n", GoBase64.STD)).isEqualTo("hello");
        assertThat(decode("aG=\r\n=", GoBase64.STD)).isEqualTo("h");
    }

    /**
     * A bad input fails at the same byte offset Go reports.
     */
    @ErrorGuessing
    @ParameterizedTest(name = "\"{0}\" fails at byte {1}")
    @CsvSource(delimiter = '|', value = {
            "aGVsbG8|4",            // missing padding: Go rejects what Java would accept
            "aGV$bG8=|3",           // illegal character
            "=|0",                  // padding first
            "a===|1",               // padding after one character
            "aG=|3",                // single '=' where two are needed, then end of input
            "aG=x|2",               // '=' followed by a non-padding character
            "aGVsbG8=aGk=|8",       // data after padding
            "aGk|0"})               // input ends mid-quantum
    void reportsTheOffsetLikeGo(String input, long offset) {
        assertThatThrownBy(() -> GoBase64.decode(input, GoBase64.STD))
                .isInstanceOf(GoBase64.CorruptInputError.class)
                .hasMessage("illegal base64 data at input byte " + offset);
    }

    @EquivalencePartitioning
    @Test
    void urlAlphabetUsesDashAndUnderscore() throws Exception {
        assertThat(GoBase64.decode("-_8=", GoBase64.URL)).containsExactly(0xFB, 0xFF);
        assertThatThrownBy(() -> GoBase64.decode("-_8=", GoBase64.STD)).hasMessage("illegal base64 data at input byte 0");
        assertThatThrownBy(() -> GoBase64.decode("+/8=", GoBase64.URL)).hasMessage("illegal base64 data at input byte 0");
    }

    /**
     * Seeded random byte arrays of many lengths, for the round-trip property.
     */
    static Stream<Arguments> randomBytes() {
        return Generators.samples(r -> {
            byte[] b = new byte[r.nextInt(200)];
            r.nextBytes(b);
            return b;
        });
    }

    /**
     * Whatever Java's standard and MIME encoders write, this decoder reads back unchanged.
     */
    @PropertyBased
    @ParameterizedTest
    @MethodSource("randomBytes")
    void decodeInvertsStandardAndMimeEncoders(byte[] original) throws Exception {
        assertThat(GoBase64.decode(Base64.getEncoder().encodeToString(original), GoBase64.STD)).isEqualTo(original);
        assertThat(GoBase64.decode(Base64.getUrlEncoder().encodeToString(original), GoBase64.URL)).isEqualTo(original);
        // MIME output has CRLF line breaks every 76 characters; Go ignores them.
        assertThat(GoBase64.decode(Base64.getMimeEncoder().encodeToString(original), GoBase64.STD)).isEqualTo(original);
    }
}
