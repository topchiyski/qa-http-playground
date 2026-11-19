package io.qaplayground.server.support;

import java.time.Duration;
import java.util.Map;

/**
 * Parses and prints durations the way go-httpbin does.
 *
 * <p>Endpoints such as {@code /delay/{d}} accept a Go duration ({@code 1.5s}, {@code 300ms}, {@code 1m30s}) or a
 * plain number of seconds ({@code 1.5}). Error messages print durations the Go way ({@code 10s}, {@code 1m30s},
 * {@code 500ms}). So both directions are ported from Go's {@code time} package.
 */
public final class GoDuration {

    private static final long NS = 1;
    private static final long US = 1_000 * NS;
    private static final long MS = 1_000 * US;
    private static final long S = 1_000 * MS;
    private static final long M = 60 * S;
    private static final long H = 60 * M;

    /**
     * Unit suffixes Go accepts, in nanoseconds. Both spellings of microseconds are included.
     */
    private static final Map<String, Long> UNITS = Map.of(
            "ns", NS, "us", US, "µs", US, "μs", US, "ms", MS, "s", S, "m", M, "h", H);

    private GoDuration() {
    }

    /**
     * Thrown with the message Go would produce.
     */
    public static final class DurationError extends IllegalArgumentException {
        public DurationError(String message) {
            super(message);
        }
    }

    /**
     * go-httpbin's {@code parseBoundedDuration}: parse, then require {@code min <= d <= max}.
     *
     * <p>Like the reference, a value out of range is an error. It is not quietly clamped.
     */
    public static Duration parseBounded(String input, Duration min, Duration max) {
        Duration d = parse(input);
        if (d.compareTo(max) > 0) {
            throw new DurationError("duration " + format(d) + " longer than " + format(max));
        }
        if (d.compareTo(min) < 0) {
            throw new DurationError("duration " + format(d) + " shorter than " + format(min));
        }
        return d;
    }

    /**
     * go-httpbin's {@code parseDuration}: a Go duration string, else a number of seconds.
     */
    public static Duration parse(String input) {
        try {
            return parseGo(input);
        } catch (DurationError notAGoDuration) {
            double seconds;
            try {
                seconds = GoStrconv.parseFloat(input);
            } catch (GoStrconv.NumberFormatError e) {
                throw new DurationError(e.getMessage());
            }
            return fromFloatSeconds(seconds);
        }
    }

    /**
     * The largest whole number of milliseconds that still fits in a Go duration.
     */
    private static final long MAX_MILLIS = Long.MAX_VALUE / 1_000_000;

    /**
     * Go's {@code time.Duration(n*1000) * time.Millisecond}, which truncates to whole milliseconds.
     *
     * <p>Values out of range (huge numbers, ±Inf) saturate at the largest duration, so the bounds check rejects them
     * with a 400. Go wraps them to 0 on amd64 (an int64 overflow), which would turn {@code /delay/1e18} into "no
     * delay";
     * see docs/COMPATIBILITY.md. NaN is 0 in both.
     */
    static Duration fromFloatSeconds(double seconds) {
        double millis = seconds * 1000;
        if (Double.isNaN(millis)) {
            return Duration.ZERO;
        }
        long ms = (long) millis; // truncates toward zero; saturates at ±Long.MAX_VALUE
        if (ms > MAX_MILLIS) {
            return Duration.ofNanos(Long.MAX_VALUE);
        }
        if (ms < -MAX_MILLIS) {
            return Duration.ofNanos(-Long.MAX_VALUE);
        }
        return Duration.ofMillis(ms);
    }

    /**
     * A port of Go's {@code time.ParseDuration}: signed sequences of decimal numbers with units, such as
     * {@code -1.5h} or {@code 2h45m}.
     */
    public static Duration parseGo(String orig) {
        String s = orig;
        boolean neg = false;
        if (!s.isEmpty() && (s.charAt(0) == '-' || s.charAt(0) == '+')) {
            neg = s.charAt(0) == '-';
            s = s.substring(1);
        }
        if (s.equals("0")) {
            return Duration.ZERO;
        }
        if (s.isEmpty()) {
            throw invalid(orig);
        }
        // Accumulate nanoseconds; any overflow past Long.MAX_VALUE is "invalid duration", as in Go.
        long total = 0;
        while (!s.isEmpty()) {
            char c0 = s.charAt(0);
            if (!(c0 == '.' || (c0 >= '0' && c0 <= '9'))) {
                throw invalid(orig);
            }
            int i = 0;
            long v = 0;
            while (i < s.length() && Character.isDigit(s.charAt(i)) && s.charAt(i) < 128) {
                if (v > Long.MAX_VALUE / 10) {
                    throw invalid(orig);
                }
                v = v * 10 + (s.charAt(i) - '0');
                if (v < 0) {
                    throw invalid(orig);
                }
                i++;
            }
            boolean pre = i > 0;
            s = s.substring(i);

            long frac = 0;
            double scale = 1;
            boolean post = false;
            if (!s.isEmpty() && s.charAt(0) == '.') {
                s = s.substring(1);
                int j = 0;
                boolean overflow = false;
                while (j < s.length() && s.charAt(j) >= '0' && s.charAt(j) <= '9') {
                    if (!overflow) {
                        if (frac > Long.MAX_VALUE / 10) {
                            overflow = true;
                        } else {
                            frac = frac * 10 + (s.charAt(j) - '0');
                            scale *= 10;
                        }
                    }
                    j++;
                }
                post = j > 0;
                s = s.substring(j);
            }
            if (!pre && !post) {
                throw invalid(orig);
            }

            int u = 0;
            while (u < s.length() && !(s.charAt(u) == '.' || (s.charAt(u) >= '0' && s.charAt(u) <= '9'))) {
                u++;
            }
            if (u == 0) {
                throw new DurationError("time: missing unit in duration " + GoStrconv.quote(orig));
            }
            String unitName = s.substring(0, u);
            s = s.substring(u);
            Long unit = UNITS.get(unitName);
            if (unit == null) {
                throw new DurationError("time: unknown unit " + GoStrconv.quote(unitName)
                        + " in duration " + GoStrconv.quote(orig));
            }
            if (v > Long.MAX_VALUE / unit) {
                throw invalid(orig);
            }
            v *= unit;
            if (frac > 0) {
                v += (long) (frac * (unit / scale));
                if (v < 0) {
                    throw invalid(orig);
                }
            }
            total += v;
            if (total < 0) {
                throw invalid(orig);
            }
        }
        return Duration.ofNanos(neg ? -total : total);
    }

    /**
     * Go's "invalid duration" error, with the input quoted the Go way.
     */
    private static DurationError invalid(String orig) {
        return new DurationError("time: invalid duration " + GoStrconv.quote(orig));
    }

    /**
     * A port of Go's {@code Duration.String()}: {@code 0s}, {@code 1.5s}, {@code 1m30s}, {@code 500ms}.
     */
    public static String format(Duration duration) {
        long d = duration.toNanos();
        if (d == 0) {
            return "0s";
        }
        boolean neg = d < 0;
        long u = Math.abs(d);
        StringBuilder sb = new StringBuilder();
        if (u < S) {
            if (u < US) {
                sb.append(u).append("ns");
            } else if (u < MS) {
                sb.append(withFraction(u, 3)).append("µs");
            } else {
                sb.append(withFraction(u, 6)).append("ms");
            }
        } else {
            long totalSeconds = u / S;
            String seconds = (totalSeconds % 60) + fraction(u % S, 9) + "s";
            long minutes = totalSeconds / 60;
            if (minutes > 0) {
                long hours = minutes / 60;
                String m = (minutes % 60) + "m";
                sb.append(hours > 0 ? hours + "h" : "").append(m);
            }
            sb.append(seconds);
        }
        return (neg ? "-" : "") + sb;
    }

    /**
     * {@code v} in units of 10^prec, as whole units plus a fraction: 1500 with prec 3 becomes "1.5".
     */
    private static String withFraction(long v, int prec) {
        long pow = (long) Math.pow(10, prec);
        return (v / pow) + fraction(v % pow, prec);
    }

    /**
     * The fraction part: ".5" for 5 with prec 1, "" when zero, trailing zeros trimmed.
     */
    private static String fraction(long remainder, int prec) {
        if (remainder == 0) {
            return "";
        }
        String digits = String.format("%0" + prec + "d", remainder).replaceAll("0+$", "");
        return "." + digits;
    }
}
