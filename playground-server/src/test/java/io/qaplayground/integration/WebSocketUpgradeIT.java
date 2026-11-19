package io.qaplayground.integration;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import io.qaplayground.testing.technique.StateTransition;
import io.qaplayground.testing.trace.Trace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The WebSocket echo through the real Servlet HTTP Upgrade path.
 *
 * <p>The handshake, the container handing over the connection, and the session deadline ({@code max-duration} is
 * lowered to 1 s). Unit tests can't see any of this, because it is the container's part.
 */
@TestPropertySource(properties = "playground.max-duration=1s")
class WebSocketUpgradeIT extends ServerIT {

    /**
     * The {@code ws://} URI of the echo endpoint.
     */
    private String wsUri() {
        Trace.path("/websocket/echo");
        return base().replace("http://", "ws://") + "/websocket/echo";
    }

    /**
     * A message sent through the real container comes back unchanged.
     */
    @StateTransition
    @Test
    @DisplayName("Connecting --101--> Open --text--> echo --close--> Closed")
    void echoThroughTheContainer() throws Exception {
        CompletableFuture<String> message = new CompletableFuture<>();
        CompletableFuture<Integer> closed = new CompletableFuture<>();
        WebSocket ws = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create(wsUri()), new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
                        message.complete(data.toString());
                        w.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket w, int statusCode, String reason) {
                        closed.complete(statusCode);
                        return null;
                    }
                }).get(5, TimeUnit.SECONDS);
        ws.sendText("héllo €", true);
        assertThat(message.get(5, TimeUnit.SECONDS)).isEqualTo("héllo €");
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        assertThat(closed.get(5, TimeUnit.SECONDS)).isEqualTo(1000);
    }

    /**
     * A session that stays open past {@code max-duration} is closed by the server.
     */
    @StateTransition
    @Test
    @DisplayName("Open --max-duration elapses--> Closed by the server")
    void sessionEndsAfterMaxDuration() throws Exception {
        CompletableFuture<Integer> closeCode = new CompletableFuture<>();
        long start = System.nanoTime();
        HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create(wsUri()), new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onClose(WebSocket w, int statusCode, String reason) {
                        closeCode.complete(statusCode);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket w, Throwable error) {
                        closeCode.completeExceptionally(error);
                    }
                }).get(5, TimeUnit.SECONDS);
        assertThat(closeCode.get(5, TimeUnit.SECONDS)).isEqualTo(1001);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isBetween(Duration.ofMillis(900), Duration.ofSeconds(4));
    }

    /**
     * A plain GET without the upgrade headers gets 400, not an upgrade.
     */
    @Test
    @DisplayName("A plain GET without upgrade headers is a 400 handshake error")
    void handshakeErrors() {
        Res r = get("/websocket/echo");
        assertThat(r.status()).isEqualTo(400);
    }

    /**
     * The handshake byte for byte, over a raw socket: 101, and the {@code Sec-WebSocket-Accept} from the RFC example.
     */
    @Test
    @DisplayName("Raw handshake: 101 with the RFC 6455 accept key")
    void rawHandshake() throws Exception {
        URI uri = URI.create(base());
        Trace.path("/websocket/echo");
        try (Socket s = new Socket(uri.getHost(), uri.getPort())) {
            s.setSoTimeout(5000);
            OutputStream out = s.getOutputStream();
            out.write(("GET /websocket/echo HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = s.getInputStream();
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            while (!head.toString(StandardCharsets.ISO_8859_1).endsWith("\r\n\r\n")) {
                int b = in.read();
                if (b < 0) {
                    break;
                }
                head.write(b);
            }
            String response = head.toString(StandardCharsets.ISO_8859_1);
            assertThat(response).startsWith("HTTP/1.1 101");
            assertThat(response).containsIgnoringCase("Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=");
        }
    }
}
