package io.qaplayground.course.exercises;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exercises for lesson 15: a wrong user name, and markup that must come back escaped.
 *
 * <p>Steps and expected results are on the lesson page in {@code docs/learning-paths/rest-assured/}.
 */
@Disabled("Remove this line when you start the exercise")
class E15_SecurityNegativeExercise extends CourseTest {

    @Test
    void wrongUserRightPassword() {
        // TODO: GET /basic-auth/u/p with preemptive basic auth as user "x", password "p". Expect 401.
        fail("Not done yet: wrongUserRightPassword");
    }

    @Test
    void markupIsEscaped() {
        // TODO: GET /response-headers?Content-Type=text/html&x=<script> (let REST Assured encode it: queryParam).
        // TODO: The body must not contain a raw "<script>"; it must contain the escaped "&lt;script&gt;".
        fail("Not done yet: markupIsEscaped");
    }
}
