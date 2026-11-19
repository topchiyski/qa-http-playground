package io.qaplayground.server.web;

import java.io.IOException;

import io.qaplayground.server.config.PlaygroundProperties;
import io.qaplayground.server.support.GoStrconv;
import io.qaplayground.server.support.HttpbinResponses;
import io.qaplayground.server.support.QueryString;
import io.qaplayground.server.websocket.EchoUpgradeHandler;
import io.qaplayground.server.websocket.WebSocketFrames;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static io.qaplayground.server.config.RawResponseEntityHandler.write;

/**
 * A WebSocket echo server whose size limits the client can choose.
 */
@Tag(name = "WebSocket", description = "WebSocket echo")
@RestController
public class WebSocketController {

    private final PlaygroundProperties properties;

    public WebSocketController(PlaygroundProperties properties) {
        this.properties = properties;
    }

    /**
     * Upgrades to a WebSocket and echoes every message.
     *
     * <p>{@code max_fragment_size} (default half of max-body-size) and {@code max_message_size} (default max-body-size)
     * let you test how your client handles fragmentation and "message too big" (close code 1009). Sessions end after
     * {@code playground.max-duration}.
     *
     * <p><b>HTTP concept:</b> the HTTP/1.1 Upgrade handshake ({@code 101 Switching Protocols},
     * {@code Sec-WebSocket-Key} and {@code Sec-WebSocket-Accept}), then a different protocol on the same connection.
     */
    @Operation(summary = "WebSocket echo (ws://host/websocket/echo)")
    @Parameter(name = "max_fragment_size", in = ParameterIn.QUERY, description = "Largest frame accepted and sent")
    @Parameter(name = "max_message_size", in = ParameterIn.QUERY, description = "Largest message accepted")
    @GetMapping("/websocket/echo")
    public void echo(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        var q = QueryString.parse(request.getQueryString());
        long maxBody = properties.maxBodyBytes();
        long maxFragment = maxBody / 2;
        long maxMessage = maxBody;
        try {
            maxFragment = limit(q, "max_fragment_size", maxFragment, maxBody);
            maxMessage = limit(q, "max_message_size", maxMessage, maxBody);
        } catch (IllegalArgumentException e) {
            write(response, HttpbinResponses.error(400, e.getMessage()));
            return;
        }
        if (maxFragment > maxMessage) {
            write(response, HttpbinResponses.error(400, "max_fragment_size " + maxFragment
                    + " must be less than or equal to max_message_size " + maxMessage));
            return;
        }

        String upgrade = request.getHeader("Upgrade");
        if (upgrade == null || !upgrade.equalsIgnoreCase("websocket")) {
            write(response, HttpbinResponses.error(400, "missing required `Upgrade: websocket` header"));
            return;
        }
        String version = request.getHeader("Sec-WebSocket-Version");
        if (!"13".equals(version)) {
            write(response, HttpbinResponses.error(400, "only websocket version \"13\" is supported, got "
                    + GoStrconv.quote(version == null ? "" : version)));
            return;
        }
        String key = request.getHeader("Sec-WebSocket-Key");
        if (key == null || key.isEmpty()) {
            write(response, HttpbinResponses.error(400, "missing required `Sec-Websocket-Key` header"));
            return;
        }

        response.setHeader("Connection", "upgrade");
        response.setHeader("Upgrade", "websocket");
        response.setHeader("Sec-Websocket-Accept", WebSocketFrames.acceptKey(key));
        EchoUpgradeHandler handler = request.upgrade(EchoUpgradeHandler.class); // container sends 101
        handler.configure((int) maxFragment, (int) maxMessage, properties.maxDuration());
    }

    /**
     * Reads a size limit from the query: the default when absent, an error unless it lies in [1, {@code max}].
     */
    private static long limit(java.util.Map<String, java.util.List<String>> q, String name, long dflt, long max) {
        String raw = QueryString.first(q, name);
        if (raw.isEmpty()) {
            return dflt;
        }
        long v;
        try {
            v = GoStrconv.parseInt(raw, 32);
        } catch (GoStrconv.NumberFormatError e) {
            throw new IllegalArgumentException("invalid " + name + ": " + e.getMessage());
        }
        if (v < 1 || v > max) {
            throw new IllegalArgumentException("invalid " + name + ": " + v + " not in range [1, " + max + "]");
        }
        return v;
    }
}
