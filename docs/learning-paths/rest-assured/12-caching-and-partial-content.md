# Lesson 12: Caching and partial content

**Level:** advanced · **Time:** about 1.5 hours

## Goal

Test conditional requests (ETag and `If-Modified-Since` → `304`) and range requests (`Range` → `206`).

## Concept

**Conditional requests** let a client reuse the copy it already has. The server labels a response with a version, and
the client sends the label back:

| Server sends | Client sends back | Unchanged → |
|---|---|---|
| `ETag: "abc"` (a version id) | `If-None-Match: "abc"` | `304 Not Modified`, no body |
| `Last-Modified: <date>` | `If-Modified-Since: <date>` | `304 Not Modified`, no body |

`Cache-Control` says how long a copy may be reused at all (`max-age=60`: 60 seconds) and by whom (`public`,
`private`, `no-store`). Caching bugs are expensive because they're invisible: users silently see stale data, or the
server does work it could skip.

**Range requests** fetch part of a resource, which is how downloads resume and videos seek. The client sends
`Range: bytes=0-9`; the server answers `206 Partial Content` with `Content-Range: bytes 0-9/26` (first-last/total) and
only those bytes.

What to test: the `304` really has no body; the `206` has the right `Content-Range`; the parts join up to exactly
the full resource; a range outside the resource is refused.

## Example

[`L12_CachingAndRangesTest`](../../../examples-restassured/src/test/java/io/qaplayground/course/advanced/L12_CachingAndRangesTest.java)

```java
String etag = given().get("/etag/abc").then().statusCode(200).extract().header("ETag");

given().header("If-None-Match", etag).get("/etag/abc").then().statusCode(304);
```

## Run it

```bash
./mvnw -pl examples-restassured -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest='L12*'
```

## Exercises

[`E12_CachingAndRangesExercise`](../../../examples-restassured/src/test/java/io/qaplayground/course/exercises/E12_CachingAndRangesExercise.java)

### 12.1 Download in parts

**Steps:** fetch `/range/100` in four requests of 25 bytes (`bytes=0-24`, `25-49`, `50-74`, `75-99`). Check that each
is `206` with the right `Content-Range`, join the bodies, and compare with the full `GET /range/100`.

**Expected result:** `Content-Range: bytes 0-24/100`, …, `bytes 75-99/100`; the joined text equals the full body.

**Done when:** ☐ green.

<details><summary>Hint</summary>A loop over <code>start = 0, 25, 50, 75</code> with <code>end = start + 24</code>; <code>.extract().asString()</code> gives each body.</details>

**Solution:** [`S12_CachingAndRangesSolution`](../../../examples-restassured/src/test/java/io/qaplayground/course/solutions/S12_CachingAndRangesSolution.java)

## Next

[Lesson 13: Slow and flaky services](13-slow-and-flaky-services.md)
