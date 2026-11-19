package io.qaplayground.server.support;

/**
 * Go's {@code textproto.CanonicalMIMEHeaderKey}: {@code x-request-id} becomes {@code X-Request-Id}.
 *
 * <p>The first letter, and every letter after a hyphen, become upper case; all others become lower case. HTTP header
 * names are case-insensitive, so this is cosmetic. It still matters here: httpbin echoes header names back, and
 * tests often compare them as JSON keys.
 */
public final class HeaderNames {

    private HeaderNames() {
    }

    /**
     * The canonical form of a header name. Names with characters that aren't allowed in a token come back unchanged,
     * as in Go.
     */
    public static String canonical(String name) {
        // Go leaves names containing non-token characters untouched.
        for (int i = 0; i < name.length(); i++) {
            if (!isTokenChar(name.charAt(i))) {
                return name;
            }
        }
        StringBuilder sb = new StringBuilder(name.length());
        boolean upper = true;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (upper && c >= 'a' && c <= 'z') {
                c = (char) (c - 32);
            } else if (!upper && c >= 'A' && c <= 'Z') {
                c = (char) (c + 32);
            }
            sb.append(c);
            upper = c == '-';
        }
        return sb.toString();
    }

    /**
     * Whether {@code c} may appear in an HTTP token, such as a header name.
     */
    private static boolean isTokenChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || "!#$%&'*+-.^_`|~".indexOf(c) >= 0;
    }
}
