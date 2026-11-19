# Lesson 15: Security negative tests

**Level:** advanced · **Time:** about 1.5 hours

## Goal

Write cheap automated checks for well-known classes of attack, and explain what each one protects against.

## Concept

Security testing starts with a question every tester already asks: *what happens with input the developer didn't
expect?* You don't need to be a penetration tester to automate the basics. Each check below is one or two requests,
and each guards against a well-known class of attack:

| Attack | What you send | Safe behaviour |
|---|---|---|
| **Open redirect** | a redirect target on another domain | refused, or limited to an allow-list |
| **Header injection** (CRLF) | a value containing `%0D%0A` (a line break) | the line break never starts a new header |
| **Reflected XSS** | `<script>` in input that comes back in HTML | escaped as `&lt;script&gt;` |
| **Oversized input** | a body or value over the limit | `400`/`413`, not a crash or a slow death |
| **Broken authentication** | no, wrong or partial credentials | `401` every time |

Two habits matter more than any single check:

1. **Assert the safe behaviour, not only "not 500".** A `200` that echoes your script unescaped is the bug.
2. **Document accepted risks as tests.** The playground allows open redirects by default, because it's a test tool.
   `openRedirectIsPossibleByDefault` makes that a deliberate, visible decision. If someone changes it, a test
   tells them.

Only test systems you own or have written permission to test. The playground is yours, so test it freely.

## Example

[`L15_SecurityNegativeTest`](../../../examples-restassured/src/test/java/io/qaplayground/course/advanced/L15_SecurityNegativeTest.java)

```java
Response response = given().urlEncodingEnabled(false)
        .get("/response-headers?X-Test=a%0D%0ASet-Cookie:%20pwned=1");

assertThat(response.headers().getValues("Set-Cookie")).noneMatch(value -> value.contains("pwned"));
```

`urlEncodingEnabled(false)` sends `%0D%0A` exactly as written; otherwise REST Assured would encode the `%` itself.

## Run it

```bash
./mvnw -pl examples-restassured -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest='L15*'
```

## Exercises

[`E15_SecurityNegativeExercise`](../../../examples-restassured/src/test/java/io/qaplayground/course/exercises/E15_SecurityNegativeExercise.java)

### 15.1 Wrong user, right password

**Steps:** call `/basic-auth/u/p` with preemptive basic auth as user `x`, password `p`. Expect `401`.

**Done when:** ☐ green ☐ you can name one more credential variation worth a test.

### 15.2 Markup is escaped

**Steps:** `GET /response-headers` with query parameters `Content-Type=text/html` and `x=<script>`. Assert that the
body does not contain `<script>` and does contain `&lt;script&gt;`.

**Done when:** ☐ green ☐ you can explain what an attacker could do if it came back raw.

**Solution:** [`S15_SecurityNegativeSolution`](../../../examples-restassured/src/test/java/io/qaplayground/course/solutions/S15_SecurityNegativeSolution.java)

## Next

[Final project](project.md)
