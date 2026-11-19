package io.qaplayground.course.intermediate;

import java.time.Duration;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.qaplayground.course.Playground;

import static io.gatling.javaapi.core.CoreDsl.constantConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Lesson 8: a ramp-up load test.
 *
 * <p>Concurrency climbs from 0 to {@code -Dusers} over the first half of {@code -Dminutes}, then holds. Half the
 * users call a fast endpoint and half a slow one. One overall number would hide the slow half, so read the report
 * per request.
 *
 * <pre>-Dusers=100 -Dminutes=2   (defaults: 10 users, 1 minute)</pre>
 */
public class L08_RampUpSimulation extends Simulation {

    int users = Playground.intProperty("users", 10);
    int minutes = Playground.intProperty("minutes", 1);
    Duration half = Duration.ofSeconds(minutes * 60L / 2);

    // forever(): in a closed model each user loops, so concurrency stays at the target.
    ScenarioBuilder fast = scenario("Fast").forever().on(
            http("GET /get").get("/get").check(status().is(200)));

    ScenarioBuilder slow = scenario("Slow").forever().on(
            http("GET /delay/1").get("/delay/1").check(status().is(200)));

    {
        setUp(
                fast.injectClosed(rampConcurrentUsers(0).to(users / 2).during(half),
                        constantConcurrentUsers(users / 2).during(half)),
                slow.injectClosed(rampConcurrentUsers(0).to(users / 2).during(half),
                        constantConcurrentUsers(users / 2).during(half)))
                .protocols(Playground.protocol())
                .maxDuration(half.multipliedBy(2))    // stop the forever() loops at the end
                .assertions(
                        global().failedRequests().percent().lt(1.0),
                        global().responseTime().percentile(95.0).lt(1500));
    }
}
