package io.qaplayground.server.web;

import java.util.List;

import io.qaplayground.server.config.PlaygroundProperties;
import io.qaplayground.server.support.GoStrconv;
import io.qaplayground.server.support.GoUrl;
import io.qaplayground.server.support.HttpbinResponses;
import io.qaplayground.server.support.QueryString;
import io.qaplayground.server.support.RequestInfo;
import io.qaplayground.server.support.StatusCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Redirect chains and open redirects.
 *
 * <p>Practise "follow redirects" settings and maximum-redirect limits, and asserting on {@code Location} instead of
 * following it.
 */
@Tag(name = "Redirects", description = "Redirect chains, absolute and relative Location headers")
@RestController
public class RedirectController {

    private final RequestInfo info;
    private final PlaygroundProperties properties;
    private final String forbiddenMessage;

    public RedirectController(RequestInfo info, PlaygroundProperties properties) {
        this.info = info;
        this.properties = properties;
        this.forbiddenMessage = forbiddenRedirectMessage(properties.allowedRedirectDomains());
    }

    /**
     * Redirects {@code n} times, then lands on {@code /get}.
     *
     * <p>{@code Location} is relative by default; {@code ?absolute=true} switches to absolute URLs.
     *
     * <p><b>HTTP concept:</b> 302 Found and redirect chains. Count the hops your client follows.
     */
    @Operation(summary = "Redirect n times, then to /get")
    @Parameter(name = "absolute", in = ParameterIn.QUERY, description = "`true` for absolute Location headers")
    @RequestMapping("/redirect/{n}")
    public ResponseEntity<byte[]> redirect(@PathVariable String n, HttpServletRequest request) {
        boolean relative = !"true".equalsIgnoreCase(QueryString.first(info.args(request), "absolute"));
        return handleRedirect(n, request, relative);
    }

    /**
     * Redirects {@code n} times with relative {@code Location} headers ({@code /relative-redirect/2}).
     *
     * <p><b>HTTP concept:</b> a client must resolve a relative reference against the request URL.
     */
    @Operation(summary = "n redirects with relative Location headers")
    @RequestMapping("/relative-redirect/{n}")
    public ResponseEntity<byte[]> relativeRedirect(@PathVariable String n, HttpServletRequest request) {
        return handleRedirect(n, request, true);
    }

    /**
     * Redirects {@code n} times with absolute {@code Location} headers ({@code http://host/absolute-redirect/2}).
     *
     * <p><b>HTTP concept:</b> an absolute redirect reveals the host name the server believes it has. Watch it change
     * behind a proxy ({@code X-Forwarded-Proto}).
     */
    @Operation(summary = "n redirects with absolute Location headers")
    @RequestMapping("/absolute-redirect/{n}")
    public ResponseEntity<byte[]> absoluteRedirect(@PathVariable String n, HttpServletRequest request) {
        return handleRedirect(n, request, false);
    }

    /**
     * The shared logic of the redirect chains: check {@code n}, then point to the next hop or to {@code /get}.
     */
    private ResponseEntity<byte[]> handleRedirect(String rawN, HttpServletRequest request, boolean relative) {
        long n;
        try {
            n = GoStrconv.atoi(rawN);
        } catch (GoStrconv.NumberFormatError e) {
            return HttpbinResponses.error(400, "invalid redirect count: " + e.getMessage());
        }
        if (n < 1) {
            return HttpbinResponses.error(400, "redirect count must be > 0");
        }
        if (properties.maxRedirects() > 0 && n > properties.maxRedirects()) {
            return HttpbinResponses.error(400, "redirect count must be <= " + properties.maxRedirects());
        }
        long remaining = n - 1;
        String path = remaining < 1 ? "/get"
                : (relative ? "/relative-redirect/" : "/absolute-redirect/") + remaining;
        String location = relative ? path : info.scheme(request) + "://" + info.host(request) + path;
        return redirectTo(location, 302);
    }

    /**
     * Redirects to {@code url} with {@code status_code} (300 to 399, default 302).
     *
     * <p>Only absolute URLs and root-relative paths ({@code /get}) are allowed.
     * {@code playground.allowed-redirect-domains} restricts absolute targets further.
     *
     * <p><b>HTTP concept:</b> open redirects, a classic security finding. Try {@code //evil.com} and encoded variants,
     * and see them refused.
     */
    @Operation(summary = "Redirect to an arbitrary URL")
    @Parameter(name = "url", in = ParameterIn.QUERY, required = true, description = "Target URL")
    @Parameter(name = "status_code", in = ParameterIn.QUERY, description = "3xx status, default 302")
    @RequestMapping("/redirect-to")
    public ResponseEntity<byte[]> redirectTo(HttpServletRequest request) {
        var args = info.args(request);
        String target = QueryString.first(args, "url");
        if (target.isEmpty()) {
            return HttpbinResponses.error(400, "missing required query parameter: url");
        }
        GoUrl url;
        try {
            url = GoUrl.parse(target);
        } catch (GoUrl.UrlError e) {
            return HttpbinResponses.error(400, "invalid url: " + e.getMessage());
        }
        if (!redirectAllowed(url)) {
            // Deliberately plain text: humans should notice this is a blocked redirect.
            return HttpbinResponses.bytes(403, "text/plain", forbiddenMessage.getBytes());
        }
        int status = 302;
        String rawStatus = QueryString.first(args, "status_code");
        if (!rawStatus.isEmpty()) {
            try {
                status = StatusCodes.parse(rawStatus, 300, 399);
            } catch (IllegalArgumentException e) {
                return HttpbinResponses.error(400, e.getMessage());
            }
        }
        // Like go-httpbin's doRedirect(u.String()): re-serialised, so e.g. a backslash is escaped.
        // Sending the raw value let browsers read "/\evil.com" as "//evil.com" (risk R1).
        return redirectTo(GoUrl.serialize(target), status);
    }

    /**
     * Whether {@code url} may be a redirect target.
     *
     * <p>Without an allowlist: any absolute URL or root-relative path. With one: only listed hosts, or plain
     * root-relative paths. That second rule is stricter than go-httpbin, see docs/COMPATIBILITY.md.
     */
    private boolean redirectAllowed(GoUrl url) {
        List<String> allowed = properties.allowedRedirectDomains();
        if (!allowed.isEmpty()) {
            if (!url.hostname().isEmpty()) {
                return allowed.contains(url.hostname());
            }
            // Hardening beyond the reference (docs/COMPATIBILITY.md): with an allow-list, go-httpbin
            // still lets through host-less targets that browsers resolve to another host —
            // "///evil.com" and "https:evil.com". Only plain root-relative paths are allowed.
            return !url.isAbsolute() && url.path().startsWith("/") && !url.path().startsWith("//");
        }
        return url.isAbsolute() || url.path().startsWith("/");
    }

    /**
     * A redirect with no body: just the status and {@code Location}.
     */
    private static ResponseEntity<byte[]> redirectTo(String location, int status) {
        return ResponseEntity.status(status).header("Location", location).build();
    }

    /**
     * The body of a refused redirect. Empty unless an allowlist is configured, as in the reference.
     */
    private static String forbiddenRedirectMessage(List<String> allowed) {
        if (allowed.isEmpty()) {
            return "";
        }
        List<String> items = allowed.stream().map(h -> "- " + h).sorted().toList();
        return "Forbidden redirect URL. Please be careful with this link.\n\nAllowed redirect destinations:\n"
                + String.join("\n", items);
    }
}
