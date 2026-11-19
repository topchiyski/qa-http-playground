# Images

## `/image/{format}`

`png`, `jpeg`, `webp`, `svg` (`image/svg+xml`) or `avif`. Anything else → `404`.

```console
$ curl -s -o pig.png -w "%{http_code} %{content_type} %{size_download}\n" http://localhost:8080/image/png
200 image/png 8090
```

**Practise:** binary responses: assert on `Content-Type`, `Content-Length` and the file signature
("magic bytes": PNG starts with `89 50 4E 47`).

## `/image`

Chooses a format from `Accept` with a simple substring match (like the reference, q-values are ignored):
no header or `image/*` → PNG; otherwise the first of `image/png`, `image/webp`, `image/svg+xml`,
`image/jpeg`, `image/avif` found. Anything else → `415 Unsupported Media Type`.

```console
$ curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/image
415
$ curl -s -o /dev/null -w "%{http_code} %{content_type}\n" -H "Accept: image/webp" http://localhost:8080/image
200 image/webp
```

**Common mistake:** curl and REST Assured send `Accept: */*` by default, which this endpoint rejects.
Defaults matter in content negotiation.
