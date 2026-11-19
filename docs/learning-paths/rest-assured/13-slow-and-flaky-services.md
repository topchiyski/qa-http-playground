# Lesson 13: Slow and flaky services

**Level:** advanced · **Time:** about 1.5 hours

## Goal

Keep tests against slow or unreliable services fast and deterministic: set timeouts, bound response times sensibly,
and pin down random failures instead of retrying them away.

## Concept

**Timeouts.** By default an HTTP client may wait a very long time for a response. One hung dependency can then stall a
whole build. Set a socket timeout and test what happens when it fires:

```java
RestAssuredConfig oneSecond = RestAssuredConfig.config().httpClient(
        HttpClientConfig.httpClientConfig().setParam("http.socket.timeout", 1000));
```

A call that takes longer throws `java.net.SocketTimeoutException`. A test that fails after one second is far better
than one that waits forever.

**Response-time assertions.** `then().time(lessThan(5000L))` is useful for catching "suddenly takes ten seconds".
Keep the bound generous: functional tests run on busy machines, and tight bounds make them flaky. Real performance
measurement is what the [Gatling path](../gatling/README.md) is for.

**Flaky services.** `/unstable` returns `200` or `500` at random. A test that passes "most of the time" is worse than
none: people learn to ignore red. Make randomness controllable instead. The playground takes a `seed` (same seed, same
outcome) and a `failure_rate` (`0` = never fails, `1` = always fails). Many real systems let you do the same with test
doubles or feature flags.

**Don't retry blindly.** Retrying until green makes the build pass and the product no better: real users still get
the `500`. Retries belong in the *client* code under test, where they are a feature you test on purpose.

## Example

[`L13_SlowAndFlakyTest`](../../../examples-restassured/src/test/java/io/qaplayground/course/advanced/L13_SlowAndFlakyTest.java)

```java
assertThatThrownBy(() -> given().config(oneSecond).get("/delay/3"))
        .isInstanceOf(SocketTimeoutException.class);

given().queryParam("failure_rate", 1).get("/unstable").then().statusCode(500);
```

## Run it

```bash
./mvnw -pl examples-restassured -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest='L13*'
```

## Exercises

[`E13_SlowAndFlakyExercise`](../../../examples-restassured/src/test/java/io/qaplayground/course/exercises/E13_SlowAndFlakyExercise.java)

### 13.1 Pin a failing seed

**Steps:** find a `seed` for which `/unstable?seed=N` returns `500` (try a few with curl or a quick test). Write a test
that pins it. In a comment, explain why retrying until `200` would hide the bug.

**Done when:** ☐ green ☐ it stays green when you run it ten times.

**Solution:** [`S13_SlowAndFlakySolution`](../../../examples-restassured/src/test/java/io/qaplayground/course/solutions/S13_SlowAndFlakySolution.java)

## Next

[Lesson 14: Custom filters](14-custom-filters.md)
