package io.qaplayground.server.web.filter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Permissive CORS on every response, and an immediate 200 for every {@code OPTIONS} request.
 *
 * <p>This mirrors go-httpbin's {@code preflight} middleware, which runs <em>before</em> routing. That is why
 * {@code OPTIONS /status/500} or {@code OPTIONS /does-not-exist} answer 200 in go-httpbin: the router never sees
 * them. Browsers send such an {@code OPTIONS} "preflight" before cross-origin requests that use non-simple methods
 * or headers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorsPreflightFilter extends OncePerRequestFilter {

    /**
     * Allows the caller's origin (or any origin), and answers preflights here, before any controller runs.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        response.setHeader("Access-Control-Allow-Origin", origin == null || origin.isEmpty() ? "*" : origin);
        response.setHeader("Access-Control-Allow-Credentials", "true");

        if ("OPTIONS".equals(request.getMethod())) {
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, HEAD, PUT, DELETE, PATCH, QUERY, OPTIONS");
            response.setHeader("Access-Control-Max-Age", "3600");
            String requestHeaders = request.getHeader("Access-Control-Request-Headers");
            if (requestHeaders != null && !requestHeaders.isEmpty()) {
                response.setHeader("Access-Control-Allow-Headers", requestHeaders);
            }
            response.setStatus(200);
            return;
        }
        chain.doFilter(request, response);
    }
}
