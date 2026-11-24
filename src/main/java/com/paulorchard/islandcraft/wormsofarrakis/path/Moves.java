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
    public static final double EMERGE_PAUSE = 1.0, CRASH_PAUSE = 2.0, CREST_PAUSE = 0.0, ENGULF_PAUSE = 1.5;
    public static final double DEFAULT_EXPOSED = 2, ENGULF_MAX_EXPOSED = 3, ENGULF_FORCE_MAX_EXPOSED = 6, ENGULF_HOLD = 0.5, DEFAULT_ENGULF_LEAN = 7;
    public static final double MAX_SPEED = 64;
    public static final double DEFAULT_CREST_HEIGHT = 0.4, DEFAULT_EMERGE_N = 10, DEFAULT_EMERGE_ANGLE = 90, DEFAULT_EMERGE_HOLD = 2;

    /** Exposed rings (real number) for a head tip height in body widths: (n + HEAD_LENGTH) x sin(angle) = height x width. */
    public static double emergeRingsForHeight(double width, double heightWidths, double angleDeg) {
        return heightWidths * width / Math.sin(Math.toRadians(angleDeg)) - HeadSpec.headLength(width);
    }

    /** Head tip height in body widths for n exposed rings at the hold: (n + HEAD_LENGTH) x sin(angle) / width. */
    public static double emergeHeightForRings(double width, int n, double angleDeg) {
        return (n + HeadSpec.headLength(width)) * Math.sin(Math.toRadians(angleDeg)) / width;
    }
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
        // projectile crash (mode proj)
        public double gravity, v0, angleDeg, angleRequested, flightTime, range, paraLength;
        public int segmentsNeeded, exposedMax;
        // crash as rise, hold and fall into the hole (mode fall)
        public FallFamily fam;
        public double holeX, h0, fallTime, dropEnd, leanDeg, fallScale, nFormula, riseTime;
        public int segmentsNeededFall;
        public double[] tTab, sTab;      // crash: ballistic time table
        public double advanceTime, cycle;
        // prompt 07: rest between loops, the way back of emerge, the crest apex, the straight drop of engulf
        public double pause, retreatSpeed, retreatTime, sApex = Double.NaN;
        public double dropAngleSin = 1.0;

        /** Name of the command this move comes from (the mode of the spec is internal: fall = crash, proj = crashproj, crash = crasharc). */
        public String label() {
            return switch (kind) {
                case "fall" -> "crash";
                case "proj" -> "crashproj";
                case "crash" -> "crasharc";
                default -> kind;
            };
        }

        /** Sets the rest between loops; the loop period changes by exactly the difference. */
        public void setPause(double seconds) {
            cycle += seconds - pause;
            pause = seconds;
        }

        /** Seconds from the start of the loop until everything is under the sand again (the loop without its rest). */
        public double playTime() { return cycle - pause; }

        /** One named phase of the loop and its length in seconds. */
        public record Phase(String name, double duration) {}

        /** The phase at an instant: its name, the time inside it and its length. */
        public record PhaseAt(String name, double t, double duration) {}

        /** The phases of one loop in order (a phase of length 0 is listed but never reported by {@link #phaseAt}). */
        public java.util.List<Phase> phases() {
            switch (kind) {
                case "crest": {
                    double rise = Math.max(0, Math.min(advanceTime, (sApex - s0) / speed));
                    return java.util.List.of(new Phase("rise", rise), new Phase("fall", advanceTime - rise), new Phase("pause", pause));
                }
                case "emerge":
                    return java.util.List.of(new Phase("rise", advanceTime), new Phase("hold", hold), new Phase("retreat", retreatTime), new Phase("pause", pause));
                case "fall":
                    return java.util.List.of(new Phase("rise", advanceTime), new Phase("hold", hold), new Phase("fall", fallTime), new Phase("pause", pause));
                case "engulf":
                    return java.util.List.of(new Phase("rise", advanceTime), new Phase("hold", hold), new Phase("drop", fallTime), new Phase("pause", pause));
                default:
                    return java.util.List.of(new Phase("run", advanceTime), new Phase("pause", pause));
            }
        }

        /** Phase name, time inside it and its length at script time tau (loops). */
        public PhaseAt phaseAt(double tau) {
            double t = tau % cycle;
            if (t < 0) t += cycle;
            PhaseAt last = null;
            double start = 0;
            for (Phase p : phases()) {
                if (p.duration() <= 0) continue;
                last = new PhaseAt(p.name(), t - start, p.duration());
                if (t < start + p.duration()) return last;
                start += p.duration();
            }
            return last == null ? new PhaseAt("pause", 0, 0) : last;
        }

        /** {s, phase} at script time tau seconds (loops). Phase: 0 advance/run, 1 hold, 2 retreat, 3 pause. */
        public double[] at(double tau) {
            double t = tau % cycle;
            if (t < 0) t += cycle;
            switch (kind) {
                case "crest":
                    if (t >= advanceTime) return new double[]{sEnd, 3};
                    return new double[]{s0 + speed * t, 0};
                case "engulf": {
                    if (t < advanceTime) return new double[]{s0 + speed * t, 0};
                    if (t < advanceTime + hold) return new double[]{sHold, 1};
                    double tf = t - advanceTime - hold;
                    if (tf < fallTime) return new double[]{sHold - 0.5 * gravity * tf * tf / dropAngleSin, 2};
                    return new double[]{sHold - dropEnd / dropAngleSin, 3};
                }
                case "fall": {
                    if (t < advanceTime) return new double[]{s0 + speed * t, 0};
                    if (t < advanceTime + hold) return new double[]{sHold, 1};
                    double tf = t - advanceTime - hold;
                    if (tf < fallTime) return new double[]{sHold + 0.5 * gravity * tf * tf, 2};
                    return new double[]{sHold + dropEnd, 3};
                }
                case "emerge":
                    if (t < advanceTime) return new double[]{s0 + speed * t, 0};
                    if (t < advanceTime + hold) return new double[]{sHold, 1};
                    if (t < advanceTime + hold + retreatTime) return new double[]{sHold - retreatSpeed * (t - advanceTime - hold), 2};
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

        /** True when the pose parameter s is on the fall (the family) and not on the rise path. */
        private boolean onFall(double s) { return fam != null && s > sHold + 1e-9; }

        public WormPose pose(double s) { return poseLimited(s, segments); }

        public WormPose poseLimited(double s, int limit) {
            if (onFall(s)) return fam.pose(h0 - (s - sHold), segments, holeX, limit);
            return WormPose.at(path, s, segments, 1.0, limit);
        }

        /** Head tip {model z, height}: the closed flap apex, on the axis of the head ring (segment 0) in front of its front edge. */
        public double[] tip(double s) {
            WormPose p = poseLimitedTwo(s);
            double cone = HeadSpec.CONE_HEIGHT * r0;
            return new double[]{p.joints[0][2] + (p.joints[0][2] - p.joints[1][2]) * cone, p.joints[0][1] + (p.joints[0][1] - p.joints[1][1]) * cone};
        }

        private WormPose poseLimitedTwo(double s) {
            if (onFall(s)) return fam.pose(h0 - (s - sHold), 2, holeX, 2);
            return WormPose.at(path, s, 2, 1.0, 2);
        }

        /** {base height, top of the head height}: the rear of the head ring (joint 1) and the closed flap tip. */
        public double[] headHeights(double s) {
            WormPose p = poseLimitedTwo(s);
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
        public record Frame(double t, double tipHeightW, double headingDeg, double dxW, int exposedRings, String extra) {
            public Frame(double t, double tipHeightW, double headingDeg, double dxW, int exposedRings) { this(t, tipHeightW, headingDeg, dxW, exposedRings, ""); }

            public String line() {
                return String.format(java.util.Locale.ROOT, "frame t=%.2f: headTipHeight=%.2fW headHeading=%.0fdeg headDx=%.2fW exposedRings=%d%s", t, tipHeightW, headingDeg, dxW, exposedRings, extra);
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
            if (fam != null || kind.equals("engulf")) return t >= 5 ? advanceTime + 0.5 * hold : advanceTime + hold + Math.max(0, Math.min(1, t)) * fallTime;
            double[] w = window();
            return w[0] + Math.max(0, Math.min(1, t)) * (w[1] - w[0]);
        }

        /** {model z, heading deg, arc from the tip} where the body (tip, then the joints) crosses height y, or null. */
        public double[] crossing(WormPose p, double[] tip, double y) {
            double[] a = {tip[0], tip[1]};
            double arc = 0;
            for (int k = 0; k <= p.segments; k++) {
                double[] b = k == 0 ? new double[]{p.joints[0][2], p.joints[0][1]} : new double[]{p.joints[k][2], p.joints[k][1]};
                if (k == 0 && Math.abs(b[0] - a[0]) + Math.abs(b[1] - a[1]) < 1e-12) continue;
                double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
                if (a[1] > y && b[1] <= y || a[1] >= y && b[1] < y) {
                    double f = (a[1] - y) / (a[1] - b[1]);
                    return new double[]{a[0] + f * (b[0] - a[0]), Math.toDegrees(Math.atan2(a[1] - b[1], a[0] - b[0])), arc + f * len};
                }
                arc += len;
                a = b;
            }
            return null;
        }

        private Frame fallFrame(double t) {
            double s = at(tauAt(t))[0];
            WormPose p = pose(s);
            double[] tp = tip(s);
            double heading = Math.toDegrees(Math.atan2(p.joints[0][1] - p.joints[1][1], p.joints[0][2] - p.joints[1][2]));
            double[] hole = crossing(p, tp, 0), tail = crossing(p, tp, -FallFamily.TAIL_DEPTH * width * fallScale);
            String extra = String.format(java.util.Locale.ROOT, " exposedLength=%.2fW holeHeading=%s tailDrift=%s", hole == null ? 0.0 : hole[2] / width,
                    hole == null ? "n/a" : String.format(java.util.Locale.ROOT, "%.1fdeg", hole[1]),
                    tail == null ? "n/a" : String.format(java.util.Locale.ROOT, "%.2fW", (tail[0] - holeX) / width));
            return new Frame(t, tp[1] / width, heading, (tp[0] - holeX) / width, p.exposedBody(0), extra);
        }

        public Frame frame(double t) {
            if (fam != null) return fallFrame(t);
            double tauv = tauAt(t);
            double s = at(tauv)[0];
            double[] tp = tip(s);
            double z0 = tip(at(tauAt(0))[0])[0];
            WormPose p = pose(s);
            double[] from = {0, p.joints[0][1], p.joints[0][2]};
            double sf = WormPose.ahead(path, s, from, HeadSpec.CONE_HEIGHT * r0);
            String extra = kind.equals("engulf") ? String.format(java.util.Locale.ROOT, " exposed=%d tipHeight=%.2fblocks phase=%s", p.exposedBody(0), tp[1], phaseAt(tauv).name()) : "";
            return new Frame(t, tp[1] / width, Math.toDegrees(path.theta(sf)), (tp[0] - z0) / width, p.exposedBody(0), extra);
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
        m.pause = CREST_PAUSE;
        m.sApex = TUNNEL_LEAD + 2 * Ru * Math.toRadians(b1);
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

    public static final double MAX_APEX = 6.0;
    public static final double DEFAULT_APEX = 2.5, DEFAULT_ANGLE = 40, DEFAULT_GRAVITY = 12;

    /** Largest launch angle (degrees) for an apex (body widths): the radius at the apex 2H / tan^2(a) must be at least the bend limit. */
    public static double maxProjectileAngle(double apexWidths) {
        return Math.toDegrees(Math.atan(Math.sqrt(apexWidths / (BendLimit.MIN_BEND_RADIUS_WIDTHS / 2.0))));
    }

    /**
     * Crash as a projectile: the head follows a parabola launched from the surface hole at angleDeg, reaching apexWidths x width
     * above the base level, under the worm's own gravity (blocks per second squared); the body follows the head's trail. The
     * speed along the path is physical, v^2 = v0^2 - 2 g y (v0 underground), so the head slows to v0 cos(a) at the top.
     */
    public static Move projectile(double width, int segments, double apexWidths, double angleDeg, double gravity, double underRadiusWidths, boolean force) {
        if (apexWidths <= 0) throw new Rejected("apex must be above 0");
        if (apexWidths > MAX_APEX + 1e-9 && !force) throw new Rejected("apex must be at most " + MAX_APEX + " body widths; use force");
        if (gravity <= 0) throw new Rejected("gravity must be above 0");
        if (angleDeg < 5 || angleDeg > 85) throw new Rejected("angle must be between 5 and 85 degrees");
        double H = apexWidths * width;
        double aMax = maxProjectileAngle(apexWidths), angle = angleDeg;
        String note = "";
        if (angle > aMax + 1e-9 && !force) {
            angle = aMax;
            note = String.format(java.util.Locale.ROOT, "angle %.1f is above the largest allowed %.1f degrees for apex %.2f (the radius at the top would be under %.1f body widths); clamped to %.1f (add force to build it anyway)",
                    angleDeg, aMax, apexWidths, BendLimit.MIN_BEND_RADIUS_WIDTHS, angle);
        }
        double a = Math.toRadians(angle);
        double v0 = Math.sqrt(2 * gravity * H) / Math.sin(a);
        double k = gravity / (v0 * v0 * Math.cos(a) * Math.cos(a));
        Move m = new Move();
        common(m, "proj", width, segments, 0, v0);
        m.nominalSpeed = m.speed = v0;
        m.gravity = gravity;
        m.v0 = v0;
        m.angleDeg = angle;
        m.angleRequested = angleDeg;
        m.note = note;
        m.flightTime = 2 * v0 * Math.sin(a) / gravity;
        m.range = 4 * H / Math.tan(a);
        m.topRadius = 2 * H / (Math.tan(a) * Math.tan(a));
        double ur = underRadiusWidths * width;
        m.underRadius = ur;
        double depth = ascentDepth(width, m.r0, angle, ur);
        double yEnd = depth + ur * (1 - Math.cos(a));
        double straightBelow = -yEnd / Math.sin(a);
        double yDown = Math.min(-(width + m.r0) * Math.sin(a), -(m.r0 + 2.0) + ur * (1 - Math.cos(a)));
        double descentLen = -yDown / Math.sin(a);
        PlanarPath p = PlanarPath.start(0, depth, 0).line(TUNNEL_LEAD).arc(ur, angle).line(straightBelow).parabola(k).line(descentLen).arc(ur, angle).line(20).build();
        m.path = p;
        m.tunnelDepth = depth;
        m.sCross1 = TUNNEL_LEAD + ur * a + straightBelow;
        PlanarPath.Prim para = null;
        for (PlanarPath.Prim q : p.prims()) if (q.isPara()) para = q;
        m.paraLength = para.len();
        m.sCross2 = m.sCross1 + para.len();
        m.sTurn = m.sCross1 + para.len() / 2;
        m.apexY = p.pos(m.sTurn)[1];
        m.segmentsNeeded = (int) Math.ceil(para.len()) + 2;
        if (segments < m.segmentsNeeded && !force) {
            throw new Rejected(String.format(java.util.Locale.ROOT, "the worm is too short for this flight: it needs at least %d segments (the flight is %.1f blocks long) to keep its tail under the sand at both holes; add force to build it anyway", m.segmentsNeeded, para.len()));
        }
        m.sEnd = m.sCross2 + segments + HeadSpec.headLength(width) + 2 * m.r0 + (m.r0 + 1.2) / Math.sin(a) + 4;
        m.hold = 0;
        double v0sq = v0 * v0, vTop = v0 * Math.cos(a);
        int cnt = (int) Math.ceil((m.sEnd - m.s0) / 0.25);
        m.tTab = new double[cnt + 2];
        m.sTab = new double[cnt + 2];
        double t = 0;
        for (int i = 0; i <= cnt; i++) {
            double s = m.s0 + 0.25 * i;
            m.tTab[i] = t;
            m.sTab[i] = s;
            double y = p.pos(s + 0.125)[1];
            double v = y <= 0 ? v0 : Math.sqrt(Math.max(v0sq - 2 * gravity * y, vTop * vTop));
            t += 0.25 / v;
        }
        m.tTab[cnt + 1] = t + 1e-6;
        m.sTab[cnt + 1] = m.s0 + 0.25 * (cnt + 1);
        m.sEnd = m.sTab[cnt];
        m.advanceTime = m.tTab[cnt];
        m.cycle = m.advanceTime + CRASH_PAUSE;
        m.pause = CRASH_PAUSE;
        // most body rings above the base level: scan the flight coarsely and the landing finely (the maximum is around the landing)
        int best = 0;
        double step = Math.max(0.5, para.len() / 40);
        for (double s = m.sCross1; s <= m.sCross2 + m.r0 + 12; s += step) best = Math.max(best, m.pose(s).exposedBody(0));
        for (double s = m.sCross2 - 4; s <= m.sCross2 + 4; s += 0.25) best = Math.max(best, m.pose(s).exposedBody(0));
        m.exposedMax = best;
        return m;
    }

    public static final double MIN_FALL_HEIGHT = 2.0, MAX_FALL_HEIGHT = 6.0, DEFAULT_HEIGHT = 2.5, DEFAULT_LEAN = 7, DEFAULT_HOLD = 1.5;

    /**
     * Crash as rise, hold and fall into the hole (prompt 06). Rise: head-leading, straight at 90 - lean degrees (as emerge) until the
     * head tip is heightWidths x width above the base level; hold for holdSeconds; fall: the tip height drops freely under gravity
     * (h = h0 - g t^2 / 2) and the whole-body pose for each tip height comes from {@link FallFamily}, until the head is under the sand.
     * The pose parameter of the move is the path position s on the rise (up to sHold) and sHold + the drop in blocks on the fall.
     */
    public static Move fall(double width, int segments, double heightWidths, double leanDeg, double holdSeconds, double speed, double gravity,
                            double minRadiusWidths, double underRadiusWidths, boolean force) {
        if (heightWidths <= 0) throw new Rejected("height must be above 0");
        if (!force && heightWidths < MIN_FALL_HEIGHT - 1e-9) throw new Rejected("height must be at least " + MIN_FALL_HEIGHT + " body widths (the hook at the top would break the bend limit); use force");
        if (!force && heightWidths > MAX_FALL_HEIGHT + 1e-9) throw new Rejected("height must be at most " + MAX_FALL_HEIGHT + " body widths; use force");
        if (leanDeg < 0 || leanDeg > 45) throw new Rejected("lean must be between 0 and 45 degrees");
        if (holdSeconds < 0) throw new Rejected("hold must be 0 or more seconds");
        if (gravity <= 0) throw new Rejected("gravity must be above 0");
        String note = "";
        double minR = minRadiusWidths;
        if (minR < BendLimit.MIN_BEND_RADIUS_WIDTHS && !force) {
            note = String.format(java.util.Locale.ROOT, "min_radius %.2f is below the limit of %.1f body widths; clamped to %.1f (add force to build it anyway)", minRadiusWidths, BendLimit.MIN_BEND_RADIUS_WIDTHS, BendLimit.MIN_BEND_RADIUS_WIDTHS);
            minR = BendLimit.MIN_BEND_RADIUS_WIDTHS;
        }
        if (minR <= 0) throw new Rejected("min_radius must be above 0");
        Move m = new Move();
        common(m, "fall", width, segments, 0, speed);
        m.note = note;
        m.gravity = gravity;
        m.leanDeg = leanDeg;
        m.fallScale = heightWidths / FallFamily.SKETCH_HEIGHT;
        double H = heightWidths * width;
        m.h0 = H;
        double angle = 90 - leanDeg, a = Math.toRadians(angle);
        double ur = underRadiusWidths * width;
        m.underRadius = ur;
        double depth = ascentDepth(width, m.r0, angle, ur);
        double yEnd = depth + ur * (1 - Math.cos(a));
        double straightBelow = -yEnd / Math.sin(a);
        double upLen = straightBelow + H / Math.sin(a) + 4 * width + 10;
        PlanarPath p = PlanarPath.start(0, depth, 0).line(TUNNEL_LEAD).arc(ur, angle).line(upLen).build();
        m.path = p;
        m.tunnelDepth = depth;
        m.sCross1 = TUNNEL_LEAD + ur * a + straightBelow;
        m.holeX = p.pos(m.sCross1)[0];
        m.nFormula = H / Math.sin(a) - HeadSpec.headLength(width);
        // rise until the head tip (the closed flap apex) is exactly H above the base level
        double lo = m.sCross1, hi = m.sCross1 + upLen;
        for (int i = 0; i < 60; i++) {
            double mid = 0.5 * (lo + hi);
            if (m.tip(mid)[1] < H) lo = mid; else hi = mid;
        }
        m.sHold = 0.5 * (lo + hi);
        m.n = m.pose(m.sHold).exposedBody(0);
        m.sEnd = m.sHold;
        m.hold = holdSeconds;
        m.advanceTime = (m.sHold - m.s0) / m.speed;
        m.riseTime = m.advanceTime;
        m.fam = new FallFamily(width, heightWidths, leanDeg, minR);
        double hEnd = -(HeadSpec.headLength(width) + 1);
        m.dropEnd = H - hEnd;
        m.fallTime = Math.sqrt(2 * m.dropEnd / gravity);
        m.cycle = m.advanceTime + holdSeconds + m.fallTime + CRASH_PAUSE;
        m.pause = CRASH_PAUSE;
        m.segmentsNeededFall = m.fam.segmentsNeeded(H);
        m.segmentsNeeded = m.segmentsNeededFall;
        if (segments < m.segmentsNeededFall && !force) {
            throw new Rejected(String.format(java.util.Locale.ROOT, "the worm is too short for this fall: it needs at least %d segments (exposed length %.1f blocks at the hold, the buried slant of the last frame and one body width); add force to build it anyway",
                    m.segmentsNeededFall, m.fam.place(H, 0).sigmaTip));
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
        return emerge(width, segments, n, angleDeg, speed, hold, speed, underRadiusWidths, force);
    }

    /** Emerge with its own speed for the way back (blocks per second, prompt 07); retreatSpeed = speed gives the old move. */
    public static Move emerge(double width, int segments, int n, double angleDeg, double speed, double hold, double retreatSpeed, double underRadiusWidths, boolean force) {
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
        m.retreatSpeed = Math.max(retreatSpeed, MIN_SPEED);
        m.retreatTime = (m.sHold - m.s0) / m.retreatSpeed;
        m.cycle = m.advanceTime + hold + m.retreatTime + EMERGE_PAUSE;
        m.pause = EMERGE_PAUSE;
        return m;
    }

    /**
     * Engulf (prompt 07): the head (mouth) and 1 to 3 body rings come out of the ground, then go back down. Rise: as emerge, head-leading,
     * straight at 90 - lean degrees at speed blocks per second until {@code exposed} body rings are above the sand. Hold. Drop: from rest the
     * head tip height falls freely, h = h0 - g t^2 / 2, the body following the same straight line (path position back by
     * (h0 - h) / sin(angle)); no hook, no pose family. The drop ends when the head tip is HEAD_LENGTH + 1 below the sand.
     */
    public static Move engulf(double width, int segments, int exposed, double leanDeg, double holdSeconds, double speed, double gravity,
                              double underRadiusWidths, boolean force) {
        if (exposed < 1) throw new Rejected("exposed must be at least 1 body ring");
        if (exposed > ENGULF_FORCE_MAX_EXPOSED) throw new Rejected("exposed must be at most " + (int) ENGULF_FORCE_MAX_EXPOSED + " body rings, even with force");
        if (exposed > ENGULF_MAX_EXPOSED && !force) throw new Rejected("exposed must be 1 to " + (int) ENGULF_MAX_EXPOSED + " body rings (the mouth and a few rings); use force for up to " + (int) ENGULF_FORCE_MAX_EXPOSED);
        if (leanDeg < 0 || leanDeg > 45) throw new Rejected("lean must be between 0 and 45 degrees");
        if (holdSeconds < 0) throw new Rejected("hold must be 0 or more seconds");
        if (gravity <= 0) throw new Rejected("gravity must be above 0");
        if (segments < exposed + 2 && !force) throw new Rejected("the worm needs at least exposed + 2 = " + (exposed + 2) + " segments (the head ring and the exposed body rings); add force to build it anyway");
        double angle = 90 - leanDeg, a = Math.toRadians(angle);
        Move m = new Move();
        common(m, "engulf", width, segments, exposed, speed);
        double hl = HeadSpec.headLength(width);
        double ur = underRadiusWidths * width;
        m.underRadius = ur;
        // the straight stretch below the crossing must hold the whole drop (head tip HEAD_LENGTH + 1 below the sand, the head ring behind it)
        double depth = Math.min(ascentDepth(width, m.r0, angle, ur), -((hl + 2 + 2 * m.r0) * Math.sin(a) + ur * (1 - Math.cos(a))));
        double yEnd = depth + ur * (1 - Math.cos(a));
        double straightBelow = -yEnd / Math.sin(a);
        double upLen = straightBelow + exposed + hl + 2 * width + 10;
        PlanarPath p = PlanarPath.start(0, depth, 0).line(TUNNEL_LEAD).arc(ur, angle).line(upLen).build();
        m.path = p;
        m.tunnelDepth = depth;
        m.sCross1 = TUNNEL_LEAD + ur * a + straightBelow;
        int seg = Math.max(segments, exposed + 3);
        double sa = firstReach(p, m.sCross1, m.sCross1 + upLen, exposed, seg);
        double sb = firstReach(p, sa, m.sCross1 + upLen + 4 * exposed, exposed + 1, seg);
        m.sHold = 0.5 * (sa + sb);
        m.sEnd = m.sHold;
        m.hold = holdSeconds;
        m.advanceTime = (m.sHold - m.s0) / m.speed;
        m.riseTime = m.advanceTime;
        m.gravity = gravity;
        m.leanDeg = leanDeg;
        m.angleDeg = angle;
        m.dropAngleSin = Math.sin(a);
        m.h0 = m.tip(m.sHold)[1];
        m.dropEnd = m.h0 + hl + 1;
        m.fallTime = Math.sqrt(2 * m.dropEnd / gravity);
        m.cycle = m.advanceTime + holdSeconds + m.fallTime + ENGULF_PAUSE;
        m.pause = ENGULF_PAUSE;
        m.segmentsNeeded = exposed + 2;
        m.exposedMax = exposed;
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
        m.pause = CRASH_PAUSE;
        return m;
    }
}
