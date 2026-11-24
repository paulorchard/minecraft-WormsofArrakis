package com.paulorchard.islandcraft.wormsofarrakis.fx;

import com.paulorchard.islandcraft.wormsofarrakis.fx.CrashFx.PType;

import java.util.List;

/**
 * Prompt 10 part 1: the eruption of the breach, the owner's second previs (a low dust bank, tall dust columns, spiky sand plumes shot up round the
 * rim, sand pouring off the body, a high haze, sand blocks flying at a quarter size), as data. It is a second group of particle rows that runs in
 * addition to {@link CrashFx#EMITTERS} (nothing of those is removed). Every row is anchored at the breach. Pure code, no Minecraft classes.
 */
public final class EruptionFx {
    private EruptionFx() {}

    /** A sustain row runs from its start until this many seconds after the breach. */
    /** The smaller eruption at the exit hole of a crest (prompt 10 part 2): this fraction of the counts and rates, the sustain rows at most this many seconds. */
    public static final double EXIT_FACTOR = 0.4, EXIT_SUSTAIN = 3.0;
    public static final double ERUPTION_SUSTAIN = 5.0;
    /** The jets (the flickering spiky tongues): how many, how often they are re-rolled, the spread of a particle round its jet's angle, the strength range. */
    public static final int ERUPTION_JETS = 14;
    public static final double JET_REROLL = 0.6, JET_ANGLE_SIGMA = 0.12, JET_STRENGTH_MIN = 0.5, JET_STRENGTH_MAX = 1.4;
    /** Shade of every particle: the tint times uniform(SHADE_MIN, SHADE_MAX), clamped to 1. */
    public static final double SHADE_MIN = 0.85, SHADE_MAX = 1.15;
    /** The fade of a puff: in over the first FADE_IN of its life, out over the last FADE_OUT. */
    public static final double FADE_IN = 0.15, FADE_OUT = 0.40;
    /** A chunk is a block particle of this full width (1/4 block: the sand blocks flying out at 1/4 size). */
    public static final double CHUNK_SIZE = 0.25;

    public enum Mode { SUSTAIN, BURST }

    /** ring/disc/jets are on the base level round the crossing; curtain is on the ring at heights 0.5 .. 0.5 + yJit (grains pouring off the body). */
    public enum Shape { RING, DISC, JETS, CURTAIN }

    /**
     * One row. {@code amount}: particles per second (sustain) or the whole count (burst, spread evenly over {@code over} seconds from {@code start}).
     * {@code ring}, {@code radJit} in units of r0; {@code yJit} in blocks; {@code up}, {@code out}, {@code spread} in blocks per tick; {@code grav} is
     * the previs's gravity factor (1 = the vanilla block grain; vy -= 0.04 x grav per tick); {@code drag} the previs's per-tick velocity factor;
     * {@code size} the full width in blocks; {@code endScale} the size at death as a fraction of the start size; life in ticks; {@code alpha} opacity.
     */
    public record Row(String name, PType type, Mode mode, Shape shape, double start, double amount, double over, double ring, double radJit, double yJit,
                      double up, double out, double spread, double grav, double drag, double size, double endScale, int lifeMin, int lifeMax, int tint, double alpha) {
        public boolean isBurst() { return mode == Mode.BURST; }
        /** True for a particle that fades (a puff: dust_plume or cloud). */
        public boolean fades() { return type == PType.DUST_PLUME || type == PType.CLOUD; }
    }

    private static Row sustain(String n, PType t, Shape s, double start, double rate, double ring, double radJit, double yJit, double up, double out, double spread, double grav,
                               double drag, double size, double end, int l0, int l1, int tint, double alpha) {
        return new Row(n, t, Mode.SUSTAIN, s, start, rate, 0, ring, radJit, yJit, up, out, spread, grav, drag, size, end, l0, l1, tint, alpha);
    }

    private static Row burst(String n, PType t, Shape s, double start, double count, double over, double ring, double radJit, double yJit, double up, double out, double spread,
                             double grav, double drag, double size, double end, int l0, int l1, int tint, double alpha) {
        return new Row(n, t, Mode.BURST, s, start, count, over, ring, radJit, yJit, up, out, spread, grav, drag, size, end, l0, l1, tint, alpha);
    }

    private static final double THIRD = 0.333;

    public static final List<Row> ROWS = List.of(
            sustain("Eruption: ground dust bank", PType.CLOUD, Shape.RING, 0, 520, 1.05, 0.4, 2.5, 0.08, 0.09, 0.05, -0.01, 0.95, 3.6, THIRD, 30, 80, 0xCDB07A, 0.6),
            sustain("Eruption: rising dust columns", PType.DUST_PLUME, Shape.JETS, 0.05, 260, 1.0, 0.15, 0.5, 0.95, 0.16, 0.07, -0.01, 0.975, 5.0, THIRD, 40, 100, 0xD8BB86, 0.55),
            sustain("Eruption: spiky sand plumes", PType.BLOCK, Shape.JETS, 0, 2400, 1.0, 0.1, 0.3, 1.0, 0.2, 0.14, 1, 0.98, 0.15, 1, 20, 70, 0xB8A888, 1),
            burst("Eruption: sand fountain", PType.BLOCK, Shape.RING, 0, 3500, 0.45, 0.9, 0.3, 0.3, 1.3, 0.6, 0.22, 1, 0.98, 0.15, 1, 25, 80, 0xB8A888, 1),
            burst("Eruption: central dust column", PType.DUST_PLUME, Shape.DISC, 0, 260, 0.5, 0.6, 0, 0.5, 1.1, 0.1, 0.12, -0.01, 0.975, 5.5, THIRD, 40, 90, 0xD8BB86, 0.6),
            burst("Eruption: dust ring", PType.DUST_PLUME, Shape.RING, 0, 420, 0.35, 1.0, 0.15, 0.8, 0.12, 0.65, 0.08, -0.01, 0.95, 5.0, THIRD, 25, 60, 0xD8BB86, 0.6),
            burst("Eruption: shock ring", PType.DUST_PLUME, Shape.RING, 0, 220, 0.12, 1.2, 0.05, 0.2, 0.03, 0.9, 0.05, 0, 0.94, 3.2, THIRD, 15, 35, 0xE0C690, 0.55),
            burst("Eruption: flying sand blocks", PType.CHUNK, Shape.RING, 0, 260, 0.4, 0.9, 0.3, 0.3, 1.1, 0.55, 0.3, 1, 0.99, 0.25, 1, 40, 120, 0xB8A888, 1),
            sustain("Eruption: sand blocks in the plumes", PType.CHUNK, Shape.JETS, 0.1, 60, 1.0, 0.1, 0.3, 0.9, 0.25, 0.2, 1, 0.99, 0.25, 1, 40, 100, 0xB8A888, 1),
            sustain("Eruption: sand curtain off the body", PType.BLOCK, Shape.CURTAIN, 0.3, 900, 0.98, 0.03, 14, 0, 0, 0.01, 1, 0.98, 0.15, 1, 30, 70, 0xB8A888, 1),
            sustain("Eruption: high haze", PType.DUST_PLUME, Shape.DISC, 0.2, 80, 1.8, 0, 1, 0.45, 0.08, 0.06, -0.01, 0.98, 7.0, THIRD, 50, 110, 0xE0C690, 0.32),
            sustain("Eruption: fine grain haze", PType.BLOCK, Shape.DISC, 0, 700, 2.4, 0, 1, 0.28, 0.2, 0.16, 1, 0.98, 0.10, 1, 20, 90, 0xC8B898, 1));

    /** Number of particles one row makes in one crash at count scale 1 (rate x duration, or the burst count). */
    public static double countAtScaleOne(Row r) { return r.isBurst() ? r.amount() : r.amount() * Math.max(0, ERUPTION_SUSTAIN - r.start()); }
}
