package com.paulorchard.islandcraft.wormsofarrakis;

import com.paulorchard.islandcraft.wormsofarrakis.geometry.BendLimit;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ChainMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ShingleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.WormMath;
import com.paulorchard.islandcraft.wormsofarrakis.path.HeadSpec;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;
import com.paulorchard.islandcraft.wormsofarrakis.path.PlanarPath;
import com.paulorchard.islandcraft.wormsofarrakis.path.SurfaceProvider;
import com.paulorchard.islandcraft.wormsofarrakis.path.WormPose;
import com.paulorchard.islandcraft.wormsofarrakis.path.WormSkin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PathTest {
    private static double maxY(ShingleMath.Result r) {
        double m = -1e9;
        for (int q = 0; q < r.quads; q++) for (int k = 0; k < 4; k++) m = Math.max(m, r.v[q * 32 + k * 8 + 1]);
        return m;
    }

    private static Moves.Move[] sampleMoves(double w) {
        return new Moves.Move[]{
                Moves.crest(w, 60, 0.4, 6, 6, 12, false),
                Moves.emerge(w, 60, 10, 90, 12, 2, 1.0, false),
                Moves.emerge(w, 60, 10, 60, 12, 2, 1.0, false),
                Moves.crash(w, 400, 10, 70, 12, 12, 4, 1.0, 1.0, true, false)};
    }

    @Test void arcLengthParametrisationAndQuarterCircle() {
        PlanarPath q = PlanarPath.start(0, 0, 0).arc(10, 90).build();
        assertEquals(10 * Math.PI / 2, q.length(), 1e-9);
        double[] e = q.pos(q.length());
        assertEquals(10, e[0], 1e-9);
        assertEquals(10, e[1], 1e-9);
        for (Moves.Move m : sampleMoves(16)) {
            double h = 1e-4;
            for (double s = -5; s < m.path.length() + 5; s += 0.37) {
                double[] a = m.path.pos(s), b = m.path.pos(s + h);
                assertEquals(1.0, Math.hypot(b[0] - a[0], b[1] - a[1]) / h, 1e-3, "|dP/ds| = 1 at s=" + s);
            }
            // continuity at every primitive boundary
            for (PlanarPath.Prim p : m.path.prims()) {
                double[] a = m.path.pos(p.s0() - 1e-9), b = m.path.pos(p.s0() + 1e-9);
                assertEquals(0, Math.hypot(a[0] - b[0], a[1] - b[1]), 1e-6);
            }
        }
    }

    @Test void poseJointsMatchChainMathWithinTolerance() {
        for (double w : new double[]{3, 16, 48}) for (Moves.Move m : sampleMoves(w)) {
            double r0 = HeadSpec.ringRadius(w);
            for (double f = 0; f <= 1.0; f += 0.1) {
                double s = m.s0 + f * (m.sEnd - m.s0);
                WormPose p = m.pose(s);
                ChainMath c = p.chain(r0);
                for (int k = 0; k <= p.segments; k++) {
                    assertEquals(p.joints[k][1], c.center[k][1], 1e-4, m.kind + " w" + w + " joint " + k + " y");
                    assertEquals(p.joints[k][2], c.center[k][2], 1e-4, m.kind + " w" + w + " joint " + k + " z");
                    // every joint is on the path: distance to the path point found by marching is 0 by construction; check spacing
                    if (k > 0) assertEquals(1.0, Math.hypot(p.joints[k][1] - p.joints[k - 1][1], p.joints[k][2] - p.joints[k - 1][2]), 1e-9);
                }
            }
        }
    }

    /** Distance from the head tip (the closed flap apex) to the path. */
    private static double tipToPath(Moves.Move m, double s) {
        double[] tp = m.tip(s);
        double best = 1e9;
        for (double a = s - 2; a <= s + HeadSpec.headLength(m.width) + 3; a += 0.05) {
            double[] q = m.path.pos(a);
            best = Math.min(best, Math.hypot(q[0] - tp[0], q[1] - tp[1]));
        }
        return best;
    }

    @Test void crestApexTopEqualsHeightTimesWidth() {
        System.out.println("[path] crest apex: width height radius -> apexTop(path) measuredTop(plates) entry exit span exposedLength");
        double[] heights = {0.2, 0.4, 0.8, 1.3, 2.0, 3.0};
        for (double w : new double[]{3, 16, 48}) for (double radius : new double[]{6, 2}) for (double h : heights) {
            Moves.Move m = Moves.crest(w, 60, h, radius, radius, 12, false);
            double rise = Math.sin(Math.toRadians(com.paulorchard.islandcraft.wormsofarrakis.geometry.ScaleMath.TILT_DEG)) * com.paulorchard.islandcraft.wormsofarrakis.geometry.ScaleMath.SKIN_LENGTH;
            double pathTop = m.maxPathHeight() + m.r0 + rise;
            assertEquals(h * w, pathTop, 0.06, "path apex top, width " + w + " height " + h + " radius " + radius);
            double measured = Double.NaN;
            if (w < 20 && radius == 6) {
                int n = WormMath.faceCount(w);
                double top = -1e9, step = w < 5 ? 0.5 : 1.0;
                for (double s = m.s0; s <= m.sEnd; s += step) {
                    WormSkin k = WormSkin.build(m.pose(s), n, m.r0, SurfaceProvider.PLANE, ShingleMath.Options.defaults(0.132, true));
                    top = Math.max(top, maxY(k.result));
                }
                measured = top;
                assertEquals(h * w, top, 0.06, "plate top, width " + w + " height " + h);
            }
            System.out.printf("[path] crest apex: %4.0f %4.1f %3.0f -> %.3f %.3f entry=%.1f exit=%.1f span=%.1f exposedLength=%.1f%n", w, h, radius, pathTop, measured, m.entryAngle, m.exitAngle, m.span(), m.exposedLength);
            double maxTip = 0;
            for (double s = m.s0; s <= m.sEnd; s += 0.5) maxTip = Math.max(maxTip, tipToPath(m, s));
            assertTrue(maxTip <= 0.05 * w + 0.1, "head tip is on the path within " + (0.05 * w + 0.1) + ", measured " + maxTip + " (width " + w + " height " + h + " radius " + radius + ")");
        }
        assertThrows(Moves.Rejected.class, () -> Moves.crest(16, 60, 3.2, 6, 6, 12, false));
        assertThrows(Moves.Rejected.class, () -> Moves.crest(16, 60, 0, 6, 6, 12, false));
    }

    @Test void crestAsymmetricRadiiGiveDifferentAnglesAndTheRadiusIsClamped() {
        Moves.Move m = Moves.crest(16, 80, 1.3, 6, 3, 12, false);
        System.out.printf("[path] crest asymmetric 1.3 radius 6 / 3: entry=%.1f exit=%.1f%n", m.entryAngle, m.exitAngle);
        assertTrue(m.exitAngle > m.entryAngle + 3, "a tighter descent is steeper: entry " + m.entryAngle + " exit " + m.exitAngle);
        Moves.Move sym = Moves.crest(16, 80, 1.3, 6, 6, 12, false);
        assertEquals(sym.entryAngle, sym.exitAngle, 0.5);
        for (double rd : new double[]{2, 3, 4, 6}) {
            Moves.Move a = Moves.crest(16, 80, 1.3, 6, rd, 12, false);
            System.out.printf("[path] crest 16 80 height=1.3 radius=6 radius_down=%.0f: entry=%.1f exit=%.1f span=%.1f W exposedLength=%.1f W%n", rd, a.entryAngle, a.exitAngle, a.span() / 16, a.exposedLength / 16);
        }
        Moves.Move clamped = Moves.crest(16, 80, 0.4, 1.0, 1.0, 12, false);
        assertEquals(BendLimit.MIN_BEND_RADIUS_WIDTHS * 16, clamped.topRadius, 1e-9);
        assertFalse(clamped.note.isEmpty());
        Moves.Move forced = Moves.crest(16, 80, 0.4, 1.0, 1.0, 12, true);
        assertEquals(16.0, forced.topRadius, 1e-9);
        // the reference diagram: 16 80 height 1.3 radius 6 is about 8 widths long and the head re-enters at about 30 degrees
        System.out.printf("[path] crest 16 80 1.3 radius 6: exposedLength=%.1f (%.1f W) span=%.1f (%.1f W) entry=%.1f exit=%.1f%n", sym.exposedLength, sym.exposedLength / 16, sym.span(), sym.span() / 16, sym.entryAngle, sym.exitAngle);
    }

    @Test void frameTableIsMonotoneInTime() {
        Moves.Move m = Moves.crash(16, 150, 25, 70, 12, 12, 4, 1.0, 1.0, true, true);
        for (int i = 0; i <= 3; i++) System.out.println("[path] crash 16 150 25 " + m.frame(i / 3.0).line());
        Moves.Move.Frame f0 = m.frame(0);
        assertEquals(0, f0.dxW(), 1e-9);
        assertTrue(m.frame(0.5).tipHeightW() > f0.tipHeightW());
        assertTrue(m.frame(1).dxW() > m.frame(0.5).dxW());
    }

    @Test void visibleJointsNeverExceedTheLimit() {
        for (double w : new double[]{3, 16, 48}) for (Moves.Move m : sampleMoves(w)) {
            double limit = BendLimit.maxJointAngle(w, 1.0) * (1 + 5e-3);   // chord angle of a unit chord on the limit radius is 0.1% above pitch / radius at width 3
            for (double s = m.s0; s <= m.sEnd; s += 0.5) {
                WormPose p = m.pose(s);
                for (int k = 1; k < p.segments; k++) {
                    boolean visible = Math.max(p.centreY(k), p.centreY(k - 1)) + m.r0 + 0.1 > 0;
                    if (!visible) continue;
                    // a joint between the head turn: relative angle of the two neighbouring chords
                    assertTrue(Math.abs(p.pitchAngle[k]) <= limit, m.kind + " w" + w + " s=" + s + " joint " + k + " angle " + Math.toDegrees(p.pitchAngle[k]));
                }
            }
        }
    }

    @Test void buriedSegmentsAreSkippedAndNoVisibleSegmentIs() {
        for (double w : new double[]{3, 16, 48}) for (Moves.Move m : sampleMoves(w)) {
            int n = WormMath.faceCount(w);
            double step = w > 20 ? (m.sEnd - m.s0) / 8 : (m.sEnd - m.s0) / 20;
            int skippedTotal = 0;
            for (double s = m.s0; s <= m.sEnd; s += step) {
                WormPose p = m.pose(s);
                boolean[] drawn = p.drawn(m.r0, SurfaceProvider.PLANE);
                int cap = Math.min(p.segments, 40);
                for (int k = 0; k < cap; k++) {
                    ShingleMath.Result r = ShingleMath.build(p.chain(m.r0), n, m.r0, 1, ShingleMath.Options.defaults(0.132, true), k, k);
                    double top = maxY(r);
                    if (!drawn[k]) { skippedTotal++; assertTrue(top <= -0.9, m.kind + " w" + w + " segment " + k + " skipped but reaches y=" + top); }
                    if (top > 0) assertTrue(drawn[k], m.kind + " w" + w + " segment " + k + " is above the sand but not drawn");
                }
            }
            assertTrue(skippedTotal > 0, "some segments are buried");
        }
    }

    @Test void quadsBuiltForAHundredSegmentsWithTenExposed() {
        System.out.println("[path] quads for a 100-segment worm with 10 exposed (emerge 90 deg):  width  builtSegments  quads(partial)  quads(whole worm)");
        for (double w : new double[]{16, 48}) {
            Moves.Move m = Moves.emerge(w, 100, 10, 90, 12, 2, 1.0, false);
            int n = WormMath.faceCount(w);
            WormSkin k = WormSkin.build(m.pose(m.sHold), n, m.r0, SurfaceProvider.PLANE, ShingleMath.Options.defaults(0.132, true));
            ShingleMath.Result all = ShingleMath.build(m.pose(m.sHold).chain(m.r0), n, m.r0, 1, ShingleMath.Options.defaults(0.132, true));
            System.out.printf("[path] %5.0f %8d %12d %14d%n", w, k.to - k.from + 1, k.result.quads, all.quads);
            assertTrue(k.result.quads < all.quads / 3);
        }
    }

    @Test void emergeExposedCountEqualsNAtTheHold() {
        System.out.println("[path] emerge: width angle n -> baseHeight  mouthTop  headLength  (hold)");
        for (double w : new double[]{3, 16, 48}) for (int n = 1; n <= 40; n++) {
            Moves.Move m = Moves.emerge(w, n + 12, n, 90, 12, 2, 1.0, false);
            WormPose p = m.pose(m.sHold);
            assertEquals(n, p.exposedBody(0), "width " + w + " n " + n);
            double[] h = m.headHeights(m.sHold);
            assertTrue(Math.abs(h[0] - n) <= 1.0, "base height " + h[0] + " vs n " + n);
        }
        for (double angle : new double[]{90, 75, 60}) {
            Moves.Move m = Moves.emerge(16, 40, 10, angle, 12, 2, 1.0, false);
            double[] h = m.headHeights(m.sHold);
            System.out.printf("[path] emerge: 16 %4.0f 10 -> base %.2f  top %.2f  headLength %.2f  (n=10, n*sin(angle)=%.2f)%n", angle, h[0], h[1], HeadSpec.headLength(16), 10 * Math.sin(Math.toRadians(angle)));
            assertEquals(10, m.pose(m.sHold).exposedBody(0));
            assertEquals(10 * Math.sin(Math.toRadians(angle)), h[0], 1.0);
            assertEquals(h[0] + HeadSpec.headLength(16) * Math.sin(Math.toRadians(angle)), h[1], 0.5 + 0.03 * 16);
        }
    }

    @Test void retreatRetracesTheEmergenceAndTheCrossingIsStraight() {
        for (double w : new double[]{3, 16, 48}) {
            Moves.Move m = Moves.emerge(w, 30, 10, 75, 12, 2, 1.0, false);
            for (double f = 0.1; f < 1; f += 0.2) {
                double t = f * m.advanceTime;
                double s = m.at(t)[0];
                double tBack = m.advanceTime + m.hold + (m.advanceTime - t);
                double[] back = m.at(tBack);
                assertEquals(2, back[1]);
                assertEquals(s, back[0], 1e-6);
                WormPose a = m.pose(s), b = m.pose(back[0]);
                for (int k = 0; k <= a.segments; k++) assertEquals(a.joints[k][1], b.joints[k][1], 1e-4);
            }
            double th = m.path.theta(m.sCross1);
            for (double s = m.sCross1 - w; s <= m.sCross1 + w; s += 0.25) assertEquals(th, m.path.theta(s), 1e-9, "straight through the crossing");
            assertEquals(0, m.path.pos(m.sCross1)[1], 1e-6);
        }
    }

    @Test void crashArcsHeadReentryAndHole() {
        System.out.println("[path] crash: width segs n angle -> maxExposed span apexHeight arcRadius exposedPath (force where too short)");
        for (double w : new double[]{16, 48}) for (double angle : new double[]{70, 45}) {
            Moves.Move m = Moves.crash(w, 600, 10, angle, 12, 12, 4, 1.0, 1.0, true, false);
            double limit = BendLimit.minRadius(w);
            for (PlanarPath.Prim p : m.path.prims()) {
                if (!p.isArc() || p.radius() >= limit - 1e-9) continue;
                for (double s = p.s0(); s <= p.s0() + p.len(); s += 0.1) assertTrue(m.path.pos(s)[1] <= -m.r0 - 0.5, "an arc tighter than the limit must be buried");
            }
            assertTrue(m.topRadius >= limit - 1e-9);
            // mirror re-entry
            assertEquals(-Math.toRadians(angle), m.path.theta(m.sCross2), 1e-9);
            assertEquals(0, m.path.pos(m.sCross2)[1], 1e-6);
            // path length between the crossings = polyline length of the sampled path
            double len = 0;
            double[] prev = m.path.pos(m.sCross1);
            for (double s = m.sCross1 + 0.01; s <= m.sCross2; s += 0.01) { double[] c = m.path.pos(s); len += Math.hypot(c[0] - prev[0], c[1] - prev[1]); prev = c; }
            assertEquals(m.sCross2 - m.sCross1, len, 0.05);
            // the body goes through the same holes: every joint crossing the surface does so within one pitch of the path crossing
            double x1 = m.path.pos(m.sCross1)[0], x2 = m.path.pos(m.sCross2)[0];
            for (double s = m.s0; s <= m.sEnd; s += 0.5) {
                WormPose p = m.pose(s);
                for (int k = 0; k < p.segments; k++) {
                    double y0 = p.joints[k][1], y1 = p.joints[k + 1][1];
                    if ((y0 > 0) != (y1 > 0)) {
                        double z = p.joints[k][2] + (0 - y0) / (y1 - y0) * (p.joints[k + 1][2] - p.joints[k][2]);
                        boolean ok = Math.abs(z - x1) <= 1.0 || Math.abs(z - x2) <= 1.0;
                        assertTrue(ok, "joint crossing at z=" + z + " is not near a hole (" + x1 + ", " + x2 + ")");
                    }
                }
            }
            System.out.printf("[path] crash: %4.0f 600 10 %4.0f -> maxExposed=%d span=%.1f apex=%.1f arcRadius=%.1f exposedPath=%.1f%n", w, angle, m.maxExposed(), m.span(), m.apexY, m.topRadius, m.sCross2 - m.sCross1);
        }
        for (double a : new double[]{30, 45, 60}) {
            Moves.Move m = Moves.crash(16, 900, 1, a, 12, 12, 4, 1.0, 1.0, true, false);
            System.out.printf("[path] crash estimate check: width 16 launch %.0f deg -> arc apex above the turn start %.1f, whole apex height %.1f, span %.1f%n", a, 32 * (1 - Math.cos(Math.toRadians(a))), m.apexY, m.span());
        }
        Moves.Move a = Moves.crash(16, 80, 10, 70, 12, 12, 4, 1.0, 1.0, true, true);
        Moves.Move b = Moves.crash(48, 150, 20, 70, 12, 12, 4, 1.0, 1.0, true, true);
        System.out.printf("[path] crash 16 80 10: maxExposed=%d span=%.1f apex=%.1f exposedPath=%.1f%n", a.maxExposed(), a.span(), a.apexY, a.sCross2 - a.sCross1);
        System.out.printf("[path] crash 48 150 20: maxExposed=%d span=%.1f apex=%.1f exposedPath=%.1f%n", b.maxExposed(), b.span(), b.apexY, b.sCross2 - b.sCross1);
        assertThrows(Moves.Rejected.class, () -> Moves.crash(16, 80, 10, 70, 12, 12, 4, 1.0, 1.0, true, false));
        assertThrows(Moves.Rejected.class, () -> Moves.crash(16, 600, 10, 70, 12, 12, 4, 0.5, 1.0, true, false));
    }

    @Test void ballisticProfileSlowsAtTheTop() {
        Moves.Move m = Moves.crash(16, 600, 10, 70, 12, 12, 4, 1.0, 1.0, true, false);
        double sApex = m.sTurn + m.topRadius * Math.toRadians(70);
        double t0 = 0;
        // find the time at which the head is at the apex and compare speeds around it
        double tApex = -1;
        for (double t = 0; t < m.advanceTime; t += 0.01) if (m.at(t)[0] >= sApex) { tApex = t; break; }
        double vTop = (m.at(tApex + 0.05)[0] - m.at(tApex - 0.05)[0]) / 0.1;
        double vStart = (m.at(0.55)[0] - m.at(0.45)[0]) / 0.1;
        System.out.printf("[path] ballistic: speed at the apex %.2f blocks/s (top_speed 4), early speed %.2f%n", vTop, vStart);
        assertEquals(4.0, vTop, 0.6);
        assertTrue(vStart > vTop);
    }
}
