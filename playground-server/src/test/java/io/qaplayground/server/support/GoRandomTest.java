package io.qaplayground.server.support;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.qaplayground.testing.Generators;
import io.qaplayground.testing.technique.BoundaryValue;
import io.qaplayground.testing.technique.EquivalencePartitioning;
import io.qaplayground.testing.technique.PropertyBased;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The port of Go's {@code math/rand}.
 *
 * <p>Seeded endpoints ({@code /bytes?seed=}, {@code /unstable?seed=}) must give learners the same output as go-httpbin
 * (risk R10). The known answers come from Go: the well-known first outputs of {@code rand.New(rand.NewSource(1))}.
 */
class GoRandomTest {

    @Test
    void knownAnswerInt63ForSeedOne() {
        // Go before 1.20: rand.Int() with the default seed 1 printed 5577006791947779410.
        assertThat(new GoRandom(1).int63()).isEqualTo(5577006791947779410L);
    }

    @Test
    void knownAnswerIntnForSeedOne() {
        // The sequence every Go programmer has seen from rand.Intn(100) with seed 1.
        GoRandom rng = new GoRandom(1);
        int[] got = IntStream.range(0, 10).map(i -> rng.intn(100)).toArray();
        assertThat(got).containsExactly(81, 87, 47, 59, 81, 18, 25, 40, 56, 0);
    }

    /**
     * Seeds are reduced modulo 2^31-1, and 0 is replaced by 89482311. These seeds form one equivalence class, so they
     * must produce identical sequences.
     */
    @EquivalencePartitioning
    @ParameterizedTest(name = "seed {0} behaves like seed 0")
    @ValueSource(longs = {2147483647L, 4294967294L, 89482311L})
    void seedsInTheSameClassGiveTheSameSequence(long seed) {
        assertThat(firstValues(new GoRandom(seed))).isEqualTo(firstValues(new GoRandom(0)));
    }

    /**
     * Seeds next to each other give different sequences, so a seed really selects an output.
     */
    @BoundaryValue
    @Test
    void neighbouringSeedsDiffer() {
        assertThat(firstValues(new GoRandom(2147483646L))).isNotEqualTo(firstValues(new GoRandom(2147483647L)));
        assertThat(firstValues(new GoRandom(2147483648L))).isEqualTo(firstValues(new GoRandom(1)));
        assertThat(firstValues(new GoRandom(-1))).isEqualTo(firstValues(new GoRandom(2147483646L)));
    }

    /**
     * The first few outputs of a generator, to compare two sequences.
     */
    private static long[] firstValues(GoRandom rng) {
        return new long[]{rng.int63(), rng.int63(), rng.int63()};
    }

    /**
     * {@code intn} refuses a bound of zero or less, as Go panics on it.
     */
    @BoundaryValue
    @ParameterizedTest(name = "intn({0}) is rejected")
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void intnRequiresPositiveBound(int n) {
        assertThatThrownBy(() -> new GoRandom(1).intn(n)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Seeded random pairs of a seed and a bound.
     */
    static Stream<Arguments> seedsAndBounds() {
        return Generators.samples(r -> new long[]{r.nextLong(), 1 + r.nextInt(Integer.MAX_VALUE - 1)});
    }

    /**
     * For any seed and bound, every output is in range, and a second generator with the same seed agrees.
     */
    @PropertyBased
    @ParameterizedTest
    @MethodSource("seedsAndBounds")
    void outputsStayInRangeAndAreDeterministic(long[] seedAndBound) {
        long seed = seedAndBound[0];
        int bound = (int) seedAndBound[1];
        GoRandom a = new GoRandom(seed);
        GoRandom b = new GoRandom(seed);
        for (int i = 0; i < 20; i++) {
            int x = a.intn(bound);
            assertThat(x).isBetween(0, bound - 1).isEqualTo(b.intn(bound));
            double f = a.float64();
            assertThat(f).isGreaterThanOrEqualTo(0.0).isLessThan(1.0).isEqualTo(b.float64());
            assertThat(a.int31()).isNotNegative().isEqualTo(b.int31());
        }
    }

    /**
     * Two generators seeded from the clock give different output, so unseeded requests really are random.
     */
    @Test
    void clockSeededGeneratorsDiffer() {
        assertThat(GoRandom.fromClock().int63()).isNotEqualTo(new GoRandom(0).int63());
    }
}
