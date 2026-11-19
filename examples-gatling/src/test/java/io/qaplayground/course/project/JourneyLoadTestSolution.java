package io.qaplayground.course.project;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.qaplayground.course.Playground;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.csv;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.group;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Final project, reference solution: a load test for the checkout journey.
 *
 * <p><b>Goal:</b> checkout stays fast and reliable at the expected peak of 5 new shoppers per second.
 * <br><b>Model:</b> open, because shoppers don't wait for each other. Ramp from 1 to 5 per second over 30 s, then
 * hold for 60 s.
 * <br><b>SLOs:</b> under 1 % failed requests, browse p95 under 500 ms, every request p99 under 1 s.
 */
public class JourneyLoadTestSolution extends Simulation {

    ScenarioBuilder shopper = scenario("Checkout journey")
            .feed(csv("data/users.csv").circular())
            .exec(group("checkout").on(
                    exec(http("browse").get("/get?user=#{name}&page=products")
                            .check(status().is(200), jsonPath("$.args.user[0]").isEL("#{name}")))
                            .pause(1, 3)
                            .exec(http("add to basket").post("/post")
                                    .body(StringBody("{\"user\": \"#{name}\", \"item\": 42}")).asJson()
                                    .check(status().is(200), jsonPath("$.json.user").isEL("#{name}")))
                            .pause(1)
                            .exec(http("place order").get("/status/201").check(status().is(201)))));

    {
        setUp(shopper.injectOpen(
                rampUsersPerSec(1).to(5).during(30),
                constantUsersPerSec(5).during(60)))
                .protocols(Playground.protocol())
                .assertions(
                        global().failedRequests().percent().lt(1.0),
                        details("checkout", "browse").responseTime().percentile(95.0).lt(500),
                        global().responseTime().percentile(99.0).lt(1000));
    }
}
