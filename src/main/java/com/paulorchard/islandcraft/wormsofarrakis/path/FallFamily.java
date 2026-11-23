package com.paulorchard.islandcraft.wormsofarrakis.path;

import com.paulorchard.islandcraft.wormsofarrakis.geometry.BendLimit;

/**
 * The pose family of the fall (prompt 06): a whole-body planar curve, buried part included, pinned at the hole (the origin),
 * described by its heading phi(sigma) with sigma the arc length from the hole (positive up the exposed body to the head tip,
 * negative into the buried body). The curve is the integral of phi in both directions, so the hole is exactly at the origin and
 * arc lengths are exact. The four key frames come from the owner's sketch (polylines in body widths, hole at the origin); each
 * heading profile is the piecewise-constant sketch smoothed by a low-pass of about 0.5 W, then the exposed turn is spread so
 * that |dphi/dsigma| never exceeds 1 / (min radius), the buried part (free) keeping its lean. For a head tip height h between
 * the keys the profile is a monotone cubic (PCHIP) in h on a common sigma grid; below the last frame the last profile is kept
 * and the head sinks along it, above the first the first profile is kept. All lengths scale with height / 2.5 (not the width).
 * Pure code.
 */
public final class FallFamily {
    /** Sigma grid in table body widths (the sketch's W). */
    public static final double D = 0.0025, SIG_MIN = -3.2, SIG_MAX = 2.8;
    public static final int N = (int) Math.round((SIG_MAX - SIG_MIN) / D) + 1, I0 = (int) Math.round(-SIG_MIN / D);
    /** The sketch, frame 1 (the tower) to frame 4, tail to head, in body widths; the hole is at index HOLE. */
    public static final double[][][] SKETCH = {
            {{-0.159, -1.240}, {0, 0}, {0.318, 2.474}},
            {{-0.730, -1.240}, {0, 0}, {0.230, 0.390}, {0.383, 1.063}, {0.147, 2.003}},
            {{-1.668, -1.240}, {-0.401, -0.545}, {0, 0}, {0.090, 0.546}, {-0.418, 1.456}},
            {{-2.220, -1.240}, {-0.421, -0.309}, {0, 0}, {0.260, 0.394}}};
    public static final int[] HOLE = {1, 1, 2, 2};
    /** Reference numbers of the sketch (table W): head tip height, exposed length (hole to tip), buried arc length (tail to hole). */
    public static final double[] KEY_H = {2.474, 2.003, 1.456, 0.394};
    public static final double[] KEY_EXPOSED = {2.50, 2.11, 1.60, 0.47};
    public static final double[] KEY_DRIFT = {-0.159, -0.730, -1.668, -2.220};
    public static final double[] KEY_DX = {0.318, 0.147, -0.418, 0.260};
    public static final double TAIL_DEPTH = 1.24;
    public static final double SKETCH_HEIGHT = 2.5;
    /** The curve is limited to a radius 3% above the requested one, so that the joint angles of the unit-pitch chords (a polygon of the curve) stay inside the limit. */
    public static final double SAFETY = 1.03;

    private final double width, f, sc, minRadiusW, k, delta;
    private final double coneBlocks;
    private final double[][] node = new double[4][];     // clamped heading profiles, ascending tip height (node 0 = frame 4)
    private final double[][] slope = new double[4][];    // PCHIP derivative in h at each node
    private final double[] nodeH = new double[4];        // ascending tip heights (table W)

    public FallFamily(double width, double heightW, double leanDeg, double minRadiusW) {
        this.width = width;
        this.f = heightW / SKETCH_HEIGHT;
        this.sc = width * f;
        this.minRadiusW = minRadiusW;
        this.k = f / (minRadiusW * SAFETY);
        this.delta = Math.toRadians((90 - leanDeg) - Math.toDegrees(rawHeadingAt(0, 1.0)));
        this.coneBlocks = HeadSpec.coneHeight(width);
        for (int n = 0; n < 4; n++) {
            int frame = 3 - n;
            nodeH[n] = KEY_H[frame];
            node[n] = clamp(smooth(raw(frame)), k);
        }
        for (int j = 0; j < 4; j++) slope[j] = new double[N];
        for (int j = 0; j < N; j++) {
            double[] y = {node[0][j], node[1][j], node[2][j], node[3][j]};
            double[] d = pchip(nodeH, y);
            for (int n = 0; n < 4; n++) slope[n][j] = d[n];
        }
    }

    public double scale() { return f; }

    public double blocksPerTableW() { return sc; }

    // ---------------------------------------------------------------- the sketch profiles

    private static double[] cumulative(int frame) {
        double[][] p = SKETCH[frame];
        double[] cum = new double[p.length];
        for (int i = 1; i < p.length; i++) cum[i] = cum[i - 1] + Math.hypot(p[i][0] - p[i - 1][0], p[i][1] - p[i - 1][1]);
        return cum;
    }

    /** Arc position (table W, hole = 0) of each polyline vertex. */
    public static double[] vertexSigma(int frame) {
        double[] cum = cumulative(frame);
        double h = cum[HOLE[frame]];
        double[] s = new double[cum.length];
        for (int i = 0; i < s.length; i++) s[i] = cum[i] - h;
        return s;
    }

    /** Heading (radians) of the sketch's piece that contains sigma (table W); the end pieces extend. */
    public static double rawHeadingAt(int frame, double sigma) {
        double[][] p = SKETCH[frame];
        if (frame == 0) return Math.atan2(p[2][1] - p[0][1], p[2][0] - p[0][0]);   // the tower is one straight line
        double[] vs = vertexSigma(frame);
        int piece = 0;
        for (int i = 0; i < p.length - 1; i++) if (vs[i] <= sigma) piece = i;
        return Math.atan2(p[piece + 1][1] - p[piece][1], p[piece + 1][0] - p[piece][0]);
    }

    private static double[] raw(int frame) {
        double[] phi = new double[N];
        for (int j = 0; j < N; j++) phi[j] = rawHeadingAt(frame, SIG_MIN + j * D);
        return phi;
    }

    /** Low-pass of about 0.5 W: two boxes of 0.25 W, the ends extended with their end value. */
    private static double[] smooth(double[] phi) {
        int hw = (int) Math.round(0.125 / D);
        double[] a = phi;
        for (int pass = 0; pass < 2; pass++) {
            double[] b = new double[N];
            for (int j = 0; j < N; j++) {
                double s = 0;
                for (int i = -hw; i <= hw; i++) s += a[Math.max(0, Math.min(N - 1, j + i))];
                b[j] = s / (2 * hw + 1);
            }
            a = b;
        }
        return a;
    }

    /**
     * Rate-limits the heading above the sand (sigma >= 0): from the heading at the hole, which is kept, each step may turn by at
     * most kLimit (rad per table W); the buried part is free and keeps the sketch's lean. A profile that obeys the limit is unchanged.
     */
    static double[] clamp(double[] phi, double kLimit) {
        double[] out = phi.clone();
        double step = D * kLimit;
        for (int j = I0 + 1; j < N; j++) out[j] = Math.max(out[j - 1] - step, Math.min(out[j - 1] + step, phi[j]));
        return out;
    }

    /** Fritsch-Carlson monotone cubic derivatives. */
    static double[] pchip(double[] x, double[] y) {
        int n = x.length;
        double[] h = new double[n - 1], del = new double[n - 1], d = new double[n];
        for (int i = 0; i < n - 1; i++) {
            h[i] = x[i + 1] - x[i];
            del[i] = (y[i + 1] - y[i]) / h[i];
        }
        for (int i = 1; i < n - 1; i++) {
            if (del[i - 1] * del[i] <= 0) d[i] = 0;
            else {
                double w1 = 2 * h[i] + h[i - 1], w2 = h[i] + 2 * h[i - 1];
                d[i] = (w1 + w2) / (w1 / del[i - 1] + w2 / del[i]);
            }
        }
        d[0] = edge(h[0], h[1], del[0], del[1]);
        d[n - 1] = edge(h[n - 2], h[n - 3], del[n - 2], del[n - 3]);
        return d;
    }

    private static double edge(double h0, double h1, double d0, double d1) {
        double d = ((2 * h0 + h1) * d0 - h0 * d1) / (h0 + h1);
        if (Math.signum(d) != Math.signum(d0)) return 0;
        if (Math.signum(d0) != Math.signum(d1) && Math.abs(d) > 3 * Math.abs(d0)) return 3 * d0;
        return d;
    }

    // ---------------------------------------------------------------- the curve for one head tip height

    /** Heading profile (radians) for a tip height in table W, interpolated, re-limited and rotated for the lean. */
    private double[] profile(double ht) {
        double[] phi = new double[N];
        if (ht <= nodeH[0]) System.arraycopy(node[0], 0, phi, 0, N);
        else if (ht >= nodeH[3]) System.arraycopy(node[3], 0, phi, 0, N);
        else {
            int i = 0;
            while (ht > nodeH[i + 1]) i++;
            double h = nodeH[i + 1] - nodeH[i], t = (ht - nodeH[i]) / h;
            double t2 = t * t, t3 = t2 * t;
            double h00 = 2 * t3 - 3 * t2 + 1, h10 = t3 - 2 * t2 + t, h01 = -2 * t3 + 3 * t2, h11 = t3 - t2;
            for (int j = 0; j < N; j++) phi[j] = h00 * node[i][j] + h10 * h * slope[i][j] + h01 * node[i + 1][j] + h11 * h * slope[i + 1][j];
        }
        phi = clamp(phi, k);
        for (int j = 0; j < N; j++) phi[j] += delta;
        return phi;
    }

    /** The curve of one pose: heading, and the positions from the integral (table W), scaled to blocks on request. */
    public final class Shape implements PosCurve {
        public final double[] phi, x, y;
        final double xOff;

        Shape(double[] phi, double xOff) {
            this.phi = phi;
            this.xOff = xOff;
            x = new double[N];
            y = new double[N];
            for (int j = I0; j < N - 1; j++) {
                double m = 0.5 * (phi[j] + phi[j + 1]);
                x[j + 1] = x[j] + D * Math.cos(m);
                y[j + 1] = y[j] + D * Math.sin(m);
            }
            for (int j = I0; j > 0; j--) {
                double m = 0.5 * (phi[j] + phi[j - 1]);
                x[j - 1] = x[j] - D * Math.cos(m);
                y[j - 1] = y[j] - D * Math.sin(m);
            }
        }

        @Override
        public double[] pos(double s) {
            double u = s / sc, idx = (u - SIG_MIN) / D;
            if (idx <= 0) {
                double d = u - SIG_MIN;
                return new double[]{xOff + (x[0] + d * Math.cos(phi[0])) * sc, (y[0] + d * Math.sin(phi[0])) * sc};
            }
            if (idx >= N - 1) {
                double d = u - SIG_MAX;
                return new double[]{xOff + (x[N - 1] + d * Math.cos(phi[N - 1])) * sc, (y[N - 1] + d * Math.sin(phi[N - 1])) * sc};
            }
            int i = (int) idx;
            double t = idx - i;
            return new double[]{xOff + sc * (x[i] + t * (x[i + 1] - x[i])), sc * (y[i] + t * (y[i + 1] - y[i]))};
        }

        /** Heading (radians) at arc position s (blocks). */
        public double heading(double s) {
            double idx = Math.max(0, Math.min(N - 1, (s / sc - SIG_MIN) / D));
            int i = Math.min(N - 2, (int) idx);
            return phi[i] + (idx - i) * (phi[i + 1] - phi[i]);
        }

        /** Smallest radius of curvature (in body widths) over arc positions [sa, sb] (blocks); infinity if straight. */
        public double minRadiusW(double sa, double sb) {
            double best = Double.POSITIVE_INFINITY;
            int a = Math.max(0, (int) Math.floor((sa / sc - SIG_MIN) / D)), b = Math.min(N - 2, (int) Math.ceil((sb / sc - SIG_MIN) / D));
            for (int j = a; j < b; j++) {
                double dphi = Math.abs(phi[j + 1] - phi[j]);
                if (dphi > 1e-12) best = Math.min(best, D * f / dphi);
            }
            return best;
        }

        /** Arc position (blocks) where the curve is at height yBlocks (the curve rises monotonically). */
        public double arcAtHeight(double yBlocks) {
            double lo = SIG_MIN * sc - 4 * sc, hi = SIG_MAX * sc + 4 * sc;
            for (int i = 0; i < 60; i++) {
                double mid = 0.5 * (lo + hi);
                if (pos(mid)[1] < yBlocks) lo = mid; else hi = mid;
            }
            return 0.5 * (lo + hi);
        }
    }

    /** One pose of the family: the curve, the arc position of joint 0 (front of the head ring) and of the head tip, in blocks. */
    public final class Placed {
        public final Shape shape;
        public final double sigma0, sigmaTip;

        Placed(Shape shape, double sigma0) {
            this.shape = shape;
            this.sigma0 = sigma0;
            this.sigmaTip = sigma0 + coneBlocks;
        }
    }

    private double tipY(Shape sh, double s0) {
        WormPose p = WormPose.at(sh, s0, 2, 1.0, 2);
        return p.joints[0][1] + (p.joints[0][1] - p.joints[1][1]) * coneBlocks;
    }

    /** The pose whose head tip (the closed flap apex) is hBlocks above the base level, the hole at model z = holeX. */
    public Placed place(double hBlocks, double holeX) {
        Shape sh = new Shape(profile(hBlocks / sc), holeX);
        double lo = SIG_MIN * sc - 4 * sc, hi = SIG_MAX * sc + 4 * sc;
        for (int i = 0; i < 56; i++) {
            double mid = 0.5 * (lo + hi);
            if (tipY(sh, mid) < hBlocks) lo = mid; else hi = mid;
        }
        return new Placed(sh, 0.5 * (lo + hi));
    }

    /** Head-leading pose with the given number of segments (only the first {@code limit} are computed). */
    public WormPose pose(double hBlocks, int segments, double holeX, int limit) {
        Placed pl = place(hBlocks, holeX);
        return WormPose.at(pl.shape, pl.sigma0, segments, 1.0, limit);
    }

    /** Measured numbers of one pose, for the frame table and the tests. */
    public record Info(double tipDxW, double tipHeightW, double exposedW, double holeHeadingDeg, double tipHeadingDeg, double tailDriftW, double minRadiusW) {
        public String line() {
            return String.format(java.util.Locale.ROOT, "tipDx=%.2fW tipHeight=%.2fW exposedLength=%.2fW holeHeading=%.1fdeg tipHeading=%.1fdeg tailDrift=%.2fW minRadius=%.2fW",
                    tipDxW, tipHeightW, exposedW, holeHeadingDeg, tipHeadingDeg, tailDriftW, minRadiusW);
        }
    }

    public Info info(double hBlocks) {
        Placed pl = place(hBlocks, 0);
        Shape sh = pl.shape;
        WormPose p = WormPose.at(sh, pl.sigma0, 2, 1.0, 2);
        double tip = p.joints[0][1] + (p.joints[0][1] - p.joints[1][1]) * coneBlocks;
        double tailS = sh.arcAtHeight(-TAIL_DEPTH * sc);
        double mr = pl.sigmaTip > 0 ? sh.minRadiusW(0, pl.sigmaTip) : Double.POSITIVE_INFINITY;
        double tipX = p.joints[0][2] + (p.joints[0][2] - p.joints[1][2]) * coneBlocks;
        return new Info(tipX / width, tip / width, pl.sigmaTip / width, Math.toDegrees(sh.heading(0)), Math.toDegrees(sh.heading(pl.sigmaTip)),
                sh.pos(tailS)[0] / width, mr);
    }

    /** Heading (degrees) of the stored key profile of frame (0 = tower .. 3) at sigma (table W), after smoothing and clamping, with the lean. */
    public double keyHeadingDeg(int frame, double sigmaTable) {
        double[] p = node[3 - frame];
        double idx = Math.max(0, Math.min(N - 1, (sigmaTable - SIG_MIN) / D));
        int i = Math.min(N - 2, (int) idx);
        return Math.toDegrees(p[i] + (idx - i) * (p[i + 1] - p[i]) + delta);
    }

    public double minRadiusWidths() { return minRadiusW; }

    /** Segments the fall needs: the exposed length at the hold, the buried slant of the last frame, and one body width (blocks). */
    public int segmentsNeeded(double holdHeightBlocks) {
        double buried = cumulative(3)[HOLE[3]];
        Placed pl = place(holdHeightBlocks, 0);
        return (int) Math.ceil(pl.sigmaTip + buried * sc + width);
    }

    public static double limitRadiusWidths() { return BendLimit.MIN_BEND_RADIUS_WIDTHS; }
}
