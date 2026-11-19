# Architecture

Reference: go-httpbin v2.26.0 (`8b1bc14`). Differences are listed in [COMPATIBILITY.md](COMPATIBILITY.md).

## Stack

| Item | Version | Note |
|---|---|---|
| Java | 21 | |
| Spring Boot | 4.1.2 | Spring Framework 7, Jakarta EE 11, Jackson 3. Embedded Tomcat. |
| springdoc-openapi | 3.2.0 (`springdoc-openapi-starter-webmvc-ui`) | Swagger UI and the OpenAPI document |
| REST Assured | 6.3.0 | API tests and the REST Assured course |
| Gatling | 3.16.0, `gatling-maven-plugin` 4.21.12 | Java DSL; the Gatling course |
| Build | Maven wrapper (`./mvnw`), one multi-module reactor | |

## Modules

```text
pom.xml                  parent: versions, plugin management
playground-server/       the server (runnable jar) and every test of it
examples-restassured/    REST Assured course: lessons, exercises, solutions
examples-gatling/        Gatling course: simulations, solutions, final project
docs/                    this documentation
scripts/                 CheckDocLinks.java
```

## Server packages (`io.qaplayground.server`)

| Package | Job |
|---|---|
| `config` | `PlaygroundProperties` (`playground.*` settings), `RawResponseEntityHandler` (writes responses byte for byte), `ClockConfig`, `OpenApiConfig` |
| `web` | One controller per endpoint group, plus `GoMuxErrorHandler` and `PlainErrorController` (Go's 404/405 and error responses) |
| `web.filter` | `CorsPreflightFilter` and `BodySizeLimitFilter`, mirroring go-httpbin's `preflight` and `limitRequestSize` middleware |
| `support` | Plain classes shared by the controllers: `GoJson`, `GoDuration`, `GoRandom`, `GoStrconv`, `GoUrl`, `GoBase64` (ports of Go behaviour), `RequestInfo`, `RequestBodyParser`, `EchoResponses`, `HttpbinResponses`, `ServeContent` (Go's conditional and range rules), `CookieCodec` |
| `auth` | `DigestAuth`: challenge and verification, no Spring Security |
| `websocket` | `WebSocketFrames` (frame codec ported from go-httpbin) and `EchoUpgradeHandler` (Servlet HTTP Upgrade) |

`ArchitectureTest` enforces the layering: `web` sits on top; `support`, `auth` and `websocket` never depend on it.

## Endpoint → controller map

| Controller (`web/`) | Endpoints |
|---|---|
| `IndexController` | `/`, `/robots.txt`, `/deny`, `/version` |
| `InspectionController` | `/anything`, `/anything/**`, `/get`, `/headers`, `/ip`, `/user-agent`, `/hostname`, `/dump/request`, `/env`, `/uuid` |
| `MethodsController` | `/delete`, `/head`, `/patch`, `/post`, `/put`, `/upload` (POST/PUT/PATCH), `/forms/post` (GET) |
| `StatusController` | `/status/{code}` (incl. weighted), `/unstable` |
| `RedirectController` | `/redirect/{n}`, `/absolute-redirect/{n}`, `/relative-redirect/{n}`, `/redirect-to` |
| `AuthController` | `/basic-auth/{user}/{password}`, `/hidden-basic-auth/{user}/{password}`, `/bearer`, `/digest-auth/{qop}/{user}/{password}[/{algorithm}]` |
| `CookiesController` | `/cookies`, `/cookies/set`, `/cookies/delete` (with `attr[Name]`) |
| `CachingController` | `/cache`, `/cache/{seconds}`, `/etag/{etag}` |
| `CompressionController` | `/gzip`, `/deflate`, `/brotli` (501) |
| `ContentController` | `/base64/{data}`, `/base64/{operation}/{data}`, `/encoding/utf8`, `/html`, `/json`, `/xml`, `/links/{n}[/{offset}]`, `/response-headers` |
| `ImageController` | `/image` (chosen by `Accept`), `/image/{kind}` |
| `BytesController` | `/bytes/{n}`, `/stream-bytes/{n}`, `/range/{n}` |
| `TimingController` | `/delay/{duration}`, `/drip` |
| `StreamingController` | `/stream/{n}`, `/jsonl`, `/sse`, `/trailers` |
| `WebSocketController` | `/websocket/echo` |

## Runtime settings

- `spring.threads.virtual.enabled=true`; streaming uses `StreamingResponseBody` on virtual threads;
  `spring.mvc.async.request-timeout` (30 s) is longer than `playground.max-duration`.
- `server.compression.enabled=false`, so `/gzip`, `/deflate` and `/range` send exact bytes.
- `server.address=0.0.0.0`, port 8080.
- Actuator on `management.server.port=8081`: health, info, metrics, prometheus. Per-endpoint timers come from
  `http.server.requests` (tagged by URI template), with histograms turned on for the Prometheus endpoint.
- No database, no outbound calls; Swagger UI is served locally.
