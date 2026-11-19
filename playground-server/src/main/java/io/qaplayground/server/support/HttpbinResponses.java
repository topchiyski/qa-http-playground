package io.qaplayground.server.support;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Builders for the response shapes go-httpbin uses.
 *
 * <p>Controllers return {@code ResponseEntity<byte[]>}, so what you read in the code is exactly what goes on the
 * wire. No message converters, no content negotiation, no surprises.
 */
public final class HttpbinResponses {

    /**
     * {@code Content-Type} for JSON responses.
     */
    public static final String JSON = "application/json; charset=utf-8";
    /**
     * {@code Content-Type} for plain-text responses.
     */
    public static final String TEXT = "text/plain; charset=utf-8";
    /**
     * {@code Content-Type} for HTML pages.
     */
    public static final String HTML = "text/html; charset=utf-8";
    /**
     * {@code Content-Type} for raw bytes.
     */
    public static final String BINARY = "application/octet-stream";
    /**
     * {@code Content-Type} for JSON Lines: one JSON object per line.
     */
    public static final String JSONL = "application/x-ndjson; charset=utf-8";
    /**
     * {@code Content-Type} for server-sent events.
     */
    public static final String SSE = "text/event-stream; charset=utf-8";

    private HttpbinResponses() {
    }

    /**
     * Pretty-printed JSON with {@code Content-Type: application/json; charset=utf-8}.
     */
    public static ResponseEntity<byte[]> json(int status, Object body) {
        return bytes(status, JSON, GoJson.pretty(body));
    }

    /**
     * Pretty-printed JSON with status 200.
     */
    public static ResponseEntity<byte[]> json(Object body) {
        return json(200, body);
    }

    /**
     * A plain-text response in UTF-8.
     */
    public static ResponseEntity<byte[]> text(int status, String body) {
        return bytes(status, TEXT, body.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * An HTML page with status 200.
     */
    public static ResponseEntity<byte[]> html(byte[] body) {
        return bytes(200, HTML, body);
    }

    /**
     * Any body with any content type, written exactly as given.
     */
    public static ResponseEntity<byte[]> bytes(int status, String contentType, byte[] body) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(body);
    }

    /**
     * Re-wraps a ready-made response for a handler that is declared to return
     * {@code ResponseEntity<StreamingResponseBody>}.
     *
     * <p>Spring picks its streaming machinery from the declared type, so a streaming endpoint's error responses need
     * that type too.
     */
    public static ResponseEntity<StreamingResponseBody> asStream(ResponseEntity<byte[]> entity) {
        byte[] body = entity.getBody() == null ? new byte[0] : entity.getBody();
        return ResponseEntity.status(entity.getStatusCode())
                .headers(entity.getHeaders())
                .contentLength(body.length)
                .body(out -> out.write(body));
    }

    /**
     * go-httpbin's standard error response:
     * <pre>{"status_code": 400, "error": "Bad Request", "detail": "..."}</pre>
     * {@code detail} is left out when null, like Go's {@code omitempty}.
     */
    public static ResponseEntity<byte[]> error(int status, String detail) {
        return json(status, errorBody(status, detail));
    }

    /**
     * The body of {@link #error(int, String)}, for endpoints that write it themselves.
     */
    public static Map<String, Object> errorBody(int status, String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status_code", status);
        body.put("error", StatusText.of(status));
        if (detail != null && !detail.isEmpty()) {
            body.put("detail", detail);
        }
        return body;
    }
}
