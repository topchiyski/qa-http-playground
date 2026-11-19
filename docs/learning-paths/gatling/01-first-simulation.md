# Lesson 1: First simulation

**Level:** beginner · **Time:** about 45 minutes

## Goal

Name the three parts of a Gatling simulation, run one against your playground, and open its report.

## Concept

A load test imitates many users at once and measures how the system copes. In Gatling, each imitated user is a
**virtual user**, and a load test is a **simulation**: a Java class that extends `Simulation` and has three parts.

| Part | Answers | In code |
|---|---|---|
| **Protocol** | Where do requests go, with which common headers? | `Playground.protocol()`: `http.baseUrl("http://localhost:8080")…` |
| **Scenario** | What does *one* virtual user do? | `scenario("…").exec(http("GET /get").get("/get"))` |
| **Injection** (`setUp`) | How many users start, and when? | `scenario.injectOpen(atOnceUsers(1))` |

Every request gets a **name** (`http("GET /get")`). The report groups statistics by that name, so choose names you'll
recognise later.

Gatling doesn't run inside `./mvnw verify`: it needs a running server, and a load test is something you decide to
run. You start the playground yourself, then run the simulation with the Gatling Maven plugin.

## Example

[`L01_FirstSimulation`](../../../examples-gatling/src/test/java/io/qaplayground/course/beginner/L01_FirstSimulation.java)

```java
ScenarioBuilder scenario = scenario("First simulation")
        .exec(http("GET /get").get("/get"));

{
    setUp(scenario.injectOpen(atOnceUsers(1)))
            .protocols(Playground.protocol());
}
```

## Run it

```bash
./mvnw spring-boot:run -pl playground-server     # terminal 1: start the playground
./mvnw -pl examples-gatling gatling:test         # terminal 2: lesson 1 is the default simulation
```

Expected: a console summary with `request count 1`, then `BUILD SUCCESS`. The last lines print the path of the HTML
report: `examples-gatling/target/gatling/l01-firstsimulation-<timestamp>/index.html`.

## Exercises

### 1.1 Find your request

**Steps:** open the report. Find the request named `GET /get` and its response time.

**Done when:** ☐ you found the request in the *Requests* table ☐ you can say how long it took.

### 1.2 More users

**Steps:** copy `L01_FirstSimulation` into a new class in the same folder, for example `MyFirstSimulation`, change
`atOnceUsers(1)` to `atOnceUsers(20)`, and run it with the class's *full* name (package included):
`-Dgatling.simulationClass=io.qaplayground.course.beginner.MyFirstSimulation`.

**Expected result:** `request count 20`.

**Done when:** ☐ green ☐ you can say why the slowest of the 20 is slower than the single request in 1.1.

<details><summary>Hint</summary>All 20 arrive at the same instant and compete for the same server threads and the same network connection setup.</details>

## Next

[Lesson 2: Checks](02-checks.md)
