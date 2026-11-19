package io.qaplayground.course.exercises;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exercise for lesson 13: turn a random failure into one you can reproduce.
 *
 * <p>Steps and expected results are on the lesson page in {@code docs/learning-paths/rest-assured/}.
 */
@Disabled("Remove this line when you start the exercise")
class E13_SlowAndFlakyExercise extends CourseTest {

    @Test
    void pinAFailingSeed() {
        // TODO: Find a seed for which /unstable?seed=N returns 500 (try a few). Write a test that pins it.
        // TODO: In a comment, explain why retrying /unstable until it returns 200 would hide the bug this test is about.
        fail("Not done yet: pinAFailingSeed");
    }
}
