package com.paulorchard.islandcraft.wormsofarrakis.geometry;

/**
 * Prompt 04: seeded, deterministic variety for the shingle skin. Everything is a pure function of (seed, ring index k,
 * face index i, salt); the ring index is the segment's fixed place along the body, never its position in the world, so
 * the pattern stays with the segment while the worm moves.
 */
public final class Stagger {
    public static final int SALT_ROTATION = 1, SALT_OFFSET = 2, SALT_JITTER = 3, SALT_LINER_JITTER = 5, SALT_FLAP = 6;

    private Stagger() {}

    private static long mix(long x) {
        x += 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }

    /** Uniform value in [0, 1). */
    public static double u(long seed, int k, int i, int salt) {
        long h = mix(seed ^ mix(((long) k << 24) ^ ((long) i << 4) ^ salt));
        return (h >>> 11) * 0x1.0p-53;
    }

    /**
     * Seeded rotation of each ring as a fraction of a face, u_k in [0, 1). A pure function of (seed, k), never of the pose. Each ring
     * steps from the one before by between 0.15 and 0.85 of a face (circular), so neighbouring rings always differ by at least
     * 0.15 of a face, which independent random offsets could not promise (they would differ by under 0.1 in one pair of five).
     */
    public static double[] offsets(long seed, int count) {
        double[] u = new double[count];
        double prev = 0;
        for (int k = 0; k < count; k++) {
            double r = u(seed, k, 0, SALT_ROTATION);
            prev = k == 0 ? r : (prev + 0.15 + 0.7 * r) % 1.0;
            u[k] = prev;
        }
        return u;
    }

    /** Rotation of ring k about its axis, radians: u_k x stagger x one face. */
    public static double ringDelta(long seed, int k, int n, double stagger) {
        return stagger == 0 ? 0 : offsets(seed, k + 1)[k] * stagger * (2 * Math.PI / n);
    }

    /**
     * Texture offset in pixels (0..15) along the circumferential axis of the tile, one per ring. Even rings (the head ring is ring 0)
     * are mapped normally (0); every odd ring gets a seeded offset in 1..15 that differs from the previous odd ring's by at least
     * 2 pixels (mod 16). A pure function of (seed, ring index).
     */
    public static int[] ringOffsetsPx(long seed, int count) {
        int[] o = new int[count];
        int prev = -1;
        for (int k = 1; k < count; k += 2) {
            int v;
            if (prev < 0) v = 1 + (int) (u(seed, k, 0, SALT_OFFSET) * 15) % 15;
            else {
                int d = 2 + (int) (u(seed, k, 0, SALT_OFFSET) * 13) % 13;   // 2..14
                v = (prev + d) % 16;
                if (v == 0) { d = d < 14 ? d + 1 : d - 1; v = (prev + d) % 16; }
            }
            o[k] = v;
            prev = v;
        }
        return o;
    }

    /** Colour multiplier 1 +- jitter for a face. */
    public static double jitterFactor(long seed, int k, int i, int salt, double jitter) {
        return jitter == 0 ? 1.0 : 1.0 + jitter * (2 * u(seed, k, i, salt) - 1);
    }

    /** ARGB colour with every channel multiplied by f (clamped). */
    public static int scale(int argb, double f) {
        if (f == 1.0) return argb;
        int r = clamp(((argb >> 16) & 255) * f), g = clamp(((argb >> 8) & 255) * f), b = clamp((argb & 255) * f);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }
}
