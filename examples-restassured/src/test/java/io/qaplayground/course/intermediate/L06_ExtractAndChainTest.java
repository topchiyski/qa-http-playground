package io.qaplayground.course.intermediate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.equalTo;

/**
 * Lesson 6: take a value out of one response and use it in the next.
 *
 * <p>Real flows work this way. You create something, read its id, then fetch or change it with that id.
 */
class L06_ExtractAndChainTest extends CourseTest {

    /**
     * The parts of a {@code /get} response this lesson needs. Fields it doesn't name are ignored.
     */
    record GetResponse(Map<String, List<String>> args, String url) {
    }

    /**
     * Takes one value out of a response and proves it is a real UUID.
     */
    @Test
    void extractAValue() {
        String id = given().get("/uuid").then().statusCode(200).extract().path("uuid");

        assertThatCode(() -> UUID.fromString(id)).doesNotThrowAnyException();
    }

    /**
     * Sends a value from one response in the next request: the shape of every real flow.
     */
    @Test
    void useOneResponseInTheNext() {
        String id = given().get("/uuid").then().extract().path("uuid");

        given().queryParam("id", id).get("/anything").then()
                .body("args.id[0]", equalTo(id));
    }

    /**
     * Turns a whole JSON response into a Java record, the opposite of lesson 4.
     */
    @Test
    void deserialiseIntoRecord() {
        // .as(...) is the opposite of lesson 4: JSON in, Java object out.
        GetResponse response = given().queryParam("page", "2").get("/get").as(GetResponse.class);

        assertThat(response.url()).endsWith("/get?page=2");
        assertThat(response.args()).containsEntry("page", List.of("2"));
    }
}
