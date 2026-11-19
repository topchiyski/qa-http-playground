package io.qaplayground.course.solutions;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Solutions for lesson 1. Compare them with yours after your own version is green.
 */
class S01_FirstRequestSolution extends CourseTest {

    /**
     * The parameter you send comes back under {@code args}, as a list.
     */
    @Test
    void queryParameterTeamIsEchoed() {
        given().queryParam("team", "qa").get("/get").then()
                .statusCode(200)
                .body("args.team[0]", equalTo("qa"));
    }

    /**
     * The header you send comes back under {@code headers}.
     */
    @Test
    void requestHeaderIsEchoed() {
        given().header("X-Course", "1").get("/headers").then()
                .body("headers.X-Course[0]", equalTo("1"));
    }
}
