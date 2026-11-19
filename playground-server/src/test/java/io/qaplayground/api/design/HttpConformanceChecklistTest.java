package io.qaplayground.api.design;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import io.qaplayground.api.support.PlaygroundTest;
import io.qaplayground.testing.Endpoints;
import io.qaplayground.testing.Endpoints.Route;
import io.qaplayground.testing.technique.ChecklistBased;
import io.restassured.config.DecoderConfig;
import io.restassured.config.RedirectConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checklist-based testing (docs/testing/TEST-DESIGN.md section 7): an HTTP/1.1 conformance checklist applied to
 * <b>every route</b> in the inventory.
 *
 * <p>A new endpoint is checked without anyone writing a new test, and RoutesContractIT keeps the inventory itself
 * honest. There is one test method per checklist item, parameterised over the routes, so a failure names both the
 * item and the route.
 */
@ChecklistBased
class HttpConformanceChecklistTest extends PlaygroundTest {

    /**
     * Routes that need a header to be served normally, such as an {@code Accept} that {@code /image} can satisfy.
     */
    private static final Map<String, String[]> EXTRA_HEADERS = Map.of(
            "/image", new String[]{"Accept", "image/png"});

    /**
     * Routes the checklist doesn't apply to. WebSocket isn't plain HTTP after the handshake.
     */
    private static final Set<String> NOT_HTTP = Set.of("/websocket/echo");

    /**
     * Every HTTP route in the inventory.
     */
    static Stream<Route> routes() {
        return Endpoints.ROUTES.stream().filter(r -> !NOT_HTTP.contains(r.template()));
    }

    /**
     * The routes that accept GET, for the items that compare with a GET.
     */
    static Stream<Route> getRoutes() {
        return routes().filter(r -> r.methods().contains("GET"));
    }

    /**
     * The routes that refuse at least one common method, for the 405 item.
     */
    static Stream<Route> restrictedRoutes() {
        return routes().filter(r -> !r.methods().containsAll(Set.of("GET", "POST", "PUT", "PATCH", "DELETE")));
    }

    /**
     * A request that sees each response as sent: no redirects followed, no decompression.
     */
    private static RequestSpecification request(Route route) {
        RequestSpecification spec = given().config(RestAssuredConfig.config()
                .redirect(RedirectConfig.redirectConfig().followRedirects(false))
                // Measure bytes as sent: do not let the client gunzip /gzip before we compare lengths.
                .decoderConfig(DecoderConfig.decoderConfig().noContentDecoders()));
        String[] extra = EXTRA_HEADERS.get(route.template());
        if (extra != null) {
            spec.header(extra[0], extra[1]);
        }
        return spec;
    }

    /**
     * The route's sample request, with its preferred method.
     */
    private static Response sample(Route route) {
        return request(route).request(route.sampleMethod(), route.sample());
    }

    /**
     * Item 0: the sample request never gets a 5xx. {@code /brotli} is the one deliberate exception: 501, as upstream.
     */
    @ParameterizedTest(name = "0. reachable without a server error: {0}")
    @MethodSource("routes")
    void item0NoServerError(Route route) {
        int status = sample(route).statusCode();
        if (route.template().equals("/brotli")) {
            assertThat(status).as("deliberate 501 Not Implemented, as upstream").isEqualTo(501);
        } else {
            assertThat(status).isBetween(200, 499);
        }
    }

    /**
     * Item 1: HEAD gets the same status and {@code Content-Type} as GET, and no body.
     */
    @ParameterizedTest(name = "1. HEAD = GET without a body: {0}")
    @MethodSource("getRoutes")
    void item1HeadMatchesGet(Route route) {
        Response get = request(route).get(route.sample());
        Response head = request(route).head(route.sample());
        assertThat(head.statusCode()).as("status").isEqualTo(get.statusCode());
        assertThat(head.asByteArray()).as("HEAD body").isEmpty();
        assertThat(head.contentType()).as("Content-Type").isEqualTo(get.contentType());
    }

    /**
     * Item 2: {@code Content-Length} equals the bytes actually sent; without it, the body must be chunked.
     */
    @ParameterizedTest(name = "2. Content-Length equals the body length: {0}")
    @MethodSource("routes")
    void item2ContentLengthIsAccurate(Route route) {
        if (route.sampleMethod().equals("HEAD")) {
            return; // a HEAD response's Content-Length describes the GET body it omits
        }
        Response r = sample(route);
        String length = r.header("Content-Length");
        if (length != null) {
            assertThat(Long.parseLong(length)).isEqualTo(r.asByteArray().length);
        } else {
            assertThat(r.header("Transfer-Encoding")).as("no Content-Length means chunked").isEqualTo("chunked");
        }
    }

    /**
     * Item 3: a method the route refuses gets 405, and {@code Allow} lists every method it accepts.
     */
    @ParameterizedTest(name = "3. a disallowed method gets 405 with Allow: {0}")
    @MethodSource("restrictedRoutes")
    void item3MethodNotAllowedHasAllow(Route route) {
        String wrong = route.methods().contains("DELETE") ? "PUT" : "DELETE";
        Response r = request(route).request(wrong, route.sample());
        assertThat(r.statusCode()).isEqualTo(405);
        assertThat(r.header("Allow")).isNotBlank();
        for (String allowed : route.methods()) {
            if (!allowed.equals("OPTIONS")) {
                assertThat(r.header("Allow")).contains(allowed);
            }
        }
    }

    /**
     * Item 4: every response has a {@code Date} header in the IMF-fixdate format.
     */
    @ParameterizedTest(name = "4. every response has a Date header: {0}")
    @MethodSource("routes")
    void item4DateHeader(Route route) {
        assertThat(sample(route).header("Date")).matches("\\w{3}, \\d{2} \\w{3} \\d{4} \\d{2}:\\d{2}:\\d{2} GMT");
    }

    /**
     * Item 5: a request with an {@code Origin} gets that origin back in {@code Access-Control-Allow-Origin}.
     */
    @ParameterizedTest(name = "5. every response carries CORS headers: {0}")
    @MethodSource("routes")
    void item5Cors(Route route) {
        Response r = request(route).header("Origin", "https://checklist.example")
                .request(route.sampleMethod(), route.sample());
        assertThat(r.header("Access-Control-Allow-Origin")).isEqualTo("https://checklist.example");
    }

    /**
     * Item 6: a CORS preflight gets 200 and the allowed methods.
     */
    @ParameterizedTest(name = "6. OPTIONS preflight is 200 with Allow-Methods: {0}")
    @MethodSource("routes")
    void item6Preflight(Route route) {
        Response r = given().header("Origin", "https://checklist.example").header("Access-Control-Request-Method", "POST")
                .options(route.sample());
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.header("Access-Control-Allow-Methods")).contains("GET", "POST");
    }

    /**
     * Item 7: a JSON response says {@code application/json; charset=utf-8} and really parses as JSON.
     */
    @ParameterizedTest(name = "7. JSON responses parse and declare UTF-8: {0}")
    @MethodSource("routes")
    void item7JsonIsValid(Route route) {
        if (route.sampleMethod().equals("HEAD")) {
            return; // no body to parse
        }
        // This item needs the decoded body (/gzip and /deflate are JSON once decompressed).
        Response r = given().header("Accept", "image/png").redirects().follow(false)
                .request(route.sampleMethod(), route.sample());
        String type = r.contentType();
        if (type != null && type.startsWith("application/json")) {
            assertThat(type.replace(" ", "")).isEqualTo("application/json;charset=utf-8");
            assertThat((Object) r.jsonPath().get("$")).as("parsed JSON").isNotNull();
        }
    }
}
