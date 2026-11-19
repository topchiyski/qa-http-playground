# Caching and conditional requests

## `/cache`

Returns 304 Not Modified if the request has **any** `If-Modified-Since` or `If-None-Match` header;
otherwise the `/get` response with fresh `Last-Modified` and `ETag`.

```console
$ curl -s -i http://localhost:8080/cache | grep -iE "etag|last-modified"
Last-Modified: Tue, 10 Nov 2026 05:10:00 UTC
ETag: 5475652c2031302…da39a3ee5e6b4b0d3255bfef95601890afd80709

$ curl -s -o /dev/null -w "%{http_code}\n" -H 'If-None-Match: "anything"' http://localhost:8080/cache
304
```

(The odd-looking ETag and the `UTC` suffix match go-httpbin exactly; see COMPATIBILITY.)

## `/cache/{seconds}`

The `/get` response with `Cache-Control: public, max-age=<seconds>`.

**Practise:** reading `Cache-Control`; observing whether your HTTP client caches (most API test tools do not).

## `/etag/{etag}`

A resource whose entity tag is `"<etag>"`. Implements the full conditional-request rules:

| Request header | Matches `"v1"`? | Result |
|---|---|---|
| `If-None-Match: "v1"` (or `W/"v1"`, `*`, a list containing it) | yes | **304** (GET/HEAD), **412** for other methods |
| `If-None-Match: "other"` | no | 200 |
| `If-Match: "v1"` | yes (strong comparison) | 200 |
| `If-Match: "stale"` or `W/"v1"` | no | **412 Precondition Failed** |

```console
$ curl -s -i -H 'If-None-Match: "v1"' http://localhost:8080/etag/v1
HTTP/1.1 304
ETag: "v1"
```

Range requests work here too (see [bytes and ranges](bytes-and-ranges.md)).

**Practise:** the "GET → remember ETag → conditional GET → 304" flow; optimistic locking
(`PUT` with `If-Match`); strong vs. weak comparison.

**Common mistakes:** sending the ETag without quotes (`If-None-Match: v1` does not match); expecting a body on
304; using `If-Match` with a weak tag.
