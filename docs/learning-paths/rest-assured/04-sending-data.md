# Lesson 4: Sending data

**Level:** beginner · **Time:** about 1 hour

## Goal

Send query parameters, path parameters, JSON bodies and form fields, and prove they arrived.

## Concept

| You want to send | Use | On the wire |
|---|---|---|
| A query parameter | `queryParam("tag", "smoke")` | `/get?tag=smoke` |
| A value in the path | `pathParam("code", 418)` with `get("/status/{code}")` | `/status/418` |
| A JSON body | `contentType(ContentType.JSON).body(object)` | `{"title":"Dune","pages":412}` |
| An HTML form | `formParam("name", "ada")` | `name=ada` (`application/x-www-form-urlencoded`) |

For JSON, pass a `Map` or a Java **record**, and REST Assured turns it into JSON for you (*serialisation*). A record is
better for real tests, because the compiler checks the field names.

Path parameters are encoded for you, so a value with spaces or slashes can't break the URL. Prefer them to building
strings with `+`.

The playground's `/post` and `/anything` echo what they received (`json`, `form`, `args`, `method`), so each test can
check that the data arrived unchanged.

## Example

[`L04_SendingDataTest`](../../../examples-restassured/src/test/java/io/qaplayground/course/beginner/L04_SendingDataTest.java)

```java
record Book(String title, int pages) { }

given().contentType(ContentType.JSON).body(new Book("Dune", 412))
        .post("/post").then()
        .body("json.title", equalTo("Dune"))
        .body("json.pages", equalTo(412));
```

## Run it

```bash
./mvnw -pl examples-restassured -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest='L04*'
```

## Exercises

[`E04_SendingDataExercise`](../../../examples-restassured/src/test/java/io/qaplayground/course/exercises/E04_SendingDataExercise.java)

### 4.1 PUT a record

**Steps:** define a `Book` record, `PUT` one as JSON to `/anything`, and assert that `method` is `PUT` and
`json.pages` is the number you sent.

**Done when:** ☐ green.

### 4.2 A repeated query parameter

**Steps:** send `tag=smoke` and `tag=regression` in one request to `/get`. Assert that `args.tag` has size 2 and
contains both.

**Expected result:** `"args": {"tag": ["smoke", "regression"]}`.

**Done when:** ☐ green.

<details><summary>Hint</summary><code>queryParam("tag", "smoke", "regression")</code> sends both.</details>

**Solution:** [`S04_SendingDataSolution`](../../../examples-restassured/src/test/java/io/qaplayground/course/solutions/S04_SendingDataSolution.java)

## Next

[Lesson 5: Status codes and negative tests](05-status-codes-and-negative-tests.md)
