package io.qaplayground.server.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

/**
 * Writes a {@code ResponseEntity<byte[]>} to the wire exactly as built: status, headers, body.
 *
 * <p>Why not let Spring do it? For GET requests, Spring MVC runs its own conditional-request logic on any
 * {@code ResponseEntity} that carries {@code ETag} or {@code Last-Modified}. It rewrites those headers (quoting
 * ETags, re-formatting dates) and may answer 304 by itself. httpbin endpoints must behave exactly like the
 * reference, so their responses bypass that machinery.
 */
@Configuration
public class RawResponseEntityHandler implements HandlerMethodReturnValueHandler {

    /**
     * Claims only {@code ResponseEntity<byte[]>}, the type every httpbin endpoint returns. Everything else is left to
     * Spring.
     */
    @Override
    public boolean supportsReturnType(MethodParameter returnType) {
        if (!ResponseEntity.class.isAssignableFrom(returnType.getParameterType())) {
            return false;
        }
        Class<?> body = ResolvableType.forMethodParameter(returnType).getGeneric(0).resolve();
        return body == byte[].class;
    }

    /**
     * Copies status, headers and body to the servlet response, untouched.
     */
    @Override
    public void handleReturnValue(Object returnValue, MethodParameter returnType,
                                  ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {
        mavContainer.setRequestHandled(true);
        if (!(returnValue instanceof ResponseEntity<?> entity)) {
            return;
        }
        // supportsReturnType admits only ResponseEntity<byte[]>.
        @SuppressWarnings("unchecked")
        ResponseEntity<byte[]> bytes = (ResponseEntity<byte[]>) entity;
        write(webRequest.getNativeResponse(HttpServletResponse.class), bytes);
    }

    /**
     * Copies status, headers and body to {@code response}, untouched, and flushes it.
     *
     * <p>Endpoints that write to the response themselves (streams, upgrades, ranges) call this for their error
     * answers, so every error leaves the server the same way: repeated headers kept, and an empty body answered with
     * {@code Content-Length: 0} as Go does.
     */
    public static void write(HttpServletResponse response, ResponseEntity<byte[]> entity) throws IOException {
        response.setStatus(entity.getStatusCode().value());
        for (Map.Entry<String, List<String>> header : entity.getHeaders().headerSet()) {
            for (String value : header.getValue()) {
                response.addHeader(header.getKey(), value);
            }
        }
        byte[] body = entity.getBody();
        int status = entity.getStatusCode().value();
        if (body != null) {
            if (!entity.getHeaders().containsHeader("Content-Length")) {
                response.setContentLength(body.length);
            }
            response.getOutputStream().write(body);
        } else if (status >= 200 && status != 204 && status != 304
                && !entity.getHeaders().containsHeader("Content-Length")) {
            // Go answers a handler that wrote nothing with "Content-Length: 0"; without this,
            // Tomcat would switch to chunked encoding for e.g. an empty 302.
            response.setContentLength(0);
        }
        response.flushBuffer();
    }

    /**
     * Puts this handler in front of Spring's own {@code ResponseEntity} handling, so it wins.
     */
    @Bean
    static BeanPostProcessor rawResponseEntityHandlerFirst() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof RequestMappingHandlerAdapter adapter && adapter.getReturnValueHandlers() != null) {
                    List<HandlerMethodReturnValueHandler> handlers = new ArrayList<>(adapter.getReturnValueHandlers());
                    handlers.addFirst(new RawResponseEntityHandler());
                    adapter.setReturnValueHandlers(handlers);
                }
                return bean;
            }
        };
    }
}
