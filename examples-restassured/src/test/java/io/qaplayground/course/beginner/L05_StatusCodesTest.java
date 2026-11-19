package io.qaplayground.course.beginner;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Lesson 5: negative tests.
 *
 * <p>Most real bugs hide in the "wrong" requests: the wrong method, an unknown path, bad input. A good suite checks
 * that these are refused clearly. A suite that only tests the happy path tests the half that rarely breaks.
 */
class L05_StatusCodesTest extends CourseTest {

    /**
     * {@code /status/{code}} answers with the code you ask for. Later lessons rely on that.
     */
    @Test
    void requestedStatusIsReturned() {
        given().get("/status/404").then().statusCode(404);
    }

    /**
     * The wrong method gets 405, and the {@code Allow} header names the methods that would work.
     */
    @Test
    void wrongMethodIsRefused() {
        // 405 Method Not Allowed must say which methods *are* allowed.
        given().delete("/get").then()
                .statusCode(405)
                .header("Allow", equalTo("GET, HEAD"));
    }

    /**
     * An unknown path gets 404, not an error page with 200.
     */
    @Test
    void unknownPathIs404() {
        given().get("/does-not-exist").then().statusCode(404);
    }
}
