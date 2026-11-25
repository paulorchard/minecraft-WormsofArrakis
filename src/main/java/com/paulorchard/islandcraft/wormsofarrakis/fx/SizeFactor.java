package com.paulorchard.islandcraft.wormsofarrakis.fx;

/**
 * Prompt 12 part 1: the share of the previs numbers that a worm of width W gets. The numbers tuned in the previs (every cue's volume, every particle row's count and size)
 * belong to a W48 worm; smaller worms get a share: piecewise linear through the owner's points (W8 0.25, W16 0.5, W32 0.75, W48 1.0), W/32 from 0 up to 16 (so 8 gives 0.25),
 * flat 1.0 above 48. The table is changed here and nowhere else.
 */
public final class SizeFactor {
    private SizeFactor() {}

    /** The anchor widths and factors (the owner's four points); the segment below W16 is the line through the origin and the W16 point. */
    public static final double[] W = {0, 16, 32, 48};
    public static final double[] F = {0, 0.5, 0.75, 1.0};
    public static final double SMALL = 8, MEDIUM = 16, LARGE = 32, HUGE = 48;
    /** A row with a count of 1 or more plays at least one particle when the factor is at least this; below it the count is rounded stochastically (its mean is exact). */
    public static final double MIN_FACTOR_FOR_ONE = 0.1;

    public static double of(double width) {
        if (!(width > 0)) return 0;
        if (width >= W[W.length - 1]) return F[F.length - 1];
        for (int i = 1; i < W.length; i++) {
            if (width <= W[i]) return F[i - 1] + (F[i] - F[i - 1]) * (width - W[i - 1]) / (W[i] - W[i - 1]);
        }
        return 1;
    }

    /** The line of the log: {@code size factor: W8=0.25 W16=0.5 W32=0.75 W48=1.0}. */
    public static String logLine() {
        return "size factor: W8=" + fmt(of(SMALL)) + " W16=" + fmt(of(MEDIUM)) + " W32=" + fmt(of(LARGE)) + " W48=" + fmt(of(HUGE));
    }

    private static String fmt(double v) { return v == Math.rint(v) ? String.format(java.util.Locale.ROOT, "%.1f", v) : String.format(java.util.Locale.ROOT, "%s", Double.toString(v)); }
}
