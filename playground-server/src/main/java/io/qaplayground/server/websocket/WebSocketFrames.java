package io.qaplayground.server.websocket;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Set;

/**
 * The RFC 6455 wire format, ported from go-httpbin's {@code websocket} package.
 *
 * <pre>
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-------+-+-------------+-------------------------------+
 * |F|R|R|R| opcode|M| Payload len |    Extended payload length    |
 * |I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
 * |N|V|V|V|       |S|             |   (if payload len==126/127)   |
 * | |1|2|3|       |K|             |                               |
 * +-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
 * |     Masking-key (4 bytes, client frames only)   |  Payload    |
 * +-------------------------------------------------+-------------+
 * </pre>
 */
public final class WebSocketFrames {

    /**
     * Opcode: the next part of a message that was split into frames.
     */
    public static final int OP_CONTINUATION = 0x0;
    /**
     * Opcode: a text message, which must be valid UTF-8.
     */
    public static final int OP_TEXT = 0x1;
    /**
     * Opcode: a binary message.
     */
    public static final int OP_BINARY = 0x2;
    /**
     * Opcode: closes the connection, with an optional code and reason.
     */
    public static final int OP_CLOSE = 0x8;
    /**
     * Opcode: "are you there?" The other side answers with a pong.
     */
    public static final int OP_PING = 0x9;
    /**
     * Opcode: the answer to a ping.
     */
    public static final int OP_PONG = 0xA;

    /**
     * Close code: a normal, clean close.
     */
    public static final int STATUS_NORMAL = 1000;
    /**
     * Close code: the other side broke the protocol.
     */
    public static final int STATUS_PROTOCOL_ERROR = 1002;
    /**
     * Close code: a text message that isn't valid UTF-8.
     */
    public static final int STATUS_UNSUPPORTED_PAYLOAD = 1007;
    /**
     * Close code: a message bigger than the server accepts.
     */
    public static final int STATUS_TOO_LARGE = 1009;
    /**
     * Close code: something went wrong on the server.
     */
    public static final int STATUS_SERVER_ERROR = 1011;

    /**
     * Close codes a client may never send; RFC 6455 reserves them for other uses.
     */
    private static final Set<Integer> RESERVED_CLOSE_CODES = Set.of(1004, 1005, 1006, 1015, 1016, 1100, 2000, 2999);

    private WebSocketFrames() {
    }

    /**
     * One frame on the wire: the flags, the opcode and the unmasked payload.
     */
    public record Frame(boolean fin, boolean rsv1, boolean rsv2, boolean rsv3, int opcode, byte[] payload) {
        /**
         * A frame with no reserved bits set, the only kind this server writes.
         */
        static Frame of(boolean fin, int opcode, byte[] payload) {
            return new Frame(fin, false, false, false, opcode, payload);
        }
    }

    /**
     * A protocol violation. {@code closeCode} says how the server closes the connection.
     */
    public static final class ProtocolException extends IOException {
        final int closeCode;

        ProtocolException(int closeCode, String message) {
            super(message);
            this.closeCode = closeCode;
        }
    }

    /**
     * The {@code Sec-WebSocket-Accept} value that completes the handshake: base64(SHA-1(key + magic GUID)).
     *
     * <p>It proves to the client that the server understood the WebSocket request, rather than just echoing it.
     */
    public static String acceptKey(String clientKey) {
        try {
            byte[] sha1 = MessageDigest.getInstance("SHA-1")
                    .digest((clientKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(sha1);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Reads one client frame.
     *
     * <p>The header is checked <em>before</em> the payload is read. A client that announces a huge length can't make
     * the server allocate a huge buffer.
     */
    public static Frame read(DataInputStream in, int maxFragmentSize, int maxMessageSize, int currentMessageSize)
            throws IOException {
        int b0 = in.readUnsignedByte();
        int b1 = in.readUnsignedByte();
        boolean fin = (b0 & 0x80) != 0;
        boolean rsv1 = (b0 & 0x40) != 0;
        boolean rsv2 = (b0 & 0x20) != 0;
        boolean rsv3 = (b0 & 0x10) != 0;
        int opcode = b0 & 0x0F;

        if ((b1 & 0x80) == 0) {
            throw new ProtocolException(STATUS_PROTOCOL_ERROR, "received unmasked client frame");
        }
        long length = b1 & 0x7F;
        if (length == 126) {
            length = in.readUnsignedShort();
        } else if (length == 127) {
            length = in.readLong();
            if (length < 0) {
                throw new ProtocolException(STATUS_PROTOCOL_ERROR,
                        "invalid payload length: most significant bit of 64-bit length must be 0");
            }
        }
        validateHeader(fin, rsv1 || rsv2 || rsv3, opcode, length, maxFragmentSize, maxMessageSize, currentMessageSize);

        byte[] mask = new byte[4];
        in.readFully(mask);
        byte[] payload = new byte[(int) length];
        in.readFully(payload);
        for (int i = 0; i < payload.length; i++) {
            payload[i] ^= mask[i % 4];
        }
        if (opcode == OP_CLOSE) {
            validateClosePayload(payload);
        }
        return new Frame(fin, rsv1, rsv2, rsv3, opcode, payload);
    }

    /**
     * Rejects a frame from its header alone: reserved bits, frames or messages that are too large, and control
     * frames that are longer than 125 bytes or split into parts.
     */
    private static void validateHeader(boolean fin, boolean anyRsv, int opcode, long length,
                                       int maxFragmentSize, int maxMessageSize, int currentMessageSize)
            throws ProtocolException {
        if (anyRsv) {
            throw new ProtocolException(STATUS_PROTOCOL_ERROR, "frame has unsupported RSV bits set");
        }
        switch (opcode) {
            case OP_CONTINUATION, OP_TEXT, OP_BINARY -> {
                if (length > maxFragmentSize) {
                    throw new ProtocolException(STATUS_TOO_LARGE, "frame too large: payload size " + length
                            + " exceeds maximum of " + maxFragmentSize + " bytes");
                }
                if (opcode == OP_CONTINUATION && currentMessageSize + length > maxMessageSize) {
                    throw new ProtocolException(STATUS_TOO_LARGE, "message too large: size "
                            + (currentMessageSize + length) + " exceeds maximum of " + maxMessageSize + " bytes");
                }
            }
            case OP_CLOSE, OP_PING, OP_PONG -> {
                if (length > 125) {
                    throw new ProtocolException(STATUS_PROTOCOL_ERROR,
                            "invalid control frame: payload size " + length + " exceeds 125 bytes");
                }
                if (!fin) {
                    throw new ProtocolException(STATUS_PROTOCOL_ERROR,
                            "invalid control frame: control frame " + opcode + " must not be fragmented");
                }
            }
            default -> {
                // Unknown opcodes are rejected after reading, in the serve loop, as upstream.
            }
        }
    }

    /**
     * Checks a close frame's payload: empty, or a valid code followed by a UTF-8 reason.
     */
    private static void validateClosePayload(byte[] payload) throws ProtocolException {
        if (payload.length == 0) {
            return;
        }
        if (payload.length == 1) {
            throw new ProtocolException(STATUS_PROTOCOL_ERROR, "invalid close frame payload: must be at least 2 bytes");
        }
        int code = ((payload[0] & 0xff) << 8) | (payload[1] & 0xff);
        if (code < 1000 || code >= 5000) {
            throw new ProtocolException(STATUS_PROTOCOL_ERROR,
                    "invalid close frame payload: status code " + code + " out of range");
        }
        if (RESERVED_CLOSE_CODES.contains(code)) {
            throw new ProtocolException(STATUS_PROTOCOL_ERROR,
                    "invalid close frame payload: status code " + code + " is reserved");
        }
        if (payload.length > 2 && !isValidUtf8(payload, 2, payload.length - 2)) {
            throw new ProtocolException(STATUS_PROTOCOL_ERROR, "invalid close frame payload: reason must be valid UTF-8");
        }
    }

    /**
     * Whether the bytes are well-formed UTF-8, as RFC 6455 requires for every text message.
     */
    public static boolean isValidUtf8(byte[] bytes, int offset, int length) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, length));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    /**
     * Writes an unmasked server frame and flushes it.
     */
    public static void write(OutputStream out, Frame frame) throws IOException {
        int b0 = (frame.fin() ? 0x80 : 0) | (frame.rsv1() ? 0x40 : 0) | (frame.rsv2() ? 0x20 : 0)
                | (frame.rsv3() ? 0x10 : 0) | (frame.opcode() & 0x0F);
        out.write(b0);
        int len = frame.payload().length;
        if (len <= 125) {
            out.write(len);
        } else if (len <= 65535) {
            out.write(126);
            out.write(len >>> 8);
            out.write(len);
        } else {
            out.write(127);
            for (int shift = 56; shift >= 0; shift -= 8) {
                out.write((int) ((long) len >>> shift));
            }
        }
        out.write(frame.payload());
        out.flush();
    }

    /**
     * Writes a close frame: a 2-byte status code plus an optional UTF-8 reason.
     */
    public static void writeClose(OutputStream out, int code, String reason) throws IOException {
        byte[] text = reason == null ? new byte[0] : reason.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[2 + text.length];
        payload[0] = (byte) (code >>> 8);
        payload[1] = (byte) code;
        System.arraycopy(text, 0, payload, 2, text.length);
        write(out, Frame.of(true, OP_CLOSE, payload));
    }

    /**
     * Whether an error just means the client went away, which is a normal way for a session to end.
     */
    static boolean isEof(IOException e) {
        return e instanceof EOFException;
    }
}
