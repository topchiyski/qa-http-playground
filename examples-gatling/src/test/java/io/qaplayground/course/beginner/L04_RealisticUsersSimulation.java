package io.qaplayground.course.beginner;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.qaplayground.course.Playground;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.group;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Lesson 4: real users pause.
 *
 * <p>A person reads, thinks, then clicks. A test without pauses ("think time") fires requests like a machine gun,
 * and measures a load no real audience creates. The {@code checkout} group reports the whole journey as one line,
 * next to its steps.
 */
public class L04_RealisticUsersSimulation extends Simulation {

    ScenarioBuilder scenario = scenario("Shopper")
            .exec(group("checkout").on(
                    exec(http("browse").get("/get?page=products").check(status().is(200)))
                            .pause(1, 3)    // reads the page for 1 to 3 seconds
                            .exec(http("add to basket").post("/post")
                                    .body(StringBody("{\"item\": 42, \"quantity\": 1}")).asJson()
                                    .check(status().is(200)))
                            .pause(1)
                            .exec(http("place order").get("/status/201").check(status().is(201)))));

    {
        // 10 users arriving evenly over 20 seconds.
        setUp(scenario.injectOpen(rampUsers(10).during(20)))
                .protocols(Playground.protocol());
    }
}
