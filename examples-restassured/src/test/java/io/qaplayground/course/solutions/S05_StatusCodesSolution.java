package io.qaplayground.course.solutions;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

/**
 * Solutions for lesson 5. Compare them with yours after your own version is green.
 */
class S05_StatusCodesSolution extends CourseTest {

    /**
     * 418 is a real status code, and the body says so.
     */
    @Test
    void teapot() {
        given().get("/status/418").then()
                .statusCode(418)
                .body(containsString("teapot"));
    }

    /**
     * Input the server cannot parse is refused with 400.
     */
    @Test
    void statusCodeMustBeANumber() {
        given().get("/status/abc").then().statusCode(400);
    }
}
