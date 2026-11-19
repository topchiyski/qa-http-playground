# Compatibility with the reference implementation (go-httpbin)

qa-http-playground implements the httpbin API. Its reference implementation is the open-source Go project
[mccutchen/go-httpbin](https://github.com/mccutchen/go-httpbin) (MIT), which also runs the public instance at
httpbingo.org. This page lists every known difference from that reference.

qa-http-playground is an independent project with its own server code (Java, Spring Boot), test suites, docs and
goals. Its **API** follows the httpbin API that Kenneth Reitz introduced with httpbin.org and that many tools and
tutorials assume. It does not use or call any public httpbin service.

**Reference baseline:** go-httpbin **v2.26.0**, commit `8b1bc1433750404fefd2a419cda3720d3088701b`
(2026-10-15). The source is the specification; where a running instance and the source disagree, the source
wins and the difference is recorded here.

Status values used below:

| Status | Meaning |
|---|---|
| MATCHES | Verified by a REST Assured test written from the reference handler/test. |
| DEVIATION | Intentional difference; reason given. |
| UNVERIFIED | Behaviour could not be fully confirmed; most literal reading of the source implemented. |
| OPEN | Known problem under investigation. |

---

## 1. Global behaviour

| # | Topic | Reference behaviour | Our behaviour | Status |
|---|---|---|---|---|
| G1 | JSON formatting | Go `json.Encoder`: 2-space indent, `"key": value`, HTML not escaped, trailing `\n`, struct fields in declaration order, map keys sorted. | Jackson with a custom pretty printer that emits the same bytes. | MATCHES |
| G2 | Multi-value maps | `args`, `headers`, `form`, `files` are `{"Name": ["v1", "v2"]}`. | Same. | MATCHES |
| G3 | Header name case | Go canonicalises names (`accept-encoding` → `Accept-Encoding`, via `CanonicalMIMEHeaderKey`). Tomcat keeps the case the client sent. | We apply Go's canonicalisation rules before echoing. | MATCHES |
| G4 | `Host` / `Transfer-Encoding` in echoed headers | Added explicitly from the request line. | Same. | MATCHES |
| G5 | Client IP (`origin`) | First of `Fly-Client-IP`, `CF-Connecting-IP`, `Fastly-Client-IP`, `True-Client-IP`, first entry of `X-Forwarded-For`, then the socket address. | Same order. | MATCHES |
| G6 | Request URL (`url`) | Scheme from `X-Forwarded-Proto` / `X-Forwarded-Protocol` / `X-Forwarded-Ssl=on` / TLS; host from `Host`. | Same. | MATCHES |
| G7 | CORS | Every response gets `Access-Control-Allow-Origin` (echoes `Origin`, else `*`) and `Access-Control-Allow-Credentials: true`. **Every `OPTIONS` request on any path returns 200** with `Allow-Methods: GET, POST, HEAD, PUT, DELETE, PATCH, QUERY, OPTIONS`, `Max-Age: 3600` and echoed `Allow-Headers`: before routing, so even `OPTIONS /status/500` is 200. | Servlet filter with the same rules, ordered before Spring MVC. | MATCHES |
| G8 | HEAD | Any GET route also answers HEAD with an empty body. `HEAD /post` → 405, `GET /head` → 405. | Same. | MATCHES |
| G9 | Unknown path | Go mux: `404`, `text/plain; charset=utf-8`, `X-Content-Type-Options: nosniff`, body `404 page not found\n`. | Same body and headers (see G18 for the Content-Type spelling). | MATCHES |
| G10 | Wrong method | Go mux: `405`, sorted `Allow` header (GET implies HEAD), `text/plain`, body `Method Not Allowed\n`. | Same. | MATCHES |
| G11 | Handler errors | JSON `{"status_code": N, "error": "<reason phrase>", "detail": "<message>"}`; `detail` omitted when empty. | Same shape. `status_code` and `error` always match. `detail` matches for messages go-httpbin writes itself; Go stdlib error text embedded in `detail` (e.g. `strconv.ParseFloat: parsing "x": invalid syntax`) is emulated for the common cases. | MATCHES |
| G12 | Max request body | `MaxBytesReader(1 MiB)`; overflow surfaces as `400 error parsing request body: http: request body too large`. | Same limit (`playground.max-body-size`), same error. | MATCHES |
| G13 | Durations | Accepts Go duration syntax (`1.5s`, `300ms`, `1m2s`) **or** float seconds. Error messages print Go `Duration.String()` (`10s`, `1.5s`). | Java port of `time.ParseDuration` and `Duration.String()`. | MATCHES |
| G14 | Seeded randomness | `/bytes`, `/stream-bytes`, `/unstable` use Go `math/rand` seeded with `seed`. | Java port of Go's `math/rand` source (additive lagged Fibonacci, 607 taps) so the **same seed gives the same bytes as go-httpbin**. Generator verified against the reference vectors `/bytes/16?seed=1234567890` and `/unstable?seed=1234567890` (unit test `GoCompatTest`) and at the endpoint level (`BytesAndRangesTest`, `StatusCodesTest`). | MATCHES |
| G15 | Response compression | None. | Spring/Tomcat compression disabled so `/gzip`, `/deflate`, `/range` bytes are exact. | MATCHES |
| G16 | Server headers | Go adds `Date`; Fly adds `server`, `via`. | Tomcat adds `Date`; may add `Connection`/`Keep-Alive`. No `Server` header. | DEVIATION (harmless) |
| G18 | `Content-Type` spelling | Go sends `application/json; charset=utf-8` (space after `;`). | Tomcat always re-serialises the header as `application/json;charset=utf-8`. Same media type and charset (RFC 9110 treats them as equivalent); only a byte-exact string comparison notices. Not configurable in Tomcat 11. Tests should compare media types, not raw strings. | DEVIATION (verified with curl) |
| G19 | Status line reason phrase | `HTTP/1.1 200 OK` | Tomcat 11 sends `HTTP/1.1 200 ` (no reason phrase; RFC 9112 says clients must ignore it). | DEVIATION (verified with curl) |
| G20 | `/error` | Does not exist (404). | Spring's internal error path is overridden to return Go's plain-text 404 when requested directly. | MATCHES |
| G21 | Spring MVC's automatic ETag/Last-Modified handling | None. | Bypassed for all httpbin endpoints (`RawResponseEntityHandler`), otherwise Spring would quote ETags, reformat dates and send its own 304s. | MATCHES |
| G22 | Unencoded `[ ] { } \| ^ \` " < >` in URLs | Go accepts them. | Tomcat rejects them with 400 by default; we relax that (`server.tomcat.relaxed-query-chars` / `relaxed-path-chars`) so `?attr[SameSite]=Strict` works. | MATCHES |
| G23 | Keep-alive after errors | Go keeps the connection open after any status. | Tomcat closes the connection (`Connection: close`) after 400, 408, 411, 413, 414, 500, 501 and 503: hard-coded in Tomcat (`statusDropsConnection`), not configurable. Clients open a new connection after e.g. every `/unstable` 500. Relevant for load tests; see docs/load-testing.md. | DEVIATION |
| G24 | Empty bodies | Go answers a handler that wrote nothing with `Content-Length: 0`. | Same (explicitly set; Tomcat would otherwise use chunked encoding for e.g. an empty 302). | MATCHES |

## 2. Endpoints not in the reference's landing-page list

| Endpoint | Notes | Status |
|---|---|---|
| `/version` | Returns `{"service": "..."}`; extra fields only with `use-full-version`. We return `"service": "qa-http-playground"` plus version fields only when `playground.full-version=true`. | DEVIATION (service name) |
| `/brotli` | Listed upstream but not implemented: `501` JSON error. We mirror that exactly. | MATCHES |
| `/links/{n}/{offset}` | Exists in reference. Implemented. | MATCHES |
| `/base64/{op}/{data}` | Generic form; `op` other than `encode`/`decode` → 400 `invalid operation`. | MATCHES |

## 3. Additional routes we add (not in the reference)

| Route | Why |
|---|---|
| `/swagger-ui`, `/swagger-ui/**`, `/v3/api-docs` | OpenAPI documentation (springdoc). |
| Port 8081: `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus` | Server-side observability for load-testing learners. Separate port, so it never collides with httpbin paths. |

## 4. Per-endpoint notes

| Endpoint | Note | Status |
|---|---|---|
| `/` | Our own landing page (endpoint list + links to Swagger UI and docs), not a copy of go-httpbin's template. Keeps the reference's `Content-Security-Policy` header. | DEVIATION |
| `/hostname` | Returns dummy `go-httpbin` by default, like the reference; `playground.use-real-hostname=true` exposes the real hostname. | MATCHES |
| `/status/{code}` 2xx–5xx | All codes, weighted choices, special headers/bodies and error messages tested. | MATCHES |
| `/status/103` | Go sends an interim `103` then the final `200 OK`. We do the same via Tomcat's `sendEarlyHints()` (verified on the wire). | MATCHES |
| `/status/100`, `102`, `104`–`199` | Go sends the interim 1xx, then a final `200 OK`. Tomcat cannot emit arbitrary interim responses: `setStatus(1xx)` writes a 1xx as if it were final and clients hang waiting (observed through the same local HTTP-filtering proxy that strips trailers; Tomcat offers no API for arbitrary interim responses either way). We send only the final `200 OK` (empty body, `text/plain`), which is what REST Assured, curl without `-v` and browsers show for the reference too. Only raw-socket clients and `curl -v` notice the missing interim line. | DEVIATION |
| `/status/101` | Final status in both Go and Tomcat (`101` with no upgrade). What clients do next is client-specific. | UNVERIFIED |
| `/unstable` | Seeds give the same outcome as the reference (`seed=1234567890` → 500; with `failure_rate=0.07` → 200). An out-of-range `failure_rate` returns the reference's message **verbatim, including its formatting bug**: `invalid failure rate: %!d(<nil>) not in range [0, 1]`. | MATCHES |
| `/redirect/{n}` etc. | The reference has **no upper limit** on `n`. We add `playground.max-redirects`, default `0` = unlimited (reference behaviour); when set, over-limit returns `400 redirect count must be <= N`. Note `/redirect/n` hops via `/relative-redirect/n-1`, as in the reference. | MATCHES (limit is opt-in DEVIATION) |
| `/redirect-to` | Allowed targets: absolute URLs or root-relative paths; `//host` and encoded variants are refused; `playground.allowed-redirect-domains` allowlist; `403 text/plain` when forbidden, with an **empty body unless an allowlist is configured** (as upstream); `status_code` must be 300–399. | MATCHES |
| `/redirect-to` `Location` | Re-serialised like Go's `u.String()` (port of `EscapedPath`/`EscapedFragment`): valid percent-encoding is kept, anything else is escaped. **Security fix found by test design:** we used to send the raw value, so `url=/\evil.com` produced `Location: /\evil.com`, which browsers follow to `evil.com`; Go sends `/%5Cevil.com`. Also ported: Go's error `first path segment in URL cannot contain colon` and fragment escape validation. | MATCHES |
| `/redirect-to` with `allowed-redirect-domains` | **Hardening beyond the reference:** go-httpbin only checks the host when the URL has one, so `///evil.com` and `https:evil.com` pass its allow-list although browsers resolve both to another host. With an allow-list we accept only listed hosts or plain root-relative paths (`/x`, not `//x`). Without an allow-list behaviour is unchanged. | DEVIATION (security) |
| Body echo, malformed JSON | 400 with `detail: error parsing request body: <reason>`. Status and `error` match; `<reason>` is Jackson's wording, not Go's (`invalid character 'f' looking for beginning of value`). | DEVIATION |
| Body echo, form/multipart | Same content-type rules (exact, case-sensitive, before `;`), invalid escapes → `invalid URL escape "%ZZ"`, missing boundary → `no multipart boundary param in Content-Type`. Multipart is parsed by our own small reader. | MATCHES |
| Body echo, JSON numbers | Decoded as float64 like Go: `{"a": 1}` echoes as `1`, `12345678901234567890` as `12345678901234567000`. | MATCHES |
| `HEAD` responses | Spring/Tomcat send the `Content-Length` the GET would have; Go's autohead discards the body before net/http sees it, so it sends none. | UNVERIFIED |
| `/env` | Reads `HTTPBIN_ENV_*` from Spring's `Environment` (OS environment **and** other property sources such as `--HTTPBIN_ENV_X=y` command-line args). `{"env": null}` when empty, as upstream. | DEVIATION (superset) |
| `/dump/request` with chunked body | Go re-chunks the body as it reads it; we emit it as one chunk plus the terminator. | UNVERIFIED |
| `/basic-auth`, `/hidden-basic-auth` | `{"authenticated", "user", "authorized"}` (field order kept); 401 + `WWW-Authenticate: Basic realm="Fake Realm"` vs. 404 JSON error with no challenge. Basic header parsed like Go's `Request.BasicAuth` (case-insensitive scheme, split at first `:`). | MATCHES |
| `/bearer` | Scheme `Bearer` is case-sensitive; exactly two whitespace-separated fields; else 401 + `WWW-Authenticate: Bearer`. All six reference negative cases tested. | MATCHES |
| `/digest-auth/...` | Reference is stateless: it does **not** validate nonce, opaque, realm or nc, and uses the raw request URI. qop `auth` only; MD5 and SHA-256. No `-sess` algorithms, no `userhash`, no `auth-int` challenge. We match this exactly rather than implementing the full RFC 7616 state machine: stricter checks would reject requests go-httpbin accepts. Verified against go-httpbin's browser-captured header and the RFC 2617 / RFC 7616 (SHA-256) example vectors; challenge realm is `go-httpbin` as upstream. | MATCHES reference; DEVIATION from full RFC 7616 (by design) |
| `/stream/{n}` | Clamped to [1, 100], not rejected (cap configurable via `playground.max-stream-lines`). Compact JSON lines with Go's `json.Marshal` HTML escaping, chunked, flushed per line; `text/plain` like Go's sniffing. | MATCHES |
| `/jsonl` | `count` clamped to [1, max-body-size / sample-line-size], `duration` ≥ 1ns, `delay`, `jitter` in [0, 1] (NaN slips through, as upstream), plain-text `Too much time` 400 when duration + delay > max. | MATCHES |
| `/sse` | Exact wire bytes `event: ping\ndata: {"id":N,"timestamp":ms}\n\n`; `count` outside [1, max] is 400 (unlike /jsonl); `Trailer: Server-Timing` declared. Spring's `SseEmitter` writes `event:ping` without the space, so we use `StreamingResponseBody` for byte fidelity. The `Server-Timing` trailer itself is affected by the trailer issue below. | MATCHES |
| `/delay/{d}` | Seconds or Go durations, [0, max-duration], `Server-Timing: initial_delay;dur=...`, echoes like `/anything`. | MATCHES |
| Durations given as huge or infinite seconds (`/delay/1e18`, `/delay/Infinity`, also `duration=` on `/drip`, `/range`, `/jsonl`) | Go computes `time.Duration(n*1000) * time.Millisecond`; the float-to-int64 conversion overflows, which on amd64 wraps to **0**, so the reference waits 0 s. We saturate at the largest duration and answer **400** `duration 2562047h47m16.854775807s longer than 10s`. Found by error guessing: before the fix such input caused a **500** (overflow while formatting the error). `NaN` is 0 in both. Also note: plain seconds are truncated to whole milliseconds in binary floating point, so `1.001` becomes 1000 ms in both implementations. | DEVIATION (deliberate) |
| `/drip` | Validation order and messages (`too much time: 6s+5s > 10s`), initial delay before any header is sent, `Content-Length` (not chunked), `Server-Timing` with four entries, byte-by-byte pacing. | MATCHES |
| **HTTP trailers** (`/trailers`, `/sse`) | Go sends trailer fields after the last chunk. We use the standard Servlet API (`HttpServletResponse.setTrailerFields`) and declare them with `Trailer:`; a debugger confirms Tomcat serialises the fields and writes them to the socket. On the development machine they never reached any client: **and neither did trailers sent by a plain Node.js HTTP server (control test)**: an HTTP-filtering proxy on that machine (AdGuard, which intercepts all local HTTP and also injected scripts into HTML responses) strips them. Implementation believed correct; `TrailersWireTest` (tag `trailers-wire`, excluded from the default build because it fails behind such proxies) checks the raw wire; run it on a clean Linux machine. If you do not see trailers, check for antivirus/ad-blocking/corporate proxies first. | UNVERIFIED (environment) |
| `/websocket/echo` | RFC 6455 frame handling ported from go-httpbin onto the Servlet HTTP Upgrade API: handshake errors as 400 JSON, masking, RSV/opcode/control-frame checks, close-code validation, UTF-8 checks, 1009 for frames/messages over the limits, ping→pong, session ends after `max-duration`. UTF-8 validity is checked on the **whole message**, not per frame (RFC 6455 §5.6: a character may be split across fragments). go-httpbin checks each frame, so it rejects such valid messages with 1007; found by state-transition testing, deliberate fix. **Session deadline:** go-httpbin's socket deadline drops the TCP connection after `max-duration` (clients see 1006). The Servlet API cannot drop an upgraded socket (Tomcat's `WebConnection.close()` only sets flags; the deadline was silently a no-op until an integration test caught it), so we send close `1001 session exceeded max duration of …` and stop echoing. **Deliberate fix:** go-httpbin's `frameResponse` never advances its offset, so echoing a message larger than `max_fragment_size` (sent in several fragments) appears to loop forever upstream; we fragment correctly. Not run against the Autobahn suite. | MATCHES (fragmentation fix DEVIATION, upstream behaviour UNVERIFIED) |
| `/delay`, `/drip`, `/sse`, `/jsonl` client disconnect | Go logs 499. We stop writing when the socket write fails; nothing is logged as 499. | DEVIATION (invisible to clients) |
| `/range/{n}` | Port of Go `http.ServeContent` + `parseRange`: single range → 206, suffix ranges, multiple ranges → `multipart/byteranges` (Go's part layout, 60-hex-char random boundary), unsatisfiable → 416 `invalid range: failed to overlap` with `Content-Range: bytes */N` and the ETag dropped (Go 1.23+ `serveError`), `If-Range` honoured, ranges larger than the resource ignored. `duration` paces the body in 32 KiB reads like the reference. | MATCHES |
| `/etag/{etag}` | Same `ServeContent` port: `If-Match` (strong, lists, `*`) → 412, `If-None-Match` (weak) → 304 for GET/HEAD and 412 otherwise, `If-(Un)Modified-Since` against "now", Range support. 304 drops Content-Type and Last-Modified like Go. | MATCHES |
| `/cache` | Any `If-Modified-Since`/`If-None-Match` → bare 304. `Last-Modified` uses Go's `time.RFC1123` layout in UTC (`... 05:10:00 UTC`, not `GMT`). The `ETag` is unquoted and reproduces an upstream quirk: go-httpbin's `sha1hash` calls `h.Sum(input)`, which appends SHA-1("") to the hex of the input instead of hashing it. | MATCHES |
| `/cache/{n}` | `Cache-Control: public, max-age=N`; any int64 accepted (even negative), as upstream. | MATCHES |
| `/cookies`, `/cookies/set`, `/cookies/delete` | Cookie parsing and `Set-Cookie` serialisation follow Go's `net/http` rules (attribute order, `Max-Age=0` + past `Expires` on delete, quoting of values with space/comma, invalid names dropped, `Secure` by default behind HTTPS). Go emits multiple cookies in random map order; we use query-string order. | MATCHES (order DEVIATION) |
| `/gzip`, `/deflate` | Same JSON (with `"url": ""`, as upstream leaves it empty), same headers, zlib format for deflate. The compressed bytes come from Java's zlib and may differ from Go's compressor; they decompress to the same JSON. | MATCHES (compressed bytes DEVIATION) |
| `/brotli` | 501 JSON error. | MATCHES |
| `/base64/...` | Go-faithful decoder (padding required, `illegal base64 data at input byte N`), URL-safe then standard alphabet; encode is URL-safe with padding; `content-type` query param; dangerous types HTML-escaped. | MATCHES |
| `/links/{n}[/{offset}]` | `n` in [0, 256] (configurable), redirect keeps the query string, exact HTML. | MATCHES |
| `/response-headers` | Query params become headers (names canonicalised by Go's `Header.Add`); body is the JSON of the params, HTML-escaped for dangerous Content-Types. With several `Content-Type` values Go sends several headers; Tomcat keeps only one. | MATCHES (multi Content-Type DEVIATION) |
| `/response-headers` framing headers | `Content-Length` and `Transfer-Encoding` are echoed in the body but **not emitted**. Go honours them (it truncates to the declared length and closes, or chunks). Tomcat kept an app-set `Transfer-Encoding: chunked` next to its own `Content-Length` with an unchunked body, which corrupts keep-alive connections (exploratory session ES-01, F1). | DEVIATION (protocol safety) |
| Non-ASCII header values | Tomcat writes header values as ISO-8859-1 (`é` → byte `E9`); Go writes the UTF-8 bytes (`C3 A9`). Visible with `/response-headers?X-A=é` (ES-01, F3). | DEVIATION |
| `/image`, `/image/{kind}` | Accept substring matching as upstream (`*/*` → 415), byte-identical assets. | MATCHES |
| `/bytes`, `/stream-bytes` | Byte-identical output for the same seed (verified against the reference vector), same validation order and messages; `chunk_size <= 0` sends one chunk. | MATCHES |
| `/dump/request` | Go `httputil.DumpRequest` output (headers sorted, approximation by design). | UNVERIFIED (exact header set) |
| `/image/*` | We ship go-httpbin's image assets under its MIT license (attributed in `LICENSE`/`NOTICE`). | MATCHES |

## 5. Reference options not supported

| go-httpbin option | Reason | Status |
|---|---|---|
| `-prefix` | Use a reverse proxy or `server.servlet.context-path`; prefix-aware `Location` rewriting not ported. | DEVIATION (candidate for later) |
| `-https-cert-file` / `-https-key-file` | Use Spring Boot `server.ssl.*` instead. | DEVIATION |
| `-log-format`, `-log-level` | Use Spring Boot logging properties. | DEVIATION |

Supported settings: [Configuration](configuration.md).
