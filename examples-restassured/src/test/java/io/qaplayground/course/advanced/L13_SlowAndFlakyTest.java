package io.qaplayground.course.advanced;

import java.net.SocketTimeoutException;

import io.qaplayground.course.support.CourseTest;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.lessThan;

/**
 * Lesson 13: slow and flaky services.
 *
 * <p>A test without a timeout can hang a whole build. A test that passes "most of the time" teaches people to
 * ignore red. Make both deterministic.
 */
class L13_SlowAndFlakyTest extends CourseTest {

    /**
     * A timeout turns "hangs forever" into a clear, fast failure.
     */
    @Test
    void timeoutIsAnException() {
        RestAssuredConfig oneSecond = RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.socket.timeout", 1000));

        assertThatThrownBy(() -> given().config(oneSecond).get("/delay/3"))
                .isInstanceOf(SocketTimeoutException.class);
    }

    /**
     * A generous upper bound on response time. It catches "suddenly takes 10 seconds", not "5 % slower".
     */
    @Test
    void responseTimeBound() {
        // A generous bound: this catches "suddenly takes 10 seconds", not 5 % slower.
        // Precise timing belongs in a load test (the Gatling path).
        given().get("/delay/0.2").then()
                .statusCode(200)
                .time(lessThan(5000L));
    }

    /**
     * The same seed gives the same answer, so a random failure becomes one you can reproduce.
     */
    @Test
    void unstableIsDeterministicWithSeed() {
        int first = given().queryParam("seed", 1).get("/unstable").statusCode();
        int second = given().queryParam("seed", 1).get("/unstable").statusCode();

        assertThat(first).isEqualTo(second);
    }

    /**
     * At the extremes there is no randomness left: 0 always succeeds, 1 always fails.
     */
    @Test
    void failureRateExtremes() {
        given().queryParam("failure_rate", 0).get("/unstable").then().statusCode(200);
        given().queryParam("failure_rate", 1).get("/unstable").then().statusCode(500);
    }
}
