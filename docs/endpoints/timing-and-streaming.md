# Timing and streaming

Durations accept seconds (`1.5`) or Go-style durations (`1500ms`, `1m30s`). The maximum is
`playground.max-duration` (10 s by default).

## `/delay/{duration}`

Waits, then echoes the request like `/anything` (bodies included). Adds
`Server-Timing: initial_delay;dur=…`.

```console
$ curl -s -o /dev/null -w "%{http_code} after %{time_total}s\n" http://localhost:8080/delay/1
200 after 1.01s
$ curl -s http://localhost:8080/delay/abc
{ "status_code": 400, "error": "Bad Request", "detail": "invalid duration: strconv.ParseFloat: parsing \"abc\": invalid syntax" }
```

**Practise:** client timeouts (set a read timeout below the delay); latency assertions; in load tests,
"a slow dependency". Thanks to virtual threads, hundreds of concurrent `/delay` requests do not tie up the
server.

## `/drip?numbytes=&duration=&delay=&code=`

After `delay` (default 2s), sends `numbytes` (default 10) asterisks spread over `duration` (default 2s), with
status `code`. Uses `Content-Length`, not chunking: it imitates a *slow network*, not a streaming API.

```console
$ curl -s "http://localhost:8080/drip?numbytes=5&duration=1&delay=0.5&code=201" -w "  [%{http_code} in %{time_total}s]\n"
*****  [201 in 1.53s]
```

`duration + delay` above the maximum → `400 too much time: 6s+5s > 10s`.

**Practise:** time-to-first-byte vs. total time; idle/read timeouts that fire mid-body.

## `/stream/{n}`

`n` JSON objects (max 100), one per line, each flushed as its own chunk.

**Practise:** reading a body line by line; `Transfer-Encoding: chunked`.

## `/jsonl?count=&duration=&delay=&jitter=`

JSON Lines (`application/x-ndjson`): `count` lines spread over `duration` after `delay`; `jitter` (0–1)
randomises the gaps by ±that fraction.

**Practise:** streaming APIs such as log tails or LLM token streams.

## `/sse?count=&duration=&delay=&jitter=`

Server-Sent Events (`text/event-stream`), default 10 events over 5 s:

```console
$ curl -s -N "http://localhost:8080/sse?count=2&duration=100ms"
event: ping
data: {"id":0,"timestamp":1794289562812}

event: ping
data: {"id":1,"timestamp":1794289562914}
```

**Practise:** the browser `EventSource` API; SSE support in Gatling; parsing events.

## `/trailers?Name=value&…`

Echoes the request, declares each query parameter in a `Trailer:` header and sends it as an HTTP **trailer**
after the body. Names that may not be trailers (`Content-Length`, `Authorization`, …) → `400`.

> ⚠️ Proxies, antivirus and ad blockers that intercept HTTP (even on localhost) often strip trailers.
> On the development machine an HTTP-filtering tool removed them, from this server and from a plain Node.js
> server alike. Use `curl --raw` and look after the final `0` chunk; if they are missing, suspect the
> network path first. See [COMPATIBILITY](../COMPATIBILITY.md#4-per-endpoint-notes).

**Practise:** most HTTP clients hide trailers: find out whether yours exposes them.

**Client trap:** Java's built-in `java.net.http.HttpClient` cannot read a response that carries trailers at all: it
fails with `java.net.ProtocolException: CR expected`. That affects `/trailers` and `/sse` (which sends a
`Server-Timing` trailer, like go-httpbin). Use `HttpURLConnection`, Apache HttpClient, REST Assured, curl or a
browser instead. This project's own tests ran into it the first time they ran on a machine where trailers were not
stripped.
