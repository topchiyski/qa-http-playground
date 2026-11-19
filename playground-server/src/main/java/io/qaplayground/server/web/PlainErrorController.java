package io.qaplayground.server.web;

import io.qaplayground.server.support.HttpbinResponses;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Hidden;

/**
 * Replaces Spring Boot's "Whitelabel" error page.
 *
 * <p>Errors raised outside our controllers, for example by the servlet container, are shown in go-httpbin's style.
 * A client that asks for {@code /error} directly gets a 404, because that path doesn't exist in go-httpbin.
 */
@Hidden
@RestController
public class PlainErrorController implements ErrorController {

    /**
     * Renders the error the container recorded, or a plain 404 when there is none.
     */
    @RequestMapping("/error")
    public ResponseEntity<byte[]> error(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (!(status instanceof Integer code) || code == 404) {
            return GoMuxErrorHandler.goHttpError(404, "404 page not found");
        }
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        return HttpbinResponses.error(code, message == null ? null : message.toString());
    }
}
