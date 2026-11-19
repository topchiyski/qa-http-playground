package io.qaplayground.course.intermediate;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Lesson 7: one test, many inputs.
 *
 * <p>The inputs come from test design, not from guesswork. Equivalence partitions are groups of inputs that should
 * behave the same, so one from each group is enough. Boundary values sit at the edges of a limit, where most
 * off-by-one bugs live.
 */
class L07_DataDrivenAndDesignTest extends CourseTest {

    /**
     * Partition 1 of the 3xx codes: redirects that tell the client where to go must send a {@code Location}.
     */
    @ParameterizedTest(name = "/status/{0} has a Location")
    @ValueSource(ints = {301, 302, 303, 307, 308})
    void redirectCodesWithLocation(int code) {
        given().redirects().follow(false).get("/status/" + code).then()
                .statusCode(code)
                .header("Location", notNullValue());
    }

    /**
     * Partition 2: 304 is a 3xx code too, but it means "use your cached copy", so it has no {@code Location}.
     */
    @ParameterizedTest(name = "/status/{0} has no Location")
    @ValueSource(ints = {304})
    void redirectCodesWithoutLocation(int code) {
        given().redirects().follow(false).get("/status/" + code).then()
                .statusCode(code)
                .header("Location", nullValue());
    }

    /**
     * The limit is 0 to 1 MiB (1,048,576 bytes). Each edge is tested, with one step either side of it.
     */
    @ParameterizedTest(name = "/bytes/{0} -> {1}")
    @CsvSource({
            "-1, 400",
            "0, 200",
            "1, 200",
            "1048575, 200",
            "1048576, 200",
            "1048577, 400"})
    void bytesBoundaries(int n, int status) {
        given().get("/bytes/" + n).then().statusCode(status);
    }
}
