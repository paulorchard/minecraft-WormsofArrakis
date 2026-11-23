package com.paulorchard.islandcraft.wormsofarrakis.path;

import com.paulorchard.islandcraft.wormsofarrakis.geometry.ChainMath;

import java.util.Arrays;

/**
 * Head-leading pose of a worm on a {@link PlanarPath}: joint 0 (the base of the head) is the path point at arc
 * length sHead and joint k+1 is the path point behind joint k at Euclidean distance pitch, so every joint lies on
 * the path and every segment is a straight chord of exactly one pitch. Everything is a function of sHead alone, so
 * reversing sHead plays the move backwards. Model coordinates: (0, y, z) with the path's x as model z (the head
 * travels towards +z, the body trails towards -z), y the height above the surface.
 */
public final class WormPose {
    public final int segments;
    public final double pitch;
    public final double[][] joints;     // segments + 1 entries, {0, y, z}
    public final double[] yaw, pitchAngle;   // relative joint angles for ChainMath (yaw is always 0: planar)
    public final double[] tailPhi;      // absolute elevation of each segment's head-to-tail axis

    private WormPose(int segments, double pitch) {
        this.segments = segments;
        this.pitch = pitch;
        this.joints = new double[segments + 1][];
        this.yaw = new double[segments];
        this.pitchAngle = new double[segments];
        this.tailPhi = new double[segments];
    }

    public static WormPose at(PlanarPath path, double sHead, int segments, double pitch) {
        return at(path, sHead, segments, pitch, segments);
    }

    /** As {@link #at(PlanarPath, double, int, double)} but only the first {@code limit} segments are computed (the rest repeat the last angle). */
    public static WormPose at(PlanarPath path, double sHead, int segments, double pitch, int limit) {
        WormPose w = new WormPose(segments, pitch);
        double s = sHead;
        double[] p = path.pos(s);
        w.joints[0] = new double[]{0, p[1], p[0]};
        int upto = Math.min(segments, limit);
        for (int k = 0; k < upto; k++) {
            s = behind(path, s, w.joints[k], pitch);
            double[] q = path.pos(s);
            w.joints[k + 1] = new double[]{0, q[1], q[0]};
            double ay = w.joints[k + 1][1] - w.joints[k][1], az = w.joints[k + 1][2] - w.joints[k][2];
            w.tailPhi[k] = Math.atan2(ay, -az);
        }
        for (int k = upto; k < segments; k++) {   // beyond the limit: straight on, same direction (not drawn / not counted)
            double[] a = w.joints[k];
            double ay = w.joints[k][1] - w.joints[k - 1][1], az = w.joints[k][2] - w.joints[k - 1][2];
            w.joints[k + 1] = new double[]{0, a[1] + ay, a[2] + az};
            w.tailPhi[k] = w.tailPhi[k - 1];
        }
        for (int k = 0; k < segments; k++) w.pitchAngle[k] = wrap(w.tailPhi[k] - (k == 0 ? 0 : w.tailPhi[k - 1]));
        return w;
    }

    /** Arc length s' < s with |P(s') - from| = pitch (the smallest such arc length behind s). */
    static double behind(PlanarPath path, double s, double[] from, double pitch) {
        double lo = pitch, hi = pitch * 1.6;
        while (dist(path, s - hi, from) < pitch && hi < pitch * 8) hi *= 1.6;
        for (int i = 0; i < 60; i++) {
            double mid = 0.5 * (lo + hi);
            if (dist(path, s - mid, from) < pitch) lo = mid; else hi = mid;
        }
        return s - 0.5 * (lo + hi);
    }

    /** Arc length s' > s with |P(s') - from| = distance (point ahead of the head, for the rigid head's chord). */
    public static double ahead(PlanarPath path, double s, double[] from, double distance) {
        double lo = distance, hi = distance * 1.6;
        while (dist(path, s + hi, from) < distance && hi < distance * 8) hi *= 1.6;
        for (int i = 0; i < 60; i++) {
            double mid = 0.5 * (lo + hi);
            if (dist(path, s + mid, from) < distance) lo = mid; else hi = mid;
        }
        return s + 0.5 * (lo + hi);
    }

    private static double dist(PlanarPath path, double s, double[] from) {
        double[] q = path.pos(s);
        return Math.hypot(q[1] - from[1], q[0] - from[2]);
    }

    private static double wrap(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a <= -Math.PI) a += 2 * Math.PI;
        return a;
    }

    public ChainMath chain(double[] radius) {
        return new ChainMath(segments, pitch, joints[0], yaw, pitchAngle, radius);
    }

    public ChainMath chain(double r) {
        double[] radius = new double[segments + 1];
        Arrays.fill(radius, r);
        return chain(radius);
    }

    /** Centre height of segment k. */
    public double centreY(int k) {
        return 0.5 * (joints[k][1] + joints[k + 1][1]);
    }

    /** Number of segments whose centre is above the surface (plane at height 0), counting from the head base. */
    public int exposed(double surface) {
        int n = 0;
        for (int k = 0; k < segments; k++) if (centreY(k) > surface) n++;
        return n;
    }

    /**
     * Which segments are drawn: a segment is skipped only when its whole ring (centre plus radius plus 0.2 for the plates)
     * lies at least one block below the surface at both its joints; anything uncertain is drawn.
     */
    public boolean[] drawn(double ringRadius, SurfaceProvider surface) {
        boolean[] d = new boolean[segments];
        for (int k = 0; k < segments; k++) {
            double top = Math.max(joints[k][1], joints[k + 1][1]) + ringRadius + 0.2;
            double surf = Math.max(surface.heightAt(joints[k][2]), surface.heightAt(joints[k + 1][2]));
            d[k] = top > surf - 1.0;
        }
        return d;
    }
}
