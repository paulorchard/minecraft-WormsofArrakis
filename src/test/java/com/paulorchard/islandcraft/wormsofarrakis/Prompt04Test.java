package com.paulorchard.islandcraft.wormsofarrakis;

import com.paulorchard.islandcraft.wormsofarrakis.geometry.BendLimit;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ChainMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.HeadFlaps;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ScaleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ShingleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.Stagger;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.WormMath;
import com.paulorchard.islandcraft.wormsofarrakis.path.HeadSpec;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;
import com.paulorchard.islandcraft.wormsofarrakis.path.SurfaceProvider;
import com.paulorchard.islandcraft.wormsofarrakis.path.WormPose;
import com.paulorchard.islandcraft.wormsofarrakis.path.WormSkin;
import com.paulorchard.islandcraft.wormsofarrakis.path.WorldBase;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Prompt 04: head ring and flaps, tight liner, stagger, base level, frame table. */
class Prompt04Test {
    private static ChainMath curve(double width, int segs, double radiusWidths, double yawShare, double pitchShare) {
        int n = WormMath.faceCount(width);
        double r = WormMath.circumradius(n, 1.0);
        double theta = Math.toRadians(1.0 / (radiusWidths * width));
        theta = 1.0 / (radiusWidths * width);
        double[] yaw = new double[segs], pitch = new double[segs], rad = new double[segs + 1];
        for (int k = 1; k < segs; k++) { yaw[k] = theta * yawShare; pitch[k] = theta * pitchShare; }
        Arrays.fill(rad, r);
        return new ChainMath(segs, 1.0, r, yaw, pitch, rad);
    }

    private static final double[][] PLANES = {{1, 0}, {0, 1}, {0.7071067811865476, 0.7071067811865476}};

    private static ShingleMath.Options opt(double stagger, long seed, boolean tiles, double jitter) {
        return ShingleMath.Options.defaults(ScaleMath.LIP_LENGTH, true).withVariety(stagger, seed, tiles, jitter);
    }

    // ---- Part 0 ----

    @Test void baseLevelResolution() {
        WorldBase.mode = "sea";
        assertEquals(63.0, WorldBase.resolve(63, 80), 1e-9);
        assertEquals(63.0, WorldBase.resolve(0, 80), 1e-9, "fallback constant when the level has no sea level");
        WorldBase.mode = "here";
        assertEquals(79.0, WorldBase.resolve(63, 80), 1e-9);
        WorldBase.mode = "y";
        WorldBase.fixedY = 70;
        assertEquals(70.0, WorldBase.resolve(63, 80), 1e-9);
        WorldBase.mode = "sea";
        assertEquals(63, WorldBase.BASE_Y);
    }

    // ---- Part 1: the head ring ----

    @Test void flapSplitAndHeadLength() {
        assertArrayEquals(new int[]{4, 3, 3}, HeadFlaps.split(10));
        assertArrayEquals(new int[]{17, 17, 16}, HeadFlaps.split(50));
        assertArrayEquals(new int[]{50, 50, 50}, HeadFlaps.split(150));
        for (double w : new double[]{3, 16, 48}) {
            System.out.printf("[head] HEAD_LENGTH width %.0f: ring %.2f (R=%.2f, n=%d) + cone %.2f = %.2f%n", w, ScaleMath.PITCH, HeadSpec.ringRadius(w), WormMath.faceCount(w), HeadSpec.coneHeight(w), HeadSpec.headLength(w));
            assertEquals(ScaleMath.PITCH + 0.6 * HeadSpec.ringRadius(w), HeadSpec.headLength(w), 1e-12);
        }
    }

    @Test void headRingPassesTheCoverageTestsAtTheLimit() {
        for (double w : new double[]{16, 48}) for (double[] pl : PLANES) for (double stagger : new double[]{0, 0.5, 1.0}) {
            int n = WormMath.faceCount(w);
            double r = WormMath.circumradius(n, 1.0);
            ChainMath c = curve(w, 12, BendLimit.MIN_BEND_RADIUS_WIDTHS, pl[0], pl[1]);
            ShingleMath.Result res = ShingleMath.build(c, n, r, 1, opt(stagger, 7, true, 0.04));
            assertEquals(0, res.uncovered, "uncovered vertices, width " + w + " stagger " + stagger);
            assertEquals(0, res.uncutIntersections, "intersections left, width " + w + " stagger " + stagger);
            for (int k = 0; k < 12; k++) assertTrue(Math.max(res.linerRadius[k], res.linerRadius[k + 1]) <= res.deepest[k] - ShingleMath.LINER_MARGIN + 1e-9, "liner inside ring " + k);
        }
        // the head ring of real poses: whenever it is built, the same guarantees hold
        for (double w : new double[]{16, 48}) {
            int n = WormMath.faceCount(w);
            Moves.Move[] moves = {Moves.crest(w, 60, 1.3, 6, 6, 12, false), Moves.emerge(w, 60, 10, 75, 12, 2, 1.0, false), Moves.crash(w, 400, 10, 70, 12, 12, 4, 1.0, 1.0, true, false)};
            for (Moves.Move m : moves) for (double f = 0; f <= 1; f += 0.05) {
                double s = m.s0 + f * (m.sEnd - m.s0);
                WormSkin k = WormSkin.build(m.pose(s), n, m.r0, SurfaceProvider.PLANE, opt(1.0, 3, true, 0.04), 70);
                if (k.from != 0 || k.to < 0) continue;
                assertEquals(0, k.result.uncovered, m.kind + " w" + w + " s=" + s);
                assertEquals(0, k.result.uncutIntersections, m.kind + " w" + w + " s=" + s);
            }
        }
    }

    private static boolean has(float[] v, int quad, double[] p, double tol) {
        for (int k = 0; k < 4; k++) {
            int at = quad * 32 + k * 8;
            if (Math.abs(v[at] - p[0]) < tol && Math.abs(v[at + 1] - p[1]) < tol && Math.abs(v[at + 2] - p[2]) < tol) return true;
        }
        return false;
    }

    @Test void flapHingeVerticesCoincideWithTheRingFrontVerticesAtEveryBend() {
        for (double w : new double[]{3, 16, 48}) for (double[] pl : PLANES) for (double open : new double[]{0, 70}) for (double stagger : new double[]{0, 1.0}) {
            int n = WormMath.faceCount(w);
            double r = WormMath.circumradius(n, 1.0);
            ChainMath c = curve(w, 6, BendLimit.MIN_BEND_RADIUS_WIDTHS, pl[0], pl[1]);
            ShingleMath.Options o = opt(stagger, 11, true, 0.04);
            ShingleMath.Result res = ShingleMath.build(c, n, r, 1, o, 0, 3);
            int before = res.quads;
            double d0 = Stagger.ringDelta(11, 0, n, stagger);
            HeadFlaps.append(res, c, n, r, d0, HeadSpec.CONE_HEIGHT * r, open, o);
            assertEquals(2 * n, res.quads - before, "two faces per flap triangle");
            for (int f = 0; f < n; f++) {
                int q = before + 2 * f;
                assertTrue(has(res.v, q, ShingleMath.frontVertex(c, 0, n, r, d0, f), 1e-4), "hinge vertex " + f);
                assertTrue(has(res.v, q, ShingleMath.frontVertex(c, 0, n, r, d0, (f + 1) % n), 1e-4), "hinge vertex " + (f + 1));
            }
            if (open == 0) {   // closed flaps meet at the apex on the axis
                double[] apex = HeadFlaps.apex(c, HeadSpec.CONE_HEIGHT * r);
                for (int f = 0; f < n; f++) assertTrue(has(res.v, before + 2 * f, apex, 1e-4), "closed flap apex " + f);
            }
        }
    }

    @Test void headTipLiesOnThePath() {
        for (double w : new double[]{16, 48}) {
            Moves.Move[] moves = {Moves.crest(w, 60, 1.3, 6, 6, 12, false), Moves.emerge(w, 60, 10, 75, 12, 2, 1.0, false), Moves.crash(w, 400, 10, 70, 12, 12, 4, 1.0, 1.0, true, false)};
            for (Moves.Move m : moves) {
                double worst = 0;
                for (double s = m.s0; s <= m.sEnd; s += 0.5) {
                    double[] tp = m.tip(s);
                    double best = 1e9;
                    for (double a = s - 1; a <= s + HeadSpec.headLength(w) + 3; a += 0.05) {
                        double[] q = m.path.pos(a);
                        best = Math.min(best, Math.hypot(q[0] - tp[0], q[1] - tp[1]));
                    }
                    worst = Math.max(worst, best);
                }
                System.out.printf("[head] tip distance to path: %s width %.0f worst %.3f%n", m.kind, w, worst);
                assertTrue(worst <= 0.05 * w + 0.1);
            }
        }
    }

    @Test void emergeCountsBodyRingsAndTheHeadStandsOnTop() {
        for (double w : new double[]{3, 16, 48}) for (int n : new int[]{1, 5, 10, 25}) for (double angle : new double[]{90, 60}) {
            Moves.Move m = Moves.emerge(w, n + 12, n, angle, 12, 2, 1.0, false);
            WormPose p = m.pose(m.sHold);
            assertEquals(n, p.exposedBody(0));
            assertTrue(p.centreY(0) > 0, "the head ring is above the base level too");
            double[] h = m.headHeights(m.sHold);
            double sin = Math.sin(Math.toRadians(angle));
            assertEquals(n * sin, h[0], 1.0, "head base height is about n sin(angle)");
            assertEquals(h[0] + HeadSpec.headLength(w) * sin, h[1], 0.1 + 0.05 * w);
        }
    }

    // ---- Part 2: the tight liner ----

    @Test void linerInsetNumbers() {
        System.out.println("[liner] inset (blocks below the skin surface): case, min..max");
        for (double w : new double[]{16, 48}) {
            int n = WormMath.faceCount(w);
            double r = WormMath.circumradius(n, 1.0);
            for (double rw : new double[]{1000, BendLimit.MIN_BEND_RADIUS_WIDTHS}) for (double[] pl : PLANES) {
                ChainMath c = curve(w, 12, rw, pl[0], pl[1]);
                ShingleMath.Result res = ShingleMath.build(c, n, r, 1, ShingleMath.Options.defaults(ScaleMath.LIP_LENGTH, true));
                System.out.printf("[liner] width %.0f radius %.0f widths plane %.2f/%.2f: inset %.4f .. %.4f%n", w, rw, pl[0], pl[1], res.linerInsetMin, res.linerInsetUsed);
                assertTrue(res.linerInsetUsed < 0.5);
                for (int k = 0; k < 12; k++) assertTrue(Math.max(res.linerRadius[k], res.linerRadius[k + 1]) <= res.deepest[k] - ShingleMath.LINER_MARGIN + 1e-9);
            }
        }
    }

    /** True if every skin quad shares both of its lengthwise edge vertices exactly with the next face of the same ring. */
    private static void assertWatertight(ShingleMath.Result res, int n, String what) {
        int[] skin = new int[res.byKind[ShingleMath.SKIN]];
        int c = 0;
        for (int q = 0; q < res.quads; q++) if (res.kind[q] == ShingleMath.SKIN) skin[c++] = q;
        for (int base = 0; base + n <= skin.length; base += n) for (int i = 0; i < n; i++) {
            int a = skin[base + i], b = skin[base + (i + 1) % n];
            int common = 0;
            for (int ka = 0; ka < 4; ka++) for (int kb = 0; kb < 4; kb++) {
                boolean same = true;
                for (int d = 0; d < 3; d++) same &= res.v[a * 32 + ka * 8 + d] == res.v[b * 32 + kb * 8 + d];
                if (same) common++;
            }
            assertTrue(common >= 2, what + ": faces " + i + " and " + (i + 1) + " of a ring share " + common + " vertices");
        }
    }

    @Test void skinIsWatertightAroundEveryRing() {
        for (double w : new double[]{16, 48}) for (double[] pl : PLANES) for (double stagger : new double[]{0, 1.0}) {
            int n = WormMath.faceCount(w);
            double r = WormMath.circumradius(n, 1.0);
            ShingleMath.Result res = ShingleMath.build(curve(w, 10, BendLimit.MIN_BEND_RADIUS_WIDTHS, pl[0], pl[1]), n, r, 1, opt(stagger, 5, true, 0.04));
            assertWatertight(res, n, "width " + w + " stagger " + stagger);
        }
    }

    // ---- Part 3: stagger ----

    @Test void deterministicAndSeedDependent() {
        int n = 50;
        double r = WormMath.circumradius(n, 1.0);
        ChainMath c = curve(16, 20, 4, 1, 0);
        ShingleMath.Result a = ShingleMath.build(c, n, r, 1, opt(1.0, 42, true, 0.04)), b = ShingleMath.build(c, n, r, 1, opt(1.0, 42, true, 0.04)), d = ShingleMath.build(c, n, r, 1, opt(1.0, 43, true, 0.04));
        assertEquals(a.quads, b.quads);
        assertArrayEquals(Arrays.copyOf(a.v, a.quads * 32), Arrays.copyOf(b.v, b.quads * 32));
        assertArrayEquals(Arrays.copyOf(a.color, a.quads), Arrays.copyOf(b.color, b.quads));
        assertFalse(Arrays.equals(Arrays.copyOf(a.v, a.quads * 32), Arrays.copyOf(d.v, d.quads * 32)), "a different seed gives different geometry");
        assertFalse(Arrays.equals(a.ringDelta, d.ringDelta));
    }

    @Test void ringRotationDoesNotDependOnThePose() {
        int n = 50;
        double r = WormMath.circumradius(n, 1.0);
        ShingleMath.Result a = ShingleMath.build(curve(16, 30, 1000, 1, 0), n, r, 1, opt(1.0, 9, true, 0)), b = ShingleMath.build(curve(16, 30, 3, 0.7, 0.7), n, r, 1, opt(1.0, 9, true, 0));
        assertArrayEquals(a.ringDelta, b.ringDelta, 0.0);
        // and a ring keeps its rotation when the worm is longer (the fixed index along the body is what counts)
        ShingleMath.Result longer = ShingleMath.build(curve(16, 80, 1000, 1, 0), n, r, 1, opt(1.0, 9, true, 0));
        for (int k = 0; k < 30; k++) assertEquals(a.ringDelta[k], longer.ringDelta[k], 0.0);
    }

    @Test void neighbouringRingsDifferByMoreThanATenthOfAFace() {
        int n = 50;
        double step = 2 * Math.PI / n;
        for (long seed : new long[]{1, 2, 3, 12345, 99999}) {
            double[] u = Stagger.offsets(seed, 100);
            int good = 0;
            for (int k = 1; k < 100; k++) {
                double dd = Math.abs(u[k] - u[k - 1]);
                dd = Math.min(dd, 1 - dd);
                if (dd > 0.1) good++;
            }
            assertTrue(good >= 0.9 * 99, "seed " + seed + ": " + good + " of 99 pairs");
            double[] one = new double[100];
            for (int k = 0; k < 100; k++) one[k] = Stagger.ringDelta(seed, k, n, 1.0) / step;
            for (int k = 0; k < 100; k++) assertEquals(u[k], one[k], 1e-12);
        }
    }

    @Test void jitterStaysWithinBounds() {
        int n = 50;
        double r = WormMath.circumradius(n, 1.0);
        ShingleMath.Result res = ShingleMath.build(curve(16, 12, 4, 1, 0), n, r, 1, opt(1.0, 5, true, 0.04));
        // jitter stays in 1 +- 0.04 and never above the maximum
        for (int q = 0; q < res.quads; q++) {
            int c = res.color[q];
            int ch = c & 255;
            int base = res.kind[q] == ShingleMath.LINER ? (ShingleMath.LINER_TINT & 255) : 255;
            assertTrue(Math.abs(ch - base) <= base * 0.04 + 1.01, "jitter " + ch + " vs " + base);
        }
    }

    @Test void coverageHoldsForStaggerZeroHalfAndOne() {
        for (double stagger : new double[]{0, 0.5, 1.0}) for (double w : new double[]{3, 16, 48}) for (double[] pl : PLANES) for (long seed : new long[]{1, 2, 3}) {
            int n = WormMath.faceCount(w);
            double r = WormMath.circumradius(n, 1.0);
            ShingleMath.Result res = ShingleMath.build(curve(w, 10, BendLimit.MIN_BEND_RADIUS_WIDTHS, pl[0], pl[1]), n, r, 1, opt(stagger, seed, true, 0.04));
            assertEquals(0, res.uncovered, "uncovered: width " + w + " stagger " + stagger + " seed " + seed);
            assertEquals(0, res.uncutIntersections, "intersections: width " + w + " stagger " + stagger);
            for (int k = 0; k < 10; k++) assertTrue(Math.max(res.linerRadius[k], res.linerRadius[k + 1]) <= res.deepest[k] - ShingleMath.LINER_MARGIN + 1e-9, "liner inside ring " + k);
        }
    }

    @Test void quadCountsAreUnchangedByTheVariety() {
        int n = 50;
        double r = WormMath.circumradius(n, 1.0);
        ChainMath c = curve(16, 20, 3, 1, 0);
        assertEquals(ShingleMath.build(c, n, r, 1, ShingleMath.Options.defaults(ScaleMath.LIP_LENGTH, true)).quads, ShingleMath.build(c, n, r, 1, opt(1.0, 5, true, 0.1)).quads);
    }

    @Test void buildTimeOfAWholeWorm() {
        for (double w : new double[]{16, 48}) {
            int n = WormMath.faceCount(w);
            double r = WormMath.circumradius(n, 1.0);
            ChainMath c = curve(w, 100, 4, 1, 0);
            ShingleMath.build(c, n, r, 1, opt(1.0, 5, true, 0.04));   // warm up
            long best = Long.MAX_VALUE;
            ShingleMath.Result res = null;
            for (int i = 0; i < 5; i++) {
                long t0 = System.nanoTime();
                res = ShingleMath.build(c, n, r, 1, opt(1.0, 5, true, 0.04));
                best = Math.min(best, System.nanoTime() - t0);
            }
            long t0 = System.nanoTime();
            ShingleMath.build(c, n, r, 1, ShingleMath.Options.defaults(ScaleMath.LIP_LENGTH, true));
            long plain = System.nanoTime() - t0;
            System.out.printf("[stagger] build time width %.0f, 100 segments: %.1f ms with stagger/tiles/jitter (%d quads), %.1f ms plain (first run)%n", w, best / 1e6, res.quads, plain / 1e6);
        }
    }

    // ---- Part 5 ----

    @Test void frameTableOfTheThreeMoves() {
        Moves.Move[] moves = {Moves.crest(16, 80, 1.3, 6, 6, 12, false), Moves.emerge(16, 60, 10, 90, 12, 2, 1.0, false), Moves.crash(16, 150, 25, 70, 12, 12, 4, 1.0, 1.0, true, true), Moves.crash(16, 150, 25, 45, 12, 12, 4, 1.0, 1.0, true, true), Moves.crash(16, 150, 25, 85, 12, 12, 4, 1.0, 1.0, true, true)};
        for (Moves.Move m : moves) {
            double[] w = m.window();
            assertTrue(w[1] > w[0], m.kind);
            for (int i = 0; i <= 3; i++) System.out.println("[frames] " + m.kind + (m.kind.equals("crash") ? " angle " + (int) Math.round(Math.toDegrees(m.path.theta(m.sCross1))) : "") + " " + m.frame(i / 3.0).line());
            assertEquals(0, m.frame(0).dxW(), 1e-9);
        }
    }
}
