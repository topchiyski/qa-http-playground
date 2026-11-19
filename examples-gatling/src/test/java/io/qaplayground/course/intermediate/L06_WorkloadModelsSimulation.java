package io.qaplayground.course.intermediate;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.qaplayground.course.Playground;

import static io.gatling.javaapi.core.CoreDsl.rampConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Lesson 6: the closed workload model.
 *
 * <p>Gatling keeps a fixed number of users busy. When one finishes, the next one starts. So when the server slows
 * down, the users wait, and the request rate drops on its own. Compare it with the open model in
 * {@code S06_WorkloadModelsSolution}.
 */
public class L06_WorkloadModelsSimulation extends Simulation {

    ScenarioBuilder scenario = scenario("Closed model")
            .exec(http("GET /delay/0.2").get("/delay/0.2").check(status().is(200)));

    {
        // From 1 to 10 users active at the same time, over 30 seconds.
        setUp(scenario.injectClosed(rampConcurrentUsers(1).to(10).during(30)))
                .protocols(Playground.protocol());
    }
}
