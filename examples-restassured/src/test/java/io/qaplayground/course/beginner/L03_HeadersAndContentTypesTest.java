package io.qaplayground.course.beginner;

import io.qaplayground.course.support.CourseTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

/**
 * Lesson 3: not every response is JSON.
 *
 * <p>Check the {@code Content-Type} first. It tells the client how to read the body. A wrong one breaks real
 * clients, even when the body looks fine to you.
 */
class L03_HeadersAndContentTypesTest extends CourseTest {

    /**
     * A JSON endpoint says it is JSON.
     */
    @Test
    void jsonContentType() {
        given().get("/get").then().contentType(ContentType.JSON);
    }

    /**
     * A response header you asked for is really there.
     */
    @Test
    void customResponseHeader() {
        // /response-headers returns every query parameter as a response header.
        given().queryParam("X-Test", "1").get("/response-headers").then()
                .header("X-Test", equalTo("1"));
    }

    /**
     * A plain-text response: check its type, then search its body as text.
     */
    @Test
    void plainTextBody() {
        given().get("/robots.txt").then()
                .statusCode(200)
                .contentType(startsWith("text/plain"))
                .body(containsString("Disallow: /deny"));
    }
}
