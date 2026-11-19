package io.qaplayground.course.intermediate;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.qaplayground.course.Playground;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Lesson 7: requirements as assertions.
 *
 * <p>"95 % of requests under 500 ms, more than 99 % successful" becomes two lines of code. If either one fails,
 * the build fails. A requirement you can run is a requirement you keep.
 */
public class L07_PassFailCriteriaSimulation extends Simulation {

    ScenarioBuilder scenario = scenario("SLO check")
            .exec(http("GET /get").get("/get").check(status().is(200)));

    {
        setUp(scenario.injectOpen(constantUsersPerSec(10).during(20)))
                .protocols(Playground.protocol())
                .assertions(
                        global().responseTime().percentile(95.0).lt(500),
                        global().successfulRequests().percent().gt(99.0));
    }
}
