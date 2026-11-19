package io.qaplayground.server.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a request body into httpbin's {@code data}, {@code form}, {@code files} and {@code json} fields. A port
 * of go-httpbin's {@code parseBody}.
 *
 * <p>The {@code Content-Type} decides what happens. It is compared exactly, as go-httpbin does: only the part before
 * {@code ;} counts, and it is case-sensitive.
 * <ul>
 *   <li>{@code text/plain}, {@code text/html}: the body is copied to {@code data};</li>
 *   <li>{@code application/x-www-form-urlencoded}: fields are parsed into {@code form};</li>
 *   <li>{@code multipart/form-data}: fields go into {@code form}, uploaded files into {@code files};</li>
 *   <li>{@code application/json}: parsed into {@code json};</li>
 *   <li>anything else: {@code data} becomes a {@code data:<type>;base64,...} URL.</li>
 * </ul>
 */
public final class RequestBodyParser {

    /**
     * Reads JSON bodies. Its output is converted to Go's types afterwards, see {@link #parseJson}.
     */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private RequestBodyParser() {
    }

    /**
     * The parsed body fields. {@code json} is {@code null} unless a JSON body was sent.
     */
    public record Body(String data, Map<String, List<String>> form, Map<String, List<String>> files, Object json) {
        /**
         * No body: empty data, form and files, and no JSON.
         */
        static Body empty() {
            return new Body("", new TreeMap<>(), new TreeMap<>(), null);
        }
    }

    /**
     * Thrown for any malformed body. It becomes a 400 "error parsing request body: ..." response.
     */
    public static final class BodyParseException extends Exception {
        public BodyParseException(String message) {
            super(message);
        }
    }

    /**
     * Reads the whole request body, within the max-body-size limit.
     */
    public static byte[] readBody(HttpServletRequest request) throws IOException {
        return request.getInputStream().readAllBytes();
    }

    /**
     * Reads the body and parses it according to its {@code Content-Type}, as listed in the class comment.
     */
    public static Body parse(HttpServletRequest request) throws BodyParseException {
        byte[] body;
        try {
            body = readBody(request);
        } catch (IOException e) {
            throw new BodyParseException(e.getMessage());
        }
        if (body.length == 0) {
            return Body.empty();
        }
        String rawContentType = request.getHeader("Content-Type");
        String contentType = rawContentType == null ? "" : rawContentType.split(";", 2)[0];
        String data = new String(body, StandardCharsets.UTF_8);

        return switch (contentType) {
            case "text/html", "text/plain" -> new Body(data, new TreeMap<>(), new TreeMap<>(), null);
            case "application/x-www-form-urlencoded" -> {
                checkQuery(request.getQueryString()); // Go's ParseForm also validates the URL query
                yield new Body(data, strictForm(data), new TreeMap<>(), null);
            }
            case "multipart/form-data" -> parseMultipart(data, body, rawContentType);
            case "application/json" -> new Body(data, new TreeMap<>(), new TreeMap<>(), parseJson(body));
            default -> new Body(dataUrl(body, contentType), new TreeMap<>(), new TreeMap<>(), null);
        };
    }

    /**
     * {@code data:application/octet-stream;base64,...}, with Go's URL-safe, padded alphabet.
     */
    static String dataUrl(byte[] body, String contentType) {
        String type = contentType.isEmpty() ? HttpbinResponses.BINARY : contentType;
        return "data:" + type + ";base64," + Base64.getUrlEncoder().encodeToString(body);
    }

    /**
     * Parses a form body, failing on the first bad pair as {@code r.ParseForm()} does.
     */
    private static Map<String, List<String>> strictForm(String body) throws BodyParseException {
        String error = QueryString.firstError(body);
        if (error != null) {
            throw new BodyParseException(error);
        }
        return new TreeMap<>(QueryString.parse(body));
    }

    /**
     * Fails on a bad pair in the URL query: Go's {@code ParseForm} checks the query as well as the body.
     */
    private static void checkQuery(String rawQuery) throws BodyParseException {
        String error = QueryString.firstError(rawQuery);
        if (error != null) {
            throw new BodyParseException(error);
        }
    }

    /**
     * Decodes JSON the way Go decodes into {@code any}: objects sorted by key, every number a float64.
     */
    static Object parseJson(byte[] body) throws BodyParseException {
        try {
            return toGoValue(JSON.readValue(body, Object.class));
        } catch (JacksonException e) {
            String message = e.getOriginalMessage();
            if (message != null && message.startsWith("No content")) {
                message = "EOF";
            }
            throw new BodyParseException(message);
        }
    }

    /**
     * Converts a decoded value to its Go shape: maps sorted, numbers as doubles, lists converted element by element.
     */
    private static Object toGoValue(Object value) {
        return switch (value) {
            case Map<?, ?> map -> {
                Map<String, Object> sorted = new TreeMap<>();
                map.forEach((k, v) -> sorted.put(String.valueOf(k), toGoValue(v)));
                yield sorted;
            }
            case List<?> list -> {
                List<Object> out = new ArrayList<>(list.size());
                list.forEach(v -> out.add(toGoValue(v)));
                yield out;
            }
            case Number n -> n.doubleValue();
            case null, default -> value;
        };
    }

    // ---- multipart/form-data ------------------------------------------------------------------

    /**
     * Parses a {@code multipart/form-data} body into form fields and files.
     */
    private static Body parseMultipart(String data, byte[] body, String rawContentType) throws BodyParseException {
        String boundary = parameter(rawContentType, "boundary");
        if (boundary == null || boundary.isEmpty()) {
            throw new BodyParseException("no multipart boundary param in Content-Type");
        }
        Map<String, List<String>> form = new TreeMap<>();
        Map<String, List<String>> files = new TreeMap<>();
        for (Part part : splitParts(body, boundary)) {
            String disposition = part.headers().getOrDefault("content-disposition", "");
            if (!disposition.toLowerCase().startsWith("form-data")) {
                continue;
            }
            String name = parameter(disposition, "name");
            if (name == null || name.isEmpty()) {
                continue;
            }
            String filename = parameter(disposition, "filename");
            String content = new String(part.content(), StandardCharsets.UTF_8);
            if (filename == null || filename.isEmpty()) {
                form.computeIfAbsent(name, k -> new ArrayList<>()).add(content);
            } else {
                files.computeIfAbsent(name, k -> new ArrayList<>()).add(content);
            }
        }
        return new Body(data, form, files, null);
    }

    /**
     * One part of a multipart body: its headers and its raw content.
     */
    private record Part(Map<String, String> headers, byte[] content) {
    }

    /**
     * Splits a multipart body at its boundary lines. A missing boundary or a malformed part header fails with Go's
     * message.
     */
    private static List<Part> splitParts(byte[] body, String boundary) throws BodyParseException {
        byte[] delimiter = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        List<Part> parts = new ArrayList<>();
        int pos = indexOf(body, delimiter, 0);
        if (pos < 0) {
            throw new BodyParseException("multipart: NextPart: EOF");
        }
        while (true) {
            int afterDelimiter = pos + delimiter.length;
            if (startsWith(body, afterDelimiter, "--")) {
                return parts; // closing delimiter
            }
            int headersStart = skipLineBreak(body, afterDelimiter);
            int next = indexOf(body, delimiter, headersStart);
            if (next < 0) {
                throw new BodyParseException("multipart: NextPart: EOF");
            }
            // The CRLF before the next delimiter belongs to the delimiter, not the content.
            int contentEnd = next;
            if (contentEnd >= 2 && body[contentEnd - 2] == '\r' && body[contentEnd - 1] == '\n') {
                contentEnd -= 2;
            } else if (contentEnd >= 1 && body[contentEnd - 1] == '\n') {
                contentEnd -= 1;
            }
            int headerEnd = indexOf(body, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), headersStart);
            int contentStart = headerEnd + 4;
            if (headerEnd < 0 || headerEnd > contentEnd) {
                headerEnd = indexOf(body, "\n\n".getBytes(StandardCharsets.ISO_8859_1), headersStart);
                contentStart = headerEnd + 2;
            }
            if (headerEnd < 0 || contentStart > contentEnd + 2) {
                throw new BodyParseException("multipart: NextPart: malformed MIME header");
            }
            Map<String, String> headers = new LinkedHashMap<>();
            String headerBlock = new String(body, headersStart, headerEnd - headersStart, StandardCharsets.UTF_8);
            for (String line : headerBlock.split("\r?\n")) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(line.substring(0, colon).trim().toLowerCase(), line.substring(colon + 1).trim());
                }
            }
            byte[] content = java.util.Arrays.copyOfRange(body, Math.min(contentStart, contentEnd), contentEnd);
            parts.add(new Part(headers, content));
            pos = next;
        }
    }

    /**
     * Skips spaces and tabs, then one line break ({@code CRLF} or {@code LF}). Returns the next index.
     */
    private static int skipLineBreak(byte[] b, int i) {
        while (i < b.length && (b[i] == ' ' || b[i] == '\t')) {
            i++;
        }
        if (i < b.length && b[i] == '\r') {
            i++;
        }
        if (i < b.length && b[i] == '\n') {
            i++;
        }
        return i;
    }

    /**
     * Whether the bytes at {@code at} spell {@code s}.
     */
    private static boolean startsWith(byte[] b, int at, String s) {
        if (at + s.length() > b.length) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (b[at + i] != s.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The first position of {@code needle} in {@code haystack} at or after {@code from}, or -1.
     */
    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = Math.max(from, 0); i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    /**
     * Reads {@code name="value"} or {@code name=value} from a header such as {@code Content-Disposition}.
     */
    static String parameter(String header, String name) {
        if (header == null) {
            return null;
        }
        for (String piece : header.split(";")) {
            String p = piece.trim();
            int eq = p.indexOf('=');
            if (eq > 0 && p.substring(0, eq).trim().equalsIgnoreCase(name)) {
                String v = p.substring(eq + 1).trim();
                if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
                    v = v.substring(1, v.length() - 1).replace("\\\"", "\"");
                }
                return v;
            }
        }
        return null;
    }
}
