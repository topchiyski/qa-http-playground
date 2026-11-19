package io.qaplayground.course.advanced;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.qaplayground.course.Playground;

import static io.gatling.javaapi.core.CoreDsl.incrementUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Lesson 9: a stress test climbs until something gives.
 *
 * <p>There are no assertions, on purpose: you expect it to break. The result is <em>where</em> it breaks, the
 * "knee" in the report where response times stop being flat.
 *
 * <pre>-Dusers=20   (arrival rate added per step, default 5)</pre>
 */
public class L09_StressSimulation extends Simulation {

    int step = Playground.intProperty("users", 5);

    ScenarioBuilder scenario = scenario("Stress")
            .exec(http("GET /get").get("/get").check(status().is(200)))
            .exec(http("GET /delay/0.1").get("/delay/0.1").check(status().is(200)));

    {
        // 5 steps of 20 seconds: step, 2 x step, ... 5 x step new users per second (open model).
        setUp(scenario.injectOpen(
                incrementUsersPerSec(step)
                        .times(5)
                        .eachLevelLasting(20)
                        .startingFrom(step)))
                .protocols(Playground.protocol());
    }
}
