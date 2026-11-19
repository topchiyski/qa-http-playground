# Lesson 8: Ramp-up load test

**Level:** intermediate · **Time:** about 1 hour

## Goal

Run a classic load test (ramp up to the expected load, then hold), parameterise it from the command line, and read
the results per request.

## Concept

A **load test** checks that the system meets its targets at the load you *expect*. Its usual shape:

1. **Ramp up** gradually to the target, so problems show up at a level you can identify, not all at once.
2. **Hold** at the target long enough for things to settle (caches warm, connection pools full).
3. Judge it with **assertions** (lesson 7).

This lesson's simulation uses the closed model with users that loop (`forever()`), so concurrency stays at the target
during the hold. `maxDuration(...)` stops the loops at the end. Half the users hit `/get` (fast) and half `/delay/1`
(a slow dependency), the kind of mix where only per-request numbers make sense.

**Parameters.** Hard-coded numbers mean editing code for every run. The simulation reads `-Dusers` and `-Dminutes`
(through `Playground.intProperty`), so the same class serves a quick check and a real test.

**Watch the server too.** The playground's metrics are at `http://localhost:8081/actuator/metrics/`. During the hold,
`jvm.threads.live` barely moves even with many users waiting in `/delay/1`: the server uses virtual threads, so a
waiting request costs almost nothing. On a classic thread-per-request server, each waiting user would hold a thread.

## Example

[`L08_RampUpSimulation`](../../../examples-gatling/src/test/java/io/qaplayground/course/intermediate/L08_RampUpSimulation.java)

```java
fast.injectClosed(rampConcurrentUsers(0).to(users / 2).during(half),
        constantConcurrentUsers(users / 2).during(half))
```

## Run it

```bash
./mvnw -pl examples-gatling gatling:test -Dgatling.simulationClass=io.qaplayground.course.intermediate.L08_RampUpSimulation -Dusers=100 -Dminutes=2
```

Without `-Dusers`/`-Dminutes`, it runs 10 users for 1 minute.

## Exercises

### 8.1 Per-request percentiles

**Steps:** run it with `-Dusers=100 -Dminutes=2`. In the report, compare p95 for `GET /get` and `GET /delay/1`, and
for *All Requests*.

**Expected result:** `/delay/1` has a p95 a little over 1000 ms and `/get` only a few milliseconds. The global p95 is
close to `/get`'s: fast users loop thousands of times while slow users wait a second per request, so almost every
request in the run is a `/get`. Half your users wait over a second, and the global number hides it completely.

**Done when:** ☐ you can explain why a single global p95 is misleading for this mix ☐ you've compared the request
counts of the two endpoints.

### 8.2 Watch the threads

**Steps:** during a run with `-Dusers=200`, open
`http://localhost:8081/actuator/metrics/jvm.threads.live` a few times.

**Done when:** ☐ you've noted the thread count before and during the run, and can explain why it barely changes.

## Next

[Lesson 9: Stress test](09-stress-test.md) (advanced)
