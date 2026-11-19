package io.qaplayground.integration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import io.qaplayground.server.PlaygroundApplication;
import io.qaplayground.testing.trace.Trace;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/**
 * Base for integration tests: the real application (Spring context, Tomcat, filters) on random ports, driven over
 * real HTTP with the JDK client.
 *
 * <p>Integration tests check that components work <em>together with the framework</em>. Endpoint behaviour is the
 * API suite's job (docs/testing/TEST-STRATEGY.md section 3). Subclasses add properties with
 * {@code @TestPropertySource} to test configuration binding.
 */
@SpringBootTest(classes = PlaygroundApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0", "server.address=127.0.0.1", "spring.main.banner-mode=off"})
abstract class ServerIT {

    /**
     * A response, with the body as text.
     */
    record Res(int status, HttpHeaders headers, String body) {
        /**
         * The first value of a response header, or null.
         */
        String header(String name) {
            return headers.firstValue(name).orElse(null);
        }
    }

    // HTTP/1.1 explicitly: the JDK default (HTTP/2, an h2c upgrade attempt) made an oversized POST
    // wait 20 s locally. Open question Q1 in docs/testing/EXPLORATORY-CHARTERS.md.
    /**
     * One client for every test. It never follows redirects, so tests see each hop.
     */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Autowired
    Environment environment;

    /**
     * The application's base URL, on its random port.
     */
    String base() {
        return "http://127.0.0.1:" + environment.getProperty("local.server.port");
    }

    /**
     * The management (Actuator) base URL, on its own random port.
     */
    String managementBase() {
        return "http://127.0.0.1:" + environment.getProperty("local.management.port");
    }

    /**
     * GET through {@code HttpURLConnection}, for endpoints that send HTTP trailers ({@code /sse}, {@code /trailers}).
     *
     * <p>The JDK {@code HttpClient} can't parse a chunked body followed by trailer fields; it fails with "CR expected".
     */
    String getBodyAllowingTrailers(String path) {
        Trace.path(path);
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) URI.create(base() + path).toURL().openConnection();
            try (java.io.InputStream in = c.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } finally {
                c.disconnect();
            }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /**
     * A GET with optional headers, given as name/value pairs.
     */
    Res get(String path, String... headers) {
        return send("GET", path, null, headers);
    }

    /**
     * A request with any method, an optional body and optional headers.
     */
    Res send(String method, String path, String body, String... headers) {
        return sendTo(base(), method, path, body, headers);
    }

    /**
     * Like {@link #send}, against any base URL, such as the management port.
     */
    Res sendTo(String base, String method, String path, String body, String... headers) {
        Trace.path(path);
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(20))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        for (int i = 0; i < headers.length; i += 2) {
            b.header(headers[i], headers[i + 1]);
        }
        try {
            HttpResponse<String> r = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Res(r.statusCode(), r.headers(), r.body());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
