# Writing style

Follow these rules when you write or change any page in this repository. They keep the docs short, accurate and
easy to follow.

## The rules

1. **One idea per paragraph.** Paragraphs have 1 to 4 sentences.
2. **Short sentences.** Aim for under 20 words. Use active voice and present tense.
3. **Lead with the point.** The first sentence of every section says what the reader gets or does.
4. **Concrete before abstract.** Show a command, example or scenario first, then name the principle.
5. **Plain words.** Write "use", not "utilize"; "start", not "initiate". Define a technical term in one sentence the
   first time it appears, or link to the [glossary](glossary.md).
6. **Second person for instructions.** "Run the tests", not "The tests should be run".
7. **At most one takeaway line per page,** and only where it captures the idea. No slogans.
8. **No filler.** Cut "In this section we will", "It is important to note that", "simply", "just", "obviously",
   "basically", and marketing adjectives such as "powerful" or "seamless".
9. **The right structure.** Numbered steps for procedures, bullets for parallel items, tables for comparisons. Nest
   bullets at most one level deep.
10. **Runnable code blocks.** Every command can be copied and run as shown. Say where to run it (directory, shell)
    when that is not obvious. Commands run from the repository root unless the page says otherwise.
11. **No em dashes.** Use a colon, a comma, parentheses or a new sentence.

## Examples

**Rule 1: one idea per paragraph.**

> Bad: The playground echoes requests, which makes it ideal for learning, and it also has Actuator metrics on port
> 8081 that you can use during load tests, and Swagger UI lets you try every endpoint in the browser.

> Good: The playground echoes every request, so you can see exactly what your tool sent.
>
> Swagger UI at <http://localhost:8080/swagger-ui> lets you try each endpoint in the browser.

**Rule 3: lead with the point.**

> Bad: Tomcat handles some errors differently from Go. Because of a hard-coded rule, after a 500 it closes the
> connection, so load tools must reconnect.

> Good: After a `500`, the server closes the connection, so your load tool opens a new one. Tomcat hard-codes this.

**Rule 4: concrete before abstract.**

> Bad: Query parameters are multi-valued, so the echo represents them as lists. Try `/get?colour=red`.

> Good: Send `curl "http://localhost:8080/get?colour=red"`. The answer contains `"colour": ["red"]`, a list,
> because a query parameter can appear more than once.

**Rule 8: no filler.**

> Bad: It is important to note that you simply need to run `./mvnw verify`.

> Good: Run `./mvnw verify`.

## Facts

The code, configuration and tests are the source of truth. When a page disagrees with them, the page is wrong.
Copy values (defaults, messages, counts) from the code or from a real run, never from memory.

Every page appears in [docs/index.md](index.md). Check links with `java scripts/CheckDocLinks.java`.
