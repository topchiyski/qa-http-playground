# Run the tests

Run these commands from the repository root. On Windows, type `.\mvnw` in PowerShell or `mvnw` in `cmd.exe`.

## Run everything

```bash
./mvnw verify
```

This runs the unit, integration, API and browser (E2E) tests. The tests start their own copy of the playground, so a
copy you started yourself can keep running. The first run downloads Maven and the dependencies and takes a few
minutes.

## Run one part

| To run… | Use |
|---|---|
| Only the server's unit tests | `./mvnw -pl playground-server test-compile surefire:test@default-test` |
| Only the tests of one technique, e.g. boundary values | `./mvnw -pl playground-server verify -Dgroups=tech:bva` |
| Everything except the browser tests | `./mvnw verify -DskipE2E` |
| The browser tests in Firefox, with a visible window | `./mvnw -pl playground-server test-compile surefire:test@e2e -Dbrowser=firefox -Dheadless=false` |
| The link check over all Markdown files | `java scripts/CheckDocLinks.java` |

After adding a route or a technique tag, regenerate the traceability matrix with
`./mvnw verify -Dtraceability.write=true`.

## Run the API tests against your own server

Start the playground, then point the API tests at it (or at any other copy):

```bash
./mvnw -pl playground-server test-compile surefire:test@api -Dplayground.baseUri=http://localhost:8080
```

## When results look wrong on your machine

Check for software that intercepts HTTP before you blame the server. Antivirus, ad blockers and corporate proxies can
filter even `localhost` traffic: they strip HTTP trailers and change responses. A clean Linux machine is the
authority for wire-level checks such as trailers.

If no browser can start, the build skips the browser tests instead of failing them. The build log says so.
