# Final project: design a test suite

**Level:** advanced · **Time:** 3–4 hours

## Goal

Design and write a complete, reviewable test suite for one feature on your own, choosing your inputs with test-design
techniques and justifying each test.

## The brief

Pick **one** feature:

- **The cookie jar:** `/cookies`, `/cookies/set`, `/cookies/delete`;
- **Digest authentication:** `/digest-auth/{qop}/{user}/{password}` and `/digest-auth/{qop}/{user}/{password}/{algorithm}`.

Then:

1. **Explore it** with curl or Swagger UI (`http://localhost:8080/swagger-ui`) for 20 minutes. Write down what it does
   and anything that surprised you.
2. **List the risks:** what could go wrong that a user would notice? Rank the top five.
3. **Choose techniques** from the path: equivalence partitioning and boundary values (lesson 7), negative tests
   (lesson 5), state transitions (sequences of calls), security checks (lesson 15). Name the technique for each test.
4. **Write at least 8 tests** in a new class, for example `course/project/MySuiteTest.java`, extending `CourseTest`.
   The name must end in `Test`, or the build skips it. Run it with
   `./mvnw -pl examples-restassured -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest='MySuiteTest'`.
5. **Review your own suite** with the checklist below, then compare it with the reference solution.

## Review checklist

- ☐ Every test name says what behaviour it checks, as a sentence.
- ☐ Each test checks one behaviour and fails for one reason.
- ☐ Each test's comment or Javadoc names its technique.
- ☐ There are negative tests, not only happy paths.
- ☐ No test depends on another test's leftovers. Each one creates its own state.
- ☐ No copy-paste: shared setup lives in a helper or a specification (lesson 10).
- ☐ You made each test fail once on purpose, to prove it can.
- ☐ The suite runs green three times in a row.

## Reference solution

[`CookieJarSuiteSolution`](../../../examples-restassured/src/test/java/io/qaplayground/course/project/CookieJarSuiteSolution.java)
models the cookie jar as a state machine (empty, holds cookies) and covers its transitions, a boundary, an error guess
and isolation between clients. Yours doesn't have to look the same. Compare the risks you chose, not the code.

## What next

You've finished the REST Assured path. Load testing is a natural next step: the [Gatling path](../gatling/README.md)
builds on the HTTP you now know.
