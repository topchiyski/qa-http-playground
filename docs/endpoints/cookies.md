# Cookies

## `/cookies`

The cookies your client sent: `{"cookies": {"name": "value"}}`.

```console
$ curl -s -b "flavour=oatmeal; size=large" http://localhost:8080/cookies
{ "cookies": { "flavour": "oatmeal", "size": "large" } }
```

## `/cookies/set?name=value&…`

Sets one cookie per query parameter, then `302` to `/cookies`. Defaults: `Path=/; HttpOnly`, plus `Secure`
when the client used HTTPS. Override attributes with `attr[...]` parameters:

| Parameter | Effect |
|---|---|
| `attr[SameSite]=Strict\|Lax\|None` | `SameSite=…` |
| `attr[Secure]=true\|false` | add / remove `Secure` |
| `attr[HttpOnly]=false` | remove `HttpOnly` |
| `attr[Path]=/foo` | `Path=/foo` |
| `attr[Domain]=example.org` | `Domain=example.org` |

```console
$ curl -s -i -g "http://localhost:8080/cookies/set?flavour=oatmeal&attr[SameSite]=Lax"
HTTP/1.1 302
Location: /cookies
Set-Cookie: flavour=oatmeal; Path=/; HttpOnly; SameSite=Lax

$ curl -s -L -c jar.txt -b jar.txt "http://localhost:8080/cookies/set?flavour=oatmeal"
{ "cookies": { "flavour": "oatmeal" } }
```

(`-g` stops curl treating `[` `]` as a glob pattern.)

## `/cookies/delete?name`

Expires the named cookies (`Max-Age=0` and an `Expires` date in the past), then `302` to `/cookies`.

**Practise:** cookie jars across requests; `HttpOnly`/`Secure`/`SameSite` semantics; deleting a cookie that was
set with a custom `Path` (you must delete with the same `attr[Path]`).

**Common mistakes:**
- In REST Assured, cookies set on a 302 that REST Assured follows *internally* never reach a `CookieFilter`.
  Stop at the 302 (see `CookiesTest.cookieJarRoundTrip`).
- `Secure` cookies are not sent back over plain HTTP by browsers and most clients.
- Expecting multiple `Set-Cookie` values in a specific order: order is not significant.
- `attr[SameSite]=None` without `attr[Secure]=true`: the server sends exactly what you asked for, and Chromium
  silently refuses to store it over plain http, so the cookie seems to "disappear". An API client keeps it;
  a browser does not. Demonstrated by `CookieJarJourneyTest.sameSiteNoneNeedsSecure` (found in exploratory
  session ES-01).
