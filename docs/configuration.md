# Configuration

Every setting has a default in `playground-server/src/main/resources/application.yml`. Override one with an
environment variable, for example `PLAYGROUND_MAX_DURATION=30s` or `SERVER_PORT=9090`. Defaults match go-httpbin.

## Limits and behaviour (`playground.*`)

| Variable | Default | Meaning |
|---|---|---|
| `PLAYGROUND_MAX_BODY_SIZE` | `1MB` | Largest request body accepted and response body generated |
| `PLAYGROUND_MAX_DURATION` | `10s` | Longest `/delay`, `/drip`, `/sse`, `/jsonl`, `/range?duration=` and WebSocket session |
| `PLAYGROUND_MAX_STREAM_LINES` | `100` | `/stream/{n}` is clamped to this |
| `PLAYGROUND_MAX_LINKS` | `256` | Upper bound for `/links/{n}` |
| `PLAYGROUND_MAX_REDIRECTS` | `0` (unlimited) | Cap for the redirect endpoints |
| `PLAYGROUND_ALLOWED_REDIRECT_DOMAINS` | empty (any absolute URL) | Allowlist for `/redirect-to` |
| `PLAYGROUND_EXCLUDE_HEADERS` | empty | Request headers hidden from echo endpoints, e.g. `x-fly-*,via` |
| `PLAYGROUND_UNSAFE_ALLOW_DANGEROUS_RESPONSES` | `false` | `true` turns off the HTML-escaping of bodies whose `Content-Type` the client chose (`/base64`, `/response-headers`) |
| `PLAYGROUND_HOSTNAME` | `go-httpbin` | Value reported by `/hostname` |
| `PLAYGROUND_USE_REAL_HOSTNAME` | `false` | Report the machine's real hostname in `/hostname` |
| `PLAYGROUND_ENV_PREFIX` | `HTTPBIN_ENV_` | Only environment variables with this prefix appear in `/env` |
| `PLAYGROUND_FULL_VERSION` | `false` | Add build version, build date and Java version to `/version` |

## Endpoint defaults

Used when the request does not set the parameter.

| Variable | Default |
|---|---|
| `PLAYGROUND_DRIP_DURATION`, `PLAYGROUND_DRIP_DELAY`, `PLAYGROUND_DRIP_NUM_BYTES` | `2s`, `2s`, `10` |
| `PLAYGROUND_SSE_COUNT`, `PLAYGROUND_SSE_DURATION`, `PLAYGROUND_SSE_DELAY` | `10`, `5s`, `0s` |
| `PLAYGROUND_JSONL_COUNT`, `PLAYGROUND_JSONL_DURATION`, `PLAYGROUND_JSONL_DELAY` | `10`, `0s`, `0s` |

## Ports and other values

| Variable | Default | Meaning |
|---|---|---|
| `SERVER_PORT` | `8080` | The API |
| `MANAGEMENT_SERVER_PORT` | `8081` | Actuator (health, metrics, Prometheus) |
| `HTTPBIN_ENV_*` | none | Values exposed by `/env` (the prefix is `PLAYGROUND_ENV_PREFIX`) |

The server listens on all interfaces (`0.0.0.0`), so other machines in your lab can reach it.
