package com.paulorchard.islandcraft.wormsofarrakis.geometry;

import java.util.Arrays;

/**
 * Prompt 02: the shingle skin of a bent chain, pure code (no Minecraft classes, unit tested).
 * <p>
 * Segment k of a {@link ChainMath} is a rigid piece of {@code per} scales (pitch 1.0 each). Its plates are drawn from
 * the segment's own frame; the joint between segment k-1 and k is where the skin can open (outside of a bend) or
 * intersect (inside). Per ring vertex i at the joint:
 * <pre>
 * P = joint + frame(k-1) * (r0 sin a_i, r0 cos a_i, 0)      plate end of segment k-1, as if it ended at the joint
 * Q = joint + frame(k)   * (r0 sin a_i, r0 cos a_i, 0)      front ring of the next plate
 * gap_i = (Q - P) . axis(k-1)                                positive = opening, negative = overlap
 * </pre>
 * The last plate of segment k-1 then gets a per-vertex lip: gap &gt; 0: lip = min(max(base, gap + margin), maxLip),
 * gap &lt; 0: lip = max(minLip, base + gap) and the skin's trailing edge is cut by min(-gap, maxCut * skin).
 * Plates of one segment (not the last) keep the base lip. A dark liner (the closed mitre-ring loft skin at radius
 * r0 - inset) sits behind everything. Everything is one-sided, outer faces only.
 */
public final class ShingleMath {
    public static final double OVERLAP_MARGIN = 0.05;
    public static final double MAX_LIP_FRACTION = 0.6;   // of the pitch
    public static final double MIN_LIP = 0.02;
    public static final double MAX_CUT_FRACTION = 0.3;   // of the skin length
    /** Removed in prompt 04 (the liner has no fixed floor): kept only so older references compile. */
    public static final double LINER_INSET = 0.0;
    public static final double LINER_MARGIN = 0.02;      // liner stays this far inside the deepest plate edge
    public static final double RIM_PX = 1.0;
    /** Vertex colour multiplier of the liner (ARGB). */
    public static final int LINER_TINT = 0xFFC0C0C0;   // about 75% grey: the liner shows the dripstone tiles, the plates stay proud
    public static final int LINER_DARK = 0xFF404040;   // the old dark liner (flag: liner dark)
    public static final int WHITE = 0xFFFFFFFF;

    public static final byte LINER = 0, SKIN = 1, LIP = 2, RIM_SKIN = 3, RIM_LIP = 4;

    private ShingleMath() {}

    public record Options(double baseLip, double overlapMargin, double maxLipFraction, double minLip, double maxCutFraction,
                          double rimPx, double linerInset, boolean liner, double stagger, long seed, boolean offset, double jitter, int linerColor, double linerMargin) {
        /** Plain geometry: no stagger, no tile variation, no jitter (the maths tests of prompts 02 and 03 use this). */
        public static Options defaults(double baseLip, boolean liner) {
            return new Options(baseLip, OVERLAP_MARGIN, MAX_LIP_FRACTION, MIN_LIP, MAX_CUT_FRACTION, RIM_PX, 0.0, liner, 0.0, 0L, false, 0.0, LINER_TINT, LINER_MARGIN);
        }

        public Options withVariety(double stagger, long seed, boolean offset, double jitter) {
            return new Options(baseLip, overlapMargin, maxLipFraction, minLip, maxCutFraction, rimPx, linerInset, liner, stagger, seed, offset, jitter, linerColor, linerMargin);
        }

        public Options withLinerColor(int color) {
            return new Options(baseLip, overlapMargin, maxLipFraction, minLip, maxCutFraction, rimPx, linerInset, liner, stagger, seed, offset, jitter, color, linerMargin);
        }
    }

    /** Built geometry: vertices are 8 floats (x y z u v nx ny nz), 4 per quad, one byte kind per quad. */
    public static final class Result {
        public float[] v = new float[8 * 4 * 1024];
        public byte[] kind = new byte[1024];
        public int quads;
        public final int[] byKind = new int[5];
        /** gap[j][i] for joints j = 1..segments-1 (index 0 unused), per ring vertex; null entries for j = 0. */
        public double[][] gap;
        /** lip length chosen for the last plate of segment j-1 at joint j, per vertex. */
        public double[][] lip;
        public int[] color = new int[1024];
        /** Quad colour used by add() (set by the builder before each call). */
        int curColor = WHITE;
        public double linerInsetUsed;   // the largest inset of any built ring
        public double linerInsetMin = Double.POSITIVE_INFINITY;
        /** Per ring: deepest plate, lip or rim vertex radius (local radial), and the radius of the liner at joint j. */
        public double[] deepest, linerRadius, ringDelta;
        /** Texture offset in pixels (0..15) of each ring. */
        public int[] offsetPx;
        /** Liner ring of vertices at each joint (null where not built), shared by the two neighbouring liner segments. */
        public double[][][] linerJoint;
        public double maxGap = Double.NEGATIVE_INFINITY, minGap = Double.POSITIVE_INFINITY;
        public int uncovered;       // vertices where gap + margin exceeded the maximum lip (only the liner closes those)
        public int cutVertices;     // vertices where the skin trailing edge was shortened
        public int uncutIntersections; // vertices where the overlap exceeded even the maximum cut
        public double minPlateRadius = Double.POSITIVE_INFINITY;   // lowest radial distance of any plate/lip/rim vertex from its segment axis

        void add(byte k, double[][] p, float[][] uv, double[] hint) {
            // normal from the quad, sign chosen so it agrees with the hint (outward / tailward), winding follows it
            double[] a = sub(p[1], p[0]), b = sub(p[3], p[0]);
            double[] nrm = cross(a, b);
            double len = Math.sqrt(dot(nrm, nrm));
            if (len < 1e-12) { // degenerate: use the other diagonal
                nrm = cross(sub(p[2], p[1]), sub(p[0], p[1]));
                len = Math.sqrt(dot(nrm, nrm));
                if (len < 1e-12) return;
            }
            int[] order = {0, 1, 2, 3};
            if (dot(nrm, hint) < 0) { order = new int[]{0, 3, 2, 1}; nrm = new double[]{-nrm[0], -nrm[1], -nrm[2]}; }
            if (quads * 32 + 32 > v.length) v = Arrays.copyOf(v, v.length * 2);
            if (quads + 1 > kind.length) kind = Arrays.copyOf(kind, kind.length * 2);
            for (int q = 0; q < 4; q++) {
                int o = order[q], at = quads * 32 + q * 8;
                v[at] = (float) p[o][0]; v[at + 1] = (float) p[o][1]; v[at + 2] = (float) p[o][2];
                v[at + 3] = uv[o][0]; v[at + 4] = uv[o][1];
                v[at + 5] = (float) (nrm[0] / len); v[at + 6] = (float) (nrm[1] / len); v[at + 7] = (float) (nrm[2] / len);
            }
            if (quads + 1 > color.length) color = Arrays.copyOf(color, color.length * 2);
            color[quads] = curColor;
            kind[quads] = k;
            byKind[k]++;
            quads++;
        }
    }

    /** Gap per ring vertex at joint j (between segment j-1 and j), from the actual frames; both rings unrotated. */
    public static double[] jointGap(ChainMath c, int j, int n, double r0) {
        return jointGap(c, j, n, r0, 0.0, 0.0);
    }

    /**
     * Gap at joint j for the vertices of ring j-1 (turned by dPrev about its axis) against the real surface of ring j
     * (turned by dNext): the point of ring j's polygon at the same angle, found on its face edge, so rings that do not
     * line up (stagger) are measured against what is really there, not against the vertex with the same index.
     */
    public static double[] jointGap(ChainMath c, int j, int n, double r0, double dPrev, double dNext) {
        double[] g = new double[n];
        double[] axis = c.axis(j - 1);
        double step = 2 * Math.PI / n, apo = Math.cos(Math.PI / n);
        for (int i = 0; i < n; i++) {
            double a = step * i + dPrev;
            double t = (a - dNext) % step;
            if (t < 0) t += step;
            double rho = r0 * apo / Math.cos(t - Math.PI / n);
            double[] p = ChainMath.apply(c.frame[j - 1], new double[]{r0 * Math.sin(a), r0 * Math.cos(a), 0});
            double[] q = ChainMath.apply(c.frame[j], new double[]{rho * Math.sin(a), rho * Math.cos(a), 0});
            g[i] = dot(sub(q, p), axis);
        }
        return g;
    }

    /** World position of the front-edge skin vertex i of ring k (the vertex the head flaps hinge on). */
    public static double[] frontVertex(ChainMath c, int k, int n, double r0, double delta, int i) {
        double a = 2 * Math.PI * i / n + delta;
        return world(c, k, c.frame[k], Math.sin(a), Math.cos(a), r0, 0);
    }

    public static Result build(ChainMath c, int n, double r0, int per, Options o) {
        return build(c, n, r0, per, o, 0, c.segments - 1);
    }

    /** Builds only segments from..to (inclusive); gaps are computed for the joints around them from the full chain. */
    public static Result build(ChainMath c, int n, double r0, int per, Options o, int from, int to) {
        Result res = new Result();
        double tilt = Math.toRadians(ScaleMath.TILT_DEG), tan = Math.tan(tilt), sin = Math.sin(tilt), cos = Math.cos(tilt);
        double skin = ScaleMath.SKIN_LENGTH, pitch = ScaleMath.PITCH;
        double rimW = o.rimPx() / 16.0;
        int segs = c.segments;
        res.gap = new double[segs][];
        res.lip = new double[segs][];
        res.deepest = new double[segs];
        Arrays.fill(res.deepest, Double.POSITIVE_INFINITY);
        res.ringDelta = new double[segs];
        if (o.stagger() != 0) {
            double[] u = Stagger.offsets(o.seed(), segs);
            for (int k = 0; k < segs; k++) res.ringDelta[k] = u[k] * o.stagger() * (2 * Math.PI / n);
        }
        double maxLip = o.maxLipFraction() * pitch;

        // pass 1: per-joint gaps, lips, cuts
        double[][][] lipAt = new double[segs][][], cutAt = new double[segs][][];
        for (int j = Math.max(1, from); j <= Math.min(segs - 1, to + 1); j++) {
            double[] g = jointGap(c, j, n, r0, res.ringDelta[j - 1], res.ringDelta[j]);
            res.gap[j] = g;
            double[] lip = new double[n], cut = new double[n];
            for (int i = 0; i < n; i++) {
                res.maxGap = Math.max(res.maxGap, g[i]);
                res.minGap = Math.min(res.minGap, g[i]);
                if (g[i] > 0) {
                    double want = Math.max(o.baseLip(), g[i] + o.overlapMargin());
                    if (want > maxLip + 1e-12) res.uncovered++;
                    lip[i] = Math.min(want, Math.max(maxLip, o.baseLip()));
                } else {
                    lip[i] = Math.max(o.minLip(), o.baseLip() + g[i]);
                    double over = -g[i];
                    cut[i] = Math.min(over, o.maxCutFraction() * skin);
                    if (cut[i] > 1e-9) res.cutVertices++;
                    if (over > o.maxCutFraction() * skin + 1e-9) res.uncutIntersections++;
                }
            }
            res.lip[j] = lip;
            lipAt[j - 1] = new double[][]{lip};
            cutAt[j - 1] = new double[][]{cut};
        }

        // texture offsets: one integer pixel offset per whole ring (skin, lips, rims and liner of the ring move together)
        int[] offPx = o.offset() ? Stagger.ringOffsetsPx(o.seed(), segs) : new int[segs];
        res.offsetPx = offPx;

        // pass 2: plates
        for (int k = Math.max(0, from); k <= Math.min(segs - 1, to); k++) {
            double[][] f = c.frame[k];
            double dk = res.ringDelta[k];
            float shift = offPx[k] / 16f;
            for (int s = 0; s < per; s++) {
                boolean last = s == per - 1 && k < segs - 1;
                double[] lipL = last ? lipAt[k][0] : null, cutL = last ? cutAt[k][0] : null;
                double u0 = s * pitch;
                double[][] front = new double[n][], rear = new double[n][], lipEnd = new double[n][];
                double[][] lipRim = new double[n][], leadEnd = new double[n][];
                double[] lipLen = new double[n];
                for (int i = 0; i < n; i++) {
                    double a = 2 * Math.PI * i / n + dk, sa = Math.sin(a), ca = Math.cos(a);
                    double uRear = skin * cos - (cutL == null ? 0 : cutL[i]);
                    double rRear = r0 + uRear * tan;
                    double L = lipL == null ? o.baseLip() : lipL[i];
                    lipLen[i] = L;
                    front[i] = world(c, k, f, sa, ca, r0, u0);
                    rear[i] = world(c, k, f, sa, ca, rRear, u0 + uRear);
                    double rl = rRear - L * cos, ul = uRear + L * sin;
                    lipEnd[i] = world(c, k, f, sa, ca, rl, u0 + ul);
                    double rr2 = rl - rimW * cos, ur2 = ul + rimW * sin;
                    lipRim[i] = world(c, k, f, sa, ca, rr2, u0 + ur2);
                    // leading-edge rim hangs inward from the leading edge, perpendicular to the skin, facing the head
                    double rd = r0 - rimW * cos, ud = rimW * sin;
                    leadEnd[i] = world(c, k, f, sa, ca, rd, u0 + ud);
                    res.deepest[k] = Math.min(res.deepest[k], Math.min(rr2, rd));
                    res.minPlateRadius = Math.min(res.minPlateRadius, Math.min(rr2, rd));
                }
                double[] tail = ChainMath.apply(f, new double[]{0, 0, -1});
                double[] head = {-tail[0], -tail[1], -tail[2]};
                float rw = (float) rimW;
                for (int i = 0; i < n; i++) {
                    int jn = (i + 1) % n;
                    double am = 2 * Math.PI * (i + 0.5) / n + dk;
                    double[] radial = ChainMath.apply(f, new double[]{Math.sin(am), Math.cos(am), 0});
                    res.curColor = Stagger.scale(WHITE, Stagger.jitterFactor(o.seed(), k, i, Stagger.SALT_JITTER, o.jitter()));
                    float li = (float) lipLen[i], lj = (float) lipLen[jn];
                    // the skin shows one tile; a lip or rim samples only its own slice at tile scale: one block of length = one tile,
                    // so its height in tiles equals its height in blocks (the lip continues the tile past the skin's trailing edge:
                    // rows 0..L, the cyclic continuation of the skin), never the whole tile stretched over the strip
                    res.add(SKIN, new double[][]{front[i], front[jn], rear[jn], rear[i]}, uv(shift, 0f, 0f, 1f, 1f), radial);
                    res.add(LIP, new double[][]{rear[i], rear[jn], lipEnd[jn], lipEnd[i]}, uv(shift, 0f, 0f, lj, li, rear[i], rear[jn], lipEnd[i], lipEnd[jn]), tail);
                    if (o.rimPx() > 0) {
                        res.add(RIM_LIP, new double[][]{lipEnd[i], lipEnd[jn], lipRim[jn], lipRim[i]}, uv(shift, li, lj, lj + rw, li + rw, lipEnd[i], lipEnd[jn], lipRim[i], lipRim[jn]), tail);
                        res.add(RIM_SKIN, new double[][]{front[i], front[jn], leadEnd[jn], leadEnd[i]}, uv(shift, 0f, 0f, rw, rw, front[i], front[jn], leadEnd[i], leadEnd[jn]), head);
                    }
                }
            }
        }

        // liner: closed loft right under the skin. Per ring its radius is the deepest plate, lip or rim vertex of that
        // ring minus a small margin (no fixed floor); at a joint it is the smaller of the two neighbouring rings.
        double[] rad = new double[segs + 1];
        Arrays.fill(rad, r0 - 0.25);
        for (int j = 0; j <= segs; j++) {
            double d = Double.POSITIVE_INFINITY;
            if (j - 1 >= Math.max(0, from) && j - 1 <= Math.min(segs - 1, to)) d = Math.min(d, res.deepest[j - 1]);
            if (j >= Math.max(0, from) && j <= Math.min(segs - 1, to)) d = Math.min(d, res.deepest[j]);
            if (d < Double.POSITIVE_INFINITY) rad[j] = d - o.linerMargin();
        }
        res.linerRadius = rad;
        for (int j = Math.max(0, from); j <= Math.min(segs, to + 1); j++) {
            res.linerInsetUsed = Math.max(res.linerInsetUsed, r0 - rad[j]);
            res.linerInsetMin = Math.min(res.linerInsetMin, r0 - rad[j]);
        }
        if (o.liner()) {
            // one ring of vertices per joint, shared by the liner segments on both sides (never rotated per ring)
            double ph = Math.PI / n;   // the liner seams sit half a face away from the plate edges
            double[][][] joint = new double[segs + 1][][];
            res.linerJoint = joint;
            for (int k = Math.max(0, from); k <= Math.min(segs - 1, to); k++) {
                if (joint[k] == null) joint[k] = jointRing(c, k, n, rad[k], ph);
                if (joint[k + 1] == null) joint[k + 1] = jointRing(c, k + 1, n, rad[k + 1], ph);
                double[][] fr = joint[k], rr = joint[k + 1];
                float shift = ((offPx[k] + (o.offset() ? 8 : 0)) % 16) / 16f;   // the liner is half a tile away from its ring's plates
                for (int i = 0; i < n; i++) {
                    int jn = (i + 1) % n;
                    double am = 2 * Math.PI * (i + 0.5) / n + ph;
                    double[] radial = ChainMath.apply(c.frame[k], new double[]{Math.sin(am), Math.cos(am), 0});
                    res.curColor = Stagger.scale(o.linerColor(), Stagger.jitterFactor(o.seed(), k, i, Stagger.SALT_LINER_JITTER, o.jitter()));
                    res.add(LINER, new double[][]{fr[i], fr[jn], rr[jn], rr[i]}, uv(shift, 0f, 0f, 1f, 1f), radial);
                }
            }
        }
        res.curColor = WHITE;
        return res;
    }

    /**
     * The liner ring at joint j: n points on a circle of radius r in the mitre plane (the plane that bisects the two axes),
     * built once and used by both neighbouring liner segments, so the liner is watertight at every joint.
     */
    public static double[][] jointRing(ChainMath c, int j, int n, double r, double phase) {
        int a = Math.max(0, j - 1), b = Math.min(c.segments - 1, j);
        double[] a1 = c.axis(a), a2 = c.axis(b);
        double[] bis = {a1[0] + a2[0], a1[1] + a2[1], a1[2] + a2[2]};
        double bl = Math.sqrt(dot(bis, bis));
        double[] nrm = bl < 1e-9 ? a1 : new double[]{bis[0] / bl, bis[1] / bl, bis[2] / bl};
        double[] x = ChainMath.apply(c.frame[b], new double[]{1, 0, 0}), y = ChainMath.apply(c.frame[b], new double[]{0, 1, 0});
        x = unit(project(x, nrm));
        y = unit(project(y, nrm));
        double xy = dot(x, y);
        y = unit(new double[]{y[0] - x[0] * xy, y[1] - x[1] * xy, y[2] - x[2] * xy});
        double[][] ring = new double[n][];
        for (int i = 0; i < n; i++) {
            double ang = 2 * Math.PI * i / n + phase, sa = Math.sin(ang), ca = Math.cos(ang);
            ring[i] = new double[]{c.center[j][0] + r * (sa * x[0] + ca * y[0]), c.center[j][1] + r * (sa * x[1] + ca * y[1]), c.center[j][2] + r * (sa * x[2] + ca * y[2])};
        }
        return ring;
    }

    private static double[] project(double[] v, double[] nrm) {
        double d = dot(v, nrm);
        return new double[]{v[0] - nrm[0] * d, v[1] - nrm[1] * d, v[2] - nrm[2] * d};
    }

    private static double[] unit(double[] v) {
        double l = Math.sqrt(dot(v, v));
        return new double[]{v[0] / l, v[1] / l, v[2] / l};
    }

    private static double[] world(ChainMath c, int k, double[][] f, double sa, double ca, double r, double u) {
        double[] l = ChainMath.apply(f, new double[]{r * sa, r * ca, -u});
        return new double[]{c.center[k][0] + l[0], c.center[k][1] + l[1], c.center[k][2] + l[2]};
    }

    /**
     * UV corners of a face in the order (near edge i, near edge i+1, far edge i+1, far edge i). u runs one tile along the
     * face, starting at the ring's texture offset (shift, in tiles; the sampler repeats, see the report); v is the distance
     * from the strip's near edge in tiles (one block = one tile) with its own value per vertex.
     */
    public static float[][] uv(float shift, float vNearI, float vNearJ, float vFarJ, float vFarI) {
        return new float[][]{{shift, vNearI}, {shift + 1f, vNearJ}, {shift + 1f, vFarJ}, {shift, vFarI}};
    }

    /** As above, but u runs for the real length (in blocks = tiles) of the strip's near and far edge, so a lip or rim keeps tile scale along its length too. */
    public static float[][] uv(float shift, float vNearI, float vNearJ, float vFarJ, float vFarI, double[] nearI, double[] nearJ, double[] farI, double[] farJ) {
        float un = (float) Math.sqrt(dot(sub(nearJ, nearI), sub(nearJ, nearI))), uf = (float) Math.sqrt(dot(sub(farJ, farI), sub(farJ, farI)));
        return new float[][]{{shift, vNearI}, {shift + un, vNearJ}, {shift + uf, vFarJ}, {shift, vFarI}};
    }

    private static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }

    private static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }
}
