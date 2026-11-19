package io.qaplayground.course.solutions;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

/**
 * Solutions for lesson 15. Compare them with yours after your own version is green.
 */
class S15_SecurityNegativeSolution extends CourseTest {

    /**
     * The right password with the wrong user is still refused. Both halves are checked.
     */
    @Test
    void wrongUserRightPassword() {
        given().auth().preemptive().basic("x", "p").get("/basic-auth/u/p").then().statusCode(401);
    }

    /**
     * Markup in input comes back escaped, never raw.
     *
     * <p>If {@code <script>} came back raw in an HTML response, a link could run an attacker's script in a victim's
     * browser. That attack is called reflected cross-site scripting (XSS).
     */
    @Test
    void markupIsEscaped() {
        // If <script> came back raw in an HTML response, a link could run an attacker's script in
        // the victim's browser (reflected cross-site scripting, XSS).
        given().queryParam("Content-Type", "text/html").queryParam("x", "<script>")
                .get("/response-headers").then()
                .statusCode(200)
                .body(not(containsString("<script>")))
                .body(containsString("&lt;script&gt;"));
    }
}
