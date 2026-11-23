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
        public double apexY, tunnelDepth, topRadius, radiusDown, underRadius;
        public double entryAngle = Double.NaN, exitAngle = Double.NaN, exposedLength = Double.NaN;
        public String note = "";
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

        /** Head tip {model z, height}: the closed flap apex, on the axis of the head ring (segment 0) in front of its front edge. */
        public double[] tip(double s) {
            WormPose p = WormPose.at(path, s, 2, 1.0, 2);
            double cone = HeadSpec.CONE_HEIGHT * r0;
            return new double[]{p.joints[0][2] + (p.joints[0][2] - p.joints[1][2]) * cone, p.joints[0][1] + (p.joints[0][1] - p.joints[1][1]) * cone};
        }

        /** {base height, top of the head height}: the rear of the head ring (joint 1) and the closed flap tip. */
        public double[] headHeights(double s) {
            WormPose p = WormPose.at(path, s, 2, 1.0, 2);
            return new double[]{p.joints[1][1], tip(s)[1]};
        }

        /** Maximum exposed count while the head advances (scanned every 0.25 blocks of arc). */
        public int maxExposed() {
            int max = 0;
            for (double s = s0; s <= sEnd; s += 0.25) max = Math.max(max, pose(s).exposedBody(0));
            return max;
        }

        /** Height of the highest point of the path. */
        public double maxPathHeight() {
            double m = -1e9;
            for (double s = s0; s <= sEnd; s += 0.1) m = Math.max(m, path.pos(s)[1]);
            return m;
        }

        public double span() { return Double.isNaN(sCross2) ? Double.NaN : path.pos(sCross2)[0] - path.pos(sCross1)[0]; }

        /** One measured moment of the move (heights and travel in body widths relative to the base level). */
        public record Frame(double t, double tipHeightW, double headingDeg, double dxW, int exposedRings) {
            public String line() {
                return String.format(java.util.Locale.ROOT, "frame t=%.2f: headTipHeight=%.2fW headHeading=%.0fdeg headDx=%.2fW exposedRings=%d", t, tipHeightW, headingDeg, dxW, exposedRings);
            }
        }

        private double[] window;

        private double tipHeightAt(double tau) { return tip(at(tau)[0])[1]; }

        /** Script times {a, b} of the normalised window: a = the head tip rises above the base level, b = it is back below it for the last time. */
        public synchronized double[] window() {
            if (window != null) return window;
            double a = Double.NaN, b = Double.NaN, prev = tipHeightAt(0);
            for (double tau = 0.01; tau < cycle; tau += 0.01) {
                double y = tipHeightAt(tau);
                if (prev <= 0 && y > 0 && Double.isNaN(a)) a = tau;
                if (prev > 0 && y <= 0) b = tau;
                prev = y;
            }
            window = new double[]{Double.isNaN(a) ? 0 : a, Double.isNaN(b) ? cycle : b};
            return window;
        }

        /** Script time of the normalised time t in [0, 1]. */
        public double tauAt(double t) {
            double[] w = window();
            return w[0] + Math.max(0, Math.min(1, t)) * (w[1] - w[0]);
        }

        public Frame frame(double t) {
            double s = at(tauAt(t))[0];
            double[] tp = tip(s);
            double z0 = tip(at(tauAt(0))[0])[0];
            WormPose p = pose(s);
            double[] from = {0, p.joints[0][1], p.joints[0][2]};
            double sf = WormPose.ahead(path, s, from, HeadSpec.CONE_HEIGHT * r0);
            return new Frame(t, tp[1] / width, Math.toDegrees(path.theta(sf)), (tp[0] - z0) / width, p.exposedBody(0));
        }
    }

    private static double rise() { return Math.sin(Math.toRadians(ScaleMath.TILT_DEG)) * ScaleMath.SKIN_LENGTH; }

    public static final double MAX_CREST_HEIGHT = 3.0;

    /**
     * Crest: the body rises as a hump whose top surface is heightWidths body widths above the sand at the apex (local y = 0 is
     * the base level). The rise to the apex has radius radiusUpWidths x width, the descent radiusDownWidths x width; both are
     * clamped up to the bend limit unless force. The path is a planar arch under the surface: line, rise (two arcs of the rise
     * radius), descent (two arcs of the descent radius), line.
     */
    public static Move crest(double width, int segments, double heightWidths, double radiusUpWidths, double radiusDownWidths, double speed, boolean force) {
        if (heightWidths <= 0) throw new Rejected("crest height must be above 0");
        if (heightWidths > MAX_CREST_HEIGHT + 1e-9 && !force) throw new Rejected("crest height must be at most " + MAX_CREST_HEIGHT + " body widths (above that it is an emerge or a crash); use force");
        String note = "";
        double limitW = BendLimit.MIN_BEND_RADIUS_WIDTHS;
        if (!force && (radiusUpWidths < limitW || radiusDownWidths < limitW)) {
            note = String.format("radius clamped up to the limit of %.1f body widths", limitW);
            radiusUpWidths = Math.max(radiusUpWidths, limitW);
            radiusDownWidths = Math.max(radiusDownWidths, limitW);
        }
        double r0 = HeadSpec.ringRadius(width);
        double Ru = radiusUpWidths * width, Rd = radiusDownWidths * width;
        double depth = -(r0 + 1.5);
        double peakAxis = heightWidths * width - r0 - rise();
        double h = peakAxis - depth;
        if (h > 2 * Math.min(Ru, Rd) + 1e-9) throw new Rejected("the arch radius is too small for this crest height; increase radius or lower height");
        double b1 = Math.toDegrees(Math.acos(1.0 - h / (2 * Ru))), b2 = Math.toDegrees(Math.acos(1.0 - h / (2 * Rd)));
        PlanarPath p = PlanarPath.start(0, depth, 0).line(TUNNEL_LEAD).arc(Ru, b1).arc(Ru, -b1).arc(Rd, -b2).arc(Rd, b2).line(10).build();
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
        m.topRadius = Ru;
        m.radiusDown = Rd;
        m.note = note;
        m.sEnd = TUNNEL_LEAD + 2 * Ru * Math.toRadians(b1) + 2 * Rd * Math.toRadians(b2) + segments + HeadSpec.headLength(width) + 2 * r0 + 4;
        m.advanceTime = (m.sEnd - m.s0) / m.speed;
        m.cycle = m.advanceTime;
        // where the top of the body crosses the base level on the way up and down
        double top = r0 + rise(), prev = p.pos(0)[1] + top;
        double sIn = Double.NaN, sOut = Double.NaN;
        for (double s = 0.05; s < p.length(); s += 0.05) {
            double y = p.pos(s)[1] + top;
            if (prev <= 0 && y > 0 && Double.isNaN(sIn)) sIn = s;
            if (prev > 0 && y <= 0) sOut = s;
            prev = y;
        }
        if (!Double.isNaN(sIn) && !Double.isNaN(sOut)) {
            m.sCross1 = sIn;
            m.sCross2 = sOut;
            m.entryAngle = Math.toDegrees(p.theta(sIn));
            m.exitAngle = -Math.toDegrees(p.theta(sOut));
            m.exposedLength = sOut - sIn;
        }
        return m;
    }

    private static double ascentDepth(double width, double r0, double angleDeg, double underRadius) {
        double a = Math.toRadians(angleDeg);
        // the straight stretch below the crossing is at least width + r0 long (one body width, plus the radius so the ring is clear of the sand)
        return Math.min(-((width + r0) * Math.sin(a) + underRadius * (1 - Math.cos(a))), -(r0 + 2.0));
    }

    /** First s in [lo, hi] where the exposed count reaches target (monotone on a straight ascent). */
    private static double firstReach(PlanarPath path, double lo, double hi, int target, int segments) {
        for (int i = 0; i < 50; i++) {
            double mid = 0.5 * (lo + hi);
            if (WormPose.at(path, mid, segments, 1.0, target + 2).exposedBody(0) >= target) hi = mid; else lo = mid;
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
        if (segments < n + 2 && !force) throw new Rejected("the worm needs at least n + 2 = " + (n + 2) + " segments (the head ring and n body rings)");
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
        int seg = Math.max(segments, n + 3);
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
        if (segments < n + 2 && !force) throw new Rejected("the worm needs at least n + 2 = " + (n + 2) + " segments (the head ring and n body rings)");
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
        int seg = Math.max(segments, n + 3);
        double sa = firstReach(probe, sCross1, sCross1 + upLen, n, seg);
        double sb = firstReach(probe, sa, sCross1 + upLen + 4 * n, n + 1, seg);
        double sTurn = 0.5 * (sa + sb);
        double ascentStraight = sTurn - sArcEnd;
        double yTurn = yEnd + ascentStraight * Math.sin(a);
        double yDown = Math.min(-(width + m.r0) * Math.sin(a), -(m.r0 + 2.0) + ur * (1 - Math.cos(a)));
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
