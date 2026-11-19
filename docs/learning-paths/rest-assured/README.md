# REST Assured path

Automated API tests in Java with [REST Assured](https://rest-assured.io/) and JUnit 5. You start with a single
request and finish by designing a complete suite yourself.

**You need:** JDK 21, an IDE and basic Java (classes, methods, imports). New to HTTP? Read
[Start here](../../start-here.md) first.

**Code:** `examples-restassured/src/test/java/io/qaplayground/course/`. Lessons are in `beginner/`, `intermediate/`
and `advanced/`; exercise stubs in `exercises/`; solutions in `solutions/`. Every lesson class extends `CourseTest`,
which points REST Assured at a playground for you.

## Beginner: write and read a single request (about 4 h)

After this level you can write a test for one endpoint, assert on its status, headers and body, and read a failure.

| # | Lesson | You learn |
|---|---|---|
| 1 | [First request](01-first-request.md) | Request and response, `given/when/then`, running one test |
| 2 | [Assert on JSON](02-assert-on-json.md) | GPath paths, Hamcrest matchers, reading a failure message |
| 3 | [Headers and content types](03-headers-and-content-types.md) | Headers, plain text, HTML and XML bodies |
| 4 | [Sending data](04-sending-data.md) | Query and path parameters, JSON bodies, forms |
| 5 | [Status codes and negative tests](05-status-codes-and-negative-tests.md) | Error responses and why negative tests matter |

## Intermediate: real suites (about 6 h)

After this level you can build a suite that is data-driven, authenticated, readable and free of copy-paste.

| # | Lesson | You learn |
|---|---|---|
| 6 | [Extract and chain](06-extract-and-chain.md) | Using one response in the next request; mapping JSON to a Java record |
| 7 | [Data-driven tests and test design](07-data-driven-and-test-design.md) | Parameterised tests; equivalence partitions and boundary values |
| 8 | [Authentication](08-authentication.md) | Basic, preemptive vs. challenge, bearer and digest |
| 9 | [Redirects and cookies](09-redirects-and-cookies.md) | Controlling redirects; keeping a session |
| 10 | [Reuse without copy-paste](10-reuse-without-copy-paste.md) | Request and response specifications |
| 11 | [Contract checks](11-contract-checks.md) | JSON Schema validation |

## Advanced: the hard parts of HTTP (about 6 h)

After this level you can test caching, timeouts and flaky services without fooling yourself, extend REST Assured, and
write security negative tests.

| # | Lesson | You learn |
|---|---|---|
| 12 | [Caching and partial content](12-caching-and-partial-content.md) | ETag and 304, `Range` and 206 |
| 13 | [Slow and flaky services](13-slow-and-flaky-services.md) | Timeouts, response-time bounds, deterministic flakiness |
| 14 | [Custom filters](14-custom-filters.md) | Writing your own REST Assured `Filter` |
| 15 | [Security negative tests](15-security-negative-tests.md) | Open redirects, oversized bodies, header injection, missing credentials |

**[Final project](project.md):** design and write a complete suite for one feature.

## How to run

```bash
./mvnw -pl examples-restassured -am test -Dtest='L01*' -Dsurefire.failIfNoSpecifiedTests=false   # one lesson
./mvnw -pl examples-restassured -am test -Dtest='E01*' -Dsurefire.failIfNoSpecifiedTests=false   # your exercise
```

The first run builds the playground too (`-am`), so it takes a minute. In the IDE, use the green arrow next to a
class or method.

The REST Assured lessons start their own playground. To use one you started yourself (`./mvnw spring-boot:run`), add
`-Dplayground.baseUri=http://localhost:8080`.

## How exercises work

The exercises are already in the repository, as stubs in
`examples-restassured/src/test/java/io/qaplayground/course/exercises/`:

1. Open the exercise class, for example `E01_FirstRequestExercise`.
2. Delete the line `@Disabled("Remove this line when you start the exercise")`.
3. Replace the `fail("Not done yet: ...")` line with your test, following the `// TODO` steps.
4. Run it until it is green, then compare it with the solution (`S01_FirstRequestSolution` in `course/solutions/`).

The solutions run in every build, so they always match the current playground.

**Naming your own test classes:** end the name with `Test` (for example `MyCookieJarSuiteTest`). The build only runs
classes named `…Test`, `…Exercise` or `…Solution`, so a class with any other name is skipped without warning.
Name test methods in lowerCamelCase, as a sentence that says what is checked (for example `unknownPathIs404`).
