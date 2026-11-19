package io.qaplayground.api.content;

import io.qaplayground.api.support.PlaygroundTest;
import io.qaplayground.testing.technique.EquivalencePartitioning;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.equalTo;

/**
 * Tutorial: binary responses, and content negotiation with the {@code Accept} header.
 */
class ImagesTest extends PlaygroundTest {

    /**
     * Each image format has its own path and the matching {@code Content-Type}.
     */
    @EquivalencePartitioning
    @ParameterizedTest(name = "/image/{0} -> {1}")
    @CsvSource({"png, image/png", "jpeg, image/jpeg", "webp, image/webp", "svg, image/svg+xml", "avif, image/avif"})
    void eachFormat(String kind, String contentType) {
        when()
            .get("/image/" + kind)
        .then()
            .statusCode(200)
            .contentType(contentType);
    }

    /**
     * Check the file signature ("magic bytes") rather than trusting Content-Type.
     */
    @Test
    void pngMagicBytes() {
        byte[] png = when().get("/image/png").then().extract().asByteArray();
        org.junit.jupiter.api.Assertions.assertArrayEquals(
                new byte[]{(byte) 0x89, 'P', 'N', 'G'}, java.util.Arrays.copyOf(png, 4));
    }

    /**
     * {@code /image} picks the format your {@code Accept} header asks for; {@code image/*} and lists fall back to PNG.
     */
    @ParameterizedTest(name = "Accept: {0} -> {1}")
    @CsvSource({"image/webp, image/webp", "image/svg+xml, image/svg+xml", "image/jpeg, image/jpeg",
            "image/avif, image/avif", "image/*, image/png", "'image/webp,image/png', image/png"})
    @EquivalencePartitioning
    void negotiatedByAccept(String accept, String expected) {
        given()
            .accept(accept)
        .when()
            .get("/image")
        .then()
            .statusCode(200)
            .contentType(expected);
    }

    /**
     * A surprise worth knowing: REST Assured, like curl, sends {@code Accept: *}{@code /*} by default. This endpoint
     * doesn't accept that, so the answer is 415.
     */
    @EquivalencePartitioning
    @Test
    void wildcardAcceptIs415() {
        given()
            .accept("*/*")
        .when()
            .get("/image")
        .then()
            .statusCode(415)
            .body("error", equalTo("Unsupported Media Type"));
    }

    /**
     * An image format that doesn't exist is 404.
     */
    @EquivalencePartitioning
    @Test
    void unknownFormatIs404() {
        when().get("/image/gif").then().statusCode(404);
    }
}
