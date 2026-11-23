package com.paulorchard.islandcraft.wormsofarrakis.geometry;

/** Pure geometry for the ring-shaped worm body (no Minecraft classes, unit tested). */
public final class WormMath {
    /** Face width in blocks: one 16 px texture tile maps 1:1 onto a face. */
    public static final double FACE_WIDTH = 1.0;
    public static final int MIN_FACES = 10;

    private WormMath() {}

    /** n = floor(pi * W / s), never below 10. The prompt says round(), but round gives 151 for W 48 and 9 for W 3, while the stated values are 150 and 10; floor plus the decagon minimum reproduces all three (10, 50, 150). */
    public static int faceCount(double width) {
        return Math.max(MIN_FACES, (int) Math.floor(Math.PI * width / FACE_WIDTH + 1e-9));
    }

    /** Circumradius of the regular n-gon with side s: R = s / (2 sin(pi/n)). Faces meet exactly on this circle. */
    public static double circumradius(int n, double s) {
        return s / (2.0 * Math.sin(Math.PI / n));
    }

    /** Distance from the axis to the middle of a flat face. */
    public static double apothem(int n, double circumradius) {
        return circumradius * Math.cos(Math.PI / n);
    }

    public static double faceAngleDegrees(int n) {
        return 360.0 / n;
    }
}
