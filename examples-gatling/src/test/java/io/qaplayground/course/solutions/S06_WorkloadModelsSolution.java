package io.qaplayground.course.solutions;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.qaplayground.course.Playground;

import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Solution for lesson 6, exercise 6.1: the same request in the open model.
 *
 * <p>New users arrive at a set rate, whether or not earlier ones have finished. When the server slows down, the
 * queue grows and the arrivals keep coming. That is how real traffic behaves.
 */
public class S06_WorkloadModelsSolution extends Simulation {

    ScenarioBuilder scenario = scenario("Open model")
            .exec(http("GET /delay/0.2").get("/delay/0.2").check(status().is(200)));

    {
        // From 1 to 20 new users per second, over 30 seconds.
        setUp(scenario.injectOpen(rampUsersPerSec(1).to(20).during(30)))
                .protocols(Playground.protocol());
    }
}
