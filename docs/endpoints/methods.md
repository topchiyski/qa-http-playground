# HTTP methods

One endpoint per method. Each accepts **only** its own method; anything else is `405 Method Not Allowed`
with an `Allow` header: the cheapest negative test there is.

| Endpoint | Method | Body echoed? |
|---|---|---|
| `/post` | POST | yes (`data`, `form`, `files`, `json`) |
| `/put` | PUT | yes |
| `/patch` | PATCH | yes |
| `/delete` | DELETE | yes (bodies on DELETE are legal but unusual) |
| `/head` | HEAD | no body in the response |
| `/upload` | POST, PUT, PATCH | discarded; only `bytes_received` reported |
| `/forms/post` | GET | an HTML form that submits to `/post` |

## `/post`, `/put`, `/patch`, `/delete`

```console
$ curl -s -X PUT http://localhost:8080/put -H "Content-Type: application/json" -d '{"id": 1}'
{ "args": {}, "headers": {...}, "method": "PUT", "origin": "::1", "url": "http://localhost:8080/put",
  "data": "{\"id\": 1}", "files": {}, "form": {}, "json": { "id": 1 } }

$ curl -s -i -X GET http://localhost:8080/put
HTTP/1.1 405
Allow: PUT
Content-Type: text/plain;charset=utf-8

Method Not Allowed
```

How the body is interpreted depends on `Content-Type` (compared exactly, before any `;`):

| Content-Type | Lands in |
|---|---|
| `application/json` | `json` (and raw text in `data`) |
| `application/x-www-form-urlencoded` | `form` |
| `multipart/form-data` | text fields in `form`, uploaded files in `files` |
| `text/plain`, `text/html` | `data` |
| anything else / missing | `data` as `data:<type>;base64,...` |

**Practise:** idempotency (PUT twice = same result), form vs. JSON, file uploads, negative tests for wrong
methods, malformed JSON → `400 {"detail": "error parsing request body: ..."}`, bodies over 1 MiB → `400`.

**Common mistakes:** `Application/JSON` (case matters here: it will not be parsed); expecting
`json` to be filled for a form post; forgetting the `Content-Type` entirely.

## `/head`

HEAD only. You get headers (including `Content-Length` of what a GET would return) and no body.
`GET /head` → `405 Allow: HEAD`. **Practise:** checking size/type of a resource without downloading it.

## `/upload`

Reads and throws away the body; useful as a **cheap upload sink in load tests**.

```console
$ head -c 2048 /dev/zero | curl -s -X POST http://localhost:8080/upload --data-binary @- -H "Content-Type: application/octet-stream"
{ ..., "method": "POST", "url": "http://localhost:8080/upload", "bytes_received": 2048 }
```

**Common mistake:** expecting the content back: it is deliberately discarded.

## `/forms/post`

An HTML pizza-order form posting to `/post`. Open it in a browser, submit, and compare with what your tool
sends for the same form (repeated checkboxes become repeated `topping` fields).
