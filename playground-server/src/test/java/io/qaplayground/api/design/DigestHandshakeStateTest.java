package io.qaplayground.api.design;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.qaplayground.api.support.PlaygroundTest;
import io.qaplayground.testing.technique.StateTransition;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * State transition testing of the digest handshake (docs/testing/TEST-DESIGN.md section 4.1).
 *
 * <p>Unauthenticated --request--> Challenged --valid response--> Authenticated, plus the invalid transitions. The
 * client side is written out by hand, so every state is visible.
 */
@StateTransition
class DigestHandshakeStateTest extends PlaygroundTest {

    /**
     * One {@code name=value} or {@code name="value"} field of a challenge.
     */
    private static final Pattern PARAM = Pattern.compile("(\\w+)=\"?([^\",]*)\"?");

    /**
     * Unauthenticated --GET--> Challenged: returns the challenge's fields.
     */
    private static Map<String, String> challenge(String path) {
        Response r = given().get(path);
        assertThat(r.statusCode()).isEqualTo(401);
        String header = r.header("WWW-Authenticate");
        assertThat(header).startsWith("Digest ");
        Map<String, String> params = new LinkedHashMap<>();
        Matcher m = PARAM.matcher(header.substring(7));
        while (m.find()) {
            params.put(m.group(1), m.group(2));
        }
        return params;
    }

    /**
     * The hex digest of {@code s}: H(s) in RFC 7616.
     */
    private static String h(String algorithm, String s) throws Exception {
        String java = algorithm.equals("SHA-256") ? "SHA-256" : "MD5";
        return HexFormat.of().formatHex(MessageDigest.getInstance(java).digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * The {@code Authorization} header a correct client would send in answer to this challenge.
     */
    private static String authorization(Map<String, String> c, String user, String password, String method, String uri)
            throws Exception {
        String alg = c.get("algorithm");
        String ha1 = h(alg, user + ":" + c.get("realm") + ":" + password);
        String ha2 = h(alg, method + ":" + uri);
        String response = h(alg, String.join(":", ha1, c.get("nonce"), "00000001", "0a4f113b", "auth", ha2));
        return "Digest username=\"" + user + "\", realm=\"" + c.get("realm") + "\", nonce=\"" + c.get("nonce")
                + "\", uri=\"" + uri + "\", algorithm=" + alg + ", qop=auth, nc=00000001, cnonce=\"0a4f113b\""
                + ", response=\"" + response + "\", opaque=\"" + c.get("opaque") + "\"";
    }

    @ParameterizedTest(name = "{0}: Unauthenticated -> Challenged -> Authenticated")
    @CsvSource({"MD5", "SHA-256"})
    void validTransitions(String algorithm) throws Exception {
        String path = "/digest-auth/auth/user/pass/" + algorithm;
        Map<String, String> c = challenge(path);
        assertThat(c).containsEntry("algorithm", algorithm).containsEntry("qop", "auth").containsEntry("realm", "go-httpbin");
        assertThat(c.get("nonce")).matches("[0-9a-f]{32}");

        given().header("Authorization", authorization(c, "user", "pass", "GET", path))
                .get(path).then().statusCode(200).body("authenticated", equalTo(true)).body("user", equalTo("user"));
    }

    /**
     * Challenged --wrong password--> Challenged: 401 again, with a challenge.
     */
    @Test
    @DisplayName("Challenged --wrong password--> Challenged (a new challenge, not 403)")
    void wrongPasswordStaysChallenged() throws Exception {
        String path = "/digest-auth/auth/user/pass";
        Map<String, String> c = challenge(path);
        Response r = given().header("Authorization", authorization(c, "user", "wrong", "GET", path)).get(path);
        assertThat(r.statusCode()).isEqualTo(401);
        assertThat(r.header("WWW-Authenticate")).startsWith("Digest ");
    }

    @Test
    @DisplayName("Challenged --response computed for another URI--> Challenged")
    void responseForAnotherUri() throws Exception {
        String path = "/digest-auth/auth/user/pass";
        Map<String, String> c = challenge(path);
        given().header("Authorization", authorization(c, "user", "pass", "GET", "/other"))
                .get(path).then().statusCode(401);
    }

    /**
     * The design expected 401 here; the reference source says otherwise. {@code digest.Check} uses the algorithm the
     * <em>client</em> names, so a correct MD5 response is accepted even at the SHA-256 endpoint (handlers.go,
     * DigestAuth). Pinned as reference behaviour.
     */
    @Test
    @DisplayName("Challenged --MD5 credentials to a SHA-256 endpoint--> Authenticated (client picks the algorithm, as upstream)")
    void algorithmChosenByTheClient() throws Exception {
        String path = "/digest-auth/auth/user/pass/SHA-256";
        Map<String, String> c = new LinkedHashMap<>(challenge(path));
        c.put("algorithm", "MD5");
        given().header("Authorization", authorization(c, "user", "pass", "GET", path)).get(path).then().statusCode(200);
    }

    /**
     * The reference is stateless. It doesn't remember nonces, so a nonce it never issued is accepted when the response
     * is computed correctly. Documented in docs/COMPATIBILITY.md.
     */
    @Test
    @DisplayName("Stateless server: a self-made nonce is accepted (no replay protection, as upstream)")
    void serverDoesNotTrackNonces() throws Exception {
        String path = "/digest-auth/auth/user/pass";
        Map<String, String> c = new LinkedHashMap<>(challenge(path));
        c.put("nonce", "00000000000000000000000000000000");
        given().header("Authorization", authorization(c, "user", "pass", "GET", path)).get(path).then().statusCode(200);
    }
}
