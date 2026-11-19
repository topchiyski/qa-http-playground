package io.qaplayground.course.support;

import io.qaplayground.server.PlaygroundApplication;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Points REST Assured at a playground, so every lesson can start with {@code given()}.
 *
 * <p>This is plumbing. You don't need to read it to learn REST Assured.
 *
 * <p>By default the tests start their own playground inside the test JVM, once, on a free port. To use a server you
 * started yourself, pass {@code -Dplayground.baseUri=http://localhost:8080} or set {@code PLAYGROUND_BASE_URI}.
 */
public abstract class CourseTest {

    private static String baseUri;
    private static boolean inProcess;

    /**
     * Runs before each test class: sets the base URI and logs the full exchange when a check fails.
     *
     * <p>That log is the single most useful thing when a test goes red. You see what was sent and what came back,
     * without rerunning anything.
     */
    @BeforeAll
    static void pointRestAssuredAtPlayground() {
        RestAssured.baseURI = baseUri();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    /**
     * The playground's address, without a trailing slash. The first call decides where it is and starts it if needed.
     */
    public static synchronized String baseUri() {
        if (baseUri == null) {
            String configured = System.getProperty("playground.baseUri", System.getenv("PLAYGROUND_BASE_URI"));
            inProcess = configured == null || configured.isBlank();
            baseUri = inProcess ? startInProcess() : configured.replaceAll("/+$", "");
        }
        return baseUri;
    }

    /**
     * Whether the tests started their own playground, rather than using one you configured.
     */
    public static synchronized boolean startedInProcess() {
        baseUri();
        return inProcess;
    }

    /**
     * Starts the playground on a free port, bound to this machine only, and stops it when the JVM exits.
     */
    private static String startInProcess() {
        ConfigurableApplicationContext context = SpringApplication.run(PlaygroundApplication.class,
                "--server.port=0", "--management.server.port=0", "--server.address=127.0.0.1",
                "--spring.main.banner-mode=off", "--logging.level.root=WARN");
        Runtime.getRuntime().addShutdownHook(new Thread(context::close));
        return "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port");
    }
}
