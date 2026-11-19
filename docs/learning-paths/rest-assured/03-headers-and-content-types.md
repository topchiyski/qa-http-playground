# Lesson 3: Headers and content types

**Level:** beginner · **Time:** about 45 minutes

## Goal

Assert on response headers, and test bodies that are plain text, HTML or XML.

## Concept

Headers carry information *about* the response: how long to cache it, where to redirect, what type of content the
body is. The most important one is **`Content-Type`**: it tells the client how to read the body. A JSON body sent as
`text/html` breaks real clients even though it looks fine in a browser, so checking the content type is a cheap test
that catches real bugs.

- `.contentType(ContentType.JSON)` checks for `application/json`;
- `.contentType(startsWith("text/plain"))` allows a `;charset=...` suffix;
- `.header("X-Test", equalTo("1"))` checks any header by name (names are case-insensitive).

For bodies that are not JSON, match the text with `containsString(...)`. For XML, REST Assured switches to **XmlPath**
automatically: paths look the same, and `@name` selects an attribute.

## Example

[`L03_HeadersAndContentTypesTest`](../../../examples-restassured/src/test/java/io/qaplayground/course/beginner/L03_HeadersAndContentTypesTest.java)

```java
given().get("/robots.txt").then()
        .statusCode(200)
        .contentType(startsWith("text/plain"))
        .body(containsString("Disallow: /deny"));
```

## Run it

```bash
./mvnw -pl examples-restassured -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest='L03*'
```

## Exercises

[`E03_HeadersAndContentTypesExercise`](../../../examples-restassured/src/test/java/io/qaplayground/course/exercises/E03_HeadersAndContentTypesExercise.java)

### 3.1 An HTML page

**Steps:** `GET /html`; assert a content type starting with `text/html` and a body containing `Moby-Dick`.

**Done when:** ☐ green ☐ you made it fail by expecting `application/json` and read the message.

### 3.2 An XML attribute

**Steps:** `GET /xml`; assert that the `title` attribute of the root `slideshow` element is `Sample Slide Show`.

**Done when:** ☐ green.

<details><summary>Hint</summary>The path is <code>slideshow.@title</code>.</details>

**Solution:** [`S03_HeadersAndContentTypesSolution`](../../../examples-restassured/src/test/java/io/qaplayground/course/solutions/S03_HeadersAndContentTypesSolution.java)

## Next

[Lesson 4: Sending data](04-sending-data.md)
