package io.qaplayground.course.exercises;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exercises for lesson 4: send a record with PUT, and send one query parameter twice.
 *
 * <p>Steps and expected results are on the lesson page in {@code docs/learning-paths/rest-assured/}.
 */
@Disabled("Remove this line when you start the exercise")
class E04_SendingDataExercise extends CourseTest {

    @Test
    void putARecord() {
        // TODO: Define a record Book(String title, int pages). PUT one as JSON to /anything.
        // TODO: Expect method equal to "PUT" and json.pages equal to the pages you sent.
        fail("Not done yet: putARecord");
    }

    @Test
    void repeatedQueryParameter() {
        // TODO: GET /get with tag=smoke and tag=regression in one request.
        // TODO: Expect args.tag to have size 2 and contain both values.
        fail("Not done yet: repeatedQueryParameter");
    }
}
