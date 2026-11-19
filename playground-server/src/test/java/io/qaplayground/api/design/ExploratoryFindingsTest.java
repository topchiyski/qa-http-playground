package io.qaplayground.api.design;

import io.qaplayground.api.support.PlaygroundTest;
import io.qaplayground.api.support.RawHttp;
import io.qaplayground.testing.technique.ExploratoryFinding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

/**
 * Regression tests for findings from exploratory sessions.
 *
 * <p>Each test names its session sheet in docs/testing/EXPLORATORY-CHARTERS.md. The exploration found the problem;
 * the test keeps it fixed.
 */
@ExploratoryFinding
class ExploratoryFindingsTest extends PlaygroundTest {

    /**
     * ES-01 F1: {@code /response-headers?Transfer-Encoding=chunked} produced a response with both
     * {@code Transfer-Encoding: chunked} and {@code Content-Length}, and a body that wasn't chunked.
     *
     * <p>Clients must prefer {@code Transfer-Encoding} (RFC 9112 6.3), so they misread the body and the keep-alive
     * connection was corrupted. Checked on the raw wire: an HTTP client would hide the problem.
     */
    @Test
    @DisplayName("ES-01 F1: a client cannot inject conflicting message framing")
    void noConflictingFraming() {
        RawHttp.Reply r = RawHttp.get("/response-headers?Transfer-Encoding=chunked&X-Ok=1");
        String headers = r.headers().toLowerCase();
        assertThat(headers.contains("transfer-encoding:") && headers.contains("content-length:"))
                .as("both framing headers in:%n%s", r.headers()).isFalse();
        assertThat(headers).contains("x-ok: 1");
        assertThat(r.body()).contains("\"Transfer-Encoding\"").contains("\"chunked\""); // still echoed in the body
    }

    @Test
    @DisplayName("ES-01 F1: a client-supplied Content-Length cannot truncate or stretch the body")
    void contentLengthIsTheServers() {
        for (String declared : new String[]{"5", "100000"}) {
            RawHttp.Reply r = RawHttp.get("/response-headers?Content-Length=" + declared);
            String length = r.headers().lines().filter(l -> l.toLowerCase().startsWith("content-length:"))
                    .map(l -> l.substring(15).trim()).findFirst().orElseThrow();
            assertThat(Integer.parseInt(length)).isEqualTo(r.body().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        }
    }

    /**
     * ES-02 F1: form bodies sent with PUT, PATCH or DELETE vanished ({@code data} and {@code form} were empty).
     *
     * <p>Spring's {@code FormContentFilter} parsed such bodies into request parameters and used up the stream before
     * the
     * endpoint could read it; POST was unaffected. Found while writing learning material: the partitions "body type"
     * and "method" had never been crossed.
     */
    @org.junit.jupiter.params.ParameterizedTest(name = "ES-02 F1: {0} with a form body echoes the form")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void formBodiesSurviveEveryMethod(String method) {
        given().contentType("application/x-www-form-urlencoded").body("x=1&y=2")
                .request(method, "/" + method.toLowerCase()).then()
                .statusCode(200)
                .body("data", org.hamcrest.Matchers.equalTo("x=1&y=2"))
                .body("form.x[0]", org.hamcrest.Matchers.equalTo("1"))
                .body("form.y[0]", org.hamcrest.Matchers.equalTo("2"));
    }

    /**
     * ES-01 F2, not a defect, matches the reference: {@code SameSite=None} is sent without {@code Secure} when asked
     * to.
     *
     * <p>Browsers reject such cookies over http, so a learner sees the cookie "disappear". Pinned here; shown in the
     * browser by the E2E cookie journey.
     */
    @Test
    @DisplayName("ES-01 F2: SameSite=None is passed through without forcing Secure (as upstream)")
    void sameSiteNoneIsNotSilentlyMadeSecure() {
        given().redirects().follow(false).queryParam("a", "b").queryParam("attr[SameSite]", "None")
                .get("/cookies/set").then().header("Set-Cookie", containsString("SameSite=None"))
                .header("Set-Cookie", org.hamcrest.Matchers.not(containsString("Secure")));
    }
}
