# Test strategy

How qa-http-playground itself is tested: what we test, at which level, with which technique, and when a change
counts as done. The structure follows the test-strategy content of ISO/IEC/IEEE 29119-3; the test-design vocabulary
is that of the ISTQB Foundation syllabus (CTFL v4.0), with a few Advanced Test Analyst techniques where noted.

| | |
|---|---|
| Product | qa-http-playground: a standalone, httpbin-compatible HTTP API server (Spring Boot) |
| Document status | Implemented 2026-11-10. Numbers and the per-route matrix: [TRACEABILITY.md](TRACEABILITY.md) (generated) |
| Related | [TEST-DESIGN.md](TEST-DESIGN.md) (how each technique is applied) · [RISK-REGISTER.md](RISK-REGISTER.md) · [COMPATIBILITY.md](../COMPATIBILITY.md) |

## 1. Scope

**In scope:**
- Every route in the endpoint inventory (62 routes) and its query/header/body parameters.
- The pages a learner uses in a browser: the index, `/forms/post`, Swagger UI, and the HTML responses.
- Configuration through `playground.*` properties and environment variables.
- The Actuator endpoints on the management port.
- Non-functional qualities that matter for a load-testing target: behaviour under concurrency, enforced
  resource limits, and absence of thread starvation.

**Out of scope, deliberately:**
- Spring, Tomcat and Jackson themselves. We test our *use* of them (e.g. that our `RawResponseEntityHandler` stops
  Spring's ETag handling), not their correctness.
- Swagger UI internals. We test that it loads our spec and can execute a call.
- The course modules (`examples-restassured`, `examples-gatling`) as products. They are learning material: the
  build runs the REST Assured lessons and compiles the Gatling ones, so they stay correct, but they aren't the test
  basis for the server.
- Internet-facing hardening. The playground must not be exposed; see the accepted risks in the register.

## 2. Test basis and oracles

The **test basis** is what the expected results are derived from. Here it is, in order of authority:

1. **The go-httpbin source at v2.26.0 (commit `8b1bc14`).** It is the specification for behaviour.
2. **[COMPATIBILITY.md](../COMPATIBILITY.md).** Documented, accepted deviations; a deviation not listed there is a
   defect.
3. **RFC 9110 / 9111 / 9112 (HTTP), RFC 6455 (WebSocket), RFC 7616 (Digest), the WHATWG HTML spec for SSE.** These
   apply where the reference is silent.
4. **The endpoint guides in [docs/endpoints/](../endpoints/).** Promises we make to learners.

**Oracles:**

| Oracle | Used for |
|---|---|
| **Specified result** (hand-derived from the basis) | Most API and unit tests |
| **Known-answer vectors** (e.g. `/bytes/16?seed=1234567890`, RFC 7616 digest examples) | Ports of Go algorithms |
| **Properties / invariants** (round trips, determinism, idempotence) | Property-based unit tests |
| **Consistency** (HEAD equals GET minus body; OpenAPI paths equal controller routes) | Checklist and contract tests |
| **Human judgement** | Exploratory sessions |

We never use a public httpbin instance as an automated oracle: expected results are derived from the reference
source, not from live traffic to it.

## 3. Test levels

```text
               ┌────────────────┐
               │      E2E       │  Selenium WebDriver: browser journeys, accessibility
             ┌─┴────────────────┴─┐
             │     API / system   │  REST Assured, black-box, any running instance
           ┌─┴────────────────────┴─┐
           │ Integration / component │  Spring context + real HTTP, one concern at a time
         ┌─┴────────────────────────┴─┐
         │            Unit            │  Go ports, parsers, framing; no Spring, milliseconds
         └────────────────────────────┘
```

| Level | Objective | Test object | Runner, location | Environment |
|---|---|---|---|---|
| **Unit** | Each Go port, parser and codec behaves like its Go original on all input classes | Classes in `support`, `auth`, `websocket` | JUnit + AssertJ + seeded generators, `playground-server/src/test/**/*Test` (Surefire) | Plain JVM, no Spring |
| **Integration / component** | Our components work *together with the framework*: filters, error handling, response handling, config binding, management port, WebSocket upgrade, OpenAPI contract | The Spring application | `@SpringBootTest(RANDOM_PORT)` + JDK `HttpClient`, `playground-server/src/test/**/*IT` (Failsafe) | In-process server, overridden properties per test class |
| **API / system** | Every endpoint meets its specification from the outside, over real HTTP | The whole server as a black box | REST Assured, `playground-server` (`api` package) | In-process server by default, or any instance via `-Dplayground.baseUri` |
| **E2E** | A learner's browser journeys work end to end; the pages are accessible | Server + pages + browser | Selenium WebDriver + Page Objects, `playground-server` (`e2e` package) | Headless Chrome (or Firefox with `-Dbrowser=firefox`), drivers via Selenium Manager |

The REST Assured suite has two jobs. It is tutorial material, so tests stay readable and commented, and it is the
system test suite. When those two goals conflict, readability wins in the tutorial classes, and the extra
systematic cases go in clearly named technique classes alongside them (e.g. `DecisionTableTest`).

## 4. Risk-based test depth

Depth follows the product risks in [RISK-REGISTER.md](RISK-REGISTER.md):

| Risk level | What every area at that level gets |
|---|---|
| **High** | All applicable black-box techniques, negative/security cases |
| **Medium** | Equivalence partitioning + boundary values + one structural or state-based technique |
| **Low** | One representative per partition and the HTTP conformance checklist |

## 5. Test types

| Type | How |
|---|---|
| Functional | All levels |
| **Compatibility** | Expected results derived from the go-httpbin source; deviations listed in COMPATIBILITY.md |
| **Security** | Negative suite tagged `type:security` (`@SecurityTest`): open redirect, reflected content, CRLF injection, oversized bodies/headers, limit bypass |
| **Performance** | Gatling smoke (lesson 2), ramp, stress and soak on demand with documented hardware ([load-testing.md](../load-testing.md)) |
| **Accessibility** | axe-core in the E2E suite, WCAG 2.1 AA rules, baseline for third-party Swagger UI findings |
| **Architecture** | ArchUnit rules: `support` is framework-free, controllers don't call each other, no field injection, tests use no `Thread.sleep` |
| **Regression** | `./mvnw verify` before every commit; every defect found gets a test that failed before the fix |

## 6. Test design techniques

Each technique is applied where it finds the most defects for the least effort. The full derivations (partitions,
boundaries, tables, state diagrams, classification trees) are in [TEST-DESIGN.md](TEST-DESIGN.md).

| Category (CTFL v4.0 ch. 4) | Technique | Main application |
|---|---|---|
| Black-box | Equivalence partitioning | Status-code classes, `Accept` media types, allowed vs disallowed methods |
| Black-box | Boundary value analysis (3-value) | Every configured limit, status-code range, counts in path parameters |
| Black-box | Decision table testing | `/redirect-to`, conditional requests, basic vs hidden basic auth |
| Black-box | State transition testing | Digest handshake, WebSocket lifecycle, browser cookie jar |
| Experience-based | Error guessing | Malformed input fault list |
| Experience-based | Exploratory testing | Charters in [EXPLORATORY-CHARTERS.md](EXPLORATORY-CHARTERS.md) |
| Experience-based | Checklist-based testing | HTTP conformance checklist over every route |
| Collaboration-based | Acceptance criteria, ATDD | Given/When/Then criteria on each E2E journey |
| *CTAL-TA* | Pairwise testing | `/drip`, `/range` parameter combinations |
| *CTAL-TA* | Classification tree | The `Range` header |
| *CTAL-TA* | Use-case testing | E2E journeys |
| *Beyond ISTQB* | Property-based testing | Round trips and determinism of Go ports |

**Traceability:** tests carry a technique tag (`tech:ep`, `tech:bva`, `tech:decision-table`, …) through a
composed annotation such as `@BoundaryValue`; pure tutorial demonstrations stay untagged. A JUnit extension records
each test's level, tags and the paths it actually requested (through a REST Assured filter, a Selenium listener or
the raw clients), and `TraceabilityReport` turns that into [TRACEABILITY.md](TRACEABILITY.md). Because the
matrix comes from what ran, it cannot drift from the code. A route without an API test, a technique without a test
or a failed test is listed under *Gaps* in the report and printed at the end of the build.

## 7. Entry and exit criteria

| Level | Entry | Exit (all must hold) |
|---|---|---|
| Unit | Code compiles | All pass |
| Integration | Unit exit met | All pass; every filter, error handler and property in `PlaygroundProperties` exercised at least once |
| API | Integration exit met | All pass; every inventory route has at least one test; every High risk has its techniques applied |
| E2E | API exit met; a browser obtainable | All journeys pass in the build's browser (Chrome by default, Firefox with `-Dbrowser=firefox`); no new serious/critical accessibility violation |

**Suspension:** see [Run the tests](../how-to/run-the-tests.md#when-results-look-wrong-on-your-machine).

## 8. Environments and tools

| Concern | Choice | Why |
|---|---|---|
| Test framework | JUnit 6 | Already used; tags drive selection and traceability |
| Assertions | Hamcrest in tutorials (REST Assured's idiom), AssertJ elsewhere | Learners meet the REST Assured style; the rest gets clearer failure messages |
| Property-based | Seeded generators (`Generators` in `io.qaplayground.testing`) on JUnit parameterized tests | Runs on the Jupiter engine, so the traceability recorder sees it; each sample's seed is in its name, so a failure is reproducible. No shrinking: a failing sample is minimised by hand and kept as a fixed example. jqwik was evaluated; it targets JUnit Platform 1.x, while the project runs JUnit 6. |
| API | REST Assured | The tool learners are here to learn |
| Browser | Selenium WebDriver 4 with Selenium Manager | Driver and browser provisioned automatically, so the JDK-only promise holds |
| Accessibility | axe-core for Selenium | Industry-standard rules engine |
| Architecture | ArchUnit | Rules run as ordinary tests |
| BDD framework | **None** (no Cucumber) | Given/When/Then lives in test names and Javadoc; a Gherkin layer would add tooling without adding information for this audience |

**Who needs what locally:** only a JDK. The commands for each level and option are in
[Run the tests](../how-to/run-the-tests.md).

## 9. Test data

- Tests generate their own data; no shared fixtures that one test could corrupt for another.
- Randomness is seeded where the endpoint allows it (`seed=`) so failures reproduce.
- Time-dependent tests assert ranges, not exact values, and use lowered limits (e.g. `max-duration=1s`) to keep
  runs fast without changing the logic under test.

## 10. Defect management

- Each defect found is first reproduced as a failing test, then fixed.
- If the fix is a deliberate deviation from the reference, it goes into COMPATIBILITY.md in the same change.
- Flaky tests are fixed or quarantined with a tracking issue within one working day; there are no automatic
  retries, because a retry hides the flakiness it is meant to work around.

## 11. Metrics

Reported by every full build, under `target/`:

| Metric | Purpose |
|---|---|
| Tests per level and per technique (from tags) | Shows the pyramid shape and technique coverage |
| Routes without a test at each level | Must be zero at API level |

Reports (JUnit, traceability, E2E screenshots) are written under each module's `target/` directory, pass or fail.
