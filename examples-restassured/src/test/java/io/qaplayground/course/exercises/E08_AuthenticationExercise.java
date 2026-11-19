package io.qaplayground.course.exercises;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exercises for lesson 8: a missing token, and digest authentication.
 *
 * <p>Steps and expected results are on the lesson page in {@code docs/learning-paths/rest-assured/}.
 */
@Disabled("Remove this line when you start the exercise")
class E08_AuthenticationExercise extends CourseTest {

    @Test
    void bearerWithoutToken() {
        // TODO: GET /bearer without a token. Expect 401 and a WWW-Authenticate header containing "Bearer".
        fail("Not done yet: bearerWithoutToken");
    }

    @Test
    void digestAuth() {
        // TODO: GET /digest-auth/auth/u/p with auth().digest("u", "p"). Expect 200.
        fail("Not done yet: digestAuth");
    }
}
