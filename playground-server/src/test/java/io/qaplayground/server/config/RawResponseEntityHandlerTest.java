package io.qaplayground.server.config;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one place that copies a {@code ResponseEntity<byte[]>} onto the servlet response.
 *
 * <p>Endpoints that write to the response themselves (streams, upgrades, ranges) use it for their error answers, so
 * an error looks the same on the wire whichever way the endpoint produced it.
 */
class RawResponseEntityHandlerTest {

    @Test
    void copiesStatusHeadersAndBody() throws Exception {
        var response = new MockHttpServletResponse();
        RawResponseEntityHandler.write(response, ResponseEntity.status(400)
                .header("Content-Type", "text/plain; charset=utf-8")
                .body("bad\n".getBytes(StandardCharsets.UTF_8)));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).isEqualTo("text/plain; charset=utf-8");
        assertThat(response.getContentAsString()).isEqualTo("bad\n");
        assertThat(response.getContentLength()).isEqualTo(4);
    }

    /**
     * A header sent twice stays twice. Replacing instead of adding would keep only the last value.
     */
    @Test
    void keepsEveryValueOfARepeatedHeader() throws Exception {
        var response = new MockHttpServletResponse();
        RawResponseEntityHandler.write(response, ResponseEntity.ok()
                .header("Set-Cookie", "a=1", "b=2")
                .body(new byte[0]));

        assertThat(response.getHeaders("Set-Cookie")).containsExactly("a=1", "b=2");
    }

    /**
     * Go answers a handler that wrote nothing with {@code Content-Length: 0}; a null body must not throw.
     */
    @Test
    void anEmptyRedirectGetsAZeroContentLength() throws Exception {
        var response = new MockHttpServletResponse();
        RawResponseEntityHandler.write(response, ResponseEntity.status(302).header("Location", "/get").build());

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Content-Length")).isEqualTo("0");
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    /**
     * 204 and 304 never carry a body, so they get no {@code Content-Length} either.
     */
    @Test
    void noContentGetsNoContentLength() throws Exception {
        var response = new MockHttpServletResponse();
        RawResponseEntityHandler.write(response, ResponseEntity.status(204).build());

        assertThat(response.getHeader("Content-Length")).isNull();
    }
}
