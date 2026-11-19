package io.qaplayground.api.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import io.qaplayground.testing.trace.Trace;

/**
 * Sends a request exactly as written, over a plain socket.
 *
 * <p>Why: real clients protect you from yourself. REST Assured refuses to send {@code /get?a=%4} ("Malformed escape
 * pair") and normalises {@code ?a&a=}, and Apache HttpClient waits forever after a final {@code 101}. When a test is
 * <em>about</em> malformed or unusual input, the client must not repair it first. This is the error-guessing
 * tester's tool.
 */
public final class RawHttp {

    /**
     * The status line, header block and body, as received.
     */
    public record Reply(String statusLine, String headers, String body) {
        /**
         * The status code from the status line.
         */
        public int status() {
            return Integer.parseInt(statusLine.split(" ")[1]);
        }
    }

    private RawHttp() {
    }

    /**
     * GET {@code pathAndQuery} with {@code Connection: close}, reading until the server closes.
     */
    public static Reply get(String pathAndQuery) {
        URI base = URI.create(PlaygroundTarget.baseUri());
        Trace.path(pathAndQuery);
        try (Socket socket = new Socket(base.getHost(), base.getPort())) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write(("GET " + pathAndQuery + " HTTP/1.1\r\nHost: " + base.getHost() + "\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            in.transferTo(raw);
            String all = raw.toString(StandardCharsets.UTF_8);
            int lineEnd = all.indexOf("\r\n");
            int headEnd = all.indexOf("\r\n\r\n");
            return new Reply(all.substring(0, lineEnd), all.substring(lineEnd + 2, headEnd), all.substring(headEnd + 4));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
