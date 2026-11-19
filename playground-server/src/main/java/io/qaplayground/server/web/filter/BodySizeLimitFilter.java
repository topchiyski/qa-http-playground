package io.qaplayground.server.web.filter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import io.qaplayground.server.config.PlaygroundProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Caps how many request-body bytes a handler may read ({@code playground.max-body-size}).
 *
 * <p>It matches go-httpbin's {@code limitRequestSize} middleware, which wraps the body in Go's
 * {@code http.MaxBytesReader}. Reading past the limit throws {@link BodyTooLargeException} with Go's message, and
 * the endpoints turn that into a 400.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class BodySizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;

    public BodySizeLimitFilter(PlaygroundProperties properties) {
        this.maxBytes = properties.maxBodyBytes();
    }

    /**
     * Hands the request on with a body that stops at the limit.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(new LimitedRequest(request, maxBytes), response);
    }

    /**
     * Thrown when a client sends more than the configured maximum. The message is Go's, word for word.
     */
    public static class BodyTooLargeException extends IOException {
        public BodyTooLargeException() {
            super("http: request body too large");
        }
    }

    /**
     * A request whose body stops at the limit, whichever way a handler reads it.
     *
     * <p>Both {@link #getInputStream()} and {@link #getReader()} go through the same limited stream. Without the
     * second override, the reader would come straight from the original request and skip the limit. The limited
     * stream is made only when a handler reads the body.
     */
    private static final class LimitedRequest extends HttpServletRequestWrapper {
        private final long maxBytes;
        private ServletInputStream stream;
        private BufferedReader reader;

        LimitedRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new LimitedInputStream(super.getInputStream(), maxBytes);
            }
            return stream;
        }

        /**
         * Decodes the limited stream with the request's charset, or ISO-8859-1 (the Servlet default) if it has none.
         */
        @Override
        public BufferedReader getReader() throws IOException {
            if (reader == null) {
                String encoding = getCharacterEncoding();
                Charset charset = encoding == null ? StandardCharsets.ISO_8859_1 : Charset.forName(encoding);
                reader = new BufferedReader(new InputStreamReader(getInputStream(), charset));
            }
            return reader;
        }
    }

    /**
     * Counts bytes as they are read, and throws as soon as the count passes the limit.
     */
    private static final class LimitedInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private long remaining;

        LimitedInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.remaining = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0 && --remaining < 0) {
                throw new BodyTooLargeException();
            }
            return b;
        }

        /**
         * Reads a block, asking for one byte more than allowed. That extra byte tells "exactly at the limit" apart
         * from "over the limit".
         */
        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            // Ask for one byte more than allowed so we can tell "exactly at limit" from "over".
            int n = delegate.read(buf, off, (int) Math.min(len, remaining + 1));
            if (n > 0) {
                remaining -= n;
                if (remaining < 0) {
                    throw new BodyTooLargeException();
                }
            }
            return n;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}
