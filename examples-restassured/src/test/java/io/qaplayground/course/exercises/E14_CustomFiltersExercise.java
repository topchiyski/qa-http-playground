package io.qaplayground.course.exercises;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exercise for lesson 14: a filter that fails fast on any server error.
 *
 * <p>Steps and expected results are on the lesson page in {@code docs/learning-paths/rest-assured/}.
 */
@Disabled("Remove this line when you start the exercise")
class E14_CustomFiltersExercise extends CourseTest {

    @Test
    void failFastOnServerErrors() {
        // TODO: Write a Filter that throws an AssertionError when any response status is 5xx,
        // TODO: naming the method and path. Prove it: with the filter, GET /status/500 must throw; GET /status/404 must not.
        fail("Not done yet: failFastOnServerErrors");
    }
}
