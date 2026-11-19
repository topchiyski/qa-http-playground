package io.qaplayground.server.websocket;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import io.qaplayground.server.websocket.WsTestFrames.ServerFrame;
import io.qaplayground.testing.technique.StateTransition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.qaplayground.server.websocket.WebSocketFrames.OP_BINARY;
import static io.qaplayground.server.websocket.WebSocketFrames.OP_CLOSE;
import static io.qaplayground.server.websocket.WebSocketFrames.OP_CONTINUATION;
import static io.qaplayground.server.websocket.WebSocketFrames.OP_PING;
import static io.qaplayground.server.websocket.WebSocketFrames.OP_PONG;
import static io.qaplayground.server.websocket.WebSocketFrames.OP_TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * State transition testing of the echo session (docs/testing/TEST-DESIGN.md section 4.2).
 *
 * <p>The states are Open (idle), Open (assembling a fragmented message) and Closing. Every valid transition is
 * covered (0-switch), plus the invalid events listed in the design. The session runs on in-memory streams, so no
 * network is needed.
 */
@StateTransition
class EchoSessionStateTest {

    /**
     * Runs a session over these client frames with this fragment size, and returns what the server wrote.
     */
    private static List<ServerFrame> session(int maxFragment, byte[]... clientFrames) throws IOException {
        EchoUpgradeHandler handler = new EchoUpgradeHandler();
        handler.configure(maxFragment, 64 * 1024, Duration.ofSeconds(10));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            handler.serveLoop(WsTestFrames.in(clientFrames), out);
        } catch (EOFException clientWentAway) {
            // the input ran out without a close frame: the session just ends
        }
        return WsTestFrames.decode(out.toByteArray());
    }

    /**
     * Runs a session over these client frames, and returns what the server wrote.
     */
    private static List<ServerFrame> session(byte[]... clientFrames) throws IOException {
        return session(1024, clientFrames);
    }

    /**
     * The UTF-8 bytes of {@code s}.
     */
    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    // --- valid transitions --------------------------------------------------------------------

    @Test
    @DisplayName("Open --text--> Open: echoed as one text frame")
    void textIsEchoed() throws IOException {
        List<ServerFrame> out = session(WsTestFrames.text("hello"), WsTestFrames.close(1000, ""));
        assertThat(out.get(0).opcode()).isEqualTo(OP_TEXT);
        assertThat(out.get(0).fin()).isTrue();
        assertThat(out.get(0).text()).isEqualTo("hello");
    }

    @Test
    @DisplayName("Open --binary--> Open: echoed as binary")
    void binaryIsEchoed() throws IOException {
        byte[] data = {0, 1, (byte) 0xff};
        List<ServerFrame> out = session(WsTestFrames.client(true, OP_BINARY, data));
        assertThat(out.get(0).opcode()).isEqualTo(OP_BINARY);
        assertThat(out.get(0).payload()).isEqualTo(data);
    }

    @Test
    @DisplayName("Open --ping--> Open: pong with the same payload")
    void pingGetsPong() throws IOException {
        List<ServerFrame> out = session(WsTestFrames.client(true, OP_PING, utf8("p")), WsTestFrames.text("after"));
        assertThat(out.get(0).opcode()).isEqualTo(OP_PONG);
        assertThat(out.get(0).text()).isEqualTo("p");
        assertThat(out.get(1).text()).as("session still open").isEqualTo("after");
    }

    @Test
    @DisplayName("Open --pong--> Open: unsolicited pong is ignored")
    void pongIsIgnored() throws IOException {
        List<ServerFrame> out = session(WsTestFrames.client(true, OP_PONG, new byte[0]), WsTestFrames.text("x"));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).text()).isEqualTo("x");
    }

    @Test
    @DisplayName("Open --close--> Closing: server answers close 1000 and stops")
    void closeIsAnswered() throws IOException {
        List<ServerFrame> out = session(WsTestFrames.close(1001, "going away"), WsTestFrames.text("ignored"));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).opcode()).isEqualTo(OP_CLOSE);
        assertThat(out.get(0).closeCode()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Open --text(fin=0)--> Assembling --cont(fin=1)--> Open: echoed once complete")
    void fragmentedMessageIsEchoedWhenComplete() throws IOException {
        List<ServerFrame> out = session(
                WsTestFrames.client(false, OP_TEXT, utf8("hel")),
                WsTestFrames.client(false, OP_CONTINUATION, utf8("l")),
                WsTestFrames.client(true, OP_CONTINUATION, utf8("o")));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).text()).isEqualTo("hello");
    }

    /**
     * A ping in the middle of a fragmented message is answered at once, and the message still completes.
     */
    @Test
    @DisplayName("Assembling --ping--> Assembling: control frames may interleave with fragments")
    void pingDuringFragmentedMessage() throws IOException {
        List<ServerFrame> out = session(
                WsTestFrames.client(false, OP_TEXT, utf8("a")),
                WsTestFrames.client(true, OP_PING, new byte[0]),
                WsTestFrames.client(true, OP_CONTINUATION, utf8("b")));
        assertThat(out).extracting(ServerFrame::opcode).containsExactly(OP_PONG, OP_TEXT);
        assertThat(out.get(1).text()).isEqualTo("ab");
    }

    @Test
    @DisplayName("Long messages are echoed in fragments of at most max-fragment-size bytes")
    void echoRespectsMaxFragmentSize() throws IOException {
        // The client fragments 3/4/3; the server re-fragments the whole message 4/4/2.
        List<ServerFrame> out = session(4,
                WsTestFrames.client(false, OP_TEXT, utf8("012")),
                WsTestFrames.client(false, OP_CONTINUATION, utf8("3456")),
                WsTestFrames.client(true, OP_CONTINUATION, utf8("789")));
        assertThat(out).extracting(ServerFrame::opcode).containsExactly(OP_TEXT, OP_CONTINUATION, OP_CONTINUATION);
        assertThat(out).extracting(ServerFrame::fin).containsExactly(false, false, true);
        assertThat(out).extracting(ServerFrame::text).containsExactly("0123", "4567", "89");
    }

    @Test
    @DisplayName("Empty text message is echoed as one empty frame")
    void emptyMessage() throws IOException {
        List<ServerFrame> out = session(WsTestFrames.text(""));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).payload()).isEmpty();
        assertThat(out.get(0).fin()).isTrue();
    }

    /**
     * Regression for a defect this test found: UTF-8 was checked per fragment, so a character split across two
     * fragments was rejected with 1007, although the message is valid.
     *
     * <p>RFC 6455 section 5.6 requires the whole message to be valid. Autobahn cases 6.2.x test exactly this.
     */
    @Test
    @DisplayName("A UTF-8 character split across fragments is valid (RFC 6455 5.6)")
    void utf8SplitAcrossFragments() throws IOException {
        byte[] euro = utf8("€"); // E2 82 AC
        List<ServerFrame> out = session(
                WsTestFrames.client(false, OP_TEXT, Arrays.copyOfRange(euro, 0, 1)),
                WsTestFrames.client(true, OP_CONTINUATION, Arrays.copyOfRange(euro, 1, 3)));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).opcode()).isEqualTo(OP_TEXT);
        assertThat(out.get(0).text()).isEqualTo("€");
    }

    // --- invalid events -----------------------------------------------------------------------

    @Test
    @DisplayName("Open --continuation--> Closing(1002): nothing to continue")
    void continuationWithoutStart() throws IOException {
        ServerFrame close = session(WsTestFrames.client(true, OP_CONTINUATION, utf8("x"))).getFirst();
        assertThat(close.closeCode()).isEqualTo(1002);
        assertThat(close.closeReason()).isEqualTo("unexpected continuation frame");
    }

    @Test
    @DisplayName("Assembling --text--> Closing(1002): a new message before the old one finished")
    void newMessageWhileAssembling() throws IOException {
        ServerFrame close = session(
                WsTestFrames.client(false, OP_TEXT, utf8("a")),
                WsTestFrames.text("b")).getFirst();
        assertThat(close.closeCode()).isEqualTo(1002);
        assertThat(close.closeReason()).isEqualTo("continuation frame expected");
    }

    @Test
    @DisplayName("Open --invalid UTF-8 text--> Closing(1007)")
    void invalidUtf8Text() throws IOException {
        ServerFrame close = session(WsTestFrames.client(true, OP_TEXT, new byte[]{(byte) 0xc3, 0x28})).getFirst();
        assertThat(close.closeCode()).isEqualTo(1007);
    }

    /**
     * A message that ends halfway through a UTF-8 character is invalid, even if every fragment looked fine.
     */
    @Test
    @DisplayName("Assembling --fin with incomplete UTF-8--> Closing(1007)")
    void messageEndingInsideACharacter() throws IOException {
        byte[] euro = utf8("€");
        ServerFrame close = session(
                WsTestFrames.client(false, OP_TEXT, utf8("a")),
                WsTestFrames.client(true, OP_CONTINUATION, Arrays.copyOfRange(euro, 0, 2))).getFirst();
        assertThat(close.closeCode()).isEqualTo(1007);
    }

    /**
     * Binary messages may hold any bytes. Only text must be UTF-8.
     */
    @Test
    @DisplayName("Binary messages are never UTF-8 checked")
    void binaryIsNotValidated() throws IOException {
        ServerFrame echo = session(WsTestFrames.client(true, OP_BINARY, new byte[]{(byte) 0xc3, 0x28})).getFirst();
        assertThat(echo.opcode()).isEqualTo(OP_BINARY);
    }

    @Test
    @DisplayName("Open --reserved opcode--> Closing(1002)")
    void reservedOpcode() throws IOException {
        ServerFrame close = session(WsTestFrames.client(true, 0x3, new byte[0])).getFirst();
        assertThat(close.closeCode()).isEqualTo(1002);
        assertThat(close.closeReason()).isEqualTo("unsupported opcode: 3");
    }

    @Test
    @DisplayName("Open --unmasked frame--> Closing(1002)")
    void unmaskedFrame() throws IOException {
        ServerFrame close = session(WsTestFrames.client(true, 0, OP_TEXT, utf8("x"), false)).getFirst();
        assertThat(close.closeCode()).isEqualTo(1002);
    }

    @Test
    @DisplayName("Open --frame larger than max-fragment-size--> Closing(1009)")
    void oversizedFrame() throws IOException {
        ServerFrame close = session(8, WsTestFrames.text("123456789")).getFirst();
        assertThat(close.closeCode()).isEqualTo(1009);
    }

    /**
     * Regression: the deadline used to do nothing on Tomcat, so sessions outlived max-duration (risk R3).
     */
    @Test
    @DisplayName("Open --max-duration elapses--> Closing(1001): later frames are not echoed")
    void deadlineClosesTheSession() throws IOException {
        EchoUpgradeHandler handler = new EchoUpgradeHandler();
        handler.configure(1024, 1024, Duration.ofSeconds(3));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        handler.expire(out);
        handler.expire(out); // idempotent
        handler.serveLoop(WsTestFrames.in(WsTestFrames.text("too late")), out);
        List<ServerFrame> frames = WsTestFrames.decode(out.toByteArray());
        assertThat(frames).hasSize(1);
        assertThat(frames.getFirst().closeCode()).isEqualTo(1001);
        assertThat(frames.getFirst().closeReason()).isEqualTo("session exceeded max duration of 3s");
    }

    /**
     * A client that vanishes ends the loop with an {@code EOFException}, which the caller treats as a normal end.
     */
    @Test
    @DisplayName("Client disappears without a close frame: the loop ends with EOF")
    void clientDisappears() {
        EchoUpgradeHandler handler = new EchoUpgradeHandler();
        handler.configure(1024, 1024, Duration.ofSeconds(1));
        assertThatThrownBy(() -> handler.serveLoop(WsTestFrames.in(), new ByteArrayOutputStream()))
                .isInstanceOf(EOFException.class);
    }
}
