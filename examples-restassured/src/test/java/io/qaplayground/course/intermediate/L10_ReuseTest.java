package io.qaplayground.course.intermediate;

import io.qaplayground.course.support.CourseTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Lesson 10: say it once.
 *
 * <p>A {@code RequestSpecification} holds what every request sends. A {@code ResponseSpecification} holds what
 * every response must satisfy. Each test then shows only what makes it special, and a change happens in one place.
 */
class L10_ReuseTest extends CourseTest {

    /**
     * What every request in this class sends: a course header and a JSON {@code Accept}.
     */
    static final RequestSpecification COURSE_CLIENT = new RequestSpecBuilder()
            .addHeader("X-Course", "rest-assured")
            .setAccept(ContentType.JSON)
            .build();

    /**
     * What every good response here looks like: 200, as JSON.
     */
    static final ResponseSpecification OK_JSON = new ResponseSpecBuilder()
            .expectStatusCode(200)
            .expectContentType(ContentType.JSON)
            .build();

    /**
     * The shared request spec really adds its header.
     */
    @Test
    void requestSpecAppliesHeaders() {
        given().spec(COURSE_CLIENT)
                .get("/headers").then()
                .spec(OK_JSON)
                .body("headers.X-Course[0]", equalTo("rest-assured"));
    }

    /**
     * One response spec checks two different endpoints.
     */
    @Test
    void responseSpecReused() {
        given().spec(COURSE_CLIENT).get("/get").then().spec(OK_JSON);
        given().spec(COURSE_CLIENT).post("/anything").then().spec(OK_JSON).body("method", equalTo("POST"));
    }
}
