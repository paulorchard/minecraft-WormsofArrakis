package com.paulorchard.islandcraft.wormsofarrakis.path;

import com.paulorchard.islandcraft.wormsofarrakis.geometry.BendLimit;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ScaleMath;

/**
 * The three scripted moves (crest, emerge, crash) as fixed planar paths plus a speed profile, pure code.
 * Surface at height 0. Every move starts with the head base at s = 0 and the body lying in a straight tunnel behind it.
 * Visible curvature rule: arcs above (or near) the surface have radius >= BendLimit.minRadius(width) x factor; the
 * stretch around every surface crossing is straight for at least one body width on each side; underground arcs use
 * under_radius_widths (free). A move that cannot satisfy this is refused unless force.
 */
public final class Moves {
    public static final double TUNNEL_LEAD = 10.0;
    public static final double EMERGE_PAUSE = 1.0, CRASH_PAUSE = 2.0;
    public static final double MIN_SPEED = 0.5;

    private Moves() {}

    public static final class Rejected extends RuntimeException {
        public Rejected(String message) { super(message); }
    }

    public static final class Move {
        public String kind;
        public PlanarPath path;
        public double width, r0, speed, hold, nominalSpeed;
        public int segments;
        public int n;                    // exposed segments (emerge, crash)
        public double s0, sHold, sEnd, sCross1 = Double.NaN, sCross2 = Double.NaN, sTurn = Double.NaN;
        public double apexY, tunnelDepth, topRadius, underRadius;
        public double[] tTab, sTab;      // crash: ballistic time table
        public double advanceTime, cycle;

        /** {s, phase} at script time tau seconds (loops). Phase: 0 advance/run, 1 hold, 2 retreat, 3 pause. */
        public double[] at(double tau) {
            double t = tau % cycle;
            if (t < 0) t += cycle;
            switch (kind) {
                case "crest":
                    return new double[]{s0 + speed * t, 0};
                case "emerge":
                    if (t < advanceTime) return new double[]{s0 + speed * t, 0};
                    if (t < advanceTime + hold) return new double[]{sHold, 1};
                    if (t < 2 * advanceTime + hold) return new double[]{sHold - speed * (t - advanceTime - hold), 2};
                    return new double[]{s0, 3};
                default:
                    if (t >= advanceTime) return new double[]{sEnd, 3};
                    if (tTab == null) return new double[]{s0 + speed * t, 0};
                    int lo = 0, hi = tTab.length - 1;
                    while (lo < hi - 1) {
                        int mid = (lo + hi) >>> 1;
                        if (tTab[mid] <= t) lo = mid; else hi = mid;
                    }
                    double f = (t - tTab[lo]) / Math.max(1e-12, tTab[hi] - tTab[lo]);
                    return new double[]{sTab[lo] + f * (sTab[hi] - sTab[lo]), 0};
            }
        }

        public WormPose pose(double s) { return WormPose.at(path, s, segments, 1.0); }

        /** {base height, top of the head height}; the head is a rigid straight piece along the chord to the point BODY_LENGTH ahead. */
        public double[] headHeights(double s) {
            double[] b = path.pos(s);
            double sf = WormPose.ahead(path, s, new double[]{0, b[1], b[0]}, HeadSpec.BODY_LENGTH);
            double[] f = path.pos(sf);
            double dy = (f[1] - b[1]) / HeadSpec.BODY_LENGTH;
            return new double[]{b[1], b[1] + dy * HeadSpec.headLength(width)};
        }

        /** Maximum exposed count while the head advances (scanned every 0.25 blocks of arc). */
        public int maxExposed() {
            int max = 0;
            for (double s = s0; s <= sEnd; s += 0.25) max = Math.max(max, pose(s).exposed(0));
            return max;
        }

        /** Height of the highest point of the path. */
        public double maxPathHeight() {
            double m = -1e9;
            for (double s = s0; s <= sEnd; s += 0.1) m = Math.max(m, path.pos(s)[1]);
            return m;
        }

        public double span() { return Double.isNaN(sCross2) ? Double.NaN : path.pos(sCross2)[0] - path.pos(sCross1)[0]; }
    }

    private static double rise() { return Math.sin(Math.toRadians(ScaleMath.TILT_DEG)) * ScaleMath.SKIN_LENGTH; }

    /** Crest: the body rises as a hump whose top surface is crestFraction x width above the sand at the peak. */
    public static Move crest(double width, int segments, double crestFraction, double speed, double radiusFactor, boolean force) {
        if (crestFraction > 0.5 + 1e-9 && !force) throw new Rejected("crest_fraction must be at most 0.5 (less than half the body thickness above the sand)");
        if (crestFraction <= 0) throw new Rejected("crest_fraction must be above 0");
        double r0 = HeadSpec.ringRadius(width);
        double limitR = BendLimit.minRadius(width), R = limitR * radiusFactor;
        if (R + 1e-9 < limitR && !force) throw new Rejected(String.format("arch radius %.1f is below the limit %.1f (%.1f body widths); use force", R, limitR, BendLimit.MIN_BEND_RADIUS_WIDTHS));
        double depth = -(r0 + 1.0);
        double peakAxis = crestFraction * width - r0 - rise();
        double h = peakAxis - depth;
        double c = 1.0 - h / (2 * R);
        if (c < -1 || c > 1) throw new Rejected("arch radius too small for this crest height; increase the radius or lower crest_fraction");
        double beta = Math.toDegrees(Math.acos(c));
        PlanarPath p = PlanarPath.start(0, depth, 0).line(TUNNEL_LEAD).arc(R, beta).arc(R, -2 * beta).arc(R, beta).line(10).build();
        Move m = new Move();
        m.kind = "crest";
        m.path = p;
        m.width = width;
        m.r0 = r0;
        m.segments = segments;
        m.nominalSpeed = m.speed = Math.max(speed, MIN_SPEED);
        m.s0 = 0;
        m.tunnelDepth = depth;
        m.apexY = peakAxis;
        m.topRadius = R;
        m.sEnd = TUNNEL_LEAD + 4 * R * Math.toRadians(beta) + segments + HeadSpec.headLength(width) + 2 * r0 + 4;
        m.advanceTime = (m.sEnd - m.s0) / m.speed;
        m.cycle = m.advanceTime;
        return m;
    }

    private static double ascentDepth(double width, double r0, double angleDeg, double underRadius) {
        double a = Math.toRadians(angleDeg);
        return Math.min(-(width * Math.sin(a) + underRadius * (1 - Math.cos(a))), -(r0 + 1.0));
    }

    /** First s in [lo, hi] where the exposed count reaches target (monotone on a straight ascent). */
    private static double firstReach(PlanarPath path, double lo, double hi, int target, int segments) {
        for (int i = 0; i < 50; i++) {
            double mid = 0.5 * (lo + hi);
            if (WormPose.at(path, mid, segments, 1.0, target + 1).exposed(0) >= target) hi = mid; else lo = mid;
        }
        return hi;
    }

    private static void common(Move m, String kind, double width, int segments, int n, double speed) {
        m.kind = kind;
        m.width = width;
        m.r0 = HeadSpec.ringRadius(width);
        m.segments = segments;
        m.n = n;
        m.nominalSpeed = m.speed = Math.max(speed, MIN_SPEED);
        m.s0 = 0;
    }

    /** Emerge: curve up to angleDeg, cross straight, stop when exactly n segments are above the surface, hold, retreat. */
    public static Move emerge(double width, int segments, int n, double angleDeg, double speed, double hold, double underRadiusWidths, boolean force) {
        if (n < 1) throw new Rejected("n must be at least 1");
        if (segments < n + 1 && !force) throw new Rejected("the worm needs at least n + 1 = " + (n + 1) + " segments");
        if (angleDeg < 5 || angleDeg > 90) throw new Rejected("angle must be between 5 and 90 degrees");
        Move m = new Move();
        common(m, "emerge", width, segments, n, speed);
        double ur = underRadiusWidths * width;
        m.underRadius = ur;
        double a = Math.toRadians(angleDeg);
        double depth = ascentDepth(width, m.r0, angleDeg, ur);
        double yEnd = depth + ur * (1 - Math.cos(a));
        double straightBelow = -yEnd / Math.sin(a);
        double upLen = straightBelow + n + HeadSpec.headLength(width) + 2 * width + 10;
        PlanarPath p = PlanarPath.start(0, depth, 0).line(TUNNEL_LEAD).arc(ur, angleDeg).line(upLen).build();
        m.path = p;
        m.tunnelDepth = depth;
        double sArcEnd = TUNNEL_LEAD + ur * a;
        m.sCross1 = sArcEnd + straightBelow;
        int seg = Math.max(segments, n + 2);
        double sa = firstReach(p, m.sCross1, m.sCross1 + upLen, n, seg);
        double sb = firstReach(p, sa, m.sCross1 + upLen + 4 * n, n + 1, seg);
        m.sHold = 0.5 * (sa + sb);
        m.sEnd = m.sHold;
        m.hold = hold;
        m.advanceTime = (m.sHold - m.s0) / m.speed;
        m.cycle = 2 * m.advanceTime + hold + EMERGE_PAUSE;
        return m;
    }

    /**
     * Crash: the ascent of emerge, then (where n segments are exposed) an arc over the top of radius
     * topRadiusFactor x limit, the mirror descent, and an underground curve. Ballistic speed profile unless off.
     */
    public static Move crash(double width, int segments, int n, double angleDeg, double speed, double gravity, double topSpeed,
                             double topRadiusFactor, double underRadiusWidths, boolean ballistic, boolean force) {
        if (n < 1) throw new Rejected("n must be at least 1");
        if (angleDeg < 5 || angleDeg > 85) throw new Rejected("angle must be between 5 and 85 degrees");
        double limitR = BendLimit.minRadius(width), rt = limitR * topRadiusFactor;
        if (rt + 1e-9 < limitR && !force) throw new Rejected(String.format("the top arc radius %.1f is below the limit %.1f (%.1f body widths); use force", rt, limitR, BendLimit.MIN_BEND_RADIUS_WIDTHS));
        if (segments < n + 1 && !force) throw new Rejected("the worm needs at least n + 1 = " + (n + 1) + " segments");
        Move m = new Move();
        common(m, "crash", width, segments, n, speed);
        double ur = underRadiusWidths * width;
        m.underRadius = ur;
        m.topRadius = rt;
        double a = Math.toRadians(angleDeg);
        double depth = ascentDepth(width, m.r0, angleDeg, ur);
        double yEnd = depth + ur * (1 - Math.cos(a));
        double straightBelow = -yEnd / Math.sin(a);
        double upLen = straightBelow + n + HeadSpec.headLength(width) + 2 * width + 10;
        PlanarPath probe = PlanarPath.start(0, depth, 0).line(TUNNEL_LEAD).arc(ur, angleDeg).line(upLen + 4 * n).build();
        double sArcEnd = TUNNEL_LEAD + ur * a;
        double sCross1 = sArcEnd + straightBelow;
        int seg = Math.max(segments, n + 2);
        double sa = firstReach(probe, sCross1, sCross1 + upLen, n, seg);
        double sb = firstReach(probe, sa, sCross1 + upLen + 4 * n, n + 1, seg);
        double sTurn = 0.5 * (sa + sb);
        double ascentStraight = sTurn - sArcEnd;
        double yTurn = yEnd + ascentStraight * Math.sin(a);
        double yDown = Math.min(-width * Math.sin(a), -(m.r0 + 1.0) + ur * (1 - Math.cos(a)));
        double descentLen = (yTurn - yDown) / Math.sin(a);
        PlanarPath p = PlanarPath.start(0, depth, 0).line(TUNNEL_LEAD).arc(ur, angleDeg).line(ascentStraight).arc(rt, -2 * angleDeg)
                .line(descentLen).arc(ur, angleDeg).line(20).build();
        m.path = p;
        m.tunnelDepth = depth;
        m.sTurn = sTurn;
        m.sCross1 = sCross1;
        double sTurnEnd = sTurn + rt * 2 * a;
        m.sCross2 = sTurnEnd + yTurn / Math.sin(a);
        double exposedLen = m.sCross2 - m.sCross1;
        if (segments < exposedLen && !force) {
            throw new Rejected(String.format("the worm is too short for this arc: it must be at least %d segments long (exposed path %.1f blocks); use force to build it anyway", (int) Math.ceil(exposedLen), exposedLen));
        }
        m.sEnd = m.sCross2 + segments + HeadSpec.headLength(width) + 2 * m.r0 + (m.r0 + 1.2) / Math.sin(a) + 4;
        m.apexY = p.pos(sTurn + rt * a)[1];
        m.hold = 0;
        if (!ballistic) {
            m.advanceTime = (m.sEnd - m.s0) / m.speed;
        } else {
            double v0sq = topSpeed * topSpeed + 2 * gravity * m.apexY;
            double vmax = m.speed;
            int cnt = (int) Math.ceil((m.sEnd - m.s0) / 0.25);
            m.tTab = new double[cnt + 2];
            m.sTab = new double[cnt + 2];
            double t = 0;
            for (int i = 0; i <= cnt; i++) {
                double s = m.s0 + 0.25 * i;
                m.tTab[i] = t;
                m.sTab[i] = s;
                double y = p.pos(s + 0.125)[1];
                double v = Math.sqrt(Math.max(0, v0sq - 2 * gravity * y));
                v = Math.min(Math.max(v, MIN_SPEED), Math.max(vmax, topSpeed));
                t += 0.25 / v;
            }
            m.tTab[cnt + 1] = t + 1e-6;
            m.sTab[cnt + 1] = m.s0 + 0.25 * (cnt + 1);
            m.sEnd = m.sTab[cnt];
            m.advanceTime = m.tTab[cnt];
        }
        m.cycle = m.advanceTime + CRASH_PAUSE;
        return m;
    }
}
