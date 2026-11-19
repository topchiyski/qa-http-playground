package io.qaplayground.server.web;

import java.nio.charset.StandardCharsets;

import io.qaplayground.server.support.EchoResponses;
import io.qaplayground.server.support.HttpbinResponses;
import io.qaplayground.server.support.StaticAssets;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * One endpoint per HTTP method.
 *
 * <p>Each accepts only its own method. Anything else gets 405 with an {@code Allow} header, which makes these good
 * targets for negative tests.
 */
@Tag(name = "HTTP methods", description = "Method-restricted echo endpoints")
@RestController
public class MethodsController {

    /**
     * go-httpbin's form template, rendered with an empty path prefix.
     */
    private static final byte[] FORMS_POST_HTML =
            new String(StaticAssets.get("static-httpbin/forms-post.html.tmpl"), StandardCharsets.UTF_8)
                    .replace("{{.Prefix}}", "")
                    .getBytes(StandardCharsets.UTF_8);

    private final EchoResponses echo;

    public MethodsController(EchoResponses echo) {
        this.echo = echo;
    }

    /**
     * Echoes a POST request, parsed body included.
     *
     * <p><b>HTTP concept:</b> POST creates or submits. The body format is declared by {@code Content-Type}.
     */
    @Operation(summary = "Echo a POST request and its body")
    @PostMapping("/post")
    public ResponseEntity<byte[]> post(HttpServletRequest request) {
        return echo.withBody(request);
    }

    /**
     * Echoes a PUT request, parsed body included.
     *
     * <p><b>HTTP concept:</b> PUT replaces a resource, and it is idempotent: sending it twice leaves the same state as
     * sending it once.
     */
    @Operation(summary = "Echo a PUT request and its body")
    @PutMapping("/put")
    public ResponseEntity<byte[]> put(HttpServletRequest request) {
        return echo.withBody(request);
    }

    /**
     * Echoes a PATCH request, parsed body included.
     *
     * <p><b>HTTP concept:</b> PATCH applies a partial update.
     */
    @Operation(summary = "Echo a PATCH request and its body")
    @PatchMapping("/patch")
    public ResponseEntity<byte[]> patch(HttpServletRequest request) {
        return echo.withBody(request);
    }

    /**
     * Echoes a DELETE request. A body is allowed and echoed, though many servers ignore it.
     *
     * <p><b>HTTP concept:</b> DELETE is idempotent, and a body on DELETE has no defined meaning.
     */
    @Operation(summary = "Echo a DELETE request")
    @DeleteMapping("/delete")
    public ResponseEntity<byte[]> delete(HttpServletRequest request) {
        return echo.withBody(request);
    }

    /**
     * Accepts only HEAD: the headers a GET would return, with no body.
     *
     * <p><b>HTTP concept:</b> HEAD checks a resource (size, type, caching headers) without downloading it.
     * {@code GET /head} is 405.
     */
    @Operation(summary = "HEAD-only endpoint")
    @RequestMapping(value = "/head", method = RequestMethod.HEAD)
    public ResponseEntity<byte[]> head(HttpServletRequest request) {
        return HttpbinResponses.json(echo.noBody(request));
    }

    /**
     * Reads and discards the body, reporting {@code bytes_received}. Accepts POST, PUT and PATCH.
     *
     * <p><b>HTTP concept:</b> upload throughput. Useful in load tests, because the server does no work with the
     * payload.
     */
    @Operation(summary = "Upload sink: discard the body, report its size")
    @RequestMapping(value = "/upload", method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH})
    public ResponseEntity<byte[]> upload(HttpServletRequest request) {
        return echo.discarded(request);
    }

    /**
     * An HTML pizza-order form that submits to {@code /post}.
     *
     * <p><b>HTTP concept:</b> what a browser sends for an {@code application/x-www-form-urlencoded} form. Ticking
     * several
     * checkboxes sends the same field several times.
     */
    @Operation(summary = "HTML form that posts to /post")
    @GetMapping("/forms/post")
    public ResponseEntity<byte[]> formsPost() {
        return HttpbinResponses.html(FORMS_POST_HTML);
    }
}
