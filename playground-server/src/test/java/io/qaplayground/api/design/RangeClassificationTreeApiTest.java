package io.qaplayground.api.design;

import io.qaplayground.api.support.PlaygroundTest;
import io.qaplayground.testing.technique.ClassificationTree;
import io.restassured.response.Response;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code Range} classification tree (docs/testing/TEST-DESIGN.md section 11) on the wire, against
 * {@code /range/{n}}.
 *
 * <p>Every leaf appears at least once. The full set of combinations is unit-tested in the server's
 * RangeClassificationTreeTest.
 */
@ClassificationTree
class RangeClassificationTreeApiTest extends PlaygroundTest {

    @ParameterizedTest(name = "{0} | {1} | size {2}: Range \"{3}\" -> {4}")
    @CsvSource(delimiter = '|', value = {
            // syntax   | satisfiability  | size | Range             | status | Content-Range      | body
            "absent     | -               | 26   | -                 | 200    | -                  | abcdefghijklmnopqrstuvwxyz",
            "single     | inside          | 26   | bytes=0-4         | 206    | bytes 0-4/26       | abcde",
            "single     | overlaps end    | 26   | bytes=24-99       | 206    | bytes 24-25/26     | yz",
            "single     | inside          | 1    | bytes=0-0         | 206    | bytes 0-0/1        | a",
            "open-ended | inside          | 26   | bytes=23-         | 206    | bytes 23-25/26     | xyz",
            "suffix     | inside          | 26   | bytes=-2          | 206    | bytes 24-25/26     | yz",
            "suffix     | overlaps start  | 1    | bytes=-5          | 206    | bytes 0-0/1        | a",
            // "\\n" is a literal backslash-n (a real newline would split the CSV record); expanded below.
            "single     | beyond end      | 26   | bytes=26-         | 416    | bytes */26         | invalid range: failed to overlap\\n",
            "malformed  | -               | 26   | bytes=5-1         | 416    | -                  | invalid range\\n",
            "malformed  | -               | 26   | items=0-1         | 416    | -                  | invalid range\\n"})
    /**
     * One combination of leaves: the status and {@code Content-Range} on the wire.
     */
    void rangeLeaves(String syntax, String satisfiability, int size, String range, int status, String contentRange,
                     String body) {
        var req = given();
        if (!range.equals("-")) {
            req.header("Range", range);
        }
        Response r = req.get("/range/" + size);
        assertThat(r.statusCode()).isEqualTo(status);
        assertThat(r.header("Content-Range")).isEqualTo(contentRange.equals("-") ? null : contentRange);
        assertThat(r.asString()).isEqualTo(body.replace("\\n", "\n"));
    }

    /**
     * Several ranges get a {@code multipart/byteranges} body.
     */
    @ParameterizedTest(name = "multiple ranges \"{0}\" -> multipart/byteranges")
    @CsvSource(delimiter = '|', value = {"bytes=0-1,24-", "bytes=0-0, 2-2, -1"})
    void multipleRanges(String range) {
        Response r = given().header("Range", range).get("/range/26");
        assertThat(r.statusCode()).isEqualTo(206);
        assertThat(r.contentType()).startsWith("multipart/byteranges; boundary=");
    }
}
