# Lesson 7: Pass/fail criteria

**Level:** intermediate · **Time:** about 45 minutes

## Goal

Turn performance requirements into Gatling assertions that pass or fail the build, including an error budget.

## Concept

A load test that only produces a report needs a person to decide whether it passed. **Assertions** make that decision
explicit and repeatable. Write them from the requirements, often called **service-level objectives (SLOs)**:

| Requirement | Assertion |
|---|---|
| "95 % of requests under 500 ms" | `global().responseTime().percentile(95.0).lt(500)` |
| "More than 99 % of requests succeed" | `global().successfulRequests().percent().gt(99.0)` |
| "No errors at all" (smoke tests) | `global().failedRequests().count().is(0L)` |
| One request must be fast | `details("GET /get").responseTime().percentile(99.0).lt(200)` |

A broken assertion fails the Maven build, like a failed unit test.

**Error budgets.** Real systems fail sometimes, and demanding zero errors under load is usually unrealistic. An
**error budget** states how many failures are acceptable: "fewer than 5 % failed requests". The assertion then tells
you whether you're inside the budget, not whether everything was perfect.

Make assertions strict enough to catch a real regression and loose enough not to fail on noise. If one fails, the
first question is whether the requirement or the system is wrong. Answer it before you touch the number.

## Example

[`L07_PassFailCriteriaSimulation`](../../../examples-gatling/src/test/java/io/qaplayground/course/intermediate/L07_PassFailCriteriaSimulation.java)

```java
setUp(scenario.injectOpen(constantUsersPerSec(10).during(20)))
        .protocols(Playground.protocol())
        .assertions(
                global().responseTime().percentile(95.0).lt(500),
                global().successfulRequests().percent().gt(99.0));
```

## Run it

```bash
./mvnw -pl examples-gatling gatling:test -Dgatling.simulationClass=io.qaplayground.course.intermediate.L07_PassFailCriteriaSimulation
```

Expected: both assertions `true`, `BUILD SUCCESS`.

## Exercises

### 7.1 An error budget

**Steps:** write a simulation that requests `/unstable?failure_rate=0.02&seed=#{seed}`, feeding seeds 1 to 100 in order
(repeating). Check `status().is(200)` and assert fewer than 5 % failed requests. Run it, then lower the budget to 1 %
and run it again.

**Expected result:** 200 requests with a few KO (about 3 %): the 5 % budget passes; the 1 % budget fails with
`BUILD FAILURE`. Because the seeds are fixed, every run gives exactly the same result.

**Done when:** ☐ you've seen both outcomes ☐ you can say why fixed seeds matter here.

**Solution:** [`S07_PassFailCriteriaSolution`](../../../examples-gatling/src/test/java/io/qaplayground/course/solutions/S07_PassFailCriteriaSolution.java)

## Next

[Lesson 8: Ramp-up load test](08-ramp-up-load-test.md)
