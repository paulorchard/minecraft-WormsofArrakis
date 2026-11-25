package com.paulorchard.islandcraft.wormsofarrakis.hazard;

import com.paulorchard.islandcraft.wormsofarrakis.geometry.ChainMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.HeadMouth;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.TailMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.WormMath;
import com.paulorchard.islandcraft.wormsofarrakis.path.HeadSpec;
import com.paulorchard.islandcraft.wormsofarrakis.path.MouthAnim;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;
import com.paulorchard.islandcraft.wormsofarrakis.path.SurfaceProvider;
import com.paulorchard.islandcraft.wormsofarrakis.path.WormPose;
import com.paulorchard.islandcraft.wormsofarrakis.path.WormSkin;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveFactory;
import com.paulorchard.islandcraft.wormsofarrakis.test.WormSpec;

import java.util.List;

/**
 * Prompt 16 part 2: the lethal volume of one worm, assembled from the pose the renderer draws and nothing else: the move's joint frames ({@code Moves.Move.pose} and {@code at}), the sway offsets
 * and the body chain ({@link WormSkin#bodyChain}), the per-ring radius of the skin ({@link TailMath#radius}) and the live mouth angle ({@link MoveFactory#mouthAngle}, quantised as the renderer
 * does). Three shapes, any one touching is contact: the body (a chain of capsules), the head (the three mandible flaps as thin shells) and the throat (the solid funnel behind the rim and a
 * contact skin in front of the rim disc). Engine-free; the world is only the origin and the yaw of the entity.
 * <p>Coordinates: the model frame of the renderer (x sideways, y up from the sand level of the entity origin, z along the way it travels); the world point of a model point is
 * {@code origin + (mx cos(yaw) - mz sin(yaw), my, mx sin(yaw) + mz cos(yaw))} (the same turn as Alignment.worldOf).
 */
public final class WormHazard {
    /** Which shapes are on, how thick the skins are and how finely time is sampled. */
    public record Settings(double skin, double throatReach, boolean body, boolean head, boolean throat, int substeps, double timeOffset) {
        public static final Settings DEFAULTS = new Settings(0.3, 0.4, true, true, true, 4, 0);

        public static Settings of(double skin, double throatReach, String parts, int substeps, double timeOffset) {
            String p = "," + parts.toLowerCase(java.util.Locale.ROOT).replace(" ", "") + ",";
            return new Settings(skin, throatReach, p.contains(",body,"), p.contains(",head,"), p.contains(",throat,"), Math.max(1, substeps), timeOffset);
        }

        public Settings withParts(boolean b, boolean h, boolean t) { return new Settings(skin, throatReach, b, h, t, substeps, timeOffset); }
        public Settings withTimeOffset(double o) { return new Settings(skin, throatReach, body, head, throat, substeps, o); }
        public Settings withSubsteps(int n) { return new Settings(skin, throatReach, body, head, throat, Math.max(1, n), timeOffset); }
    }

    /** A contact: the part, the script time of the substep in which it happened (offset included) and the substep (1..substeps). */
    public record Contact(String part, double t, int substep) {}

    /** The pose at one script time, in the model frame. */
    public static final class Pose {
        public double[][] centre;      // joint centres (sway included), segments + 1
        public double[] radius;        // the skin's radius at each joint
        public boolean[] drawn;        // segments the renderer draws
        public double[] headC;         // head frame origin (model)
        public double[][] headF;       // head frame rotation, local to model (columns = local axes)
        public double mouthDeg;        // the angle the renderer quantises to
        public HeadMouth.Flaps flaps;
        public double headSphere;      // every flap vertex is within this of the head origin
        public double tau;
    }

    public final WormSpec spec;
    public final Moves.Move move;
    public final double ox, oy, oz, yawDeg;
    public final Settings settings;
    public final int n;
    public final double R, coneH, delta0, thicknessShell;
    private final double cosY, sinY;
    private static final int CACHE = 16;
    private final Pose[] cache = new Pose[CACHE];
    private int cacheAt;
    private final WormHitShape body = new BodyShape(), head = new HeadShape(), throat = new ThroatShape();

    public WormHazard(WormSpec spec, double ox, double oy, double oz, double yawDeg, Settings settings) {
        this.spec = spec;
        this.move = MoveFactory.get(spec);
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.yawDeg = yawDeg;
        this.settings = settings;
        this.cosY = Math.cos(Math.toRadians(yawDeg));
        this.sinY = Math.sin(Math.toRadians(yawDeg));
        this.n = WormMath.faceCount(move.width);
        this.R = move.r0;
        this.coneH = HeadSpec.coneHeight(move.width);
        this.delta0 = HeadMouth.roll(n);   // the stagger of the head ring is a client look setting and is not known here (a shift of at most half a face along the lip)
        this.thicknessShell = HeadMouth.thickness(move.width);
    }

    public List<WormHitShape> shapes() {
        java.util.ArrayList<WormHitShape> l = new java.util.ArrayList<>();
        if (settings.body()) l.add(body);
        if (settings.head()) l.add(head);
        if (settings.throat()) l.add(throat);
        return l;
    }

    public WormHitShape body() { return body; }
    public WormHitShape head() { return head; }
    public WormHitShape throat() { return throat; }

    // ---- frames ----

    public double[] toModel(double x, double y, double z) {
        double dx = x - ox, dz = z - oz;
        return new double[]{dx * cosY + dz * sinY, y - oy, -dx * sinY + dz * cosY};
    }

    public double[] toWorld(double mx, double my, double mz) {
        return new double[]{ox + mx * cosY - mz * sinY, oy + my, oz + mx * sinY + mz * cosY};
    }

    // ---- the pose (the renderer's) ----

    /** The pose at script time {@code tau}, the same functions the renderer calls (the last 16 instants are kept, so the three shapes and every entity of a tick share the poses of its substeps). */
    public synchronized Pose poseAt(double tau) {
        for (Pose c : cache) if (c != null && c.tau == tau) return c;
        Moves.Move m = move;
        double s = m.at(tau)[0];
        WormPose wp = m.pose(s);
        double[][] off = MoveFactory.swayOffsets(spec, m, tau, wp.joints);
        ChainMath chain = WormSkin.bodyChain(wp, m.r0, off);
        TailMath.Spec ts = MoveFactory.tailSpec(spec);
        Pose p = new Pose();
        p.tau = tau;
        p.centre = chain.center;
        p.radius = new double[wp.segments + 1];
        for (int k = 0; k <= wp.segments; k++) p.radius[k] = ts.on() ? TailMath.radius(k, wp.segments, m.r0, ts) : m.r0;
        p.drawn = wp.drawn(m.r0, SurfaceProvider.PLANE);
        p.headC = chain.center[0];
        p.headF = chain.frame[0];
        p.mouthDeg = MouthAnim.angleKey(MoveFactory.mouthAngle(spec, tau)) / 10.0;
        p.flaps = HeadMouth.flaps(m.width, n, R, delta0, coneH, p.mouthDeg);
        double sph = 0;
        for (int j = 0; j < HeadMouth.FLAPS; j++) {
            for (double[] v : p.flaps.arc[j]) sph = Math.max(sph, len(v));
            sph = Math.max(sph, len(p.flaps.apex[j]));
        }
        p.headSphere = sph;
        cache[cacheAt] = p;
        cacheAt = (cacheAt + 1) % CACHE;
        return p;
    }

    private static double len(double[] v) { return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]); }

    /** A model point in the head's local frame. */
    static double[] toLocal(Pose p, double[] m) {
        double dx = m[0] - p.headC[0], dy = m[1] - p.headC[1], dz = m[2] - p.headC[2];
        double[][] F = p.headF;
        return new double[]{F[0][0] * dx + F[1][0] * dy + F[2][0] * dz, F[0][1] * dx + F[1][1] * dy + F[2][1] * dz, F[0][2] * dx + F[1][2] * dy + F[2][2] * dz};
    }

    /** A head-local point in the model frame. */
    public static double[] localToModel(Pose p, double[] l) { return fromLocal(p, l); }

    static double[] fromLocal(Pose p, double[] l) {
        double[][] F = p.headF;
        return new double[]{p.headC[0] + F[0][0] * l[0] + F[0][1] * l[1] + F[0][2] * l[2], p.headC[1] + F[1][0] * l[0] + F[1][1] * l[1] + F[1][2] * l[2], p.headC[2] + F[2][0] * l[0] + F[2][1] * l[1] + F[2][2] * l[2]};
    }

    // ---- the contact test over the substeps ----

    /**
     * The first contact of a box that stands still between the script times {@code tPrev} and {@code tNow} (the previous tick and this one): the pose is evaluated at
     * {@code settings.substeps} instants, the last being {@code tNow}, shifted by the time offset; the first substep with a touching shape wins (the parts of one substep in the order body, head, throat).
     */
    public Contact firstContact(Box box, double tPrev, double tNow) { return firstContact(box, tPrev, tNow, null); }

    /**
     * Prompt 17 part 0.5: as above, but only the parts in {@code parts} count (null: all the enabled ones) and only what is above the sand level of the move counts: a point of the box below the
     * origin height of the worm (model y = 0, the sand surface at the strike) touches nobody, so the tunnel and the buried body are harmless.
     */
    public Contact firstContact(Box box, double tPrev, double tNow, java.util.Set<String> parts) {
        Box above = box.above(oy - 1e-6);
        if (above == null) return null;
        int N = settings.substeps();
        List<WormHitShape> sh = shapes();
        for (int i = 1; i <= N; i++) {
            double t = tPrev + (tNow - tPrev) * i / N + settings.timeOffset();
            for (WormHitShape s : sh) if ((parts == null || parts.contains(s.part())) && s.touches(above, t)) return new Contact(s.part(), t, i);
        }
        return null;
    }

    /** True when any enabled shape touches the box at the script time. */
    public boolean touches(Box box, double t) {
        for (WormHitShape s : shapes()) if (s.touches(box, t)) return true;
        return false;
    }

    /** The world box {minX, minY, minZ, maxX, maxY, maxZ} of everything lethal at the script time (drawn body, flaps, throat), inflated by {@code inflate}. */
    public double[] boundsWorld(double tau, double inflate) {
        Pose p = poseAt(tau);
        double[] lo = {1e18, 1e18, 1e18}, hi = {-1e18, -1e18, -1e18};
        for (int k = 0; k < p.drawn.length; k++) {
            if (!p.drawn[k]) continue;
            for (int e = 0; e < 2; e++) {
                double[] c = p.centre[k + e];
                double r = p.radius[k + e] + settings.skin();
                double[] w = toWorld(c[0], c[1], c[2]);
                for (int i = 0; i < 3; i++) { lo[i] = Math.min(lo[i], w[i] - r); hi[i] = Math.max(hi[i], w[i] + r); }
            }
        }
        double hr = p.headSphere + Math.max(settings.skin(), thicknessShell) + settings.throatReach() + R;
        double[] hw = toWorld(p.headC[0], p.headC[1], p.headC[2]);
        for (int i = 0; i < 3; i++) { lo[i] = Math.min(lo[i], hw[i] - hr); hi[i] = Math.max(hi[i], hw[i] + hr); }
        return new double[]{lo[0] - inflate, lo[1] - inflate, lo[2] - inflate, hi[0] + inflate, hi[1] + inflate, hi[2] + inflate};
    }

    // ---- the shapes ----

    private final class BodyShape implements WormHitShape {
        @Override public String part() { return "body"; }

        @Override
        public boolean touches(Box box, double t) {
            Pose p = poseAt(t);
            double[] c = toModel(box.cx(), box.cy(), box.cz());
            double h = box.halfDiagonal(), skin = settings.skin();
            int segs = p.drawn.length;
            int[] cand = new int[segs];
            int nc = 0;
            for (int k = 0; k < segs; k++) {   // the per-segment prefilter: the sphere of the box against the capsule's own sphere
                if (!p.drawn[k]) continue;
                double[] a = p.centre[k], b = p.centre[k + 1];
                double reach = Math.max(p.radius[k], p.radius[k + 1]) + skin + h + 0.5 * dist(a, b);
                double[] mid = {0.5 * (a[0] + b[0]), 0.5 * (a[1] + b[1]), 0.5 * (a[2] + b[2])};
                if (dist(mid, c) <= reach) cand[nc++] = k;
            }
            if (nc == 0) return false;
            for (double[] s : box.samples()) {
                double[] q = toModel(s[0], s[1], s[2]);
                for (int i = 0; i < nc; i++) if (inCapsule(p, cand[i], q, skin)) return true;
            }
            return false;
        }
    }

    /** Point q (model) within the tapered capsule of segment k grown by {@code skin}. */
    static boolean inCapsule(Pose p, int k, double[] q, double skin) {
        double[] a = p.centre[k], b = p.centre[k + 1];
        double abx = b[0] - a[0], aby = b[1] - a[1], abz = b[2] - a[2];
        double l2 = abx * abx + aby * aby + abz * abz;
        double u = l2 < 1e-12 ? 0 : ((q[0] - a[0]) * abx + (q[1] - a[1]) * aby + (q[2] - a[2]) * abz) / l2;
        u = Math.max(0, Math.min(1, u));
        double dx = q[0] - (a[0] + u * abx), dy = q[1] - (a[1] + u * aby), dz = q[2] - (a[2] + u * abz);
        double r = p.radius[k] + u * (p.radius[k + 1] - p.radius[k]) + skin;
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    private static double dist(double[] a, double[] b) {
        double x = a[0] - b[0], y = a[1] - b[1], z = a[2] - b[2];
        return Math.sqrt(x * x + y * y + z * z);
    }

    private final class HeadShape implements WormHitShape {
        @Override public String part() { return "head"; }

        @Override
        public boolean touches(Box box, double t) {
            Pose p = poseAt(t);
            double skin = settings.skin(), th = thicknessShell;
            double reach = p.headSphere + Math.max(skin, th);
            for (double[] s : box.samples()) {
                double[] q = toLocal(p, toModel(s[0], s[1], s[2]));
                if (len(q) > reach) continue;
                if (inShell(p.flaps, q, skin, th)) return true;
            }
            return false;
        }
    }

    /** Point (head frame) within {@code skin} outside, or {@code thickness} inside, of any outer skin triangle of the three flaps (the shell is hollow). */
    static boolean inShell(HeadMouth.Flaps f, double[] q, double skin, double thickness) {
        for (int j = 0; j < HeadMouth.FLAPS; j++) {
            double[][] arc = f.arc[j];
            double[] apex = f.apex[j];
            for (int i = 0; i < arc.length - 1; i++) {
                double[] c = closest(q, arc[i], arc[i + 1], apex);
                double dx = q[0] - c[0], dy = q[1] - c[1], dz = q[2] - c[2];
                double d2 = dx * dx + dy * dy + dz * dz;
                double[] nr = f.normal[j][i];
                boolean outer = dx * nr[0] + dy * nr[1] + dz * nr[2] >= 0;
                double lim = outer ? skin : thickness;
                if (d2 <= lim * lim) return true;
            }
        }
        return false;
    }

    /** The closest point of triangle (a, b, c) to p (Ericson, Real-Time Collision Detection). */
    static double[] closest(double[] p, double[] a, double[] b, double[] c) {
        double[] ab = sub(b, a), ac = sub(c, a), ap = sub(p, a);
        double d1 = dot(ab, ap), d2 = dot(ac, ap);
        if (d1 <= 0 && d2 <= 0) return a;
        double[] bp = sub(p, b);
        double d3 = dot(ab, bp), d4 = dot(ac, bp);
        if (d3 >= 0 && d4 <= d3) return b;
        double vc = d1 * d4 - d3 * d2;
        if (vc <= 0 && d1 >= 0 && d3 <= 0) { double v = d1 / (d1 - d3); return add(a, mul(ab, v)); }
        double[] cp = sub(p, c);
        double d5 = dot(ab, cp), d6 = dot(ac, cp);
        if (d6 >= 0 && d5 <= d6) return c;
        double vb = d5 * d2 - d1 * d6;
        if (vb <= 0 && d2 >= 0 && d6 <= 0) { double w = d2 / (d2 - d6); return add(a, mul(ac, w)); }
        double va = d3 * d6 - d5 * d4;
        if (va <= 0 && (d4 - d3) >= 0 && (d5 - d6) >= 0) { double w = (d4 - d3) / ((d4 - d3) + (d5 - d6)); return add(b, mul(sub(c, b), w)); }
        double denom = 1.0 / (va + vb + vc);
        double v = vb * denom, w = vc * denom;
        return add(a, add(mul(ab, v), mul(ac, w)));
    }

    private static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    private static double[] add(double[] a, double[] b) { return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
    private static double[] mul(double[] a, double f) { return new double[]{a[0] * f, a[1] * f, a[2] * f}; }
    private static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    private final class ThroatShape implements WormHitShape {
        @Override public String part() { return "throat"; }

        @Override
        public boolean touches(Box box, double t) {
            Pose p = poseAt(t);
            for (double[] s : box.samples()) {
                double[] q = toLocal(p, toModel(s[0], s[1], s[2]));
                if (inThroat(q, R, settings.throatReach())) return true;
            }
            return false;
        }
    }

    /** The throat in the head frame: the solid funnel -0.5 R &lt;= z &lt;= 0 with radius R (1 + z / (0.5 R)), plus the contact skin 0 &lt; z &lt;= reach over the rim disc (radius R). */
    public static boolean inThroat(double[] q, double R, double reach) {
        double rr = Math.hypot(q[0], q[1]);
        double z = q[2];
        double depth = -HeadMouth.THROAT_APEX_R * R;   // 0.5 R
        if (z >= -depth && z <= 0) return rr <= R * (1 + z / depth);
        if (z > 0 && z <= reach) return rr <= R;
        return false;
    }
}
