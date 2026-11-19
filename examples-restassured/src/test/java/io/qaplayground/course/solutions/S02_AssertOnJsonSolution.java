package io.qaplayground.course.solutions;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Solution for lesson 2. Compare it with yours after your own version is green.
 */
class S02_AssertOnJsonSolution extends CourseTest {

    /**
     * One field, one matcher. The failure message you read in the exercise names both the path and the value.
     */
    @Test
    void slideshowAuthor() {
        given().get("/json").then().body("slideshow.author", equalTo("Yours Truly"));
    }
}
