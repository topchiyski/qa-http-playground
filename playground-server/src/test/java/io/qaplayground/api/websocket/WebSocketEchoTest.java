package io.qaplayground.api.websocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import io.qaplayground.api.support.PlaygroundTest;
import io.qaplayground.api.support.PlaygroundTarget;
import io.qaplayground.testing.technique.BoundaryValue;
import io.qaplayground.testing.technique.EquivalencePartitioning;
import io.qaplayground.testing.technique.StateTransition;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

/**
 * Tutorial: WebSockets with the JDK's built-in client ({@code java.net.http.WebSocket}).
 *
 * <p>REST Assured doesn't speak WebSocket, but it is handy for checking the HTTP handshake errors.
 */
class WebSocketEchoTest extends PlaygroundTest {

    /**
     * Collects everything the server sends, until it closes or we stop listening.
     */
    static final class Recorder implements WebSocket.Listener {
        final List<String> textFrames = new ArrayList<>();
        final StringBuilder text = new StringBuilder();
        final CompletableFuture<String> nextMessage = new CompletableFuture<>();
        final CompletableFuture<Integer> closeCode = new CompletableFuture<>();
        final ByteBuffer binary = ByteBuffer.allocate(1024);

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            textFrames.add(data.toString());
            text.append(data);
            if (last) {
                nextMessage.complete(text.toString());
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
            binary.put(data);
            if (last) {
                nextMessage.complete("binary:" + binary.position());
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            closeCode.complete(statusCode);
            return null;
        }
    }

    /**
     * Opens a WebSocket to the echo endpoint with these query parameters.
     */
    private static WebSocket connect(String query, Recorder recorder) throws Exception {
        URI uri = URI.create(PlaygroundTarget.baseUri().replaceFirst("^http", "ws") + "/websocket/echo" + query);
        return HttpClient.newHttpClient().newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(uri, recorder)
                .get(5, TimeUnit.SECONDS);
    }

    /**
     * A text message comes back unchanged.
     */
    @EquivalencePartitioning
    @Test
    void echoesTextMessages() throws Exception {
        Recorder r = new Recorder();
        WebSocket ws = connect("", r);
        ws.sendText("hello, websocket", true);
        assertThat(r.nextMessage.get(5, TimeUnit.SECONDS), equalTo("hello, websocket"));
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(5, TimeUnit.SECONDS);
        assertThat(r.closeCode.get(5, TimeUnit.SECONDS), equalTo(1000));
    }

    /**
     * A binary message comes back unchanged.
     */
    @EquivalencePartitioning
    @Test
    void echoesBinaryMessages() throws Exception {
        Recorder r = new Recorder();
        WebSocket ws = connect("", r);
        ws.sendBinary(ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5}), true);
        assertThat(r.nextMessage.get(5, TimeUnit.SECONDS), equalTo("binary:5"));
        ws.abort();
    }

    /**
     * With max_fragment_size=4 the server splits a 10-byte message into 3 frames.
     */
    @StateTransition
    @Test
    void serverFragmentsLargeMessages() throws Exception {
        Recorder r = new Recorder();
        WebSocket ws = connect("?max_fragment_size=4&max_message_size=100", r);
        // Wait for each send: java.net.http.WebSocket allows one outstanding send and fails the next
        // one's future otherwise, so on a slow machine a fragment would be silently dropped.
        ws.sendText("0123", false).join();
        ws.sendText("4567", false).join();
        ws.sendText("89", true).join();
        assertThat(r.nextMessage.get(5, TimeUnit.SECONDS), equalTo("0123456789"));
        assertThat(r.textFrames, equalTo(List.of("0123", "4567", "89")));
        ws.abort();
    }

    /**
     * A frame bigger than max_fragment_size closes the connection with 1009 "message too big".
     */
    @BoundaryValue
    @Test
    void oversizedFrameClosesWith1009() throws Exception {
        Recorder r = new Recorder();
        WebSocket ws = connect("?max_fragment_size=8&max_message_size=8", r);
        ws.sendText("this is far more than eight bytes", true);
        assertThat(r.closeCode.get(5, TimeUnit.SECONDS), equalTo(1009));
        ws.abort();
    }

    /**
     * A plain GET, or an upgrade with an unsupported WebSocket version, is refused with 400.
     */
    @Test
    void handshakeRequiresUpgradeHeaders() {
        when()
            .get("/websocket/echo")
        .then()
            .statusCode(400)
            .body("detail", equalTo("missing required `Upgrade: websocket` header"));

        given()
            .header("Upgrade", "websocket")
            .header("Sec-WebSocket-Version", "8")
        .when()
            .get("/websocket/echo")
        .then()
            .statusCode(400)
            .body("detail", startsWith("only websocket version \"13\" is supported"));
    }

    /**
     * Size limits that are out of range, or inconsistent with each other, are refused.
     */
    @BoundaryValue
    @Test
    void limitsAreValidated() {
        when().get("/websocket/echo?max_fragment_size=0").then().statusCode(400);
        when().get("/websocket/echo?max_fragment_size=100&max_message_size=10").then().statusCode(400)
                .body("detail", equalTo("max_fragment_size 100 must be less than or equal to max_message_size 10"));
    }
}
