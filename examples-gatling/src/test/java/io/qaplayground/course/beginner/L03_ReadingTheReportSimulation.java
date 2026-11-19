package io.qaplayground.course.beginner;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.qaplayground.course.Playground;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Lesson 3: a run made to be read.
 *
 * <p>One request is fast and one is slow. Put both in one average and the number describes neither. The report
 * shows why you read response times per request, and as percentiles.
 */
public class L03_ReadingTheReportSimulation extends Simulation {

    ScenarioBuilder scenario = scenario("Fast and slow")
            .exec(http("fast: GET /get").get("/get").check(status().is(200)))
            .exec(http("slow: GET /delay/0.5").get("/delay/0.5").check(status().is(200)));

    {
        // 5 new users every second for 30 seconds: 150 users, 300 requests.
        setUp(scenario.injectOpen(constantUsersPerSec(5).during(30)))
                .protocols(Playground.protocol());
    }
}
