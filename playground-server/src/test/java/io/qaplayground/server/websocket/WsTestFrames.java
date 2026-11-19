package io.qaplayground.server.websocket;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds masked client frames and decodes unmasked server frames, independently of the code under test.
 */
final class WsTestFrames {

    /**
     * The masking key for every client frame. Fixed, so failures are easy to read.
     */
    private static final byte[] MASK = {0x37, (byte) 0xfa, 0x21, 0x3d};

    private WsTestFrames() {
    }

    /**
     * A client frame, masked unless {@code masked} is false.
     */
    static byte[] client(boolean fin, int rsvBits, int opcode, byte[] payload, boolean masked) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write((fin ? 0x80 : 0) | (rsvBits << 4) | opcode);
        int maskBit = masked ? 0x80 : 0;
        int len = payload.length;
        if (len <= 125) {
            out.write(maskBit | len);
        } else if (len <= 65535) {
            out.write(maskBit | 126);
            out.write(len >>> 8);
            out.write(len);
        } else {
            out.write(maskBit | 127);
            for (int shift = 56; shift >= 0; shift -= 8) {
                out.write((int) ((long) len >>> shift));
            }
        }
        if (masked) {
            out.writeBytes(MASK);
            for (int i = 0; i < len; i++) {
                out.write(payload[i] ^ MASK[i % 4]);
            }
        } else {
            out.writeBytes(payload);
        }
        return out.toByteArray();
    }

    /**
     * A correct, masked client frame with no reserved bits.
     */
    static byte[] client(boolean fin, int opcode, byte[] payload) {
        return client(fin, 0, opcode, payload, true);
    }

    /**
     * A complete text message in one frame.
     */
    static byte[] text(String s) {
        return client(true, WebSocketFrames.OP_TEXT, s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * A close frame with this code and reason.
     */
    static byte[] close(int code, String reason) {
        byte[] r = reason.getBytes(StandardCharsets.UTF_8);
        byte[] p = new byte[2 + r.length];
        p[0] = (byte) (code >>> 8);
        p[1] = (byte) code;
        System.arraycopy(r, 0, p, 2, r.length);
        return client(true, WebSocketFrames.OP_CLOSE, p);
    }

    /**
     * The frames one after the other, as they would arrive on the wire.
     */
    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.writeBytes(p);
        }
        return out.toByteArray();
    }

    /**
     * A stream that delivers these frames to the code under test.
     */
    static DataInputStream in(byte[]... frames) {
        return new DataInputStream(new ByteArrayInputStream(concat(frames)));
    }

    /**
     * A frame the server wrote, decoded.
     */
    record ServerFrame(boolean fin, int opcode, byte[] payload) {
        /**
         * The payload as UTF-8 text.
         */
        String text() {
            return new String(payload, StandardCharsets.UTF_8);
        }

        /**
         * The status code of a close frame.
         */
        int closeCode() {
            return ((payload[0] & 0xff) << 8) | (payload[1] & 0xff);
        }

        /**
         * The reason text of a close frame.
         */
        String closeReason() {
            return new String(payload, 2, payload.length - 2, StandardCharsets.UTF_8);
        }
    }

    /**
     * Decodes everything the server wrote. Server frames must be unmasked.
     */
    static List<ServerFrame> decode(byte[] wire) throws IOException {
        List<ServerFrame> frames = new ArrayList<>();
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(wire));
        while (true) {
            int b0;
            try {
                b0 = in.readUnsignedByte();
            } catch (EOFException end) {
                return frames;
            }
            int b1 = in.readUnsignedByte();
            if ((b1 & 0x80) != 0) {
                throw new AssertionError("server frames must not be masked (RFC 6455 5.1)");
            }
            long len = b1 & 0x7f;
            if (len == 126) {
                len = in.readUnsignedShort();
            } else if (len == 127) {
                len = in.readLong();
            }
            byte[] payload = new byte[(int) len];
            in.readFully(payload);
            frames.add(new ServerFrame((b0 & 0x80) != 0, b0 & 0x0f, payload));
        }
    }
}
