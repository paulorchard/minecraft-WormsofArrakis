package com.paulorchard.islandcraft.wormsofarrakis.geometry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Prompt 08: the mouth of the worm, ported from the owner-approved web previs, in the head's own local frame (pure code, no Minecraft
 * classes). The head ring is segment 0: ring vertex v[i] at z = 0 with x = R sin a, y = R cos a, a = 2 pi i / n + delta, head toward +z.
 * <ul>
 * <li>Three mandible flaps ({@link HeadFlaps#split}) are shells: the outer skin (textured with unrolled-fan UVs, 1 block = 1 tile), an inner
 *     skin offset by the thickness along the averaged vertex normals, and side walls along the lip and both ridges, so the shell is closed.
 *     Opening rotates the apex about the chord of the flap's two end vertices ({@link HeadFlaps#openApex}); the arc stays on the ring.</li>
 * <li>The throat is the same flap set with its apex at -0.5 R behind the ring: flat, closed, never animated, no thickness.</li>
 * <li>Teeth (the owner's "filter teeth"): seeded bristles of two crossed triangles on the inner skin and on the throat.</li>
 * </ul>
 * Everything is deterministic. The mesh is two {@link ShingleMath.Result}s: {@code shell} (dripstone texture) and {@code flat} (a flat vanilla
 * texture tinted by vertex colour: the throat and the teeth). Local to world is centre[0] + frame[0] x local.
 */
public final class HeadMouth {
    /** The owner's chosen thickness multiplier (one named constant). */
    public static final double THICKNESS_MULTIPLIER = 1.0;
    /** Throat apex, in ring radii: negative = behind the ring's front edge, inside the worm. */
    public static final double THROAT_APEX_R = -0.5;
    public static final int FLAPS = HeadFlaps.FLAPS;

    // quad kinds (the bytes 0..4 belong to ShingleMath)
    public static final byte K_OUTER = 5, K_INNER = 6, K_WALL = 7, K_THROAT = 8, K_TOOTH_M = 9, K_TOOTH_T = 10;

    // locked, owner-approved teeth parameters (longest in ring radii, tilt in degrees)
    public static final double M_LONGEST = 0.7, M_TILT = 59, M_SPACING = 0.7, M_REACH = Double.POSITIVE_INFINITY;
    public static final int M_LAYERS = 16;
    public static final double T_LONGEST = 0.55, T_TILT = 33, T_SPACING = 0.45, T_REACH = 0.5;
    public static final int T_LAYERS = 8;
    /** How far the bristle base stands off the skin, in blocks. */
    public static final double BASE_PUSH = 0.02;

    /** Tint of the inner skin (the same texture, darkened). */
    public static final int INNER_TINT = 0xFF909090;
    /** The texel of the flat texture the tints are divided by (vanilla white_concrete, about 207/213/214), so the result is the wanted colour. */
    public static final double[] FLAT_TEXEL = {207 / 255.0, 213 / 255.0, 214 / 255.0};
    /** The throat colour, #3A3A3A. */
    public static final int THROAT_RGB = 0x3A3A3A;
    /** Flat ivory of the bristles (before the per-tooth shade and the tip-to-base average). */
    public static final int IVORY_RGB = 0xFFFFF0;

    private HeadMouth() {}

    /** Shell thickness in blocks: max(0.02, 0.05 + (W - 6) x 0.45 / 42) x multiplier. */
    public static double thickness(double width) {
        return Math.max(0.02, 0.05 + (width - 6) * 0.45 / 42) * THICKNESS_MULTIPLIER;
    }

    /**
     * The one uniform roll of every ring (radians, added to every ring's own angle) that puts the larger flap, the upper jaw, on top (+y):
     * the first flap covers vertices 0..sizes[0] at angles a = i step, its middle is at (sizes[0] / 2) step; a ring turned by
     * -(sizes[0] / 2) step has that middle at a = 0, which is (x, y) = (0, R), the top.
     */
    public static double roll(int n) {
        return -(HeadFlaps.split(n)[0] / 2.0) * 2 * Math.PI / n;
    }

    /** The built mouth. */
    public static final class Mesh {
        public final ShingleMath.Result shell = new ShingleMath.Result();
        public final ShingleMath.Result flat = new ShingleMath.Result();   // the throat (flat texture)
        public final ShingleMath.Result bristles = new ShingleMath.Result();   // the teeth (flat ivory texture, or bone_block)
        public final List<Tooth> teeth = new ArrayList<>();
        /** First shell quad of each flap (and one past the last): outer, inner and wall quads of a flap are contiguous. */
        public final int[] flapShell = new int[FLAPS + 1];
        public final double[][][] arcOuter = new double[FLAPS][][], arcInner = new double[FLAPS][][];
        public final double[][] apexOuter = new double[FLAPS][], apexInner = new double[FLAPS][];
        public double thickness, openDeg;
        public int mandibleTeeth, throatTeeth, toothQuads;
        public double buildMs, teethMs;
    }

    /** One bristle: base on the skin, unit direction, length, v (0 at the lip, 1 at the apex), unit normal into the cavity. */
    public record Tooth(boolean throat, int flap, double[] base, double[] dir, double length, double v, double[] nin) {}

    // ---------------------------------------------------------------------------------------------------------------- mandibles

    /**
     * The three mandible shells at an opening angle, plus (optionally) their teeth.
     * @param delta0 angle of ring vertex 0: the ring's own stagger plus {@link #roll}
     * @param coneH closed height of the apex in front of the ring (blocks)
     */
    public static Mesh mandibles(double width, int n, double R, double delta0, double coneH, double openDeg, ShingleMath.Options o, boolean withTeeth) {
        long t0 = System.nanoTime();
        Mesh m = new Mesh();
        m.openDeg = openDeg;
        double th = thickness(width);
        m.thickness = th;
        double[][] v = ring(n, R, delta0);
        double[] apexC = {0, 0, coneH};
        int[] sz = HeadFlaps.split(n);
        int at = 0;
        for (int j = 0; j < FLAPS; j++) {
            int i0 = at, i1 = at + sz[j], k = sz[j];
            at = i1;
            m.flapShell[j] = m.shell.quads;
            double[][] arc = new double[k + 1][];
            for (int a = 0; a <= k; a++) arc[a] = v[i0 + a];
            double[] mid = new double[3];
            for (double[] p : arc) for (int c = 0; c < 3; c++) mid[c] += p[c] / (k + 1);
            double[] outward = norm(new double[]{mid[0], mid[1], 0});
            double[] apex = HeadFlaps.openApex(apexC, arc[0], arc[k], outward, Math.toRadians(openDeg), null);
            double[] sgn = signs(arc, apexC);
            double[][] tn = new double[k][];
            for (int f = 0; f < k; f++) tn[f] = scale(norm(cross(sub(arc[f + 1], arc[f]), sub(apex, arc[f]))), sgn[f]);
            double[][] vn = new double[k + 1][];
            double[] an = new double[3];
            for (int a = 0; a <= k; a++) {
                double[] s = new double[3];
                if (a > 0) s = add(s, tn[a - 1]);
                if (a < k) s = add(s, tn[a]);
                vn[a] = norm(s);
            }
            for (int f = 0; f < k; f++) an = add(an, tn[f]);
            an = norm(an);
            double[][] in = new double[k + 1][];
            for (int a = 0; a <= k; a++) in[a] = sub(arc[a], scale(vn[a], th));
            double[] apexIn = sub(apex, scale(an, th));
            m.arcOuter[j] = arc;
            m.arcInner[j] = in;
            m.apexOuter[j] = apex;
            m.apexInner[j] = apexIn;
            float[][] uv = unrolledUv(arc, apexC);
            float[] uvA = {0f, 0f};
            for (int f = 0; f < k; f++) {
                double jit = Stagger.jitterFactor(o.seed(), 0, i0 + f, Stagger.SALT_FLAP, o.jitter());
                m.shell.curColor = Stagger.scale(ShingleMath.WHITE, jit);
                m.shell.add(K_OUTER, new double[][]{arc[f], arc[f + 1], apex, apex}, new float[][]{uv[f], uv[f + 1], uvA, uvA}, tn[f]);
            }
            for (int f = 0; f < k; f++) {
                double jit = Stagger.jitterFactor(o.seed(), 0, i0 + f, Stagger.SALT_FLAP, o.jitter());
                m.shell.curColor = Stagger.scale(INNER_TINT, jit);
                m.shell.add(K_INNER, new double[][]{in[f], in[f + 1], apexIn, apexIn}, new float[][]{uv[f], uv[f + 1], uvA, uvA}, scale(tn[f], -1));
            }
            m.shell.curColor = INNER_TINT;
            for (int f = 0; f < k; f++) {   // the lip wall, facing away from the apex
                double len = (float) dist(arc[f], arc[f + 1]);
                double[] hint = sub(scale(add(arc[f], arc[f + 1]), 0.5), apex);
                m.shell.add(K_WALL, new double[][]{arc[f], arc[f + 1], in[f + 1], in[f]}, new float[][]{{0, 0}, {(float) len, 0}, {(float) len, (float) th}, {0, (float) th}}, hint);
            }
            for (int side = 0; side < 2; side++) {   // the ridges: ring vertex to apex, closed by a wall facing out of the flap
                int e = side == 0 ? 0 : k, nb = side == 0 ? 1 : k - 1;
                float len = (float) dist(arc[e], apex);
                m.shell.add(K_WALL, new double[][]{arc[e], apex, apexIn, in[e]}, new float[][]{{0, 0}, {len, 0}, {len, (float) th}, {0, (float) th}}, sub(arc[e], arc[nb]));
            }
            if (withTeeth) {
                long t1 = System.nanoTime();
                double[][] nin = new double[k][];
                for (int f = 0; f < k; f++) nin[f] = scale(tn[f], -1);
                makeTeeth(m, false, j, in, apexIn, nin, R, seed(width, j, 0), M_LAYERS, M_LONGEST, M_TILT, M_SPACING, M_REACH);
                m.teethMs += (System.nanoTime() - t1) / 1e6;
            }
        }
        m.flapShell[FLAPS] = m.shell.quads;
        m.buildMs = (System.nanoTime() - t0) / 1e6;
        return m;
    }

    // ---------------------------------------------------------------------------------------------------------------- throat

    /** The fixed throat: the same three arcs with the apex at {@link #THROAT_APEX_R} ring radii, flat grey, never animated, plus its teeth. */
    public static Mesh throat(double width, int n, double R, double delta0, boolean withTeeth) {
        long t0 = System.nanoTime();
        Mesh m = new Mesh();
        double[][] v = ring(n, R, delta0);
        double[] apex = {0, 0, THROAT_APEX_R * R};
        int[] sz = HeadFlaps.split(n);
        int at = 0;
        int tint = flatTint(THROAT_RGB, 1.0);
        double[][][] ninAll = new double[FLAPS][][];
        for (int j = 0; j < FLAPS; j++) {
            int i0 = at, k = sz[j];
            at += k;
            double[][] arc = new double[k + 1][];
            for (int a = 0; a <= k; a++) arc[a] = v[i0 + a];
            double[] sgn = signs(arc, apex);
            double[][] nin = new double[k][];
            m.flat.curColor = tint;
            for (int f = 0; f < k; f++) {
                double[] outw = scale(norm(cross(sub(arc[f + 1], arc[f]), sub(apex, arc[f]))), sgn[f]);
                nin[f] = scale(outw, -1);   // faces the cavity
                m.flat.add(K_THROAT, new double[][]{arc[f], arc[f + 1], apex, apex}, new float[][]{{0, 0}, {1, 0}, {0.5f, 1}, {0.5f, 1}}, nin[f]);
            }
            ninAll[j] = nin;
            m.arcOuter[j] = arc;
            m.arcInner[j] = arc;
            m.apexOuter[j] = apex;
            m.apexInner[j] = apex;
        }
        if (withTeeth) {
            long t1 = System.nanoTime();
            for (int j = 0; j < FLAPS; j++) makeTeeth(m, true, j, m.arcOuter[j], apex, ninAll[j], R, seed(width, j, 1), T_LAYERS, T_LONGEST, T_TILT, T_SPACING, T_REACH);
            m.teethMs += (System.nanoTime() - t1) / 1e6;
        }
        m.buildMs = (System.nanoTime() - t0) / 1e6;
        return m;
    }

    // ---------------------------------------------------------------------------------------------------------------- teeth

    private static long seed(double width, int flap, int set) {
        return (Double.doubleToLongBits(width) * 1_000_003L + flap) * 31L + set;
    }

    /**
     * The previs's buildTeeth for one flap. lip = the arc vertices of the (inner) skin, apexIn the (inner) apex, nin per triangle the unit normal
     * into the cavity. Layers l = 0..L-1: u = 1 - (l + 0.5) / L x 0.9, v = (1 - u) / 0.9, bell = sin(pi v)^0.9, length = longest x R x
     * (0.12 + 0.88 bell) x (0.85 + 0.3 rand), count m = max(1, round(nT u / spacing)), tooth i at c = (i + 0.5 + (rand - 0.5) 0.5) / m x nT.
     */
    private static void makeTeeth(Mesh mesh, boolean throat, int flap, double[][] lip, double[] apexIn, double[][] nin, double R, long seed,
                                  int layers, double longest, double tiltDeg, double spacing, double reach) {
        Random rnd = new Random(seed);
        int nT = nin.length;
        double tau = Math.toRadians(tiltDeg), ct = Math.cos(tau), st = Math.sin(tau);
        double w = Math.max(0.05, 0.012 * R);
        ShingleMath.Result out = mesh.bristles;
        float[][] uv = {{0f, 0f}, {1f, 0f}, {0.5f, 1f}, {0.5f, 1f}};
        byte kind = throat ? K_TOOTH_T : K_TOOTH_M;
        for (int l = 0; l < layers; l++) {
            double u = 1 - (l + 0.5) / layers * 0.9, vv = (1 - u) / 0.9;
            if (vv > reach) continue;
            double bell = Math.pow(Math.sin(Math.PI * Math.max(0, Math.min(1, vv))), 0.9);
            int m = Math.max(1, (int) Math.round(nT * u / spacing));
            for (int i = 0; i < m; i++) {
                double len = longest * R * (0.12 + 0.88 * bell) * (0.85 + 0.3 * rnd.nextDouble());
                double c = (i + 0.5 + (rnd.nextDouble() - 0.5) * 0.5) / m * nT;
                int f = Math.min(nT - 1, Math.max(0, (int) Math.floor(c)));
                double t = Math.max(0, Math.min(1, c - f));
                double shade = 0.8 + 0.2 * rnd.nextDouble();
                double[] lp = lerp(lip[f], lip[f + 1], t);
                double[] base = add(lerp(apexIn, lp, u), scale(nin[f], BASE_PUSH));
                double[] g = throat ? norm(sub(apexIn, lp)) : norm(sub(lp, apexIn));
                double[] d = norm(add(scale(g, ct), scale(nin[f], st)));
                double[] e1 = norm(cross(d, nin[f]));
                double[] e2 = norm(cross(d, e1));
                double[] tip = add(base, scale(d, len));
                out.curColor = BONE_TEETH ? greyTint(0.89 * shade) : flatTint(IVORY_RGB, 0.89 * shade);   // base 1.0 and tip 0.78 averaged: one colour per blade
                out.add(kind, new double[][]{sub(base, scale(e1, w)), add(base, scale(e1, w)), tip, tip}, uv, e2);
                out.add(kind, new double[][]{sub(base, scale(e2, w)), add(base, scale(e2, w)), tip, tip}, uv, e1);
                mesh.teeth.add(new Tooth(throat, flap, base, d, len, vv, nin[f]));
                mesh.toothQuads += 2;
                if (throat) mesh.throatTeeth++; else mesh.mandibleTeeth++;
            }
        }
        out.curColor = ShingleMath.WHITE;
    }

    /** One-line switch: true draws the bristles with the vanilla bone_block_side texture (tint = shade only) instead of flat ivory on white_concrete. */
    public static boolean BONE_TEETH = false;

    /** ARGB grey vertex colour of the given brightness (the bone block texture is already ivory). */
    public static int greyTint(double f) { int c = clamp255(255 * f); return 0xFF000000 | (c << 16) | (c << 8) | c; }

    /** ARGB vertex colour that, multiplied by the flat texture texel, gives rgb x scale. */
    public static int flatTint(int rgb, double scale) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        int tr = clamp255(r * scale / FLAT_TEXEL[0]), tg = clamp255(g * scale / FLAT_TEXEL[1]), tb = clamp255(b * scale / FLAT_TEXEL[2]);
        return 0xFF000000 | (tr << 16) | (tg << 8) | tb;
    }

    private static int clamp255(double x) { return (int) Math.max(0, Math.min(255, Math.round(x))); }

    // ---------------------------------------------------------------------------------------------------------------- shared pieces

    /** Ring front vertices v[0..n] (v[n] = v[0]) in the head's local frame. */
    public static double[][] ring(int n, double R, double delta0) {
        double[][] v = new double[n + 1][];
        for (int i = 0; i <= n; i++) {
            double a = 2 * Math.PI * (i % n) / n + delta0;
            v[i] = new double[]{R * Math.sin(a), R * Math.cos(a), 0};
        }
        return v;
    }

    /** Per triangle: +1 when the cross product of (v[f+1] - v[f]) x (apex - v[f]) already points away from the axis, else -1 (read from the closed layout). */
    private static double[] signs(double[][] arc, double[] apexClosed) {
        double[] s = new double[arc.length - 1];
        for (int f = 0; f < s.length; f++) {
            double[] nrm = cross(sub(arc[f + 1], arc[f]), sub(apexClosed, arc[f]));
            double[] rad = {(arc[f][0] + arc[f + 1][0] + apexClosed[0]) / 3, (arc[f][1] + arc[f + 1][1] + apexClosed[1]) / 3, 0};
            s[f] = dot(nrm, rad) < 0 ? -1 : 1;
        }
        return s;
    }

    /**
     * Unrolled-fan UVs of one flap from its CLOSED layout (computed once, so the texture does not swim when the mouth moves): the apex is at
     * (0, 0); with d_i = |v_i - A| and phi_i the cumulative angle at the apex between consecutive rays, centred by the middle angle,
     * uv_i = (d_i sin(phi_i - phi_m), d_i cos(phi_i - phi_m)) in blocks, 1 block = 1 tile.
     */
    public static float[][] unrolledUv(double[][] arc, double[] apexClosed) {
        int k = arc.length - 1;
        double[] d = new double[k + 1], phi = new double[k + 1];
        for (int i = 0; i <= k; i++) d[i] = dist(arc[i], apexClosed);
        for (int i = 0; i < k; i++) {
            double[] a = sub(arc[i], apexClosed), b = sub(arc[i + 1], apexClosed);
            double cos = dot(a, b) / Math.max(1e-12, Math.sqrt(dot(a, a) * dot(b, b)));
            phi[i + 1] = phi[i] + Math.acos(Math.max(-1, Math.min(1, cos)));
        }
        double mid = phi[k] / 2;
        float[][] uv = new float[k + 1][];
        for (int i = 0; i <= k; i++) uv[i] = new float[]{(float) (d[i] * Math.sin(phi[i] - mid)), (float) (d[i] * Math.cos(phi[i] - mid))};
        return uv;
    }

    // ---------------------------------------------------------------------------------------------------------------- vectors

    static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }

    static double[] add(double[] a, double[] b) { return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }

    static double[] scale(double[] a, double f) { return new double[]{a[0] * f, a[1] * f, a[2] * f}; }

    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    static double dist(double[] a, double[] b) { double[] d = sub(a, b); return Math.sqrt(dot(d, d)); }

    static double[] lerp(double[] a, double[] b, double t) { return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t}; }

    static double[] norm(double[] a) {
        double l = Math.sqrt(dot(a, a));
        return l < 1e-12 ? a : new double[]{a[0] / l, a[1] / l, a[2] / l};
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }
}
