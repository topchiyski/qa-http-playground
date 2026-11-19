# Lesson 9: Redirects and cookies

**Level:** intermediate · **Time:** about 1 hour

## Goal

Decide when to let REST Assured follow redirects and when to stop it, and keep cookies across requests like a browser.

## Concept

**Redirects.** A `3xx` response with a `Location` header tells the client to go elsewhere. REST Assured follows it
silently by default, so your test only sees the final response. That's right when the redirect is a detail, and wrong
when the redirect *is* the behaviour you test (its status, its `Location`, how many hops there are). Turn it off per
request with `given().redirects().follow(false)`.

**Cookies.** A server sets a cookie with a `Set-Cookie` response header, and the client sends it back in `Cookie` on
later requests. REST Assured doesn't remember cookies between requests unless you give it a jar: a `CookieFilter`,
added to every request with `.filter(jar)`.

The two meet in a classic trap. `/cookies/set` answers `302` with `Set-Cookie`. If REST Assured follows that redirect
internally, the `Set-Cookie` never reaches your filter. Turn redirects off on calls that set or delete cookies.

## Example

[`L09_RedirectsAndCookiesTest`](../../../examples-restassured/src/test/java/io/qaplayground/course/intermediate/L09_RedirectsAndCookiesTest.java)

```java
CookieFilter jar = new CookieFilter();
given().filter(jar).redirects().follow(false).get("/cookies/set?a=1").then().statusCode(302);

given().filter(jar).get("/cookies").then().body("cookies.a", equalTo("1"));
```

## Run it

```bash
./mvnw -pl examples-restassured -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest='L09*'
```

## Exercises

[`E09_RedirectsAndCookiesExercise`](../../../examples-restassured/src/test/java/io/qaplayground/course/exercises/E09_RedirectsAndCookiesExercise.java)

### 9.1 Count the hops

**Steps:** with redirects off, request `/redirect/5`, then follow each `Location` yourself in a loop until the status is
no longer `302`. Count the hops.

**Expected result:** 5 hops, then a `200`.

**Done when:** ☐ green.

<details><summary>Hint</summary>Keep the response in a variable; <code>response.header("Location")</code> is the next path.</details>

### 9.2 Delete one cookie

**Steps:** with one `CookieFilter`, call `/cookies/set?a=1&b=2`, then `/cookies/delete?a` (redirects off on both).
Then `GET /cookies`.

**Expected result:** `cookies.b` is `"2"` and `cookies.a` is gone.

**Done when:** ☐ green ☐ you tried it with redirects on and saw it fail.

**Solution:** [`S09_RedirectsAndCookiesSolution`](../../../examples-restassured/src/test/java/io/qaplayground/course/solutions/S09_RedirectsAndCookiesSolution.java)

## Next

[Lesson 10: Reuse without copy-paste](10-reuse-without-copy-paste.md)
