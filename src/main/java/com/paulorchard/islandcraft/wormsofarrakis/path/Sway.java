package com.paulorchard.islandcraft.wormsofarrakis.path;

/**
 * Prompt 11 part 1: the sway of the body, pure code. Every ring (and the head with ring 0) is moved a little off the path by two waves that travel down the body
 * from the head: a left-right one (along the model x axis, the horizontal axis perpendicular to the vertical plane of the path) and a front-back one (along the
 * normal of the path tangent inside that plane, pointing up when the worm travels flat). A visual offset only: the path, the joints, the timing and the effect
 * positions are untouched.
 * <pre>
 * env(t)  = idle + (1 - idle) exp(-max(0, t - tBreach) / fade)          (1 before the breach)
 * LR(k,t) = ampLR r0 env sin(2 pi (hzLR t - k / waveLR))
 * FB(k,t) = ampFB r0 env sin(2 pi (hzFB t - k / waveFB) + 1.3)
 * </pre>
 * No randomness. There is no ripple of the ring radius (the owner tried it and did not like it).
 */
public final class Sway {
    private Sway() {}

    /** The nine keys: sway, swayamp, swayamp2, swaywave, swaywave2, swayhz, swayhz2, swayfade, swayidle. */
    public record Params(boolean on, double ampLR, double ampFB, double waveLR, double waveFB, double hzLR, double hzFB, double fade, double idle) {
        public static final Params DEFAULT = new Params(true, 0.04, 0.04, 14, 21, 0.6, 0.45, 3.0, 0.25);
        public static final Params OFF = new Params(false, 0.04, 0.04, 14, 21, 0.6, 0.45, 3.0, 0.25);
        public boolean isDefault() { return this.equals(DEFAULT); }
        /** The largest offset of any ring in blocks: (ampLR + ampFB) x r0 (two perpendicular axes: the vector is at most the root of the squares, this bound is the sum). */
        public double maxOffset(double r0) { return on ? (ampLR + ampFB) * r0 : 0; }
    }

    public static final double MIN_AMP = 0, MAX_AMP = 0.5, MIN_WAVE = 3, MAX_WAVE = 40, MIN_HZ = 0.1, MAX_HZ = 3, MIN_FADE = 0.5, MAX_FADE = 12, MIN_IDLE = 0, MAX_IDLE = 1;
    /** The phase of the front-back wave (radians). */
    public static final double FB_PHASE = 1.3;

    /** The envelope: 1 up to the breach, then idle + (1 - idle) e^(-(t - tBreach) / fade). NaN breach: 1. */
    public static double env(double t, double tBreach, Params p) {
        if (Double.isNaN(tBreach)) return 1;
        return p.idle() + (1 - p.idle()) * Math.exp(-Math.max(0, t - tBreach) / p.fade());
    }

    /** Left-right offset (blocks) of ring k; {@code t} is the phase time, {@code env} the envelope. */
    public static double lr(int k, double t, double r0, double env, Params p) {
        return p.ampLR() * r0 * env * Math.sin(2 * Math.PI * (p.hzLR() * t - k / p.waveLR()));
    }

    /** Front-back offset (blocks) of ring k. */
    public static double fb(int k, double t, double r0, double env, Params p) {
        return p.ampFB() * r0 * env * Math.sin(2 * Math.PI * (p.hzFB() * t - k / p.waveFB()) + FB_PHASE);
    }

    /**
     * The offsets of every joint (index k = 0..segments) in the model frame {x, y, z}: x = LR, (y, z) = FB x the unit normal of the tangent at the joint (the tangent from
     * the neighbouring joints; the normal is (fz, -fy) of the forward direction, which is +y when the worm travels flat towards +z). Null when the sway is off.
     * @param tPhase the move-local time of the phase (continuous, never reset by a loop); @param tEnv the loop time the envelope is measured in
     */
    public static double[][] offsets(double[][] joints, double r0, double tPhase, double tEnv, double tBreach, Params p) {
        if (!p.on()) return null;
        int n = joints.length - 1;
        double env = env(tEnv, tBreach, p);
        double[][] o = new double[n + 1][3];
        for (int k = 0; k <= n; k++) {
            double[] a = joints[Math.max(0, k - 1)], b = joints[Math.min(n, k + 1)];
            double dy = a[1] - b[1], dz = a[2] - b[2], len = Math.hypot(dy, dz);   // forward = from the next joint towards the previous one
            double fy = len < 1e-12 ? 0 : dy / len, fz = len < 1e-12 ? 1 : dz / len;
            double f = fb(k, tPhase, r0, env, p);
            o[k][0] = lr(k, tPhase, r0, env, p);
            o[k][1] = f * fz;
            o[k][2] = -f * fy;
        }
        return o;
    }
}
