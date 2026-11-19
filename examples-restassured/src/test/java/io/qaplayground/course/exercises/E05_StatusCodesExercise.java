package io.qaplayground.course.exercises;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exercises for lesson 5: an unusual status code, and input the server must refuse.
 *
 * <p>Steps and expected results are on the lesson page in {@code docs/learning-paths/rest-assured/}.
 */
@Disabled("Remove this line when you start the exercise")
class E05_StatusCodesExercise extends CourseTest {

    @Test
    void teapot() {
        // TODO: GET /status/418. Expect status 418 and a body containing "teapot".
        fail("Not done yet: teapot");
    }

    @Test
    void statusCodeMustBeANumber() {
        // TODO: GET /status/abc. Expect status 400: the server refuses input it cannot parse.
        fail("Not done yet: statusCodeMustBeANumber");
    }
}
