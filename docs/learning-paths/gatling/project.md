# Final project: plan, run and report a load test

**Level:** advanced · **Time:** 3–4 hours

## Goal

Do a small load test from start to finish, as you would at work: decide what to test, choose a workload model and
pass/fail criteria, implement it, run it, and write a short report someone else can act on.

## The brief

Pick one user journey on the playground, for example *checkout* (browse → add to basket → place order), or *sign in
and read your data* (basic auth → `/cookies/set` → `/cookies`).

1. **Plan (one page).** Write down:
   - **Goal:** the question the test answers, for example "Does checkout stay fast at the expected peak?"
   - **Workload model:** open or closed, and why (lesson 6); the target load; ramp and hold durations.
   - **Data:** what varies between users (lesson 5).
   - **SLOs:** two or three pass/fail criteria with numbers (lesson 7).
   - **Environment:** where the server and the load generator run (lesson 11).
2. **Implement** it in a new simulation, for example `course/project/MyJourneySimulation.java`. Start with a smoke run
   (lesson 2) before the real one.
3. **Run** it, then save the report.
4. **Report (one page):** use the template below.

## Report template

```text
Test:          <journey>, <model>, <load>, <duration>
Environment:   <machine, CPUs, memory; same machine as Gatling or not>
Result:        PASS / FAIL against: <each SLO with its measured value>
Key numbers:   <p50, p95, p99 per request; throughput; errors>
Observations:  <knee, trends, anything surprising>
Caveats:       <what these numbers do NOT tell you>
Next step:     <one concrete recommendation>
```

## Review checklist

- ☐ The goal is a question with a yes/no answer.
- ☐ The workload model is justified, not a default.
- ☐ Every SLO has a number and became an assertion.
- ☐ Users differ (feeder), and responses are checked (not just status codes where content matters).
- ☐ A smoke run passed before the real run.
- ☐ The report names the environment and at least one caveat.

## Reference solution

[`JourneyLoadTestSolution`](../../../examples-gatling/src/test/java/io/qaplayground/course/project/JourneyLoadTestSolution.java):
the checkout journey from lesson 4 with a CSV feeder, an open model (ramp 1 → 5 users per second over 30 s, hold 60 s)
and three SLOs. Its Javadoc is the plan in short form. Compare your plan's reasoning with it, not just the code.

```bash
./mvnw -pl examples-gatling gatling:test -Dgatling.simulationClass=io.qaplayground.course.project.JourneyLoadTestSolution
```

## What next

You've finished the Gatling path. The concepts reference, [load-testing.md](../../load-testing.md), includes measured
results from this playground to compare with yours.
