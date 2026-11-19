package io.qaplayground.course.solutions;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.qaplayground.course.Playground;

import static io.gatling.javaapi.core.CoreDsl.bodyBytes;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Solution for lesson 5, exercise 5.1: a feeder generated in code instead of read from a file.
 *
 * <p>Each user gets a random size from 1 to 100, and the check proves the server sent exactly that many bytes.
 */
public class S05_DynamicDataSolution extends Simulation {

    // An endless supply of random sizes from 1 to 100.
    Iterator<Map<String, Object>> sizes = Stream.generate(
            () -> Map.<String, Object>of("n", ThreadLocalRandom.current().nextInt(1, 101))).iterator();

    ScenarioBuilder scenario = scenario("Random sizes")
            .feed(sizes)
            .exec(http("GET /bytes/n").get("/bytes/#{n}")
                    .check(status().is(200), bodyBytes().transform(bytes -> bytes.length).isEL("#{n}")));

    {
        setUp(scenario.injectOpen(rampUsers(20).during(10)))
                .protocols(Playground.protocol())
                .assertions(global().failedRequests().count().is(0L));
    }
}
