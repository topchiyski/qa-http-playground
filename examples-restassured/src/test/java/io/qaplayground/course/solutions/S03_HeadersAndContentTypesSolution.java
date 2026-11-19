package io.qaplayground.course.solutions;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

/**
 * Solutions for lesson 3. Compare them with yours after your own version is green.
 */
class S03_HeadersAndContentTypesSolution extends CourseTest {

    /**
     * Check the type first, then the content.
     */
    @Test
    void htmlPage() {
        given().get("/html").then()
                .contentType(startsWith("text/html"))
                .body(containsString("Moby-Dick"));
    }

    /**
     * An XML response is read with XmlPath. The {@code @} selects an attribute.
     */
    @Test
    void xmlAttribute() {
        // An XML response is read with XmlPath; "@" selects an attribute.
        given().get("/xml").then().body("slideshow.@title", equalTo("Sample Slide Show"));
    }
}
