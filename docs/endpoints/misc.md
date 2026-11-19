# Landing page and utilities

| Endpoint | What it returns | Good for |
|---|---|---|
| `/` | HTML list of all endpoints, links to Swagger UI. Sent with a `Content-Security-Policy` header. | Orientation; inspecting CSP in browser dev tools |
| `/robots.txt` | `User-agent: *` / `Disallow: /deny` | Plain-text responses |
| `/deny` | `YOU SHOULDN'T BE HERE` | Showing robots.txt is advisory only |
| `/version` | `{"service": "qa-http-playground"}` (+ build info with `PLAYGROUND_FULL_VERSION=true`) | A cheap smoke-test target |
| `/swagger-ui` | Interactive API documentation | Exploring parameters, trying requests |
| `/v3/api-docs` | OpenAPI 3 JSON | Generating clients, contract tests |
| `:8081/actuator/health` | `{"groups":["liveness","readiness"],"status":"UP"}` | Readiness checks before a load test |
| `:8081/actuator/prometheus` | Prometheus metrics | Server-side view during load tests |

Behaviour shared by every endpoint (CORS, 404, 405, error format): see the [endpoint index](README.md#behaviour-shared-by-every-endpoint).
