# Lesson 8: Authentication

**Level:** intermediate · **Time:** about 1 hour

## Goal

Test endpoints protected by basic, bearer and digest authentication, and explain preemptive vs. challenge-based
credentials.

## Concept

| Scheme | What the client sends | REST Assured |
|---|---|---|
| Basic | `Authorization: Basic base64(user:password)` | `auth().preemptive().basic("u", "p")` |
| Bearer token | `Authorization: Bearer <token>` | `header("Authorization", "Bearer abc")` |
| Digest | A hash of the password and a server *nonce*, after a `401` challenge | `auth().digest("u", "p")` |

**Preemptive vs. challenge.** `auth().basic(...)` is polite: it sends nothing until the server answers `401` with a
`WWW-Authenticate` header, then retries with credentials. `auth().preemptive().basic(...)` sends them on the first
request. Most real APIs never send the challenge, so use **preemptive** unless you are testing the challenge itself.
Digest always needs the challenge, because the hash includes the server's nonce.

Test both sides: the right credentials get in (`200`), and wrong or missing ones are refused (`401`, with a
`WWW-Authenticate` header telling the client how to authenticate).

## Example

[`L08_AuthenticationTest`](../../../examples-restassured/src/test/java/io/qaplayground/course/intermediate/L08_AuthenticationTest.java)

```java
// This endpoint never sends a 401 challenge, so only the preemptive style gets in.
given().auth().basic("u", "p").get("/hidden-basic-auth/u/p").then().statusCode(404);
given().auth().preemptive().basic("u", "p").get("/hidden-basic-auth/u/p").then().statusCode(200);
```

## Run it

```bash
./mvnw -pl examples-restassured -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest='L08*'
```

## Exercises

[`E08_AuthenticationExercise`](../../../examples-restassured/src/test/java/io/qaplayground/course/exercises/E08_AuthenticationExercise.java)

### 8.1 No token

**Steps:** `GET /bearer` without a token. Assert `401` and a `WWW-Authenticate` header containing `Bearer`.

**Done when:** ☐ green.

### 8.2 Digest

**Steps:** `GET /digest-auth/auth/u/p` with `auth().digest("u", "p")`. Assert `200`.

**Done when:** ☐ green ☐ you can say why `preemptive()` doesn't exist for digest.

**Solution:** [`S08_AuthenticationSolution`](../../../examples-restassured/src/test/java/io/qaplayground/course/solutions/S08_AuthenticationSolution.java)

## Next

[Lesson 9: Redirects and cookies](09-redirects-and-cookies.md)
