package com.paulorchard.islandcraft.wormsofarrakis.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * The L-shaped scale (experiment 2), pure code. Units: s = face width = 1 block. Axis along -Z (head at z = 0, tail
 * towards -Z), ring points at angle 2 pi k / n: (r sin a, r cos a, z) as in {@link Frustum}.
 * <pre>
 * tailward (default): low leading edge towards the head, raised edge and lip towards the tail
 *   skin: front ring (r0, u 0) to rear ring (r0 + sin(tilt) L, u cos(tilt) L)
 *   lip:  rear ring to (rear r - lip cos(tilt), u + lip sin(tilt)), perpendicular to the skin, leaning to the tail
 * headward: the same scale mirrored along the axis (raised edge and lip towards the head).
 * </pre>
 * u is the distance along the body from the scale's start towards the tail.
 */
public final class ScaleMath {
    public static final double TILT_DEG = 5.25;
    public static final double SKIN_LENGTH = 1.0;
    public static final double LIP_LENGTH = 0.132;
    public static final double PITCH = 1.0;
    /** The lip shows a 2 pixel high strip of a 16 pixel tile. */
    public static final float LIP_V = 2f / 16f;

    private ScaleMath() {}

    public record Params(double tiltDeg, double skinLength, double lipLength, boolean headward) {
        public static Params defaults(boolean headward) { return new Params(TILT_DEG, SKIN_LENGTH, LIP_LENGTH, headward); }
        public double tilt() { return Math.toRadians(tiltDeg); }
        public double rise() { return Math.sin(tilt()) * skinLength; }
        public double skinAxial() { return Math.cos(tilt()) * skinLength; }
        /** Axial extent of the whole scale including the lean of the lip. */
        public double extent() { return skinAxial() + Math.sin(tilt()) * lipLength; }
        /** Radius at which the lip ends, relative to the start radius r0 (negative = inside it). */
        public double lipEndOffset() { return rise() - Math.cos(tilt()) * lipLength; }
    }

    /** {skin, lip} frusta of one scale whose start is at body position u0 (blocks from the head, towards the tail). */
    public static List<Frustum> scale(int n, double r0, double u0, Params p) {
        double rFront = r0, rRear = r0 + p.rise(), rLip = r0 + p.lipEndOffset();
        double uFront = 0, uRear = p.skinAxial(), uLip = p.extent();
        if (p.headward()) {
            double e = p.extent();
            uFront = e - uFront;
            uRear = e - uRear;
            uLip = e - uLip;
        }
        List<Frustum> out = new ArrayList<>(2);
        out.add(Frustum.fromRings(n, ring(n, rFront, -(u0 + uFront)), ring(n, rRear, -(u0 + uRear))));
        out.add(Frustum.fromRings(n, ring(n, rRear, -(u0 + uRear)), ring(n, rLip, -(u0 + uLip)), LIP_V));
        return out;
    }

    /** A straight row of count scales at pitch 1.0, all with the same start radius. */
    public static List<Frustum> row(int n, double r0, int count, Params p) {
        List<Frustum> out = new ArrayList<>();
        for (int k = 0; k < count; k++) out.addAll(scale(n, r0, k * PITCH, p));
        return out;
    }

    private static double[][] ring(int n, double r, double z) {
        double[][] ring = new double[n][];
        for (int k = 0; k < n; k++) {
            double a = 2 * Math.PI * k / n;
            ring[k] = new double[]{r * Math.sin(a), r * Math.cos(a), z};
        }
        return ring;
    }
}
