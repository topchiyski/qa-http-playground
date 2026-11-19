package io.qaplayground.server.support;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses and picks status codes for {@code /status}, {@code /unstable}, {@code /redirect-to}, {@code /drip} and
 * {@code /bytes}.
 *
 * <p>It is framework-free, so its tests run in milliseconds with no server.
 */
public final class StatusCodes {

    private StatusCodes() {
    }

    /**
     * go-httpbin's {@code parseBoundedStatusCode}, with its error messages.
     */
    public static int parse(String input, int min, int max) {
        long code;
        try {
            code = GoStrconv.atoi(input);
        } catch (GoStrconv.NumberFormatError e) {
            throw new IllegalArgumentException("invalid status code: " + GoStrconv.quote(input) + ": " + e.getMessage());
        }
        if (code < min || code > max) {
            throw new IllegalArgumentException("invalid status code: " + code + " not in range [" + min + ", " + max + "]");
        }
        return (int) code;
    }

    /**
     * Parses {@code 200:0.7,500:0.3} and picks one code, using {@code random} in [0, 1).
     *
     * <p>A missing weight counts as 1, and the weights needn't add up to 1. Like the reference, the codes are not
     * range-checked here.
     */
    public static int weightedChoice(String raw, double random) {
        /**
         * One option of a weighted choice: a status code and its weight.
         */
        record Choice(int code, double weight) {
        }
        List<Choice> choices = new ArrayList<>();
        for (String pair : raw.split(",", -1)) {
            String[] parts = pair.split(":", 2);
            double weight = 1.0;
            if (parts.length == 2) {
                try {
                    weight = GoStrconv.parseFloat(parts[1]);
                } catch (GoStrconv.NumberFormatError e) {
                    throw new IllegalArgumentException("invalid weight value: " + GoStrconv.quote(parts[1]));
                }
            }
            try {
                choices.add(new Choice((int) GoStrconv.atoi(parts[0]), weight));
            } catch (GoStrconv.NumberFormatError e) {
                throw new IllegalArgumentException("invalid choice value: " + GoStrconv.quote(parts[0]));
            }
        }
        double total = choices.stream().mapToDouble(Choice::weight).sum();
        double target = random * total;
        double current = 0;
        for (Choice c : choices) {
            current += c.weight();
            if (target < current) {
                return c.code();
            }
        }
        throw new IllegalArgumentException("failed to select a weighted random choice");
    }

    /**
     * go-httpbin's {@code parseSeed}: a generator seeded with the given int64, or from the clock when absent.
     */
    public static GoRandom seededRandom(String rawSeed) {
        return rawSeed.isEmpty() ? GoRandom.fromClock() : new GoRandom(GoStrconv.parseInt(rawSeed, 64));
    }
}
