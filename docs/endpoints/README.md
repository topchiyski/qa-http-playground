# Endpoints

One guide per endpoint group. New to a term? See the [glossary](../glossary.md). Beginners: [Start here](../start-here.md).
You can also try every endpoint in your browser at <http://localhost:8080/swagger-ui>.

| Group | Endpoints | Guide |
|---|---|---|
| Request inspection | `/anything`, `/anything/{path}`, `/get`, `/headers`, `/ip`, `/user-agent`, `/hostname`, `/dump/request`, `/env`, `/uuid` | [inspection](inspection.md) |
| HTTP methods | `/post`, `/put`, `/patch`, `/delete`, `/head`, `/upload`, `/forms/post` | [methods](methods.md) |
| Status codes | `/status/{code}` (incl. weighted `200:0.9,500:0.1`), `/unstable?failure_rate=&seed=` | [status](status.md) |
| Redirects | `/redirect/{n}`, `/absolute-redirect/{n}`, `/relative-redirect/{n}`, `/redirect-to?url=&status_code=` | [redirects](redirects.md) |
| Auth | `/basic-auth/{u}/{p}`, `/hidden-basic-auth/{u}/{p}`, `/bearer`, `/digest-auth/{qop}/{u}/{p}[/{MD5\|SHA-256}]` | [auth](auth.md) |
| Caching | `/cache`, `/cache/{n}`, `/etag/{etag}` | [caching](caching.md) |
| Cookies | `/cookies`, `/cookies/set`, `/cookies/delete` (with `attr[SameSite]` etc.) | [cookies](cookies.md) |
| Content | `/json`, `/xml`, `/html`, `/encoding/utf8`, `/gzip`, `/deflate`, `/brotli`, `/base64/...`, `/links/{n}[/{offset}]`, `/response-headers` | [content](content.md) |
| Images | `/image`, `/image/{png,jpeg,webp,svg,avif}` | [images](images.md) |
| Bytes & ranges | `/bytes/{n}?seed=`, `/stream-bytes/{n}`, `/range/{n}` | [bytes and ranges](bytes-and-ranges.md) |
| Timing & streaming | `/delay/{d}`, `/drip`, `/stream/{n}`, `/jsonl`, `/sse`, `/trailers` | [timing and streaming](timing-and-streaming.md) |
| WebSocket | `ws://…/websocket/echo` | [websocket](websocket.md) |
| Misc | `/`, `/robots.txt`, `/deny`, `/version` | [misc](misc.md) |

The same seed gives the same output as the reference implementation for `/bytes`, `/stream-bytes` and `/unstable`.
The few known deviations are listed in [COMPATIBILITY.md](../COMPATIBILITY.md).

## Behaviour shared by every endpoint

- **CORS:** every response has `Access-Control-Allow-Origin` (your `Origin`, or `*`) and
  `Access-Control-Allow-Credentials: true`. Every `OPTIONS` request, on any path, answers `200` with the
  preflight headers.
- **Unknown path:** `404` with the plain-text body `404 page not found`.
- **Wrong method:** `405` with `Allow`.
- **Errors from endpoints:** JSON `{"status_code": 400, "error": "Bad Request", "detail": "…"}`.

```console
$ curl -s -i -X OPTIONS http://localhost:8080/anything -H "Origin: https://app.example" -H "Access-Control-Request-Headers: X-Token"
HTTP/1.1 200
Access-Control-Allow-Origin: https://app.example
Access-Control-Allow-Credentials: true
Access-Control-Allow-Methods: GET, POST, HEAD, PUT, DELETE, PATCH, QUERY, OPTIONS
Access-Control-Max-Age: 3600
Access-Control-Allow-Headers: X-Token
```
