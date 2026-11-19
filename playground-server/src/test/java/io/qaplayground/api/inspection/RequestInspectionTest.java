package io.qaplayground.api.inspection;

import java.util.List;

import io.qaplayground.api.support.PlaygroundTest;
import io.qaplayground.testing.technique.EquivalencePartitioning;
import io.qaplayground.testing.technique.ErrorGuessing;
import io.qaplayground.testing.technique.SecurityTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

/**
 * Tutorial: inspecting what your client really sends.
 *
 * <p>The echo endpoints return JSON in which <em>every</em> query parameter and header maps to a list of values
 * ({@code "args": {"a": ["1"]}}). In GPath that means {@code args.a[0]}, not {@code args.a}. This is the most common
 * surprise when you move from the old httpbin.org.
 */
class RequestInspectionTest extends PlaygroundTest {

    /**
     * Query parameters arrive as lists; repeated keys collect several values.
     */
    @EquivalencePartitioning
    @Test
    void getEchoesQueryArgsAsLists() {
        given()
            .queryParam("colour", "red")
            .queryParam("size", "S", "M")
        .when()
            .get("/get")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("args.colour[0]", equalTo("red"))
            .body("args.size", equalTo(List.of("S", "M")))
            .body("method", equalTo("GET"))
            .body("url", containsString("/get?colour=red"));
    }

    /**
     * Header names are echoed in canonical form, so {@code x-trace-id} shows up as {@code X-Trace-Id}.
     */
    @Test
    void headersAreCanonicalised() {
        given()
            .header("x-trace-id", "abc-123")
        .when()
            .get("/headers")
        .then()
            .statusCode(200)
            .body("headers.X-Trace-Id[0]", equalTo("abc-123"))
            .body("headers.Host[0]", notNullValue());
    }

    /**
     * {@code /get} accepts GET (and HEAD) only.
     */
    @Test
    void getRejectsPost() {
        when()
            .post("/get")
        .then()
            .statusCode(405)
            .header("Allow", "GET, HEAD");
    }

    /**
     * {@code /anything} takes any method and any sub-path, and parses JSON bodies.
     */
    @Test
    void anythingEchoesMethodPathAndJsonBody() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"name\": \"Ada\", \"age\": 36, \"tags\": [\"a\", \"b\"]}")
        .when()
            .put("/anything/users/42")
        .then()
            .statusCode(200)
            .body("method", equalTo("PUT"))
            .body("url", endsWith("/anything/users/42"))
            .body("json.name", equalTo("Ada"))
            .body("json.age", equalTo(36)) // Go prints whole float64s without ".0", so GPath sees an int
            .body("json.tags[1]", equalTo("b"));
    }

    /**
     * Even a GET may carry a body on {@code /anything}. Useful to test clients that refuse to send one.
     */
    @Test
    void anythingAcceptsBodyOnGet() {
        given()
            .contentType(ContentType.TEXT)
            .body("hello")
        .when()
            .get("/anything")
        .then()
            .body("data", equalTo("hello"));
    }

    /**
     * Proxy headers win over the socket address, which is exactly why they must not be trusted.
     */
    @Test
    void ipHonoursForwardedForHeader() {
        given()
            .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1")
        .when()
            .get("/ip")
        .then()
            .body("origin", equalTo("203.0.113.7"));
    }

    /**
     * {@code ?format=text} returns the IP as plain text, for clients that don't want JSON.
     */
    @Test
    void ipAsPlainText() {
        given()
            .queryParam("format", "text")
            .header("X-Forwarded-For", "203.0.113.7")
        .when()
            .get("/ip")
        .then()
            .contentType(startsWith("text/plain"))
            .body(equalTo("203.0.113.7\n"));
    }

    /**
     * JSON keys may contain characters GPath treats specially; quote them with single quotes.
     */
    @Test
    void userAgentIsEchoed() {
        given()
            .header("User-Agent", "my-test-suite/1.0")
        .when()
            .get("/user-agent")
        .then()
            .body("'user-agent'", equalTo("my-test-suite/1.0"));
    }

    /**
     * An empty {@code User-Agent} header is fine: 200, not an error.
     */
    @ErrorGuessing
    @Test
    void userAgentIsEmptyStringWhenMissing() {
        given()
            .header("User-Agent", "")
        .when()
            .get("/user-agent")
        .then()
            .statusCode(200);
    }

    /**
     * {@code /hostname} says {@code go-httpbin} unless configured otherwise, so the real host name stays private.
     */
    @Test
    void hostnameIsTheDummyValueByDefault() {
        when()
            .get("/hostname")
        .then()
            .body("hostname", equalTo("go-httpbin"));
    }

    /**
     * Only HTTPBIN_ENV_* values are exposed, never other variables such as PATH. The in-process test
     * server defines HTTPBIN_ENV_GREETING; a server you started yourself may define none.
     */
    @SecurityTest
    @Test
    void envExposesOnlyPrefixedVariables() {
        var response = when()
            .get("/env")
        .then()
            .statusCode(200)
            .body("env.PATH", nullValue());
        if (io.qaplayground.api.support.PlaygroundTarget.isInProcess()) {
            response.body("env.HTTPBIN_ENV_GREETING", equalTo("hello"));
        }
    }

    /**
     * A UUID changes on every call; assert on its shape, not its value.
     */
    @Test
    void uuidIsVersion4() {
        String first = when().get("/uuid").then().statusCode(200).extract().path("uuid");
        String second = when().get("/uuid").then().extract().path("uuid");
        org.junit.jupiter.api.Assertions.assertNotEquals(first, second);
        org.hamcrest.MatcherAssert.assertThat(first,
                matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
    }

    /**
     * The raw dump shows the request line, sorted headers, a blank line and the body.
     */
    @Test
    void dumpRequestShowsTheWireFormat() {
        given()
            .header("X-Zebra", "last")
            .header("X-Alpha", "first")
            .contentType(ContentType.TEXT)
            .body("payload")
        .when()
            .post("/dump/request?q=1")
        .then()
            .statusCode(200)
            .body(startsWith("POST /dump/request?q=1 HTTP/1.1\r\nHost: "))
            .body(containsString("X-Alpha: first\r\nX-Zebra: last\r\n"))
            .body(endsWith("\r\n\r\npayload"));
    }

    /**
     * {@code /get} has no sub-paths; only {@code /anything} accepts them.
     */
    @Test
    void unknownSubPathOfGetIs404() {
        when()
            .get("/get/extra")
        .then()
            .statusCode(404)
            .body(not(startsWith("{")));
    }
}
