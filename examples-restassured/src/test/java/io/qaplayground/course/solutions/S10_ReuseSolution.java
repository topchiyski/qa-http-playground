package io.qaplayground.course.solutions;

import io.qaplayground.course.support.CourseTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.RedirectConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * Solution for lesson 10. Compare it with yours after your own version is green.
 */
class S10_ReuseSolution extends CourseTest {

    /**
     * A spec that never follows redirects, so every test that uses it sees the 302 itself.
     */
    static final RequestSpecification NO_REDIRECTS = new RequestSpecBuilder()
            .setConfig(RestAssuredConfig.config().redirect(RedirectConfig.redirectConfig().followRedirects(false)))
            .build();

    /**
     * One spec, two different redirecting endpoints, the same result.
     */
    @Test
    void noRedirectsSpec() {
        given().spec(NO_REDIRECTS).get("/redirect/1").then().statusCode(302);
        given().spec(NO_REDIRECTS).queryParam("url", "/get").get("/redirect-to").then().statusCode(302);
    }
}
