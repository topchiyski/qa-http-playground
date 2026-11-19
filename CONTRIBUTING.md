# Contributing

Thanks for helping! This project has two goals that guide every change:

1. **Stay compatible with the httpbin API.** The reference is the
   [go-httpbin](https://github.com/mccutchen/go-httpbin) source (handlers and their tests), currently
   v2.26.0. Any difference must be listed in [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).
2. **Be readable by learners.** Plain code over clever abstractions; one controller per endpoint group;
   Javadoc on every endpoint explaining the HTTP concept it demonstrates.

## Build and test

Commands: [Run the server](docs/how-to/run-the-server.md) and [Run the tests](docs/how-to/run-the-tests.md).

Java 21+ is the only requirement; the Maven wrapper downloads Maven. Keep it that way: the clone-start-test path
for a newcomer must not need anything but a JDK.

## Testing a change

Read [docs/testing/TEST-STRATEGY.md](docs/testing/TEST-STRATEGY.md) once. In short:

- Write the failing test first, at the lowest level that can see the behaviour (Go port → unit; wiring or
  configuration → `*IT`; endpoint behaviour → API; something only a browser does → E2E).
- Say how you derived it: annotate it with its technique (`@BoundaryValue`, `@DecisionTable`, …). Untagged tests are
  fine for tutorials; derived tests are tagged.
- A new route must be added to `playground-server/src/test/java/io/qaplayground/testing/Endpoints.java`; `RoutesContractIT` fails until it is, and the
  checklist then covers it automatically.
- A deliberate difference from go-httpbin goes into `docs/COMPATIBILITY.md` in the same change.

## Adding or changing an endpoint

1. Read the go-httpbin handler **and** its test for the endpoint. Note status codes, field names and order,
   headers, limits and the exact error messages.
2. **Write the REST Assured test first**, in the topic class under `playground-server/src/test/java/io/qaplayground/api`, from the reference
   behaviour: at least one happy path and one edge/error case. Comment it like a tutorial.
3. Implement it in the matching controller under `playground-server/.../web`. Return
   `ResponseEntity<byte[]>` built with `HttpbinResponses` so the bytes on the wire are exactly what you wrote.
   Use the `Go*` helpers for anything whose format must match Go (JSON, durations, numbers, base64).
4. Add `@Operation` and a Javadoc with an "HTTP concept" paragraph.
5. Document it in `docs/endpoints/<group>.md` (purpose, curl example, what to practise, common mistakes).
6. Record any deviation in `docs/COMPATIBILITY.md`. If you could not verify something, mark it
   **UNVERIFIED** rather than guessing.

## Ground rules

- Do not script traffic against public httpbin instances. A single manual request to confirm a response shape is
  fine.
- No outbound network calls, databases or telemetry in the server.
- Keep abuse-relevant limits configurable in `application.yml`, defaulting to go-httpbin's values.
- Commit messages: imperative mood, explain *why*.

## Licensing

Contributions are accepted under the MIT License. Code or assets derived from go-httpbin or Go must keep
their attribution in [NOTICE](NOTICE).
