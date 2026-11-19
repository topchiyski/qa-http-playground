package io.qaplayground.course.beginner;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

/**
 * Lesson 2: assertions on JSON.
 *
 * <p>A GPath expression such as {@code "slideshow.title"} walks the JSON tree with dots. A Hamcrest matcher says
 * what the value must look like. Together they let you check one field without parsing the whole response.
 */
class L02_AssertOnJsonTest extends CourseTest {

    /**
     * Reads one value deep inside the JSON.
     */
    @Test
    void nestedValue() {
        given().get("/json").then()
                .statusCode(200)
                .body("slideshow.title", equalTo("Sample Slide Show"));
    }

    /**
     * Checks a list twice: how long it is, and one item it must contain.
     */
    @Test
    void listSizeAndItems() {
        // "slideshow.slides.title" collects the title of every slide into one list.
        given().get("/json").then()
                .body("slideshow.slides", hasSize(2))
                .body("slideshow.slides.title", hasItem("Wake up to WonderWidgets!"));
    }
}
