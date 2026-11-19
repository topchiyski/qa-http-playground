package io.qaplayground.course.advanced;

import java.time.Duration;

import io.gatling.javaapi.core.PopulationBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.qaplayground.course.Playground;

import static io.gatling.javaapi.core.CoreDsl.constantConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Lesson 11: the same endpoint, measured two ways.
 *
 * <p>Run it with {@code -Dmodel=open}, then with {@code -Dmodel=closed}, and compare. When the server slows down,
 * the closed model sends less, so it reports better numbers than users actually get. That gap has a name:
 * coordinated omission.
 */
public class L11_HonestMeasurementSimulation extends Simulation {

    String model = System.getProperty("model", "open");
    Duration duration = Duration.ofSeconds(30);

    ScenarioBuilder scenario = scenario("Honest measurement (" + model + ")")
            .exec(http("GET /delay/0.5").get("/delay/0.5").check(status().is(200)));

    PopulationBuilder population = model.equals("closed")
            ? scenario.injectClosed(constantConcurrentUsers(5).during(duration))   // 5 at a time
            : scenario.injectOpen(constantUsersPerSec(10).during(duration));       // 10 arrive per second

    {
        setUp(population).protocols(Playground.protocol());
    }
}
