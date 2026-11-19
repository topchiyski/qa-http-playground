# Lesson 11: Contract checks

**Level:** intermediate · **Time:** about 1 hour

## Goal

Validate the *shape* of a response (which fields exist and what type they are) with a JSON Schema.

## Concept

Value assertions check what you thought of. A field renamed from `url` to `URL`, a number that became a string, or a
required field that disappeared can all slip past them. A **JSON Schema** describes the shape of a response:

```json
{
  "type": "object",
  "required": ["args", "headers", "origin", "url"],
  "properties": {
    "url": {"type": "string"}
  }
}
```

One assertion, `body(matchesJsonSchemaInClasspath("schemas/get.json"))`, then checks every rule in the file. Schemas
live in `src/test/resources/schemas/`. The validator comes from the `io.rest-assured:json-schema-validator` dependency,
already in `examples-restassured/pom.xml`.

Two rules keep schema tests honest:

1. **Required is what matters.** Without `required`, an empty object `{}` matches almost any schema.
2. **Prove it can fail.** Check that the schema rejects a response of the wrong shape (`not(matchesJsonSchema...)`).
   A schema that accepts everything is a test that can't fail.

A schema doesn't replace value assertions: it says a `uuid` is a string, not that it's the right one.

## Example

[`L11_ContractTest`](../../../examples-restassured/src/test/java/io/qaplayground/course/intermediate/L11_ContractTest.java)
with [`schemas/get.json`](../../../examples-restassured/src/test/resources/schemas/get.json) and
[`schemas/slideshow.json`](../../../examples-restassured/src/test/resources/schemas/slideshow.json).

```java
given().get("/get").then()
        .statusCode(200)
        .body(matchesJsonSchemaInClasspath("schemas/get.json"));
```

## Run it

```bash
./mvnw -pl examples-restassured -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest='L11*'
```

## Exercises

[`E11_ContractExercise`](../../../examples-restassured/src/test/java/io/qaplayground/course/exercises/E11_ContractExercise.java)

### 11.1 A schema for /uuid

**Steps:** write `src/test/resources/schemas/uuid.json`: an object that requires a `uuid` string matching
`^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$` (`"pattern"`). Assert that `/uuid` matches it and that
`/get` does **not**.

**Done when:** ☐ green ☐ you removed `required`, saw the `/get` check fail, and put it back.

**Solution:** [`S11_ContractSolution`](../../../examples-restassured/src/test/java/io/qaplayground/course/solutions/S11_ContractSolution.java)
and its schema [`schemas/solutions/uuid.json`](../../../examples-restassured/src/test/resources/schemas/solutions/uuid.json)

## Next

[Lesson 12: Caching and partial content](12-caching-and-partial-content.md) (advanced)
