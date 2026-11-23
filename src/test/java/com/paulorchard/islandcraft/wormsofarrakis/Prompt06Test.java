package com.paulorchard.islandcraft.wormsofarrakis;

import com.paulorchard.islandcraft.wormsofarrakis.geometry.BendLimit;
import com.paulorchard.islandcraft.wormsofarrakis.path.FallFamily;
import com.paulorchard.islandcraft.wormsofarrakis.path.HeadSpec;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;
import com.paulorchard.islandcraft.wormsofarrakis.path.WormPose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Prompt 06: the pose family of the fall and the crash as rise, hold and fall. */
class Prompt06Test {
    private static final double[] WIDTHS = {3, 16, 48};
    private static final double LIMIT = BendLimit.MIN_BEND_RADIUS_WIDTHS;

    private static FallFamily fam(double w, double heightW) { return new FallFamily(w, heightW, 7, LIMIT); }

    // ---------------------------------------------------------------- Part 1

    @Test void holeAtTheOriginAndOneCrossing() {
        for (double w : WIDTHS) {
            FallFamily f = fam(w, 2.5);
            for (int i = 0; i <= 40; i++) {
                double h = (2.5 - 2.9 * i / 40.0) * w;
                FallFamily.Placed pl = f.place(h, 0.0);
                double[] o = pl.shape.pos(0);
                assertEquals(0, o[0], 1e-9);
                assertEquals(0, o[1], 1e-9);
                int crossings = 0;
                double prev = pl.shape.pos(-200 * w)[1];
                for (double s = -200 * w; s <= 40 * w; s += w / 20) {
                    double y = pl.shape.pos(s)[1];
                    if ((prev < 0) != (y < 0)) crossings++;
                    prev = y;
                }
                assertEquals(1, crossings, "h=" + h / w);
            }
        }
    }

    @Test void keyFramesMatchTheSketchAndTheLimitHolds() {
        System.out.println("[fall] key frames at the default tower (W16): frame tipH exposed holeHdg tipHdg tailDrift minRadius | sketch tipH exposed tailDrift");
        for (double w : WIDTHS) {
            FallFamily f = fam(w, 2.5);
            for (int k = 0; k < 4; k++) {
                FallFamily.Info in = f.info(FallFamily.KEY_H[k] * w);
                if (w == 16) System.out.printf("[fall] tip dx frame %d: %.2f (sketch %.2f)%n", k + 1, in.tipDxW(), FallFamily.KEY_DX[k]);
                if (true) System.out.printf("[fall] w=" + w + " frame %d: %s | %.2f %.2f %.2f; sketch hole heading (smoothed profile) %.1f tip heading %.1f%n", k + 1, in.line(), FallFamily.KEY_H[k], FallFamily.KEY_EXPOSED[k], FallFamily.KEY_DRIFT[k],
                        f.keyHeadingDeg(k, 0), f.keyHeadingDeg(k, FallFamily.KEY_EXPOSED[k]));
                assertEquals(FallFamily.KEY_H[k], in.tipHeightW(), 0.03, "tip height, frame " + (k + 1));
                // the sketch hook of frame 3 is tighter than the limit and the smoothing blends the corner at the hole of frame 4: those two may differ more
                assertEquals(FallFamily.KEY_EXPOSED[k], in.exposedW(), k < 2 ? 0.05 : 0.15, "exposed length, frame " + (k + 1) + " width " + w);
                assertEquals(f.keyHeadingDeg(k, 0), in.holeHeadingDeg(), 3.0, "hole heading, frame " + (k + 1));
                assertEquals(f.keyHeadingDeg(k, in.exposedW()), in.tipHeadingDeg(), 5.0, "tip heading, frame " + (k + 1));
                assertEquals(FallFamily.KEY_DRIFT[k], in.tailDriftW(), 0.3, "tail drift, frame " + (k + 1) + " width " + w);
                assertTrue(in.minRadiusW() >= LIMIT - 1e-6, "radius " + in.minRadiusW());
            }
        }
    }

    @Test void limitHoldsAtEveryHeightForEveryTowerHeight() {
        for (double w : WIDTHS) for (double towerW : new double[]{2.0, 2.5, 4.0, 6.0}) {
            FallFamily f = fam(w, towerW);
            for (int i = 0; i < 200; i++) {
                double h = (towerW - (towerW + 0.4) * i / 199.0) * w;
                FallFamily.Placed pl = f.place(h, 0);
                double r = pl.shape.minRadiusW(0, Math.max(pl.sigmaTip, 0));
                assertTrue(r >= LIMIT - 1e-6, "w" + w + " tower " + towerW + " h " + h / w + " radius " + r);
            }
        }
    }

    @Test void jointSpacingIsOnePitchAndTheBodyOnlyRecedes() {
        for (double w : WIDTHS) {
            FallFamily f = fam(w, 2.5);
            double prev = Double.POSITIVE_INFINITY;
            for (int i = 0; i < 200; i++) {
                double h = (2.5 - 2.9 * i / 199.0) * w;
                FallFamily.Placed pl = f.place(h, 0);
                assertTrue(pl.sigmaTip < prev, "sigma_head strictly decreasing at h=" + h / w);
                prev = pl.sigmaTip;
                if (i % 20 == 0) {
                    WormPose p = f.pose(h, 120, 0, 120);
                    for (int k = 0; k < 120; k++)
                        assertEquals(1.0, Math.hypot(p.joints[k + 1][1] - p.joints[k][1], p.joints[k + 1][2] - p.joints[k][2]), 1e-9);
                }
            }
        }
    }

    @Test void poseIsContinuousInTheTipHeight() {
        double worstVisible = 0, worstAll = 0;
        for (double w : WIDTHS) {
            FallFamily f = fam(w, 2.5);
            for (double hw = 2.5; hw > -0.5; hw -= 0.01 * (w > 20 ? 3 : 1)) {
                WormPose a = f.pose(hw * w, 200, 0, 200), b = f.pose((hw - 0.01) * w, 200, 0, 200);
                for (int k = 0; k <= 200; k++) {
                    double d = Math.hypot(a.joints[k][1] - b.joints[k][1], a.joints[k][2] - b.joints[k][2]) / w;
                    worstAll = Math.max(worstAll, d);
                    if (a.joints[k][1] > 0 && b.joints[k][1] > 0) worstVisible = Math.max(worstVisible, d);
                }
            }
        }
        System.out.printf("[fall] largest ring step for 0.01 W of h: visible rings %.4f W, all rings (buried tail included) %.4f W%n", worstVisible, worstAll);
        assertTrue(worstVisible <= 0.02, "visible rings jump " + worstVisible);
    }

    // ---------------------------------------------------------------- Part 2

    private static Moves.Move crash(double w, double heightW) {
        return Moves.fall(w, 1000, heightW, 7, 1.5, 12, 12, LIMIT, 1.0, false);
    }

    private static double tipH(Moves.Move m, double s) { return m.tip(s)[1]; }

    @Test void towerHeightAtTheHoldAndTheFreeFallLaw() {
        for (double w : WIDTHS) for (double hw : new double[]{2.0, 2.5, 4.0, 6.0}) {
            Moves.Move m = crash(w, hw);
            assertEquals(hw * w, tipH(m, m.at(m.advanceTime + 0.5 * m.hold)[0]), 0.06 * w, "tower tip at the hold");
            assertEquals(Math.sqrt(2 * m.dropEnd / m.gravity), m.fallTime, 1e-9);
            double t0 = m.advanceTime + m.hold;
            for (int i = 1; i <= 5; i++) {
                double tf = m.fallTime * i / 5.0 * 0.999;
                double expect = hw * w - 0.5 * m.gravity * tf * tf;
                assertEquals(expect, tipH(m, m.at(t0 + tf)[0]), 0.02 * Math.max(1, w / 16) + 1e-6, "h(t) = h0 - g t^2/2 at " + tf);
            }
            if (w == 16 && hw == 2.5) System.out.printf("[crash] W16 default: n=%d (formula %.2f), tower tip %.2f W, rise %.2f s, fall %.2f s, segmentsNeeded %d%n", m.n, m.nFormula, tipH(m, m.sHold) / w, m.riseTime, m.fallTime, m.segmentsNeeded);
            if (w == 48 && hw == 2.5) System.out.printf("[crash] W48 default: n=%d (formula %.2f), tower tip %.2f W, rise %.2f s, fall %.2f s, segmentsNeeded %d%n", m.n, m.nFormula, tipH(m, m.sHold) / w, m.riseTime, m.fallTime, m.segmentsNeeded);
        }
    }

    @Test void tipDescendsAndTheBodyOnlyRecedesUntilNothingIsAbove() {
        for (double w : WIDTHS) {
            Moves.Move m = crash(w, 2.5);
            double t0 = m.advanceTime + m.hold;
            double prevTip = Double.POSITIVE_INFINITY;
            int prevExposed = Integer.MAX_VALUE;
            for (int i = 0; i <= 300; i++) {
                double s = m.at(t0 + m.fallTime * i / 300.0)[0];
                double tip = tipH(m, s);
                int ex = m.pose(s).exposedBody(0);
                assertTrue(tip < prevTip + 1e-9, "tip strictly falling");
                assertTrue(ex <= prevExposed, "exposed rings never increase");
                prevTip = tip;
                prevExposed = ex;
            }
            WormPose end = m.pose(m.at(t0 + m.fallTime + 0.1)[0]);
            assertEquals(0, end.exposedBody(0));
            assertTrue(end.centreY(0) < 0);
        }
    }

    @Test void riseAndFallAgreeAtTheSwitch() {
        for (double w : WIDTHS) for (double hw : new double[]{2.0, 2.5, 4.0, 6.0}) for (double lean : new double[]{7, 0, 20}) {
            Moves.Move m = Moves.fall(w, 1000, hw, lean, 1.5, 12, 12, LIMIT, 1.0, false);
            WormPose rise = m.poseLimited(m.sHold, 300);
            WormPose fall = m.fam.pose(m.h0, 300, m.holeX, 300);
            int visible = 0;
            for (int k = 0; k <= 300; k++) {
                if (rise.joints[k][1] <= 0 && k > 0 && rise.joints[k - 1][1] <= 0) continue;
                visible++;
                assertEquals(rise.joints[k][1], fall.joints[k][1], 1e-3, "y of joint " + k);
                assertEquals(rise.joints[k][2], fall.joints[k][2], 1e-3, "z of joint " + k);
                if (k < 300) assertEquals(rise.pitchAngle[k], fall.pitchAngle[k], 1e-3, "joint angle " + k);
            }
            assertTrue(visible > 3);
        }
    }

    @Test void noVisibleJointExceedsTheBendLimitDuringTheWholeMove() {
        double worst = 0;
        for (double w : WIDTHS) for (double hw : new double[]{2.0, 2.5, 4.0, 6.0}) {
            Moves.Move m = Moves.fall(w, 1000, hw, 7, 1.5, 12, 12, LIMIT, 1.0, false);
            double limit = BendLimit.maxJointAngle(w, 1.0) * (1 + 1e-3);
            double t0 = m.advanceTime + m.hold;
            for (int i = 0; i < 200; i++) {
                WormPose p = m.pose(m.at(t0 + m.fallTime * i / 199.0)[0]);
                for (int k = 1; k < 400; k++) {
                    if (p.joints[k + 1][1] <= 0) continue;   // the limit is for the body above the sand; the rings that dip under it at the hole are free
                    double a = Math.abs(p.pitchAngle[k]);
                    worst = Math.max(worst, a / BendLimit.maxJointAngle(w, 1.0));
                    assertTrue(a <= limit, "w" + w + " h" + hw + " i" + i + " joint " + k + " angle " + Math.toDegrees(a) + " limit " + Math.toDegrees(limit));
                }
            }
        }
        System.out.printf("[crash] largest visible joint angle during the fall: %.3f of the limit%n", worst);
    }

    @Test void shortWormIsRefusedWithTheNumberNeeded() {
        for (double w : new double[]{16, 48}) {
            Moves.Move m = crash(w, 2.5);
            System.out.printf("[crash] segments needed for the defaults at W%.0f: %d%n", w, m.segmentsNeeded);
            Moves.Rejected e = assertThrows(Moves.Rejected.class, () -> Moves.fall(w, m.segmentsNeeded - 1, 2.5, 7, 1.5, 12, 12, LIMIT, 1.0, false));
            assertTrue(e.getMessage().contains("at least " + m.segmentsNeeded + " segments"), e.getMessage());
            assertDoesNotThrow(() -> Moves.fall(w, m.segmentsNeeded, 2.5, 7, 1.5, 12, 12, LIMIT, 1.0, false));
            assertDoesNotThrow(() -> Moves.fall(w, m.segmentsNeeded - 1, 2.5, 7, 1.5, 12, 12, LIMIT, 1.0, true));
        }
        assertThrows(Moves.Rejected.class, () -> Moves.fall(16, 500, 1.9, 7, 1.5, 12, 12, LIMIT, 1.0, false));
        assertThrows(Moves.Rejected.class, () -> Moves.fall(16, 500, 6.1, 7, 1.5, 12, 12, LIMIT, 1.0, false));
        assertFalse(Moves.fall(16, 500, 2.5, 7, 1.5, 12, 12, 1.0, 1.0, false).note.isEmpty(), "min_radius below the limit is clamped with a message");
    }

    @Test void noVisibleRingIsDroppedByTheUndergroundRule() {
        for (double w : new double[]{3, 16}) {
            Moves.Move m = crash(w, 2.5);
            int n = com.paulorchard.islandcraft.wormsofarrakis.geometry.WormMath.faceCount(w);
            double t0 = m.advanceTime + m.hold;
            for (int i = 0; i <= 10; i++) {
                WormPose p = m.pose(m.at(t0 + m.fallTime * i / 10.0)[0]);
                boolean[] drawn = p.drawn(m.r0, com.paulorchard.islandcraft.wormsofarrakis.path.SurfaceProvider.PLANE);
                int cap = Math.min(p.segments, w > 10 ? 120 : 160);
                for (int k = 0; k < cap; k++) {
                    var r = com.paulorchard.islandcraft.wormsofarrakis.geometry.ShingleMath.build(p.chain(m.r0), n, m.r0, 1, com.paulorchard.islandcraft.wormsofarrakis.geometry.ShingleMath.Options.defaults(0.132, true), k, k);
                    double top = -1e9;
                    for (int q = 0; q < r.quads; q++) for (int c = 0; c < 4; c++) top = Math.max(top, r.v[q * 32 + c * 8 + 1]);
                    if (!drawn[k]) assertTrue(top <= -0.9, "w" + w + " t" + i + " segment " + k + " skipped but reaches y=" + top);
                    if (top > 0) assertTrue(drawn[k], "w" + w + " t" + i + " segment " + k + " is above the sand but not drawn");
                }
            }
        }
    }

    @Test void frameTableAtTheFourKeyHeights() {
        for (double w : new double[]{16, 48}) {
            Moves.Move m = crash(w, 2.5);
            for (int i = 0; i <= 4; i++) System.out.println("[crash] W" + (int) w + " fall " + m.frame(i / 4.0).line());
            System.out.println("[crash] W" + (int) w + " hold " + m.frame(9).line());
        }
    }

    @Test void exposedPartBuildTimeAndQuadsShrinkDuringTheFall() {
        for (double w : new double[]{16, 48}) {
            Moves.Move m = crash(w, 2.5);
            int n = com.paulorchard.islandcraft.wormsofarrakis.geometry.WormMath.faceCount(w);
            var opt = com.paulorchard.islandcraft.wormsofarrakis.path.WormSkin.defaultOptions(true);
            double t0 = m.advanceTime + m.hold;
            int prevQuads = Integer.MAX_VALUE;
            for (int i = 0; i <= 4; i++) {
                double s = m.at(t0 + m.fallTime * i / 4.0 * 0.999)[0];
                WormPose p = m.pose(s);
                com.paulorchard.islandcraft.wormsofarrakis.path.WormSkin.build(p, n, m.r0, com.paulorchard.islandcraft.wormsofarrakis.path.SurfaceProvider.PLANE, opt, 0);
                long best = Long.MAX_VALUE;
                com.paulorchard.islandcraft.wormsofarrakis.path.WormSkin sk = null;
                for (int r = 0; r < 4; r++) {
                    long a = System.nanoTime();
                    sk = com.paulorchard.islandcraft.wormsofarrakis.path.WormSkin.build(p, n, m.r0, com.paulorchard.islandcraft.wormsofarrakis.path.SurfaceProvider.PLANE, opt, 0);
                    best = Math.min(best, System.nanoTime() - a);
                }
                System.out.printf("[crash] W%.0f fall t=%.2f: exposed rings %d, built segments %d..%d, quads %d, build %.1f ms%n", w, i / 4.0, p.exposedBody(0), sk.from, sk.to, sk.result.quads, best / 1e6);
                assertTrue(sk.result.quads <= prevQuads, "quads shrink during the fall");
                prevQuads = sk.result.quads;
            }
        }
    }
}
