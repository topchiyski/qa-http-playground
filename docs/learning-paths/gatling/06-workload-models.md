# Lesson 6: Workload models

**Level:** intermediate · **Time:** about 1 hour

## Goal

Choose between an open and a closed workload model, and explain why only the open model can overload a server.

## Concept

There are two ways to describe load, and they behave very differently when the system slows down
([load-testing.md](../../load-testing.md) has the background):

| | **Closed** model | **Open** model |
|---|---|---|
| You set | how many users are active at once | how many new users arrive per second |
| Gatling | `injectClosed(rampConcurrentUsers(1).to(10)…)`, `constantConcurrentUsers(…)` | `injectOpen(rampUsersPerSec(1).to(20)…)`, `constantUsersPerSec(…)` |
| When the server slows down | users wait, so fewer requests are sent; the load backs off | users keep arriving; requests queue up; response times grow without limit |
| Models | a fixed group of users: call-centre agents, batch workers, a connection pool | the public: web shops, APIs, anything where users don't know about each other |

The trap: a closed test can make a struggling system look healthy, because the slowdown reduces the load that caused
it. Most public systems face open traffic. If you test them with a closed model, you never see what really happens
when they slow down at peak time.

In both models, a user runs the scenario once and leaves, unless the scenario loops (`forever()`, lesson 8).

## Example

[`L06_WorkloadModelsSimulation`](../../../examples-gatling/src/test/java/io/qaplayground/course/intermediate/L06_WorkloadModelsSimulation.java)
is the closed model on `/delay/0.2`:

```java
setUp(scenario.injectClosed(rampConcurrentUsers(1).to(10).during(30)))
```

## Run it

```bash
./mvnw -pl examples-gatling gatling:test -Dgatling.simulationClass=io.qaplayground.course.intermediate.L06_WorkloadModelsSimulation
```

## Exercises

### 6.1 The open model

**Steps:** copy the lesson and switch to the open model: `injectOpen(rampUsersPerSec(1).to(20).during(30))`. Run both
and compare the requests-per-second charts and the response times.

**Expected result:** the closed run's throughput grows with the number of active users and is capped by how fast
responses come back (at most about 10 users / 0.2 s = 50 requests per second). The open run's throughput follows the
arrival rate you set, whatever the response times.

**Done when:** ☐ you can explain, from the two charts, why only the open model could overload a server.

**Solution:** [`S06_WorkloadModelsSolution`](../../../examples-gatling/src/test/java/io/qaplayground/course/solutions/S06_WorkloadModelsSolution.java)

## Next

[Lesson 7: Pass/fail criteria](07-pass-fail-criteria.md)
