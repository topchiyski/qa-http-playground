package io.qaplayground.course.exercises;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exercise for lesson 12: download a body in four parts and put it back together.
 *
 * <p>Steps and expected results are on the lesson page in {@code docs/learning-paths/rest-assured/}.
 */
@Disabled("Remove this line when you start the exercise")
class E12_CachingAndRangesExercise extends CourseTest {

    @Test
    void downloadInParts() {
        // TODO: Fetch /range/100 in four Range requests of 25 bytes each (bytes=0-24, 25-49, 50-74, 75-99).
        // TODO: Assert each is 206 with the right Content-Range ("bytes 0-24/100", ...). Join the four bodies and assert
        // TODO: the result equals the full GET /range/100 body.
        fail("Not done yet: downloadInParts");
    }
}
