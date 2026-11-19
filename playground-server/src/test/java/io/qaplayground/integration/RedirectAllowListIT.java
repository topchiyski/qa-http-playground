package io.qaplayground.integration;

import io.qaplayground.testing.technique.DecisionTable;
import io.qaplayground.testing.technique.SecurityTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Open-redirect protection when an operator restricts {@code /redirect-to} with an allowlist (risk R1).
 *
 * <p>Every vector here is a URL that a browser would follow to another host. One that slipped through would turn a
 * trusted link into a phishing link.
 */
@SecurityTest
@TestPropertySource(properties = "playground.allowed-redirect-domains=example.com,example.org")
class RedirectAllowListIT extends ServerIT {

    /**
     * Each target is allowed or refused as the decision table says.
     */
    @DecisionTable
    @ParameterizedTest(name = "url={0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "http://example.com/x|302",
            "https://example.org|302",
            "http://evil.com|403",
            "//evil.com/x|403",          // scheme-relative
            "///evil.com|403",           // three slashes: no host for Go, evil.com for browsers (hardening)
            "https:evil.com|403",        // opaque: no host for Go, https://evil.com for browsers (hardening)
            "http://example.com.evil.com/|403",
            "http://evil.com#@example.com|403",
            "http://example.com@evil.com/|403",   // userinfo trick: the host is evil.com
            "/get|302",
            "/\\evil.com|302"})          // allowed, but Location is escaped to /%5Cevil.com (see below)
    void allowListDecisions(String target, int status) {
        Res r = get("/redirect-to?url=" + java.net.URLEncoder.encode(target, java.nio.charset.StandardCharsets.UTF_8));
        assertThat(r.status()).isEqualTo(status);
        if (status == 403) {
            assertThat(r.body()).contains("Allowed redirect destinations:\n- example.com\n- example.org");
            assertThat(r.header("Location")).isNull();
        }
    }

    /**
     * An allowed target is written into {@code Location} exactly as Go writes it.
     */
    @ParameterizedTest(name = "url={0} -> Location {1}")
    @CsvSource(delimiter = '|', value = {
            "/\\evil.com|/%5Cevil.com",
            "/a b|/a%20b",
            "http://example.com/p?q=1|http://example.com/p?q=1"})
    void locationIsReSerialisedLikeGo(String target, String location) {
        Res r = get("/redirect-to?url=" + java.net.URLEncoder.encode(target, java.nio.charset.StandardCharsets.UTF_8));
        assertThat(r.header("Location")).isEqualTo(location);
    }
}
