package io.qaplayground.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.qaplayground.api.support.PlaygroundTest;
import io.qaplayground.testing.technique.DecisionTable;
import io.qaplayground.testing.technique.EquivalencePartitioning;
import io.qaplayground.testing.technique.ErrorGuessing;
import io.qaplayground.testing.technique.StateTransition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

/**
 * Tutorial: authentication schemes.
 *
 * <p>The key idea: REST Assured's {@code auth().basic(..)} is <em>challenge-driven</em>. It sends credentials only
 * after the server answers 401 with {@code WWW-Authenticate}. {@code auth().preemptive().basic(..)} sends them with
 * the first request. The hidden-basic-auth endpoint shows why the difference matters.
 */
class AuthenticationTest extends PlaygroundTest {

    // ---- Basic ---------------------------------------------------------------------------

    /**
     * Preemptive Basic auth: the credentials go out with the first request, and the server lets you in.
     */
    @Test
    void basicAuthPreemptive() {
        given()
            .auth().preemptive().basic("user", "passwd")
        .when()
            .get("/basic-auth/user/passwd")
        .then()
            .statusCode(200)
            .body("authenticated", equalTo(true))
            .body("user", equalTo("user"));
    }

    /**
     * Challenge-driven: 401 first, then REST Assured retries with credentials.
     */
    @Test
    void basicAuthChallengeResponse() {
        given()
            .auth().basic("user", "passwd")
        .when()
            .get("/basic-auth/user/passwd")
        .then()
            .statusCode(200);
    }

    /**
     * A wrong password gets 401, with the challenge that tells a browser to ask again.
     */
    @DecisionTable
    @Test
    void basicAuthWrongPasswordIs401WithChallenge() {
        given()
            .auth().preemptive().basic("user", "wrong")
        .when()
            .get("/basic-auth/user/passwd")
        .then()
            .statusCode(401)
            .header("WWW-Authenticate", "Basic realm=\"Fake Realm\"")
            .body("authenticated", equalTo(false))
            .body("user", equalTo("user"));
    }

    /**
     * You can always build the header yourself: it is just base64("user:password").
     */
    @Test
    void basicAuthByHand() {
        String token = java.util.Base64.getEncoder().encodeToString("user:passwd".getBytes(StandardCharsets.UTF_8));
        given()
            .header("Authorization", "Basic " + token)
        .when()
            .get("/basic-auth/user/passwd")
        .then()
            .statusCode(200);
    }

    /**
     * The hidden variant lets you in, as long as the credentials arrive with the first request.
     */
    @Test
    void hiddenBasicAuthWorksPreemptively() {
        given()
            .auth().preemptive().basic("user", "passwd")
        .when()
            .get("/hidden-basic-auth/user/passwd")
        .then()
            .statusCode(200)
            .body("authenticated", equalTo(true));
    }

    /**
     * No 401 challenge means a challenge-driven client never sends credentials: 404.
     */
    @DecisionTable
    @Test
    void hiddenBasicAuthDefeatsChallengeDrivenClients() {
        given()
            .auth().basic("user", "passwd")
        .when()
            .get("/hidden-basic-auth/user/passwd")
        .then()
            .statusCode(404)
            .body("status_code", equalTo(404))
            .body("error", equalTo("Not Found"));
    }

    // ---- Bearer --------------------------------------------------------------------------

    @Test
    void bearerTokenIsEchoed() {
        given()
            .auth().oauth2("abc.def.ghi")
        .when()
            .get("/bearer")
        .then()
            .statusCode(200)
            .body("authenticated", equalTo(true))
            .body("token", equalTo("abc.def.ghi"));
    }

    /**
     * A malformed bearer header is refused like a missing one.
     */
    @ErrorGuessing
    @ParameterizedTest(name = "Authorization: \"{0}\" is 401")
    @ValueSource(strings = {"Bearer", "Bearer x y", "bearer x", "Bearer1 x", "xBearer x", "Basic dXNlcjpwYXNz"})
    void malformedBearerIs401(String header) {
        given()
            .header("Authorization", header)
        .when()
            .get("/bearer")
        .then()
            .statusCode(401)
            .header("WWW-Authenticate", "Bearer");
    }

    @Test
    void missingBearerIs401() {
        when().get("/bearer").then().statusCode(401).body("error", equalTo("Unauthorized"));
    }

    // ---- Digest --------------------------------------------------------------------------

    /**
     * REST Assured handles the MD5 Digest handshake for you.
     */
    @StateTransition
    @Test
    void digestMd5WithRestAssured() {
        given()
            .auth().digest("user", "passwd")
        .when()
            .get("/digest-auth/auth/user/passwd")
        .then()
            .statusCode(200)
            .body("authenticated", equalTo(true))
            .body("user", equalTo("user"));
    }

    /**
     * The challenge names the algorithm, so a client knows how to answer before it computes anything.
     */
    @Test
    void digestChallengeNamesTheAlgorithm() {
        when()
            .get("/digest-auth/auth/user/passwd/SHA-256")
        .then()
            .statusCode(401)
            .header("WWW-Authenticate", startsWith("Digest qop=auth, realm=\"go-httpbin\", algorithm=SHA-256, nonce="));
    }

    /**
     * Digest by hand, so every step is visible. It works for MD5 and SHA-256.
     *
     * <p>Many clients only implement MD5. This is how you test SHA-256 anyway.
     */
    @StateTransition
    @ParameterizedTest(name = "digest by hand: {0}")
    @ValueSource(strings = {"MD5", "SHA-256"})
    void digestByHand(String algorithm) throws Exception {
        String path = "/digest-auth/auth/user/passwd/" + algorithm;

        // 1. Ask without credentials and read the challenge.
        String challenge = when().get(path).then().statusCode(401).extract().header("WWW-Authenticate");
        Map<String, String> c = parseChallenge(challenge);

        // 2. Compute the response the way RFC 7616 describes.
        String javaAlgorithm = algorithm.equals("MD5") ? "MD5" : "SHA-256";
        String cnonce = "0a4f113b";
        String nc = "00000001";
        String ha1 = hex(javaAlgorithm, "user:" + c.get("realm") + ":passwd");
        String ha2 = hex(javaAlgorithm, "GET:" + path);
        String response = hex(javaAlgorithm, String.join(":", ha1, c.get("nonce"), nc, cnonce, "auth", ha2));

        // 3. Retry with the Authorization header.
        String authorization = String.format(
                "Digest username=\"user\", realm=\"%s\", nonce=\"%s\", uri=\"%s\", algorithm=%s, "
                        + "qop=auth, nc=%s, cnonce=\"%s\", response=\"%s\", opaque=\"%s\"",
                c.get("realm"), c.get("nonce"), path, algorithm, nc, cnonce, response, c.get("opaque"));
        given()
            .header("Authorization", authorization)
        .when()
            .get(path)
        .then()
            .statusCode(200)
            .body("authenticated", equalTo(true));
    }

    /**
     * An unsupported qop or algorithm in the URL is a client error, 400, before any handshake starts.
     */
    @ErrorGuessing
    @Test
    void digestRejectsUnsupportedQopAndAlgorithm() {
        when().get("/digest-auth/bad-qop/user/passwd/MD5").then().statusCode(400)
                .body("detail", equalTo("invalid QOP directive: \"bad-qop\" != \"auth\""));
        when().get("/digest-auth/auth/user/passwd/SHA-512").then().statusCode(400)
                .body("detail", equalTo("invalid algorithm: SHA-512 must be one of MD5 or SHA-256"));
    }

    /**
     * A digest URL with a segment missing doesn't match a route, so it is 404.
     */
    @EquivalencePartitioning
    @Test
    void digestRoutesNeedAllSegments() {
        when().get("/digest-auth/user/passwd").then().statusCode(404);
        when().get("/digest-auth/auth/user/passwd/MD5/extra").then().statusCode(404);
    }

    /**
     * The fields of a {@code WWW-Authenticate: Digest ...} header, without their quotes.
     */
    private static Map<String, String> parseChallenge(String header) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher m = Pattern.compile("(\\w+)=(\"([^\"]*)\"|([^,\\s]+))").matcher(header);
        while (m.find()) {
            result.put(m.group(1), m.group(3) != null ? m.group(3) : m.group(4));
        }
        return result;
    }

    /**
     * The hex digest of {@code data} with the named algorithm.
     */
    private static String hex(String algorithm, String data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(data.getBytes(StandardCharsets.UTF_8)));
    }
}
