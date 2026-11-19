package io.qaplayground.server.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads bundled files from the classpath once and keeps them in memory, so each request costs no disk access.
 */
public final class StaticAssets {

    private static final Map<String, byte[]> CACHE = new ConcurrentHashMap<>();

    private StaticAssets() {
    }

    /**
     * The bytes of {@code classpath:/<path>}, or {@code null} if it doesn't exist.
     */
    public static byte[] find(String path) {
        return CACHE.computeIfAbsent(path, StaticAssets::load);
    }

    /**
     * Like {@link #find(String)}, but the asset must exist. A missing one is a packaging bug.
     */
    public static byte[] get(String path) {
        byte[] bytes = find(path);
        if (bytes == null) {
            throw new IllegalStateException("Missing bundled asset: " + path);
        }
        return bytes;
    }

    /**
     * Reads a classpath resource fully, or returns null when it doesn't exist.
     */
    private static byte[] load(String path) {
        try (InputStream in = StaticAssets.class.getClassLoader().getResourceAsStream(path)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
