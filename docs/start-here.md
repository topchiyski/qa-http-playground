# Start here

New to API testing? This page gets you from zero to your first test in about 30 minutes. No prior knowledge is
assumed; terms in *italics* are explained in the [glossary](glossary.md).

**You need:** the playground running on your machine (see the [Quickstart](../README.md#quickstart): one command,
only Java required) and a terminal. Check that it runs: open <http://localhost:8080/> in a browser. You should see
the qa-http-playground home page.

---

## 1. What is an API, and what does an API tester do?

A website is made for people: it sends pages to a browser. An **API** (*Application Programming Interface*) is
made for programs: one program sends a **request**, another sends back a **response**, usually as structured data
(*JSON*). Your banking app, a shop's checkout, a weather widget: they all talk to APIs behind the scenes.

An API tester sends requests on purpose, including unusual and wrong ones, and checks that every response is what
it should be: the right data, the right *status code*, the right *headers*, fast enough, and safe.

The playground is an API built for that. It does nothing useful on its own: it answers in predictable, documented
ways, so you can practise.

## 2. Your first request

Open a terminal and type:

```bash
curl -i "http://localhost:8080/get?colour=red&size=L" -H "X-Learner: ada"
```

`curl` is a small program that sends HTTP requests and prints the answer (it is installed on Windows 10+, macOS
and Linux). `-i` shows the response headers too; `-H` adds a header to the request.

You get this back (some lines shortened):

```text
HTTP/1.1 200                                            ← status line: 200 = OK
Content-Type: application/json;charset=utf-8            ← response headers: facts about the response
Content-Length: 370
Date: Tue, 10 Nov 2026 15:31:38 GMT
                                                        ← empty line: headers end, body starts
{
  "args": {                                             ← the query parameters you sent
    "colour": ["red"],
    "size": ["L"]
  },
  "headers": {                                          ← the request headers you sent
    "Accept": ["*/*"],
    "Host": ["localhost:8080"],
    "User-Agent": ["curl/8.23.0"],
    "X-Learner": ["ada"]
  },
  "method": "GET",
  "origin": "127.0.0.1",                                ← your IP address, as the server saw it
  "url": "http://localhost:8080/get?colour=red&size=L"
}
```

`/get` is an **echo** endpoint: it tells you what it received. You see exactly what your tool sent, which makes it a
good place to learn.

## 3. Anatomy of a request

```text
GET /get?colour=red&size=L HTTP/1.1      ← method, path, query string
Host: localhost:8080                     ← headers: one per line, "Name: value"
X-Learner: ada
                                         ← empty line
(body: none for this GET)                ← body: data you send, e.g. JSON for a POST
```

| Part | Example | What it is for |
|---|---|---|
| **Method** | `GET`, `POST`, `PUT`, `PATCH`, `DELETE` | What you want to do: read, create, replace, change, remove |
| **URL** | `http://localhost:8080/get` | Where: scheme, host, port, then the *path* |
| **Query string** | `?colour=red&size=L` | Extra parameters after `?`, separated by `&` |
| **Headers** | `Content-Type: application/json` | Facts about the request: format, language, credentials… |
| **Body** | `{"name": "Ada"}` | The data you send (not all requests have one) |

Try sending a body:

```bash
curl -i -X POST http://localhost:8080/post -H "Content-Type: application/json" -d '{"name":"Ada","age":36}'
```

In the response, look for `"json": {"age": 36, "name": "Ada"}`: the server understood your JSON.
(On Windows PowerShell, use `curl.exe` and write the body as `-d '{\"name\":\"Ada\",\"age\":36}'`.)

## 4. Anatomy of a response

| Part | Example | What to check as a tester |
|---|---|---|
| **Status code** | `200`, `404`, `500` | Did the request succeed? Is that the *expected* result? |
| **Headers** | `Content-Type: application/json` | Is the format declared? Caching, cookies, security headers? |
| **Body** | the JSON document | Is the data correct and complete? |
| **Time** | 25 ms | Fast enough? |

### Status codes in one table

The first digit says what kind of answer it is:

| Class | Meaning | Typical codes | Try it |
|---|---|---|---|
| **2xx** | Success | `200 OK`, `201 Created`, `204 No Content` | `/status/201` |
| **3xx** | Go somewhere else | `301`, `302` + a `Location` header | `/redirect/1` |
| **4xx** | *You* (the client) made a mistake | `400 Bad Request`, `401 Unauthorized`, `404 Not Found`, `405 Method Not Allowed` | `/status/404` |
| **5xx** | The *server* failed | `500 Internal Server Error`, `503 Service Unavailable` | `/status/503` |

```bash
curl -i http://localhost:8080/status/404
```

```text
HTTP/1.1 404
Content-Type: text/plain;charset=utf-8
Content-Length: 0
```

**A 404 is not automatically a bug.** If you asked for something that does not exist, 404
is the *correct* answer, and your test should expect it. Testing that errors are reported correctly ("negative
tests") is half the job.

## 5. JSON in five minutes

JSON is how most APIs format data. It has only six kinds of values:

```json
{
  "name": "Ada",               // text (string), always in double quotes
  "age": 36,                   // number
  "admin": false,              // true or false (boolean)
  "nickname": null,            // nothing
  "skills": ["math", "code"],  // list (array), in [square brackets]
  "address": {                 // object: named values in {curly braces}
    "city": "London"
  }
}
```

(Real JSON has no `//` comments; they are here to explain.)

To *point* at a value, tools use a path: `address.city` is `"London"`, `skills[0]` is `"math"`. In the playground's
answers, query parameters are lists, so the colour you sent is at `args.colour[0]`. REST Assured, Gatling and most
other tools use variations of this idea.

## 6. Your first test

A **test** is a request plus *expectations* that a tool checks automatically. Here is one in REST Assured, the Java
library this project's tests are written in:

```java
given().queryParam("colour", "red")
.when().get("/get")
.then().statusCode(200).body("args.colour[0]", equalTo("red"));
```

It says: *send this, expect that*. Every test tool works this way; the tool changes, the thinking does not.

## 7. Choose your path

| You want to… | Start with | Then |
|---|---|---|
| Automate API tests (test automation, SDET) | [REST Assured lesson 1](learning-paths/rest-assured/01-first-request.md) | the whole [REST Assured path](learning-paths/rest-assured/README.md), beginner to advanced |
| Test performance | [REST Assured lessons 1–5](learning-paths/rest-assured/README.md) for HTTP basics | the [Gatling path](learning-paths/gatling/README.md), from [lesson 1](learning-paths/gatling/01-first-simulation.md) |

Both paths: [docs/learning-paths/](learning-paths/README.md). Every endpoint is explained in [docs/endpoints/](endpoints/),
and you can try each one in your browser at <http://localhost:8080/swagger-ui>.
