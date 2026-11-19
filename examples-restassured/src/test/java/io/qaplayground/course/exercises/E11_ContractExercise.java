package io.qaplayground.course.exercises;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exercise for lesson 11: write a schema that accepts one response and rejects another.
 *
 * <p>Steps and expected results are on the lesson page in {@code docs/learning-paths/rest-assured/}.
 */
@Disabled("Remove this line when you start the exercise")
class E11_ContractExercise extends CourseTest {

    @Test
    void uuidSchema() {
        // TODO: Write src/test/resources/schemas/uuid.json: an object that requires a "uuid" string matching the pattern
        // TODO: ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$. Assert that /uuid matches it and that /get does not.
        fail("Not done yet: uuidSchema");
    }
}
