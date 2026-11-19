# Lesson 11: Honest measurement

**Level:** advanced · **Time:** about 1.5 hours

## Goal

Report load-test numbers that mean something: know what the client and the server each measure, how the environment
skews results, and how a closed model can hide slowness (coordinated omission).

## Concept

A load test produces precise-looking numbers. Three questions decide whether they're also true.

**1. Who measured it?** Gatling measures from the client: network, connection setup and its own scheduling included.
The server measures from request in to response out (`http.server.requests` on
`http://localhost:8081/actuator/metrics/`). Both are correct, but they answer different questions. When they differ a
lot, the time is going somewhere in between.

**2. On what?** On a laptop, Gatling and the playground compete for the same CPU, memory and network stack. Limit the
server (`java -XX:ActiveProcessorCount=2 -Xmx1g -jar …`) or move the load generator to a second machine, and the
numbers change, sometimes a lot. Always report the environment with the result.
[load-testing.md §3](../../load-testing.md#3-why-your-laptop-numbers-describe-your-laptop) explains why your laptop
numbers describe your laptop.

**3. Did the test wait for the system?** In a **closed** model (lesson 6), a user sends the next request only when
the previous one returns. If the server stalls for two seconds, no requests are sent during that time, so the stall
is measured once instead of for every user who would have arrived. The tool has quietly "coordinated" with the system
and omitted the bad samples: **coordinated omission**. Real users arrive regardless (an open model), so a closed test
of a public system reports better numbers than users experience.

## Example

[`L11_HonestMeasurementSimulation`](../../../examples-gatling/src/test/java/io/qaplayground/course/advanced/L11_HonestMeasurementSimulation.java)
runs `/delay/0.5` for 30 seconds in either model: `-Dmodel=open` (10 users arrive per second) or `-Dmodel=closed`
(5 users at a time). While the server keeps up, both send about 10 requests per second.

## Run it

```bash
./mvnw -pl examples-gatling gatling:test -Dgatling.simulationClass=io.qaplayground.course.advanced.L11_HonestMeasurementSimulation -Dmodel=open
./mvnw -pl examples-gatling gatling:test -Dgatling.simulationClass=io.qaplayground.course.advanced.L11_HonestMeasurementSimulation -Dmodel=closed
```

Expected: about 300 requests open and about 280 closed.

## Exercises

### 11.1 Make the server slower

**Steps:** copy the simulation and change `/delay/0.5` to `/delay/1`, as if a dependency had become twice as slow. Run
both models again and compare request counts and throughput.

**Expected result:** the open run still sends about 300 requests: users keep arriving and the slowness hits every one
of them. The closed run sends about half as many, because its 5 users each wait twice as long, so the test backed off
exactly when the system got worse.

Response times barely differ between the two runs: `/delay` waits without using any server resources, so nothing
queues. Here the closed model hides the slowdown in the *load it sends*, not yet in latency. On a saturated server
(try the CPU-limited setup from 11.3 at a higher rate), queuing starts and the closed run's response times also look
better than what arriving users actually get.

**Done when:** ☐ you can explain, with your two counts, how a closed model hides a slowdown.

### 11.2 Client view vs. server view

**Steps:** run the open model, then read
`http://localhost:8081/actuator/metrics/http.server.requests?tag=uri:/delay/{duration}`. Compute the server-side mean
(`TOTAL_TIME / COUNT`) and compare it with Gatling's mean for the same request.

**Done when:** ☐ you can say where the difference comes from.

### 11.3 Change the environment

**Steps:** build the jar (`./mvnw -pl playground-server package -DskipTests`), start it with
`java -XX:ActiveProcessorCount=2 -Xmx1g -jar playground-server/target/playground-server-0.1.0-SNAPSHOT-exec.jar`, and
repeat the lesson 9 stress test.

**Done when:** ☐ you've written a two-line result for each run that includes the environment ☐ you can say which
number you'd put in a report, and with which caveats.

## Next

[Final project](project.md)
