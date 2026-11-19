package io.qaplayground.course.beginner;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.qaplayground.course.Playground;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Lesson 2: a smoke test with checks.
 *
 * <p>Without checks, Gatling marks a request as failed only when the connection breaks. A server that answered
 * every request with an error page would look perfect. Checks fix that: they say what a correct answer looks like.
 */
public class L02_ChecksSimulation extends Simulation {

    ScenarioBuilder scenario = scenario("Smoke")
            .exec(http("GET /get").get("/get?source=gatling")
                    .check(status().is(200), jsonPath("$.args.source[0]").is("gatling")))
            .exec(http("GET /status/204").get("/status/204")
                    .check(status().is(204)));

    {
        setUp(scenario.injectOpen(atOnceUsers(5)))
                .protocols(Playground.protocol())
                // The whole run fails (non-zero exit, red build) if any request is KO.
                .assertions(global().failedRequests().count().is(0L));
    }
}
