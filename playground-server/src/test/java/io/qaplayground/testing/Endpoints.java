package io.qaplayground.testing;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The playground's public routes: the inventory every test level shares.
 *
 * <p>Three things depend on it. The conformance checklist checks every route on it. The traceability report wants a
 * test for every route on it. And an integration test compares it with the routes the server really registers, so
 * the list can't quietly drift away from the code.
 */
public final class Endpoints {

    /**
     * Routes that accept any method, such as {@code /anything}.
     */
    private static final Set<String> ALL = Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE");
    /**
     * Read-only routes: GET, plus the HEAD and OPTIONS that come with it.
     */
    private static final Set<String> GET = Set.of("GET", "HEAD", "OPTIONS");

    /**
     * One route of the inventory.
     *
     * @param template the path as the server maps it, such as {@code /status/{code}}
     * @param methods  methods the route accepts ({@code OPTIONS} is always answered by the CORS filter)
     * @param sample   a concrete path that returns a 2xx response to the first method in {@code methods}
     * @param group    the endpoint group, as in docs/endpoints/
     */
    public record Route(String template, Set<String> methods, String sample, String group) {

        /**
         * The method to use for a sample request: GET when allowed, else the first one listed.
         */
        public String sampleMethod() {
            if (methods.contains("GET")) {
                return "GET";
            }
            return methods.stream().filter(m -> !m.equals("OPTIONS")).sorted().findFirst().orElseThrow();
        }

        /**
         * Whether a concrete path, such as {@code /status/418}, belongs to this route.
         */
        public boolean matches(String path) {
            return pattern().matcher(path).matches();
        }

        /**
         * The template as a regular expression: each placeholder matches one path segment.
         */
        private Pattern pattern() {
            String regex = template.equals("/anything/**") ? "/anything/.+"
                    : template.replaceAll("\\{[^}]+}", "[^/]+");
            return Pattern.compile(regex);
        }

        @Override
        public String toString() {
            return template;
        }
    }

    /**
     * Shorthand that keeps the inventory below readable as a table.
     */
    private static Route r(String template, Set<String> methods, String sample, String group) {
        return new Route(template, methods, sample, group);
    }

    /**
     * A route for one method, plus the OPTIONS every route answers.
     */
    private static Set<String> only(String method) {
        return Set.of(method, "OPTIONS");
    }

    /**
     * Every public route, in the order of docs/endpoints/.
     */
    public static final List<Route> ROUTES = List.of(
            r("/", GET, "/", "misc"),
            r("/version", ALL, "/version", "misc"),
            r("/robots.txt", ALL, "/robots.txt", "misc"),
            r("/deny", ALL, "/deny", "misc"),
            r("/forms/post", GET, "/forms/post", "misc"),

            r("/anything", ALL, "/anything", "inspection"),
            r("/anything/**", ALL, "/anything/a/b", "inspection"),
            r("/get", GET, "/get", "inspection"),
            r("/headers", ALL, "/headers", "inspection"),
            r("/ip", ALL, "/ip", "inspection"),
            r("/user-agent", ALL, "/user-agent", "inspection"),
            r("/hostname", ALL, "/hostname", "inspection"),
            r("/dump/request", ALL, "/dump/request", "inspection"),
            r("/env", ALL, "/env", "inspection"),
            r("/uuid", ALL, "/uuid", "inspection"),

            r("/post", only("POST"), "/post", "methods"),
            r("/put", only("PUT"), "/put", "methods"),
            r("/patch", only("PATCH"), "/patch", "methods"),
            r("/delete", only("DELETE"), "/delete", "methods"),
            r("/head", Set.of("HEAD"), "/head", "methods"),
            r("/upload", Set.of("POST", "PUT", "PATCH", "OPTIONS"), "/upload", "methods"),

            r("/status/{code}", ALL, "/status/200", "status"),
            r("/unstable", ALL, "/unstable?failure_rate=0", "status"),

            r("/redirect/{n}", ALL, "/redirect/1", "redirects"),
            r("/absolute-redirect/{n}", ALL, "/absolute-redirect/1", "redirects"),
            r("/relative-redirect/{n}", ALL, "/relative-redirect/1", "redirects"),
            r("/redirect-to", ALL, "/redirect-to?url=/get", "redirects"),

            r("/basic-auth/{user}/{password}", ALL, "/basic-auth/u/p", "auth"),
            r("/hidden-basic-auth/{user}/{password}", ALL, "/hidden-basic-auth/u/p", "auth"),
            r("/bearer", ALL, "/bearer", "auth"),
            r("/digest-auth/{qop}/{user}/{password}", ALL, "/digest-auth/auth/u/p", "auth"),
            r("/digest-auth/{qop}/{user}/{password}/{algorithm}", ALL, "/digest-auth/auth/u/p/MD5", "auth"),

            r("/cache", ALL, "/cache", "caching"),
            r("/cache/{seconds}", ALL, "/cache/60", "caching"),
            r("/etag/{etag}", ALL, "/etag/abc", "caching"),

            r("/cookies", ALL, "/cookies", "cookies"),
            r("/cookies/set", ALL, "/cookies/set?a=1", "cookies"),
            r("/cookies/delete", ALL, "/cookies/delete?a", "cookies"),

            r("/json", ALL, "/json", "content"),
            r("/xml", ALL, "/xml", "content"),
            r("/html", ALL, "/html", "content"),
            r("/encoding/utf8", GET, "/encoding/utf8", "content"),
            r("/gzip", ALL, "/gzip", "content"),
            r("/deflate", ALL, "/deflate", "content"),
            r("/brotli", ALL, "/brotli", "content"),
            r("/base64/{data}", ALL, "/base64/aGVsbG8=", "content"),
            r("/base64/{operation}/{data}", ALL, "/base64/decode/aGVsbG8=", "content"),
            r("/links/{n}", ALL, "/links/3", "content"),
            r("/links/{n}/{offset}", ALL, "/links/3/1", "content"),
            r("/response-headers", ALL, "/response-headers?X-Test=1", "content"),

            r("/image", ALL, "/image", "images"),
            r("/image/{kind}", ALL, "/image/png", "images"),

            r("/bytes/{n}", ALL, "/bytes/16", "bytes-and-ranges"),
            r("/stream-bytes/{n}", ALL, "/stream-bytes/16", "bytes-and-ranges"),
            r("/range/{n}", ALL, "/range/26", "bytes-and-ranges"),

            r("/delay/{duration}", ALL, "/delay/0", "timing-and-streaming"),
            r("/drip", ALL, "/drip?duration=0&numbytes=2&delay=0", "timing-and-streaming"),
            r("/stream/{n}", ALL, "/stream/2", "timing-and-streaming"),
            r("/sse", ALL, "/sse?duration=1ms&count=2&delay=0", "timing-and-streaming"),
            r("/jsonl", ALL, "/jsonl?count=2", "timing-and-streaming"),
            r("/trailers", ALL, "/trailers?X-Test=1", "timing-and-streaming"),

            r("/websocket/echo", Set.of("GET"), "/websocket/echo", "websocket"));

    private Endpoints() {
    }

    /**
     * The most specific route that matches a concrete path. The query string is ignored.
     */
    public static Optional<Route> routeFor(String path) {
        int q = path.indexOf('?');
        String p = q >= 0 ? path.substring(0, q) : path;
        return ROUTES.stream()
                .filter(r -> r.matches(p))
                .max((a, b) -> Integer.compare(specificity(a.template()), specificity(b.template())));
    }

    /**
     * How specific a template is. Literal characters count more than placeholders, so {@code /image/png} picks
     * {@code /image/{kind}}, and {@code /anything} beats {@code /anything/**}.
     */
    private static int specificity(String template) {
        return template.replaceAll("\\{[^}]+}", "").replace("**", "").length();
    }
}
