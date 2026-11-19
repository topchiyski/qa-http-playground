package io.qaplayground.course.intermediate;

import io.qaplayground.course.support.CourseTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Lesson 8: authentication.
 *
 * <p>REST Assured supports the common schemes. The difference that matters is timing. Preemptive auth sends the
 * credentials with the first request. Challenge-based auth waits until the server asks for them with a 401.
 */
class L08_AuthenticationTest extends CourseTest {

    /**
     * The right user and password get in.
     */
    @Test
    void basicAuth() {
        given().auth().preemptive().basic("u", "p")
                .get("/basic-auth/u/p").then()
                .statusCode(200)
                .body("authenticated", equalTo(true));
    }

    /**
     * A wrong password is refused with 401.
     */
    @Test
    void wrongPasswordIs401() {
        given().auth().preemptive().basic("u", "wrong")
                .get("/basic-auth/u/p").then()
                .statusCode(401);
    }

    /**
     * Challenge-based auth waits for a 401 that this endpoint never sends, so only preemptive auth gets in.
     */
    @Test
    void challengeVsPreemptive() {
        // auth().basic() waits for a 401 challenge before sending credentials. This endpoint never
        // sends one (it answers 404), so only the preemptive style gets in.
        given().auth().basic("u", "p").get("/hidden-basic-auth/u/p").then().statusCode(404);
        given().auth().preemptive().basic("u", "p").get("/hidden-basic-auth/u/p").then().statusCode(200);
    }

    /**
     * A bearer token is just an {@code Authorization} header. Without it, the answer is 401.
     */
    @Test
    void bearerToken() {
        // There is no special bearer method: a token is just an Authorization header.
        given().header("Authorization", "Bearer abc")
                .get("/bearer").then()
                .statusCode(200)
                .body("token", equalTo("abc"));
        given().get("/bearer").then().statusCode(401);
    }
}
