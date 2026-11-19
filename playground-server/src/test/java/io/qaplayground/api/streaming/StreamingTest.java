package io.qaplayground.api.streaming;

import java.util.List;

import io.qaplayground.api.support.PlaygroundTest;
import io.qaplayground.testing.technique.BoundaryValue;
import io.qaplayground.testing.technique.ErrorGuessing;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;

/**
 * Tutorial: streamed responses.
 *
 * <p>REST Assured waits for the whole body, so these tests split it into lines or events afterwards. To handle items
 * as they arrive, use a streaming client, such as Java's {@code HttpClient} with a line subscriber, or Gatling's SSE
 * support.
 */
class StreamingTest extends PlaygroundTest {

    /**
     * {@code /stream/{n}} sends n lines, each a complete JSON object with its own id.
     */
    @Test
    void streamSendsNJsonLines() {
        String body = when()
            .get("/stream/3")
        .then()
            .statusCode(200)
            .header("Transfer-Encoding", "chunked")
            .extract().asString();
        List<String> lines = body.lines().toList();
        assertThat(lines, hasSize(3));
        assertThat(JsonPath.from(lines.get(2)).getInt("id"), equalTo(2));
    }

    /**
     * Asking for more than 100 lines gets 100, and 0 gets 1. Only a count that isn't a number is refused.
     */
    @BoundaryValue
    @Test
    void streamIsClampedTo100() {
        assertThat(when().get("/stream/500").asString().lines().toList(), hasSize(100));
        assertThat(when().get("/stream/0").asString().lines().toList(), hasSize(1));
        when().get("/stream/abc").then().statusCode(400);
    }

    /**
     * {@code /jsonl} says {@code application/x-ndjson}, and sends one line per record.
     */
    @Test
    void jsonlUsesNdjsonContentType() {
        String body = given()
            .queryParam("count", 4)
        .when()
            .get("/jsonl")
        .then()
            .statusCode(200)
            .contentType(startsWith("application/x-ndjson"))
            .extract().asString();
        assertThat(body.lines().toList(), hasSize(4));
    }

    /**
     * Jitter outside 0 to 1, a zero duration, or more time than allowed is refused.
     */
    @ErrorGuessing
    @Test
    void jsonlRejectsBadJitterAndDuration() {
        when().get("/jsonl?jitter=2").then().statusCode(400)
                .body("detail", equalTo("invalid jitter: must be a float in [0, 1]"));
        when().get("/jsonl?duration=0").then().statusCode(400);
        when().get("/jsonl?duration=8s&delay=3s").then().statusCode(400).body(equalTo("Too much time\n"));
    }

    /**
     * SSE format: "event: ping", then "data: {...}", then a blank line, per event.
     */
    @Test
    void sseEventsHaveTheRightShape() {
        String body = given()
            .queryParam("count", 3)
            .queryParam("duration", "300ms")
        .when()
            .get("/sse")
        .then()
            .statusCode(200)
            .contentType(startsWith("text/event-stream"))
            .header("Trailer", "Server-Timing")
            .extract().asString();
        String[] events = body.split("\n\n");
        assertThat(events.length, equalTo(3));
        assertThat(events[1], startsWith("event: ping\ndata: {\"id\":1,\"timestamp\":"));
    }

    /**
     * An event count outside the allowed range is refused.
     */
    @BoundaryValue
    @Test
    void sseCountLimits() {
        when().get("/sse?count=0").then().statusCode(400);
        when().get("/sse?count=1000000").then().statusCode(400);
    }

    /**
     * The body of /trailers is the usual echo; the trailer values are declared up front.
     */
    @Test
    void trailersDeclaresTrailerFields() {
        when()
            .get("/trailers?X-Checksum=abc&Server-Timing=x")
        .then()
            .statusCode(200)
            .header("Transfer-Encoding", "chunked")
            .body("args.X-Checksum[0]", equalTo("abc"));
    }

    /**
     * Header names that may not be trailers are refused with 400.
     */
    @ErrorGuessing
    @Test
    void forbiddenTrailersAre400() {
        when().get("/trailers?Content-Length=1").then().statusCode(400)
                .body("detail", equalTo("forbidden trailer: Content-Length"));
        when().get("/trailers?authorization=x").then().statusCode(400);
    }
}
