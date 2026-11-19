package io.qaplayground.api.support;

import io.qaplayground.server.PlaygroundApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Decides which server the examples talk to.
 * <ul>
 *   <li>{@code -Dplayground.baseUri=http://localhost:8080}: use a server you already started
 *       ({@code ./mvnw spring-boot:run}, {@code java -jar}, a lab machine...).</li>
 *   <li>Nothing set: start the playground inside this JVM on a free port, once, and share it between all test
 *       classes. This is the default.</li>
 * </ul>
 * You don't need to understand this class to learn REST Assured. It is plumbing.
 */
public final class PlaygroundTarget {

    private static String baseUri;
    private static boolean inProcess;

    private PlaygroundTarget() {
    }

    /**
     * The playground's address, without a trailing slash. The first call decides, and starts it if needed.
     */
    public static synchronized String baseUri() {
        if (baseUri == null) {
            String configured = System.getProperty("playground.baseUri", System.getenv("PLAYGROUND_BASE_URI"));
            inProcess = configured == null || configured.isBlank();
            baseUri = inProcess ? startInProcess() : stripSlash(configured);
        }
        return baseUri;
    }

    /**
     * Whether the tests started the server themselves, and therefore know its configuration.
     */
    public static synchronized boolean isInProcess() {
        baseUri();
        return inProcess;
    }

    /**
     * Starts the playground on a free loopback port, and stops it when the JVM exits.
     */
    private static String startInProcess() {
        ConfigurableApplicationContext context = SpringApplication.run(PlaygroundApplication.class,
                "--server.port=0", "--management.server.port=0", "--server.address=127.0.0.1",
                "--spring.main.banner-mode=off", "--HTTPBIN_ENV_GREETING=hello");
        Runtime.getRuntime().addShutdownHook(new Thread(context::close));
        String port = context.getEnvironment().getProperty("local.server.port");
        return "http://127.0.0.1:" + port;
    }

    /**
     * The address without trailing slashes, so paths can be appended as they are.
     */
    private static String stripSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
