package io.qaplayground.server.websocket;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Arrays;

import io.qaplayground.server.support.GoDuration;
import io.qaplayground.server.websocket.WebSocketFrames.Frame;
import io.qaplayground.server.websocket.WebSocketFrames.ProtocolException;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.WebConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.qaplayground.server.websocket.WebSocketFrames.OP_BINARY;
import static io.qaplayground.server.websocket.WebSocketFrames.OP_CLOSE;
import static io.qaplayground.server.websocket.WebSocketFrames.OP_CONTINUATION;
import static io.qaplayground.server.websocket.WebSocketFrames.OP_PING;
import static io.qaplayground.server.websocket.WebSocketFrames.OP_PONG;
import static io.qaplayground.server.websocket.WebSocketFrames.OP_TEXT;

/**
 * Runs an echo WebSocket on a connection the servlet container handed over after {@code 101 Switching Protocols}.
 *
 * <p>This is Servlet HTTP Upgrade, the Java equivalent of Go's connection hijacking. Every complete message is sent
 * back, split into frames of at most {@code maxFragmentSize} bytes.
 *
 * <p>The session ends after {@code maxDuration} (risk R3). go-httpbin sets a socket deadline, so its reads and
 * writes fail and the TCP connection is dropped. The Servlet API can't drop or time out an upgraded socket
 * ({@code WebConnection.close()} in Tomcat only sets flags). So at the deadline we send a close frame
 * {@code 1001 Going Away}, stop echoing, and interrupt the reader.
 */
public class EchoUpgradeHandler implements HttpUpgradeHandler {

    /**
     * Close code sent when the session reaches its time limit: "the server is going away".
     */
    static final int STATUS_GOING_AWAY = 1001;

    private static final Logger log = LoggerFactory.getLogger(EchoUpgradeHandler.class);

    private int maxFragmentSize;
    private int maxMessageSize;
    private Duration maxDuration;

    /**
     * Guards frame writes: the deadline thread must never interleave bytes inside an echo frame.
     */
    private final Object writeLock = new Object();
    private volatile boolean expired;

    /**
     * Sets the session limits. The controller calls it between {@code request.upgrade()} and the container's
     * {@code init}.
     */
    public void configure(int maxFragmentSize, int maxMessageSize, Duration maxDuration) {
        this.maxFragmentSize = maxFragmentSize;
        this.maxMessageSize = maxMessageSize;
        this.maxDuration = maxDuration;
    }

    /**
     * Starts the session and its deadline, each on a virtual thread, so a waiting session costs almost nothing.
     */
    @Override
    public void init(WebConnection connection) {
        Thread session = Thread.ofVirtual().name("websocket-echo").start(() -> serve(connection));
        Thread.ofVirtual().name("websocket-deadline").start(() -> {
            try {
                Thread.sleep(maxDuration);
                if (!session.isAlive()) {
                    return; // the client already closed; nothing to end
                }
                expire(connection.getOutputStream());
                session.interrupt(); // best effort: wake a reader blocked on a silent client
            } catch (InterruptedException | IOException | IllegalStateException alreadyClosed) {
                // The session ended between the check and the write. Tomcat reports a closed upgraded
                // stream with IllegalStateException, which used to escape as an uncaught thread error.
                log.debug("websocket deadline: session already closed ({})", alreadyClosed.toString());
            }
        });
    }

    @Override
    public void destroy() {
        // nothing to release; the serve loop ends when the connection closes
    }

    /**
     * Ends the session at the deadline with a close frame; later frames from the client are ignored.
     */
    void expire(OutputStream out) throws IOException {
        synchronized (writeLock) {
            if (expired) {
                return;
            }
            expired = true;
            WebSocketFrames.writeClose(out, STATUS_GOING_AWAY,
                    "session exceeded max duration of " + GoDuration.format(maxDuration));
        }
    }

    /**
     * Runs one session, and makes sure the connection is closed however the session ends.
     */
    private void serve(WebConnection connection) {
        try (connection) {
            DataInputStream in = new DataInputStream(connection.getInputStream());
            OutputStream out = connection.getOutputStream();
            serveLoop(in, out);
        } catch (Exception e) {
            log.debug("websocket session ended: {}", e.toString());
        }
    }

    /**
     * Writes one frame, unless the session has already expired.
     */
    private void send(OutputStream out, Frame frame) throws IOException {
        synchronized (writeLock) {
            if (!expired) {
                WebSocketFrames.write(out, frame);
            }
        }
    }

    /**
     * Writes a close frame, unless the session has already expired.
     */
    private void sendClose(OutputStream out, int code, String reason) throws IOException {
        synchronized (writeLock) {
            if (!expired) {
                WebSocketFrames.writeClose(out, code, reason);
            }
        }
    }

    /**
     * The session's state machine: read a frame, react, repeat until close.
     *
     * <p>It is package-private so unit tests can drive it with in-memory streams, without a network.
     */
    void serveLoop(DataInputStream in, OutputStream out) throws IOException {
        ByteArrayOutputStream message = null; // in-progress fragmented message
        boolean binary = false;
        while (true) {
            Frame frame;
            try {
                frame = WebSocketFrames.read(in, maxFragmentSize, maxMessageSize, message == null ? 0 : message.size());
            } catch (ProtocolException e) {
                sendClose(out, e.closeCode, e.getMessage());
                return;
            }
            if (expired) {
                return; // the deadline already sent the close frame
            }
            switch (frame.opcode()) {
                case OP_TEXT, OP_BINARY -> {
                    if (message != null) {
                        sendClose(out, WebSocketFrames.STATUS_PROTOCOL_ERROR, "continuation frame expected");
                        return;
                    }
                    binary = frame.opcode() == OP_BINARY;
                    message = new ByteArrayOutputStream();
                    message.write(frame.payload());
                }
                case OP_CONTINUATION -> {
                    if (message == null) {
                        sendClose(out, WebSocketFrames.STATUS_PROTOCOL_ERROR, "unexpected continuation frame");
                        return;
                    }
                    message.write(frame.payload());
                }
                case OP_CLOSE -> {
                    sendClose(out, WebSocketFrames.STATUS_NORMAL, null);
                    return;
                }
                case OP_PING -> {
                    send(out, Frame.of(true, OP_PONG, frame.payload()));
                    continue;
                }
                case OP_PONG -> {
                    continue;
                }
                default -> {
                    sendClose(out, WebSocketFrames.STATUS_PROTOCOL_ERROR, "unsupported opcode: " + frame.opcode());
                    return;
                }
            }
            if (frame.fin()) {
                byte[] payload = message.toByteArray();
                // RFC 6455 5.6: the *message* must be valid UTF-8. Fragment boundaries may split a
                // character, so fragments are not checked on their own (go-httpbin checks per frame).
                if (!binary && !WebSocketFrames.isValidUtf8(payload, 0, payload.length)) {
                    sendClose(out, WebSocketFrames.STATUS_UNSUPPORTED_PAYLOAD, "invalid UTF-8");
                    return;
                }
                echo(out, payload, binary);
                message = null;
            }
        }
    }

    /**
     * Sends a message back in fragments of at most {@code maxFragmentSize} bytes.
     */
    private void echo(OutputStream out, byte[] payload, boolean binary) throws IOException {
        int offset = 0;
        do {
            int end = Math.min(offset + maxFragmentSize, payload.length);
            int opcode = offset == 0 ? (binary ? OP_BINARY : OP_TEXT) : OP_CONTINUATION;
            send(out, Frame.of(end == payload.length, opcode, Arrays.copyOfRange(payload, offset, end)));
            offset = end;
        } while (offset < payload.length);
    }
}
