# qa-http-playground

[![build](https://github.com/topchiyski/qa-http-playground/actions/workflows/build.yml/badge.svg)](https://github.com/topchiyski/qa-http-playground/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

An HTTP API you run on your own machine, to learn API testing and load testing.

It is for manual QA engineers, test automation engineers and SDETs. Everything runs locally: the server, the course
code and the docs. Nobody else uses your copy, so you can test it hard and load-test it freely.

The API is compatible with the widely used httpbin API, so tutorials written for httpbin work here unchanged.

> **Local and lab networks only.** The playground echoes requests, sets arbitrary headers and redirects anywhere,
> on purpose. It listens on all interfaces (`0.0.0.0:8080`) so other machines in your lab can reach it. Do not
> expose it to the internet.

## Quickstart

You need Java 21 or newer (a JDK). The Maven wrapper (`mvnw`) in the repository downloads everything else.

```bash
git clone https://github.com/topchiyski/qa-http-playground.git
cd qa-http-playground
./mvnw spring-boot:run
```

Wait for `Started PlaygroundApplication`, then open <http://localhost:8080/>. Stop the server with `Ctrl+C`.

To run every test, open a second terminal in the same directory:

```bash
./mvnw verify
```

The tests start their own copy of the server. The first run downloads Maven and the dependencies, so it takes a few
minutes. On Windows, type `.\mvnw` in PowerShell or `mvnw` in `cmd.exe`.

## Where to go next

| You want to… | Read |
|---|---|
| Learn API testing from zero | [Start here](docs/start-here.md) (30 minutes), then a [learning path](docs/learning-paths/README.md) |
| Write automated API tests in Java | [REST Assured path](docs/learning-paths/rest-assured/README.md): 15 lessons and a final project |
| Write load tests in Java | [Gatling path](docs/learning-paths/gatling/README.md): 11 lessons and a final project |
| Look up an endpoint | [Endpoints](docs/endpoints/README.md) |
| Change a limit or a port | [Configuration](docs/configuration.md) |
| See everything | [Documentation index](docs/index.md) |

## Credit and license

MIT: see [LICENSE](LICENSE) and [NOTICE](NOTICE). Contributions are welcome: see [CONTRIBUTING.md](CONTRIBUTING.md).

Endpoint behaviour is modelled on [go-httpbin](https://github.com/mccutchen/go-httpbin) by Will McCutchen (MIT),
which is the reference; every known difference is in [COMPATIBILITY.md](docs/COMPATIBILITY.md). The bundled images
and sample documents come from go-httpbin. Parts of Go's standard library (the `math/rand` generator, duration
parsing, URL escaping) are ported under Go's BSD license. qa-http-playground is not affiliated with or endorsed by
go-httpbin, httpbin.org or httpbingo.org.

I built this project with an AI coding assistant.
