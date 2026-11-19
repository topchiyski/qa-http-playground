package io.qaplayground.server.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP Digest authentication (RFC 2617 / RFC 7616 style), written by hand so you can read every step.
 *
 * <p>It is a port of go-httpbin's {@code digest} package, simplifications included, so it accepts exactly what
 * go-httpbin accepts.
 *
 * <h2>How Digest works</h2>
 * <ol>
 *   <li>The client asks without credentials and gets 401 with a challenge:
 *       {@code WWW-Authenticate: Digest qop=auth, realm="go-httpbin", algorithm=MD5, nonce=..., opaque=...}</li>
 *   <li>The client computes, with H = MD5 or SHA-256:
 *       <pre>
 * HA1      = H(username:realm:password)
 * HA2      = H(method:uri)
 * response = H(HA1:nonce:nc:cnonce:qop:HA2)      (qop=auth)
 * response = H(HA1:nonce:HA2)                    (no qop, legacy RFC 2069)</pre></li>
 *   <li>It retries with {@code Authorization: Digest username="...", realm="...", nonce="...",
 *       uri="...", qop=auth, nc=00000001, cnonce="...", response="...", opaque="..."}.</li>
 *   <li>The server repeats the calculation with the password it knows and compares.</li>
 * </ol>
 * The password itself never crosses the wire. That is the whole idea.
 *
 * <h2>Simplifications (same as the reference)</h2>
 * The server is stateless. It doesn't remember nonces, so it can't detect replays, stale nonces or wrong
 * {@code nc} counters, and it trusts the realm and algorithm the client sends. {@code -sess} algorithms and
 * {@code userhash} are not supported. Don't copy this into a real system: it exists to give your client something
 * to talk to.
 */
public final class DigestAuth {

    /**
     * The two hash algorithms Digest supports here, with their name in the header and in Java.
     */
    public enum Algorithm {
        MD5("MD5", "MD5"), SHA256("SHA-256", "SHA-256");

        private final String headerName;
        private final String javaName;

        Algorithm(String headerName, String javaName) {
            this.headerName = headerName;
            this.javaName = javaName;
        }

        /**
         * The name as it appears in the {@code algorithm=} field of the header, such as {@code SHA-256}.
         */
        public String headerName() {
            return headerName;
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private DigestAuth() {
    }

    /**
     * The parsed {@code Authorization: Digest ...} header.
     */
    record Credentials(Algorithm algorithm, String username, String realm, String nonce, String uri,
                       String qop, String nc, String cnonce, String response) {
    }

    /**
     * Builds the {@code WWW-Authenticate} challenge that starts the handshake.
     *
     * <p>Nonce and opaque are random MD5 hex strings whatever the algorithm, exactly as go-httpbin makes them.
     */
    public static String challenge(String realm, Algorithm algorithm) {
        byte[] entropy = new byte[32];
        RANDOM.nextBytes(entropy);
        String opaque = hash(java.util.Arrays.copyOfRange(entropy, 0, 16), Algorithm.MD5);
        String nonceSeed = Instant.now() + ":" + HexFormat.of().formatHex(entropy, 16, 31);
        String nonce = hash(nonceSeed.getBytes(StandardCharsets.UTF_8), Algorithm.MD5);
        String safeRealm = realm.replace("\"", "").replace(",", "");
        return "Digest qop=auth, realm=\"" + safeRealm + "\", algorithm=" + algorithm.headerName()
                + ", nonce=" + nonce + ", opaque=" + opaque;
    }

    /**
     * Whether the {@code Authorization} header proves that the client knows {@code password}.
     *
     * @param requestUri the raw request target (path and query, as sent)
     */
    public static boolean check(String authorizationHeader, String method, String requestUri,
                                String username, String password) {
        Credentials credentials = parse(authorizationHeader);
        if (credentials == null || !credentials.username().equals(username)) {
            return false;
        }
        String expected = expectedResponse(credentials, password, method, requestUri);
        // Constant-time comparison avoids leaking how many leading characters matched.
        return MessageDigest.isEqual(credentials.response().getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The {@code response} value a client that knows the password would send, calculated as in the class comment.
     */
    static String expectedResponse(Credentials c, String password, String method, String uri) {
        String ha1 = hash(c.username() + ":" + c.realm() + ":" + password, c.algorithm());
        String ha2 = hash(method + ":" + uri, c.algorithm());
        String toHash = "auth".equals(c.qop()) || "auth-int".equals(c.qop())
                ? String.join(":", ha1, c.nonce(), c.nc(), c.cnonce(), c.qop(), ha2)
                : String.join(":", ha1, c.nonce(), ha2);
        return hash(toHash, c.algorithm());
    }

    /**
     * Parses the fields of a Digest header. Returns null unless the header starts with exactly {@code "Digest "}.
     */
    static Credentials parse(String header) {
        if (header == null || header.isEmpty()) {
            return null;
        }
        String[] parts = header.split(" ", 2);
        if (parts.length != 2 || !parts[0].equals("Digest")) {
            return null;
        }
        Map<String, String> d = parseDictHeader(parts[1]);
        Algorithm algorithm = "sha-256".equalsIgnoreCase(d.get("algorithm")) ? Algorithm.SHA256 : Algorithm.MD5;
        return new Credentials(algorithm, d.getOrDefault("username", ""), d.getOrDefault("realm", ""),
                d.getOrDefault("nonce", ""), d.getOrDefault("uri", ""), d.getOrDefault("qop", ""),
                d.getOrDefault("nc", ""), d.getOrDefault("cnonce", ""), d.getOrDefault("response", ""));
    }

    /**
     * Splits {@code a="1", b=2} into a map.
     *
     * <p>It is as simple as the reference's parser, on purpose: values that contain commas are not supported.
     */
    static Map<String, String> parseDictHeader(String value) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : value.split(",")) {
            String[] kv = pair.trim().split("=", 2);
            String key = kv[0].trim();
            if (key.isEmpty()) {
                continue;
            }
            String v = kv.length > 1 ? kv[1].trim() : "";
            if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
                v = v.substring(1, v.length() - 1);
            }
            result.put(key, v);
        }
        return result;
    }

    /**
     * The hex digest of a string's UTF-8 bytes.
     */
    static String hash(String data, Algorithm algorithm) {
        return hash(data.getBytes(StandardCharsets.UTF_8), algorithm);
    }

    /**
     * The lower-case hex digest of {@code data} with the given algorithm.
     */
    static String hash(byte[] data, Algorithm algorithm) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm.javaName).digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
