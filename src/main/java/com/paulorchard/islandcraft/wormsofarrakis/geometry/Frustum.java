package com.paulorchard.islandcraft.wormsofarrakis.geometry;

/**
 * A lofted tube piece: n quads between a front ring and a rear ring (each n points), outer faces only, normals
 * outward, one UV tile per quad. Quad vertex order is counter-clockwise seen from outside (checked in unit tests;
 * GeckoLib's own baked cubes use the same convention, see GeckoBakeTest).
 * Quad i = front[i], front[i+1], rear[i+1], rear[i] with UVs (0,0) (1,0) (1,1) (0,1).
 */
public final class Frustum {
    public final int n;
    /** quads[i] = 4 vertices * (x,y,z,u,v) = 20 floats. */
    public final float[][] quads;
    /** normals[i] = outward unit normal (x,y,z). */
    public final float[][] normals;

    private Frustum(int n) {
        this.n = n;
        this.quads = new float[n][20];
        this.normals = new float[n][3];
    }

    /** Ring vertex k sits at angle 2*pi*k/n: (x, y, z) = (r sin a, r cos a, z). Axis along Z, centred on x = y = 0. */
    public static Frustum build(int n, double r1, double r2, double z0, double length) {
        double[][] front = new double[n][], rear = new double[n][];
        for (int k = 0; k < n; k++) {
            double a = 2 * Math.PI * k / n;
            front[k] = new double[]{r1 * Math.sin(a), r1 * Math.cos(a), z0};
            rear[k] = new double[]{r2 * Math.sin(a), r2 * Math.cos(a), z0 + length};
        }
        return fromRings(n, front, rear);
    }

    /** General case (bent chains): any two rings of n points; "outward" = away from the line through the ring centres. */
    public static Frustum fromRings(int n, double[][] front, double[][] rear) {
        double[] c0 = centroid(front, n), c1 = centroid(rear, n);
        double[] axis = sub(c1, c0);
        double axisLen2 = Math.max(1e-12, dot(axis, axis));
        Frustum f = new Frustum(n);
        float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double[][] p = {front[i], front[j], rear[j], rear[i]};
            double[] nrm = cross(sub(p[1], p[0]), sub(p[3], p[0]));
            double[] mid = {(p[0][0] + p[1][0] + p[2][0] + p[3][0]) / 4, (p[0][1] + p[1][1] + p[2][1] + p[3][1]) / 4,
                    (p[0][2] + p[1][2] + p[2][2] + p[3][2]) / 4};
            double t = dot(sub(mid, c0), axis) / axisLen2;
            double[] radial = sub(mid, new double[]{c0[0] + axis[0] * t, c0[1] + axis[1] * t, c0[2] + axis[2] * t});
            boolean flip = dot(nrm, radial) < 0;
            int[] order = flip ? new int[]{0, 3, 2, 1} : new int[]{0, 1, 2, 3};
            if (flip) { nrm[0] = -nrm[0]; nrm[1] = -nrm[1]; nrm[2] = -nrm[2]; }
            double len = Math.max(1e-12, Math.sqrt(dot(nrm, nrm)));
            for (int k = 0; k < 4; k++) {
                int o = order[k];
                f.quads[i][k * 5] = (float) p[o][0];
                f.quads[i][k * 5 + 1] = (float) p[o][1];
                f.quads[i][k * 5 + 2] = (float) p[o][2];
                f.quads[i][k * 5 + 3] = uv[o][0];
                f.quads[i][k * 5 + 4] = uv[o][1];
            }
            f.normals[i][0] = (float) (nrm[0] / len);
            f.normals[i][1] = (float) (nrm[1] / len);
            f.normals[i][2] = (float) (nrm[2] / len);
        }
        return f;
    }

    private static double[] centroid(double[][] ring, int n) {
        double[] c = new double[3];
        for (double[] p : ring) { c[0] += p[0]; c[1] += p[1]; c[2] += p[2]; }
        return new double[]{c[0] / n, c[1] / n, c[2] / n};
    }

    private static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    private static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }
}
