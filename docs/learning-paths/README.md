# Learning paths

Two guided paths, each ordered from beginner to advanced. Every lesson runs against **your own** playground, so you can
break things freely.

| Path | What you learn | Lessons | Time |
|---|---|---|---|
| [REST Assured](rest-assured/README.md) | Automated API tests in Java, from your first request to security negative tests | 15 + final project | about 16 h |
| [Gatling](gatling/README.md) | Load tests in Java: write simulations, model real load, find limits honestly | 11 + final project | about 11 h |

**Before you start:** read [Start here](../start-here.md) if HTTP or APIs are new to you. Unknown words are in the
[glossary](../glossary.md). You need JDK 21 and an IDE (IntelliJ IDEA Community, or VS Code with the Java
extensions); the Maven wrapper downloads everything else.

**Which path first?** REST Assured. Gatling assumes you can read a request and a response, which REST Assured
lessons 1–5 teach.

## Levels

| Level | You can… |
|---|---|
| **Beginner** | write and run a single test or simulation, and read its result |
| **Intermediate** | build a maintainable suite or a realistic workload |
| **Advanced** | test the hard parts: caching, timeouts, security, limits, honest measurement |

Each path ends with a **final project**: a small piece of real work you design yourself, with a reference solution.

## How a lesson works

Every lesson page has the same parts, in this order:

1. **Goal**: what you can do afterwards.
2. **Concept**: the minimum theory, in a few paragraphs.
3. **Example**: a short, runnable class in the repository, one concept per class.
4. **Run it**: the exact command.
5. **Exercises**: each has *Steps*, an *Expected result*, a *Done when* checklist, a collapsed *Hint* and a link to
   the *Solution*.
6. **Next**: the next lesson.

## Running the code

Commands, exercises and naming rules are in each path's README: [REST Assured](rest-assured/README.md#how-to-run)
and [Gatling](gatling/README.md#how-to-run).
