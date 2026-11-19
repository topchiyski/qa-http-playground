package io.qaplayground.course.beginner;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.qaplayground.course.Playground;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;

/**
 * Lesson 1: the smallest useful simulation.
 *
 * <p>Every simulation has the same three parts. A protocol says where requests go. A scenario says what one
 * virtual user does. {@code setUp} says how many users start, and when. Learn these three and you can read any
 * Gatling test.
 */
public class L01_FirstSimulation extends Simulation {

    // What ONE virtual user does: a single named request.
    ScenarioBuilder scenario = scenario("First simulation")
            .exec(http("GET /get").get("/get"));

    {
        // How many users run the scenario, and when: one user, right now.
        setUp(scenario.injectOpen(atOnceUsers(1)))
                .protocols(Playground.protocol());
    }
}
