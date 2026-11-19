# Status codes and failure simulation

## `/status/{code}`: any status

Returns the status you ask for (100–599). A comma-separated list picks one at random, optionally weighted:
`/status/200:0.9,500:0.1` fails about one request in ten.

```console
$ curl -s -i http://localhost:8080/status/418
HTTP/1.1 418
X-More-Info: http://tools.ietf.org/html/rfc2324
Content-Type: text/plain;charset=utf-8

I'm a teapot!
```

Some codes carry the headers that make them meaningful:

| Code | Extra |
|---|---|
| 300, 308 | `Location: /image/jpeg` and a small HTML body |
| 301, 302, 303, 305, 307 | `Location: /redirect/1` |
| 401 | `WWW-Authenticate: Basic realm="Fake Realm"` |
| 402 | body + `X-More-Info` |
| 406 | JSON body listing acceptable image types |
| 407 | `Proxy-Authenticate` |
| 418 | `I'm a teapot!` |

Invalid input (`/status/600`, `/status/foo`) → `400` with `detail`, e.g.
`invalid status code: 600 not in range [100, 599]`.

**Practise:** how your framework treats each class: does it throw on 4xx? follow 3xx? retry 5xx?
Data-driven tests over many codes. Error-rate assertions in load tests with weighted codes.

**Common mistakes:**
- Testing 3xx with redirects followed: you then assert on the *target's* status. Turn following off.
- Expecting to see `100 Continue`: 1xx are interim responses that clients hide. Here 103 is sent as a real
  interim response; other 1xx end in a plain `200` (see [COMPATIBILITY](../COMPATIBILITY.md)).
- On this server (Tomcat), `400`, `500`, `501` and `503` responses also close the connection. Your load tool
  will open new connections after them.

## `/unstable`: random failures

Returns `200` or `500`. `failure_rate` (default `0.5`) is the probability of `500`; `seed` makes the outcome
reproducible, and identical to go-httpbin for the same seed.

```console
$ curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/unstable?seed=1234567890"
500
$ curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/unstable?seed=1234567890&failure_rate=0.07"
200
```

**Practise:** retry policies (does your client retry? how often? with back-off?), circuit breakers, and
asserting an error *budget* in load tests (`failedRequests().percent().lt(…)`).

**Common mistakes:** asserting `status == 200` on an unseeded `/unstable` (it is meant to fail); forgetting a
seed in a regression test that must be deterministic.
