# Lesson 5: Status codes and negative tests

**Level:** beginner · **Time:** about 45 minutes

## Goal

Test that wrong requests are refused clearly, and explain why those tests matter as much as the happy path.

## Concept

A **positive** test sends a valid request and expects success. A **negative** test sends something wrong (an unknown
path, a method the endpoint doesn't support, input it can't parse) and expects a clear refusal. Many real bugs live
there: a `500` where a `400` belongs, an error page that leaks a stack trace, a `200` for something that should have
failed.

Status codes come in families ([table in Start here](../../start-here.md#status-codes-in-one-table)):

| Family | Meaning | Examples |
|---|---|---|
| 2xx | Success | `200` OK, `201` Created, `204` No Content |
| 3xx | Go elsewhere | `302` Found, `304` Not Modified |
| 4xx | *Your* request is wrong | `400` Bad Request, `401` Unauthorized, `404` Not Found, `405` Method Not Allowed |
| 5xx | The *server* failed | `500`, `503` |

A `4xx` is a correct answer to a wrong request, while a `5xx` for bad input is a bug. A `405` must also say which
methods *are* allowed, in the `Allow` header.

`/status/{code}` returns whatever status you ask for, which is handy for practising assertions on each family.

## Example

[`L05_StatusCodesTest`](../../../examples-restassured/src/test/java/io/qaplayground/course/beginner/L05_StatusCodesTest.java)

```java
given().delete("/get").then()
        .statusCode(405)
        .header("Allow", equalTo("GET, HEAD"));
```

## Run it

```bash
./mvnw -pl examples-restassured -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest='L05*'
```

## Exercises

[`E05_StatusCodesExercise`](../../../examples-restassured/src/test/java/io/qaplayground/course/exercises/E05_StatusCodesExercise.java)

### 5.1 I'm a teapot

**Steps:** `GET /status/418`; assert status `418` and a body containing `teapot`.

**Done when:** ☐ green.

### 5.2 Bad input is a 400, not a 500

**Steps:** `GET /status/abc`; assert status `400`.

**Done when:** ☐ green ☐ you can say why a `500` here would be a bug.

**Solution:** [`S05_StatusCodesSolution`](../../../examples-restassured/src/test/java/io/qaplayground/course/solutions/S05_StatusCodesSolution.java)

## Next

[Lesson 6: Extract and chain](06-extract-and-chain.md) (intermediate)
