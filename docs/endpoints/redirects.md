# Redirects

Most tools follow redirects automatically, which hides what happened. Turn following **off** to inspect each
hop (curl: omit `-L`; REST Assured:
`redirect(redirectConfig().followRedirects(false))`; Gatling: `disableFollowRedirect()`).

## `/redirect/{n}`

Redirects `n` times, then to `/get`. Hops use `/relative-redirect/n-1` (relative `Location`);
`?absolute=true` switches to absolute URLs.

```console
$ curl -s -i http://localhost:8080/redirect/2
HTTP/1.1 302
Location: /relative-redirect/1
Content-Length: 0

$ curl -s -L http://localhost:8080/redirect/2 | grep url
  "url": "http://localhost:8080/get"
```

## `/relative-redirect/{n}` and `/absolute-redirect/{n}`

Same chain with relative (`/relative-redirect/1`) or absolute (`http://localhost:8080/absolute-redirect/1`)
`Location` headers. Absolute redirects use the scheme the client saw, so `X-Forwarded-Proto: https` changes them.

Errors: `n < 1` or not a number → `400`. There is no upper limit by default (as in go-httpbin);
`PLAYGROUND_MAX_REDIRECTS=20` adds one.

## `/redirect-to?url=…&status_code=…`

Redirects to `url` with `status_code` (300–399, default 302).

```console
$ curl -s -i "http://localhost:8080/redirect-to?url=/get&status_code=307"
HTTP/1.1 307
Location: /get
```

Only absolute URLs (`https://…`) and root-relative paths (`/get`) are allowed. `foo`, `//evil.com` and
double-encoded tricks → `403`. With `PLAYGROUND_ALLOWED_REDIRECT_DOMAINS=example.org`, absolute targets must be
on that list (sub-domains do not match).

**Practise:**
- max-redirect limits (`/redirect/10` with a client limit of 5: what does your tool do? REST Assured *throws*);
- 301/302 vs. 307/308: whether your client keeps the method and body on `POST /redirect-to?url=/post&status_code=307`;
- open-redirect security tests.

**Common mistakes:** asserting the final status when you meant the hop; assuming all clients resolve relative
`Location` headers the same way; using `-L` with curl and forgetting `-X POST` becomes GET after a 302.
