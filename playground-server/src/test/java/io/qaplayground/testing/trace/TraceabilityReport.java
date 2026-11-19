package io.qaplayground.testing.trace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.qaplayground.testing.Endpoints;
import io.qaplayground.testing.Endpoints.Route;

/**
 * Builds docs/testing/TRACEABILITY.md from the JSON lines {@link TraceExtension} wrote.
 *
 * <p>Usage: {@code TraceabilityReport <trace-dir> <output.md>}. Maven runs it at the end of playground-server's
 * {@code verify}, after every test level has written its records. The matrix comes from what ran, so it can't drift
 * from the code.
 *
 * <p>Gaps (a route with no API-level test, or a technique whose home level ran without a test) are printed and
 * listed in the report.
 */
public final class TraceabilityReport {

    /**
     * The test levels in pyramid order, with their display names.
     */
    static final Map<String, String> LEVELS = new LinkedHashMap<>();

    /**
     * A technique: its tag, its name, and the levels where it is expected to appear.
     */
    record Technique(String tag, String name, Set<String> homeLevels) {
    }

    /**
     * Every technique the report tracks. A technique with no test at a home level that ran is a gap.
     */
    static final List<Technique> TECHNIQUES = List.of(
            new Technique("tech:ep", "Equivalence partitioning", Set.of("unit", "api")),
            new Technique("tech:bva", "Boundary value analysis", Set.of("unit", "api")),
            new Technique("tech:decision-table", "Decision table", Set.of("api")),
            new Technique("tech:state-transition", "State transition", Set.of("api", "e2e")),
            new Technique("tech:error-guessing", "Error guessing", Set.of("unit", "api")),
            new Technique("tech:checklist", "Checklist-based", Set.of("api")),
            new Technique("tech:exploratory", "Exploratory (regression of findings)", Set.of("api")),
            new Technique("tech:atdd", "Acceptance criteria / ATDD", Set.of("e2e")),
            new Technique("tech:use-case", "Use case", Set.of("e2e")),
            new Technique("tech:pairwise", "Pairwise", Set.of("api")),
            new Technique("tech:classification-tree", "Classification tree", Set.of("api")),
            new Technique("tech:property", "Property-based", Set.of("unit")),
            new Technique("type:security", "Security (test type)", Set.of("api")));

    static {
        LEVELS.put("unit", "Unit");
        LEVELS.put("integration", "Integration");
        LEVELS.put("api", "API");
        LEVELS.put("e2e", "E2E");
    }

    /**
     * One executed test, as {@link TraceExtension} recorded it.
     */
    record Execution(String level, String module, String clazz, String method, String display,
                     List<String> tags, List<String> paths, String outcome) {

        /**
         * {@code Class#method}: the same for every run of a parameterized test.
         */
        String testId() {
            return clazz + "#" + method;
        }
    }

    private TraceabilityReport() {
    }

    /**
     * Writes the report and prints the gaps.
     *
     * <p>It runs inside the Maven JVM ({@code exec:java}), so it reports failure by throwing, never by
     * {@code System.exit}.
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException("usage: TraceabilityReport <trace-dir> <output.md>");
        }
        Path traceDir = Path.of(args[0]);
        Path out = Path.of(args[1]);

        List<Execution> executions = load(traceDir);
        Report report = new Report(executions);
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, report.markdown(), StandardCharsets.UTF_8);
        System.out.println("[traceability] " + executions.size() + " test executions from levels "
                + report.levelsRun() + " -> " + out);
        List<String> gaps = report.gaps();
        gaps.forEach(g -> System.out.println("[traceability] GAP: " + g));
    }

    /**
     * Reads every {@code <level>.jsonl} file in the trace directory, one per test level that ran.
     */
    static List<Execution> load(Path traceDir) throws IOException {
        List<Execution> all = new ArrayList<>();
        if (!Files.isDirectory(traceDir)) {
            return all;
        }
        List<Path> files;
        try (Stream<Path> list = Files.list(traceDir)) {
            files = list.filter(f -> f.toString().endsWith(".jsonl")).sorted().toList();
        }
        for (Path f : files) {
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    all.add(parse(line));
                }
            }
        }
        return all;
    }

    // --- report -------------------------------------------------------------------------------

    /**
     * The matrix and its gaps, computed from a list of executions.
     */
    static final class Report {
        private final List<Execution> executions;

        Report(List<Execution> executions) {
            this.executions = executions;
        }

        /**
         * The levels that ran in this build, in pyramid order.
         */
        Set<String> levelsRun() {
            return executions.stream().map(Execution::level)
                    .collect(Collectors.toCollection(() -> new TreeSet<>(levelOrder())));
        }

        /**
         * Orders levels as in {@link #LEVELS}; unknown levels go last, in alphabetical order.
         */
        private static java.util.Comparator<String> levelOrder() {
            List<String> order = new ArrayList<>(LEVELS.keySet());
            return java.util.Comparator.comparingInt((String l) -> order.indexOf(l) < 0 ? 99 : order.indexOf(l))
                    .thenComparing(l -> l);
        }

        /**
         * Distinct test methods per key. A parameterized test counts once.
         */
        private Map<String, Set<String>> testsBy(java.util.function.Function<Execution, List<String>> keys) {
            Map<String, Set<String>> map = new TreeMap<>();
            for (Execution e : executions) {
                for (String k : keys.apply(e)) {
                    map.computeIfAbsent(k, x -> new TreeSet<>()).add(e.level() + "|" + e.testId());
                }
            }
            return map;
        }

        /**
         * What is missing: routes without an API test, techniques without a test at a level that ran, and failed tests.
         */
        List<String> gaps() {
            List<String> gaps = new ArrayList<>();
            Set<String> run = levelsRun();
            if (run.contains("api")) {
                Map<String, Set<String>> byRoute = testsByRoute();
                for (Route r : Endpoints.ROUTES) {
                    boolean hasApi = byRoute.getOrDefault(r.template(), Set.of()).stream().anyMatch(t -> t.startsWith("api|"));
                    if (!hasApi) {
                        gaps.add("route " + r.template() + " has no API-level test");
                    }
                }
            }
            Map<String, Set<String>> byTag = testsBy(Execution::tags);
            for (Technique t : TECHNIQUES) {
                boolean homeRan = t.homeLevels().stream().anyMatch(run::contains);
                if (homeRan && byTag.getOrDefault(t.tag(), Set.of()).isEmpty()) {
                    gaps.add("technique " + t.name() + " (" + t.tag() + ") has no test although level(s) "
                            + t.homeLevels() + " ran");
                }
            }
            long failed = executions.stream().filter(e -> e.outcome().equals("failed")).count();
            if (failed > 0) {
                gaps.add(failed + " test execution(s) failed");
            }
            return gaps;
        }

        /**
         * Distinct test methods per route template, from the paths each test touched.
         */
        private Map<String, Set<String>> testsByRoute() {
            return testsBy(e -> e.paths().stream()
                    .map(Endpoints::routeFor)
                    .flatMap(java.util.Optional::stream)
                    .map(Route::template)
                    .distinct()
                    .toList());
        }

        /**
         * The whole report as Markdown: tests per level, techniques by level, endpoints by level, and the gaps.
         */
        String markdown() {
            Set<String> run = levelsRun();
            StringBuilder md = new StringBuilder();
            md.append("# Traceability matrix\n\n");
            md.append("_Generated by `TraceabilityReport` (playground-server tests) from the tests that actually ran; do not edit by hand. ")
                    .append("Regenerate with `./mvnw verify -Dtraceability.write=true`. ")
                    .append("Levels in this run: ").append(String.join(", ", run.stream().map(LEVELS::get).toList()))
                    .append(". Levels not listed were skipped (for example E2E without a browser)._\n\n");

            md.append("## Tests per level\n\n");
            md.append("| Level | Test classes | Test methods | Executions | Failed |\n|---|---:|---:|---:|---:|\n");
            for (String level : LEVELS.keySet()) {
                List<Execution> at = executions.stream().filter(e -> e.level().equals(level)).toList();
                if (at.isEmpty()) {
                    md.append("| ").append(LEVELS.get(level)).append(" | not run | | | |\n");
                    continue;
                }
                md.append("| ").append(LEVELS.get(level))
                        .append(" | ").append(at.stream().map(Execution::clazz).distinct().count())
                        .append(" | ").append(at.stream().map(Execution::testId).distinct().count())
                        .append(" | ").append(at.size())
                        .append(" | ").append(at.stream().filter(e -> e.outcome().equals("failed")).count())
                        .append(" |\n");
            }

            md.append("\n## Test design techniques × level\n\n");
            md.append("Number of test methods carrying the technique's tag. Derivations: [TEST-DESIGN.md](TEST-DESIGN.md).\n\n");
            md.append("| Technique | Tag |");
            LEVELS.values().forEach(l -> md.append(' ').append(l).append(" |"));
            md.append("\n|---|---|");
            LEVELS.keySet().forEach(l -> md.append("---:|"));
            md.append('\n');
            for (Technique t : TECHNIQUES) {
                md.append("| ").append(t.name()).append(" | `").append(t.tag()).append("` |");
                for (String level : LEVELS.keySet()) {
                    long n = executions.stream().filter(e -> e.level().equals(level) && e.tags().contains(t.tag()))
                            .map(Execution::testId).distinct().count();
                    md.append(' ').append(run.contains(level) ? (n == 0 ? "–" : String.valueOf(n)) : "n/r").append(" |");
                }
                md.append('\n');
            }

            md.append("\n## Endpoints × level\n\n");
            md.append("Number of test methods that sent at least one request to the route, and the techniques used at API level.\n\n");
            md.append("| Group | Route |");
            List<String> httpLevels = List.of("integration", "api", "e2e");
            httpLevels.forEach(l -> md.append(' ').append(LEVELS.get(l)).append(" |"));
            md.append(" API techniques |\n|---|---|");
            httpLevels.forEach(l -> md.append("---:|"));
            md.append("---|\n");
            Map<String, Set<String>> byRoute = testsByRoute();
            for (Route r : Endpoints.ROUTES) {
                Set<String> tests = byRoute.getOrDefault(r.template(), Set.of());
                md.append("| ").append(r.group()).append(" | `").append(r.template()).append("` |");
                for (String level : httpLevels) {
                    long n = tests.stream().filter(t -> t.startsWith(level + "|")).count();
                    md.append(' ').append(run.contains(level) ? (n == 0 ? "**0**" : String.valueOf(n)) : "n/r").append(" |");
                }
                Set<String> techniques = new TreeSet<>();
                for (Execution e : executions) {
                    if (e.level().equals("api") && tests.contains("api|" + e.testId())) {
                        e.tags().stream().filter(tag -> tag.startsWith("tech:")).map(tag -> tag.substring(5)).forEach(techniques::add);
                    }
                }
                md.append(' ').append(String.join(", ", techniques)).append(" |\n");
            }

            List<String> gaps = gaps();
            md.append("\n## Gaps\n\n");
            if (gaps.isEmpty()) {
                md.append("None: every route has an API-level test and every technique whose level ran has at least one test.\n");
            } else {
                gaps.forEach(g -> md.append("- ").append(g).append('\n'));
            }
            md.append("\n`n/r` = level not run in this report's build. `–` = no test with this tag at this level.\n");
            return md.toString();
        }
    }

    // --- minimal JSON for the flat objects TraceExtension writes -------------------------------

    /**
     * Parses one line of a trace file.
     */
    static Execution parse(String json) {
        Map<String, Object> m = new Json(json).object();
        return new Execution(str(m, "level"), str(m, "module"), str(m, "class"), str(m, "method"),
                str(m, "display"), list(m, "tags"), list(m, "paths"), str(m, "outcome"));
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? "" : (String) v;
    }

    @SuppressWarnings("unchecked")
    private static List<String> list(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? List.of() : (List<String>) v;
    }

    /**
     * Just enough JSON parsing for the flat objects {@link TraceExtension} writes: strings and arrays of strings.
     */
    private static final class Json {
        private final String s;
        private int i;

        Json(String s) {
            this.s = s;
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            expect('{');
            skipWs();
            if (peek() == '}') {
                i++;
                return m;
            }
            while (true) {
                skipWs();
                String key = string();
                skipWs();
                expect(':');
                skipWs();
                m.put(key, peek() == '[' ? array() : string());
                skipWs();
                if (peek() == ',') {
                    i++;
                    continue;
                }
                expect('}');
                return m;
            }
        }

        private List<String> array() {
            List<String> list = new ArrayList<>();
            expect('[');
            skipWs();
            if (peek() == ']') {
                i++;
                return list;
            }
            while (true) {
                skipWs();
                list.add(string());
                skipWs();
                if (peek() == ',') {
                    i++;
                    continue;
                }
                expect(']');
                return list;
            }
        }

        private String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private char peek() {
            return s.charAt(i);
        }

        private void expect(char c) {
            if (s.charAt(i) != c) {
                throw new IllegalArgumentException("expected '" + c + "' at " + i + " in " + s);
            }
            i++;
        }

        private void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }
    }
}
