package io.qaplayground.server.web;

import java.util.LinkedHashMap;
import java.util.Map;

import io.qaplayground.server.config.PlaygroundProperties;
import io.qaplayground.server.support.HttpbinResponses;
import io.qaplayground.server.support.StaticAssets;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The landing page, plus a few tiny utility endpoints.
 */
@Tag(name = "Index", description = "Landing page, robots.txt and version info")
@RestController
public class IndexController {

    private final PlaygroundProperties properties;
    private final BuildProperties build;

    public IndexController(PlaygroundProperties properties, ObjectProvider<BuildProperties> build) {
        this.properties = properties;
        this.build = build.getIfAvailable();
    }

    /**
     * The HTML landing page.
     *
     * <p><b>HTTP concept:</b> {@code Content-Security-Policy}. The page is sent with go-httpbin's CSP header, which
     * tells
     * the browser to load scripts, styles and images only from this server. Inspect it in your browser's dev tools.
     */
    @Operation(summary = "HTML landing page listing every endpoint")
    @GetMapping("/")
    public ResponseEntity<byte[]> index() {
        return ResponseEntity.ok()
                .header("Content-Security-Policy",
                        "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' camo.githubusercontent.com")
                .header("Content-Type", HttpbinResponses.HTML)
                .body(StaticAssets.get("pages/index.html"));
    }

    /**
     * A robots.txt that asks crawlers to stay away from {@code /deny}.
     *
     * <p><b>HTTP concept:</b> well-known plain-text resources and {@code text/plain} responses.
     */
    @Operation(summary = "robots.txt that disallows /deny")
    @RequestMapping("/robots.txt")
    public ResponseEntity<byte[]> robots() {
        return HttpbinResponses.text(200, "User-agent: *\nDisallow: /deny\n");
    }

    /**
     * The page robots.txt tells crawlers not to visit.
     *
     * <p><b>HTTP concept:</b> robots.txt is only a request. Nothing stops a client from fetching this.
     */
    @Operation(summary = "Page that robots.txt disallows")
    @RequestMapping("/deny")
    public ResponseEntity<byte[]> deny() {
        return HttpbinResponses.text(200, "YOU SHOULDN'T BE HERE");
    }

    /**
     * The service name, plus build details when {@code playground.full-version=true}.
     *
     * <p><b>HTTP concept:</b> a simple JSON "about" resource, handy as a smoke-test target.
     */
    @Operation(summary = "Service name and (optionally) build version")
    @RequestMapping("/version")
    public ResponseEntity<byte[]> version() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "qa-http-playground");
        if (properties.fullVersion() && build != null) {
            body.put("version", build.getVersion());
            if (build.getTime() != null) {
                body.put("build_date", build.getTime().toString());
            }
            body.put("java_version", System.getProperty("java.version"));
        }
        return HttpbinResponses.json(body);
    }
}
