# WebSocket

## `ws://localhost:8080/websocket/echo?max_fragment_size=&max_message_size=`

Upgrades the HTTP connection to a WebSocket (`101 Switching Protocols`) and echoes every message, text or
binary. Limits (defaults: 512 KiB per frame, 1 MiB per message) let you test fragmentation and "too big"
handling. Sessions end after `playground.max-duration` (10 s).

```console
$ npx wscat -c ws://localhost:8080/websocket/echo
Connected (press CTRL+C to quit)
> hello
< hello
```

| Situation | Server reaction |
|---|---|
| message larger than `max_fragment_size` | echoed in several frames (continuation frames) |
| a frame larger than `max_fragment_size` | close `1009` (message too big) |
| invalid UTF-8 in a text message | close `1007` |
| protocol violation (unmasked frame, bad opcode…) | close `1002` |
| ping | pong with the same payload |
| close | close `1000` |

Handshake problems are ordinary HTTP errors:

```console
$ curl -s http://localhost:8080/websocket/echo
{ "status_code": 400, "error": "Bad Request", "detail": "missing required `Upgrade: websocket` header" }
```

**Practise:** Gatling `ws(...)`, Java's
`java.net.http.WebSocket` (see `WebSocketEchoTest`); asserting on close codes.

**Common mistakes:** using `http://` instead of `ws://`; expecting a plain HTTP client to speak WebSocket (you
need a WebSocket client).
