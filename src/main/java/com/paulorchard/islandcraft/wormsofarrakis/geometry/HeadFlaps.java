package com.paulorchard.islandcraft.wormsofarrakis.geometry;

/**
 * Prompt 04: the three mouth flaps, hinged on the front-edge skin vertices of the lead ring (segment 0). The n front
 * vertices are split into three arcs (as equal as possible: 4/3/3 for n = 10, 17/17/16 for n = 50); the arc ends are shared
 * by neighbouring flaps. Closed, a flap is a fan of triangles from its hinge arc to an apex on the ring axis; open, the apex
 * swings about the chord through the two ends of the hinge arc. The hinge vertices ARE the ring's front vertices, computed
 * by the same function, so there is never a gap to the ring. Both faces are drawn (the inside is tinted like the liner).
 */
public final class HeadFlaps {
    public static final int FLAPS = 3;

    private HeadFlaps() {}

    /** Faces per flap: n split into 3 arcs, the larger ones first. */
    public static int[] split(int n) {
        int b = n / FLAPS, r = n % FLAPS;
        return new int[]{b + (r > 0 ? 1 : 0), b + (r > 1 ? 1 : 0), b};
    }

    /** First hinge vertex index of flap j. */
    public static int start(int n, int j) {
        int[] s = split(n);
        int at = 0;
        for (int i = 0; i < j; i++) at += s[i];
        return at;
    }

    /** Closed apex: on the axis of ring 0, coneHeight in front of its front edge. */
    public static double[] apex(ChainMath c, double coneHeight) {
        double[] tail = ChainMath.apply(c.frame[0], new double[]{0, 0, -1});
        return new double[]{c.center[0][0] - tail[0] * coneHeight, c.center[0][1] - tail[1] * coneHeight, c.center[0][2] - tail[2] * coneHeight};
    }

    /**
     * Apex of flap j opened by openRad about its hinge chord (the sign is chosen so the apex swings away from the axis).
     * Also returns, in out[0..2], the rotation axis (unit) and in out[3] the signed angle used.
     */
    public static double[] openApex(double[] apex, double[] e0, double[] e1, double[] outward, double openRad, double[] rot) {
        double[] u = norm(sub(e1, e0));
        double t = dot(sub(apex, e0), u);
        double[] foot = {e0[0] + u[0] * t, e0[1] + u[1] * t, e0[2] + u[2] * t};
        double[] w = sub(apex, foot);
        double best = Double.NEGATIVE_INFINITY;
        double[] res = apex;
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            double a = sgn * openRad;
            double[] r = rotate(w, u, a);
            double[] p = {foot[0] + r[0], foot[1] + r[1], foot[2] + r[2]};
            double score = dot(sub(p, apex), outward);
            if (score > best) {
                best = score;
                res = p;
                if (rot != null) { rot[0] = u[0]; rot[1] = u[1]; rot[2] = u[2]; rot[3] = a; }
            }
        }
        return openRad == 0 ? apex : res;
    }

    /** Appends the flaps of lead ring 0 to the result. delta0 = the rotation of ring 0 (stagger), openDeg = 0 for closed. */
    public static void append(ShingleMath.Result res, ChainMath c, int n, double r0, double delta0, double coneHeight, double openDeg,
                              ShingleMath.Options o) {
        double[][] v = new double[n + 1][];
        for (int i = 0; i <= n; i++) v[i] = ShingleMath.frontVertex(c, 0, n, r0, delta0, i % n);
        double[] apex = apex(c, coneHeight);
        double[] centre = c.center[0];
        double[] head = norm(sub(apex, centre));
        int[] sz = split(n);
        int at = 0;
        for (int j = 0; j < FLAPS; j++) {
            int i0 = at, i1 = at + sz[j];
            at = i1;
            double[] e0 = v[i0], e1 = v[i1];
            // outward direction of the flap: from the axis to the middle of its hinge arc
            double[] mid = new double[3];
            for (int i = i0; i <= i1; i++) for (int a = 0; a < 3; a++) mid[a] += v[i][a] / (i1 - i0 + 1);
            double[] rel = sub(mid, centre);
            double[] outward = norm(sub(rel, scale(head, dot(rel, head))));
            double[] rot = {1, 0, 0, 0};
            double[] ap = openApex(apex, e0, e1, outward, Math.toRadians(openDeg), rot);
            for (int f = i0; f < i1; f++) {
                // closed outward normal of the triangle, rotated with the flap
                double[] tri = {(v[f][0] + v[f + 1][0] + apex[0]) / 3, (v[f][1] + v[f + 1][1] + apex[1]) / 3, (v[f][2] + v[f + 1][2] + apex[2]) / 3};
                double[] tr = sub(tri, centre);
                double[] rad = sub(tr, scale(head, dot(tr, head)));
                double[] nrm = cross(sub(v[f + 1], v[f]), sub(apex, v[f]));
                if (dot(nrm, rad) < 0) nrm = scale(nrm, -1);
                nrm = norm(nrm);
                if (openDeg != 0) nrm = rotate(nrm, new double[]{rot[0], rot[1], rot[2]}, rot[3]);
                double[][] quad = {v[f], v[f + 1], ap, ap};
                float[][] uv = {{0, 0}, {1, 0}, {0.5f, 1}, {0.5f, 1}};
                double jit = Stagger.jitterFactor(o.seed(), 0, f, Stagger.SALT_FLAP, o.jitter());
                res.curColor = Stagger.scale(ShingleMath.WHITE, jit);
                res.add(ShingleMath.SKIN, quad, uv, nrm);
                res.curColor = Stagger.scale(o.linerColor(), jit);
                res.add(ShingleMath.LINER, quad, uv, scale(nrm, -1));
            }
        }
        res.curColor = ShingleMath.WHITE;
    }

    private static double[] rotate(double[] w, double[] u, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        double[] k = cross(u, w);
        double d = dot(u, w) * (1 - c);
        return new double[]{w[0] * c + k[0] * s + u[0] * d, w[1] * c + k[1] * s + u[1] * d, w[2] * c + k[2] * s + u[2] * d};
    }

    private static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }

    private static double[] scale(double[] a, double f) { return new double[]{a[0] * f, a[1] * f, a[2] * f}; }

    private static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    private static double[] norm(double[] a) {
        double l = Math.sqrt(dot(a, a));
        return l < 1e-12 ? a : new double[]{a[0] / l, a[1] / l, a[2] / l};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }
}
