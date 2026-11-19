package io.qaplayground.course.solutions;

import io.qaplayground.course.support.CourseTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;

/**
 * Solutions for lesson 4. Compare them with yours after your own version is green.
 */
class S04_SendingDataSolution extends CourseTest {

    /**
     * A book as the test sends it.
     */
    record Book(String title, int pages) {
    }

    /**
     * The method and the body both arrive: PUT, with the pages you sent.
     */
    @Test
    void putARecord() {
        given().contentType(ContentType.JSON).body(new Book("Emma", 474))
                .put("/anything").then()
                .body("method", equalTo("PUT"))
                .body("json.pages", equalTo(474));
    }

    /**
     * A parameter sent twice arrives as a list of two values.
     */
    @Test
    void repeatedQueryParameter() {
        given().queryParam("tag", "smoke", "regression").get("/get").then()
                .body("args.tag", hasSize(2))
                .body("args.tag", hasItems("smoke", "regression"));
    }
}
