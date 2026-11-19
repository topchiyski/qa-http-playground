package io.qaplayground.server.support;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
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
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Byte-for-byte compatibility with Go's {@code encoding/json}.
 *
 * <p>Learners diff responses against other httpbin servers and write assertions on exact bodies (risk R9). A
 * different number format or field order would break those tests for no good reason.
 */
class GoJsonTest {

    /**
     * An independent JSON parser, used to check that the output means what it should.
     */
    private static final JsonMapper JACKSON = JsonMapper.builder().build();

    @EquivalencePartitioning
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "1.0|1", "0.5|0.5", "-2.25|-2.25", "100000000000000000000|100000000000000000000",
            "1e21|1e+21", "1.5e300|1.5e+300", "0.000001|0.000001", "1e-7|1e-7", "-1.25e-10|-1.25e-10",
            "0.1|0.1", "123456789.125|123456789.125"})
    void floatsUseGosShortestFormat(double value, String expected) {
        assertThat(GoJson.formatFloat(value)).isEqualTo(expected);
    }

    /**
     * {@code -0.0} prints as {@code -0}, as in Go.
     */
    @Test
    void negativeZeroKeepsItsSign() {
        assertThat(GoJson.formatFloat(-0.0)).isEqualTo("-0");
        assertThat(GoJson.formatFloat(0.0)).isEqualTo("0");
    }

    /**
     * NaN and infinity are not JSON. Go refuses them, and so does this encoder.
     */
    @ErrorGuessing
    @Test
    void nanAndInfinityAreUnsupportedLikeGo() {
        assertThatThrownBy(() -> GoJson.compact(Double.NaN)).hasMessageContaining("json: unsupported value");
        assertThatThrownBy(() -> GoJson.compact(Double.POSITIVE_INFINITY)).hasMessageContaining("json: unsupported value");
    }

    /**
     * A type the encoder doesn't know fails at once, instead of being written as something surprising.
     */
    @ErrorGuessing
    @Test
    void unsupportedTypesFailLoudly() {
        assertThatThrownBy(() -> GoJson.compact(new Object())).isInstanceOf(IllegalArgumentException.class);
    }

    @EquivalencePartitioning
    @Test
    void stringEscaping() {
        assertThat(GoJson.compact("a\"b\\c\n\r\t\b\f")).isEqualTo("\"a\\\"b\\\\c\\n\\r\\t\\b\\f\"");
        assertThat(GoJson.compact("\u0001\u001f")).isEqualTo("\"\\u0001\\u001f\"");
        assertThat(GoJson.compact("\u2028\u2029")).isEqualTo("\"\\u2028\\u2029\"");
        assertThat(GoJson.compact("😀é")).isEqualTo("\"😀é\"");
    }

    @EquivalencePartitioning
    @Test
    void htmlCharactersAreEscapedOnlyInCompactOutput() {
        // json.Marshal escapes <, >, & ; the indenting encoder go-httpbin uses for bodies does not.
        assertThat(GoJson.compact("<a&b>")).isEqualTo("\"\\u003ca\\u0026b\\u003e\"");
        assertThat(new String(GoJson.pretty("<a&b>"), StandardCharsets.UTF_8)).isEqualTo("\"<a&b>\"\n");
    }

    /**
     * Half of a UTF-16 surrogate pair becomes U+FFFD, as Go's encoder replaces invalid UTF-8.
     */
    @ErrorGuessing
    @Test
    void loneSurrogatesBecomeTheReplacementCharacter() {
        assertThat(GoJson.compact("a\uD800b")).isEqualTo("\"a\\ufffdb\"");
        assertThat(GoJson.compact("\uDC00")).isEqualTo("\"\\ufffd\"");
    }

    @EquivalencePartitioning
    @Test
    void sequencedMapsKeepOrderOtherMapsAreSorted() {
        Map<String, Object> ordered = new LinkedHashMap<>();
        ordered.put("b", 1);
        ordered.put("a", 2);
        Map<String, Object> unordered = new HashMap<>(Map.of("b", 1, "a", 2, "c", 3));
        assertThat(GoJson.compact(ordered)).isEqualTo("{\"b\":1,\"a\":2}");
        assertThat(GoJson.compact(unordered)).isEqualTo("{\"a\":2,\"b\":1,\"c\":3}");
    }

    @Test
    void prettyPrintingMatchesTheGoEncoder() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("list", List.of(1, "x", true));
        value.put("empty", Map.of());
        value.put("none", List.of());
        value.put("null", null);
        value.put("array", new Object[]{(short) 1, (byte) 2, 3L});
        assertThat(new String(GoJson.pretty(value), StandardCharsets.UTF_8)).isEqualTo("""
                {
                  "list": [
                    1,
                    "x",
                    true
                  ],
                  "empty": {},
                  "none": [],
                  "null": null,
                  "array": [
                    1,
                    2,
                    3
                  ]
                }
                """);
    }

    // --- property: output is valid JSON denoting the same value -------------------------------

    /**
     * Seeded random JSON values, nested a few levels deep.
     */
    static Stream<Arguments> randomValues() {
        return Generators.samples(r -> randomValue(r, 3));
    }

    /**
     * One random JSON value: a scalar, a list or a map, up to {@code depth} levels deep.
     */
    private static Object randomValue(Random r, int depth) {
        int kind = depth == 0 ? r.nextInt(5) : r.nextInt(7);
        return switch (kind) {
            case 0 -> Generators.anyString(r, 10);
            case 1 -> (long) r.nextInt();
            case 2 -> r.nextBoolean();
            case 3 -> null;
            case 4 -> r.nextDouble() * Math.pow(10, r.nextInt(-12, 30)) * (r.nextBoolean() ? 1 : -1);
            case 5 -> {
                List<Object> list = new ArrayList<>();
                for (int i = r.nextInt(4); i > 0; i--) {
                    list.add(randomValue(r, depth - 1));
                }
                yield list;
            }
            default -> {
                Map<String, Object> map = new TreeMap<>();
                for (int i = r.nextInt(4); i > 0; i--) {
                    map.put(Generators.anyString(r, 5), randomValue(r, depth - 1));
                }
                yield map;
            }
        };
    }

    /**
     * Whatever the encoder writes, an independent parser reads back as the same value.
     */
    @PropertyBased
    @ParameterizedTest
    @MethodSource("randomValues")
    void outputParsesBackToTheSameValue(Object value) {
        Object compact = JACKSON.readValue(GoJson.compact(value), Object.class);
        Object pretty = JACKSON.readValue(GoJson.pretty(value), Object.class);
        assertThat(normalise(compact)).isEqualTo(normalise(value));
        assertThat(normalise(pretty)).isEqualTo(normalise(value));
    }

    /**
     * Makes numbers compare by value, because Jackson may read 1e21 as a double and 5 as an int.
     */
    private static Object normalise(Object v) {
        return switch (v) {
            case Number n -> n.doubleValue();
            case List<?> l -> l.stream().map(GoJsonTest::normalise).toList();
            case Map<?, ?> m -> {
                Map<String, Object> out = new TreeMap<>();
                m.forEach((k, x) -> out.put((String) k, normalise(x)));
                yield out;
            }
            case null, default -> v;
        };
    }
}
