package io.qaplayground.api.design;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.qaplayground.api.support.PlaygroundTest;
import io.qaplayground.testing.technique.Pairwise;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pairwise testing of {@code /drip} (docs/testing/TEST-DESIGN.md section 10).
 *
 * <p>Four parameters give 3 x 3 x 3 x 2 = 54 combinations; 9 rows cover every pair. The rows come from the model
 * in {@code design/drip.pict}. Before they run, the test checks that they really cover every pair, so a hand-edited
 * CSV can't quietly lose coverage.
 */
@Pairwise
class DripPairwiseTest extends PlaygroundTest {

    /**
     * Fails the class unless the CSV rows cover every pair of values of every two parameters in the model.
     */
    @BeforeAll
    static void rowsCoverEveryPairOfTheModel() throws IOException {
        Map<String, List<String>> model = new LinkedHashMap<>();
        for (String line : read("/api/design/drip.pict")) {
            if (!line.isBlank() && !line.startsWith("#")) {
                String[] kv = line.split(":", 2);
                model.put(kv[0].trim(), List.of(kv[1].trim().split("\\s*,\\s*")));
            }
        }
        List<String> csv = read("/api/design/drip.csv");
        List<String> header = List.of(csv.getFirst().split(","));
        assertThat(header).containsExactlyElementsOf(model.keySet());
        Set<String> covered = new HashSet<>();
        for (String row : csv.subList(1, csv.size())) {
            String[] v = row.split(",");
            for (int i = 0; i < v.length; i++) {
                for (int j = i + 1; j < v.length; j++) {
                    covered.add(i + "=" + v[i] + "&" + j + "=" + v[j]);
                }
            }
        }
        List<String> missing = new ArrayList<>();
        List<List<String>> values = new ArrayList<>(model.values());
        for (int i = 0; i < values.size(); i++) {
            for (int j = i + 1; j < values.size(); j++) {
                for (String a : values.get(i)) {
                    for (String b : values.get(j)) {
                        if (!covered.contains(i + "=" + a + "&" + j + "=" + b)) {
                            missing.add(header.get(i) + "=" + a + " with " + header.get(j) + "=" + b);
                        }
                    }
                }
            }
        }
        assertThat(missing).as("pairs not covered by drip.csv").isEmpty();
    }

    /**
     * The non-comment lines of a test resource.
     */
    private static List<String> read(String resource) throws IOException {
        try (InputStream in = DripPairwiseTest.class.getResourceAsStream(resource)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().filter(l -> !l.isBlank()).toList();
        }
    }

    /**
     * One row: the status, exactly {@code numbytes} asterisks with a matching {@code Content-Length}, and a wait of
     * at least {@code delay}.
     */
    @ParameterizedTest(name = "duration={0} numbytes={1} code={2} delay={3}")
    @CsvFileSource(resources = "/api/design/drip.csv", numLinesToSkip = 1)
    void dripRow(String duration, int numbytes, int code, String delay) {
        long start = System.nanoTime();
        Response r = given()
                .queryParam("duration", duration).queryParam("numbytes", numbytes)
                .queryParam("code", code).queryParam("delay", delay)
                .get("/drip");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(r.statusCode()).isEqualTo(code);
        assertThat(r.asByteArray()).hasSize(numbytes).containsOnly((byte) '*');
        assertThat(r.header("Content-Length")).isEqualTo(String.valueOf(numbytes));
        // Lower bound only: the initial delay always happens; upper bounds would make the test flaky.
        assertThat(elapsedMs).isGreaterThanOrEqualTo(millis(delay) - 20);
    }

    /**
     * A Go duration such as {@code 300ms} or {@code 1s}, in milliseconds.
     */
    private static long millis(String goDuration) {
        return goDuration.equals("0") ? 0 : Long.parseLong(goDuration.replace("ms", ""));
    }
}
