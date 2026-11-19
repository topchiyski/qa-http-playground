# Lesson 10: Soak test

**Level:** advanced · **Time:** 30–45 minutes of running, about 30 minutes of work

## Goal

Hold a normal load for a long time and decide, from the server's metrics, whether something is leaking.

## Concept

Some problems only show up over time: memory that is never freed, connections that are never returned to a pool,
threads that pile up, caches that grow without bound. A two-minute test never sees them. A **soak test** (or
endurance test) holds an ordinary, sustainable load for 30 minutes to several hours, and you watch the **trend**.

Gatling only sees the client side. For leaks, watch the server too. The playground exposes metrics at
`http://localhost:8081/actuator/metrics/`:

| Metric | URL | A leak looks like |
|---|---|---|
| Heap in use | `/actuator/metrics/jvm.memory.used?tag=area:heap` | the *lowest* points keep rising |
| Live threads | `/actuator/metrics/jvm.threads.live` | a count that only goes up |
| Request timings | `/actuator/metrics/http.server.requests` | server-side time rising while load is constant |

**Reading heap correctly.** Java memory goes up and down all the time: objects are created, then garbage collection
frees them in bursts, so a sawtooth is normal. What matters is the bottom of each tooth, the memory still in use
right after a collection. If the bottoms keep rising over the run, something is holding on to memory.

## Example

[`L10_SoakSimulation`](../../../examples-gatling/src/test/java/io/qaplayground/course/advanced/L10_SoakSimulation.java):
a three-step journey with think time, 5 new users per second for 30 minutes, with strict assertions (under 0.1 %
failures, p95 under 500 ms, p99 under 1 s).

## Run it

```bash
./mvnw -pl examples-gatling gatling:test -Dgatling.simulationClass=io.qaplayground.course.advanced.L10_SoakSimulation
```

Try it first with `-Dminutes=1` (900 requests) to check that your setup works, then run the full 30 minutes.

## Exercises

### 10.1 Keep a log

**Steps:** run the full soak. Every 5 minutes, write down heap in use and live threads in a table like this one:

| Minute | Heap used (MB) | Live threads |
|---:|---:|---:|
| 0 | | |
| 5 | | |
| … | | |
| 30 | | |

**Done when:** ☐ the table is complete ☐ the assertions passed (or you know why not).

### 10.2 Leak or not?

**Steps:** look at your table and the report's *Response time percentiles over time*.

**Done when:** ☐ you can say whether there is a leak, and point to the evidence: a rising floor, not a sawtooth.

## Next

[Lesson 11: Honest measurement](11-honest-measurement.md)
