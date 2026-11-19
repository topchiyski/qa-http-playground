package io.qaplayground.server.websocket;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import io.qaplayground.testing.Generators;
import io.qaplayground.testing.technique.BoundaryValue;
import io.qaplayground.testing.technique.EquivalencePartitioning;
import io.qaplayground.testing.technique.ErrorGuessing;
import io.qaplayground.testing.technique.PropertyBased;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.qaplayground.server.websocket.WebSocketFrames.OP_BINARY;
import static io.qaplayground.server.websocket.WebSocketFrames.OP_CLOSE;
import static io.qaplayground.server.websocket.WebSocketFrames.OP_CONTINUATION;
import static io.qaplayground.server.websocket.WebSocketFrames.OP_PING;
import static io.qaplayground.server.websocket.WebSocketFrames.OP_TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RFC 6455 framing (risk R8).
 *
 * <p>Frames are built by {@link WsTestFrames}, independently of the code under test, so a bug can't hide by being
 * the same on both sides.
 */
class WebSocketFramesTest {

    /**
     * The largest frame payload these tests allow.
     */
    private static final int MAX_FRAGMENT = 1024;
    /**
     * The largest whole message these tests allow.
     */
    private static final int MAX_MESSAGE = 4096;

    /**
     * Reads one frame from these bytes with the class's limits.
     */
    private static WebSocketFrames.Frame read(byte[] wire) throws IOException {
        return WebSocketFrames.read(WsTestFrames.in(wire), MAX_FRAGMENT, MAX_MESSAGE, 0);
    }

    /**
     * The handshake answer for the RFC 6455 example key is the one the RFC prints.
     */
    @Test
    void acceptKeyMatchesTheRfcExample() {
        // RFC 6455 section 1.3
        assertThat(WebSocketFrames.acceptKey("dGhlIHNhbXBsZSBub25jZQ==")).isEqualTo("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=");
    }

    /**
     * The three length encodings switch at 125/126 and 65535/65536 bytes.
     */
    @BoundaryValue
    @ParameterizedTest(name = "{0}-byte payload round-trips through the {1} length encoding")
    @CsvSource({"0, 7-bit", "125, 7-bit", "126, 16-bit", "65535, 16-bit", "65536, 64-bit"})
    void lengthEncodingBoundaries(int size, String encoding) throws IOException {
        byte[] payload = new byte[size];
        java.util.Arrays.fill(payload, (byte) 'x');
        WebSocketFrames.Frame f = WebSocketFrames.read(WsTestFrames.in(WsTestFrames.client(true, OP_BINARY, payload)),
                70_000, 70_000, 0);
        assertThat(f.payload()).isEqualTo(payload);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WebSocketFrames.write(out, WebSocketFrames.Frame.of(true, OP_BINARY, payload));
        int headerLength = switch (encoding) {
            case "7-bit" -> 2;
            case "16-bit" -> 4;
            default -> 10;
        };
        assertThat(out.size()).isEqualTo(headerLength + size);
        assertThat(WsTestFrames.decode(out.toByteArray()).getFirst().payload()).isEqualTo(payload);
    }

    @BoundaryValue
    @ParameterizedTest(name = "data frame of {0} bytes with max {1}: allowed {2}")
    @CsvSource({"1023, 1024, true", "1024, 1024, true", "1025, 1024, false"})
    void fragmentSizeLimit(int size, int max, boolean allowed) throws IOException {
        byte[] wire = WsTestFrames.client(true, OP_TEXT, new byte[size]);
        if (allowed) {
            assertThat(WebSocketFrames.read(WsTestFrames.in(wire), max, MAX_MESSAGE, 0).payload()).hasSize(size);
        } else {
            assertThatThrownBy(() -> WebSocketFrames.read(WsTestFrames.in(wire), max, MAX_MESSAGE, 0))
                    .isInstanceOf(WebSocketFrames.ProtocolException.class)
                    .hasMessage("frame too large: payload size 1025 exceeds maximum of 1024 bytes")
                    .extracting(e -> ((WebSocketFrames.ProtocolException) e).closeCode).isEqualTo(1009);
        }
    }

    /**
     * The message limit counts every fragment of a message, not just the current one.
     */
    @BoundaryValue
    @ParameterizedTest(name = "continuation making the message {0} bytes with max 4096: allowed {1}")
    @CsvSource({"4095, true", "4096, true", "4097, false"})
    void messageSizeLimitAppliesToContinuations(int total, boolean allowed) throws IOException {
        int already = 3500;
        byte[] wire = WsTestFrames.client(true, OP_CONTINUATION, new byte[total - already]);
        if (allowed) {
            assertThat(WebSocketFrames.read(WsTestFrames.in(wire), MAX_FRAGMENT, MAX_MESSAGE, already).fin()).isTrue();
        } else {
            assertThatThrownBy(() -> WebSocketFrames.read(WsTestFrames.in(wire), MAX_FRAGMENT, MAX_MESSAGE, already))
                    .hasMessage("message too large: size 4097 exceeds maximum of 4096 bytes");
        }
    }

    /**
     * Control frames carry at most 125 bytes.
     */
    @BoundaryValue
    @ParameterizedTest(name = "control frame with {0}-byte payload: allowed {1}")
    @CsvSource({"124, true", "125, true", "126, false"})
    void controlFramePayloadLimit(int size, boolean allowed) throws IOException {
        byte[] wire = WsTestFrames.client(true, OP_PING, new byte[size]);
        if (allowed) {
            assertThat(read(wire).payload()).hasSize(size);
        } else {
            assertThatThrownBy(() -> read(wire)).hasMessage("invalid control frame: payload size 126 exceeds 125 bytes");
        }
    }

    @ErrorGuessing
    @Test
    void unmaskedClientFramesAreAProtocolError() {
        assertThatThrownBy(() -> read(WsTestFrames.client(true, 0, OP_TEXT, "hi".getBytes(), false)))
                .hasMessage("received unmasked client frame")
                .extracting(e -> ((WebSocketFrames.ProtocolException) e).closeCode).isEqualTo(1002);
    }

    @EquivalencePartitioning
    @ParameterizedTest(name = "RSV bits {0}")
    @ValueSource(ints = {0b100, 0b010, 0b001, 0b111})
    void reservedBitsAreRejected(int rsv) {
        assertThatThrownBy(() -> read(WsTestFrames.client(true, rsv, OP_TEXT, "x".getBytes(), true)))
                .hasMessage("frame has unsupported RSV bits set");
    }

    @Test
    void fragmentedControlFramesAreRejected() {
        assertThatThrownBy(() -> read(WsTestFrames.client(false, OP_PING, new byte[0])))
                .hasMessage("invalid control frame: control frame 9 must not be fragmented");
    }

    /**
     * A 64-bit length with the top bit set is invalid, and must not be read as a negative number.
     */
    @ErrorGuessing
    @Test
    void sixtyFourBitLengthWithTheTopBitSetIsRejected() {
        byte[] wire = {(byte) 0x82, (byte) (0x80 | 127), (byte) 0x80, 0, 0, 0, 0, 0, 0, 1};
        assertThatThrownBy(() -> read(wire)).hasMessageContaining("most significant bit of 64-bit length must be 0");
    }

    /**
     * A frame cut short ends with an {@code EOFException}, which {@code isEof} recognises as a normal end.
     */
    @ErrorGuessing
    @Test
    void truncatedFramesEndWithEof() {
        byte[] full = WsTestFrames.text("hello");
        byte[] truncated = java.util.Arrays.copyOf(full, full.length - 2);
        assertThatThrownBy(() -> read(truncated)).isInstanceOf(EOFException.class);
        assertThat(WebSocketFrames.isEof(new EOFException())).isTrue();
        assertThat(WebSocketFrames.isEof(new IOException())).isFalse();
    }

    // --- close frame payloads -----------------------------------------------------------------

    /**
     * Close codes outside the allowed ranges, or reserved ones, are refused.
     */
    @BoundaryValue
    @ParameterizedTest(name = "close code {0}: valid {1}")
    @CsvSource({"999, false", "1000, true", "1003, true", "1004, false", "1005, false", "1006, false",
            "1007, true", "1015, false", "2999, false", "3000, true", "4999, true", "5000, false"})
    void closeCodeValidity(int code, boolean valid) throws IOException {
        byte[] wire = WsTestFrames.close(code, "");
        if (valid) {
            assertThat(read(wire).opcode()).isEqualTo(OP_CLOSE);
        } else {
            assertThatThrownBy(() -> read(wire)).isInstanceOf(WebSocketFrames.ProtocolException.class)
                    .hasMessageStartingWith("invalid close frame payload: status code " + code);
        }
    }

    /**
     * A close payload is empty, or two bytes of code plus an optional UTF-8 reason. Anything else is refused.
     */
    @Test
    void closePayloadShapes() throws IOException {
        assertThat(read(WsTestFrames.client(true, OP_CLOSE, new byte[0])).payload()).isEmpty();
        assertThatThrownBy(() -> read(WsTestFrames.client(true, OP_CLOSE, new byte[]{3})))
                .hasMessage("invalid close frame payload: must be at least 2 bytes");
        byte[] badUtf8 = {0x03, (byte) 0xe8, (byte) 0xc3, 0x28};
        assertThatThrownBy(() -> read(WsTestFrames.client(true, OP_CLOSE, badUtf8)))
                .hasMessage("invalid close frame payload: reason must be valid UTF-8");
        assertThat(read(WsTestFrames.close(1000, "bye 👋")).payload()).hasSize(2 + "bye 👋".getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void writeCloseEncodesCodeAndReason() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WebSocketFrames.writeClose(out, 1002, "nope");
        WebSocketFrames.writeClose(out, 1000, null);
        List<WsTestFrames.ServerFrame> frames = WsTestFrames.decode(out.toByteArray());
        assertThat(frames.get(0).closeCode()).isEqualTo(1002);
        assertThat(frames.get(0).closeReason()).isEqualTo("nope");
        assertThat(frames.get(1).payload()).hasSize(2);
    }

    // --- property: masked client frames decode to their payload ------------------------------

    /**
     * Seeded random client frames of every opcode and length class.
     */
    static Stream<Arguments> randomFrames() {
        return Generators.samples(r -> {
            int sizeClass = r.nextInt(3);
            int size = switch (sizeClass) {
                case 0 -> r.nextInt(126);
                case 1 -> 126 + r.nextInt(65535 - 126 + 1);
                default -> 65536 + r.nextInt(5000);
            };
            byte[] payload = new byte[size];
            r.nextBytes(payload);
            return new Object[]{r.nextBoolean(), r.nextBoolean() ? OP_TEXT : OP_BINARY, payload};
        });
    }

    /**
     * Whatever a correct client writes, the server reads back as the same frame.
     */
    @PropertyBased
    @ParameterizedTest
    @MethodSource("randomFrames")
    void readInvertsClientEncoding(Object[] frame) throws IOException {
        boolean fin = (boolean) frame[0];
        int opcode = (int) frame[1];
        byte[] payload = (byte[]) frame[2];
        DataInputStream in = WsTestFrames.in(WsTestFrames.client(fin, opcode, payload));
        WebSocketFrames.Frame f = WebSocketFrames.read(in, 100_000, 100_000, 0);
        assertThat(f.fin()).isEqualTo(fin);
        assertThat(f.opcode()).isEqualTo(opcode);
        assertThat(f.payload()).isEqualTo(payload);
    }

    @EquivalencePartitioning
    @Test
    void utf8Validation() {
        byte[] euro = "€".getBytes(StandardCharsets.UTF_8);
        assertThat(WebSocketFrames.isValidUtf8(euro, 0, euro.length)).isTrue();
        assertThat(WebSocketFrames.isValidUtf8(euro, 0, 2)).as("truncated sequence").isFalse();
        assertThat(WebSocketFrames.isValidUtf8(new byte[]{(byte) 0xc0, (byte) 0x80}, 0, 2)).as("overlong NUL").isFalse();
        assertThat(WebSocketFrames.isValidUtf8(new byte[]{(byte) 0xed, (byte) 0xa0, (byte) 0x80}, 0, 3)).as("surrogate").isFalse();
    }
}
