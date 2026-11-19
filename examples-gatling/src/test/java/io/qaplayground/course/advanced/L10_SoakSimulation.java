package io.qaplayground.course.advanced;

import java.time.Duration;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.qaplayground.course.Playground;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Lesson 10: a soak test holds a normal load for a long time.
 *
 * <p>Slow leaks of memory, connections or threads never show in a two-minute run. They show as a trend over 30
 * minutes or more. Given enough time, small problems become big ones.
 *
 * <pre>-Dusers=5 (new users per second) -Dminutes=30   (the defaults)</pre>
 */
public class L10_SoakSimulation extends Simulation {

    int usersPerSecond = Playground.intProperty("users", 5);
    int minutes = Playground.intProperty("minutes", 30);

    ScenarioBuilder journey = scenario("Soak journey")
            .exec(http("GET /uuid").get("/uuid").check(status().is(200), jsonPath("$.uuid").saveAs("uuid")))
            .pause(Duration.ofMillis(200), Duration.ofMillis(800))
            .exec(http("POST /post").post("/post").body(StringBody("{\"order\": \"#{uuid}\"}")).asJson()
                    .check(status().is(200), jsonPath("$.json.order").isEL("#{uuid}")))
            .pause(Duration.ofMillis(200), Duration.ofMillis(800))
            .exec(http("GET /bytes/4096").get("/bytes/4096").check(status().is(200)));

    {
        setUp(journey.injectOpen(constantUsersPerSec(usersPerSecond).during(Duration.ofMinutes(minutes))))
                .protocols(Playground.protocol())
                .assertions(
                        global().failedRequests().percent().lt(0.1),
                        global().responseTime().percentile(95.0).lt(500),
                        global().responseTime().percentile(99.0).lt(1000));
    }
}
