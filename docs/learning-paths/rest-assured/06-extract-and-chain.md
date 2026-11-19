# Lesson 6: Extract and chain

**Level:** intermediate · **Time:** about 1 hour

## Goal

Take a value out of one response, use it in the next request, and turn a JSON response into a Java object.

## Concept

Real flows are chains: create an order, read its id, fetch the order by that id. So far every assertion lived inside
`then()`. To *keep* a value, end the chain with `extract()`:

| You want | Write |
|---|---|
| One value | `.extract().path("uuid")` |
| A nested map or list | `.extract().jsonPath().getMap("json")` / `.getList("slides")` |
| The whole response as an object | `.as(GetResponse.class)` |
| The raw response (status, headers, body) | `.extract().response()` |

`.as(...)` is the opposite of lesson 4: JSON in, Java object out (*deserialisation*). Use a record with only the
fields you care about; unknown fields are ignored. Once you have a Java object, assert on it with plain Java or AssertJ
(`assertThat(...)`), which reads well for objects and collections.

Keep `then()` assertions for the response itself and AssertJ for values you've extracted. Mixing them is fine.

## Example

[`L06_ExtractAndChainTest`](../../../examples-restassured/src/test/java/io/qaplayground/course/intermediate/L06_ExtractAndChainTest.java)

```java
String id = given().get("/uuid").then().extract().path("uuid");

given().queryParam("id", id).get("/anything").then()
        .body("args.id[0]", equalTo(id));
```

## Run it

```bash
./mvnw -pl examples-restassured -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest='L06*'
```

## Exercises

[`E06_ExtractAndChainExercise`](../../../examples-restassured/src/test/java/io/qaplayground/course/exercises/E06_ExtractAndChainExercise.java)

### 6.1 What goes in comes out

**Steps:** POST `{"name": "ada", "level": 3}` (a `Map`) as JSON to `/post`. Extract the `json` field as a `Map` and
assert that it equals what you sent.

**Done when:** ☐ green.

<details><summary>Hint</summary><code>.extract().jsonPath().getMap("json")</code>, then <code>assertThat(echoed).isEqualTo(sent)</code>.</details>

### 6.2 Unique ids

**Steps:** call `/uuid` twice and assert that the two values differ.

**Done when:** ☐ green ☐ you can say what bug this test would catch.

**Solution:** [`S06_ExtractAndChainSolution`](../../../examples-restassured/src/test/java/io/qaplayground/course/solutions/S06_ExtractAndChainSolution.java)

## Next

[Lesson 7: Data-driven tests and test design](07-data-driven-and-test-design.md)
