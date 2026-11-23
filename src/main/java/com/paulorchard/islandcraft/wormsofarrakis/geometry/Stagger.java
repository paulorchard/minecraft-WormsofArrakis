package com.paulorchard.islandcraft.wormsofarrakis.geometry;

/**
 * Prompt 04: seeded, deterministic variety for the shingle skin. Everything is a pure function of (seed, ring index k,
 * face index i, salt); the ring index is the segment's fixed place along the body, never its position in the world, so
 * the pattern stays with the segment while the worm moves.
 */
public final class Stagger {
    public static final int SALT_ROTATION = 1, SALT_ORIENT = 2, SALT_JITTER = 3, SALT_LINER_ORIENT = 4, SALT_LINER_JITTER = 5, SALT_FLAP = 6;

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

    /** One of the 8 orientations of a square tile (bit 0 flip s, bit 1 flip t, bit 2 swap), 0 when tiles are off. */
    public static int orientation(long seed, int k, int i, int salt, boolean tiles) {
        return tiles ? (int) (u(seed, k, i, salt) * 8) & 7 : 0;
    }

    /** Applies an orientation to normalised tile coordinates (s, t) in [0, 1]; the result stays in [0, 1]. */
    public static float[] orient(int o, float s, float t) {
        if ((o & 4) != 0) { float x = s; s = t; t = x; }
        if ((o & 1) != 0) s = 1f - s;
        if ((o & 2) != 0) t = 1f - t;
        return new float[]{s, t};
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
