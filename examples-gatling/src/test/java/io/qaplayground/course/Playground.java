package io.qaplayground.course;

import io.gatling.javaapi.http.HttpProtocolBuilder;

import static io.gatling.javaapi.http.HttpDsl.http;

/**
 * Settings that every simulation shares.
 *
 * <p>With these in one place, each lesson can focus on the one idea it teaches. Override them on the command line:
 * {@code -DbaseUrl=...}, {@code -Dusers=...}, {@code -Dminutes=...}.
 */
public final class Playground {

    private Playground() {
    }

    /**
     * Where the playground runs: {@code http://localhost:8080}, unless {@code -DbaseUrl} says otherwise.
     */
    public static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8080");

    /**
     * The protocol every simulation uses: the base URL, plus the headers each virtual user sends.
     */
    public static HttpProtocolBuilder protocol() {
        return http
                .baseUrl(BASE_URL)
                .acceptHeader("application/json")
                .userAgentHeader("qa-http-playground-gatling");
    }

    /**
     * Reads a whole-number system property, such as {@code -Dusers=50}.
     *
     * <p>Returns {@code defaultValue} when the property is missing or 0, so a lesson always runs at a sensible size.
     */
    public static int intProperty(String name, int defaultValue) {
        int value = Integer.getInteger(name, 0);
        return value > 0 ? value : defaultValue;
    }
}
