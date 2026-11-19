# Lesson 4: Realistic users

**Level:** beginner · **Time:** about 45 minutes

## Goal

Model a user journey with several steps, think time between them and a group that reports the journey as a whole.

## Concept

Real users don't fire requests back to back. They read a page, think, then click. A scenario without pauses sends far
more requests per user than reality, so you test a load nobody will ever produce.

- **`pause(1, 3)`**: think time, random between 1 and 3 seconds. Use pauses that match how people really use the
  system. Analytics data is the best source.
- **Several steps**: a journey (browse → add to basket → place order) shows which step is slow, not just "the site".
- **`group("checkout").on(...)`**: reports the whole journey as one extra line, so you can say "checkout takes 4 s at
  p95" as well as listing each request.
- **`rampUsers(10).during(20)`**: users arrive spread over 20 seconds instead of all at once, closer to real traffic.

Think time is excluded from response times but included in a group's duration: the group measures what the *user*
experiences, pauses included.

## Example

[`L04_RealisticUsersSimulation`](../../../examples-gatling/src/test/java/io/qaplayground/course/beginner/L04_RealisticUsersSimulation.java)

```java
scenario("Shopper")
        .exec(group("checkout").on(
                exec(http("browse").get("/get?page=products"))
                        .pause(1, 3)
                        .exec(http("add to basket").post("/post").body(StringBody("{...}")).asJson())
                        .pause(1)
                        .exec(http("place order").get("/status/201").check(status().is(201)))));
```

## Run it

```bash
./mvnw -pl examples-gatling gatling:test -Dgatling.simulationClass=io.qaplayground.course.beginner.L04_RealisticUsersSimulation
```

## Exercises

### 4.1 Find the group

**Steps:** in the report, find the `checkout` group and compare its duration with the sum of its three requests.

**Done when:** ☐ you can explain the difference (it's the think time).

### 4.2 Remove the pauses

**Steps:** in a copy, remove both `pause(...)` calls and run it again. Compare the run's duration and the
requests-per-second chart.

**Done when:** ☐ you can explain why a test without think time overstates the load from the same number of users.

## Next

[Lesson 5: Dynamic data](05-dynamic-data.md) (intermediate)
