package io.qaplayground.server.support;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.qaplayground.testing.technique.EquivalencePartitioning;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Go's cookie rules, without a server: how {@code Set-Cookie} is written and how {@code Cookie} is read.
 *
 * <p>Expected values follow Go's {@code net/http} cookie code ({@code (*Cookie).String}, {@code sanitizeCookieValue},
 * {@code readCookies}), which go-httpbin v2.26.0 uses for {@code /cookies}.
 */
class CookieCodecTest {

    private static final Instant NOW = Instant.parse("2001-02-03T04:05:06Z");

    private static final CookieCodec.Attributes DEFAULTS = CookieCodec.parseAttributes(Map.of(), false);

    @Test
    void defaultsArePathRootAndHttpOnly() {
        assertThat(CookieCodec.serialize("k", "v", DEFAULTS, false, NOW)).isEqualTo("k=v; Path=/; HttpOnly");
    }

    @Test
    void secureByDefaultOverHttps() {
        var attrs = CookieCodec.parseAttributes(Map.of(), true);
        assertThat(CookieCodec.serialize("k", "v", attrs, false, NOW)).isEqualTo("k=v; Path=/; HttpOnly; Secure");
    }

    @Test
    void attributeParametersOverrideTheDefaults() {
        var attrs = CookieCodec.parseAttributes(Map.of(
                "attr[Path]", List.of("/x"),
                "attr[Domain]", List.of(".example.com"),
                "attr[HttpOnly]", List.of("false"),
                "attr[Secure]", List.of("1"),
                "attr[SameSite]", List.of("lax")), false);

        // Go drops the leading dot of a domain.
        assertThat(CookieCodec.serialize("k", "v", attrs, false, NOW))
                .isEqualTo("k=v; Path=/x; Domain=example.com; Secure; SameSite=Lax");
    }

    @EquivalencePartitioning
    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "plain|plain",
            "a b|\"a b\"",           // a space or a comma makes Go quote the value
            "a,b|\"a,b\"",
            "a;b\\c\"d|abcd",        // ; \ and " are dropped
    })
    void valuesAreSanitizedLikeGo(String value, String expected) {
        assertThat(CookieCodec.serialize("k", value, DEFAULTS, false, NOW)).startsWith("k=" + expected + ";");
    }

    @Test
    void anInvalidNameSetsNothing() {
        assertThat(CookieCodec.serialize("bad name", "v", DEFAULTS, false, NOW)).isNull();
        assertThat(CookieCodec.serialize("", "v", DEFAULTS, false, NOW)).isNull();
    }

    @Test
    void anInvalidDomainIsLeftOut() {
        var attrs = CookieCodec.parseAttributes(Map.of("attr[Domain]", List.of("bad_domain!")), false);
        assertThat(CookieCodec.serialize("k", "v", attrs, false, NOW)).isEqualTo("k=v; Path=/; HttpOnly");
    }

    /**
     * Go's {@code deleteCookies} sets {@code Expires} to 365 days before now, to the second, plus {@code Max-Age=0}.
     */
    @Test
    void deletingExpiresTheCookieAYearAgo() {
        assertThat(CookieCodec.serialize("k", "", DEFAULTS, true, Instant.parse("2001-02-03T04:05:06.789Z")))
                .isEqualTo("k=; Path=/; Expires=Fri, 04 Feb 2000 04:05:06 GMT; Max-Age=0; HttpOnly");
    }

    @Test
    void attributeParametersAreNotCookies() {
        assertThat(CookieCodec.isAttrParam("attr[Path]")).isTrue();
        assertThat(CookieCodec.isAttrParam("attr")).isFalse();
        assertThat(CookieCodec.isAttrParam("path")).isFalse();
    }

    @Test
    void readingSkipsInvalidPairsAndUnquotes() {
        Map<String, String> cookies = new TreeMap<>();
        CookieCodec.parseCookieHeader("a=1; b=\"two\"; bad name=3; c=x\\y;; d", cookies);
        assertThat(cookies).containsExactly(Map.entry("a", "1"), Map.entry("b", "two"), Map.entry("d", ""));
    }
}
