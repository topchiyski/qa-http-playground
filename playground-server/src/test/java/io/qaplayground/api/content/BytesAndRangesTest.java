package io.qaplayground.api.content;

import io.qaplayground.api.support.PlaygroundTest;
import io.qaplayground.testing.technique.BoundaryValue;
import io.qaplayground.testing.technique.DecisionTable;
import io.qaplayground.testing.technique.EquivalencePartitioning;
import io.qaplayground.testing.technique.ErrorGuessing;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tutorial: binary payloads, chunked transfer, and range requests (resumable downloads).
 */
class BytesAndRangesTest extends PlaygroundTest {

    /**
     * The same seed gives the same bytes, and they are the bytes go-httpbin returns.
     */
    @Test
    void seededBytesAreReproducible() {
        byte[] bytes = when()
            .get("/bytes/16?seed=1234567890")
        .then()
            .statusCode(200)
            .contentType("application/octet-stream")
            .header("Content-Length", "16")
            .extract().asByteArray();
        assertArrayEquals(new byte[]{(byte) 0xbf, (byte) 0xcd, '*', (byte) 0xfa, 0x15, (byte) 0xa2, (byte) 0xb3, 'r',
                (byte) 0xc7, 0x07, (byte) 0x98, 'Z', '"', 0x02, 'J', (byte) 0x8e}, bytes);
    }

    /**
     * Zero bytes is a valid, empty response. Negative counts, counts past the limit and seeds that aren't int64 get
     * 400.
     */
    @BoundaryValue
    @Test
    void bytesEdgeCases() {
        when().get("/bytes/0").then().statusCode(200).header("Content-Length", "0");
        when().get("/bytes/-1").then().statusCode(400);
        when().get("/bytes/1048577").then().statusCode(400)
                .body("detail", equalTo("invalid byte count: 1048577 not in range [1, 1048576]"));
        when().get("/bytes/16?seed=12345678901234567890").then().statusCode(400);
        when().get("/bytes/16?seed=foo").then().statusCode(400);
    }

    /**
     * Streamed bytes use chunked encoding: no Content-Length, same bytes for the same seed.
     */
    @Test
    void streamBytesIsChunked() {
        Response r = when().get("/stream-bytes/16?seed=1234567890&chunk_size=4");
        r.then()
            .statusCode(200)
            .header("Content-Length", nullValue())
            .header("Transfer-Encoding", "chunked");
        byte[] plain = when().get("/bytes/16?seed=1234567890").asByteArray();
        assertArrayEquals(plain, r.asByteArray());
    }

    /**
     * A chunk size that isn't a number is refused.
     */
    @ErrorGuessing
    @Test
    void streamBytesBadChunkSize() {
        when().get("/stream-bytes/16?chunk_size=x").then().statusCode(400);
    }

    /**
     * Without a {@code Range} header, {@code /range/{n}} sends everything and says it accepts ranges.
     */
    @Test
    void fullRangeResource() {
        when()
            .get("/range/26")
        .then()
            .statusCode(200)
            .header("Accept-Ranges", "bytes")
            .header("ETag", "range26")
            .body(equalTo("abcdefghijklmnopqrstuvwxyz"));
    }

    /**
     * One range: 206 Partial Content and a Content-Range header.
     */
    @EquivalencePartitioning
    @Test
    void singleRange() {
        given()
            .header("Range", "bytes=10-14")
        .when()
            .get("/range/100")
        .then()
            .statusCode(206)
            .header("Content-Range", "bytes 10-14/100")
            .header("Content-Length", "5")
            .body(equalTo("klmno"));
    }

    /**
     * Suffix range: the last N bytes.
     */
    @EquivalencePartitioning
    @Test
    void suffixRange() {
        given().header("Range", "bytes=-3").get("/range/26")
            .then().statusCode(206).header("Content-Range", "bytes 23-25/26").body(equalTo("xyz"));
    }

    /**
     * Several ranges: a multipart/byteranges body with one part per range.
     */
    @EquivalencePartitioning
    @Test
    void multipleRanges() {
        given()
            .header("Range", "bytes=0-1,5-6")
        .when()
            .get("/range/26")
        .then()
            .statusCode(206)
            .contentType(startsWith("multipart/byteranges; boundary="))
            .body(containsString("Content-Range: bytes 0-1/26"))
            .body(containsString("Content-Range: bytes 5-6/26"));
    }

    /**
     * A range that starts past the end: 416 with Content-Range: bytes * /size.
     */
    @Test
    void unsatisfiableRange() {
        given()
            .header("Range", "bytes=50-")
        .when()
            .get("/range/26")
        .then()
            .statusCode(416)
            .header("Content-Range", "bytes */26")
            .body(equalTo("invalid range: failed to overlap\n"));
    }

    /**
     * If-Range with a stale validator: the server ignores Range and sends everything.
     */
    @DecisionTable
    @Test
    void ifRangeWithStaleEtagSendsFullBody() {
        given()
            .header("Range", "bytes=0-1")
            .header("If-Range", "\"stale\"")
        .when()
            .get("/range/26")
        .then()
            .statusCode(200);
    }

    /**
     * {@code /range/{n}} refuses a size of zero, a size that isn't a number, and a duration above the maximum.
     */
    @BoundaryValue
    @Test
    void rangeLimits() {
        when().get("/range/0").then().statusCode(400);
        when().get("/range/abc").then().statusCode(400);
        when().get("/range/10?duration=11s").then().statusCode(400);
    }

    /**
     * {@code duration} spreads the transfer over time, so a range download can imitate a slow network.
     */
    @Test
    void rangeDurationSlowsTheResponse() {
        long start = System.nanoTime();
        when().get("/range/10?duration=500ms").then().statusCode(200);
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertEquals(true, millis >= 450, "expected ~500ms, took " + millis);
    }
}
