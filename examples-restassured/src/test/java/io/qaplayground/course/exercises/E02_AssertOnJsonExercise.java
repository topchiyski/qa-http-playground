package io.qaplayground.course.exercises;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exercise for lesson 2: read one JSON field, then break the test on purpose and read the failure.
 *
 * <p>Steps and expected results are on the lesson page in {@code docs/learning-paths/rest-assured/}.
 */
@Disabled("Remove this line when you start the exercise")
class E02_AssertOnJsonExercise extends CourseTest {

    @Test
    void slideshowAuthor() {
        // TODO: GET /json. Expect slideshow.author equal to "Yours Truly".
        // TODO: Then change the expected value, run it, read the failure message, and change it back.
        fail("Not done yet: slideshowAuthor");
    }
}
