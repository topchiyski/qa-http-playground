package io.qaplayground.server.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Digest authentication against known answers: go-httpbin's own browser capture and the RFC examples.
 *
 * <p>Known answers come from outside this project, so passing them proves the port is right, not just consistent.
 */
class DigestAuthTest {

    /**
     * The browser-captured header from go-httpbin's {@code TestDigestAuth}.
     */
    private static final String BROWSER_HEADER = String.join(", ",
            "Digest username=\"user\"", "realm=\"go-httpbin\"", "nonce=\"6fb213c6593975c877bb1247370527ad\"",
            "uri=\"/digest-auth/auth/user/pass/MD5\"", "algorithm=MD5",
            "response=\"9b7a05d78051b4f668356eedf32f55d6\"", "opaque=\"fd1c386a015a2bb7c41585f54329ce91\"",
            "qop=auth", "nc=00000001", "cnonce=\"aaab705226af5bd4\"");

    /**
     * A header a real browser sent to go-httpbin verifies here too.
     */
    @Test
    void acceptsTheReferenceBrowserCapture() {
        assertTrue(DigestAuth.check(BROWSER_HEADER, "GET", "/digest-auth/auth/user/pass/MD5", "user", "pass"));
    }

    /**
     * The same captured header fails as soon as the password, the method or the user changes.
     */
    @Test
    void rejectsWrongPasswordMethodOrUser() {
        assertFalse(DigestAuth.check(BROWSER_HEADER, "GET", "/digest-auth/auth/user/pass/MD5", "user", "nope"));
        assertFalse(DigestAuth.check(BROWSER_HEADER, "POST", "/digest-auth/auth/user/pass/MD5", "user", "pass"));
        assertFalse(DigestAuth.check(BROWSER_HEADER, "GET", "/digest-auth/auth/user/pass/MD5", "other", "pass"));
        assertFalse(DigestAuth.check("Basic dXNlcjpwYXNz", "GET", "/", "user", "pass"));
    }

    /**
     * The RFC 2617 section 3.5 example ("Mufasa").
     */
    @Test
    void rfc2617Example() {
        var c = new DigestAuth.Credentials(DigestAuth.Algorithm.MD5, "Mufasa", "testrealm@host.com",
                "dcd98b7102dd2f0e8b11d0f600bfb0c093", "/dir/index.html", "auth", "00000001", "0a4f113b", "");
        assertEquals("6629fae49393a05397450978507c4ef1",
                DigestAuth.expectedResponse(c, "Circle Of Life", "GET", "/dir/index.html"));
    }

    /**
     * The RFC 7616 section 3.9.1 SHA-256 example.
     */
    @Test
    void rfc7616Sha256Example() {
        var c = new DigestAuth.Credentials(DigestAuth.Algorithm.SHA256, "Mufasa", "http-auth@example.org",
                "7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v", "/dir/index.html", "auth", "00000001",
                "f2/wE4q74E6zIJEtWaHKaf5wv/H5QzzpXusqGemxURZJ", "");
        assertEquals("753927fa0e85d155564e2e272a28d1802ca10daf4496794697cf8db5856cb6c1",
                DigestAuth.expectedResponse(c, "Circle of Life", "GET", "/dir/index.html"));
    }

    /**
     * The challenge has the fields and the shape go-httpbin sends.
     */
    @Test
    void challengeFormat() {
        String challenge = DigestAuth.challenge("go-httpbin", DigestAuth.Algorithm.SHA256);
        assertTrue(challenge.matches(
                "Digest qop=auth, realm=\"go-httpbin\", algorithm=SHA-256, nonce=[0-9a-f]{32}, opaque=[0-9a-f]{32}"),
                challenge);
    }
}
