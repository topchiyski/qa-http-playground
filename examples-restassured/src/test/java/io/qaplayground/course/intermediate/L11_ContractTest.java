package io.qaplayground.course.intermediate;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;

/**
 * Lesson 11: check the shape, not just a value.
 *
 * <p>A JSON Schema says which fields exist and what type each one is. One assertion then catches a renamed field,
 * a missing field, or a number that became a string.
 */
class L11_ContractTest extends CourseTest {

    /**
     * {@code /get} has the fields its schema promises.
     */
    @Test
    void getMatchesSchema() {
        given().get("/get").then()
                .statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/get.json"));
    }

    /**
     * The nested {@code /json} document matches its schema too.
     */
    @Test
    void slideshowMatchesSchema() {
        given().get("/json").then()
                .body(matchesJsonSchemaInClasspath("schemas/slideshow.json"));
    }
}
