package io.qaplayground.course.exercises;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exercises for lesson 6: extract a whole JSON object, and compare two extracted values.
 *
 * <p>Steps and expected results are on the lesson page in {@code docs/learning-paths/rest-assured/}.
 */
@Disabled("Remove this line when you start the exercise")
class E06_ExtractAndChainExercise extends CourseTest {

    @Test
    void postedJsonComesBackUnchanged() {
        // TODO: POST a Map such as {"name": "ada", "level": 3} as JSON to /post.
        // TODO: Extract the "json" field as a Map (extract().jsonPath().getMap("json")) and assert it equals what you sent.
        fail("Not done yet: postedJsonComesBackUnchanged");
    }

    @Test
    void twoUuidsDiffer() {
        // TODO: Call /uuid twice, extract both values, and assert they are different.
        fail("Not done yet: twoUuidsDiffer");
    }
}
