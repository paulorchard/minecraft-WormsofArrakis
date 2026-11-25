package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Prompt 13 part 5: the maths of the sand tremors, engine-free: strength, plan, build-up, clock, ripple, budget and the tracker of fake blocks. */
public final class Tremor {
    private Tremor() {}

    /** S = 0.3 + 0.7 p, p = the fraction of the stalk elapsed (0 to 1). */
    public static double strength(double p) { return 0.3 + 0.7 * Math.max(0, Math.min(1, p)); }

    /** The size of one tremor: ripple radius (blocks), layers, particle count, sound volume and range (16 x volume). All scale with the strength S and the size factor f. */
    public record Plan(double strength, double factor, double radius, int layers, int particles, double volume, double range) {}

    public static final double MIN_RADIUS = 2.5;

    public static Plan plan(double s, double factor, HuntConfig c) {
        double radius = Math.max(MIN_RADIUS, c.tremorRippleRadius * s * factor);
        int layers = Math.max(1, (int) Math.round(c.tremorRippleLayers * s));
        int particles = Math.max(1, (int) Math.round(c.tremorParticles * s * factor));
        double volume = c.tremorSoundVolume * s * factor;
        return new Plan(s, factor, radius, layers, particles, volume, 16 * volume);
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

    /**
     * The ripple: a wave front that runs out from the player over {@code ticks} ticks and a band of raised sand round it. {@code layers(d, tick)} is the number of fake
     * layers on a column at horizontal distance d from the player at that tick; it is 0 inside 1.5 blocks of the player (the feet stay on real blocks) and 0 on the last tick.
     */
    public static final class Ripple {
        public static final double INNER = 1.5, BAND = 1.5;

        public static int layers(double d, int tick, int ticks, double radius, int maxLayers) {
            if (tick >= ticks || d < INNER || d > radius + BAND) return 0;
            double front = INNER + (radius - INNER + BAND) * (tick / (double) ticks);
            double x = (d - front) / BAND;
            double a = Math.exp(-x * x);
            // the whole wave also settles towards its end
            double settle = 1 - Math.pow(tick / (double) ticks, 3);
            return (int) Math.round(maxLayers * a * settle);
        }
    }

    /** The fake blocks that have been sent: every one is restored (also on an early end). A key is a packed block position; the viewer is who got it. */
    public static final class FakeBlocks {
        private final Set<Long> shown = new HashSet<>();
        private final Set<UUID> viewers = new HashSet<>();

        public void show(long pos, UUID viewer) { shown.add(pos); viewers.add(viewer); }
        public void restore(long pos) { shown.remove(pos); }
        public boolean any() { return !shown.isEmpty(); }
        public Set<Long> positions() { return new HashSet<>(shown); }
        public Set<UUID> viewers() { return new HashSet<>(viewers); }
        /** Restores everything through the callback (the real block state is sent again) and forgets it. */
        public int restoreAll(java.util.function.LongConsumer sendReal) {
            int n = shown.size();
            for (long p : new HashSet<>(shown)) sendReal.accept(p);
            shown.clear();
            viewers.clear();
            return n;
        }
    }
}
