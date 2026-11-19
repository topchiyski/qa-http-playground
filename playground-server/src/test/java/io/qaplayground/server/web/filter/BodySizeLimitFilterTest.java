package io.qaplayground.server.web.filter;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.qaplayground.server.TestProperties;
import io.qaplayground.testing.technique.BoundaryValue;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The body limit holds whichever way a handler reads the body.
 *
 * <p>The filter swaps the request for a wrapper. A wrapper is only safe if it can stand in for the original
 * everywhere: if one read path skipped the limit, the next endpoint to use it would accept any size of body.
 */
class BodySizeLimitFilterTest {

    private static final int LIMIT = 8;

    @BoundaryValue
    @Test
    void readerAcceptsABodyExactlyAtTheLimit() throws Exception {
        try (BufferedReader reader = filtered("x".repeat(LIMIT)).getReader()) {
            assertThat(reader.readLine()).isEqualTo("x".repeat(LIMIT));
        }
    }

    @BoundaryValue
    @Test
    void readerRefusesABodyOneByteOverTheLimit() throws Exception {
        BufferedReader reader = filtered("x".repeat(LIMIT + 1)).getReader();
        assertThatThrownBy(reader::readLine)
                .isInstanceOf(BodySizeLimitFilter.BodyTooLargeException.class);
    }

    /**
     * The limit counts bytes, not characters: four two-byte characters fill an eight-byte limit.
     */
    @BoundaryValue
    @Test
    void readerCountsBytesInTheRequestCharset() throws Exception {
        try (BufferedReader reader = filtered("éééé").getReader()) {
            assertThat(reader.readLine()).isEqualTo("éééé");
        }
        BufferedReader over = filtered("ééééé").getReader();
        assertThatThrownBy(over::readLine).isInstanceOf(BodySizeLimitFilter.BodyTooLargeException.class);
    }

    @BoundaryValue
    @Test
    void inputStreamRefusesABodyOneByteOverTheLimit() throws Exception {
        var in = filtered("x".repeat(LIMIT + 1)).getInputStream();
        assertThatThrownBy(in::readAllBytes).isInstanceOf(BodySizeLimitFilter.BodyTooLargeException.class);
    }

    /**
     * Runs a UTF-8 request with {@code body} through the filter and returns the request the handler would see.
     */
    private static HttpServletRequest filtered(String body) throws Exception {
        var filter = new BodySizeLimitFilter(TestProperties.with(Map.of("playground.max-body-size", LIMIT + "B")));
        var request = new MockHttpServletRequest("POST", "/post");
        request.setCharacterEncoding("UTF-8");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        AtomicReference<ServletRequest> seen = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> seen.set(req));
        return (HttpServletRequest) seen.get();
    }
}
