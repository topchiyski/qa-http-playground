package io.qaplayground.server.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.qaplayground.server.config.PlaygroundProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Extracts what httpbin echoes back from a request: query args, headers, client IP and the full URL.
 *
 * <p>Every rule here mirrors go-httpbin's {@code helpers.go}. Small differences, such as header casing, would break
 * tests written against the reference.
 */
@Component
public class RequestInfo {

    private final Pattern excludeHeaders;

    public RequestInfo(PlaygroundProperties properties) {
        this.excludeHeaders = compileExcludePattern(properties.excludeHeaders());
    }

    /**
     * Query string parameters only, never form fields from the body.
     */
    public Map<String, List<String>> args(HttpServletRequest request) {
        return QueryString.parse(request.getQueryString());
    }

    /**
     * Request headers under Go's canonical name ({@code accept-encoding} becomes {@code Accept-Encoding}), each with
     * all its values in the order they arrived. Headers excluded by configuration are left out.
     */
    public Map<String, List<String>> headers(HttpServletRequest request) {
        Map<String, List<String>> headers = new TreeMap<>();
        for (String name : Collections.list(request.getHeaderNames())) {
            String canonical = HeaderNames.canonical(name);
            // Tomcat lists each name once (case-insensitively) and returns all of its values.
            headers.computeIfAbsent(canonical, k -> new ArrayList<>())
                    .addAll(Collections.list(request.getHeaders(name)));
        }
        // Go moves Host out of the header map; go-httpbin puts it back as a single value.
        String host = request.getHeader("Host");
        if (host != null) {
            headers.put("Host", List.of(host));
        }
        if (excludeHeaders != null) {
            headers.keySet().removeIf(k -> excludeHeaders.matcher(k).matches());
        }
        return headers;
    }

    /**
     * The client's IP address.
     *
     * <p>Proxy and CDN headers win over the socket address, in the same order as in go-httpbin. Anyone can fake these
     * headers, so never use this for security.
     */
    public String clientIp(HttpServletRequest request) {
        for (String header : List.of("Fly-Client-IP", "CF-Connecting-IP", "Fastly-Client-IP", "True-Client-IP")) {
            String value = request.getHeader(header);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        return compressIpv6(request.getRemoteAddr());
    }

    /**
     * Shortens an IPv6 address to the RFC 5952 form Go prints.
     *
     * <p>Tomcat reports {@code 0:0:0:0:0:0:0:1}; Go prints {@code ::1}. Converting keeps {@code origin} identical.
     */
    static String compressIpv6(String addr) {
        if (addr == null || !addr.contains(":") || addr.contains("::")) {
            return addr;
        }
        String[] groups = addr.split(":");
        if (groups.length != 8) {
            return addr;
        }
        for (int i = 0; i < 8; i++) {
            groups[i] = Integer.toHexString(Integer.parseInt(groups[i], 16));
        }
        int bestStart = -1;
        int bestLen = 1; // RFC 5952: a single zero group is not shortened
        for (int i = 0; i < 8; ) {
            if (!groups[i].equals("0")) {
                i++;
                continue;
            }
            int j = i;
            while (j < 8 && groups[j].equals("0")) {
                j++;
            }
            if (j - i > bestLen) {
                bestStart = i;
                bestLen = j - i;
            }
            i = j;
        }
        if (bestStart < 0) {
            return String.join(":", groups);
        }
        String head = String.join(":", java.util.Arrays.copyOfRange(groups, 0, bestStart));
        String tail = String.join(":", java.util.Arrays.copyOfRange(groups, bestStart + bestLen, 8));
        return head + "::" + tail;
    }

    /**
     * The URL the client asked for, honouring {@code X-Forwarded-Proto} and friends.
     */
    public String url(HttpServletRequest request) {
        String query = request.getQueryString();
        return scheme(request) + "://" + host(request) + request.getRequestURI()
                + (query == null ? "" : "?" + query);
    }

    /**
     * The scheme as the client sees it: {@code https} behind a proxy that terminates TLS.
     */
    public String scheme(HttpServletRequest request) {
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (isBlank(scheme)) {
            scheme = request.getHeader("X-Forwarded-Protocol");
        }
        if (isBlank(scheme) && "on".equals(request.getHeader("X-Forwarded-Ssl"))) {
            scheme = "https";
        }
        if (isBlank(scheme) && request.isSecure()) {
            scheme = "https";
        }
        return isBlank(scheme) ? "http" : scheme;
    }

    /**
     * The {@code Host} header, or the server name and port when it is missing.
     */
    public String host(HttpServletRequest request) {
        String host = request.getHeader("Host");
        return isBlank(host) ? request.getServerName() + ":" + request.getServerPort() : host;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }

    /**
     * Turns {@code -exclude-headers "x-fly-*,via"} into one pattern: case-insensitive, with {@code *} as a wildcard.
     */
    static Pattern compileExcludePattern(String spec) {
        if (spec == null || spec.isBlank()) {
            return null;
        }
        String regex = java.util.Arrays.stream(spec.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> java.util.Arrays.stream(s.split("\\*", -1))
                        .map(Pattern::quote)
                        .collect(Collectors.joining(".*")))
                .collect(Collectors.joining("|"));
        return regex.isEmpty() ? null : Pattern.compile("(?i)(" + regex + ")");
    }
}
