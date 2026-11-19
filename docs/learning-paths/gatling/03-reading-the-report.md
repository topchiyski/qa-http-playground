# Lesson 3: Reading the report

**Level:** beginner · **Time:** about 1 hour

## Goal

Read a Gatling report: response-time percentiles per request, throughput and errors. Explain why the mean misleads.

## Concept

A load test produces thousands of measurements. The report summarises them, and the summary is easy to misread.

**Percentiles, not averages.** The p95 response time means 95 % of requests were faster than this. Averages hide the
slow requests users complain about: if 90 requests take 10 ms and 10 take 2 s, the mean (209 ms) describes nobody.
The p95 (2 s) tells you one user in twenty waited two seconds. Read p50 (the typical user), p95 and p99 (the unlucky
ones) and the max (the worst case).

**Per request, not overall.** Global numbers mix fast and slow requests. In this lesson's run, `/get` takes a few
milliseconds and `/delay/0.5` over 500. The global mean lands somewhere between, a number that describes neither.

**Throughput** is requests per second the system actually handled. If you send 10 per second and it handles 10, it
keeps up. If it handles 7, requests are queuing somewhere.

**Errors** (KO) come first. A fast response that's wrong isn't a fast response.

Where to look in `index.html`:

| Section | Shows |
|---|---|
| *Global* → *Requests* table | Per request: count, OK/KO, min, percentiles, max, mean |
| *Response time distribution* | How response times are spread out (look for a long tail) |
| *Response time percentiles over time* | Whether things got worse as the test went on |
| *Number of requests per second* | Throughput over time |
| *Errors* | Every KO reason, with counts |

More background: [glossary](../../glossary.md#performance) and
[load-testing.md §3](../../load-testing.md#3-why-your-laptop-numbers-describe-your-laptop).

## Example

[`L03_ReadingTheReportSimulation`](../../../examples-gatling/src/test/java/io/qaplayground/course/beginner/L03_ReadingTheReportSimulation.java):
5 new users per second for 30 seconds, each making one fast and one slow request.

## Run it

```bash
./mvnw -pl examples-gatling gatling:test -Dgatling.simulationClass=io.qaplayground.course.beginner.L03_ReadingTheReportSimulation
```

## Exercises

### 3.1 Read the table

**Steps:** in the report, write down p50, p95, max and mean for `fast: GET /get`, for `slow: GET /delay/0.5` and for
*All Requests*.

**Done when:** ☐ you can explain why the *All Requests* mean is a poor description of both endpoints.

### 3.2 Throughput

**Steps:** find the requests-per-second chart.

**Expected result:** about 10 requests per second (5 users per second, 2 requests each) for 30 seconds.

**Done when:** ☐ you can say whether the playground kept up, and how you know.

## Next

[Lesson 4: Realistic users](04-realistic-users.md)
