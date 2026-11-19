package io.qaplayground.integration;

import io.qaplayground.testing.technique.EquivalencePartitioning;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behaviour that lives in filters, handlers and configuration rather than in one controller.
 *
 * <p>CORS, Go-style 404 and 405, Spring's response handling switched off, compression off, and the management port.
 * None of it belongs to an endpoint, so none of it would be tested if only endpoints were tested.
 */
class CrossCuttingIT extends ServerIT {

    /**
     * Every response carries the CORS headers, not only answers to preflight requests.
     */
    @Test
    @DisplayName("CORS: the Origin is echoed with credentials; no Origin means *")
    void corsOnOrdinaryResponses() {
        Res withOrigin = get("/get", "Origin", "https://app.example");
        assertThat(withOrigin.header("Access-Control-Allow-Origin")).isEqualTo("https://app.example");
        assertThat(withOrigin.header("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(get("/get").header("Access-Control-Allow-Origin")).isEqualTo("*");
    }

    /**
     * {@code OPTIONS} gets 200 even on paths that don't exist, because the filter answers before routing, as in
     * go-httpbin.
     */
    @EquivalencePartitioning
    @ParameterizedTest(name = "OPTIONS {0} is a 200 preflight")
    @ValueSource(strings = {"/get", "/status/500", "/does-not-exist", "/post"})
    void preflightRunsBeforeRouting(String path) {
        Res r = send("OPTIONS", path, null, "Origin", "https://app.example",
                "Access-Control-Request-Method", "DELETE", "Access-Control-Request-Headers", "X-Custom, Content-Type");
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.header("Access-Control-Allow-Methods")).contains("DELETE", "PATCH");
        assertThat(r.header("Access-Control-Allow-Headers")).isEqualTo("X-Custom, Content-Type");
        assertThat(r.header("Access-Control-Max-Age")).isEqualTo("3600");
    }

    /**
     * An unknown path gets Go's plain-text 404, not Spring's JSON error.
     */
    @Test
    @DisplayName("Unknown paths get Go's plain-text 404, including /error")
    void notFoundIsGoStyle() {
        for (String path : new String[]{"/does-not-exist", "/error", "/status"}) {
            Res r = get(path);
            assertThat(r.status()).as(path).isEqualTo(404);
            assertThat(r.body()).isEqualTo("404 page not found\n");
            assertThat(r.header("Content-Type")).startsWith("text/plain");
        }
    }

    /**
     * A wrong method gets Go's plain-text 405, with an {@code Allow} header.
     */
    @Test
    @DisplayName("A wrong method gets 405 with an Allow header")
    void methodNotAllowed() {
        Res r = get("/post");
        assertThat(r.status()).isEqualTo(405);
        assertThat(r.header("Allow")).contains("POST");
        assertThat(r.body()).isEqualTo("Method Not Allowed\n");
    }

    /**
     * Spring doesn't rewrite our {@code ETag} headers or answer 304 on its own.
     */
    @Test
    @DisplayName("Spring does not answer 304 on our behalf: /response-headers sets an ETag but is not conditional")
    void springEtagHandlingIsDisabled() {
        Res r = get("/response-headers?ETag=%22v1%22", "If-None-Match", "\"v1\"");
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.header("ETag")).isEqualTo("\"v1\"");
    }

    /**
     * With Spring's handling off, our own conditional requests still answer 304.
     */
    @Test
    @DisplayName("Conditional requests are answered by our ServeContent port")
    void ourConditionalHandlingStillWorks() {
        Res r = get("/etag/v1", "If-None-Match", "\"v1\"");
        assertThat(r.status()).isEqualTo(304);
        assertThat(r.body()).isEmpty();
    }

    /**
     * Responses without a body send {@code Content-Length: 0}, except 204, which must send none.
     */
    @Test
    @DisplayName("Bodiless responses say Content-Length: 0, except 204 and 304")
    void bodilessResponsesHaveExplicitLength() {
        assertThat(get("/status/500").header("Content-Length")).isEqualTo("0");
        assertThat(get("/redirect/1").header("Content-Length")).isEqualTo("0");
        assertThat(get("/status/204").header("Content-Length")).isNull();
    }

    /**
     * The server never compresses on its own: {@code Accept-Encoding} alone changes nothing. Only {@code /gzip} and
     * friends are compressed.
     */
    @Test
    @DisplayName("Compression is never added by the server (only /gzip and /deflate compress)")
    void noTransparentCompression() {
        Res r = get("/json", "Accept-Encoding", "gzip, deflate, br");
        assertThat(r.header("Content-Encoding")).isNull();
        assertThat(get("/gzip", "Accept-Encoding", "gzip").header("Content-Encoding")).isEqualTo("gzip");
    }

    /**
     * Health and metrics answer on the management port, and not on the API port.
     */
    @Test
    @DisplayName("Actuator runs on the separate management port, not on the API port")
    void managementPort() {
        Res health = sendTo(managementBase(), "GET", "/actuator/health", null);
        assertThat(health.status()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"");

        get("/get"); // make sure at least one request is recorded
        Res metrics = sendTo(managementBase(), "GET", "/actuator/prometheus", null);
        assertThat(metrics.body()).contains("http_server_requests_seconds_count").contains("application=\"qa-http-playground\"");

        assertThat(get("/actuator/health").status()).as("not exposed on the API port").isEqualTo(404);
    }

    /**
     * Swagger UI and the OpenAPI document are served.
     */
    @Test
    @DisplayName("Swagger UI is served at /swagger-ui")
    void swaggerUi() {
        Res r = get("/swagger-ui/index.html");
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body()).contains("swagger-ui");
    }
}
