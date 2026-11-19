package io.qaplayground.server.web;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.qaplayground.server.support.CookieCodec;
import io.qaplayground.server.support.HttpbinResponses;
import io.qaplayground.server.support.QueryString;
import io.qaplayground.server.support.RequestInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cookies: read them, set them, delete them.
 *
 * <p>{@code /cookies/set} and {@code /cookies/delete} redirect to {@code /cookies}. A client with a cookie jar
 * therefore shows the result straight away.
 */
@Tag(name = "Cookies", description = "Read, set and delete cookies, with attribute control")
@RestController
public class CookiesController {

    private final RequestInfo info;
    private final Clock clock;

    public CookiesController(RequestInfo info, Clock clock) {
        this.info = info;
        this.clock = clock;
    }

    /**
     * The cookies the client sent, as {@code {"cookies": {"name": "value"}}}.
     *
     * <p><b>HTTP concept:</b> the {@code Cookie} request header. Check what your tool's cookie jar really sends back:
     * domain, path and {@code Secure} rules all apply.
     */
    @Operation(summary = "Cookies sent by the client")
    @RequestMapping("/cookies")
    public ResponseEntity<byte[]> cookies(HttpServletRequest request) {
        Map<String, String> cookies = new TreeMap<>();
        for (String header : Collections.list(request.getHeaders("Cookie"))) {
            CookieCodec.parseCookieHeader(header, cookies);
        }
        return HttpbinResponses.json(Map.of("cookies", cookies));
    }

    /**
     * Sets one cookie per query parameter, then redirects to {@code /cookies}.
     *
     * <p>Attributes default to {@code Path=/; HttpOnly}, plus {@code Secure} over HTTPS. Override them with
     * {@code attr[Domain]}, {@code attr[Path]}, {@code attr[HttpOnly]}, {@code attr[Secure]} and
     * {@code attr[SameSite]=Strict|Lax|None}.
     *
     * <p><b>HTTP concept:</b> {@code Set-Cookie} attributes, and how clients enforce them.
     */
    @Operation(summary = "Set cookies from query params, then redirect to /cookies")
    @RequestMapping("/cookies/set")
    public ResponseEntity<byte[]> setCookies(HttpServletRequest request) {
        return withCookies(request, false);
    }

    /**
     * Expires the named cookies ({@code Max-Age=0} and an {@code Expires} in the past), then redirects to
     * {@code /cookies}. Use the same attributes the cookie was set with.
     *
     * <p><b>HTTP concept:</b> there is no "delete cookie" header. You overwrite the cookie with one that has already
     * expired, and it must match on name, domain and path.
     */
    @Operation(summary = "Delete cookies named in query params, then redirect to /cookies")
    @RequestMapping("/cookies/delete")
    public ResponseEntity<byte[]> deleteCookies(HttpServletRequest request) {
        return withCookies(request, true);
    }

    /**
     * The shared logic of set and delete: one {@code Set-Cookie} per parameter, then a redirect to {@code /cookies}.
     */
    private ResponseEntity<byte[]> withCookies(HttpServletRequest request, boolean delete) {
        Map<String, List<String>> params = info.args(request);
        CookieCodec.Attributes attrs = CookieCodec.parseAttributes(params, "https".equals(info.scheme(request)));
        Instant now = Instant.now(clock);
        List<String> setCookies = new ArrayList<>();
        for (var entry : params.entrySet()) {
            String name = entry.getKey();
            if (CookieCodec.isAttrParam(name)) {
                continue;
            }
            String value = QueryString.first(params, name);
            String header = CookieCodec.serialize(name, value, attrs, delete, now);
            if (header != null) {
                setCookies.add(header);
            }
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(302).header("Location", "/cookies");
        setCookies.forEach(c -> builder.header("Set-Cookie", c));
        return builder.build();
    }
}
