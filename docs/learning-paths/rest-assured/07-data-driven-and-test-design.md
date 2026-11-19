# Lesson 7: Data-driven tests and test design

**Level:** intermediate · **Time:** about 1.5 hours

## Goal

Run one test with many inputs, and choose those inputs with two test-design techniques instead of guessing.

## Concept

**Data-driven tests.** A JUnit `@ParameterizedTest` runs the same method once per input. The inputs come from
`@ValueSource` (one value per run), `@CsvSource` (several values per run, such as an input and its expected result)
or `@MethodSource` (anything you can build in Java). Each run is reported separately, so a failure tells you exactly
which input broke.

**But which inputs?** You can't test every value, so you choose inputs that represent the rest
([glossary](../../glossary.md#testing)):

- **Equivalence partitioning:** split the inputs into groups the system should treat the same way, and test one or
  two from each group. If one member of a group works, the others very likely do too.
- **Boundary value analysis:** bugs cluster at the edges of a range (`<` instead of `<=`). For each edge, test the
  value on it and the values just inside and just outside it.

**Worked case 1: partitions.** "Redirect codes have a `Location` header" sounds like one partition: all 3xx codes. Testing
them shows two: `301, 302, 303, 307, 308` have a `Location`, and `304 Not Modified` doesn't (it means "use your
cached copy"). Finding that a group you assumed was one is really two is one of the most useful things testing can do.

**Worked case 2: boundaries.** `/bytes/{n}` accepts 0 to 1 MiB (1 048 576 bytes). The edges are 0 and 1 048 576, so
the tests are `-1 → 400`, `0 → 200`, `1 → 200`, `1048575 → 200`, `1048576 → 200` and `1048577 → 400`. That's six
requests instead of a million, and they cover the places where the code is most likely to be wrong.

## Example

[`L07_DataDrivenAndDesignTest`](../../../examples-restassured/src/test/java/io/qaplayground/course/intermediate/L07_DataDrivenAndDesignTest.java)

```java
@ParameterizedTest(name = "/bytes/{0} -> {1}")
@CsvSource({"-1, 400", "0, 200", "1, 200", "1048575, 200", "1048576, 200", "1048577, 400"})
void bytesBoundaries(int n, int status) {
    given().get("/bytes/" + n).then().statusCode(status);
}
```

## Run it

```bash
./mvnw -pl examples-restassured -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest='L07*'
```

Expected: `Tests run: 12` from three test methods, one per input.

## Exercises

[`E07_DataDrivenAndDesignExercise`](../../../examples-restassured/src/test/java/io/qaplayground/course/exercises/E07_DataDrivenAndDesignExercise.java)

### 7.1 Status code partitions

**Steps:** write three parameterised tests, one per partition: 2xx (`200`, `204`), 4xx (`400`, `418`) and 5xx (`500`,
`503`). Each asserts that `/status/{code}` returns that code.

**Done when:** ☐ the report shows 6 runs ☐ you can explain why one value per partition would also be defensible.

### 7.2 Delay boundaries

**Steps:** `/delay/{seconds}` accepts 0 to 10. Test the invalid side of both edges: `/delay/-1` and `/delay/10.1` must
return `400`.

**Done when:** ☐ green ☐ you can say why the valid edge `/delay/10` is left out of a fast test suite.

**Solution:** [`S07_DataDrivenAndDesignSolution`](../../../examples-restassured/src/test/java/io/qaplayground/course/solutions/S07_DataDrivenAndDesignSolution.java)

## Next

[Lesson 8: Authentication](08-authentication.md)
