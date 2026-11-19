# Lesson 2: Assert on JSON

**Level:** beginner · **Time:** about 45 minutes

## Goal

Pick values out of a JSON body with GPath, check them with Hamcrest matchers, and read a failure message.

## Concept

Most APIs answer in JSON: objects (`{...}`), lists (`[...]`), strings, numbers and booleans, nested inside each other
([Start here §5](../../start-here.md#5-json-in-five-minutes)). REST Assured finds a value with a **GPath** expression:

| Expression | Means |
|---|---|
| `slideshow.title` | the `title` field inside the `slideshow` object |
| `slideshow.slides[0].title` | the title of the first slide |
| `slideshow.slides.title` | the titles of *all* slides, as a list |
| `slideshow.slides.size()` | how many slides there are |

A **Hamcrest matcher** says what the value must look like: `equalTo(...)`, `hasItem(...)`, `hasSize(...)`,
`containsString(...)`, `greaterThan(...)`, `nullValue()`, and `not(...)` around any of them.

When an assertion fails, REST Assured prints the path, what it expected and what it got. Because `CourseTest` turns it
on, it also prints the whole request and response. Read that message before changing anything: it usually tells you
whether the test or the system is wrong.

## Example

[`L02_AssertOnJsonTest`](../../../examples-restassured/src/test/java/io/qaplayground/course/beginner/L02_AssertOnJsonTest.java)

```java
given().get("/json").then()
        .body("slideshow.slides", hasSize(2))
        .body("slideshow.slides.title", hasItem("Wake up to WonderWidgets!"));
```

## Run it

```bash
./mvnw -pl examples-restassured -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest='L02*'
```

## Exercises

[`E02_AssertOnJsonExercise`](../../../examples-restassured/src/test/java/io/qaplayground/course/exercises/E02_AssertOnJsonExercise.java)

### 2.1 Find the author

**Steps:** `GET /json`; assert that `slideshow.author` is `Yours Truly`.

**Done when:** ☐ green.

### 2.2 Make it fail on purpose

**Steps:** change the expected author to `Somebody Else` and run the test. Find the expected and actual values in the
output, then change it back.

**Expected result:** `Expected: "Somebody Else"` and `Actual: Yours Truly`, followed by the request and the response.

**Done when:** ☐ you can point to the line that says which path failed.

**Solution:** [`S02_AssertOnJsonSolution`](../../../examples-restassured/src/test/java/io/qaplayground/course/solutions/S02_AssertOnJsonSolution.java)

## Next

[Lesson 3: Headers and content types](03-headers-and-content-types.md)
