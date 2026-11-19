package io.qaplayground.course.exercises;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exercises for lesson 1: send a query parameter and a header, and check that each arrived.
 *
 * <p>Steps and expected results are on the lesson page in {@code docs/learning-paths/rest-assured/}.
 */
@Disabled("Remove this line when you start the exercise")
class E01_FirstRequestExercise extends CourseTest {

    @Test
    void queryParameterTeamIsEchoed() {
        // TODO: GET /get with query parameter team=qa.
        // TODO: Expect status 200 and args.team[0] equal to "qa".
        fail("Not done yet: queryParameterTeamIsEchoed");
    }

    @Test
    void requestHeaderIsEchoed() {
        // TODO: GET /headers with request header X-Course: 1.
        // TODO: Expect headers.X-Course[0] equal to "1".
        fail("Not done yet: requestHeaderIsEchoed");
    }
}
