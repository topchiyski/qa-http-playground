# Product risk register

Risk-based testing decides **how deeply** each area is tested. Every risk gets a likelihood (L) and an impact (I) on a
1–3 scale; their product is the risk level that sets the test depth in [TEST-STRATEGY.md](TEST-STRATEGY.md#4-risk-based-test-depth).

- **Likelihood:** how complex the code is, how much was ported from Go by hand, and how often it changes.
- **Impact:** what happens to a learner or a lab if the playground gets it wrong.

**Status: accepted 2026-11-10** (first assessment by the test lead, approved before implementation). Ratings are agreed before test implementation starts, and the register is
reviewed again whenever an endpoint group is added or a deviation enters [COMPATIBILITY.md](../COMPATIBILITY.md).

| Level | Score | Test depth |
|---|---|---|
| **High** | 6–9 | All applicable black-box techniques, negative/security suite |
| **Medium** | 3–4 | EP + BVA + one structural technique |
| **Low** | 1–2 | EP representatives and the HTTP conformance checklist |

## Register

| ID | Risk (what could go wrong) | Area / code | L | I | Level | Mitigating tests |
|---|---|---|---|---|---|---|
| R1 | **Open redirect:** `/redirect-to` sends a browser to an attacker-chosen host when the operator has restricted `allowed-redirect-domains`, or accepts a scheme-relative `//evil` target | `RedirectController`, `GoUrl` | 2 | 3 | **High (6)** | Decision table `/redirect-to`, security suite, E2E journey 5 |
| R2 | **Reflected XSS:** `/response-headers` or `/base64` serve attacker-controlled `text/html` unescaped | `ContentController`, `ContentSafety` | 2 | 3 | **High (6)** | EP on content types (safe / dangerous / malformed), security suite, E2E rendering check |
| R3 | **Resource exhaustion:** `/delay`, `/drip`, `/sse`, `/range?duration=`, `/bytes`, `/stream-bytes`, WebSocket sessions exceed `max-duration` / `max-body-size`, tying up threads or memory | `GoDuration.parseBounded`, `BytesController`, `StreamingController`, `TimingController`, `BodySizeLimitFilter`, `EchoUpgradeHandler` | 2 | 3 | **High (6)** | BVA on every configured limit with lowered limits, integration tests for config binding, Gatling stress/soak |
| R4 | **Header injection:** CRLF in `/response-headers` or `/cookies/set` values splits the response | `ContentController`, `CookiesController`, Tomcat | 1 | 3 | **Medium (3)** | Error guessing, security suite |
| R5 | **Wrong protocol semantics teach learners wrong HTTP:** HEAD with a body, wrong `Content-Length`, 405 without `Allow`, 204/304 with a body, missing CORS | All controllers, `RawResponseEntityHandler`, `CorsPreflightFilter` | 2 | 3 | **High (6)** | Checklist-based conformance test over every route, integration tests for `RawResponseEntityHandler` |
| R6 | **Range / conditional request errors:** wrong 206/416 handling, multipart ranges, `If-None-Match`/`If-Match` precedence | `ServeContent`, `CachingController` | 3 | 2 | **High (6)** | Classification tree for `Range`, decision table for conditionals |
| R7 | **Digest auth handshake errors:** wrong challenge format, wrong hash for MD5 / SHA-256, `qop` handling | `DigestAuth`, `AuthController` | 2 | 2 | **Medium (4)** | State transition (challenge → response → 200 / 401), unit tests with RFC 7616 vectors |
| R8 | **WebSocket framing errors:** fragmentation, control frames, close codes, masking | `WebSocketFrames`, `EchoUpgradeHandler` | 3 | 2 | **High (6)** | State transition over the connection lifecycle, property-based frame round trip, browser E2E journey 8 |
| R9 | **Compatibility drift:** responses differ from go-httpbin in shape, field names, number formatting or header casing, so tutorials written for the httpbin API break | `GoJson`, `HeaderNames`, `EchoResponses`, all controllers | 3 | 2 | **High (6)** | Expected results derived from the go-httpbin source and its tests; deviations listed in COMPATIBILITY.md |
| R10 | **Go-port errors in deterministic output:** seeded `/bytes`, `/unstable`, weighted `/status` produce different sequences than the reference | `GoRandom`, `StatusController` | 2 | 2 | **Medium (4)** | Known-answer unit tests, property-based determinism |
| R11 | **Parsing errors in Go ports:** durations, numbers, query strings, base64 behave differently on edge input | `GoDuration`, `GoStrconv`, `QueryString`, `GoBase64` | 3 | 1 | **Medium (3)** | Unit EP/BVA, property-based round trips, error guessing |
| R12 | **Streaming correctness:** SSE framing, chunk boundaries, trailers, early termination | `StreamingController`, `Pacing` | 2 | 2 | **Medium (4)** | API tests, raw-socket trailer test (opt-in), browser `EventSource` E2E |
| R13 | **Configuration not applied:** an environment variable such as `PLAYGROUND_MAX_DURATION` is ignored or bound wrongly | `PlaygroundProperties` | 1 | 2 | **Low (2)** | Integration tests with overridden properties |
| R14 | **Onboarding broken:** a junior cannot clone, start and test with only a JDK | Build, README | 1 | 3 | **Medium (3)** | Fresh-clone `./mvnw verify` after build changes |
| R15 | **Swagger UI or index out of sync with real routes** | `OpenApiConfig`, `IndexController` | 2 | 1 | **Low (2)** | Integration contract test (OpenAPI paths = controller routes), E2E journey 1 link crawl |
| R16 | **Accessibility barriers** on the pages learners use | `index.html`, `forms-post.html.tmpl` | 2 | 1 | **Low (2)** | axe-core scan in E2E |

## Accepted risks (not mitigated by tests)

- **Exposure to the internet.** The playground is unsafe on a public network by design. This is mitigated by the
  README warnings, not by tests. The server listens on all interfaces so lab machines can reach
  it; `--server.address=127.0.0.1` keeps it on one machine.
- **Performance on learner hardware.** Gatling simulations describe behaviour on the documented reference machine;
  they don't guarantee throughput elsewhere.
- **Behaviour of third-party UI (Swagger UI) internals.** E2E checks only that it loads our spec and executes a call.
