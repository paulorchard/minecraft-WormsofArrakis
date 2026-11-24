package com.paulorchard.islandcraft.wormsofarrakis.light;

/**
 * Prompt 10 part 3, hypothesis H4: the renderer shades every quad by its normal (the vertex normal is the one stored with the quad), so a zero or NaN normal
 * gives a black face. This counts the quads of a mesh (the layout of ShingleMath.Result: 32 floats per quad, 4 vertices of x y z u v nx ny nz) that have a
 * zero or tiny normal, a tiny area (the quad collapsed), or a NaN position or normal.
 */
public final class QuadCheck {
    private QuadCheck() {}

    /** Smaller than this the normal is zero (length) or the quad has no area (square blocks). */
    public static final double TINY_NORMAL = 1e-6, TINY_AREA = 1e-6;

    public record Count(int quads, int zeroNormal, int tinyArea, int nan) {
        public int bad() { return zeroNormal + tinyArea + nan; }
    }

    public static Count check(float[] v, int quads) {
        int zero = 0, tiny = 0, nan = 0;
        for (int q = 0; q < quads; q++) {
            int b = q * 32;
            boolean isNan = false;
            for (int i = 0; i < 32 && !isNan; i++) if (i % 8 != 3 && i % 8 != 4 && Float.isNaN(v[b + i])) isNan = true;   // u and v are not positions or normals
            for (int i = 0; i < 32 && !isNan; i++) if (Float.isInfinite(v[b + i])) isNan = true;
            if (isNan) {
                nan++;
                continue;
            }
            double nx = v[b + 5], ny = v[b + 6], nz = v[b + 7];
            if (Math.sqrt(nx * nx + ny * ny + nz * nz) < TINY_NORMAL) {
                zero++;
                continue;
            }
            // area of the quad = half the length of the cross product of its diagonals
            double d1x = v[b + 16] - v[b], d1y = v[b + 17] - v[b + 1], d1z = v[b + 18] - v[b + 2];
            double d2x = v[b + 24] - v[b + 8], d2y = v[b + 25] - v[b + 9], d2z = v[b + 26] - v[b + 10];
            double cx = d1y * d2z - d1z * d2y, cy = d1z * d2x - d1x * d2z, cz = d1x * d2y - d1y * d2x;
            if (0.5 * Math.sqrt(cx * cx + cy * cy + cz * cz) < TINY_AREA) tiny++;
        }
        return new Count(quads, zero, tiny, nan);
    }

    /** The line of the log for one mesh build, or null when there is nothing to say (silent when the count is 0). */
    public static String line(String part, String what, Count c) {
        if (c.bad() == 0) return null;
        return String.format(java.util.Locale.ROOT, "[WormsOfArrakis] mesh check: %s (%s): %d of %d quads are degenerate: zero normal %d, tiny area (< %.0e) %d, NaN or infinite %d",
                part, what, c.bad(), c.quads(), c.zeroNormal(), TINY_AREA, c.tinyArea(), c.nan());
    }
}
