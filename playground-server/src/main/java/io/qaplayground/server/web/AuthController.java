package io.qaplayground.server.web;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import io.qaplayground.server.auth.DigestAuth;
import io.qaplayground.server.support.GoStrconv;
import io.qaplayground.server.support.HttpbinResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication schemes: Basic, hidden Basic, Bearer and Digest.
 *
 * <p>The expected credentials are part of the URL. So you can test the happy path, and every way of getting it
 * wrong, without any setup.
 */
@Tag(name = "Auth", description = "Basic, hidden basic, bearer and digest authentication")
@RestController
public class AuthController {

    /**
     * The same realm as go-httpbin. Clients use whatever realm the challenge names.
     */
    private static final String DIGEST_REALM = "go-httpbin";

    /**
     * HTTP Basic auth against the {@code user} and {@code password} in the path.
     *
     * <p>Wrong or missing credentials get 401 with a {@code WWW-Authenticate: Basic} challenge.
     *
     * <p><b>HTTP concept:</b> Basic auth is just {@code base64(user:password)}. That is encoding, not encryption.
     * Decode the header yourself and see.
     */
    @Operation(summary = "HTTP Basic auth (401 + challenge on failure)")
    @RequestMapping("/basic-auth/{user}/{password}")
    public ResponseEntity<byte[]> basicAuth(@PathVariable String user, @PathVariable String password,
                                            HttpServletRequest request) {
        String[] given = basicCredentials(request.getHeader("Authorization"));
        boolean ok = given != null && given[0].equals(user) && given[1].equals(password);
        ResponseEntity<byte[]> response = HttpbinResponses.json(ok ? 200 : 401,
                authBody(ok, given == null ? "" : given[0]));
        if (ok) {
            return response;
        }
        return ResponseEntity.status(401)
                .header("WWW-Authenticate", "Basic realm=\"Fake Realm\"")
                .headers(response.getHeaders())
                .body(response.getBody());
    }

    /**
     * Like {@code /basic-auth}, but failure is <b>404</b> and there is no challenge.
     *
     * <p><b>HTTP concept:</b> hiding that a resource exists from anyone who isn't logged in. Clients that send
     * credentials only after a 401 challenge (non-preemptive auth) never get in.
     */
    @Operation(summary = "Basic auth that answers 404 instead of 401")
    @RequestMapping("/hidden-basic-auth/{user}/{password}")
    public ResponseEntity<byte[]> hiddenBasicAuth(@PathVariable String user, @PathVariable String password,
                                                  HttpServletRequest request) {
        String[] given = basicCredentials(request.getHeader("Authorization"));
        boolean ok = given != null && given[0].equals(user) && given[1].equals(password);
        if (!ok) {
            return HttpbinResponses.error(404, null);
        }
        return HttpbinResponses.json(authBody(true, given[0]));
    }

    /**
     * Accepts any token in {@code Authorization: Bearer <token>} and echoes it back.
     *
     * <p>The scheme name is case-sensitive here, and exactly one token is required.
     *
     * <p><b>HTTP concept:</b> bearer tokens, as in OAuth 2 and JWT. Practise taking a token from one response and
     * sending it with the next request.
     */
    @Operation(summary = "Bearer token auth")
    @RequestMapping("/bearer")
    public ResponseEntity<byte[]> bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        String[] fields = header == null ? new String[0] : header.trim().split("\\s+");
        if (fields.length != 2 || !fields[0].equals("Bearer")) {
            return ResponseEntity.status(401)
                    .header("WWW-Authenticate", "Bearer")
                    .header("Content-Type", HttpbinResponses.JSON)
                    .body(HttpbinResponses.error(401, null).getBody());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authenticated", true);
        body.put("token", fields[1]);
        return HttpbinResponses.json(body);
    }

    /**
     * HTTP Digest auth with {@code qop=auth} and the MD5 algorithm.
     *
     * <p><b>HTTP concept:</b> challenge/response authentication. The password itself is never sent.
     */
    @Operation(summary = "Digest auth (MD5)")
    @RequestMapping("/digest-auth/{qop}/{user}/{password}")
    public ResponseEntity<byte[]> digestAuth(@PathVariable String qop, @PathVariable String user,
                                             @PathVariable String password, HttpServletRequest request) {
        return digest(qop, user, password, "", request);
    }

    /**
     * HTTP Digest auth with a chosen algorithm: {@code MD5} or {@code SHA-256}.
     *
     * <p><b>HTTP concept:</b> RFC 7616 added SHA-256 to Digest. Many clients still only speak MD5. Find out whether
     * yours does.
     */
    @Operation(summary = "Digest auth (MD5 or SHA-256)")
    @RequestMapping("/digest-auth/{qop}/{user}/{password}/{algorithm}")
    public ResponseEntity<byte[]> digestAuthWithAlgorithm(
            @PathVariable String qop, @PathVariable String user, @PathVariable String password,
            @Parameter(description = "`MD5` or `SHA-256`") @PathVariable String algorithm,
            HttpServletRequest request) {
        return digest(qop, user, password, algorithm, request);
    }

    /**
     * The shared Digest logic: validate qop and algorithm, then answer with a challenge or with success.
     */
    private ResponseEntity<byte[]> digest(String rawQop, String user, String password, String rawAlgorithm,
                                          HttpServletRequest request) {
        String qop = rawQop.toLowerCase();
        String algorithmName = rawAlgorithm.isEmpty() ? "MD5" : rawAlgorithm.toUpperCase();
        if (!qop.equals("auth")) {
            return HttpbinResponses.error(400, "invalid QOP directive: " + GoStrconv.quote(qop) + " != \"auth\"");
        }
        if (!algorithmName.equals("MD5") && !algorithmName.equals("SHA-256")) {
            return HttpbinResponses.error(400, "invalid algorithm: " + algorithmName + " must be one of MD5 or SHA-256");
        }
        DigestAuth.Algorithm algorithm = algorithmName.equals("MD5") ? DigestAuth.Algorithm.MD5 : DigestAuth.Algorithm.SHA256;

        String query = request.getQueryString();
        String requestUri = request.getRequestURI() + (query == null ? "" : "?" + query);
        if (!DigestAuth.check(request.getHeader("Authorization"), request.getMethod(), requestUri, user, password)) {
            return ResponseEntity.status(401)
                    .header("WWW-Authenticate", DigestAuth.challenge(DIGEST_REALM, algorithm))
                    .header("Content-Type", HttpbinResponses.JSON)
                    .body(HttpbinResponses.error(401, null).getBody());
        }
        return HttpbinResponses.json(authBody(true, user));
    }

    /**
     * {@code {"authenticated": .., "user": .., "authorized": ..}}. "authorized" stays for older clients.
     */
    private static Map<String, Object> authBody(boolean ok, String user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authenticated", ok);
        body.put("user", user);
        body.put("authorized", ok);
        return body;
    }

    /**
     * Go's {@code Request.BasicAuth()}: a case-insensitive "Basic " prefix, standard base64, split at the first colon.
     * Returns null when the header is missing or malformed.
     */
    static String[] basicCredentials(String header) {
        String prefix = "Basic ";
        if (header == null || header.length() < prefix.length()
                || !header.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return null;
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(header.substring(prefix.length())), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        int colon = decoded.indexOf(':');
        return colon < 0 ? null : new String[]{decoded.substring(0, colon), decoded.substring(colon + 1)};
    }
}
