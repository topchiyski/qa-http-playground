package io.qaplayground.course.solutions;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.not;

/**
 * Solution for lesson 11. Compare it with yours after your own version is green.
 */
class S11_ContractSolution extends CourseTest {

    /**
     * The schema accepts {@code /uuid} and rejects {@code /get}.
     *
     * <p>A schema that accepts everything proves nothing, so the second check matters as much as the first.
     */
    @Test
    void uuidSchema() {
        given().get("/uuid").then().body(matchesJsonSchemaInClasspath("schemas/solutions/uuid.json"));
        // A schema that accepts everything proves nothing: check it also rejects a different response.
        given().get("/get").then().body(not(matchesJsonSchemaInClasspath("schemas/solutions/uuid.json")));
    }
}
