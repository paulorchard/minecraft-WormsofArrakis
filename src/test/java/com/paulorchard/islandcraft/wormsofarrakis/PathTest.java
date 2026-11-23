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
                Moves.crest(w, 60, 0.4, 8, 1.0, false),
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

    @Test void crestTopOfTheHumpIsCrestFractionOfTheWidth() {
        System.out.println("[path] crest: width fraction  measuredTop  expected  archRadius  span(blocks)  headTopAtPeak");
        for (double w : new double[]{3, 16, 48}) for (double frac : new double[]{0.4, 0.5}) {
            Moves.Move m = Moves.crest(w, 60, frac, 8, 1.0, false);
            int n = WormMath.faceCount(w);
            double top = -1e9;
            double step = w > 20 ? 3 : 1;
            for (double s = m.s0; s <= m.sEnd; s += step) {
                WormSkin k = WormSkin.build(m.pose(s), n, m.r0, SurfaceProvider.PLANE, ShingleMath.Options.defaults(0.132, true));
                top = Math.max(top, maxY(k.result));
            }
            assertEquals(frac * w, top, 0.06, "hump top at width " + w + " fraction " + frac);
            double peakHead = -1e9;
            double tipOver = 0;
            for (double s = m.s0; s <= m.sEnd; s += 0.5) {
                double[] bp = m.path.pos(s);
                double sf = WormPose.ahead(m.path, s, new double[]{0, bp[1], bp[0]}, HeadSpec.headLength(w));
                peakHead = Math.max(peakHead, m.headHeights(s)[1]);   // the tip of the head (flaps included) lies on the path
                tipOver = Math.max(tipOver, m.headHeights(s)[1] - m.path.pos(sf)[1]);
            }
            assertTrue(tipOver < 1e-6, "head tip is on the path, never above it");
            // span between the two surface crossings of the path
            double a = Double.NaN, b = Double.NaN;
            for (double s = m.s0; s < m.sEnd; s += 0.01) {
                double y0 = m.path.pos(s)[1] + m.r0, y1 = m.path.pos(s + 0.01)[1] + m.r0;
                if (y0 < 0 && y1 >= 0 && Double.isNaN(a)) a = m.path.pos(s)[0];
                if (y0 >= 0 && y1 < 0) b = m.path.pos(s)[0];
            }
            System.out.printf("[path] crest: %5.0f %4.1f %10.3f %9.3f %8.1f %12.1f %12.3f%n", w, frac, top, frac * w, m.topRadius, b - a, peakHead + m.r0);
            assertTrue(peakHead + m.r0 <= frac * w + 0.2, "head at the peak obeys the same limit");
        }
        assertThrows(Moves.Rejected.class, () -> Moves.crest(16, 60, 0.6, 8, 1.0, false));
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
            assertEquals(n, p.exposed(0), "width " + w + " n " + n);
            double[] h = m.headHeights(m.sHold);
            assertTrue(Math.abs(h[0] - n) <= 1.0, "base height " + h[0] + " vs n " + n);
        }
        for (double angle : new double[]{90, 75, 60}) {
            Moves.Move m = Moves.emerge(16, 40, 10, angle, 12, 2, 1.0, false);
            double[] h = m.headHeights(m.sHold);
            System.out.printf("[path] emerge: 16 %4.0f 10 -> base %.2f  top %.2f  headLength %.2f  (n=10, n*sin(angle)=%.2f)%n", angle, h[0], h[1], HeadSpec.headLength(16), 10 * Math.sin(Math.toRadians(angle)));
            assertEquals(10, m.pose(m.sHold).exposed(0));
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
