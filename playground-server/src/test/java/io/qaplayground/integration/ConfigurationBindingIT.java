package io.qaplayground.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every {@code playground.*} property must reach the behaviour it controls (risk R13).
 *
 * <p>One server, started with non-default values, and one observable effect per property. A property that binds
 * but changes nothing is worse than no property: it looks like a control. The boundaries themselves are tested at
 * API level (docs/testing/TEST-DESIGN.md section 2).
 */
@TestPropertySource(properties = {
        "playground.max-duration=1s",
        "playground.max-body-size=1KB",
        "playground.max-links=5",
        "playground.max-stream-lines=5",
        "playground.max-redirects=3",
        "playground.hostname=lab-1",
        "playground.exclude-headers=x-secret*",
        "playground.unsafe-allow-dangerous-responses=true",
        "playground.env-prefix=LAB_",
        "LAB_GREETING=hi",
        "playground.drip.num-bytes=3",
        "playground.drip.duration=0s",
        "playground.drip.delay=0s",
        "playground.sse.count=2",
        "playground.sse.duration=0s",
        "playground.jsonl.count=4"})
class ConfigurationBindingIT extends ServerIT {

    /**
     * {@code max-duration} limits how long a client may ask a response to take.
     */
    @Test
    @DisplayName("max-duration caps /delay and /drip")
    void maxDuration() {
        assertThat(get("/delay/2").body()).contains("duration 2s longer than 1s");
        assertThat(get("/drip?duration=2s&delay=0").status()).isEqualTo(400);
    }

    /**
     * {@code max-body-size} limits both the bodies clients send and the bodies the server generates.
     */
    @Test
    @DisplayName("max-body-size limits request bodies and generated bodies")
    void maxBodySize() {
        assertThat(send("POST", "/post", "x".repeat(1024), "Content-Type", "text/plain").status()).isEqualTo(200);
        Res tooBig = send("POST", "/post", "x".repeat(1025), "Content-Type", "text/plain");
        assertThat(tooBig.status()).isEqualTo(400);
        assertThat(tooBig.body()).contains("http: request body too large");
        assertThat(get("/bytes/1025").body()).contains("not in range [1, 1024]");
    }

    /**
     * {@code max-links}, {@code max-stream-lines} and {@code max-redirects} each cap their endpoint.
     */
    @Test
    @DisplayName("max-links, max-stream-lines and max-redirects")
    void counts() {
        assertThat(get("/links/6").body()).contains("must be in range [0, 5]");
        assertThat(get("/stream/10").body().lines().count()).isEqualTo(5);
        assertThat(get("/redirect/4").body()).contains("redirect count must be <= 3");
        assertThat(get("/redirect/3").status()).isEqualTo(302);
    }

    /**
     * {@code hostname}, {@code exclude-headers} and {@code env-prefix} change what the echo endpoints reveal.
     */
    @Test
    @DisplayName("hostname, exclude-headers, env-prefix")
    void echoSettings() {
        assertThat(get("/hostname").body()).contains("\"hostname\": \"lab-1\"");
        Res headers = get("/headers", "X-Secret-Token", "s3cr3t", "X-Public", "p");
        assertThat(headers.body()).contains("X-Public").doesNotContain("X-Secret-Token").doesNotContain("s3cr3t");
        assertThat(get("/env").body()).contains("\"LAB_GREETING\": \"hi\"");
    }

    /**
     * {@code unsafe-allow-dangerous-responses} really turns HTML escaping off. That is why it defaults to false.
     */
    @Test
    @DisplayName("unsafe-allow-dangerous-responses lets /response-headers serve raw HTML")
    void unsafeResponses() {
        Res r = get("/response-headers?Content-Type=text/html&x=%3Cb%3E");
        assertThat(r.body()).contains("<b>");
    }

    /**
     * The {@code drip}, {@code sse} and {@code jsonl} defaults apply when the client leaves parameters out.
     */
    @Test
    @DisplayName("drip, sse and jsonl defaults")
    void streamingDefaults() {
        assertThat(get("/drip").body()).hasSize(3);
        assertThat(getBodyAllowingTrailers("/sse")).containsOnlyOnce("\"id\":1").doesNotContain("\"id\":2");
        assertThat(get("/jsonl").body().lines().count()).isEqualTo(4);
    }
}
