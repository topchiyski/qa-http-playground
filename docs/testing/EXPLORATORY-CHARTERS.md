# Exploratory testing

Scripted tests check what we thought of; exploration finds what we didn't. Sessions are time-boxed
(60–90 minutes), follow a written charter, and end with a session sheet. Every finding becomes a
scripted regression test tagged `@ExploratoryFinding` (`tech:exploratory`) that names its session.

Charter template (from *Explore It!*, E. Hendrickson): **Explore** *target* **with** *resources* **to
discover** *information*.

Heuristics used: **SFDPOT** (Structure, Function, Data, Platform, Operations, Time), **Goldilocks**
(too small, too big, just right), **CRUD** on state (cookies), and **Follow the data** (where does a
value I send end up?).

## Charters

| ID | Charter | Risk | Status |
|---|---|---|---|
| EC-01 | Explore **header-producing endpoints** (`/response-headers`, `/cookies/set`) **with** unusual names, values and repetitions via raw HTTP **to discover** how header serialisation and canonicalisation behave | R4, R5 | Done: ES-01 |
| EC-02 | Explore **streaming endpoints** (`/drip`, `/sse`, `/jsonl`, `/stream-bytes`) **with** clients that disconnect, read slowly or pipeline **to discover** resource leaks and timing drift | R3, R12 | Open |
| EC-03 | Explore **redirect handling** **with** real browsers (Chrome, Firefox) and hostile URL shapes **to discover** where a browser ends up compared with what the server intended | R1 | Partly automated in E2E |
| EC-04 | Explore **the WebSocket echo** **with** a browser console and a raw socket **to discover** behaviour under interleaved control frames, slow reads and abrupt closes | R8 | Open |
| EC-05 | Explore **the learner experience** **with** the README, Swagger UI and a fresh clone on Windows, macOS and Linux **to discover** where a junior gets stuck | R14 | Open |
| EC-06 | Explore **conditional and range requests** **with** curl, a browser cache and a download manager **to discover** mismatches between clients' and our idea of freshness | R6 | Open |
| EC-07 | Explore **configuration** **with** environment variables, odd units (`1h`, `1.5MB`, negative values) and missing values **to discover** silent misconfiguration | R13 | Open |

---

## Session sheet ES-01

| | |
|---|---|
| Charter | EC-01: header-producing endpoints |
| Date | 2026-11-10 |
| Duration | 60 min (setup 10, testing 35, investigation 15) |
| Tester | Test lead |
| Environment | Fresh `playground-server` jar on `127.0.0.1:18080`, Windows 11, curl 8.23, a raw Java socket client. **Note:** AdGuard on this machine proxies local HTTP; wire-level results were cross-checked against the Tomcat and go-httpbin sources. |
| Split | 60 % testing, 25 % bug investigation, 15 % setup |

### Notes (what I tried)

- `/response-headers` with duplicates (`X-A=1&X-A=2`): two header lines. OK.
- Lower case and underscores (`x-lower`, `X_Under`): canonicalised to `X-Lower` and `X_under`, the same as Go's `CanonicalMIMEHeaderKey`. OK.
- Empty value (`X-Empty=`): header with an empty value. OK.
- **Framing headers** (`Content-Length=5`, `Transfer-Encoding=chunked`): see F1.
- Hop-by-hop and "reserved" headers (`Location`, `Set-Cookie`, `Server`, `Date`): emitted as given (by design, as upstream).
- Non-ASCII value (`X-Uni=é`): see F3.
- `/cookies/set` with names containing spaces: dropped. Values with `;`, `"` or `€`: sanitised as by Go's `sanitizeCookieValue`.
- Cookie attributes: an attempt to inject `; Domain=evil.com` through `attr[Path]` was neutralised (the `;` is stripped, as Go's `sanitizeCookiePath` does). `attr[Domain]` is honoured, by design.
- `attr[Max-Age]=-1`: ignored. *Hypothesis:* deviation from Go, whose `Cookie.String()` writes `Max-Age=0`. **Refuted** by reading the reference: go-httpbin's `parseCookieAttrs` supports only Domain, HttpOnly, Path, SameSite and Secure.

### Findings

| # | Finding | Type | Evidence | Outcome |
|---|---|---|---|---|
| F1 | `/response-headers?Transfer-Encoding=chunked` sends **both** `Transfer-Encoding: chunked` and `Content-Length`, with an unchunked body. Clients must prefer Transfer-Encoding (RFC 9112 §6.3), so they mis-read the body, and a keep-alive connection is corrupted for the next request. Locally, AdGuard answered with its own 502. | **Defect** (R4/R5) | Tomcat `Http11Processor.prepareResponse` keeps app-set headers next to its own `Content-Length` (source read). Go would chunk instead. | Fixed: framing headers are echoed in the body but never emitted. Regression: `ExploratoryFindingsTest.noConflictingFraming`, `contentLengthIsTheServers`. COMPATIBILITY.md updated. |
| F2 | `attr[SameSite]=None` without `Secure` is sent as asked; browsers reject such cookies over http, so the learner's cookie "disappears". | Learner trap (matches reference) | Set-Cookie output | Pinned by `sameSiteNoneIsNotSilentlyMadeSecure`; to be demonstrated in the E2E cookie journey; noted in the cookies guide. |
| F3 | Non-ASCII header values are sent as ISO-8859-1 bytes by Tomcat (`é` → `0xE9`); Go writes the UTF-8 bytes. | Deviation | curl shows `�` | Documented in COMPATIBILITY.md. |

### Open questions (carried over)

| # | Question | Next step |
|---|---|---|
| Q1 | With the JDK `HttpClient`'s **default** settings (HTTP/2, i.e. an `h2c` upgrade attempt on cleartext), a POST larger than `max-body-size` waited 20 s for a response; with HTTP/1.1 it got the 400 at once. Is it the JDK client, Tomcat's handling of the upgrade request with an unread body, or the local proxy? | **Strong evidence it is the client + local proxy, not the playground:** Selenium also uses the JDK client, and its new-session POST to ChromeDriver (not our server at all) hung for 180 s in exactly the same way; ChromeDriver's log shows the request never arrived. Pinning HTTP/1.1 fixed both. Still to confirm on a clean Linux machine (no proxy). Integration tests and Selenium pin HTTP/1.1, with comments. |
| Q2 | WebDriver BiDi `network.continueWithAuth` never completed with ChromeDriver 157 (the `authRequired` event arrives; answering it, even off the event thread, hangs). Driver bug or local proxy (BiDi runs over a WebSocket)? | Retry on a clean machine without a proxy. The basic-auth journey uses URL credentials (Chromium) meanwhile. |

### Debrief

- Most header behaviour matches the reference. The interesting failures were at the boundary between "the app sets a header" and "the container owns that header" (framing, encoding). A checklist item now covers this boundary: item 2 of the conformance checklist (Content-Length accuracy) runs against every route.
- Reading the reference source refuted one hypothesis in two minutes. Keep the go-httpbin source open during sessions.

---

## Session sheet ES-02

| | |
|---|---|
| Charter | EC-05 (learner experience), partly: walk through the new learning tracks with curl, checking every command and expected result the docs promise |
| Date | 2026-11-10 |
| Duration | 30 min |
| Environment | Fresh jar on `127.0.0.1:18080`, curl, Windows 11 |

### Findings

| # | Finding | Type | Outcome |
|---|---|---|---|
| F1 | `curl -X PATCH /patch -d "x=1"` echoed `"data": ""` and `"form": {}`; the same for PUT and DELETE. POST was fine. Spring's `FormContentFilter` parses form bodies of PUT/PATCH/DELETE into request parameters and consumes the stream before the endpoint reads it. | **Defect** | Fixed (`spring.mvc.formcontent.filter.enabled: false`). Regression: `ExploratoryFindingsTest.formBodiesSurviveEveryMethod`. |
| F2 | Midway, `/patch` answered **500** (`ClassNotFoundException`) and `/image/png` **404**. | Not a defect | The server was running from a jar that a rebuild had just replaced. Lesson for the docs: run a copy, not `target/`, when rebuilding at the same time. |

### Debrief

The body partitions in TEST-DESIGN.md §1.2 (JSON, form, multipart, …) had only ever been combined with POST. "Method"
is a second dimension, and the defect lived in the untested combinations, which is a classic case for
pairwise or classification-tree design. Writing learning material that tells a beginner "you will see X" turned out
to be an effective oracle: every promise in the docs is now a test.
