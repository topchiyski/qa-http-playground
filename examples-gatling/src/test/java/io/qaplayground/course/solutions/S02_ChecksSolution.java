package io.qaplayground.course.solutions;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.qaplayground.course.Playground;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Solution for lesson 2, exercise 2.1.
 *
 * <p>{@code /status/500} is <em>supposed</em> to answer 500, so the check says so. The broken version expected 200
 * and showed 5 KOs. A check describes what correct means for this request, not what "good" means in general.
 */
public class S02_ChecksSolution extends Simulation {

    ScenarioBuilder scenario = scenario("Expected errors")
            .exec(http("GET /status/500").get("/status/500").check(status().is(500)));

    {
        setUp(scenario.injectOpen(atOnceUsers(5)))
                .protocols(Playground.protocol())
                .assertions(global().failedRequests().count().is(0L));
    }
}
