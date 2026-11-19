# Lesson 2: Checks

**Level:** beginner · **Time:** about 45 minutes

## Goal

Add checks so that wrong responses count as failures, and explain why a load test without checks lies.

## Concept

Gatling counts each request as **OK** or **KO** (failed). Without checks, a request is KO only if the connection
fails. A server that answers every request with `500 Internal Server Error` would produce a perfect, fast, all-green
report. Under load, that's exactly what overloaded servers do.

A **check** is an assertion on a single response:

| Check | Means |
|---|---|
| `status().is(200)` | The status must be 200. (Gatling's default accepts any 2xx or 304.) |
| `jsonPath("$.args.source[0]").is("gatling")` | A JSON value must equal something. |
| `bodyString().exists()` | There must be a body. |

A failed check turns that request KO. The report shows how many and why.

An **assertion** is a rule for the *whole run*, checked at the end: `global().failedRequests().count().is(0L)`. If it
fails, the Maven build fails, so a load test can gate a release like any other test. Lesson 7 goes deeper.

A short run with a few users and strict checks is a **smoke test**: it answers "does it work at all, and is my
script right?". Run one before every bigger test, because a broken script under heavy load produces only noise.

## Example

[`L02_ChecksSimulation`](../../../examples-gatling/src/test/java/io/qaplayground/course/beginner/L02_ChecksSimulation.java)

```java
.exec(http("GET /get").get("/get?source=gatling")
        .check(status().is(200), jsonPath("$.args.source[0]").is("gatling")))
...
setUp(scenario.injectOpen(atOnceUsers(5)))
        .protocols(Playground.protocol())
        .assertions(global().failedRequests().count().is(0L));
```

## Run it

```bash
./mvnw -pl examples-gatling gatling:test -Dgatling.simulationClass=io.qaplayground.course.beginner.L02_ChecksSimulation
```

Expected: `request count 10`, `KO 0`, `Global: count of failed events is 0.0 : true`.

## Exercises

### 2.1 Break it, find it, fix it

**Steps:** in a copy of `L02_ChecksSimulation`, add a request to `/status/500` that checks `status().is(200)`. Run it.
Find the KOs and their reason in the console and in the report. Then fix the check: the endpoint is *supposed* to
answer 500.

**Expected result:** first run: 5 KO with `status.find.is(200), found 500`, assertion `false`, `BUILD
FAILURE`. After the fix: 0 KO.

**Done when:** ☐ you saw the failure in the report ☐ green after the fix.

**Solution:** [`S02_ChecksSolution`](../../../examples-gatling/src/test/java/io/qaplayground/course/solutions/S02_ChecksSolution.java)

## Next

[Lesson 3: Reading the report](03-reading-the-report.md)
