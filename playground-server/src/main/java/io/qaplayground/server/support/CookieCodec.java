package io.qaplayground.server.support;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Go's cookie rules: how {@code Set-Cookie} is written and how {@code Cookie} is read.
 *
 * <p>It ports the parts of Go's {@code net/http} that go-httpbin relies on for {@code /cookies}:
 * {@code (*Cookie).String}, its value and path sanitizing, and {@code readCookies}. It knows nothing about requests
 * or responses, so the rules can be tested on their own.
 */
public final class CookieCodec {

    private CookieCodec() {
    }

    /**
     * The cookie attributes for one request, after defaults and {@code attr[...]} overrides.
     */
    public record Attributes(String domain, String path, boolean httpOnly, boolean secure, String sameSite) {
    }

    /**
     * Whether a query parameter sets an attribute ({@code attr[Path]}) rather than a cookie.
     */
    public static boolean isAttrParam(String key) {
        return key.startsWith("attr[") && key.endsWith("]");
    }

    /**
     * Reads the {@code attr[...]} parameters on top of the defaults: {@code Path=/}, {@code HttpOnly}, and
     * {@code Secure} when {@code defaultSecure} is true.
     */
    public static Attributes parseAttributes(Map<String, List<String>> params, boolean defaultSecure) {
        String domain = "";
        String path = "/";
        boolean httpOnly = true;
        boolean secure = defaultSecure;
        String sameSite = "";
        for (var e : params.entrySet()) {
            if (!isAttrParam(e.getKey()) || e.getValue().isEmpty()) {
                continue;
            }
            String v = e.getValue().getFirst();
            switch (e.getKey().substring(5, e.getKey().length() - 1).toLowerCase()) {
                case "domain" -> domain = v;
                case "httponly" -> httpOnly = parseBool(v);
                case "path" -> path = v;
                case "samesite" -> sameSite = switch (v.toLowerCase()) {
                    case "strict" -> "Strict";
                    case "lax" -> "Lax";
                    case "none" -> "None";
                    default -> "";
                };
                case "secure" -> secure = parseBool(v);
                default -> {
                }
            }
        }
        return new Attributes(domain, path, httpOnly, secure, sameSite);
    }

    /**
     * "1" or "true" (any case) is true; everything else is false.
     */
    private static boolean parseBool(String s) {
        return s.equals("1") || s.equalsIgnoreCase("true");
    }

    /**
     * Go's {@code (*http.Cookie).String()}. Null for an invalid name, in which case Go sets nothing.
     *
     * <p>With {@code delete}, the cookie is written already expired: an {@code Expires} 365 days before
     * {@code now} and {@code Max-Age=0}.
     */
    public static String serialize(String name, String value, Attributes a, boolean delete, Instant now) {
        if (!isToken(name)) {
            return null;
        }
        StringBuilder sb = new StringBuilder(name).append('=').append(sanitizeValue(value));
        String path = sanitizePath(a.path());
        if (!path.isEmpty()) {
            sb.append("; Path=").append(path);
        }
        if (!a.domain().isEmpty() && isValidDomain(a.domain())) {
            sb.append("; Domain=").append(a.domain().startsWith(".") ? a.domain().substring(1) : a.domain());
        }
        if (delete) {
            Instant expires = now.minus(365, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
            sb.append("; Expires=").append(ServeContent.formatHttpDate(expires));
            sb.append("; Max-Age=0");
        }
        if (a.httpOnly()) {
            sb.append("; HttpOnly");
        }
        if (a.secure()) {
            sb.append("; Secure");
        }
        if (!a.sameSite().isEmpty()) {
            sb.append("; SameSite=").append(a.sameSite());
        }
        return sb.toString();
    }

    /**
     * Drops bytes that aren't allowed in a cookie value, and quotes values that contain a space or a comma.
     */
    private static String sanitizeValue(String v) {
        StringBuilder sb = new StringBuilder();
        for (char c : v.toCharArray()) {
            if (c >= 0x20 && c < 0x7f && c != '"' && c != ';' && c != '\\') {
                sb.append(c);
            }
        }
        String s = sb.toString();
        return s.contains(" ") || s.contains(",") ? "\"" + s + "\"" : s;
    }

    /**
     * Keeps only printable ASCII in a cookie path, without {@code ;}, as Go does.
     */
    private static String sanitizePath(String v) {
        StringBuilder sb = new StringBuilder();
        for (char c : v.toCharArray()) {
            if (c >= 0x20 && c < 0x7f && c != ';') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Whether a domain attribute is a valid host name or IPv4 address. A leading dot is allowed.
     */
    private static boolean isValidDomain(String d) {
        String host = d.startsWith(".") ? d.substring(1) : d;
        return host.matches("(?i)[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*")
                || host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    /**
     * Whether {@code s} is a valid HTTP token, as a cookie name must be.
     */
    private static boolean isToken(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (char c : s.toCharArray()) {
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || "!#$%&'*+-.^_`|~".indexOf(c) >= 0)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Go's {@code readCookies}: reads one {@code Cookie} header into {@code into}. Invalid names and values are
     * skipped, and quotes are removed.
     */
    public static void parseCookieHeader(String header, Map<String, String> into) {
        for (String raw : header.split(";")) {
            String part = raw.strip();
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            String name = (eq < 0 ? part : part.substring(0, eq)).strip();
            String value = eq < 0 ? "" : part.substring(eq + 1);
            if (!isToken(name)) {
                continue;
            }
            if (value.length() > 1 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            boolean valid = value.chars().allMatch(c -> c >= 0x20 && c < 0x7f && c != '"' && c != ';' && c != '\\');
            if (valid) {
                into.put(name, value);
            }
        }
    }
}
