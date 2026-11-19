# Content formats and encodings

## `/json`, `/xml`, `/html`, `/encoding/utf8`

Fixed documents: a JSON slideshow, the same as XML (`Content-Type: application/xml`, no charset), an
excerpt of *Moby-Dick* as HTML, and a UTF-8 stress-test page.

```console
$ curl -s http://localhost:8080/json
{
  "slideshow": {
    "author": "Yours Truly",
    "date": "date of publication",
    "slides": [ { "title": "Wake up to WonderWidgets!", "type": "all" }, { "items": [...], "title": "Overview", "type": "all" } ],
    "title": "Sample Slide Show"
  }
}
```

**Practise:** JSONPath/GPath on nested arrays (`slideshow.slides[1].items[0]`), XPath/GPath on XML attributes
(`slideshow.@title`), schema validation, charset handling (`/encoding/utf8` contains `∮ E⋅da = Q`).

## `/gzip`, `/deflate`, `/brotli`

`/gzip` and `/deflate` always compress (whatever `Accept-Encoding` says) and set `Content-Encoding`. The JSON
contains `"gzipped": true` / `"deflated": true`. `/brotli` returns `501 Not Implemented`, as in go-httpbin.

```console
$ curl -s -i http://localhost:8080/gzip | head -4
HTTP/1.1 200
Content-Encoding: gzip
Content-Type: application/json;charset=utf-8
Content-Length: 196
$ curl -s --compressed http://localhost:8080/gzip | grep gzipped
  "gzipped": true
```

**Practise:** checking that your client decompresses; `Content-Length` is the *compressed* size.
**Common mistake:** asserting on the raw body without decompression (you get binary).

## `/base64/{value}`, `/base64/decode/{value}`, `/base64/encode/{value}`

```console
$ curl -s http://localhost:8080/base64/aGVsbG8=
hello
$ curl -s http://localhost:8080/base64/encode/hello
aGVsbG8=
$ curl -s http://localhost:8080/base64/aGVsbG8
{ "status_code": 400, "error": "Bad Request", "detail": "base64 decode failed: illegal base64 data at input byte 4" }
```

Decoding accepts URL-safe (`-_`) and standard (`+/`) alphabets but **requires padding**. `?content-type=…`
sets the response type; HTML-like types are HTML-escaped to prevent XSS.

## `/links/{n}` and `/links/{n}/{offset}`

`/links/5` redirects to `/links/5/0`, an HTML page with links to its sibling pages:

```console
$ curl -s http://localhost:8080/links/3/1
<html><head><title>Links</title></head><body><a href="/links/3/0">0</a> 1 <a href="/links/3/2">2</a> </body></html>
```

**Practise:** crawling, link extraction in load tests (Gatling `css(...)`). `n` must be 0–256.

## `/response-headers?Name=value&…`

Every query parameter becomes a response header; the body lists them.

```console
$ curl -s -i "http://localhost:8080/response-headers?X-Demo=1"
HTTP/1.1 200
X-Demo: 1
Content-Type: application/json;charset=utf-8

{ "Content-Type": ["application/json; charset=utf-8"], "X-Demo": ["1"] }
```

**Practise:** asserting on headers your real API should send (`Cache-Control`, `Strict-Transport-Security`,
`Content-Security-Policy`); duplicate headers (`?X-A=1&X-A=2`).

**Common mistake:** expecting an `ETag` set here to make the endpoint conditional: it does not.
