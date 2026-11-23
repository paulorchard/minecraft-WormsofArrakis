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
            for (double hw = 2.5; hw > -0.5; hw -= 0.01) {
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
}
