package io.qaplayground.server.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import io.qaplayground.testing.technique.DecisionTable;
import io.qaplayground.testing.technique.EquivalencePartitioning;
import io.qaplayground.testing.technique.ErrorGuessing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Digest verification rules (risk R7).
 *
 * <p>The client side is computed here from the RFC 7616 formulas with the JDK's {@code MessageDigest}, independently
 * of the code under test. A test that reused the code under test would only prove it agrees with itself.
 */
class DigestAuthRulesTest {

    /**
     * The request target every header in this class is computed for.
     */
    private static final String URI = "/digest-auth/auth/user/pass";

    /**
     * The hex digest of {@code s}, as RFC 7616 calls it: H(s).
     */
    private static String h(String algorithm, String s) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Builds an {@code Authorization} header the way a correct client would.
     */
    private static String header(String algorithmParam, String javaAlgorithm, String qop, String user, String password,
                                 String method, String uri) throws Exception {
        String realm = "go-httpbin";
        String nonce = "abc123";
        String nc = "00000001";
        String cnonce = "xyz";
        String ha1 = h(javaAlgorithm, user + ":" + realm + ":" + password);
        String ha2 = h(javaAlgorithm, method + ":" + uri);
        String response = qop.isEmpty()
                ? h(javaAlgorithm, ha1 + ":" + nonce + ":" + ha2)
                : h(javaAlgorithm, String.join(":", ha1, nonce, nc, cnonce, qop, ha2));
        return "Digest username=\"" + user + "\", realm=\"" + realm + "\", nonce=\"" + nonce + "\", uri=\"" + uri
                + "\", response=\"" + response + "\""
                + (algorithmParam.isEmpty() ? "" : ", algorithm=" + algorithmParam)
                + (qop.isEmpty() ? "" : ", qop=" + qop + ", nc=" + nc + ", cnonce=\"" + cnonce + "\"");
    }

    /**
     * A correct client is accepted, for every supported algorithm and qop.
     */
    @DecisionTable
    @ParameterizedTest(name = "algorithm={0} qop={2} -> verifies with {1}")
    @CsvSource({
            "MD5, MD5, auth",
            "'', MD5, auth",              // absent algorithm: MD5
            "SHA-256, SHA-256, auth",
            "sha-256, SHA-256, auth",     // algorithm compared case-insensitively
            "SHA-512, MD5, auth",         // unknown algorithm falls back to MD5 (reference behaviour)
            "MD5, MD5, ''",               // no qop: RFC 2069 formula
            "MD5, MD5, auth-int"})        // auth-int uses the auth formula (no body hash), like the reference
    void correctClientsVerify(String algorithmParam, String javaAlgorithm, String qop) throws Exception {
        String header = header(algorithmParam, javaAlgorithm, qop, "user", "pass", "GET", URI);
        assertThat(DigestAuth.check(header, "GET", URI, "user", "pass")).isTrue();
    }

    /**
     * Change any one input (user, password, method or URI) and the response no longer verifies.
     */
    @DecisionTable
    @ParameterizedTest(name = "wrong {0} is rejected")
    @CsvSource({
            "password, user, wrong, GET, " + URI,
            "user, other, pass, GET, " + URI,
            "method, user, pass, POST, " + URI,
            "uri, user, pass, GET, /digest-auth/auth/user/pass/MD5"})
    void anyWrongInputIsRejected(String what, String user, String password, String method, String uri) throws Exception {
        String header = header("MD5", "MD5", "auth", user, password, method, uri);
        assertThat(DigestAuth.check(header, "GET", URI, "user", "pass")).as(what).isFalse();
    }

    /**
     * Headers that aren't Digest, or barely look like it, are refused rather than half-parsed.
     */
    @ErrorGuessing
    @ParameterizedTest(name = "Authorization \"{0}\" is not Digest")
    @NullAndEmptySource
    @ValueSource(strings = {"Digest", "digest username=\"user\"", "Basic dXNlcjpwYXNz", "Bearer x", "Digest  "})
    void nonDigestHeadersAreRejected(String header) {
        assertThat(DigestAuth.check(header, "GET", URI, "user", "pass")).isFalse();
    }

    /**
     * An empty {@code response} never verifies, whatever else the header says.
     */
    @ErrorGuessing
    @Test
    void emptyResponseFieldIsRejected() {
        assertThat(DigestAuth.check("Digest username=\"user\", response=\"\"", "GET", URI, "user", "pass")).isFalse();
    }

    /**
     * The {@code a="1", b=2} syntax parses into the expected fields, quoted or not.
     */
    @EquivalencePartitioning
    @Test
    void dictHeaderParsing() {
        Map<String, String> d = DigestAuth.parseDictHeader(" a=\"1\", b=2 ,=x, c, d=\"\", e=\"x");
        assertThat(d).containsExactly(
                Map.entry("a", "1"), Map.entry("b", "2"), Map.entry("c", ""), Map.entry("d", ""), Map.entry("e", "\"x"));
    }

    /**
     * Quotes are removed from the realm, so it can't inject extra fields, and each challenge has a new nonce.
     */
    @Test
    void challengeSanitisesTheRealmAndIsFreshEachTime() {
        String c1 = DigestAuth.challenge("evil\",realm=x", DigestAuth.Algorithm.MD5);
        String c2 = DigestAuth.challenge("go-httpbin", DigestAuth.Algorithm.MD5);
        assertThat(c1).contains("realm=\"evilrealm=x\"").contains("algorithm=MD5");
        assertThat(c1.replaceAll(".*nonce=([0-9a-f]+).*", "$1")).isNotEqualTo(c2.replaceAll(".*nonce=([0-9a-f]+).*", "$1"));
    }
}
