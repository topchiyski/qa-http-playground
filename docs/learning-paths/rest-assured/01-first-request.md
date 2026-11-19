# Lesson 1: First request

**Level:** beginner · **Time:** about 45 minutes

## Goal

Explain what a request and a response are, and write and run a REST Assured test that sends one.

## Concept

An API test does what a browser or an app does, minus the screen: it sends an HTTP **request** and checks the
**response**. A request has a **method** (`GET` reads, `POST` creates, …), a **URL** (`/get?colour=red`: a path and
query parameters) and **headers** (extra information such as `Accept: application/json`). A response has a **status
code** (`200` OK, `404` Not Found, …), headers and usually a **body**. If any of this is new, read sections 3 and 4 of
[Start here](../../start-here.md#3-anatomy-of-a-request) first; words are explained in the
[glossary](../../glossary.md#http-and-apis).

REST Assured writes this down in three parts that read like a sentence:

- `given()`: what you send (parameters, headers, body);
- `when()`: the method and the path;
- `then()`: what you expect back.

The playground's `/get` endpoint sends back what it received, which makes it the easiest thing to test: if you send
`colour=red`, the response contains `"args": {"colour": ["red"]}`. The value is a list because a query parameter can
appear more than once.

## Example

[`L01_FirstRequestTest`](../../../examples-restassured/src/test/java/io/qaplayground/course/beginner/L01_FirstRequestTest.java)

```java
given()
    .queryParam("colour", "red")
.when()
    .get("/get")
.then()
    .statusCode(200)
    .body("args.colour[0]", equalTo("red"));
```

Every lesson class extends `CourseTest`, which points REST Assured at a playground: one it starts for you, or yours
if you pass `-Dplayground.baseUri=http://localhost:8080`.

## Run it

```bash
./mvnw -pl examples-restassured -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest='L01*'
```

Or click the green arrow next to the class in your IDE. Expected: `Tests run: 2, Failures: 0`.

## Exercises

Open [`E01_FirstRequestExercise`](../../../examples-restassured/src/test/java/io/qaplayground/course/exercises/E01_FirstRequestExercise.java),
delete the `@Disabled` line and replace each `fail(...)` with your test.

### 1.1 Echo a query parameter

**Steps:** `GET /get` with the query parameter `team=qa`. Assert status `200` and that `args.team[0]` is `qa`.

**Expected result:** the test is green.

**Done when:** ☐ green ☐ you changed `qa` to `dev` in the assertion only, saw it fail, and changed it back.

### 1.2 Send a header

**Steps:** `GET /headers` with the request header `X-Course: 1` (`given().header(...)`). Assert that
`headers.X-Course[0]` is `1`.

**Done when:** ☐ green.

<details><summary>Hint</summary>The response lists every request header under <code>headers</code>; each value is a list, like query parameters.</details>

**Solution:** [`S01_FirstRequestSolution`](../../../examples-restassured/src/test/java/io/qaplayground/course/solutions/S01_FirstRequestSolution.java)

## Next

[Lesson 2: Assert on JSON](02-assert-on-json.md)
