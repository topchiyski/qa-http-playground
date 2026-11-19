package io.qaplayground.server.support;

import java.util.List;

import io.qaplayground.server.support.ServeContent.Range;
import io.qaplayground.server.support.ServeContent.RangeError;
import io.qaplayground.testing.technique.ClassificationTree;
import io.qaplayground.testing.technique.ErrorGuessing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The {@code Range} header, designed with the classification tree in docs/testing/TEST-DESIGN.md section 11.
 *
 * <p>The classifications are syntax (absent, single, open-ended, suffix, multiple, malformed), satisfiability
 * (inside, overlapping the end, beyond the end) and resource size (0, 1, 26). Each row names the leaves it covers,
 * so you can see which corner of the tree it tests.
 */
@ClassificationTree
class RangeClassificationTreeTest {

    /**
     * Parses a {@code Range} header for a resource of {@code size} bytes.
     */
    private static List<Range> parse(String header, long size) throws RangeError {
        return ServeContent.parseRange(header, size);
    }

    @Test
    void absentHeader() throws RangeError {
        assertThat(parse(null, 26)).isEmpty();
        assertThat(parse("", 26)).isEmpty();
    }

    @ParameterizedTest(name = "{0} | {1} | size {2}: \"{3}\" -> start {4}, length {5}")
    @CsvSource(delimiter = '|', value = {
            // syntax      | satisfiability | size | header              | start | length
            "single        | inside         | 26   | bytes=0-4           | 0     | 5",
            "single        | overlaps end   | 26   | bytes=20-40         | 20    | 6",
            "single        | inside         | 1    | bytes=0-0           | 0     | 1",
            "open-ended    | inside         | 26   | bytes=10-           | 10    | 16",
            "open-ended    | inside         | 1    | bytes=0-            | 0     | 1",
            "suffix        | inside         | 26   | bytes=-5            | 21    | 5",
            "suffix        | overlaps start | 26   | bytes=-30           | 0     | 26",
            "suffix        | empty          | 26   | bytes=-0            | 26    | 0",   // same as Go's parseRange
            "whitespace    | inside         | 26   | bytes= 2 - 3        | 2     | 2"})
    void singleSatisfiableRanges(String syntax, String satisfiability, long size, String header, long start, long length)
            throws RangeError {
        assertThat(parse(header, size)).containsExactly(new Range(start, length));
    }

    @Test
    void multipleRangesKeepOrderAndSkipEmptyItems() throws RangeError {
        assertThat(parse("bytes=0-1, ,5-6,-2", 26))
                .containsExactly(new Range(0, 2), new Range(5, 2), new Range(24, 2));
    }

    /**
     * In a list of ranges, the impossible ones are dropped as long as one range can be served.
     */
    @Test
    void unsatisfiableItemsAreDroppedWhenOthersOverlap() throws RangeError {
        assertThat(parse("bytes=30-40,0-1", 26)).containsExactly(new Range(0, 2));
    }

    @ParameterizedTest(name = "beyond the end, size {1}: \"{0}\"")
    @CsvSource({"bytes=26-, 26", "bytes=30-40, 26", "bytes=26-26, 26", "bytes=0-1, 0"})
    void unsatisfiable(String header, long size) {
        assertThatThrownBy(() -> parse(header, size))
                .isInstanceOf(RangeError.class)
                .hasMessage("invalid range: failed to overlap")
                .satisfies(e -> assertThat(((RangeError) e).noOverlap).isTrue());
    }

    @ErrorGuessing
    @ParameterizedTest(name = "malformed: \"{0}\"")
    @CsvSource(delimiter = '|', value = {
            "items=0-1", "bytes=0", "bytes=5-1", "bytes=x-1", "bytes=1-x", "bytes=-", "bytes=--1",
            "bytes=-1-", "bytes=-x", "bytes=99999999999999999999-", "bytes=0-1,x"})
    void malformed(String header) {
        assertThatThrownBy(() -> parse(header, 26))
                .isInstanceOf(RangeError.class)
                .hasMessage("invalid range")
                .satisfies(e -> assertThat(((RangeError) e).noOverlap).isFalse());
    }

    @Test
    void contentRangeHeaderFormat() {
        assertThat(new Range(21, 5).contentRange(26)).isEqualTo("bytes 21-25/26");
    }
}
