package com.paulorchard.islandcraft.wormsofarrakis.rumble;

/**
 * Prompt 15 part 2: the pulse schedule of the sand rumble, a port of the Hytale one. No Minecraft types.
 * <p>
 * Every drawn column has its own pulse train. In every period of {@code pulseSeconds} a column starts one pulse at a random moment of its own inside the period (seeded by seed, column and period).
 * A pulse is {@code dropSteps} descent steps of {@code stepSeconds} each (the first about half of the depth, the last all of it), then a damped rebound: swings alternating above and below the surface
 * (+0.6 d, -0.4 d, +0.25 d, -0.12 d, then 0), each {@code swingSeconds} +-25 %. The longest pulse is {@link #maxPulseSeconds()} (0.57 s at the defaults) and a pulse starts no later than the
 * period length minus that, so a pulse never overlaps the next one of the same column. The offset of a column at a time is a pure function of (seed, column, time).
 */
public final class RumbleSchedule {
    /** The numbers of the schedule (all config values, these are the defaults). */
    public record Numbers(int dropSteps, double stepSeconds, double swingSeconds, double minWeightedStrengthForOne) {
        public static final Numbers DEFAULTS = new Numbers(2, 0.06, 0.09, 0.1);
    }

    private static final double[] SWINGS = {0.6, -0.4, 0.25, -0.12};
    private static final double SWING_SPREAD = 0.25;
    private static final long GAMMA = 0x9E3779B97F4A7C15L;

    private final Rumble.Params p;
    private final Numbers n;
    private final int max;

    public RumbleSchedule(Rumble.Params params, Numbers numbers) {
        this.p = params;
        this.n = numbers;
        this.max = RumbleCell.limit(params.maxSixteenths());
    }

    public Rumble.Params params() { return p; }
    public int max() { return max; }

    /** The longest pulse: the descent plus the four swings at +25 %. */
    public double maxPulseSeconds() { return n.dropSteps() * n.stepSeconds() + SWINGS.length * n.swingSeconds() * (1 + SWING_SPREAD); }

    /** The shortest pulse period that keeps pulses from overlapping. */
    public static double minPeriod(Numbers n) { return n.dropSteps() * n.stepSeconds() + SWINGS.length * n.swingSeconds() * (1 + SWING_SPREAD); }

    /** 1 at the centre falling linearly to edgeWeight at the radius (and beyond: edgeWeight). */
    public double weight(double dist) {
        double f = p.radius() <= 0 ? 1 : Math.max(0, Math.min(1, dist / p.radius()));
        return 1 - (1 - p.edgeWeight()) * f;
    }

    public double strengthAt(double seconds) {
        double t = p.seconds() <= 0 ? 0 : Math.max(0, Math.min(1, seconds / p.seconds()));
        return Math.max(0, Math.min(1, p.strength().applyAsDouble(t)));
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** A number in [0, 1) from a hash and a slot. */
    private static double u(long h, int slot) { return (mix(h + slot * GAMMA) >>> 11) * 0x1.0p-53; }

    private long hash(long column, long period) { return mix(mix(p.seed() ^ GAMMA) + column * 31 + mix(period * GAMMA + 0x1234567L)); }

    /** The phase of the periods of a column (0 to the period): every column has its own, so the periods of the thousand columns do not line up and the patch has no global beat. */
    public double phase(long column) { return u(mix(mix(p.seed() ^ GAMMA) + column * 31 + 0x51ED27L), 6) * p.pulseSeconds(); }

    /** The start (seconds) of the pulse a column has in the period (periods are counted from its own phase; period -1 is the one before the first). Negative: the pulse would start before the event does and is not played. */
    public double pulseStart(long column, long period) {
        double P = p.pulseSeconds();
        long h = hash(column, period);
        return phase(column) + period * P + u(h, 0) * Math.max(0, P - maxPulseSeconds());
    }

    /** The depth d (sixteenths, 0 when the column does not pulse in this period) of the pulse of a column at the distance dist. */
    public int depth(long column, long period, double dist) {
        double start = pulseStart(column, period);
        if (start < 0 || start >= p.seconds()) return 0;
        long h = hash(column, period);
        double s = strengthAt(start);
        if (u(h, 5) >= Math.max(0, Math.min(1, p.share().applyAsDouble(s)))) return 0;
        double sw = s * weight(dist);
        int d = (int) Math.round(sw * max * (0.7 + 0.6 * u(h, 1)));
        if (sw > n.minWeightedStrengthForOne()) d = Math.max(1, d);
        return Math.min(max, d);
    }

    /** The offset in sixteenths of a column at {@code seconds} after the start (negative: lowered). A pure function of (seed, column, time, distance). */
    public int offset(long column, double seconds, double dist) {
        if (seconds < 0 || seconds >= p.seconds()) return 0;
        long period = (long) Math.floor((seconds - phase(column)) / p.pulseSeconds());
        double start = pulseStart(column, period);
        double lt = seconds - start;
        if (lt < 0) return 0;
        int d = depth(column, period, dist);
        if (d == 0) return 0;
        long h = hash(column, period);
        int steps = Math.max(1, n.dropSteps());
        int idx = (int) Math.floor(lt / n.stepSeconds());
        int v;
        if (idx < steps) v = -(int) Math.round(d * (idx + 1) / (double) steps);
        else {
            lt -= steps * n.stepSeconds();
            v = 0;
            for (int k = 0; k < SWINGS.length; k++) {
                double len = n.swingSeconds() * (1 - SWING_SPREAD + 2 * SWING_SPREAD * u(h, 2 + k));
                if (lt < len) { v = (int) Math.round(SWINGS[k] * d); break; }
                lt -= len;
            }
        }
        return RumbleCell.clamp(v, max);
    }

    /** The length (seconds) of the pulse of a column in a period (0 when it has none). */
    public double pulseLength(long column, long period, double dist) {
        if (depth(column, period, dist) == 0) return 0;
        long h = hash(column, period);
        double len = n.dropSteps() * n.stepSeconds();
        for (int k = 0; k < SWINGS.length; k++) len += n.swingSeconds() * (1 - SWING_SPREAD + 2 * SWING_SPREAD * u(h, 2 + k));
        return len;
    }
}
