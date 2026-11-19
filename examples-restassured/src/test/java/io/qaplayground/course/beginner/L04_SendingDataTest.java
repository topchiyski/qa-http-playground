package io.qaplayground.course.beginner;

import java.util.Map;

import io.qaplayground.course.support.CourseTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Lesson 4: sending data.
 *
 * <p>Give REST Assured a {@code Map} or a record and it writes the JSON for you. This is called serialisation.
 * {@code /post} and {@code /anything} echo what they received, so the test can check it arrived intact.
 */
class L04_SendingDataTest extends CourseTest {

    /**
     * A book as the test sends it. REST Assured turns it into {@code {"title": ..., "pages": ...}}.
     */
    record Book(String title, int pages) {
    }

    /**
     * A {@code Map} becomes a JSON object.
     */
    @Test
    void jsonBodyFromMap() {
        given().contentType(ContentType.JSON).body(Map.of("id", 1))
                .post("/post").then()
                .statusCode(200)
                .body("json.id", equalTo(1));
    }

    /**
     * A record becomes a JSON object, one field per component.
     */
    @Test
    void jsonBodyFromRecord() {
        given().contentType(ContentType.JSON).body(new Book("Dune", 412))
                .post("/post").then()
                .body("json.title", equalTo("Dune"))
                .body("json.pages", equalTo(412));
    }

    /**
     * Form fields, sent the way an HTML form sends them.
     */
    @Test
    void formFields() {
        // formParam sends application/x-www-form-urlencoded, like an HTML form.
        given().formParam("name", "ada")
                .post("/post").then()
                .body("form.name[0]", equalTo("ada"));
    }

    /**
     * A path parameter fills a placeholder in the URL.
     */
    @Test
    void pathParameter() {
        // {code} in the path is filled in from pathParam, and encoded for you.
        given().pathParam("code", 418)
                .get("/status/{code}").then()
                .statusCode(418);
    }
}
