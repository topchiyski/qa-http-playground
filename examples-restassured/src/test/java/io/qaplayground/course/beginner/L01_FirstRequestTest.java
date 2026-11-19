package io.qaplayground.course.beginner;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Lesson 1: your first request.
 *
 * <p>Every REST Assured test has the same three parts. {@code given()} is what you send. {@code when()} is the
 * method and the path. {@code then()} is what you expect back. Master this shape and the rest of the course is
 * detail.
 */
class L01_FirstRequestTest extends CourseTest {

    /**
     * The simplest useful test: the endpoint exists and answers 200.
     */
    @Test
    void getReturns200() {
        given()
        .when()
            .get("/get")
        .then()
            .statusCode(200);
    }

    /**
     * A query parameter arrives at the server.
     *
     * <p>{@code /get} sends back what it received, so the test can check its own input.
     */
    @Test
    void queryParameterIsEchoed() {
        // /get sends back what it received, so we can check that our parameter arrived.
        // Query parameters can repeat, which is why "args.colour" is a list and we take element [0].
        given()
            .queryParam("colour", "red")
        .when()
            .get("/get")
        .then()
            .statusCode(200)
            .body("args.colour[0]", equalTo("red"));
    }
}
