package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import java.util.Random;

/**
 * Prompt 16 part 1: the maths of the violent rumble round the victim, from the start of the freeze until the death. Engine-free. One long {@code Rumble.play}; the strength is a curve of the
 * time since the freeze began: {@code AttackRumbleStart} (0.7) at 0, rising linearly to 1.0 at the end of the freeze (AttackSeconds), then 1.0 for as long as it runs.
 */
public final class AttackRumble {
    private AttackRumble() {}

    /** The radius never goes below this (blocks). */
    public static final double MIN_RADIUS = 5;
    /** The hard timeout after the longest expected run (seconds): the rumble can never outlive the strike by more than this. */
    public static final double HARD_TIMEOUT_EXTRA = 5.0;
    /** The viewers are chosen again this often (seconds). */
    public static final double VIEWER_PERIOD = 1.0;
    /** The sound and the particles repeat with a gap uniform in [GAP_MIN, GAP_MAX] seconds. */
    public static final double GAP_MIN = 0.5, GAP_MAX = 1.0;

    /** S(t): start at t = 0, linear to 1.0 at t = freezeSeconds, then 1.0. */
    public static double strength(double t, double freezeSeconds, double start) {
        double s0 = Math.max(0, Math.min(1, start));
        if (freezeSeconds <= 1e-9) return 1.0;
        double f = Math.max(0, Math.min(1, t / freezeSeconds));
        return s0 + (1.0 - s0) * f;
    }

    /** AttackRumbleRadius x size factor, at least 5. */
    public static double radius(double factor, HuntConfig c) { return Math.max(MIN_RADIUS, c.attackRumbleRadius * factor); }

    /** AttackRumbleMaxSixteenths x (0.5 + 0.5 x size factor), rounded, 1 to 15. */
    public static int maxSixteenths(double factor, HuntConfig c) {
        return Math.max(1, Math.min(15, (int) Math.round(c.attackRumbleMaxSixteenths * (0.5 + 0.5 * factor))));
    }

    /**
     * The length of the one long rumble (seconds): the freeze, the longest run of any strike move of the size, and the hard timeout extra. It is stopped at the death long before this; this is only the limit.
     * (The rumble of prompt 15 ends by itself at this time and its own restore timeout comes after that.)
     */
    public static double seconds(double freezeSeconds, double longestPlaySeconds) { return freezeSeconds + longestPlaySeconds + HARD_TIMEOUT_EXTRA; }

    /** The plan of one attack rumble. */
    public record Plan(double radius, int maxSixteenths, double start, double freezeSeconds, double seconds, double factor) {
        /** The strength at the time (seconds since the freeze began). */
        public double strengthAt(double t) { return strength(t, freezeSeconds, start); }
        /** The strength function the rumble of prompt 15 takes: its argument is the fraction 0..1 of {@link #seconds}. */
        public java.util.function.DoubleUnaryOperator asFraction() { return f -> strengthAt(f * seconds); }
    }

    public static Plan plan(double factor, HuntConfig c, double longestPlaySeconds) {
        return new Plan(radius(factor, c), maxSixteenths(factor, c), c.attackRumbleStart, c.attackSeconds, seconds(c.attackSeconds, longestPlaySeconds), factor);
    }

    /** The clock of the repeating sound and particles: a seeded gap uniform in [0.5, 1.0] s after each. */
    public static final class FxClock {
        private final Random rng;
        private double next;

        public FxClock(long seed) {
            rng = new Random(seed);
            next = 0;
        }

        public double next() { return next; }
        public boolean due(double t) { return t >= next; }
        public void fired(double t) { next = t + GAP_MIN + rng.nextDouble() * (GAP_MAX - GAP_MIN); }
    }
}
