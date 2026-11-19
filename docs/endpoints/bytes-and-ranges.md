# Bytes, streamed bytes and ranges

## `/bytes/{n}?seed=`

`n` random bytes (max 1 MiB) with `Content-Length`. The same `seed` always gives the same bytes, and they
match what go-httpbin gives for that seed.

```console
$ curl -s "http://localhost:8080/bytes/16?seed=1234567890" | xxd
00000000: bfcd 2afa 15a2 b372 c707 985a 2202 4a8e  ..*....r...Z".J.
```

**Practise:** download size and checksum assertions; reproducible fixtures. `/bytes/0` → empty `200`;
negative or too large → `400`.

## `/stream-bytes/{n}?seed=&chunk_size=`

Same bytes, sent with `Transfer-Encoding: chunked` in chunks of `chunk_size` (default 10240). No
`Content-Length`, because the client cannot know the size in advance.

**Practise:** progress reporting; client behaviour without `Content-Length`.

## `/range/{n}`: HTTP range requests

`n` bytes of the alphabet (`abcdefghijklmnopqrstuvwxyzabc…`) with full range support. `?duration=2s` spreads
the transfer over time (simulating a slow download).

```console
$ curl -s -i -H "Range: bytes=0-4" http://localhost:8080/range/26
HTTP/1.1 206
ETag: range26
Accept-Ranges: bytes
Content-Range: bytes 0-4/26
Content-Length: 5

abcde
```

| Range header | Result |
|---|---|
| none | 200, whole body |
| `bytes=10-14` | 206, `Content-Range: bytes 10-14/N` |
| `bytes=-3` | 206, last 3 bytes |
| `bytes=0-1,5-6` | 206, `multipart/byteranges` body with one part per range |
| `bytes=50-` on a 26-byte resource | **416**, `Content-Range: bytes */26` |
| with `If-Range: "stale"` | 200, whole body (the validator did not match) |

**Practise:** resumable downloads (request the rest after an interruption), parsing multipart byteranges,
416 handling.

**Common mistakes:** off-by-one (ranges are **inclusive**: `0-4` is 5 bytes); expecting 416 for a range that
is only *partly* past the end (it is clipped instead).
