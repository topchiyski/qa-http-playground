package io.qaplayground.course.solutions;

import java.util.Map;

import io.qaplayground.course.support.CourseTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Solutions for lesson 6. Compare them with yours after your own version is green.
 */
class S06_ExtractAndChainSolution extends CourseTest {

    /**
     * Extract the echoed object and compare all of it at once, instead of field by field.
     */
    @Test
    void postedJsonComesBackUnchanged() {
        Map<String, Object> sent = Map.of("name", "ada", "level", 3);

        Map<String, Object> echoed = given().contentType(ContentType.JSON).body(sent)
                .post("/post").then().statusCode(200)
                .extract().jsonPath().getMap("json");

        assertThat(echoed).isEqualTo(sent);
    }

    /**
     * Two calls, two different ids. "Unique" is a property you can test.
     */
    @Test
    void twoUuidsDiffer() {
        String first = given().get("/uuid").then().extract().path("uuid");
        String second = given().get("/uuid").then().extract().path("uuid");

        assertThat(first).isNotEqualTo(second);
    }
}
