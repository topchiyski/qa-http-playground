package io.qaplayground.api.basics;

import io.qaplayground.api.support.PlaygroundTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

/**
 * Tutorial: your first REST Assured tests. Status codes, headers, and plain-text and JSON bodies.
 */
class IndexTest extends PlaygroundTest {

    /**
     * The landing page is HTML, and it is sent with a {@code Content-Security-Policy} header.
     */
    @Test
    void landingPageIsHtmlWithContentSecurityPolicy() {
        when()
            .get("/")
        .then()
            .statusCode(200)
            .contentType(startsWith("text/html"))
            .header("Content-Security-Policy", startsWith("default-src 'self'"))
            .body(containsString("Swagger UI"));
    }

    /**
     * HEAD returns the same headers as GET but no body.
     */
    @Test
    void headRequestHasNoBody() {
        when()
            .head("/")
        .then()
            .statusCode(200)
            .contentType(startsWith("text/html"))
            .body(emptyString());
    }

    /**
     * {@code robots.txt} is plain text, and it tells crawlers to stay out of {@code /deny}.
     */
    @Test
    void robotsTxtDisallowsDeny() {
        when()
            .get("/robots.txt")
        .then()
            .statusCode(200)
            .contentType("text/plain; charset=utf-8")
            .body(equalTo("User-agent: *\nDisallow: /deny\n"));
    }

    /**
     * /robots.txt accepts any method, like the reference.
     */
    @Test
    void robotsTxtAcceptsAnyMethod() {
        when()
            .delete("/robots.txt")
        .then()
            .statusCode(200);
    }

    /**
     * {@code /deny} still answers, with "YOU SHOULDN'T BE HERE": robots.txt is a request, not a lock.
     */
    @Test
    void denyPage() {
        when()
            .get("/deny")
        .then()
            .statusCode(200)
            .body(equalTo("YOU SHOULDN'T BE HERE"));
    }

    /**
     * JSON assertions use GPath: {@code "service"} reads the top-level field.
     */
    @Test
    void versionNamesTheService() {
        given()
            .accept("application/json")
        .when()
            .get("/version")
        .then()
            .statusCode(200)
            .contentType("application/json; charset=utf-8")
            .body("service", equalTo("qa-http-playground"));
    }

    /**
     * Swagger UI is there for learners to try the API from a browser.
     */
    @Test
    void swaggerUiIsServed() {
        when()
            .get("/swagger-ui/index.html")
        .then()
            .statusCode(200)
            .contentType(startsWith("text/html"));
    }

    /**
     * The OpenAPI document behind Swagger UI lists the endpoints.
     */
    @Test
    void openApiDocumentListsEndpoints() {
        when()
            .get("/v3/api-docs")
        .then()
            .statusCode(200)
            .body("openapi", startsWith("3."))
            .body("info.title", equalTo("qa-http-playground"))
            .body("paths", org.hamcrest.Matchers.hasKey("/robots.txt"));
    }
}
