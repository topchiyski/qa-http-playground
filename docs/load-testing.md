# Load testing

This page explains how to load-test the playground safely and how to read the numbers without fooling yourself.
The playground exists so you can put serious load on a server **you own**.

## 1. Rules of the road

- Only load-test machines you own or have written permission to test. Never point these simulations at
  public httpbin instances or any other public service.
- Keep the playground on a local or lab network. It is not hardened for the internet.
- Start every session with a **smoke test**. If 5 users fail, 500 users only produce noise.

## 2. The simulations

The simulations are the lessons of the [Gatling path](learning-paths/gatling/README.md), in
`examples-gatling/src/test/java/io/qaplayground/course/`. The four classic test types:

| Lesson | Workload model | Question it answers | Assertions |
|---|---|---|---|
| [2: `L02_ChecksSimulation`](learning-paths/gatling/02-checks.md) (smoke) | 5 users at once, one pass | Does it work at all? Is my script right? | 0 failures |
| [8: `L08_RampUpSimulation`](learning-paths/gatling/08-ramp-up-load-test.md) (load) | **Closed**: ramp to *N* concurrent users (`-Dusers`, default 10) over half the time, then hold; half on `/get`, half on `/delay/1` | How does latency change with concurrency? | < 1% failures, p95 < 1.5 s |
| [9: `L09_StressSimulation`](learning-paths/gatling/09-stress-test.md) (stress) | **Open**: arrival rate stepping up in 5 steps of `-Dusers` (default 5) users/s against `/get` and `/delay/0.1` | Where is the breaking point and how does it fail? | none: you read where it bends |
| [10: `L10_SoakSimulation`](learning-paths/gatling/10-soak-test.md) (soak) | **Open**: 5 users/s for 30 min, a small journey with think time | Does it stay healthy over time (leaks)? | < 0.1% failures, p95 < 500 ms, p99 < 1 s |

How to run them, and where the reports land: [Gatling path](learning-paths/gatling/README.md#how-to-run).

**Closed vs. open models:** see [Gatling lesson 6](learning-paths/gatling/06-workload-models.md).

## 3. Why your laptop numbers describe your laptop

When the load generator and the server run on the same machine they **compete for the same CPUs, memory
bandwidth and network stack**. At high load the generator itself becomes the bottleneck. Its own scheduling
delays then show up as "server latency" in the report. Concretely:

- Throughput numbers are a property of *this laptop*, not of the code, and not of any production system.
- Latency percentiles include time the request spent waiting for the *client* to get CPU.
- Anything else running (browsers, IDEs, antivirus, **HTTP-filtering software such as AdGuard or corporate
  proxies**, which intercept local connections) changes the result.

Three ways to get more honest numbers:

1. **Fix the server's resources.** Start the jar with a fixed CPU count and heap: see
   [Run the server with fixed resources](how-to/run-the-server.md#run-it-with-fixed-resources).
2. **Separate the machines.** See [Run the server on another machine](how-to/run-the-server.md#run-it-on-another-machine).
3. **Watch the server side.** The playground measures itself on port 8081 (Spring Boot Actuator). During a
   run, ask it:

   ```bash
   curl "http://localhost:8081/actuator/metrics/http.server.requests?tag=uri:/get"   # COUNT, TOTAL_TIME, MAX
   curl http://localhost:8081/actuator/metrics/process.cpu.usage                     # the server process
   curl http://localhost:8081/actuator/metrics/system.cpu.usage                      # the whole machine
   curl http://localhost:8081/actuator/metrics/jvm.threads.live                      # platform threads
   ```

   `TOTAL_TIME / COUNT` is the server-side mean per request. Compare it with your load tool's mean. The gap
   is network plus client time. If **system** CPU is near 100% while the **process** CPU is low, something
   other than the server, often your load generator, is using the machine. The same data, including
   per-endpoint latency histograms, is at <http://localhost:8081/actuator/prometheus> for any Prometheus-compatible
   monitoring tool you already use.

## 4. Things this server does that affect load tests

- **Virtual threads.** Every request runs on a virtual thread, so `/delay`, `/drip` and streaming endpoints
  park cheaply. 250 users sleeping in `/delay/1` do not exhaust a thread pool. The platform-thread count
  (`jvm.threads.live`) stays almost flat. A classic thread-per-request server would need 250 threads for the same test.
- **Connection closing on errors.** Tomcat closes the connection after `400`, `408`, `411`, `413`, `414`,
  `500`, `501` and `503` responses (go-httpbin keeps it open). `/unstable` and `/status/5xx` therefore make
  your load tool open new TCP connections, which costs time and can exhaust ephemeral ports in very long
  runs.
- **Hard limits.** Delays and stream durations are capped at 10 s, bodies at 1 MiB (configurable). Requests
  beyond them get a fast `400`, so a mistyped parameter can make a test look surprisingly quick. Always
  assert on status codes.
- **Metrics have a cost.** Per-endpoint latency histograms are recorded for the Prometheus endpoint. They are cheap, but not free.

## 5. Measured: 500 concurrent users on `/get` and `/delay/1`

The ramp-up simulation (Gatling lesson 8, `L08_RampUpSimulation`) with `-Dusers=500 -Dminutes=5`, run on 2026-11-10 against the fat jar (`java -Xmx1g`,
no resource limits), with Gatling **on the same machine**.

**Hardware:** Intel Core i7-12700F (12 cores / 20 threads), 32 GB RAM, Windows 11 Pro, Java 21.0.12.
**Also running:** an IDE, a browser, and **AdGuard** with local HTTP filtering. AdGuard intercepts every local
HTTP connection, including Gatling's, so every request in this test passed through a filtering proxy.

| Metric (Gatling, client side) | Value |
|---|---|
| Requests | 267,241 (227,895 `/get`, 39,346 `/delay/1`) |
| Failed requests | **0** |
| Mean throughput | 888 req/s |
| Response time p50 / p95 / p99 | 273 ms / 1,494 ms / 1,725 ms |
| Max | 2,854 ms |
| Assertions | < 1% failures ✅ · p95 < 1,500 ms ✅ (just) |

| Server side (sampled every 15 s via Actuator) | Value |
|---|---|
| Platform threads | 21 at idle → **39 at peak** |
| Requests in flight at the server | up to ~180 |
| Server process CPU | **≈ 1%** throughout |
| Whole-system CPU | ≈ 27–33% |

**How to read this honestly:**

- **No server errors and no thread starvation.** With up to ~180 requests in flight, mostly sleeping in
  `/delay/1`, the server added only 18 platform threads. Virtual threads did their job.
- **The latency is not the server's.** A median of 273 ms for `/get`, a few microseconds of work, while the
  server process uses about 1% CPU, means the time is spent *outside* the server: in the interception proxy,
  in the load generator, and in connection handling between them. `/delay/1` averaging well above
  1 second, and fewer requests in flight at the server (~180) than the 250 users asking for it, point the same
  way: requests are queued *before* they reach the server.
- **So this run says** "the playground handled 500 concurrent users on this laptop without errors or thread
  growth". It does **not** say what latency the server can deliver. To measure that, repeat the test with the
  load generator on a second machine, or at least with local HTTP filtering disabled, and compare the server-side
  numbers from Actuator with Gatling's.
