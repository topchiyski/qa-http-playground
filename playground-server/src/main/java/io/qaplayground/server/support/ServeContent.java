package io.qaplayground.server.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * A port of Go's {@code http.ServeContent}, which go-httpbin uses for {@code /etag/{etag}} and {@code /range/{n}}.
 *
 * <p>It handles two families of HTTP features in one place:
 * <ul>
 *   <li><b>Conditional requests</b> (RFC 9110 §13). {@code If-Match} and {@code If-Unmodified-Since} can fail with
 *       412 Precondition Failed. {@code If-None-Match} and {@code If-Modified-Since} can short-circuit to 304 Not
 *       Modified.</li>
 *   <li><b>Range requests</b> (RFC 9110 §14). {@code Range: bytes=0-99} gives 206 Partial Content with
 *       {@code Content-Range}. Several ranges give a {@code multipart/byteranges} body. Impossible ranges give 416
 *       Range Not Satisfiable. {@code If-Range} makes a range request depend on the resource being unchanged.</li>
 * </ul>
 * The caller sets {@code ETag} and {@code Content-Type} on the response before calling.
 */
public final class ServeContent {

    /**
     * The resource being served, read in pieces so a range never needs the whole body in memory.
     */
    @FunctionalInterface
    public interface Content {
        void write(OutputStream out, long offset, long length) throws IOException;
    }

    /**
     * Go's {@code http.TimeFormat}: {@code Mon, 02 Jan 2006 15:04:05 GMT}.
     */
    public static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * The result of one precondition: header absent, condition true, or condition false.
     */
    private enum Cond { NONE, TRUE, FALSE }

    /**
     * One byte range to send: where it starts and how long it is.
     */
    record Range(long start, long length) {
        /**
         * The {@code Content-Range} value for this range, such as {@code bytes 0-9/26}.
         */
        String contentRange(long size) {
            return "bytes " + start + "-" + (start + length - 1) + "/" + size;
        }
    }

    private ServeContent() {
    }

    /**
     * Answers the request: checks the preconditions, then sends the full resource, one range, or several.
     *
     * @param modtime     last-modified time of the resource, or null if unknown
     * @param contentType the resource's media type, as written into multipart part headers
     */
    public static void serve(HttpServletRequest request, HttpServletResponse response, long size,
                             Instant modtime, String contentType, Content content) throws IOException {
        response.setHeader("Content-Type", contentType);
        if (modtime != null) {
            response.setHeader("Last-Modified", formatHttpDate(modtime));
        }
        String etag = response.getHeader("ETag");

        // --- preconditions -------------------------------------------------------------------
        Cond ch = checkIfMatch(request, etag);
        if (ch == Cond.NONE) {
            ch = checkIfUnmodifiedSince(request, modtime);
        }
        if (ch == Cond.FALSE) {
            response.setStatus(412);
            return;
        }
        switch (checkIfNoneMatch(request, etag)) {
            case FALSE -> {
                if (isGetOrHead(request)) {
                    writeNotModified(response);
                } else {
                    response.setStatus(412);
                }
                return;
            }
            case NONE -> {
                if (checkIfModifiedSince(request, modtime) == Cond.FALSE) {
                    writeNotModified(response);
                    return;
                }
            }
            default -> {
            }
        }
        String rangeHeader = request.getHeader("Range");
        if (rangeHeader != null && !rangeHeader.isEmpty() && checkIfRange(request, etag, modtime) == Cond.FALSE) {
            rangeHeader = null;
        }

        // --- ranges ---------------------------------------------------------------------------
        List<Range> ranges;
        try {
            ranges = parseRange(rangeHeader, size);
        } catch (RangeError e) {
            if (e.noOverlap && size == 0) {
                ranges = List.of();
            } else {
                if (e.noOverlap) {
                    response.setHeader("Content-Range", "bytes */" + size);
                }
                serveError(response, e.getMessage(), 416);
                return;
            }
        }
        long sum = ranges.stream().mapToLong(Range::length).sum();
        if (sum > size) {
            ranges = List.of(); // probably an attack or a confused client: ignore Range
        }

        int status = 200;
        long sendSize = size;
        byte[] multipartBody = null;
        if (ranges.size() == 1) {
            Range r = ranges.getFirst();
            status = 206;
            sendSize = r.length();
            response.setHeader("Content-Range", r.contentRange(size));
        } else if (ranges.size() > 1) {
            status = 206;
            String boundary = HexFormat.of().formatHex(randomBytes(30));
            multipartBody = multipart(ranges, size, contentType, boundary, content);
            sendSize = multipartBody.length;
            response.setCharacterEncoding((String) null); // otherwise Tomcat re-appends the old ";charset=utf-8"
            response.setContentType("multipart/byteranges; boundary=" + boundary);
        }
        response.setHeader("Accept-Ranges", "bytes");
        response.setContentLengthLong(sendSize);
        response.setStatus(status);
        if ("HEAD".equals(request.getMethod())) {
            return;
        }
        OutputStream out = response.getOutputStream();
        if (multipartBody != null) {
            out.write(multipartBody);
        } else if (ranges.size() == 1) {
            content.write(out, ranges.getFirst().start(), ranges.getFirst().length());
        } else {
            content.write(out, 0, size);
        }
        out.flush();
    }

    /**
     * Random bytes for a multipart boundary, as Go's writer makes them.
     */
    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    /**
     * Go's {@code mime/multipart.Writer} output for a set of ranges.
     */
    private static byte[] multipart(List<Range> ranges, long size, String contentType, String boundary,
                                    Content content) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        boolean first = true;
        for (Range r : ranges) {
            StringBuilder head = new StringBuilder();
            head.append(first ? "" : "\r\n").append("--").append(boundary).append("\r\n");
            head.append("Content-Range: ").append(r.contentRange(size)).append("\r\n");
            if (contentType != null && !contentType.isEmpty()) {
                head.append("Content-Type: ").append(contentType).append("\r\n");
            }
            head.append("\r\n");
            buf.write(head.toString().getBytes(StandardCharsets.UTF_8));
            content.write(buf, r.start(), r.length());
            first = false;
        }
        buf.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return buf.toByteArray();
    }

    /**
     * Whether the request is a GET or HEAD, the only methods some preconditions apply to.
     */
    private static boolean isGetOrHead(HttpServletRequest request) {
        return "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
    }

    /**
     * Answers 304 the way Go does: body-describing headers removed, and {@code Last-Modified} too when an
     * {@code ETag} is present.
     */
    private static void writeNotModified(HttpServletResponse response) {
        // Go removes body-describing headers; with an ETag present, Last-Modified goes too.
        response.setContentType(null);
        response.setHeader("Content-Type", null);
        response.setHeader("Content-Length", null);
        response.setHeader("Content-Encoding", null);
        if (response.getHeader("ETag") != null) {
            response.setHeader("Last-Modified", null);
        }
        response.setStatus(304);
    }

    /**
     * Go 1.23+ {@code serveError}: drops caching headers, then sends a plain-text {@code http.Error}.
     */
    private static void serveError(HttpServletResponse response, String text, int code) throws IOException {
        for (String h : List.of("Cache-Control", "Content-Encoding", "ETag", "Last-Modified")) {
            response.setHeader(h, null);
        }
        response.setStatus(code);
        response.setHeader("Content-Type", HttpbinResponses.TEXT);
        response.setHeader("X-Content-Type-Options", "nosniff");
        byte[] body = (text + "\n").getBytes(StandardCharsets.UTF_8);
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    // --- conditional request helpers (ported from net/http/fs.go) ------------------------------

    /**
     * {@code If-Match}: true when any listed tag strongly matches the current one, or the header is {@code *}.
     */
    private static Cond checkIfMatch(HttpServletRequest request, String etag) {
        String im = request.getHeader("If-Match");
        if (im == null || im.isEmpty()) {
            return Cond.NONE;
        }
        while (true) {
            im = im.strip();
            if (im.isEmpty()) {
                break;
            }
            if (im.charAt(0) == ',') {
                im = im.substring(1);
                continue;
            }
            if (im.charAt(0) == '*') {
                return Cond.TRUE;
            }
            String[] scanned = scanETag(im);
            if (scanned == null) {
                break;
            }
            if (strongMatch(scanned[0], etag)) {
                return Cond.TRUE;
            }
            im = scanned[1];
        }
        return Cond.FALSE;
    }

    /**
     * {@code If-None-Match}: false when any listed tag weakly matches the current one, or the header is {@code *}.
     */
    private static Cond checkIfNoneMatch(HttpServletRequest request, String etag) {
        String inm = request.getHeader("If-None-Match");
        if (inm == null || inm.isEmpty()) {
            return Cond.NONE;
        }
        String buf = inm;
        while (true) {
            buf = buf.strip();
            if (buf.isEmpty()) {
                break;
            }
            if (buf.charAt(0) == ',') {
                buf = buf.substring(1);
                continue;
            }
            if (buf.charAt(0) == '*') {
                return Cond.FALSE;
            }
            String[] scanned = scanETag(buf);
            if (scanned == null) {
                break;
            }
            if (weakMatch(scanned[0], etag)) {
                return Cond.FALSE;
            }
            buf = scanned[1];
        }
        return Cond.TRUE;
    }

    /**
     * {@code If-Unmodified-Since}: true when the resource hasn't changed since the given date.
     */
    private static Cond checkIfUnmodifiedSince(HttpServletRequest request, Instant modtime) {
        String ius = request.getHeader("If-Unmodified-Since");
        if (ius == null || ius.isEmpty() || modtime == null) {
            return Cond.NONE;
        }
        Instant t = parseHttpDate(ius);
        if (t == null) {
            return Cond.NONE;
        }
        return truncate(modtime).compareTo(t) <= 0 ? Cond.TRUE : Cond.FALSE;
    }

    /**
     * {@code If-Modified-Since}, for GET and HEAD only: false when the resource hasn't changed since the given date.
     */
    private static Cond checkIfModifiedSince(HttpServletRequest request, Instant modtime) {
        if (!isGetOrHead(request)) {
            return Cond.NONE;
        }
        String ims = request.getHeader("If-Modified-Since");
        if (ims == null || ims.isEmpty() || modtime == null) {
            return Cond.NONE;
        }
        Instant t = parseHttpDate(ims);
        if (t == null) {
            return Cond.NONE;
        }
        return truncate(modtime).compareTo(t) <= 0 ? Cond.FALSE : Cond.TRUE;
    }

    /**
     * {@code If-Range}: whether a range request may be answered with a range, or must get the full resource.
     */
    private static Cond checkIfRange(HttpServletRequest request, String etag, Instant modtime) {
        if (!isGetOrHead(request)) {
            return Cond.NONE;
        }
        String ir = request.getHeader("If-Range");
        if (ir == null || ir.isEmpty()) {
            return Cond.NONE;
        }
        String[] scanned = scanETag(ir);
        if (scanned != null) {
            return strongMatch(scanned[0], etag) ? Cond.TRUE : Cond.FALSE;
        }
        if (modtime == null) {
            return Cond.FALSE;
        }
        Instant t = parseHttpDate(ir);
        if (t == null) {
            return Cond.FALSE;
        }
        return t.getEpochSecond() == modtime.getEpochSecond() ? Cond.TRUE : Cond.FALSE;
    }

    /**
     * Reads one entity tag from the start of {@code s}. Returns {etag, remainder}, or null if none starts there.
     */
    static String[] scanETag(String s) {
        s = s.replaceFirst("^[ \t\n\r]+", "");
        int start = s.startsWith("W/") ? 2 : 0;
        if (s.length() - start < 2 || s.charAt(start) != '"') {
            return null;
        }
        for (int i = start + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 0x21 || (c >= 0x23 && c <= 0x7e) || c >= 0x80) {
                continue;
            }
            if (c == '"') {
                return new String[]{s.substring(0, i + 1), s.substring(i + 1)};
            }
            return null;
        }
        return null;
    }

    /**
     * Strong comparison: identical, and neither tag is weak.
     */
    private static boolean strongMatch(String a, String b) {
        return a.equals(b) && !a.isEmpty() && a.charAt(0) == '"';
    }

    /**
     * Weak comparison: identical once any {@code W/} prefix is removed.
     */
    private static boolean weakMatch(String a, String b) {
        return b != null && a.replaceFirst("^W/", "").equals(b.replaceFirst("^W/", ""));
    }

    /**
     * Drops sub-second precision, because HTTP dates only have whole seconds.
     */
    private static Instant truncate(Instant t) {
        return Instant.ofEpochSecond(t.getEpochSecond());
    }

    /**
     * Formats an instant as an HTTP date in GMT.
     */
    public static String formatHttpDate(Instant t) {
        return HTTP_DATE.format(t.atZone(ZoneOffset.UTC));
    }

    /**
     * Go's {@code http.ParseTime}: RFC 1123 (GMT), RFC 850 or ANSI C asctime. Null when none of them fits.
     */
    static Instant parseHttpDate(String s) {
        List<DateTimeFormatter> formats = new ArrayList<>(List.of(
                HTTP_DATE,
                DateTimeFormatter.ofPattern("EEEE, dd-MMM-yy HH:mm:ss 'GMT'", Locale.US),
                // asctime pads the day with a space ("Jan  2"), like Go's ANSIC layout "Jan _2".
                DateTimeFormatter.ofPattern("EEE MMM ppd HH:mm:ss yyyy", Locale.US)));
        for (DateTimeFormatter f : formats) {
            try {
                return ZonedDateTime.parse(s, f.withZone(ZoneOffset.UTC)).toInstant();
            } catch (RuntimeException ignored) {
                // try the next format
            }
        }
        return null;
    }

    // --- Range header parsing ------------------------------------------------------------------

    /**
     * A {@code Range} header that can't be served. {@code noOverlap} means it was valid but outside the resource.
     */
    static final class RangeError extends Exception {
        final boolean noOverlap;

        RangeError(String message, boolean noOverlap) {
            super(message);
            this.noOverlap = noOverlap;
        }
    }

    /**
     * A port of Go's {@code parseRange}. An empty list means there is no usable {@code Range} header.
     */
    static List<Range> parseRange(String s, long size) throws RangeError {
        List<Range> ranges = new ArrayList<>();
        if (s == null || s.isEmpty()) {
            return ranges;
        }
        if (!s.startsWith("bytes=")) {
            throw new RangeError("invalid range", false);
        }
        boolean noOverlap = false;
        for (String raw : s.substring(6).split(",", -1)) {
            String ra = raw.strip();
            if (ra.isEmpty()) {
                continue;
            }
            int dash = ra.indexOf('-');
            if (dash < 0) {
                throw new RangeError("invalid range", false);
            }
            String start = ra.substring(0, dash).strip();
            String end = ra.substring(dash + 1).strip();
            if (start.isEmpty()) {
                // "-500": the last 500 bytes
                if (end.isEmpty() || end.charAt(0) == '-') {
                    throw new RangeError("invalid range", false);
                }
                long i = parseNonNegative(end);
                if (i > size) {
                    i = size;
                }
                ranges.add(new Range(size - i, i));
            } else {
                long i = parseNonNegative(start);
                if (i >= size) {
                    noOverlap = true; // starts after the end of the content
                    continue;
                }
                if (end.isEmpty()) {
                    ranges.add(new Range(i, size - i));
                } else {
                    long e;
                    try {
                        e = GoStrconv.parseInt(end, 64);
                    } catch (GoStrconv.NumberFormatError ex) {
                        throw new RangeError("invalid range", false);
                    }
                    if (i > e) {
                        throw new RangeError("invalid range", false);
                    }
                    if (e >= size) {
                        e = size - 1;
                    }
                    ranges.add(new Range(i, e - i + 1));
                }
            }
        }
        if (noOverlap && ranges.isEmpty()) {
            throw new RangeError("invalid range: failed to overlap", true);
        }
        return ranges;
    }

    /**
     * Parses a byte position, rejecting negative numbers and anything that isn't a number.
     */
    private static long parseNonNegative(String s) throws RangeError {
        try {
            long v = GoStrconv.parseInt(s, 64);
            if (v < 0) {
                throw new RangeError("invalid range", false);
            }
            return v;
        } catch (GoStrconv.NumberFormatError e) {
            throw new RangeError("invalid range", false);
        }
    }
}
