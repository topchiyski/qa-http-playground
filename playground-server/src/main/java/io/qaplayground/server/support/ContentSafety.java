package io.qaplayground.server.support;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Keeps client-chosen content types from turning into a reflected-XSS hole.
 *
 * <p>Some endpoints let the client choose the response {@code Content-Type}. If a browser renders a body the client
 * controls as HTML, any script in it runs. So, like go-httpbin, we HTML-escape such bodies unless the type is known
 * to be safe.
 */
public final class ContentSafety {

    /**
     * The media types a browser never renders as a page, so their bodies are left alone.
     */
    private static final Set<String> SAFE = Set.of("text/plain", "application/json", "application/octet-string");
    /**
     * A syntactically valid {@code type/subtype}. Anything else counts as dangerous.
     */
    private static final Pattern MEDIA_TYPE = Pattern.compile("[A-Za-z0-9!#$%&'*+.^_`|~-]+/[A-Za-z0-9!#$%&'*+.^_`|~-]+");

    private ContentSafety() {
    }

    /**
     * Whether a body with this content type must be escaped: true unless the type parses and is on the safe list.
     */
    public static boolean isDangerous(String contentType) {
        String mediaType = contentType.split(";", 2)[0].trim();
        if (!MEDIA_TYPE.matcher(mediaType).matches()) {
            return true; // unparseable: assume the worst, as Go's mime.ParseMediaType error path does
        }
        return !SAFE.contains(mediaType.toLowerCase());
    }

    /**
     * Go's {@code html.EscapeString}: escapes {@code < > & ' "}, and nothing else.
     */
    public static String escapeHtml(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '&' -> sb.append("&amp;");
                case '\'' -> sb.append("&#39;");
                case '"' -> sb.append("&#34;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
