package io.qaplayground.server.support;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses {@code a=1&b=2&a=3} into a map of lists, with the same rules as Go's {@code url.ParseQuery}.
 *
 * <p>We can't use {@code request.getParameterMap()}. The Servlet API mixes in form fields from the body, but
 * httpbin's {@code args} must show the query string and nothing else.
 */
public final class QueryString {

    private QueryString() {
    }

    /**
     * Each name with all its values, in the order they arrived. Invalid pairs are skipped, as Go skips them.
     */
    public static Map<String, List<String>> parse(String rawQuery) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return result;
        }
        for (String pair : rawQuery.split("&", -1)) {
            if (pair.isEmpty() || pair.contains(";")) {
                continue; // Go rejects ';' separators since 1.17
            }
            int eq = pair.indexOf('=');
            String rawKey = eq < 0 ? pair : pair.substring(0, eq);
            String rawValue = eq < 0 ? "" : pair.substring(eq + 1);
            String key = unescape(rawKey);
            String value = unescape(rawValue);
            if (key == null || value == null) {
                continue; // malformed %-escape
            }
            result.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return result;
    }

    /**
     * The error Go's {@code url.ParseQuery} would return for this input, or {@code null}.
     *
     * <p>{@link #parse} quietly skips bad pairs, like {@code r.URL.Query()}. Form bodies go through
     * {@code r.ParseForm()} instead, which reports the first error, so they need this.
     */
    public static String firstError(String rawQuery) {
        if (rawQuery == null) {
            return null;
        }
        for (String pair : rawQuery.split("&", -1)) {
            if (pair.contains(";")) {
                return "invalid semicolon separator in query";
            }
            for (String piece : pair.split("=", 2)) {
                String bad = badEscape(piece);
                if (bad != null) {
                    return "invalid URL escape " + GoStrconv.quote(bad);
                }
            }
        }
        return null;
    }

    /**
     * The bad escape sequence (at most 3 characters, as Go reports it), or null when every escape is valid.
     */
    static String badEscape(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '%') {
                if (i + 2 >= s.length() || Character.digit(s.charAt(i + 1), 16) < 0
                        || Character.digit(s.charAt(i + 2), 16) < 0) {
                    return s.substring(i, Math.min(i + 3, s.length()));
                }
                i += 2;
            }
        }
        return null;
    }

    /**
     * The first value, or an empty string, like Go's {@code url.Values.Get}.
     */
    public static String first(Map<String, List<String>> values, String name) {
        List<String> v = values.get(name);
        return v == null || v.isEmpty() ? "" : v.getFirst();
    }

    /**
     * {@code url.QueryUnescape}: {@code +} is a space and {@code %XX} must be valid hex. Null on error.
     */
    static String unescape(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(s.length());
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];
            if (b == '+') {
                out.write(' ');
            } else if (b == '%') {
                if (i + 2 >= bytes.length) {
                    return null;
                }
                int hi = Character.digit(bytes[i + 1], 16);
                int lo = Character.digit(bytes[i + 2], 16);
                if (hi < 0 || lo < 0) {
                    return null;
                }
                out.write(hi * 16 + lo);
                i += 2;
            } else {
                out.write(b);
            }
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
