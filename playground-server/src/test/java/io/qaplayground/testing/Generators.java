package io.qaplayground.testing;

import java.util.Random;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.params.provider.Arguments;

/**
 * Seeded input generators for property-based tests, written as plain JUnit parameterized tests.
 *
 * <p>Every sample carries its seed in its display name, so you reproduce a failure by running the same seed again.
 * The base seed is fixed, so builds are repeatable. Pass {@code -Dproperty.seed=...} to explore new inputs.
 */
public final class Generators {

    /**
     * The seed of sample 0. Fixed by default; {@code -Dproperty.seed} changes it.
     */
    public static final long BASE_SEED = Long.getLong("property.seed", 20261110L);
    /**
     * How many samples each property test runs. {@code -Dproperty.samples} changes it.
     */
    public static final int SAMPLES = Integer.getInteger("property.samples", 500);

    private Generators() {
    }

    /**
     * {@link #SAMPLES} arguments. Sample {@code i} comes from {@code new Random(BASE_SEED + i)}.
     */
    public static <T> Stream<Arguments> samples(Function<Random, T> generator) {
        return IntStream.range(0, SAMPLES).mapToObj(i -> {
            long seed = BASE_SEED + i;
            return Arguments.argumentSet("seed=" + seed, generator.apply(new Random(seed)));
        });
    }

    /**
     * A string that mixes ASCII, Latin-1, the rest of the BMP, astral code points (emoji) and reserved URL characters.
     */
    public static String anyString(Random r, int maxLength) {
        int length = r.nextInt(maxLength + 1);
        StringBuilder sb = new StringBuilder();
        String reserved = "&=?#%+/ ;:@!$'()*,[]\"<>\\^`{|}~";
        for (int i = 0; i < length; i++) {
            switch (r.nextInt(5)) {
                case 0 -> sb.append((char) ('a' + r.nextInt(26)));
                case 1 -> sb.append(reserved.charAt(r.nextInt(reserved.length())));
                case 2 -> sb.append((char) (0xA0 + r.nextInt(0x60)));
                case 3 -> sb.append((char) (0x4E00 + r.nextInt(0x100)));
                default -> sb.appendCodePoint(0x1F600 + r.nextInt(0x40));
            }
        }
        return sb.toString();
    }
}
