package io.qaplayground.server.support;

import io.qaplayground.testing.technique.EquivalencePartitioning;
import io.qaplayground.testing.technique.ErrorGuessing;
import io.qaplayground.testing.technique.SecurityTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The part of Go's {@code url.Parse} that {@code /redirect-to} uses to decide whether a target is allowed.
 *
 * <p>A mistake here is an open redirect (risk R1). Each row is a class of URL a tester would try.
 */
class GoUrlTest {

    @EquivalencePartitioning
    @ParameterizedTest(name = "{0} -> scheme \"{1}\", host \"{2}\", path \"{3}\", absolute {4}")
    @CsvSource(delimiter = '|', value = {
            "http://example.com/x|http|example.com|/x|true",
            "HTTPS://Example.com|https|Example.com|''|true",          // scheme lower-cased, host kept
            "http://example.com:8080/a?b=c#d|http|example.com|/a|true",
            "http://user:pw@host/p|http|host|/p|true",
            "http://[::1]:8080/x|http|::1|/x|true",
            "mailto:someone@example.com|mailto|''|''|true",           // opaque URL
            "/get|''|''|/get|false",                                  // root-relative
            "get|''|''|get|false",                                    // relative
            "//evil.com|''|evil.com|''|false",                        // scheme-relative, no path
            "//evil.com/x|''|evil.com|/x|false",                      // scheme-relative with path
            "///evil.com|''|''|///evil.com|false",                    // three slashes: no authority in Go
            "http:/path|http|''|/path|true",                          // scheme with single slash: empty host
            "a/b:c|''|''|a/b:c|false"})                               // colon allowed after the first segment
    void parsesTheUrlClasses(String raw, String scheme, String host, String path, boolean absolute) {
        GoUrl url = GoUrl.parse(raw);
        assertThat(url.scheme()).isEqualTo(scheme);
        assertThat(url.hostname()).isEqualTo(host);
        assertThat(url.path()).isEqualTo(path);
        assertThat(url.isAbsolute()).isEqualTo(absolute);
    }

    @ErrorGuessing
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "http://foo%%bar|parse \"http://foo%%bar\": invalid URL escape \"%%b\"",
            "/a%zz|parse \"/a%zz\": invalid URL escape \"%zz\"",
            ":foo|parse \":foo\": missing protocol scheme",
            "http://[::1/x|parse \"http://[::1/x\": missing ']' in host",
            // Regressions found while reading net/url for the Location fix: Go rejects these.
            "1http://x|parse \"1http://x\": first path segment in URL cannot contain colon",
            "/a#%zz|parse \"/a#%zz\": invalid URL escape \"%zz\""})
    void reportsGoErrors(String raw, String message) {
        assertThatThrownBy(() -> GoUrl.parse(raw)).isInstanceOf(GoUrl.UrlError.class).hasMessage(message);
    }

    /**
     * {@code Location} is written exactly as Go's {@code URL.String()} writes it.
     */
    @SecurityTest
    @EquivalencePartitioning
    @ParameterizedTest(name = "{0} -> Location {1}")
    @CsvSource(delimiter = '|', value = {
            "/get|/get",
            "http://example.com/a?b=c#d|http://example.com/a?b=c#d",
            "/a b|/a%20b",                                  // invalid raw path is re-escaped
            "/\\evil.com|/%5Cevil.com",                      // backslash escaped: stays on this host
            "/a%2Fb|/a%2Fb",                                 // valid escapes are kept as sent
            "/a%20b c|/a%20b%20c",                           // mixed: decoded, then escaped
            "/é|/%C3%A9",
            "/a?|/a?",                                       // ForceQuery keeps the bare '?'
            "/a?x=<y>|/a?x=<y>",                             // the query is sent raw
            "/a#frag ment|/a#frag%20ment",
            "/a#!()*|/a#!()*",                               // fragment sub-delims stay
            "mailto:a@b.c|mailto:a@b.c",                     // opaque
            "http:/path|http:/path",                         // empty host stays omitted
            "///evil.com|///evil.com",                       // same as Go (see allow-list test)
            "HTTP://Example.com/X|http://Example.com/X"})    // scheme lower-cased
    void serialisesLikeGoUrlString(String raw, String location) {
        assertThat(GoUrl.serialize(raw)).isEqualTo(location);
    }

    /**
     * Control characters in a URL are refused, as Go refuses them.
     */
    @SecurityTest
    @ErrorGuessing
    @ParameterizedTest(name = "control character U+{0}")
    @CsvSource({"0000", "000A", "000D", "007F"})
    void rejectsControlCharacters(String hex) {
        String raw = "http://a" + (char) Integer.parseInt(hex, 16) + "b/";
        assertThatThrownBy(() -> GoUrl.parse(raw)).hasMessageEndingWith("net/url: invalid control character in URL");
    }
}
