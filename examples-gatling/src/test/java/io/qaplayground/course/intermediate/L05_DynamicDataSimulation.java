package io.qaplayground.course.intermediate;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.qaplayground.course.Playground;

import static io.gatling.javaapi.core.CoreDsl.csv;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Lesson 5: every virtual user is someone different.
 *
 * <p>A feeder hands each user a row of data. The user's session keeps it, and {@code #{name}} reads it back.
 * {@code saveAs} works the other way round: it takes a value from a response and keeps it for the next request.
 */
public class L05_DynamicDataSimulation extends Simulation {

    ScenarioBuilder scenario = scenario("Different users")
            .feed(csv("data/users.csv").circular())          // one row per user, wrapping around
            .exec(http("GET /get as user").get("/get?name=#{name}&team=#{team}")
                    .check(status().is(200), jsonPath("$.args.name[0]").isEL("#{name}")))
            .exec(http("GET /uuid").get("/uuid")
                    .check(jsonPath("$.uuid").saveAs("id")))  // remember it for the next request
            .exec(http("GET /anything with id").get("/anything?id=#{id}")
                    .check(jsonPath("$.args.id[0]").isEL("#{id}")));

    {
        setUp(scenario.injectOpen(rampUsers(10).during(10)))
                .protocols(Playground.protocol())
                .assertions(global().failedRequests().count().is(0L));
    }
}
