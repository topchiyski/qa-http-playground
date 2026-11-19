package io.qaplayground.testing;

import java.math.BigDecimal;
import java.util.List;

/**
 * 3-value boundary value analysis: for a boundary {@code b} and a step {@code ε}, the values {@code b-ε}, {@code b}
 * and {@code b+ε}.
 *
 * <p>One helper keeps every boundary test honest about which side each value is on.
 */
public final class Boundaries {

    private Boundaries() {
    }

    /**
     * A boundary value, and the side of the boundary it lies on.
     */
    public record Point(BigDecimal value, Side side) {

        /**
         * The value as a whole number, for integer boundaries.
         */
        public long asLong() {
            return value.longValueExact();
        }

        /**
         * Plain decimal text, such as {@code 0.999} or {@code 1024}, ready to put in a URL.
         */
        public String text() {
            return value.stripTrailingZeros().toPlainString();
        }

        @Override
        public String toString() {
            return text() + " (" + side.label + ")";
        }
    }

    /**
     * Where a value lies relative to the boundary: just below it, on it, or just above it.
     */
    public enum Side {
        BELOW("boundary - ε"), ON("boundary"), ABOVE("boundary + ε");

        private final String label;

        Side(String label) {
            this.label = label;
        }
    }

    /**
     * {@code limit-1}, {@code limit} and {@code limit+1}, for integer boundaries.
     */
    public static List<Point> around(long limit) {
        return around(BigDecimal.valueOf(limit), BigDecimal.ONE);
    }

    /**
     * {@code limit-ε}, {@code limit} and {@code limit+ε}, such as {@code around("1", "0.001")} for a duration in
     * seconds.
     */
    public static List<Point> around(String limit, String epsilon) {
        return around(new BigDecimal(limit), new BigDecimal(epsilon));
    }

    private static List<Point> around(BigDecimal limit, BigDecimal epsilon) {
        return List.of(
                new Point(limit.subtract(epsilon), Side.BELOW),
                new Point(limit, Side.ON),
                new Point(limit.add(epsilon), Side.ABOVE));
    }
}
