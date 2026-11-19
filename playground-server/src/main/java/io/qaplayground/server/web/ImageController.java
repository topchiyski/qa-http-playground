package io.qaplayground.server.web;

import io.qaplayground.server.support.HttpbinResponses;
import io.qaplayground.server.support.StaticAssets;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Small images in five formats: go-httpbin's assets, MIT licensed.
 */
@Tag(name = "Images", description = "Images chosen by Accept header or by format")
@RestController
public class ImageController {

    /**
     * Picks a format from the {@code Accept} header.
     *
     * <p>PNG for no header or {@code image/*}. Otherwise the first of png, webp, svg+xml, jpeg and avif that the header
     * mentions. Anything else, {@code *}{@code /*} included, gets 415.
     *
     * <p><b>HTTP concept:</b> content negotiation. This server only does a substring match and ignores q-values, like
     * the
     * reference.
     */
    @Operation(summary = "Image in a format chosen by the Accept header")
    @RequestMapping("/image")
    public ResponseEntity<byte[]> image(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        if (accept == null || accept.isEmpty() || accept.contains("image/*") || accept.contains("image/png")) {
            return image("png");
        }
        if (accept.contains("image/webp")) {
            return image("webp");
        }
        if (accept.contains("image/svg+xml")) {
            return image("svg");
        }
        if (accept.contains("image/jpeg")) {
            return image("jpeg");
        }
        if (accept.contains("image/avif")) {
            return image("avif");
        }
        return HttpbinResponses.error(415, null);
    }

    /**
     * An image in one format: {@code png}, {@code jpeg}, {@code webp}, {@code svg} or {@code avif}. Anything else is
     * 404.
     *
     * <p><b>HTTP concept:</b> binary responses. Assert on {@code Content-Type}, {@code Content-Length} and the file
     * signature ("magic bytes").
     */
    @Operation(summary = "Image in a given format")
    @RequestMapping("/image/{kind}")
    public ResponseEntity<byte[]> imageOfKind(
            @Parameter(description = "png, jpeg, webp, svg or avif") @PathVariable String kind) {
        return image(kind);
    }

    /**
     * The bundled image of this kind, or 404 when there is none.
     */
    private static ResponseEntity<byte[]> image(String kind) {
        byte[] bytes = kind.matches("[a-z]+") ? StaticAssets.find("static-httpbin/image." + kind) : null;
        if (bytes == null) {
            return HttpbinResponses.error(404, null);
        }
        String contentType = kind.equals("svg") ? "image/svg+xml" : "image/" + kind;
        return HttpbinResponses.bytes(200, contentType, bytes);
    }
}
