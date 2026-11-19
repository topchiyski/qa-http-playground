package io.qaplayground.e2e.journeys;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.deque.html.axecore.results.Results;
import com.deque.html.axecore.results.Rule;
import com.deque.html.axecore.selenium.AxeBuilder;
import io.qaplayground.e2e.support.E2eTest;
import io.qaplayground.e2e.support.Playground;
import io.qaplayground.testing.technique.ChecklistBased;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openqa.selenium.WebDriver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey 9: accessibility (risk R16).
 *
 * <p>axe-core checks the pages against WCAG 2.1 A and AA rules: a checklist applied by a machine. The build fails on
 * serious or critical violations that aren't on the reviewed baseline ({@code a11y-baseline.txt}, one
 * {@code page rule-id reason} per line). Known third-party issues stay visible but don't block; anything new does.
 */
@E2eTest
@ChecklistBased
class AccessibilityTest {

    /**
     * The axe-core rule sets to run: WCAG 2.0 and 2.1, levels A and AA.
     */
    private static final List<String> TAGS = List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa");

    /**
     * The rule ids that are accepted, with a reason, for this page.
     */
    private static Set<String> baseline(String page) throws IOException {
        try (InputStream in = AccessibilityTest.class.getResourceAsStream("/e2e/a11y-baseline.txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(l -> !l.isBlank() && !l.startsWith("#"))
                    .map(l -> l.trim().split("\\s+"))
                    .filter(f -> f[0].equals(page))
                    .map(f -> f[1])
                    .collect(Collectors.toSet());
        }
    }

    /**
     * No serious or critical violation on this page, apart from the reviewed baseline.
     */
    @ParameterizedTest(name = "{0}: no new serious or critical WCAG violations")
    @CsvSource({"/", "/forms/post", "/swagger-ui/index.html"})
    void noNewSeriousViolations(String page, WebDriver driver) throws IOException {
        driver.get(Playground.url(page));
        Results results = new AxeBuilder().withTags(TAGS).analyze(driver);
        Set<String> allowed = baseline(page);
        List<String> blocking = results.getViolations().stream()
                .filter(r -> "serious".equals(r.getImpact()) || "critical".equals(r.getImpact()))
                .filter(r -> !allowed.contains(r.getId()))
                .map(AccessibilityTest::describe)
                .toList();
        assertThat(blocking).as("serious/critical axe violations on %s", page).isEmpty();
    }

    /**
     * A one-line summary of a violation: rule, impact and the elements it affects.
     */
    private static String describe(Rule r) {
        String nodes = r.getNodes().stream().limit(3)
                .map(n -> n.getTarget() + (n.getFailureSummary() == null ? "" : " [" + n.getFailureSummary().lines()
                        .skip(1).findFirst().orElse("").trim() + "]"))
                .collect(Collectors.joining("; "));
        return r.getId() + " (" + r.getImpact() + ", " + r.getNodes().size() + " nodes): " + r.getHelp() + " -> " + nodes;
    }
}
