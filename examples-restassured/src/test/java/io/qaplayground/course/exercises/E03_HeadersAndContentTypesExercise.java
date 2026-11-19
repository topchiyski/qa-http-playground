package io.qaplayground.course.exercises;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exercises for lesson 3: check an HTML page and read an attribute from XML.
 *
 * <p>Steps and expected results are on the lesson page in {@code docs/learning-paths/rest-assured/}.
 */
@Disabled("Remove this line when you start the exercise")
class E03_HeadersAndContentTypesExercise extends CourseTest {

    @Test
    void htmlPage() {
        // TODO: GET /html. Expect a content type starting with "text/html" and a body containing "Moby-Dick".
        fail("Not done yet: htmlPage");
    }

    @Test
    void xmlAttribute() {
        // TODO: GET /xml. Read the title attribute of the root element with the XML path "slideshow.@title".
        // TODO: Expect "Sample Slide Show".
        fail("Not done yet: xmlAttribute");
    }
}
