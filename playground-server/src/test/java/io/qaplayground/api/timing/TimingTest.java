package io.qaplayground.api.timing;

import io.qaplayground.api.support.PlaygroundTest;
import io.qaplayground.testing.technique.BoundaryValue;
import io.qaplayground.testing.technique.ErrorGuessing;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tutorial: latency and timeouts.
 *
 * <p>{@code time(...)} asserts on response time. A client-side socket timeout shows what your tests do when a
 * dependency is too slow.
 */
class TimingTest extends PlaygroundTest {

    /**
     * {@code /delay/{n}} waits, reports the wait in {@code Server-Timing}, then echoes the request.
     */
    @Test
    void delayWaitsAndEchoes() {
        when()
            .get("/delay/0.3")
        .then()
            .statusCode(200)
            .time(greaterThanOrEqualTo(300L))
            .header("Server-Timing", "initial_delay;dur=300.00;desc=\"initial delay\"")
            .body("url", org.hamcrest.Matchers.endsWith("/delay/0.3"));
    }

    /**
     * Go-style durations work too, and /delay accepts a body like /anything.
     */
    @Test
    void delayAcceptsGoDurationsAndBodies() {
        given()
            .contentType("application/json")
            .body("{\"a\": 1}")
        .when()
            .post("/delay/250ms")
        .then()
            .statusCode(200)
            .body("json.a", equalTo(1));
    }

    /**
     * Delays below zero, above the maximum, or not a duration at all are refused.
     */
    @BoundaryValue
    @ParameterizedTest(name = "/delay/{0} is 400")
    @ValueSource(strings = {"11", "-1", "abc", "1h"})
    void delayRejectsOutOfRange(String d) {
        when().get("/delay/" + d).then().statusCode(400).body("detail", startsWith("invalid duration: "));
    }

    /**
     * A delay above the maximum gets a message that names both the request and the limit.
     */
    @Test
    void delayTooLongMessage() {
        when().get("/delay/11").then().body("detail", equalTo("invalid duration: duration 11s longer than 10s"));
    }

    /**
     * A client timeout shorter than the delay fails the request. That is the point.
     */
    @Test
    void clientTimeoutFires() {
        RestAssuredConfig impatient = RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.socket.timeout", 200)
                .setParam("http.connection.timeout", 1000));
        assertThrows(java.net.SocketTimeoutException.class,
                () -> given().config(impatient).get("/delay/2"));
    }

    /**
     * {@code /drip} sends exactly {@code numbytes} asterisks, with the requested status and {@code Content-Length}.
     */
    @Test
    void dripSendsBytesOverTime() {
        given()
            .queryParam("numbytes", 5)
            .queryParam("duration", "0.4")
            .queryParam("delay", "0.1")
            .queryParam("code", 201)
        .when()
            .get("/drip")
        .then()
            .statusCode(201)
            .header("Content-Length", "5")
            .time(greaterThanOrEqualTo(450L))
            .body(equalTo("*****"));
    }

    /**
     * With no duration, all bytes arrive at once.
     */
    @Test
    void dripWithoutDurationIsInstant() {
        given().queryParam("duration", 0).queryParam("delay", 0).queryParam("numbytes", 3)
            .get("/drip").then().statusCode(200).time(lessThan(1500L)).body(equalTo("***"));
    }

    /**
     * Duration plus delay above the maximum is refused.
     */
    @Test
    void dripRejectsTooMuchTime() {
        given().queryParam("duration", 6).queryParam("delay", 5).get("/drip")
            .then().statusCode(400).body("detail", equalTo("too much time: 6s+5s > 10s"));
    }

    /**
     * Parameters that aren't valid numbers or durations are refused.
     */
    @ErrorGuessing
    @ParameterizedTest(name = "/drip?{0} is 400")
    @ValueSource(strings = {"numbytes=0", "numbytes=x", "code=600", "duration=x", "delay=-1"})
    void dripRejectsBadParameters(String query) {
        when().get("/drip?" + query).then().statusCode(400);
    }
}
