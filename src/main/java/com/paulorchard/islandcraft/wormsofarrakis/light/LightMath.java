package com.paulorchard.islandcraft.wormsofarrakis.light;

/**
 * Prompt 10 part 3: the packed light of the worm. Vanilla fills the light of an entity once, from the block at one point of the entity (its eye
 * position, which for this worm is its origin on the sand plus 0.85); the worm is up to 50 blocks wide and tall, so the renderer now takes the
 * maximum of the block and sky light at several points along the exposed body (see {@link LightProbe}), smoothed so it does not flicker.
 * Pure code, no Minecraft classes; the packing is the game's (LightCoordsUtil.pack: block light in bits 4..7, sky light in bits 20..23).
 */
public final class LightMath {
    private LightMath() {}

    /** Number of sample points of the probe (the reference points and the rings along the exposed length). */
    public static final int LIGHT_SAMPLES = 8;
    /** A channel moves toward its target by at most this many levels per client tick. */
    public static final int MAX_STEP = 1;
    /** The sample is recomputed every this many ticks (the smoothing runs every tick). */
    public static final int SAMPLE_EVERY = 4;

    public static int pack(int block, int sky) { return block << 4 | sky << 20; }
    public static int block(int packed) { return packed >> 4 & 15; }
    public static int sky(int packed) { return packed >> 20 & 15; }
    public static final int FULL = pack(15, 15);

    /** light=probe|vanilla|full (the key of a move; probe is the default). */
    public enum Mode {
        PROBE, VANILLA, FULL;
        public String label() { return name().toLowerCase(java.util.Locale.ROOT); }
        public static Mode byLabel(String s) {
            for (Mode m : values()) if (m.label().equals(s)) return m;
            return null;
        }
    }

    /** One channel one step toward the target. */
    public static int step(int current, int target) {
        if (target > current) return Math.min(target, current + MAX_STEP);
        return Math.max(target, current - MAX_STEP);
    }

    /** The per-channel maximum of block and sky light over a set of samples (packed values). */
    public static int maxCombine(int[] packed, int count) {
        int b = 0, s = 0;
        for (int i = 0; i < count; i++) {
            b = Math.max(b, block(packed[i]));
            s = Math.max(s, sky(packed[i]));
        }
        return pack(b, s);
    }

    /** Moves the light of one worm toward its target by at most one level per tick per channel. The first target is taken at once. */
    public static final class Smoother {
        private int block, sky, tBlock, tSky;
        private boolean init;

        public void target(int packed) {
            tBlock = block(packed);
            tSky = sky(packed);
            if (!init) {
                init = true;
                block = tBlock;
                sky = tSky;
            }
        }

        /** One client tick; returns the packed light now. */
        public int tick() {
            block = step(block, tBlock);
            sky = step(sky, tSky);
            return pack(block, sky);
        }

        public int packed() { return pack(block, sky); }
        public boolean started() { return init; }
    }

    /** The light the renderer uses: vanilla = exactly the entity's own, full = 15/15, probe = the smoothed result of the probe (vanilla until the first sample). */
    public static int choose(Mode mode, int vanilla, Smoother probe) {
        return switch (mode) {
            case VANILLA -> vanilla;
            case FULL -> FULL;
            case PROBE -> probe != null && probe.started() ? probe.packed() : vanilla;
        };
    }
}
