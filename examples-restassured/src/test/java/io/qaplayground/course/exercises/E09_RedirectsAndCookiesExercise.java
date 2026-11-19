package io.qaplayground.course.exercises;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exercises for lesson 9: follow redirects by hand, and delete one cookie of two.
 *
 * <p>Steps and expected results are on the lesson page in {@code docs/learning-paths/rest-assured/}.
 */
@Disabled("Remove this line when you start the exercise")
class E09_RedirectsAndCookiesExercise extends CourseTest {

    @Test
    void countTheHops() {
        // TODO: With redirects off, start at /redirect/5 and follow each Location header yourself in a loop
        // TODO: until the status is no longer 302. Assert that you made 5 hops.
        fail("Not done yet: countTheHops");
    }

    @Test
    void deleteOneCookie() {
        // TODO: With one CookieFilter and redirects off on both calls: /cookies/set?a=1&b=2, then /cookies/delete?a.
        // TODO: Then GET /cookies: expect cookies.b to be "2" and cookies.a to be null.
        fail("Not done yet: deleteOneCookie");
    }
}
