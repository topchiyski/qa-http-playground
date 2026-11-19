package io.qaplayground.server;

import java.util.Map;

import io.qaplayground.server.config.PlaygroundProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * Binds {@link PlaygroundProperties} exactly as Spring Boot does, without starting a context.
 *
 * <p>Unit tests get real, defaulted configuration in microseconds instead of seconds.
 */
public final class TestProperties {

    private TestProperties() {
    }

    /**
     * The configuration with every default, as an empty {@code application.yml} would give.
     */
    public static PlaygroundProperties defaults() {
        return with(Map.of());
    }

    /**
     * The defaults plus some overrides, such as {@code with(Map.of("playground.exclude-headers", "x-fly-*"))}.
     */
    public static PlaygroundProperties with(Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bindOrCreate("playground", PlaygroundProperties.class);
    }
}
