package io.qaplayground.api.basics;

import io.qaplayground.api.support.PlaygroundTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

/**
 * Tutorial: behaviour every endpoint shares. CORS headers, preflight requests, and what "not found" and "method not
 * allowed" look like.
 *
 * <p>The pattern used throughout: {@code given()} sets up the request, {@code when()} sends it, {@code then()}
 * asserts on the response.
 */
class GlobalBehaviourTest extends PlaygroundTest {

    /**
     * Every response carries permissive CORS headers. Without an {@code Origin} request header the
     * server answers with the wildcard {@code *}.
     */
    @Test
    void everyResponseAllowsAnyOrigin() {
        when()
            .get("/robots.txt")
        .then()
            .statusCode(200)
            .header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Allow-Credentials", "true");
    }

    /**
     * When the browser sends {@code Origin}, the server echoes it back instead of {@code *}.
     */
    @Test
    void echoesTheRequestOrigin() {
        given()
            .header("Origin", "https://my-app.example")
        .when()
            .get("/robots.txt")
        .then()
            .header("Access-Control-Allow-Origin", "https://my-app.example");
    }

    /**
     * A CORS preflight is an {@code OPTIONS} request a browser sends before the "real" one.
     *
     * <p>The playground answers every OPTIONS request with 200, even on paths that don't exist, exactly like
     * go-httpbin.
     * Its CORS middleware runs before routing.
     */
    @Test
    void preflightSucceedsOnAnyPath() {
        given()
            .header("Origin", "https://my-app.example")
            .header("Access-Control-Request-Method", "PUT")
            .header("Access-Control-Request-Headers", "X-Custom, Content-Type")
        .when()
            .options("/no/such/path")
        .then()
            .statusCode(200)
            .header("Access-Control-Allow-Methods", "GET, POST, HEAD, PUT, DELETE, PATCH, QUERY, OPTIONS")
            .header("Access-Control-Allow-Headers", "X-Custom, Content-Type")
            .header("Access-Control-Max-Age", "3600");
    }

    /**
     * An unknown path gets Go's plain-text 404, not a JSON error. A classic negative test.
     */
    @Test
    void unknownPathIsPlainText404() {
        when()
            .get("/this/does/not/exist")
        .then()
            .statusCode(404)
            .contentType(startsWith("text/plain"))
            .body(equalTo("404 page not found\n"));
    }

    /**
     * {@code /error} is an internal Spring Boot path. It doesn't exist in go-httpbin, so here it is a plain 404.
     */
    @Test
    void springErrorPathIsNotExposed() {
        when()
            .get("/error")
        .then()
            .statusCode(404)
            .body(not(startsWith("{")));
    }

    /**
     * The wrong method gets 405, plus an {@code Allow} header that lists the methods that would have worked.
     *
     * <p>Asserting on {@code Allow} is a good habit for any API that restricts methods.
     */
    @Test
    void wrongMethodIs405WithAllowHeader() {
        when()
            .post("/")
        .then()
            .statusCode(405)
            .header("Allow", "GET, HEAD")
            .body(equalTo("Method Not Allowed\n"));
    }
}
