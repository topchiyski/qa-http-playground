package io.qaplayground.course.exercises;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exercise for lesson 10: build a "no redirects" spec once and reuse it.
 *
 * <p>Steps and expected results are on the lesson page in {@code docs/learning-paths/rest-assured/}.
 */
@Disabled("Remove this line when you start the exercise")
class E10_ReuseExercise extends CourseTest {

    @Test
    void noRedirectsSpec() {
        // TODO: Build a RequestSpecification that turns redirects off (RequestSpecBuilder.setConfig with a RedirectConfig).
        // TODO: Reuse it in two requests: /redirect/1 and /redirect-to?url=/get must both return 302.
        fail("Not done yet: noRedirectsSpec");
    }
}
