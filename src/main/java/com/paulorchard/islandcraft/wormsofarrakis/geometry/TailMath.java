package com.paulorchard.islandcraft.wormsofarrakis.geometry;

/**
 * Prompt 10 part 2 (tail-1): the tail of the crest, pure code. The last {@code len} of the body tapers to a small nub, built from rings like the rest of the body
 * (the same texture and colour handling, outer faces only), closed by a small rounded cap at the last joint.
 *
 * <p>Radius at joint k (0 = head, n = last joint): with n0 = round(n x (1 - taillen)), r(k) = r0 for k <= n0, else u = (k - n0) / (n - n0) and
 * r(k) = r0 x (nub + (1 - nub) x (1 - u)^curve). The segment from joint k to k+1 (k >= n0) is a frustum from r(k) to r(k+1) of
 * max(8, round(pi x W x r(k) / r0)) faces.
 */
public final class TailMath {
    private TailMath() {}

    public static final double DEFAULT_LEN = 0.3, DEFAULT_NUB = 0.12, DEFAULT_CURVE = 1.0;
    public static final double MIN_LEN = 0.1, MAX_LEN = 0.6, MIN_NUB = 0.03, MAX_NUB = 0.4, MIN_CURVE = 0.5, MAX_CURVE = 2.5;
    public static final int MIN_FACES = 8;

    /** The keys tail, taillen, tailnub, tailcurve of a crest. */
    public record Spec(boolean on, double len, double nub, double curve) {
        public static final Spec DEFAULT = new Spec(true, DEFAULT_LEN, DEFAULT_NUB, DEFAULT_CURVE);
        public static final Spec OFF = new Spec(false, DEFAULT_LEN, DEFAULT_NUB, DEFAULT_CURVE);
        public boolean isDefault() { return on && len == DEFAULT_LEN && nub == DEFAULT_NUB && curve == DEFAULT_CURVE; }
    }

    /** The first joint of the taper: n0 = round(n x (1 - taillen)). */
    public static int n0(int n, double len) { return (int) Math.round(n * (1 - len)); }

    /** Radius at joint k of a body of n segments (n + 1 joints). */
    public static double radius(int k, int n, double r0, Spec s) {
        if (!s.on()) return r0;
        int n0 = n0(n, s.len());
        if (k <= n0 || n <= n0) return r0;
        double u = Math.min(1.0, (double) (k - n0) / (n - n0));
        return r0 * (s.nub() + (1 - s.nub()) * Math.pow(1 - u, s.curve()));
    }

    /** All the joint radii (n + 1 values). */
    public static double[] radii(int n, double r0, Spec s) {
        double[] r = new double[n + 1];
        for (int k = 0; k <= n; k++) r[k] = radius(k, n, r0, s);
        return r;
    }

    /** Number of faces of the frustum that starts at joint k. */
    public static int faces(int k, int n, double width, double r0, Spec s) {
        return Math.max(MIN_FACES, (int) Math.round(Math.PI * width * radius(k, n, r0, s) / r0));
    }

    /**
     * The tail quads of the segments {@code from..to} (only those from n0 on are built) as a {@link ShingleMath.Result} (kind SKIN), and the cap at the last
     * joint when the last segment is among them. {@code chain} carries the joint radii of {@link #radii}. Colours: white with the body's per-face jitter.
     */
    public static ShingleMath.Result build(ChainMath chain, int n, double width, double r0, Spec s, int from, int to, long seed, double jitter) {
        ShingleMath.Result res = new ShingleMath.Result();
        if (!s.on()) return res;
        int n0 = n0(n, s.len());
        for (int k = Math.max(from, n0); k <= Math.min(to, n - 1); k++) {
            int nk = faces(k, n, width, r0, s);
            double[][] front = chain.miterRing(k, false, nk), rear = chain.miterRing(k + 1, true, nk);
            double[] axis = chain.axis(k);
            for (int i = 0; i < nk; i++) {
                int j = (i + 1) % nk;
                double am = 2 * Math.PI * (i + 0.5) / nk;
                double[] radial = ChainMath.apply(chain.frame[k], new double[]{Math.sin(am), Math.cos(am), 0});
                res.curColor = Stagger.scale(ShingleMath.WHITE, Stagger.jitterFactor(seed, k, i, Stagger.SALT_JITTER, jitter));
                res.add(ShingleMath.SKIN, new double[][]{front[i], front[j], rear[j], rear[i]}, ShingleMath.uv(0f, 0f, 0f, 1f, 1f), radial);
            }
            if (k == n - 1) cap(res, chain, n, k, nk, r0, s, axis, seed, jitter);
        }
        res.curColor = ShingleMath.WHITE;
        return res;
    }

    /** Latitudes (degrees from the last ring) of the rounded cap: three bands and the pole. */
    static final double[] CAP_LAT = {0, 38, 68};

    private static void cap(ShingleMath.Result res, ChainMath chain, int n, int k, int nk, double r0, Spec s, double[] axis, long seed, double jitter) {
        double r = radius(n, n, r0, s);
        double[] c = chain.center[n];
        double[][][] rings = new double[CAP_LAT.length][nk][];
        for (int b = 0; b < CAP_LAT.length; b++) {
            double phi = Math.toRadians(CAP_LAT[b]), rr = r * Math.cos(phi), along = r * Math.sin(phi);
            for (int i = 0; i < nk; i++) {
                double a = 2 * Math.PI * i / nk;
                double[] off = ChainMath.apply(chain.frame[k], new double[]{rr * Math.sin(a), rr * Math.cos(a), 0});
                rings[b][i] = new double[]{c[0] + off[0] + axis[0] * along, c[1] + off[1] + axis[1] * along, c[2] + off[2] + axis[2] * along};
            }
        }
        double[] pole = {c[0] + axis[0] * r, c[1] + axis[1] * r, c[2] + axis[2] * r};
        for (int b = 0; b < CAP_LAT.length; b++) {
            for (int i = 0; i < nk; i++) {
                int j = (i + 1) % nk;
                double[] a = rings[b][i], bb = rings[b][j];
                double[][] q = b + 1 < CAP_LAT.length ? new double[][]{a, bb, rings[b + 1][j], rings[b + 1][i]} : new double[][]{a, bb, pole, pole};
                double[] mid = {(q[0][0] + q[2][0]) / 2 - c[0], (q[0][1] + q[2][1]) / 2 - c[1], (q[0][2] + q[2][2]) / 2 - c[2]};
                res.curColor = Stagger.scale(ShingleMath.WHITE, Stagger.jitterFactor(seed, k, i, Stagger.SALT_JITTER, jitter));
                res.add(ShingleMath.SKIN, q, ShingleMath.uv(0f, 0f, 0f, 1f, 1f), mid);
            }
        }
    }
}
