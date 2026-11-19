package io.qaplayground.course.intermediate;

import io.qaplayground.course.support.CourseTest;
import io.restassured.filter.cookie.CookieFilter;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Lesson 9: redirects and cookies.
 *
 * <p>REST Assured follows redirects silently. That is convenient, but it hides what happened. Turn it off when the
 * redirect itself is what you test.
 */
class L09_RedirectsAndCookiesTest extends CourseTest {

    /**
     * Three redirects, followed for you. You only see where they ended.
     */
    @Test
    void followsRedirectsByDefault() {
        given().get("/redirect/3").then()
                .statusCode(200)
                .body("url", endsWith("/get"));
    }

    /**
     * With redirects off, you see the first hop: a 302 and where it points.
     */
    @Test
    void redirectsOffShows302() {
        given().redirects().follow(false)
                .get("/redirect/3").then()
                .statusCode(302)
                .header("Location", notNullValue());
    }

    /**
     * A {@code CookieFilter} keeps cookies between requests, like a small browser cookie jar.
     */
    @Test
    void cookieFilterKeepsSession() {
        // A CookieFilter is a tiny browser cookie jar: it stores Set-Cookie and sends the cookies back.
        // Redirects stay off on the set call: if REST Assured followed the 302 internally, the
        // Set-Cookie on the redirect response would never reach the filter.
        CookieFilter jar = new CookieFilter();
        given().filter(jar).redirects().follow(false).get("/cookies/set?a=1").then().statusCode(302);

        given().filter(jar).get("/cookies").then()
                .body("cookies.a", equalTo("1"));
    }
}
