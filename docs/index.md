# Documentation

Every page, grouped by what you want to do. If you are new, read the tutorials from the top.

## Tutorials: learn by doing

1. [Start here](start-here.md): HTTP, curl, JSON and your first test, in about 30 minutes.
2. [Learning paths](learning-paths/README.md): pick a path and see how a lesson works.
3. [REST Assured path](learning-paths/rest-assured/README.md): automated API tests in Java.
   - Beginner: [1 First request](learning-paths/rest-assured/01-first-request.md),
     [2 Assert on JSON](learning-paths/rest-assured/02-assert-on-json.md),
     [3 Headers and content types](learning-paths/rest-assured/03-headers-and-content-types.md),
     [4 Sending data](learning-paths/rest-assured/04-sending-data.md),
     [5 Status codes and negative tests](learning-paths/rest-assured/05-status-codes-and-negative-tests.md)
   - Intermediate: [6 Extract and chain](learning-paths/rest-assured/06-extract-and-chain.md),
     [7 Data-driven tests and test design](learning-paths/rest-assured/07-data-driven-and-test-design.md),
     [8 Authentication](learning-paths/rest-assured/08-authentication.md),
     [9 Redirects and cookies](learning-paths/rest-assured/09-redirects-and-cookies.md),
     [10 Reuse without copy-paste](learning-paths/rest-assured/10-reuse-without-copy-paste.md),
     [11 Contract checks](learning-paths/rest-assured/11-contract-checks.md)
   - Advanced: [12 Caching and partial content](learning-paths/rest-assured/12-caching-and-partial-content.md),
     [13 Slow and flaky services](learning-paths/rest-assured/13-slow-and-flaky-services.md),
     [14 Custom filters](learning-paths/rest-assured/14-custom-filters.md),
     [15 Security negative tests](learning-paths/rest-assured/15-security-negative-tests.md),
     [Final project](learning-paths/rest-assured/project.md)
4. [Gatling path](learning-paths/gatling/README.md): load tests in Java.
   - Beginner: [1 First simulation](learning-paths/gatling/01-first-simulation.md),
     [2 Checks](learning-paths/gatling/02-checks.md),
     [3 Reading the report](learning-paths/gatling/03-reading-the-report.md),
     [4 Realistic users](learning-paths/gatling/04-realistic-users.md)
   - Intermediate: [5 Dynamic data](learning-paths/gatling/05-dynamic-data.md),
     [6 Workload models](learning-paths/gatling/06-workload-models.md),
     [7 Pass/fail criteria](learning-paths/gatling/07-pass-fail-criteria.md),
     [8 Ramp-up load test](learning-paths/gatling/08-ramp-up-load-test.md)
   - Advanced: [9 Stress test](learning-paths/gatling/09-stress-test.md),
     [10 Soak test](learning-paths/gatling/10-soak-test.md),
     [11 Honest measurement](learning-paths/gatling/11-honest-measurement.md),
     [Final project](learning-paths/gatling/project.md)

## How-to guides: get one task done

- [Run the server](how-to/run-the-server.md): start and stop it, as a jar, with fixed resources, on another machine.
- [Run the tests](how-to/run-the-tests.md): all of them, one part, or against your own server.
- [Contribute](../CONTRIBUTING.md): add or change an endpoint.

## Reference: look up exact facts

- [Endpoints](endpoints/README.md): the full list and the behaviour all endpoints share.
  [Inspection](endpoints/inspection.md), [methods](endpoints/methods.md), [status](endpoints/status.md),
  [redirects](endpoints/redirects.md), [auth](endpoints/auth.md), [caching](endpoints/caching.md),
  [cookies](endpoints/cookies.md), [content](endpoints/content.md), [images](endpoints/images.md),
  [bytes and ranges](endpoints/bytes-and-ranges.md), [timing and streaming](endpoints/timing-and-streaming.md),
  [WebSocket](endpoints/websocket.md), [misc](endpoints/misc.md).
- [Configuration](configuration.md): every setting, its environment variable and default.
- [Compatibility](COMPATIBILITY.md): every difference from go-httpbin.
- [Glossary](glossary.md): the terms used in these docs.
- [Traceability matrix](testing/TRACEABILITY.md): tests per level, technique and route (generated).

## Explanation: understand why

- [Architecture](architecture.md): modules, packages and which controller serves which endpoint.
- [Load testing](load-testing.md): why laptop numbers lie, and how this server behaves under load.
- [Test strategy](testing/TEST-STRATEGY.md): how the playground itself is tested.
- [Test design](testing/TEST-DESIGN.md): the derivations behind the tests, and the defects they found.
- [Risk register](testing/RISK-REGISTER.md): the risks that set how deeply each area is tested.
- [Exploratory testing](testing/EXPLORATORY-CHARTERS.md): charters and session notes.
