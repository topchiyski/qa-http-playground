# Authentication

The expected credentials are part of the URL, so every success and failure case needs no setup.

## `/basic-auth/{user}/{password}`

```console
$ curl -s -u user:passwd http://localhost:8080/basic-auth/user/passwd
{ "authenticated": true, "user": "user", "authorized": true }

$ curl -s -i -u user:wrong http://localhost:8080/basic-auth/user/passwd
HTTP/1.1 401
WWW-Authenticate: Basic realm="Fake Realm"

{ "authenticated": false, "user": "user", "authorized": false }
```

`authorized` duplicates `authenticated` for older clients.

**Practise:** preemptive vs. challenge-driven auth. Basic is `Authorization: Basic base64(user:password)`:
build it by hand once to see it is encoding, not encryption.

## `/hidden-basic-auth/{user}/{password}`

Like basic-auth, but failure is **404** with no challenge.

```console
$ curl -s -i http://localhost:8080/hidden-basic-auth/user/passwd
HTTP/1.1 404
{ "status_code": 404, "error": "Not Found" }
```

**Practise:** clients that only send credentials after a 401 challenge (REST Assured `auth().basic(…)`,
many browsers) **never get in**: you need preemptive auth. That is why the option exists.

## `/bearer`

Accepts any token: `Authorization: Bearer <token>` (exactly two parts, `Bearer` case-sensitive).

```console
$ curl -s -H "Authorization: Bearer abc.def.ghi" http://localhost:8080/bearer
{ "authenticated": true, "token": "abc.def.ghi" }

$ curl -s -i http://localhost:8080/bearer
HTTP/1.1 401
WWW-Authenticate: Bearer
{ "status_code": 401, "error": "Unauthorized" }
```

**Practise:** chaining: get a token from one call, use it on the next; negative tests for `bearer x`,
`Bearer`, `Bearer a b`.

## `/digest-auth/{qop}/{user}/{password}[/{algorithm}]`

HTTP Digest with `qop=auth` and `MD5` (default) or `SHA-256`. The password never crosses the wire.

```console
$ curl -s --digest -u user:passwd http://localhost:8080/digest-auth/auth/user/passwd
{ "authenticated": true, "user": "user", "authorized": true }

$ curl -s -i http://localhost:8080/digest-auth/auth/user/passwd/SHA-256 | grep -i www-auth
WWW-Authenticate: Digest qop=auth, realm="go-httpbin", algorithm=SHA-256, nonce=8f1c…, opaque=3a9e…
```

Errors: `qop` other than `auth` → `400 invalid QOP directive`; algorithm other than MD5/SHA-256 → `400`.

**Practise:** the challenge/response flow step by step (see `AuthenticationTest.digestByHand`), and finding out whether your tool supports SHA-256.

**Common mistakes:**
- Some clients' built-in Digest helpers fail here **and on go-httpbin**, because the server sends
  `nonce`/`opaque` unquoted. Computing the response yourself, as `digestByHand` does, always works.
- Like the reference, the server is stateless: it does not detect nonce reuse. Do not use it to test replay
  protection.
