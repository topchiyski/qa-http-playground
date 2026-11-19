package io.qaplayground.course.solutions;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.RestAssured.given;

/**
 * Solutions for lesson 7. Compare them with yours after your own version is green.
 *
 * <p>One parameterised test per partition keeps each group's purpose visible in the report.
 */
class S07_DataDrivenAndDesignSolution extends CourseTest {

    /**
     * The 2xx partition: one test, two representatives.
     */
    @ParameterizedTest(name = "2xx: /status/{0}")
    @ValueSource(ints = {200, 204})
    void successPartition(int code) {
        given().get("/status/" + code).then().statusCode(code);
    }

    /**
     * The 4xx partition.
     */
    @ParameterizedTest(name = "4xx: /status/{0}")
    @ValueSource(ints = {400, 418})
    void clientErrorPartition(int code) {
        given().get("/status/" + code).then().statusCode(code);
    }

    /**
     * The 5xx partition.
     */
    @ParameterizedTest(name = "5xx: /status/{0}")
    @ValueSource(ints = {500, 503})
    void serverErrorPartition(int code) {
        given().get("/status/" + code).then().statusCode(code);
    }

    /**
     * Just outside both edges of the 0 to 10 second limit. The valid edge would cost 10 seconds per run.
     */
    @ParameterizedTest(name = "/delay/{0} -> 400")
    @ValueSource(strings = {"-1", "10.1"})
    void delayBoundaries(String seconds) {
        given().get("/delay/" + seconds).then().statusCode(400);
    }
}
