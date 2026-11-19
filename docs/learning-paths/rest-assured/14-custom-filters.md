# Lesson 14: Custom filters

**Level:** advanced · **Time:** about 1 hour

## Goal

Write your own REST Assured `Filter` to add behaviour to every request and response in one place.

## Concept

A **filter** sits between your test and the network. It sees each request before it is sent and each response before
your assertions run. You've already used two: the `CookieFilter` from lesson 9, and the logging filter that `CourseTest`
switches on. Typical home-made filters:

- add a header to every request (a trace id, an API key, a tenant);
- measure or log timings;
- fail fast on any `5xx`, with a clear message;
- record traffic for a report.

A filter implements one method. It can change the request, must call `context.next(...)` to actually send it, and
returns the response:

```java
public Response filter(FilterableRequestSpecification request,
                       FilterableResponseSpecification response, FilterContext context) {
    request.header("X-Trace-Id", UUID.randomUUID().toString());   // before sending
    Response result = context.next(request, response);           // send
    return result;                                               // after receiving
}
```

Add it per request with `given().filter(...)`, or for every request with `RestAssured.filters(...)` in a base class.
Keep filters small and obvious. A filter that silently changes what a test sends makes failures very hard to explain.

## Example

[`L14_CustomFiltersTest`](../../../examples-restassured/src/test/java/io/qaplayground/course/advanced/L14_CustomFiltersTest.java):
a `TraceFilter` that adds `X-Trace-Id` and records how long each call took.

## Run it

```bash
./mvnw -pl examples-restassured -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest='L14*'
```

## Exercises

[`E14_CustomFiltersExercise`](../../../examples-restassured/src/test/java/io/qaplayground/course/exercises/E14_CustomFiltersExercise.java)

### 14.1 Fail fast on server errors

**Steps:** write a filter that throws an `AssertionError` when a response status is `5xx`, naming the method and the
URI. Prove it: with the filter, `GET /status/500` throws; `GET /status/404` doesn't.

**Done when:** ☐ green ☐ the error message alone tells you which request failed.

<details><summary>Hint</summary><code>request.getMethod()</code> and <code>request.getURI()</code>; check <code>result.statusCode() &gt;= 500</code> after <code>context.next(...)</code>.</details>

**Solution:** [`S14_CustomFiltersSolution`](../../../examples-restassured/src/test/java/io/qaplayground/course/solutions/S14_CustomFiltersSolution.java)

## Next

[Lesson 15: Security negative tests](15-security-negative-tests.md)
