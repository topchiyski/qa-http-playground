package io.qaplayground.server.support;

import java.util.regex.Pattern;

/**
 * Number parsing with the same accept and reject rules, <em>and error messages</em>, as Go's {@code strconv}.
 *
 * <p>go-httpbin puts those messages in its error responses, such as
 * {@code "invalid count: strconv.Atoi: parsing \"abc\": invalid syntax"}. Tests compare them, so we reproduce them.
 */
public final class GoStrconv {

    /**
     * Decimal floats plus inf, infinity and nan, as {@code strconv.ParseFloat} accepts them.
     */
    private static final Pattern FLOAT = Pattern.compile(
            "[+-]?((\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?|(?i:inf|infinity|nan))");

    private GoStrconv() {
    }

    /**
     * Thrown with the exact message Go would produce.
     */
    public static final class NumberFormatError extends IllegalArgumentException {
        public NumberFormatError(String message) {
            super(message);
        }
    }

    /**
     * {@code strconv.Atoi}, with Go's 64-bit {@code int}.
     */
    public static long atoi(String s) {
        return parse("Atoi", s, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    /**
     * {@code strconv.ParseInt(s, 10, bitSize)}.
     */
    public static long parseInt(String s, int bitSize) {
        long max = bitSize == 64 ? Long.MAX_VALUE : (1L << (bitSize - 1)) - 1;
        long min = bitSize == 64 ? Long.MIN_VALUE : -(1L << (bitSize - 1));
        return parse("ParseInt", s, min, max);
    }

    /**
     * Parses a signed decimal and checks its range, failing with Go's "invalid syntax" or "value out of range".
     */
    private static long parse(String fn, String s, long min, long max) {
        String digits = s.startsWith("+") || s.startsWith("-") ? s.substring(1) : s;
        if (digits.isEmpty() || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new NumberFormatError("strconv." + fn + ": parsing " + quote(s) + ": invalid syntax");
        }
        try {
            long v = Long.parseLong(s);
            if (v < min || v > max) {
                throw new NumberFormatException();
            }
            return v;
        } catch (NumberFormatException e) {
            throw new NumberFormatError("strconv." + fn + ": parsing " + quote(s) + ": value out of range");
        }
    }

    /**
     * {@code strconv.ParseFloat(s, 64)}. Hex floats and digit underscores are not supported.
     */
    public static double parseFloat(String s) {
        if (!FLOAT.matcher(s).matches()) {
            throw new NumberFormatError("strconv.ParseFloat: parsing " + quote(s) + ": invalid syntax");
        }
        String lower = s.toLowerCase();
        if (lower.endsWith("nan")) {
            return Double.NaN;
        }
        if (lower.endsWith("inf") || lower.endsWith("infinity")) {
            return s.startsWith("-") ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        double d = Double.parseDouble(s);
        if (Double.isInfinite(d)) {
            throw new NumberFormatError("strconv.ParseFloat: parsing " + quote(s) + ": value out of range");
        }
        return d;
    }

    /**
     * A simplified {@code strconv.Quote}: wraps in quotes and escapes quotes, backslashes and control characters.
     */
    public static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x7f) {
                        sb.append(String.format("\\x%02x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
