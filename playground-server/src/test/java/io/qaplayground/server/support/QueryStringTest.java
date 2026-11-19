package io.qaplayground.server.support;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
import static org.assertj.core.api.Assertions.entry;

/**
 * {@code url.ParseQuery} rules. The {@code args} object of every echo endpoint depends on them.
 */
class QueryStringTest {

    @EquivalencePartitioning
    @Test
    void repeatedKeysKeepAllValuesInOrder() {
        Map<String, List<String>> args = QueryString.parse("a=1&b=2&a=3");
        assertThat(args).containsExactly(entry("a", List.of("1", "3")), entry("b", List.of("2")));
    }

    @EquivalencePartitioning
    @ParameterizedTest(name = "\"{0}\" -> key \"{1}\", value \"{2}\"")
    @CsvSource(delimiter = '|', value = {
            "a|a|''",                 // no '=': empty value
            "a=|a|''",
            "=1|''|1",                // empty key is allowed
            "a+b=c+d|a b|c d",        // '+' is a space
            "a=b=c|a|b=c",            // only the first '=' splits
            "%E2%82%AC=%F0%9F%98%80|€|😀"})
    void parsesSinglePairs(String query, String key, String value) {
        assertThat(QueryString.parse(query)).containsExactly(entry(key, List.of(value)));
    }

    @ErrorGuessing
    @ParameterizedTest(name = "\"{0}\" keeps only b")
    @CsvSource(delimiter = '|', value = {
            "a=%zz&b=1",   // bad escape: guessing the pair is skipped, not the whole query
            "a=%&b=1",
            "a=%4&b=1",
            "a=1;c=2&b=1", // ';' separators are rejected since Go 1.17
            "&&b=1&"})     // empty pairs
    void malformedPairsAreSkipped(String query) {
        assertThat(QueryString.parse(query)).containsOnlyKeys("b");
    }

    @Test
    void nullAndEmptyAreEmpty() {
        assertThat(QueryString.parse(null)).isEmpty();
        assertThat(QueryString.parse("")).isEmpty();
    }

    /**
     * Form bodies report their first error with the message Go's {@code ParseForm} gives.
     */
    @ErrorGuessing
    @ParameterizedTest(name = "firstError(\"{0}\") = {1}")
    @CsvSource(delimiter = '|', value = {
            "a=%zz|invalid URL escape \"%zz\"",
            "a=%|invalid URL escape \"%\"",
            "a=%4|invalid URL escape \"%4\"",
            "%gg=1|invalid URL escape \"%gg\"",
            "a=1;b=2|invalid semicolon separator in query"})
    void firstErrorReportsLikeParseForm(String query, String error) {
        assertThat(QueryString.firstError(query)).isEqualTo(error);
    }

    @Test
    void validQueriesHaveNoError() {
        assertThat(QueryString.firstError("a=1&b=%20")).isNull();
        assertThat(QueryString.firstError(null)).isNull();
    }

    @Test
    void firstReturnsEmptyStringWhenAbsent() {
        Map<String, List<String>> args = QueryString.parse("a=1&a=2");
        assertThat(QueryString.first(args, "a")).isEqualTo("1");
        assertThat(QueryString.first(args, "missing")).isEmpty();
        assertThat(QueryString.first(Map.of("e", List.of()), "e")).isEmpty();
    }

    // --- property: parse(encode(m)) == m ------------------------------------------------------

    /**
     * Seeded random maps of names to several values, for the round-trip property.
     */
    static Stream<Arguments> randomMultimaps() {
        return Generators.samples(QueryStringTest::randomMultimap);
    }

    /**
     * One random map with repeated names and awkward characters.
     */
    private static Map<String, List<String>> randomMultimap(Random r) {
        Map<String, List<String>> m = new LinkedHashMap<>();
        int keys = r.nextInt(5);
        for (int i = 0; i < keys; i++) {
            List<String> values = new ArrayList<>();
            int n = 1 + r.nextInt(3);
            for (int j = 0; j < n; j++) {
                values.add(Generators.anyString(r, 8));
            }
            m.put(Generators.anyString(r, 6), values);
        }
        return m;
    }

    /**
     * Encode a map as a form, parse it back, and you get the same names and values in the same order.
     */
    @PropertyBased
    @ParameterizedTest
    @MethodSource("randomMultimaps")
    void parseInvertsFormEncoding(Map<String, List<String>> original) {
        List<String> pairs = new ArrayList<>();
        original.forEach((k, vs) -> vs.forEach(v -> pairs.add(enc(k) + "=" + enc(v))));
        String query = String.join("&", pairs);

        assertThat(QueryString.parse(query)).isEqualTo(original);
        assertThat(QueryString.firstError(query)).isNull();
    }

    /**
     * Form-encodes one name or value, as a browser would.
     */
    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
