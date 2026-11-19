package io.qaplayground.e2e.support;

import java.net.URI;

import io.qaplayground.server.PlaygroundApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The server the browser talks to: the playground started in-process on a free port, or any running instance given
 * with {@code -Dplayground.baseUri}.
 *
 * <p>The in-process server listens on loopback only, and pages are opened via {@code localhost}. That leaves
 * {@code 127.0.0.1} as a second, distinguishable name for the same server, which the redirect journey uses to see
 * whether a browser left the original host.
 */
public final class Playground {

    private static String baseUri;
    private static int port;

    private Playground() {
    }

    /**
     * The playground's address. The first call starts it in-process unless {@code -Dplayground.baseUri} is set.
     */
    public static synchronized String baseUri() {
        if (baseUri == null) {
            String configured = System.getProperty("playground.baseUri", System.getenv("PLAYGROUND_BASE_URI"));
            if (configured != null && !configured.isBlank()) {
                baseUri = configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
                port = URI.create(baseUri).getPort();
            } else {
                ConfigurableApplicationContext context = SpringApplication.run(PlaygroundApplication.class,
                        "--server.port=0", "--management.server.port=0", "--server.address=127.0.0.1",
                        "--spring.main.banner-mode=off");
                Runtime.getRuntime().addShutdownHook(new Thread(context::close));
                port = Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
                baseUri = "http://localhost:" + port;
            }
        }
        return baseUri;
    }

    /**
     * The full URL of {@code path} on the playground.
     */
    public static String url(String path) {
        return baseUri() + path;
    }

    /**
     * The same server under its other loopback name: a different "site" to the browser.
     */
    public static String otherHostUrl(String path) {
        baseUri();
        return "http://127.0.0.1:" + port + path;
    }

    /**
     * The playground's port.
     */
    public static int port() {
        baseUri();
        return port;
    }
}
