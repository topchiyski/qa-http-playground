package io.qaplayground.server.support;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Just enough of Go's {@code net/url} to decide and write {@code Location} exactly as go-httpbin's
 * {@code /redirect-to} does.
 *
 * <p>Two parts are ported: {@code url.Parse} (scheme, host, path, query, fragment, with Go's validation errors) and
 * {@code URL.String()} (writing it back out with Go's escaping rules).
 *
 * @param scheme   such as {@code https}, or empty
 * @param hostname host without port or IPv6 brackets, or empty
 * @param path     path (still percent-encoded), or empty
 */
public record GoUrl(String scheme, String hostname, String path) {

    /**
     * Thrown with Go's message, such as {@code parse "http://foo%%bar": invalid URL escape "%%b"}.
     */
    public static final class UrlError extends IllegalArgumentException {
        public UrlError(String raw, String reason) {
            super("parse " + GoStrconv.quote(raw) + ": " + reason);
        }
    }

    /**
     * Go's {@code url.IsAbs}: true when there is a scheme.
     */
    public boolean isAbsolute() {
        return !scheme.isEmpty();
    }

    /**
     * Go's {@code url.Parse}: the parts {@code /redirect-to} needs, or a {@link UrlError} with Go's message.
     */
    public static GoUrl parse(String raw) {
        Parts p = decompose(raw);
        return new GoUrl(p.scheme, p.hostname, p.opaque != null ? "" : p.path);
    }

    /**
     * Go's {@code url.Parse(raw).String()}: what go-httpbin puts in {@code Location}.
     *
     * <p>Paths and fragments keep a valid percent-encoding as sent and are re-escaped otherwise. A backslash or a space
     * never reaches the client raw.
     */
    public static String serialize(String raw) {
        Parts p = decompose(raw);
        StringBuilder sb = new StringBuilder();
        if (!p.scheme.isEmpty()) {
            sb.append(p.scheme).append(':');
        }
        if (p.opaque != null) {
            sb.append(p.opaque);
        } else {
            if (p.authority != null && (!p.authority.isEmpty() || !p.path.isEmpty())) {
                sb.append("//").append(p.authority);
            }
            String path = escapedPath(p.path);
            if (sb.isEmpty()) {
                int slash = path.indexOf('/');
                String firstSegment = slash < 0 ? path : path.substring(0, slash);
                if (firstSegment.contains(":")) {
                    sb.append("./"); // RFC 3986 4.2: would otherwise look like a scheme
                }
            }
            sb.append(path);
        }
        if (p.query != null) {
            sb.append('?').append(p.query);
        }
        if (p.fragment != null && !p.fragment.isEmpty()) {
            sb.append('#').append(escaped(p.fragment, Mode.FRAGMENT));
        }
        return sb.toString();
    }

    // --- url.Parse -----------------------------------------------------------------------------

    /**
     * The raw pieces of a URL. {@code authority} and {@code opaque} are null when there is none.
     */
    private record Parts(String scheme, String authority, String hostname, String path, String opaque,
                         String query, String fragment) {
    }

    /**
     * Splits a URL into its pieces in Go's order: fragment, scheme, query, then authority and path.
     */
    private static Parts decompose(String raw) {
        String rest = raw;
        String fragment = null;
        int hash = rest.indexOf('#');
        if (hash >= 0) {
            fragment = rest.substring(hash + 1);
            rest = rest.substring(0, hash);
        }
        String withoutFragment = rest;
        for (char c : withoutFragment.toCharArray()) {
            if (c < 0x20 || c == 0x7f) {
                throw new UrlError(withoutFragment, "net/url: invalid control character in URL");
            }
        }

        // Scheme: a letter followed by letters, digits, '+', '-' or '.', then ':'.
        String scheme = "";
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (Character.isLetter(c) && c < 128) {
                continue;
            }
            if ((Character.isDigit(c) || c == '+' || c == '-' || c == '.') && i > 0) {
                continue;
            }
            if (c == ':') {
                if (i == 0) {
                    throw new UrlError(withoutFragment, "missing protocol scheme");
                }
                scheme = rest.substring(0, i).toLowerCase();
                rest = rest.substring(i + 1);
            }
            break;
        }

        String query = null;
        int q = rest.indexOf('?');
        if (q >= 0) {
            query = rest.substring(q + 1);
            rest = rest.substring(0, q);
        }

        if (!rest.startsWith("/")) {
            if (!scheme.isEmpty()) {
                return new Parts(scheme, null, "", "", rest, query, checkedFragment(raw, fragment)); // opaque, e.g. mailto:
            }
            int slash = rest.indexOf('/');
            String firstSegment = slash < 0 ? rest : rest.substring(0, slash);
            if (firstSegment.contains(":")) {
                // Avoid confusion with malformed schemes, like cache_object:foo/bar.
                throw new UrlError(withoutFragment, "first path segment in URL cannot contain colon");
            }
        }

        String authority = null;
        String hostname = "";
        if (rest.startsWith("//") && (!scheme.isEmpty() || !rest.startsWith("///"))) {
            authority = rest.substring(2);
            int slash = authority.indexOf('/');
            rest = slash >= 0 ? authority.substring(slash) : "";
            authority = slash >= 0 ? authority.substring(0, slash) : authority;
            int at = authority.lastIndexOf('@');
            String host = at >= 0 ? authority.substring(at + 1) : authority;
            checkEscapes(withoutFragment, host);
            hostname = hostnameOf(withoutFragment, host);
        }
        checkEscapes(withoutFragment, rest);
        return new Parts(scheme, authority, hostname, rest, null, query, checkedFragment(raw, fragment));
    }

    /**
     * The fragment, after checking its escapes. Go reports a bad fragment against the whole input.
     */
    private static String checkedFragment(String raw, String fragment) {
        if (fragment != null) {
            checkEscapes(raw, fragment); // Go reports fragment errors against the full input
        }
        return fragment;
    }

    /**
     * The host without port or IPv6 brackets, rejecting an unclosed {@code [}.
     */
    private static String hostnameOf(String raw, String host) {
        if (host.startsWith("[")) {
            int close = host.indexOf(']');
            if (close < 0) {
                throw new UrlError(raw, "missing ']' in host");
            }
            return host.substring(1, close);
        }
        int colon = host.lastIndexOf(':');
        return colon >= 0 ? host.substring(0, colon) : host;
    }

    /**
     * Rejects a bad {@code %XX} escape with Go's message.
     */
    private static void checkEscapes(String raw, String s) {
        String bad = QueryString.badEscape(s);
        if (bad != null) {
            throw new UrlError(raw, "invalid URL escape " + GoStrconv.quote(bad));
        }
    }

    // --- URL.String() escaping ------------------------------------------------------------------

    /**
     * Which part of the URL is being escaped. Paths and fragments allow different characters.
     */
    private enum Mode { PATH, FRAGMENT }

    /**
     * Go's {@code EscapedPath}. A path of exactly {@code *} stays as it is.
     */
    private static String escapedPath(String rawPath) {
        if (rawPath.equals("*")) {
            return "*";
        }
        return escaped(rawPath, Mode.PATH);
    }

    /**
     * Go's {@code EscapedPath}/{@code EscapedFragment}: keep a valid encoding as sent, else escape the decoded text.
     */
    private static String escaped(String raw, Mode mode) {
        if (validEncoded(raw, mode)) {
            return raw;
        }
        byte[] decoded = unescape(raw);
        StringBuilder sb = new StringBuilder();
        for (byte b : decoded) {
            int c = b & 0xff;
            if (shouldEscape(c, mode)) {
                sb.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 15, 16)));
            } else {
                sb.append((char) c);
            }
        }
        return sb.toString();
    }

    /**
     * Whether {@code s} is already a valid encoding for this part, so it can be sent on unchanged.
     */
    private static boolean validEncoded(String s, Mode mode) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            int c = b & 0xff;
            if ("!$&'()*+,;=:@[]%".indexOf(c) >= 0 && c < 128) {
                continue;
            }
            if (shouldEscape(c, mode)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Go's {@code shouldEscape} for {@code encodePath} and {@code encodeFragment}.
     */
    private static boolean shouldEscape(int c, Mode mode) {
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
            return false;
        }
        if (c == '-' || c == '_' || c == '.' || c == '~') {
            return false;
        }
        if ("$&+,/:;=?@".indexOf(c) >= 0 && c < 128) {
            return mode == Mode.PATH && c == '?';
        }
        if (mode == Mode.FRAGMENT && (c == '!' || c == '(' || c == ')' || c == '*')) {
            return false;
        }
        return true;
    }

    /**
     * {@code %XX} decoding on UTF-8 bytes. The escapes were already validated by {@link #decompose}.
     */
    private static byte[] unescape(String s) {
        byte[] in = s.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        for (int i = 0; i < in.length; i++) {
            if (in[i] == '%' && i + 2 < in.length) {
                out.write(Character.digit(in[i + 1], 16) * 16 + Character.digit(in[i + 2], 16));
                i += 2;
            } else {
                out.write(in[i]);
            }
        }
        return out.toByteArray();
    }
}
