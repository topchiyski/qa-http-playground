package io.qaplayground.course.solutions;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

/**
 * Solutions for lesson 8. Compare them with yours after your own version is green.
 */
class S08_AuthenticationSolution extends CourseTest {

    /**
     * No token: 401, and the challenge header names the scheme the server expects.
     */
    @Test
    void bearerWithoutToken() {
        given().get("/bearer").then()
                .statusCode(401)
                .header("WWW-Authenticate", containsString("Bearer"));
    }

    /**
     * Digest is always challenge-based. REST Assured reads the nonce from the 401, then answers it.
     */
    @Test
    void digestAuth() {
        // Digest is always challenge-based: REST Assured reads the 401's nonce, then answers it.
        given().auth().digest("u", "p").get("/digest-auth/auth/u/p").then().statusCode(200);
    }
}
