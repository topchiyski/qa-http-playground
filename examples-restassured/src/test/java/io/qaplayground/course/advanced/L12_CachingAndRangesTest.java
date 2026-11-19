package io.qaplayground.course.advanced;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * Lesson 12: the cheapest response is the one you don't send.
 *
 * <p>Conditional requests ({@code ETag}, {@code If-Modified-Since}) let a client reuse the copy it already has.
 * Range requests fetch only the part it needs.
 */
class L12_CachingAndRangesTest extends CourseTest {

    /**
     * Send the {@code ETag} back and an unchanged resource answers 304, with no body.
     */
    @Test
    void etagThen304() {
        String etag = given().get("/etag/abc").then().statusCode(200).extract().header("ETag");

        // Send the ETag back: "I already have this version". Unchanged -> 304, no body.
        given().header("If-None-Match", etag).get("/etag/abc").then().statusCode(304);
    }

    /**
     * A copy with a date is "fresh enough", so the server answers 304.
     */
    @Test
    void ifModifiedSinceIs304() {
        given().header("If-Modified-Since", "Wed, 21 Oct 2015 07:28:00 GMT")
                .get("/cache").then()
                .statusCode(304);
    }

    /**
     * The server says how long a copy may be reused.
     */
    @Test
    void cacheControl() {
        given().get("/cache/60").then()
                .header("Cache-Control", containsString("max-age=60"));
    }

    /**
     * Ask for ten bytes and get exactly those ten, with 206 and a matching {@code Content-Range}.
     */
    @Test
    void rangeIs206() {
        given().header("Range", "bytes=0-9")
                .get("/range/26").then()
                .statusCode(206)
                .header("Content-Range", equalTo("bytes 0-9/26"))
                .body(equalTo("abcdefghij"));
    }
}
