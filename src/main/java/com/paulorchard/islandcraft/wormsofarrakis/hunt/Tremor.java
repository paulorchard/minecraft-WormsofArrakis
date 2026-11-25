package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import java.util.Random;
import java.util.UUID;

/** Prompt 13 part 5: the maths of the sand tremors, engine-free: strength, plan, build-up, clock and budget (the sand itself is moved by the rumble of prompt 15). */
public final class Tremor {
    private Tremor() {}

    /** S = 0.3 + 0.7 p, p = the fraction of the stalk elapsed (0 to 1). */
    public static double strength(double p) { return 0.3 + 0.7 * Math.max(0, Math.min(1, p)); }

    /** The size of one tremor: rumble radius (blocks), the most a column moves (sixteenths), particle count, sound volume and range (16 x volume). */
    public record Plan(double strength, double factor, double radius, int maxSixteenths, int particles, double volume, double range) {}

    public static final double MIN_RADIUS = 3;
    /** One tremor is one rumble of one pulse period. */
    public static final double RUMBLE_SECONDS = 1.0;

    /** The share of the columns that pulse at strength S: S squared (a few at S 0.3, all at 1). */
    public static double share(double s) { double x = Math.max(0, Math.min(1, s)); return x * x; }

    /**
     * radius = TremorRadius x size factor (at least 3); the most a column moves = RumbleMaxSixteenths x (0.5 + 0.5 x factor); the strength S scales the depth of the pulses and the share of the
     * columns that pulse (S squared); particles and sound scale with S and the factor as before.
     */
    public static Plan plan(double s, double factor, HuntConfig c) {
        double radius = Math.max(MIN_RADIUS, c.tremorRadius * factor);
        int max = Math.max(1, Math.min(15, (int) Math.round(c.rumbleMaxSixteenths * (0.5 + 0.5 * factor))));
        int particles = Math.max(1, (int) Math.round(c.tremorParticles * s * factor));
        double volume = c.tremorSoundVolume * s * factor;
        return new Plan(s, factor, radius, max, particles, volume, 16 * volume);
    }

    /** The continuous build-up in the last BuildUpFraction of the stalk: 0 before it, rising to BuildUpMaxParticlesPerSecond x factor at the end. */
    public static double buildUpRate(double p, double factor, HuntConfig c) {
        double start = 1 - c.buildUpFraction;
        if (p <= start) return 0;
        return c.buildUpMaxParticlesPerSecond * factor * Math.min(1, (p - start) / c.buildUpFraction);
    }

    /** The tremor clock of one listed player in one event: a gap uniform in [min, max] after every tremor, the first tremor at a random time within the first gap. */
    public static final class Clock {
        private final Random rng;
        private final double min, max;
        private double next;

        public Clock(long eventId, UUID player, HuntConfig c) {
            this.rng = new Random(eventId * 0x9E3779B97F4A7C15L ^ player.getMostSignificantBits() * 31 ^ player.getLeastSignificantBits());
            this.min = c.tremorMinSeconds;
            this.max = c.tremorMaxSeconds;
            this.next = rng.nextDouble() * gap();
        }

        private double gap() { return min + rng.nextDouble() * (max - min); }

        public double next() { return next; }
        public boolean due(double t) { return t >= next; }
        /** A tremor happened at {@code t}: the next one comes after a new gap. */
        public void fired(double t) { next = t + gap(); }
        /** The player was off sand when it was due: no tremor, a new gap. */
        public void skipped(double t) { next = t + gap(); }
    }

    /** The share of the requests that can be made when the server is over a budget: 1 when under it. */
    public static double ratio(int requested, int cap) { return requested <= cap || requested <= 0 ? 1.0 : (double) cap / requested; }

    /** What one player gets of {@code n} requests under a ratio: floor(n x ratio) but at least 1 when n is at least 1 (nobody drops to zero). */
    public static int thin(int n, double ratio) { return n <= 0 ? 0 : Math.max(1, (int) Math.floor(n * ratio)); }

}
