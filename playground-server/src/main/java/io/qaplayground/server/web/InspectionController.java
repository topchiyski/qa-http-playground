package io.qaplayground.server.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import io.qaplayground.server.config.PlaygroundProperties;
import io.qaplayground.server.support.EchoResponses;
import io.qaplayground.server.support.HeaderNames;
import io.qaplayground.server.support.HttpbinResponses;
import io.qaplayground.server.support.QueryString;
import io.qaplayground.server.support.RequestInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static java.util.Collections.list;

/**
 * Endpoints that show you what your client actually sent.
 *
 * <p>Use them to see what your tool adds behind your back: default headers, encoded query strings, the client IP a
 * proxy reports.
 */
@Tag(name = "Request inspection", description = "Echo back the request: args, headers, IP, user agent, raw dump")
@RestController
public class InspectionController {

    private final EchoResponses echo;
    private final RequestInfo info;
    private final PlaygroundProperties properties;
    private final ConfigurableEnvironment environment;

    public InspectionController(EchoResponses echo, RequestInfo info, PlaygroundProperties properties,
                                ConfigurableEnvironment environment) {
        this.echo = echo;
        this.info = info;
        this.properties = properties;
        this.environment = environment;
    }

    /**
     * Echoes everything: method, query args, headers, origin IP, URL, and the parsed body ({@code data},
     * {@code form}, {@code files}, {@code json}). Accepts any method and any sub-path, such as
     * {@code /anything/orders/42}.
     *
     * <p><b>HTTP concept:</b> how a body is read depends on {@code Content-Type}. Send the same payload as JSON, as
     * form-urlencoded and as multipart, and compare the responses.
     */
    @Operation(summary = "Echo the request back, any method, any sub-path")
    @RequestMapping({"/anything", "/anything/**"})
    public ResponseEntity<byte[]> anything(HttpServletRequest request) {
        if ("HEAD".equals(request.getMethod())) {
            return HttpbinResponses.json(echo.noBody(request));
        }
        // For compatibility with httpbin, even GET requests may carry a body here.
        return echo.withBody(request);
    }

    /**
     * Echoes the query args, headers, origin and URL of a GET request.
     *
     * <p><b>HTTP concept:</b> query strings. Repeated keys ({@code ?a=1&a=2}) become arrays, and {@code +} decodes to a
     * space.
     */
    @Operation(summary = "Echo a GET request")
    @GetMapping("/get")
    public ResponseEntity<byte[]> get(HttpServletRequest request) {
        return HttpbinResponses.json(echo.noBody(request));
    }

    /**
     * The request headers, with names in canonical form.
     *
     * <p><b>HTTP concept:</b> header names are case-insensitive, and one name can carry several values.
     */
    @Operation(summary = "Request headers")
    @RequestMapping("/headers")
    public ResponseEntity<byte[]> headers(HttpServletRequest request) {
        return HttpbinResponses.json(Map.of("headers", info.headers(request)));
    }

    /**
     * The client's IP address. {@code ?format=text} returns it as plain text.
     *
     * <p><b>HTTP concept:</b> behind a proxy, the socket address is the proxy's. Headers such as
     * {@code X-Forwarded-For} carry the original client, and anyone can forge them.
     */
    @Operation(summary = "Client IP address")
    @Parameter(name = "format", in = ParameterIn.QUERY, description = "Use `text` for a plain-text response")
    @RequestMapping("/ip")
    public ResponseEntity<byte[]> ip(HttpServletRequest request) {
        String ip = info.clientIp(request);
        if ("text".equals(QueryString.first(info.args(request), "format"))) {
            return HttpbinResponses.text(200, ip + "\n");
        }
        return HttpbinResponses.json(Map.of("origin", ip));
    }

    /**
     * The {@code User-Agent} header.
     *
     * <p><b>HTTP concept:</b> every tool introduces itself differently. Compare curl, REST Assured and a browser.
     */
    @Operation(summary = "User-Agent header")
    @RequestMapping("/user-agent")
    public ResponseEntity<byte[]> userAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return HttpbinResponses.json(Map.of("user-agent", ua == null ? "" : ua));
    }

    /**
     * The server's hostname: {@code go-httpbin} by default, the real one when
     * {@code playground.use-real-hostname=true}.
     *
     * <p><b>HTTP concept:</b> behind a load balancer, this tells you which instance answered.
     */
    @Operation(summary = "Server hostname")
    @RequestMapping("/hostname")
    public ResponseEntity<byte[]> hostname() {
        String hostname = properties.hostname();
        if (properties.useRealHostname()) {
            try {
                hostname = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                // keep the configured name
            }
        }
        return HttpbinResponses.json(Map.of("hostname", hostname));
    }

    /**
     * Environment values the operator chose to expose: only names that start with {@code HTTPBIN_ENV_}. Returns
     * {@code {"env": null}} when there are none, exactly like the reference.
     *
     * <p><b>HTTP concept:</b> asserting on configuration that a deployment exposes.
     */
    @Operation(summary = "HTTPBIN_ENV_* variables")
    @RequestMapping("/env")
    public ResponseEntity<byte[]> env() {
        Map<String, String> env = new TreeMap<>();
        for (var source : environment.getPropertySources()) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                for (String name : enumerable.getPropertyNames()) {
                    if (name.startsWith(properties.envPrefix()) && !env.containsKey(name)) {
                        env.put(name, environment.getProperty(name, ""));
                    }
                }
            }
        }
        Map<String, Object> body = new TreeMap<>();
        body.put("env", env.isEmpty() ? null : env);
        return HttpbinResponses.json(body);
    }

    /**
     * The request as it would look on the wire in HTTP/1.x: request line, sorted headers, a blank line, then the body.
     *
     * <p><b>HTTP concept:</b> HTTP is text. This is the closest you get to the raw request without a packet sniffer.
     */
    @Operation(summary = "Raw HTTP/1.x dump of the request")
    @RequestMapping("/dump/request")
    public ResponseEntity<byte[]> dumpRequest(HttpServletRequest request) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String query = request.getQueryString();
        String uri = request.getRequestURI() + (query == null ? "" : "?" + query);
        StringBuilder head = new StringBuilder();
        head.append(request.getMethod()).append(' ').append(uri).append(' ').append(request.getProtocol()).append("\r\n");
        String host = request.getHeader("Host");
        if (host != null && !host.isEmpty()) {
            head.append("Host: ").append(host).append("\r\n");
        }
        String transferEncoding = request.getHeader("Transfer-Encoding");
        boolean chunked = transferEncoding != null && transferEncoding.toLowerCase().startsWith("chunked");
        if (transferEncoding != null) {
            head.append("Transfer-Encoding: ").append(transferEncoding).append("\r\n");
        }
        Map<String, List<String>> headers = new TreeMap<>();
        for (String name : list(request.getHeaderNames())) {
            String canonical = HeaderNames.canonical(name);
            if (canonical.equals("Host") || canonical.equals("Transfer-Encoding") || canonical.equals("Trailer")) {
                continue;
            }
            headers.computeIfAbsent(canonical, k -> new java.util.ArrayList<>()).addAll(list(request.getHeaders(name)));
        }
        headers.forEach((name, values) -> values.forEach(v ->
                head.append(name).append(": ").append(v.replace('\n', ' ').replace('\r', ' ').trim()).append("\r\n")));
        head.append("\r\n");
        try {
            out.write(head.toString().getBytes(StandardCharsets.UTF_8));
            byte[] body = request.getInputStream().readAllBytes();
            if (chunked) {
                if (body.length > 0) {
                    out.write((Integer.toHexString(body.length) + "\r\n").getBytes(StandardCharsets.US_ASCII));
                    out.write(body);
                    out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                }
                out.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            } else {
                out.write(body);
            }
        } catch (IOException e) {
            return HttpbinResponses.error(500, "failed to dump request: " + e.getMessage());
        }
        return HttpbinResponses.bytes(200, HttpbinResponses.TEXT, out.toByteArray());
    }

    /**
     * A random version-4 UUID.
     *
     * <p><b>HTTP concept:</b> taking a value from one response and using it in the next request (REST Assured
     * {@code extract()}, Gatling {@code saveAs}).
     */
    @Operation(summary = "Random UUID v4")
    @RequestMapping("/uuid")
    public ResponseEntity<byte[]> uuid() {
        return HttpbinResponses.json(Map.of("uuid", UUID.randomUUID().toString()));
    }
}
