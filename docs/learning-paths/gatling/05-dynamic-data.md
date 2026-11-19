# Lesson 5: Dynamic data

**Level:** intermediate · **Time:** about 1 hour

## Goal

Give each virtual user its own data with feeders, read it with `#{...}` expressions, and pass values from one
response to the next request.

## Concept

If every virtual user sends the same request, caches answer most of them and the test measures the cache, not the
system. Real users are different people looking at different things.

**The session.** Each virtual user carries a **session**: a small map of its own values. `#{name}` in a URL, header or
body reads `name` from the session when the request is sent.

**Feeders** put values into the session. `feed(...)` takes the next record and adds its fields:

| Feeder | Use when |
|---|---|
| `csv("data/users.csv")` | You have test data in a file (`src/test/resources/data/`) |
| An `Iterator<Map<String, Object>>` | You can generate data in Java (random ids, sequences) |
| `.circular()` / `.random()` / `.queue()` | Wrap around / pick at random / fail when the data runs out (the default) |

**`saveAs`** puts a value from a *response* into the session, for example an id returned by a create call:
`check(jsonPath("$.uuid").saveAs("id"))`, then `#{id}` in the next request. This is how you chain steps such as
"create, then read" under load.

Checks can use session values too: `jsonPath("$.args.name[0]").isEL("#{name}")` (EL = expression language).

## Example

[`L05_DynamicDataSimulation`](../../../examples-gatling/src/test/java/io/qaplayground/course/intermediate/L05_DynamicDataSimulation.java)
with [`data/users.csv`](../../../examples-gatling/src/test/resources/data/users.csv)

```java
scenario("Different users")
        .feed(csv("data/users.csv").circular())
        .exec(http("GET /get as user").get("/get?name=#{name}&team=#{team}")
                .check(status().is(200), jsonPath("$.args.name[0]").isEL("#{name}")))
        .exec(http("GET /uuid").get("/uuid").check(jsonPath("$.uuid").saveAs("id")))
        .exec(http("GET /anything with id").get("/anything?id=#{id}"));
```

## Run it

```bash
./mvnw -pl examples-gatling gatling:test -Dgatling.simulationClass=io.qaplayground.course.intermediate.L05_DynamicDataSimulation
```

Expected: `request count 30`, 0 KO.

## Exercises

### 5.1 Generate the data

**Steps:** write a simulation with a *generated* feeder that gives each user a random `n` from 1 to 100. Request
`/bytes/#{n}` and check that the body is exactly `n` bytes long.

**Done when:** ☐ 0 KO.

<details><summary>Hint</summary><code>Stream.generate(() -&gt; Map.&lt;String, Object&gt;of("n", ThreadLocalRandom.current().nextInt(1, 101))).iterator()</code>; for the length, <code>bodyBytes().transform(bytes -&gt; bytes.length).isEL("#{n}")</code>.</details>

**Solution:** [`S05_DynamicDataSolution`](../../../examples-gatling/src/test/java/io/qaplayground/course/solutions/S05_DynamicDataSolution.java)

### 5.2 Run out of data

**Steps:** in a copy of the lesson, remove `.circular()` and inject 10 users (the CSV has 5 rows). Run it.

**Done when:** ☐ you've seen what Gatling does when a queue feeder runs out, and can say when that's the behaviour you
want.

## Next

[Lesson 6: Workload models](06-workload-models.md)
