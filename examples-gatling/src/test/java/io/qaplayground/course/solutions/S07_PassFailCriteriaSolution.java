package io.qaplayground.course.solutions;

import java.util.Iterator;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.qaplayground.course.Playground;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Solution for lesson 7, exercise 7.1: an error budget.
 *
 * <p>{@code /unstable} fails about 2 % of the time, and the budget allows 5 %. The seeds 1 to 100 repeat in a
 * cycle, so every run sends the same requests and gets the same result.
 */
public class S07_PassFailCriteriaSolution extends Simulation {

    Iterator<Map<String, Object>> seeds = Stream.generate(() -> IntStream.rangeClosed(1, 100).boxed())
            .flatMap(s -> s)
            .map(seed -> Map.<String, Object>of("seed", seed))
            .iterator();

    ScenarioBuilder scenario = scenario("Error budget")
            .feed(seeds)
            .exec(http("GET /unstable").get("/unstable?failure_rate=0.02&seed=#{seed}")
                    .check(status().is(200)));

    {
        setUp(scenario.injectOpen(constantUsersPerSec(10).during(20)))
                .protocols(Playground.protocol())
                .assertions(global().failedRequests().percent().lt(5.0));
    }
}
