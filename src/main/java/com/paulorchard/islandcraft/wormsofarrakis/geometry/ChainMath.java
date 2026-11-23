package com.paulorchard.islandcraft.wormsofarrakis.geometry;

/**
 * Pose maths of a chain of straight segments (experiment 4), pure code.
 * Model space: the head is at z = 0 and the body extends towards -Z; the chain starts at height cy.
 * Segment k runs from joint k to joint k+1 along its own forward axis. At joint k (k >= 1) the segment is turned by
 * yaw[k] about its up axis and then pitch[k] about its right axis, i.e. frame[k] = frame[k-1] * Ry(yaw[k]) * Rx(pitch[k]),
 * the same order GeckoLib applies to a bone's rotation (X first, then Y), so the bone chain and this maths agree.
 */
public final class ChainMath {
    public final int segments;
    public final double length;
    /** joint positions, joints+1 = segments+1 entries. */
    public final double[][] center;
    /** frame[k] is the 3x3 rotation of segment k (row-major, columns = right, up, forward-local axes). */
    public final double[][][] frame;
    public final double[] radius;

    public ChainMath(int segments, double length, double cy, double[] yawRad, double[] pitchRad, double[] radius) {
        this.segments = segments;
        this.length = length;
        this.radius = radius;
        this.center = new double[segments + 1][];
        this.frame = new double[segments][][];
        double[][] f = ident();
        double[] c = {0, cy, 0};
        for (int k = 0; k < segments; k++) {
            if (k > 0) f = mul(mul(f, ry(yawRad[k])), rx(pitchRad[k]));
            frame[k] = f;
            center[k] = c;
            c = add(c, apply(f, new double[]{0, 0, -length}));
        }
        center[segments] = c;
    }

    private ChainMath(ChainMath o, double[] radius) {
        this.segments = o.segments;
        this.length = o.length;
        this.center = o.center;
        this.frame = o.frame;
        this.radius = radius;
    }

    /** Same spine and frames, different ring radii (the liner uses this). */
    public ChainMath withRadius(double[] radius) {
        return new ChainMath(this, radius);
    }

    public double[] axis(int k) {
        return apply(frame[k], new double[]{0, 0, -1});
    }

    /**
     * Ring of n points at joint j seen from the segment on one side, cut by the plane that bisects the two axes
     * (a mitre). incoming = true: the rear end of segment j-1; false: the front end of segment j.
     * In a pure yaw (or pure pitch) bend both rings are identical, so the skin is closed; for mixed bends they differ
     * by a second-order amount.
     */
    public double[][] miterRing(int j, boolean incoming, int n) {
        int a = Math.max(0, j - 1), b = Math.min(segments - 1, j);
        double[] a1 = axis(a), a2 = axis(b);
        double[] bis = add(a1, a2);
        double bl = Math.sqrt(dot(bis, bis));
        double[] nrm = bl < 1e-9 ? a1 : new double[]{bis[0] / bl, bis[1] / bl, bis[2] / bl};
        int seg = incoming ? a : b;
        double[] ax = incoming ? a1 : a2;
        double r = radius[j];
        double[][] ring = new double[n][];
        for (int k = 0; k < n; k++) {
            double ang = 2 * Math.PI * k / n;
            double[] off = apply(frame[seg], new double[]{r * Math.sin(ang), r * Math.cos(ang), 0});
            double t = -dot(nrm, off) / dot(nrm, ax);
            ring[k] = new double[]{center[j][0] + off[0] + t * ax[0], center[j][1] + off[1] + t * ax[1], center[j][2] + off[2] + t * ax[2]};
        }
        return ring;
    }

    /** Plain (un-mitred) ring of segment seg at its front (end = 0) or rear (end = 1): what a straight frustum has. */
    public double[][] plainRing(int seg, int end, int n) {
        double r = radius[seg + end];
        double[][] ring = new double[n][];
        double[] c = center[seg + end];
        for (int k = 0; k < n; k++) {
            double ang = 2 * Math.PI * k / n;
            double[] off = apply(frame[seg], new double[]{r * Math.sin(ang), r * Math.cos(ang), 0});
            ring[k] = add(c, off);
        }
        return ring;
    }

    /**
     * Width of the wedge-shaped opening on the outside of a bend between two straight, equal-radius segments joined at
     * their centre line, measured along the axis: r * tan(bend). (The overlap on the inside is the same size.)
     */
    public static double outerGap(double radius, double bendRad) {
        return radius * Math.tan(Math.abs(bendRad));
    }

    static double[][] ident() { return new double[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}; }

    static double[][] ry(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[][]{{c, 0, s}, {0, 1, 0}, {-s, 0, c}};
    }

    static double[][] rx(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[][]{{1, 0, 0}, {0, c, -s}, {0, s, c}};
    }

    static double[][] mul(double[][] a, double[][] b) {
        double[][] r = new double[3][3];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) for (int k = 0; k < 3; k++) r[i][j] += a[i][k] * b[k][j];
        return r;
    }

    static double[] apply(double[][] m, double[] v) {
        return new double[]{m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2], m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
                m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]};
    }

    static double[] add(double[] a, double[] b) { return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }

    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
}
