# Lesson 10: Reuse without copy-paste

**Level:** intermediate · **Time:** about 45 minutes

## Goal

Move what every request sends and every response must satisfy into one place, so each test shows only what is special
about it.

## Concept

After a few lessons, the same lines repeat: the same headers, the same "200 and JSON" checks, the same "no redirects"
config. Repetition makes a suite harder to read and to change: when the auth header changes, you edit fifty tests.

REST Assured has two building blocks for this:

- a **`RequestSpecification`** (built with `RequestSpecBuilder`): headers, content type, auth, base path, config.
  Apply it with `given().spec(...)`.
- a **`ResponseSpecification`** (built with `ResponseSpecBuilder`): expected status, content type, headers, body
  conditions. Apply it with `then().spec(...)`.

Keep specifications as `static final` fields in a base class or a small `Specs` class. Don't hide the interesting
part of a test inside one: if a reader has to open the spec to understand what a test checks, it hides too much.

`CourseTest` already does one piece of this for you: it calls
`RestAssured.enableLoggingOfRequestAndResponseIfValidationFails()` once, so every test logs only when it fails.

## Example

[`L10_ReuseTest`](../../../examples-restassured/src/test/java/io/qaplayground/course/intermediate/L10_ReuseTest.java)

```java
static final ResponseSpecification OK_JSON = new ResponseSpecBuilder()
        .expectStatusCode(200)
        .expectContentType(ContentType.JSON)
        .build();

given().spec(COURSE_CLIENT).get("/get").then().spec(OK_JSON);
```

## Run it

```bash
./mvnw -pl examples-restassured -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest='L10*'
```

## Exercises

[`E10_ReuseExercise`](../../../examples-restassured/src/test/java/io/qaplayground/course/exercises/E10_ReuseExercise.java)

### 10.1 A "no redirects" specification

**Steps:** build a `RequestSpecification` that turns redirects off, and reuse it for `/redirect/1` and
`/redirect-to?url=/get`. Both must return `302`.

**Done when:** ☐ green ☐ neither test mentions redirects directly.

<details><summary>Hint</summary><code>new RequestSpecBuilder().setConfig(RestAssuredConfig.config().redirect(RedirectConfig.redirectConfig().followRedirects(false))).build()</code></details>

**Solution:** [`S10_ReuseSolution`](../../../examples-restassured/src/test/java/io/qaplayground/course/solutions/S10_ReuseSolution.java)

## Next

[Lesson 11: Contract checks](11-contract-checks.md)
