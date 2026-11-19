package io.qaplayground.server.support;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Base64 decoding with Go's rules and Go's error messages ({@code encoding/base64}, padded, non-strict).
 *
 * <p>Java's decoder accepts missing padding; Go's doesn't. Go also says where decoding failed:
 * {@code illegal base64 data at input byte 4}. {@code /base64} returns those messages, so we port Go's
 * {@code decodeQuantum}.
 */
public final class GoBase64 {

    /**
     * The standard alphabet ({@code +} and {@code /}).
     */
    public static final String STD = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    /**
     * The URL-safe alphabet ({@code -} and {@code _}).
     */
    public static final String URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    private GoBase64() {
    }

    /**
     * Go's {@code CorruptInputError}: says at which input byte decoding failed.
     */
    public static final class CorruptInputError extends Exception {
        public CorruptInputError(long offset) {
            super("illegal base64 data at input byte " + offset);
        }
    }

    /**
     * Decodes padded base64 in the given alphabet, failing exactly where Go would.
     */
    public static byte[] decode(String input, String alphabet) throws CorruptInputError {
        byte[] src = input.getBytes(StandardCharsets.UTF_8);
        int[] map = new int[256];
        Arrays.fill(map, -1);
        for (int i = 0; i < alphabet.length(); i++) {
            map[alphabet.charAt(i)] = i;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int si = 0;
        while (si < src.length) {
            si = decodeQuantum(out, src, si, map);
        }
        return out.toByteArray();
    }

    /**
     * Decodes up to 4 input characters into up to 3 bytes, and returns the next input index.
     */
    private static int decodeQuantum(ByteArrayOutputStream out, byte[] src, int si, int[] map) throws CorruptInputError {
        int[] dbuf = new int[4];
        int dlen = 4;
        Integer trailingGarbageAt = null;
        for (int j = 0; j < 4; j++) {
            if (src.length == si) {
                if (j == 0) {
                    return si;
                }
                throw new CorruptInputError(si - j); // padded encoding: input ended mid-quantum
            }
            int in = src[si] & 0xff;
            si++;
            int value = map[in];
            if (value >= 0) {
                dbuf[j] = value;
                continue;
            }
            if (in == '\n' || in == '\r') {
                j--;
                continue;
            }
            if (in != '=') {
                throw new CorruptInputError(si - 1);
            }
            // Padding reached.
            switch (j) {
                case 0, 1 -> throw new CorruptInputError(si - 1);
                case 2 -> {
                    while (si < src.length && (src[si] == '\n' || src[si] == '\r')) {
                        si++;
                    }
                    if (si == src.length) {
                        throw new CorruptInputError(src.length);
                    }
                    if (src[si] != '=') {
                        throw new CorruptInputError(si - 1);
                    }
                    si++;
                }
                default -> {
                }
            }
            while (si < src.length && (src[si] == '\n' || src[si] == '\r')) {
                si++;
            }
            if (si < src.length) {
                trailingGarbageAt = si;
            }
            dlen = j;
            break;
        }
        int val = dbuf[0] << 18 | dbuf[1] << 12 | dbuf[2] << 6 | dbuf[3];
        byte b0 = (byte) (val >> 16);
        byte b1 = (byte) (val >> 8);
        byte b2 = (byte) val;
        if (dlen >= 2) {
            out.write(b0);
        }
        if (dlen >= 3) {
            out.write(b1);
        }
        if (dlen == 4) {
            out.write(b2);
        }
        if (trailingGarbageAt != null) {
            throw new CorruptInputError(trailingGarbageAt);
        }
        return si;
    }
}
