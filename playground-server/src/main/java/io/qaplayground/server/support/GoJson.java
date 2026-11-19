package io.qaplayground.server.support;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.TreeMap;

/**
 * Writes JSON byte for byte the way Go's {@code encoding/json} does, so responses look exactly like go-httpbin's.
 *
 * <p>The value model is small on purpose:
 * <ul>
 *   <li>a {@link SequencedMap} (such as {@code LinkedHashMap}) is a Go <em>struct</em>: fields are written in
 *       insertion order;</li>
 *   <li>any other {@link Map} is a Go <em>map</em>: keys are written sorted;</li>
 *   <li>{@link Collection}, {@link CharSequence}, {@link Number}, {@link Boolean} and {@code null} become arrays,
 *       strings, numbers, booleans and {@code null}.</li>
 * </ul>
 * <p>go-httpbin uses two flavours, and both are reproduced:
 * <ul>
 *   <li>{@link #pretty(Object)}: {@code json.Encoder} with a two-space indent and HTML escaping off. Almost every
 *       endpoint uses it.</li>
 *   <li>{@link #compact(Object)}: {@code json.Marshal}, no indent, HTML characters escaped as <code>&#92;u003c</code>
 * and so
 *       on. {@code /stream}, {@code /jsonl} and {@code /sse} use it.</li>
 * </ul>
 */
public final class GoJson {

    private GoJson() {
    }

    /**
     * Pretty JSON, plus the trailing newline Go's encoder always writes.
     */
    public static byte[] pretty(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, "", true, false);
        sb.append('\n');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Compact JSON as {@code json.Marshal} writes it, with no trailing newline.
     */
    public static String compact(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, "", false, true);
        return sb.toString();
    }

    /**
     * Writes any supported value, choosing the JSON form by its Java type.
     */
    private static void write(StringBuilder sb, Object value, String indent, boolean pretty, boolean escapeHtml) {
        switch (value) {
            case null -> sb.append("null");
            case CharSequence s -> writeString(sb, s.toString(), escapeHtml);
            case Boolean b -> sb.append(b);
            case Integer i -> sb.append(i);
            case Long l -> sb.append(l);
            case Short s -> sb.append(s);
            case Byte b -> sb.append(b);
            case Number n -> sb.append(formatFloat(n.doubleValue()));
            case Map<?, ?> map -> writeObject(sb, map, indent, pretty, escapeHtml);
            case Collection<?> list -> writeArray(sb, list, indent, pretty, escapeHtml);
            case Object[] array -> writeArray(sb, List.of(array), indent, pretty, escapeHtml);
            default -> throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass());
        }
    }

    /**
     * Writes a JSON object: struct-like maps in insertion order, other maps with sorted keys.
     */
    private static void writeObject(StringBuilder sb, Map<?, ?> map, String indent, boolean pretty, boolean escapeHtml) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        Map<?, ?> ordered = map instanceof SequencedMap<?, ?> ? map : sorted(map);
        String inner = indent + "  ";
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : ordered.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            if (pretty) {
                sb.append('\n').append(inner);
            }
            writeString(sb, String.valueOf(e.getKey()), escapeHtml);
            sb.append(pretty ? ": " : ":");
            write(sb, e.getValue(), inner, pretty, escapeHtml);
        }
        if (pretty) {
            sb.append('\n').append(indent);
        }
        sb.append('}');
    }

    /**
     * A copy of the map with string keys in sorted order, as Go writes a map.
     */
    private static Map<String, Object> sorted(Map<?, ?> map) {
        Map<String, Object> result = new TreeMap<>();
        map.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }

    /**
     * Writes a JSON array, one element per line when pretty.
     */
    private static void writeArray(StringBuilder sb, Collection<?> list, String indent, boolean pretty, boolean escapeHtml) {
        if (list.isEmpty()) {
            sb.append("[]");
            return;
        }
        String inner = indent + "  ";
        sb.append('[');
        boolean first = true;
        for (Object item : new ArrayList<>(list)) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            if (pretty) {
                sb.append('\n').append(inner);
            }
            write(sb, item, inner, pretty, escapeHtml);
        }
        if (pretty) {
            sb.append('\n').append(indent);
        }
        sb.append(']');
    }

    /**
     * Writes a quoted JSON string with Go's escapes. Mirrors {@code encodeState.string} in {@code encoding/json}.
     */
    static void writeString(StringBuilder sb, String s, boolean escapeHtml) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case ' ' -> sb.append("\\u2028");
                case ' ' -> sb.append("\\u2029");
                default -> {
                    if (c < 0x20 || (escapeHtml && (c == '<' || c == '>' || c == '&'))) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else if (Character.isHighSurrogate(c) && i + 1 < s.length()
                            && Character.isLowSurrogate(s.charAt(i + 1))) {
                        sb.append(c).append(s.charAt(++i));
                    } else if (Character.isSurrogate(c)) {
                        sb.append("\\ufffd"); // Go replaces invalid UTF-8 with U+FFFD
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    /**
     * Formats a float64 the way Go's {@code encoding/json} does.
     *
     * <p>Shortest round-trip digits; plain notation for 1e-6 &lt;= |f| &lt; 1e21, exponent notation otherwise
     * ({@code 1e+21}, {@code 1e-7}).
     */
    static String formatFloat(double f) {
        if (Double.isNaN(f) || Double.isInfinite(f)) {
            throw new IllegalArgumentException("json: unsupported value: " + f);
        }
        if (f == 0) {
            return (1 / f < 0) ? "-0" : "0";
        }
        double abs = Math.abs(f);
        // Double.toString yields the shortest digits that round-trip (JDK 19+), as Go does.
        BigDecimal bd = new BigDecimal(Double.toString(f)).stripTrailingZeros();
        if (abs >= 1e-6 && abs < 1e21) {
            return bd.toPlainString();
        }
        String digits = bd.unscaledValue().abs().toString();
        int exponent = digits.length() - 1 - bd.scale();
        StringBuilder sb = new StringBuilder();
        if (f < 0) {
            sb.append('-');
        }
        sb.append(digits.charAt(0));
        if (digits.length() > 1) {
            sb.append('.').append(digits, 1, digits.length());
        }
        sb.append('e').append(exponent < 0 ? '-' : '+');
        int absExp = Math.abs(exponent);
        // Go writes at least two exponent digits, then encoding/json trims "e-07" to "e-7".
        if (absExp < 10 && exponent >= 0) {
            sb.append('0');
        }
        sb.append(absExp);
        return sb.toString();
    }
}
