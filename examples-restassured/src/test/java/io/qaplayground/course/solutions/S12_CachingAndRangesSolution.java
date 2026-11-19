package io.qaplayground.course.solutions;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Solution for lesson 12. Compare it with yours after your own version is green.
 */
class S12_CachingAndRangesSolution extends CourseTest {

    /**
     * Four ranges, each checked on its own, then joined and compared with the full body.
     */
    @Test
    void downloadInParts() {
        StringBuilder joined = new StringBuilder();
        for (int start = 0; start < 100; start += 25) {
            int end = start + 24;
            joined.append(given().header("Range", "bytes=" + start + "-" + end)
                    .get("/range/100").then()
                    .statusCode(206)
                    .header("Content-Range", equalTo("bytes " + start + "-" + end + "/100"))
                    .extract().asString());
        }

        String full = given().get("/range/100").then().statusCode(200).extract().asString();
        assertThat(joined.toString()).isEqualTo(full);
    }
}
