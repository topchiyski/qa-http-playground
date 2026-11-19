package io.qaplayground.api.content;

import java.util.List;

import io.qaplayground.api.support.PlaygroundTest;
import io.qaplayground.testing.technique.BoundaryValue;
import io.qaplayground.testing.technique.EquivalencePartitioning;
import io.qaplayground.testing.technique.ErrorGuessing;
import io.qaplayground.testing.technique.SecurityTest;
import io.restassured.config.DecoderConfig;
import io.restassured.config.RedirectConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;

/**
 * Tutorial: response formats (JSON, XML, HTML), compressed responses, base64, link pages, and response headers you
 * control.
 */
class ContentTest extends PlaygroundTest {

    // ---- formats -----------------------------------------------------------------------

    /**
     * GPath walks nested JSON with dots, and reads lists with indexes.
     */
    @Test
    void jsonWithNestedPaths() {
        when()
            .get("/json")
        .then()
            .contentType(ContentType.JSON)
            .body("slideshow.title", equalTo("Sample Slide Show"))
            .body("slideshow.slides", hasSize(2))
            .body("slideshow.slides[1].items[0]", equalTo("Why <em>WonderWidgets</em> are great"));
    }

    /**
     * REST Assured switches to XPath-like GPath for XML bodies.
     */
    @Test
    void xmlWithAttributes() {
        when()
            .get("/xml")
        .then()
            .contentType("application/xml")
            .body("slideshow.@title", equalTo("Sample Slide Show"))
            .body("slideshow.slide[0].title", equalTo("Wake up to WonderWidgets!"));
    }

    /**
     * An HTML page: check the type, then the text you care about.
     */
    @Test
    void htmlPage() {
        when().get("/html").then().contentType(ContentType.HTML).body(containsString("Herman Melville"));
    }

    /**
     * The UTF-8 page is HTML, and its characters arrive intact.
     */
    @Test
    void utf8Page() {
        when().get("/encoding/utf8").then().contentType(ContentType.HTML).body(containsString("∮ E⋅da = Q"));
    }

    // ---- compression -------------------------------------------------------------------

    /**
     * REST Assured decompresses gzip/deflate transparently.
     */
    @ParameterizedTest(name = "/{0}")
    @CsvSource({"gzip, gzipped", "deflate, deflated"})
    void compressedResponsesAreDecoded(String path, String flag) {
        when()
            .get("/" + path)
        .then()
            .statusCode(200)
            .header("Content-Encoding", path)
            .body(flag, equalTo(true))
            .body("method", equalTo("GET"));
    }

    /**
     * With decompression off you see the raw gzip bytes (magic number 1f 8b).
     */
    @Test
    void rawGzipBytes() {
        byte[] raw = given()
            .config(RestAssuredConfig.config().decoderConfig(DecoderConfig.decoderConfig().noContentDecoders()))
        .when()
            .get("/gzip")
        .then()
            .extract().asByteArray();
        org.junit.jupiter.api.Assertions.assertEquals((byte) 0x1f, raw[0]);
        org.junit.jupiter.api.Assertions.assertEquals((byte) 0x8b, raw[1]);
    }

    /**
     * {@code /brotli} answers 501 Not Implemented, as in go-httpbin.
     */
    @Test
    void brotliIsNotImplemented() {
        when().get("/brotli").then().statusCode(501).body("error", equalTo("Not Implemented"));
    }

    // ---- base64 ------------------------------------------------------------------------

    /**
     * Decoding and encoding both work, and the decoder also accepts the URL-safe alphabet.
     */
    @Test
    void base64DecodeAndEncode() {
        when().get("/base64/aGVsbG8gd29ybGQ=").then().statusCode(200).body(equalTo("hello world"));
        when().get("/base64/decode/YWJjMTIzIT8kKiYoKSctPUB-").then().body(equalTo("abc123!?$*&()'-=@~"));
        when().get("/base64/encode/hello world").then().body(equalTo("aGVsbG8gd29ybGQ="));
    }

    /**
     * Go's decoder requires padding and reports where decoding failed.
     */
    @ErrorGuessing
    @Test
    void base64InvalidInput() {
        when()
            .get("/base64/invalid_base64_encoded_string")
        .then()
            .statusCode(400)
            .body("detail", startsWith("base64 decode failed: illegal base64 data at input byte "));
        when().get("/base64/aGVsbG8").then().statusCode(400);
        when().get("/base64/unknown/aGVsbG8=").then().statusCode(400).body("detail", equalTo("invalid operation: unknown"));
    }

    /**
     * The response Content-Type is yours to choose; dangerous types are HTML-escaped.
     */
    @SecurityTest
    @Test
    void base64ContentTypeAndEscaping() {
        given().queryParam("content-type", "application/json")
            .get("/base64/eyJzZXJ2ZXIiOiAiZ28taHR0cGJpbiJ9Cg==")
            .then().contentType(ContentType.JSON).body("server", equalTo("go-httpbin"));

        given().queryParam("content-type", "text/html")
            .get("/base64/PGltZy9zcmMvb25lcnJvcj1hbGVydCgneHNzJyk+")
            .then().body(equalTo("&lt;img/src/onerror=alert(&#39;xss&#39;)&gt;"));
    }

    /**
     * A base64 URL with a segment missing doesn't match a route, so it is 404.
     */
    @EquivalencePartitioning
    @Test
    void base64MissingSegmentsAre404() {
        when().get("/base64/").then().statusCode(404);
        when().get("/base64/decode/").then().statusCode(404);
    }

    // ---- links -------------------------------------------------------------------------

    /**
     * {@code /links/{n}} redirects to its first page.
     */
    @Test
    void linksRedirectsToFirstPage() {
        given()
            .config(RestAssuredConfig.config().redirect(RedirectConfig.redirectConfig().followRedirects(false)))
        .when()
            .get("/links/5")
        .then()
            .statusCode(302)
            .header("Location", "/links/5/0");
    }

    /**
     * XmlPath in HTML mode can count the links on the page.
     */
    @Test
    void linksPageHasNMinusOneLinks() {
        when()
            .get("/links/5/2")
        .then()
            .contentType(ContentType.HTML)
            .body(containsString("<a href=\"/links/5/1\">1</a> 2 <a href=\"/links/5/3\">3</a>"));
    }

    /**
     * Link counts outside the allowed range, and offsets that aren't numbers, are refused.
     */
    @BoundaryValue
    @Test
    void linksLimits() {
        when().get("/links/257").then().statusCode(400)
                .body("detail", equalTo("invalid link count: 257 must be in range [0, 256]"));
        when().get("/links/5/x").then().statusCode(400);
    }

    // ---- response headers --------------------------------------------------------------

    /**
     * Each query parameter comes back as a response header, and in the JSON body.
     */
    @Test
    void responseHeadersFromQuery() {
        given()
            .queryParam("X-Demo", "1", "2")
            .queryParam("Cache-Control", "no-store")
        .when()
            .get("/response-headers")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .header("Cache-Control", "no-store")
            .body("'X-Demo'", equalTo(List.of("1", "2")))
            .body("'Content-Type'[0]", equalTo("application/json; charset=utf-8"));
    }

    /**
     * Setting an {@code ETag} here doesn't make the endpoint conditional: it always answers 200.
     *
     * <p>A regression guard: Spring MVC would otherwise turn this into a 304 on its own.
     */
    @Test
    void responseHeadersEtagIsNotConditional() {
        given()
            .queryParam("ETag", "\"x\"")
            .header("If-None-Match", "\"x\"")
        .when()
            .get("/response-headers")
        .then()
            .statusCode(200)
            .header("ETag", "\"x\"");
    }

    /**
     * With an HTML {@code Content-Type}, the echoed values are HTML-escaped.
     */
    @SecurityTest
    @Test
    void responseHeadersEscapesDangerousContentType() {
        given()
            .queryParam("Content-Type", "text/html")
            .queryParam("x", "<b>")
        .when()
            .get("/response-headers")
        .then()
            .contentType(ContentType.HTML)
            .body(containsString("\"&lt;b&gt;\""));
    }
}
