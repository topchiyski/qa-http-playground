# Lesson 9: Stress test

**Level:** advanced · **Time:** about 1 hour

## Goal

Increase load in steps until the system degrades, and find the "knee": the load where response times start to climb.

## Concept

A load test asks "does it meet its targets at the expected load?". A **stress test** asks "how much can it take, and
what happens then?". You keep adding load until something gives: response times rise, errors appear, throughput stops
growing.

**Step load.** Increase the arrival rate in steps and hold each step long enough to settle:

```java
incrementUsersPerSec(step).times(5).eachLevelLasting(20).startingFrom(step)
```

**The knee.** Plot response time against load. At first it's flat: the system has spare capacity. Then, at some load,
it bends upwards: requests start to queue. That bend is the knee. It tells you the real capacity, which is usually
well below the point where errors start. Past the knee, small increases in load cause large increases in response
time.

**No assertions.** A stress test is *expected* to break, so a pass/fail rule makes little sense. The result is a
number ("p95 starts rising at about 60 users per second on this machine") and a description of *how* it failed. A
system that slows down gracefully is better than one that falls over.

**Mind your laptop.** Gatling and the playground share one machine, so the load generator competes with the system
under test. The knee you find describes this setup, not production ([load-testing.md §3](../../load-testing.md#3-why-your-laptop-numbers-describe-your-laptop)).
Lesson 11 deals with that.

## Example

[`L09_StressSimulation`](../../../examples-gatling/src/test/java/io/qaplayground/course/advanced/L09_StressSimulation.java):
5 steps of 20 seconds, adding `-Dusers` (default 5) new users per second at each step.

## Run it

```bash
./mvnw -pl examples-gatling gatling:test -Dgatling.simulationClass=io.qaplayground.course.advanced.L09_StressSimulation -Dusers=40
```

With the default `-Dusers=5` the playground hardly notices (3 000 requests, no errors). Raise it until the report
changes.

## Exercises

### 9.1 Find the knee

**Steps:** run it with `-Dusers=20`, `40`, `80`, … (one run each). In each report, open *Response time percentiles over
time* and *Number of requests per second*.

**Done when:** ☐ you've found the step where p95 starts rising faster than the load ☐ you've written down the arrival
rate there and what failed first (latency or errors).

### 9.2 Throughput stops following load

**Steps:** in your heaviest run, compare *users started per second* with *responses per second*.

**Done when:** ☐ you can say at which step responses per second stopped keeping up with the arrival rate, and what that
means for the users arriving after it.

## Next

[Lesson 10: Soak test](10-soak-test.md)
