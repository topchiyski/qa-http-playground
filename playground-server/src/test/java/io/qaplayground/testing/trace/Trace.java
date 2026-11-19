package io.qaplayground.testing.trace;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Collects the paths the running test touched.
 *
 * <p>HTTP clients call {@link #path}: REST Assured through a filter, Selenium through the page objects, raw clients
 * by hand. When the test ends, {@link TraceExtension} writes the paths next to the test's tags.
 *
 * <p>Tests run one at a time within a JVM in this project, so one static slot is enough. Recording does nothing
 * unless the build set {@code trace.dir}.
 */
public final class Trace {

    /**
     * The paths the current test has touched so far, in order, without duplicates.
     */
    private static final Set<String> CURRENT = new LinkedHashSet<>();

    private Trace() {
    }

    /**
     * Records a path ({@code /status/418}) or a full URL ({@code http://host:8080/status/418?x=1}).
     */
    public static synchronized void path(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) {
            return;
        }
        String path = pathOrUrl;
        if (pathOrUrl.contains("://")) {
            try {
                path = URI.create(pathOrUrl.replace(" ", "%20")).getRawPath();
            } catch (IllegalArgumentException e) {
                return;
            }
        } else {
            int q = path.indexOf('?');
            if (q >= 0) {
                path = path.substring(0, q);
            }
        }
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        CURRENT.add(path);
    }

    /**
     * Returns the paths recorded so far and starts a new, empty list.
     */
    static synchronized Set<String> drain() {
        Set<String> copy = new LinkedHashSet<>(CURRENT);
        CURRENT.clear();
        return copy;
    }

    /**
     * Forgets everything recorded so far, before a test starts.
     */
    static synchronized void reset() {
        CURRENT.clear();
    }
}
