package io.qaplayground.api.support;

import io.qaplayground.server.PlaygroundApplication;
import org.junit.jupiter.api.Assumptions;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * A second in-process playground with <b>lowered limits</b>, for boundary value tests.
 *
 * <p>Boundary tests need to know the server's limits, and need them to be cheap to reach. Probing a 10-second limit
 * costs 30 seconds per boundary; a 1-second limit costs 3. The code paths are the same; only the configured numbers
 * change (docs/testing/TEST-DESIGN.md section 2).
 *
 * <p>When the suite runs against an external server ({@code -Dplayground.baseUri}), its limits are unknown. Boundary
 * tests are then skipped with a message, rather than guessing.
 */
public final class LimitedPlayground {

    /**
     * The lowered {@code max-duration}: 1 second.
     */
    public static final long MAX_DURATION_MS = 1000;
    /**
     * The lowered {@code max-body-size}: 1 KiB.
     */
    public static final int MAX_BODY_BYTES = 1024;
    /**
     * The lowered {@code max-stream-lines}.
     */
    public static final int MAX_STREAM_LINES = 5;
    /**
     * The lowered {@code max-links}.
     */
    public static final int MAX_LINKS = 5;
    /**
     * The lowered {@code max-redirects}.
     */
    public static final int MAX_REDIRECTS = 3;

    private static String baseUri;

    private LimitedPlayground() {
    }

    /**
     * The address of the limited playground, started on first use. Skips the calling test when the suite targets an
     * external server.
     */
    public static synchronized String baseUri() {
        Assumptions.assumeTrue(PlaygroundTarget.isInProcess(),
                "boundary tests need the in-process server with known, lowered limits; skipped for an external baseUri");
        if (baseUri == null) {
            ConfigurableApplicationContext context = SpringApplication.run(PlaygroundApplication.class,
                    "--server.port=0", "--management.server.port=0", "--server.address=127.0.0.1",
                    "--spring.main.banner-mode=off",
                    "--playground.max-duration=" + MAX_DURATION_MS + "ms",
                    "--playground.max-body-size=" + MAX_BODY_BYTES + "B",
                    "--playground.max-stream-lines=" + MAX_STREAM_LINES,
                    "--playground.max-links=" + MAX_LINKS,
                    "--playground.max-redirects=" + MAX_REDIRECTS);
            Runtime.getRuntime().addShutdownHook(new Thread(context::close));
            baseUri = "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port");
        }
        return baseUri;
    }
}
