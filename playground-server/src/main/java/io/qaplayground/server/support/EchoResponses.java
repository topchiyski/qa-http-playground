package io.qaplayground.server.support;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Builds the three JSON "echo" shapes go-httpbin returns, field order included.
 *
 * <pre>
 * noBody:    args, headers, method, origin, url
 * withBody:  args, headers, method, origin, url, data, files, form, json
 * discarded: args, headers, method, origin, url, bytes_received
 * </pre>
 * <p>Echo endpoints are the heart of httpbin: they show you exactly what your client sent.
 */
@Component
public class EchoResponses {

    private final RequestInfo info;

    public EchoResponses(RequestInfo info) {
        this.info = info;
    }

    /**
     * The shape for {@code /get}, {@code /cache}, {@code /etag} and friends, which have no body fields.
     */
    public Map<String, Object> noBody(HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("args", info.args(request));
        body.put("headers", info.headers(request));
        body.put("method", request.getMethod());
        body.put("origin", info.clientIp(request));
        body.put("url", info.url(request));
        return body;
    }

    /**
     * The shape for {@code /post}, {@code /put}, {@code /anything} and friends.
     *
     * <p>A malformed body gets 400 with {@code "detail": "error parsing request body: ..."}.
     */
    public ResponseEntity<byte[]> withBody(HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("args", info.args(request));
        body.put("headers", info.headers(request));
        body.put("method", request.getMethod());
        body.put("origin", info.clientIp(request));
        body.put("url", info.url(request));
        try {
            RequestBodyParser.Body parsed = RequestBodyParser.parse(request);
            body.put("data", parsed.data());
            body.put("files", parsed.files());
            body.put("form", parsed.form());
            body.put("json", parsed.json());
        } catch (RequestBodyParser.BodyParseException e) {
            return HttpbinResponses.error(400, "error parsing request body: " + e.getMessage());
        }
        return HttpbinResponses.json(body);
    }

    /**
     * The shape for {@code /upload}: reads the body, throws it away, and reports only its size.
     */
    public ResponseEntity<byte[]> discarded(HttpServletRequest request) {
        Map<String, Object> body = noBody(request);
        long received = 0;
        try (var in = request.getInputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                received += n;
            }
        } catch (IOException e) {
            // go-httpbin reports a failed read (e.g. body too large) as 500.
            return HttpbinResponses.error(500, e.getMessage());
        }
        body.put("bytes_received", received);
        return HttpbinResponses.json(body);
    }
}
