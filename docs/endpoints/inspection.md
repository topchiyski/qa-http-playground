# Request inspection

Endpoints that echo your request back. Use them to see what your tool *actually* sends
(default headers, encodings, the IP a proxy reports) before you write assertions against a real API.

> **Shape to remember:** query args and headers are **lists** (`"args": {"size": ["S", "M"]}`),
> like go-httpbin. In JSONPath that is `$.args.size[0]`; in REST Assured GPath `args.size[0]`.

## `/get`: echo a GET request

**Purpose:** returns query args, headers, client IP (`origin`) and the full URL. GET and HEAD only.

```console
$ curl -s "http://localhost:8080/get?colour=red&size=S&size=M"
{
  "args": {
    "colour": ["red"],
    "size": ["S", "M"]
  },
  "headers": {
    "Accept": ["*/*"],
    "Host": ["localhost:8080"],
    "User-Agent": ["curl/8.0"]
  },
  "method": "GET",
  "origin": "::1",
  "url": "http://localhost:8080/get?colour=red&size=S&size=M"
}
```

(Real responses put every array element on its own line; compacted here for reading.)

**Practise:** building query strings with repeated keys, URL-encoding (`?q=a+b` → `"a b"`), asserting on
nested JSON, negative test `POST /get` → `405` with `Allow: GET, HEAD`.

**Common mistakes:** asserting `args.size == "S"` (it is a list); forgetting that header *names* are
normalised (`x-trace-id` comes back as `X-Trace-Id`).

## `/anything`, `/anything/{anything}`: echo everything

**Purpose:** any method, any sub-path. Returns everything `/get` does plus the body:
`data` (raw text), `form`, `files`, `json`.

```console
$ curl -s -X POST http://localhost:8080/anything/orders/42 -H "Content-Type: application/json" -d '{"qty": 2}'
{
  "args": {},
  "headers": { "Content-Length": ["10"], "Content-Type": ["application/json"], ... },
  "method": "POST",
  "origin": "::1",
  "url": "http://localhost:8080/anything/orders/42",
  "data": "{\"qty\": 2}",
  "files": {},
  "form": {},
  "json": { "qty": 2 }
}
```

**Practise:** sending the same data as JSON, form-urlencoded and multipart, and seeing which field it lands
in; mocking a not-yet-built API by pointing your tests at `/anything/...`.

**Common mistakes:** sending a body without `Content-Type`: it is then not parsed and shows up in `data`
as `data:application/octet-stream;base64,...`. Expecting `json.qty` to be `2.0`: numbers are float64
server-side but whole numbers print without decimals.

## `/headers`: request headers

```console
$ curl -s http://localhost:8080/headers -H "x-trace-id: abc-123"
{ "headers": { "Accept": ["*/*"], "Host": ["localhost:8080"], "User-Agent": ["curl/8.0"], "X-Trace-Id": ["abc-123"] } }
```

**Practise:** checking that auth/correlation headers are attached by your framework's filters/interceptors.
**Common mistakes:** case-sensitive header-name assertions; forgetting tools add headers you did not set
(`User-Agent`, `Accept-Encoding`, `Connection`).

## `/ip`: client IP

`{"origin": "..."}`, or plain text with `?format=text`. The first value of `X-Forwarded-For` (or
`CF-Connecting-IP`, `Fly-Client-IP`, `Fastly-Client-IP`, `True-Client-IP`) wins over the socket address.

```console
$ curl -s "http://localhost:8080/ip?format=text" -H "X-Forwarded-For: 203.0.113.7, 10.0.0.1"
203.0.113.7
```

**Practise:** testing proxy-aware logic. **Common mistake:** trusting these headers for security: anyone can set them.

## `/user-agent`

`{"user-agent": "..."}` (empty string when absent). **Practise:** JSON keys with dashes need quoting:
GPath `'user-agent'`, JSONPath `$['user-agent']`.

## `/hostname`

`{"hostname": "go-httpbin"}` by default (a fixed value, as in go-httpbin);
`PLAYGROUND_USE_REAL_HOSTNAME=true` shows the machine name. **Practise:** behind a load balancer with several
instances, count which instance answered.

## `/dump/request`: the raw request

Returns the request as HTTP/1.x text: request line, headers **sorted by name**, blank line, body.

```console
$ curl -s -X POST "http://localhost:8080/dump/request?q=1" -H "Content-Type: text/plain" -d payload
POST /dump/request?q=1 HTTP/1.1
Host: localhost:8080
Accept: */*
Content-Length: 7
Content-Type: text/plain
User-Agent: curl/8.0

payload
```

**Practise:** seeing exactly how multipart boundaries or chunked uploads look on the wire.

## `/env`

Only environment values starting with `HTTPBIN_ENV_`; `{"env": null}` when there are none.

```console
$ HTTPBIN_ENV_GREETING=hello java -jar playground-server-*-exec.jar
$ curl -s http://localhost:8080/env
{ "env": { "HTTPBIN_ENV_GREETING": "hello" } }
```

## `/uuid`

`{"uuid": "6f0c7f5e-..."}`: a random version-4 UUID. **Practise:** extracting a value and reusing it in the
next request (REST Assured `extract().path("uuid")`, Gatling `saveAs`).
**Common mistake:** asserting on the value; assert on the format (regex) and uniqueness instead.
