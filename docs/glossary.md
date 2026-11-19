# Glossary

Short definitions of the terms used in these docs, with a playground endpoint to try where one exists.
Alphabetical within each section.

## HTTP and APIs

| Term | Meaning | Try |
|---|---|---|
| **API** | An interface one program uses to talk to another. Here: HTTP requests in, JSON responses out. | `/get` |
| **Authentication** | Proving who you are (password, token). 401 means "who are you?". | `/basic-auth/user/passwd` |
| **Authorization** | Deciding what you may do. 403 means "I know you, but no". | `/status/403` |
| **Basic auth** | Username and password sent in the `Authorization` header, Base64-encoded (not encrypted!). | `/basic-auth/user/passwd` |
| **Bearer token** | A token sent as `Authorization: Bearer <token>`; whoever holds it is let in. | `/bearer` |
| **Body** | The data part of a request or response, after the headers. | `/post` |
| **Cache** | A stored copy of a response, reused to save time. Controlled by `Cache-Control`, `ETag`, `Last-Modified`. | `/cache/60` |
| **Chunked** | A response sent in pieces as it is produced, when its total size is not known in advance (`Transfer-Encoding: chunked`). | `/stream/5` |
| **Client** | The program that sends the request: a browser, curl, your test. | none |
| **Conditional request** | "Send it only if it changed": `If-None-Match` with an ETag, or `If-Modified-Since` with a date. Unchanged → `304`. | `/etag/abc` |
| **Content-Type** | Header naming the body's format, e.g. `application/json`. Wrong or missing → the server may not understand the body. | `/post` |
| **Cookie** | A small value the server asks the client to store (`Set-Cookie`) and send back on later requests (`Cookie`). | `/cookies/set?a=1` |
| **CORS** | Browser rules for which websites may call an API from JavaScript. The server answers with `Access-Control-Allow-*` headers. | any endpoint |
| **CORS preflight** | An automatic `OPTIONS` request a browser sends before some cross-site calls, to ask permission. | `OPTIONS /get` |
| **curl** | A command-line HTTP client. `curl -i URL` shows status, headers and body. | none |
| **Digest auth** | Login where the password never travels: client and server exchange a challenge and a hash. | `/digest-auth/auth/user/passwd` |
| **Endpoint** | One address of an API, like `/get` or `/status/{code}`. | none |
| **ETag** | A version label for a response (`ETag: "abc"`). Send it back in `If-None-Match` to ask "has it changed?". | `/etag/abc` |
| **Header** | A `Name: value` line in a request or response carrying information about the message. Names are case-insensitive. | `/headers` |
| **HTTP** | The protocol of the web: request (method, URL, headers, body) → response (status, headers, body). | none |
| **Idempotent** | Doing it twice has the same effect as once. `GET`, `PUT`, `DELETE` are; `POST` usually isn't. Matters for retries. | `/put` |
| **JSON** | Text format for data: objects `{}`, lists `[]`, strings, numbers, `true`/`false`, `null`. | `/json` |
| **JSON path / GPath** | A way to point into JSON: `args.colour[0]`. Each tool has its own dialect. | `/get?colour=red` |
| **Method (verb)** | What the request does: `GET` read, `POST` create, `PUT` replace, `PATCH` change, `DELETE` remove, `HEAD` headers only, `OPTIONS` ask. | `/anything` |
| **Path parameter** | A variable part of the path, like `418` in `/status/418`. | `/status/418` |
| **Query string / query parameter** | `?a=1&b=2` after the path: named extra values. | `/get?a=1` |
| **Range request** | Asking for part of a resource: `Range: bytes=0-99` → `206 Partial Content`. Used for resumable downloads. | `/range/100` |
| **Redirect** | A `3xx` response with a `Location` header telling the client to go elsewhere. | `/redirect/2` |
| **Server** | The program that answers requests; here, the playground. | none |
| **SSE (Server-Sent Events)** | A response that stays open and pushes events to the client (`text/event-stream`). | `/sse` |
| **Status code** | The three-digit result of a request: 2xx success, 3xx redirect, 4xx client error, 5xx server error. | `/status/{code}` |
| **Trailer** | A header sent *after* a chunked body, for values known only at the end (e.g. a checksum). | `/trailers` |
| **WebSocket** | A two-way connection that starts as HTTP and then lets both sides send messages at any time. | `ws://localhost:8080/websocket/echo` |

## Testing

| Term | Meaning |
|---|---|
| **Assertion** | One expected fact a test checks automatically: "status is 200". |
| **Boundary value analysis (BVA)** | Test values at and around a limit (limit − 1, limit, limit + 1): bugs gather at edges. See [TEST-DESIGN.md](testing/TEST-DESIGN.md#2-boundary-value-analysis-422). |
| **Data-driven / parameterised test** | One test run with many input rows. |
| **Decision table** | A table of condition combinations and the expected outcome of each; every column becomes a test. |
| **Equivalence partitioning (EP)** | Split inputs into groups the system treats alike; test one value per group. |
| **Error guessing** | Choosing inputs from experience of what usually breaks: empty, huge, special characters, wrong types. |
| **Exploratory testing** | Learning, designing and testing at the same time, guided by a written charter and time-boxed. |
| **Flaky test** | A test that sometimes passes and sometimes fails without any change. Usually caused by timing or shared state. |
| **Fixture** | Reusable test setup, e.g. JUnit's `@BeforeAll` setup or REST Assured's shared `RequestSpecification`. |
| **Mock / stub** | A fake replacement for a real dependency. The playground is often used *as* a stub for third-party APIs. |
| **Negative test** | A test that sends something wrong on purpose and checks the error is handled correctly. |
| **Page object** | In UI automation, a class that represents one page and hides its locators from the tests. |
| **Regression test** | A test that keeps a fixed bug fixed. |
| **Smoke test** | A few quick checks that the system works at all, before deeper testing. |
| **State transition testing** | Model a system as states and events (logged out → logged in) and test each transition. |
| **Test oracle** | Whatever tells you the expected result: a specification, a reference system, a rule. |

## Performance

| Term | Meaning |
|---|---|
| **Closed vs. open workload** | Closed: a fixed number of users, each waits for a response before the next request. Open: requests arrive at a fixed rate regardless of response time. |
| **Latency / response time** | How long one request takes. |
| **Load / stress / soak test** | Expected load / beyond capacity to find the breaking point / normal load for hours to find leaks. |
| **Percentile (p95, p99)** | p95 = 95 % of requests were faster than this. Better than averages, which hide slow outliers. |
| **Ramp-up** | Increasing load gradually instead of all at once. |
| **Throughput** | Requests handled per second. |
| **Think time** | Pauses between a virtual user's requests, imitating a human. |
| **Threshold / assertion (load)** | A pass/fail rule for a load test, e.g. "p95 < 500 ms and error rate < 1 %". |
