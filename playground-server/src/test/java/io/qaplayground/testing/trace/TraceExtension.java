package io.qaplayground.testing.trace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

/**
 * Writes one JSON line per executed test to {@code ${trace.dir}/${trace.level}.jsonl}.
 *
 * <p>Each line holds the level, class, method, display name, tags (the techniques), the paths the test touched, and
 * the outcome. At the end of playground-server's {@code verify}, {@link TraceabilityReport} turns these lines into
 * docs/testing/TRACEABILITY.md.
 *
 * <p>JUnit finds it through {@code META-INF/services} when the build enables
 * {@code junit.jupiter.extensions.autodetection.enabled}. It does nothing unless {@code trace.dir} is set.
 */
public class TraceExtension implements BeforeEachCallback, TestWatcher {

    /**
     * Guards the trace file and the "started afresh" flag, so two writes never interleave.
     */
    private static final Object LOCK = new Object();
    private static boolean truncated;

    /**
     * Starts each test with an empty list of paths.
     */
    @Override
    public void beforeEach(ExtensionContext context) {
        Trace.reset();
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        write(context, "passed");
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        write(context, "failed");
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        write(context, "aborted");
    }

    @Override
    public void testDisabled(ExtensionContext context, Optional<String> reason) {
        write(context, "disabled");
    }

    /**
     * Appends one line for this test. The first write of a run starts the file afresh, so reruns don't count twice.
     */
    private static void write(ExtensionContext context, String outcome) {
        String dir = System.getProperty("trace.dir");
        Set<String> paths = Trace.drain();
        if (dir == null || dir.isBlank()) {
            return;
        }
        String level = System.getProperty("trace.level", "unknown");
        String line = "{"
                + field("level", level) + ","
                + field("module", System.getProperty("trace.module", "")) + ","
                + field("class", context.getRequiredTestClass().getName()) + ","
                + field("method", context.getTestMethod().map(m -> m.getName()).orElse("")) + ","
                + field("display", context.getDisplayName()) + ","
                + array("tags", new TreeSet<>(context.getTags())) + ","
                + array("paths", paths) + ","
                + field("outcome", outcome)
                + "}\n";
        Path file = Path.of(dir, level + ".jsonl");
        synchronized (LOCK) {
            try {
                Files.createDirectories(file.getParent());
                if (!truncated) {
                    // One file per level and JVM run: start fresh so re-runs without `clean` don't double count.
                    Files.writeString(file, "", StandardCharsets.UTF_8);
                    truncated = true;
                }
                Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * One {@code "name":"value"} JSON field.
     */
    private static String field(String name, String value) {
        return quote(name) + ":" + quote(value);
    }

    /**
     * One {@code "name":["a","b"]} JSON field.
     */
    private static String array(String name, Collection<String> values) {
        StringBuilder sb = new StringBuilder(quote(name)).append(":[");
        boolean first = true;
        for (String v : values) {
            if (!first) {
                sb.append(',');
            }
            sb.append(quote(v));
            first = false;
        }
        return sb.append(']').toString();
    }

    /**
     * A JSON string literal, with quotes, backslashes and control characters escaped.
     */
    static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
