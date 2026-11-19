package io.qaplayground.course.solutions;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * Solution for lesson 13. Compare it with yours after your own version is green.
 */
class S13_SlowAndFlakySolution extends CourseTest {

    /**
     * Seed 2 always fails, so the failure is pinned and repeatable.
     *
     * <p>A failure you can reproduce is a bug report. A failure you retry until it passes is a bug you've decided not
     * to see.
     */
    @Test
    void pinAFailingSeed() {
        // Seed 2 always fails. A failing case you can reproduce is a bug report; one you retry
        // until it passes is a bug you've decided not to see. Retries make the build green and the
        // product no better: the 500 is still there for real users.
        given().queryParam("seed", 2).get("/unstable").then().statusCode(500);
    }
}
