package io.qaplayground.course.exercises;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exercises for lesson 7: partition status codes, and test both edges of a limit.
 *
 * <p>Steps and expected results are on the lesson page in {@code docs/learning-paths/rest-assured/}.
 */
@Disabled("Remove this line when you start the exercise")
class E07_DataDrivenAndDesignExercise extends CourseTest {

    @Test
    void statusPartitions() {
        // TODO: Turn this into three @ParameterizedTest methods, one per partition:
        // TODO: 2xx (200, 204), 4xx (400, 418) and 5xx (500, 503). Each asserts that /status/{code} returns that code.
        fail("Not done yet: statusPartitions");
    }

    @Test
    void delayBoundaries() {
        // TODO: /delay accepts 0 to 10 seconds. Write a parameterised test for the invalid side of both edges:
        // TODO: /delay/-1 and /delay/10.1 must return 400. (The valid edge /delay/10 is too slow for a unit test run.)
        fail("Not done yet: delayBoundaries");
    }
}
