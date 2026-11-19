package io.qaplayground.server.web;

import java.util.Set;
import java.util.TreeSet;

import io.qaplayground.server.support.HttpbinResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Makes "no such route" and "wrong method" look like Go's {@code http.ServeMux}, the router go-httpbin uses:
 * <pre>
 * 404  text/plain  "404 page not found\n"
 * 405  text/plain  "Method Not Allowed\n"   + Allow: GET, HEAD
 * </pre>
 * Without this, Spring would answer in its own JSON error format, and tests written for the reference would fail.
 */
@RestControllerAdvice
public class GoMuxErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(GoMuxErrorHandler.class);

    /**
     * No route matches: Go's plain-text 404.
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<byte[]> notFound() {
        return goHttpError(404, "404 page not found");
    }

    /**
     * The route exists, but not for this method: 405 with an {@code Allow} header.
     *
     * <p>Go lists the allowed methods sorted, and every GET route also allows HEAD.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<byte[]> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        Set<String> allowed = new TreeSet<>();
        Set<HttpMethod> supported = e.getSupportedHttpMethods();
        if (supported != null) {
            supported.forEach(m -> allowed.add(m.name()));
        }
        if (allowed.contains("GET")) {
            allowed.add("HEAD");
        }
        allowed.remove("OPTIONS");
        return ResponseEntity.status(405)
                .header("Allow", String.join(", ", allowed))
                .header("Content-Type", HttpbinResponses.TEXT)
                .header("X-Content-Type-Options", "nosniff")
                .body("Method Not Allowed\n".getBytes());
    }

    /**
     * Anything unexpected becomes go-httpbin's JSON error shape, never a stack trace.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<byte[]> unexpected(Exception e, HttpServletRequest request) {
        log.error("Unhandled error for {} {}", request.getMethod(), request.getRequestURI(), e);
        return HttpbinResponses.error(500, e.getMessage());
    }

    /**
     * Go's {@code http.Error}: plain text, a trailing newline, and {@code nosniff}.
     */
    static ResponseEntity<byte[]> goHttpError(int status, String message) {
        return ResponseEntity.status(status)
                .header("Content-Type", HttpbinResponses.TEXT)
                .header("X-Content-Type-Options", "nosniff")
                .body((message + "\n").getBytes());
    }
}
