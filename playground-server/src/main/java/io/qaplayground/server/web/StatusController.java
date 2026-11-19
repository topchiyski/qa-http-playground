package io.qaplayground.server.web;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import io.qaplayground.server.support.GoRandom;
import io.qaplayground.server.support.GoStrconv;
import io.qaplayground.server.support.HttpbinResponses;
import io.qaplayground.server.support.QueryString;
import io.qaplayground.server.support.StatusCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletResponseWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static java.util.Map.entry;

/**
 * Answers with any status code you like, or fails at random.
 */
@Tag(name = "Status codes", description = "Arbitrary and random status codes, simulated failures")
@RestController
public class StatusController {

    /**
     * The headers and body go-httpbin sends with a few "special" codes.
     */
    private record SpecialCase(Map<String, String> headers, String body) {
    }

    /**
     * A redirect status means little without somewhere to go, so 3xx codes get a {@code Location}.
     */
    private static final SpecialCase REDIRECT = new SpecialCase(Map.of("Location", "/redirect/1"), null);

    /**
     * The codes that come with extra headers or a body, exactly as go-httpbin sends them.
     */
    private static final Map<Integer, SpecialCase> SPECIAL_CASES = Map.ofEntries(
            entry(300, new SpecialCase(Map.of("Location", "/image/jpeg"), """
                    <!doctype html>
                    <head>
                    <title>Multiple Choices</title>
                    </head>
                    <body>
                    <ul>
                    <li><a href="/image/jpeg">/image/jpeg</a></li>
                    <li><a href="/image/png">/image/png</a></li>
                    <li><a href="/image/svg">/image/svg</a></li>
                    </body>
                    </html>""")),
            entry(301, REDIRECT), entry(302, REDIRECT), entry(303, REDIRECT),
            entry(305, REDIRECT), entry(307, REDIRECT),
            entry(308, new SpecialCase(Map.of("Location", "/image/jpeg"), """
                    <!doctype html>
                    <head>
                    <title>Permanent Redirect</title>
                    </head>
                    <body>Permanently redirected to <a href="/image/jpeg">/image/jpeg</a>
                    </body>
                    </html>""")),
            entry(401, new SpecialCase(Map.of("WWW-Authenticate", "Basic realm=\"Fake Realm\""), null)),
            entry(402, new SpecialCase(Map.of("X-More-Info", "http://vimeo.com/22053820"), "Fuck you, pay me!")),
            entry(406, new SpecialCase(Map.of("Content-Type", HttpbinResponses.JSON), """
                    {
                      "message": "Client did not request a supported media type",
                      "accept": [
                        "image/webp",
                        "image/svg+xml",
                        "image/jpeg",
                        "image/png",
                        "image/"
                      ]
                    }
                    """)),
            entry(407, new SpecialCase(Map.of("Proxy-Authenticate", "Basic realm=\"Fake Realm\""), null)),
            entry(418, new SpecialCase(Map.of("X-More-Info", "http://tools.ietf.org/html/rfc2324"), "I'm a teapot!")));

    /**
     * Answers with the status code you ask for (100 to 599).
     *
     * <p>A comma-separated list picks one at random, optionally weighted: {@code /status/200:0.9,500:0.1} fails about
     * one request in ten. Some codes come with the headers that make them meaningful: 3xx with {@code Location}, 401
     * with {@code WWW-Authenticate}, 418 with a body.
     *
     * <p><b>HTTP concept:</b> status classes: 2xx success, 3xx redirect, 4xx client error, 5xx server error. Use it to
     * see how your client or test framework reacts to each.
     */
    @Operation(summary = "Respond with a given (or weighted random) status code")
    @RequestMapping("/status/{code}")
    public ResponseEntity<byte[]> status(
            @Parameter(description = "A code like `418`, or weighted choices like `200:0.7,429:0.2,503:0.1`")
            @PathVariable String code, HttpServletResponse response) {
        int chosen;
        if (!code.contains(",")) {
            try {
                chosen = StatusCodes.parse(code, 100, 599);
            } catch (IllegalArgumentException e) {
                return HttpbinResponses.error(400, e.getMessage());
            }
        } else {
            try {
                chosen = StatusCodes.weightedChoice(code, ThreadLocalRandom.current().nextDouble());
            } catch (IllegalArgumentException e) {
                return HttpbinResponses.error(400, e.getMessage());
            }
        }
        return respondWith(chosen, response);
    }

    /**
     * Answers a 1xx code.
     *
     * <p>1xx codes are <em>interim</em> responses. In go-httpbin the client gets, say, {@code 100 Continue}, then the
     * real, final {@code 200 OK}. Tomcat can only send one kind of interim response, {@code 103 Early Hints}. For the
     * other 1xx codes we send only the final 200 (see docs/COMPATIBILITY.md). 101 is a final status in Go too, so it is
     * not special.
     */
    private ResponseEntity<byte[]> informational(int code, HttpServletResponse response) {
        if (code == 103) {
            response.setHeader("Content-Type", HttpbinResponses.TEXT);
            ServletResponse r = response;
            while (r instanceof ServletResponseWrapper wrapper) {
                r = wrapper.getResponse();
            }
            if (r instanceof org.apache.catalina.connector.ResponseFacade tomcat) {
                tomcat.sendEarlyHints();
            }
        }
        return ResponseEntity.ok().header("Content-Type", HttpbinResponses.TEXT).build();
    }

    /**
     * Answers with {@code code}, adding the special headers and body where go-httpbin does.
     */
    private ResponseEntity<byte[]> respondWith(int code, HttpServletResponse response) {
        if (code >= 100 && code < 200 && code != 101) {
            return informational(code, response);
        }
        SpecialCase special = SPECIAL_CASES.get(code);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(code).header("Content-Type", HttpbinResponses.TEXT);
        if (special == null) {
            return builder.build();
        }
        builder.headers(h -> special.headers().forEach(h::set)); // set, not add: 406 replaces Content-Type
        return special.body() == null ? builder.build() : builder.body(special.body().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Answers 200 or 500 at random.
     *
     * <p>{@code failure_rate} (default 0.5) sets the share of 500s. {@code seed} makes the outcome reproducible, and
     * identical to go-httpbin's.
     *
     * <p><b>HTTP concept:</b> flaky dependencies. Practise retries, circuit breakers and error-rate assertions in load
     * tests.
     */
    @Operation(summary = "Randomly fail with 500")
    @Parameter(name = "failure_rate", in = ParameterIn.QUERY, description = "Probability of a 500, 0..1 (default 0.5)")
    @Parameter(name = "seed", in = ParameterIn.QUERY, description = "Integer seed for a reproducible outcome")
    @RequestMapping("/unstable")
    public ResponseEntity<byte[]> unstable(HttpServletRequest request) {
        var args = QueryString.parse(request.getQueryString());
        GoRandom rng;
        try {
            rng = StatusCodes.seededRandom(QueryString.first(args, "seed"));
        } catch (GoStrconv.NumberFormatError e) {
            return HttpbinResponses.error(400, "invalid seed: " + e.getMessage());
        }
        double failureRate = 0.5;
        String rawRate = QueryString.first(args, "failure_rate");
        if (!rawRate.isEmpty()) {
            try {
                failureRate = GoStrconv.parseFloat(rawRate);
            } catch (GoStrconv.NumberFormatError e) {
                return HttpbinResponses.error(400, "invalid failure rate: " + e.getMessage());
            }
            if (failureRate < 0 || failureRate > 1) {
                // Reproduces an upstream formatting bug verbatim (Go printed a nil error with %d).
                return HttpbinResponses.error(400, "invalid failure rate: %!d(<nil>) not in range [0, 1]");
            }
        }
        int status = rng.float64() < failureRate ? 500 : 200;
        return ResponseEntity.status(status).header("Content-Type", HttpbinResponses.TEXT).build();
    }

}
