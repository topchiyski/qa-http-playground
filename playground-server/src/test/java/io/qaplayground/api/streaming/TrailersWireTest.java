package io.qaplayground.api.streaming;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import io.qaplayground.api.support.PlaygroundTarget;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

/**
 * Checks HTTP trailers on the raw wire, because most HTTP clients hide them.
 *
 * <p>It is tagged {@code trailers-wire} and left out of the default build. HTTP-filtering software (antivirus, ad
 * blockers, corporate proxies) often strips trailers even on localhost, and the build would fail for reasons
 * outside this project. Run it with {@code -Dgroups=trailers-wire -Dplayground.excludedGroups=none}.
 */
@Tag("trailers-wire")
class TrailersWireTest {

    /**
     * The trailer arrives after the last chunk, where RFC 9112 puts it.
     */
    @Test
    void trailerArrivesAfterLastChunk() throws Exception {
        URI base = URI.create(PlaygroundTarget.baseUri());
        try (Socket socket = new Socket(base.getHost(), base.getPort())) {
            OutputStream out = socket.getOutputStream();
            out.write(("GET /trailers?X-Checksum=abc HTTP/1.1\r\nHost: " + base.getHost()
                    + "\r\nTE: trailers\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            in.transferTo(raw);
            assertThat(raw.toString(StandardCharsets.ISO_8859_1), containsString("\r\n0\r\nX-Checksum: abc\r\n\r\n"));
        }
    }
}
