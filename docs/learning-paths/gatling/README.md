# Gatling path

Load tests in Java with [Gatling](https://gatling.io/). Load tests answer "how does it behave under many users?",
not "is this one response correct?". The playground is a safe target: it is yours, so there is nobody to harm, and its
slow and failing endpoints (`/delay`, `/unstable`) let you create realistic trouble on purpose.

**You need:** JDK 21 and REST Assured lessons 1–5 (or equivalent HTTP basics). The concepts behind these lessons
(workload models, percentiles, why laptop numbers lie) are in [load-testing.md](../../load-testing.md); the lessons
link to the parts they need.

**Code:** `examples-gatling/src/test/java/io/qaplayground/course/`, in `beginner/`, `intermediate/`, `advanced/`,
`solutions/` and `project/`.

## Beginner: write and read one simulation (about 3 h)

After this level you can write a simulation with checks, run it and explain its report.

| # | Lesson | You learn |
|---|---|---|
| 1 | [First simulation](01-first-simulation.md) | Protocol, scenario, injection; running it; finding the report |
| 2 | [Checks](02-checks.md) | Why a load test without checks lies; OK and KO |
| 3 | [Reading the report](03-reading-the-report.md) | Percentiles, throughput, errors; why the mean misleads |
| 4 | [Realistic users](04-realistic-users.md) | Multi-step journeys, think time, groups |

## Intermediate: model real load (about 4 h)

After this level you can drive a simulation with data, choose a workload model and turn requirements into pass/fail
assertions.

| # | Lesson | You learn |
|---|---|---|
| 5 | [Dynamic data](05-dynamic-data.md) | Feeders, the session, saving and reusing values |
| 6 | [Workload models](06-workload-models.md) | Open vs. closed injection |
| 7 | [Pass/fail criteria](07-pass-fail-criteria.md) | Assertions as service-level objectives; error budgets |
| 8 | [Ramp-up load test](08-ramp-up-load-test.md) | Per-request percentiles; parameters from the command line |

## Advanced: find limits honestly (about 4 h, plus a 30-minute soak)

After this level you can find a system's breaking point, spot a leak, and report numbers that mean something.

| # | Lesson | You learn |
|---|---|---|
| 9 | [Stress test](09-stress-test.md) | Step load; finding the knee |
| 10 | [Soak test](10-soak-test.md) | Long runs, memory and thread metrics |
| 11 | [Honest measurement](11-honest-measurement.md) | Client vs. server view, environment effects, coordinated omission |

**[Final project](project.md):** a load-test plan for one user journey, its simulation, and a one-page report.

## How to run

Gatling needs a running playground. Start it in one terminal and run simulations in a second one:

```bash
./mvnw spring-boot:run -pl playground-server                         # terminal 1
./mvnw -pl examples-gatling gatling:test                             # terminal 2: lesson 1 (the default)
./mvnw -pl examples-gatling gatling:test -Dgatling.simulationClass=io.qaplayground.course.beginner.L02_ChecksSimulation
```

Each run writes an HTML report to `examples-gatling/target/gatling/<simulation>-<timestamp>/index.html`.
`./mvnw verify` compiles every simulation but does not run them.
