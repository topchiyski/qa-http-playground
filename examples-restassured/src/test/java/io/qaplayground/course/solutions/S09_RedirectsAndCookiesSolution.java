package io.qaplayground.course.solutions;

import io.qaplayground.course.support.CourseTest;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * Solutions for lesson 9. Compare them with yours after your own version is green.
 */
class S09_RedirectsAndCookiesSolution extends CourseTest {

    /**
     * Follows each {@code Location} by hand and counts the hops until the redirects stop.
     */
    @Test
    void countTheHops() {
        String path = "/redirect/5";
        int hops = 0;
        Response response = given().redirects().follow(false).get(path);
        while (response.statusCode() == 302) {
            hops++;
            response = given().redirects().follow(false).get(response.header("Location"));
        }

        assertThat(hops).isEqualTo(5);
        assertThat(response.statusCode()).isEqualTo(200);
    }

    /**
     * Set two cookies, delete one, and check that only the other is left.
     */
    @Test
    void deleteOneCookie() {
        CookieFilter jar = new CookieFilter();
        given().filter(jar).redirects().follow(false).get("/cookies/set?a=1&b=2").then().statusCode(302);
        given().filter(jar).redirects().follow(false).get("/cookies/delete?a").then().statusCode(302);

        given().filter(jar).get("/cookies").then()
                .body("cookies.b", equalTo("2"))
                .body("cookies.a", nullValue());
    }
}
