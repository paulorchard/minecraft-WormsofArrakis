package com.paulorchard.islandcraft.wormsofarrakis;

import com.paulorchard.islandcraft.wormsofarrakis.geometry.BendLimit;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ChainMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ScaleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ShingleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.Stagger;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.WormMath;
import com.paulorchard.islandcraft.wormsofarrakis.path.HeadSpec;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;
import com.paulorchard.islandcraft.wormsofarrakis.path.WormPose;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Prompt 05: ring texture offsets, lip and rim UVs, shared liner joints, projectile crash. */
class Prompt05Test {
    private static final double[][] PLANES = {{1, 0}, {0, 1}, {0.7071067811865476, 0.7071067811865476}};

    private static ChainMath curve(double width, int segs, double radiusWidths, double yawShare, double pitchShare) {
        int n = WormMath.faceCount(width);
        double r = WormMath.circumradius(n, 1.0);
        double theta = 1.0 / (radiusWidths * width);
        double[] yaw = new double[segs], pitch = new double[segs], rad = new double[segs + 1];
        for (int k = 1; k < segs; k++) { yaw[k] = theta * yawShare; pitch[k] = theta * pitchShare; }
        Arrays.fill(rad, r);
        return new ChainMath(segs, 1.0, r, yaw, pitch, rad);
    }

    private static ShingleMath.Options opt(boolean offset, long seed, double stagger) {
        return ShingleMath.Options.defaults(ScaleMath.LIP_LENGTH, true).withVariety(stagger, seed, offset, 0.04);
    }

    // ---- ring offsets ----

    @Test void offsetPattern() {
        for (long seed : new long[]{0, 1, 2, 42, 99999, 123456789L}) {
            int[] o = Stagger.ringOffsetsPx(seed, 400);
            assertArrayEquals(o, Stagger.ringOffsetsPx(seed, 400), "deterministic");
            int prev = -1;
            for (int k = 0; k < 400; k++) {
                assertTrue(o[k] >= 0 && o[k] <= 15, "range");
                if (k % 2 == 0) assertEquals(0, o[k], "even rings (the head ring is ring 0) are normal");
                else {
                    assertTrue(o[k] >= 1, "odd rings are shifted");
                    if (prev >= 0) {
                        int d = Math.floorMod(o[k] - prev, 16);
                        assertTrue(d >= 2 && d <= 14, "consecutive odd rings differ by at least 2 px mod 16: " + prev + " -> " + o[k]);
                    }
                    prev = o[k];
                }
            }
            // the same ring keeps its offset in a longer worm
            int[] longer = Stagger.ringOffsetsPx(seed, 800);
            for (int k = 0; k < 400; k++) assertEquals(o[k], longer[k]);
        }
        assertFalse(Arrays.equals(Stagger.ringOffsetsPx(1, 100), Stagger.ringOffsetsPx(2, 100)), "different seeds differ");
        System.out.println("[offset] seed 42, first rings: " + Arrays.toString(Arrays.copyOf(Stagger.ringOffsetsPx(42, 400), 12)));
    }

    @Test void everyFaceShowsOneFullTileShiftedByItsRingOffset() {
        for (double w : new double[]{16, 48}) {
            int n = WormMath.faceCount(w), segs = 9;
            double r = WormMath.circumradius(n, 1.0);
            ShingleMath.Result res = ShingleMath.build(curve(w, segs, 3, 0.7, 0.7), n, r, 1, opt(true, 7, 0));
            assertEquals(segs, res.offsetPx.length);
            int plates = segs * 4 * n;
            for (int q = 0; q < res.quads; q++) {
                float u0 = Float.MAX_VALUE, u1 = -Float.MAX_VALUE;
                for (int k = 0; k < 4; k++) { float u = res.v[q * 32 + k * 8 + 3]; u0 = Math.min(u0, u); u1 = Math.max(u1, u); }
                boolean liner = q >= plates;
                int ring = liner ? (q - plates) / n : q / (4 * n);
                int px = liner ? (res.offsetPx[ring] + 8) % 16 : res.offsetPx[ring];
                assertEquals(px / 16f, u0, 1e-6f, "quad " + q + " ring " + ring + " starts at its offset");
                if (res.kind[q] == ShingleMath.SKIN || res.kind[q] == ShingleMath.LINER) assertEquals(1.0f, u1 - u0, 1e-6f, "one tile across every skin or liner face (no stretching, no split)");
                else assertTrue(u1 - u0 > 0.8f && u1 - u0 < 1.1f, "a lip or rim runs at tile scale: " + (u1 - u0));
                for (int k = 0; k < 4; k++) { float v = res.v[q * 32 + k * 8 + 4]; assertTrue(v >= 0 && v <= 1, "v inside the tile " + v); }
            }
            // the liner is half a tile away from the plates of its ring
            assertNotEquals(0, (res.offsetPx[2] + 8) % 16 - res.offsetPx[2]);
        }
    }

    @Test void lipAndRimSampleOnlyTheirOwnSliceAtTileScale() {
        for (double w : new double[]{3, 16, 48}) for (double[] pl : PLANES) for (double rw : new double[]{1000, 2}) {
            int n = WormMath.faceCount(w);
            double r = WormMath.circumradius(n, 1.0);
            ShingleMath.Result res = ShingleMath.build(curve(w, 8, rw, pl[0], pl[1]), n, r, 1, opt(true, 3, 0));
            int checked = 0;
            for (int q = 0; q < res.quads; q++) {
                byte kind = res.kind[q];
                if (kind != ShingleMath.LIP && kind != ShingleMath.RIM_LIP && kind != ShingleMath.RIM_SKIN) continue;
                for (int a = 0; a < 4; a++) for (int b = a + 1; b < 4; b++) {
                    double du = Math.abs(res.v[q * 32 + a * 8 + 3] - res.v[q * 32 + b * 8 + 3]);
                    double dv = Math.abs(res.v[q * 32 + a * 8 + 4] - res.v[q * 32 + b * 8 + 4]);
                    double dx = res.v[q * 32 + a * 8] - res.v[q * 32 + b * 8], dy = res.v[q * 32 + a * 8 + 1] - res.v[q * 32 + b * 8 + 1], dz = res.v[q * 32 + a * 8 + 2] - res.v[q * 32 + b * 8 + 2];
                    double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (len < 1e-9) continue;
                    if (du < 1e-6) {   // an edge across the strip: its UV extent is its length in tiles (one block = one tile)
                        assertEquals(len, dv, 1e-3, "across the strip, kind " + kind + " width " + w);
                        checked++;
                    } else if (dv < 1e-6 && du > 0.2) {   // along the strip: its UV extent is its length in tiles
                        assertEquals(len, du, 1e-3, "along the strip, kind " + kind + " width " + w);
                    }
                }
            }
            assertTrue(checked > 0);
        }
    }

    @Test void quadCountsAreTheSameWithAndWithoutOffsets() {
        int n = 50;
        double r = WormMath.circumradius(n, 1.0);
        ChainMath c = curve(16, 40, 3, 1, 0);
        ShingleMath.Result on = ShingleMath.build(c, n, r, 1, opt(true, 5, 0)), off = ShingleMath.build(c, n, r, 1, opt(false, 5, 0));
        assertEquals(off.quads, on.quads);
        System.out.println("[offset] quads for 40 rings W16: " + on.quads + " with offsets, " + off.quads + " without: 0 extra quads per odd ring (the sampler repeats, so the UV is shifted, not split)");
    }

    // ---- liner joints ----

    @Test void linerIsWatertightAtEveryJoint() {
        for (double w : new double[]{16, 48}) for (double[] pl : PLANES) for (double rw : new double[]{1e6, BendLimit.MIN_BEND_RADIUS_WIDTHS}) for (double stagger : new double[]{0, 1.0}) {
            int n = WormMath.faceCount(w), segs = 8;
            double r = WormMath.circumradius(n, 1.0);
            ShingleMath.Result res = ShingleMath.build(curve(w, segs, rw, pl[0], pl[1]), n, r, 1, opt(true, 9, stagger));
            int plates = segs * 4 * n;
            for (int j = 1; j < segs; j++) {
                Set<String> end = new HashSet<>(), start = new HashSet<>(), joint = new HashSet<>();
                for (double[] p : res.linerJoint[j]) joint.add(key((float) p[0], (float) p[1], (float) p[2]));
                for (int i = 0; i < n; i++) {
                    collect(res, plates + (j - 1) * n + i, joint, end);
                    collect(res, plates + j * n + i, joint, start);
                }
                assertEquals(joint, end, "liner ring j-1 ends exactly on the joint ring, width " + w + " joint " + j);
                assertEquals(joint, start, "liner ring j starts exactly on the joint ring (bit-identical vertices), width " + w + " joint " + j);
                assertEquals(n, joint.size());
            }
        }
    }

    private static String key(float x, float y, float z) { return x + "/" + y + "/" + z; }

    private static void collect(ShingleMath.Result res, int quad, Set<String> joint, Set<String> into) {
        for (int k = 0; k < 4; k++) {
            String s = key(res.v[quad * 32 + k * 8], res.v[quad * 32 + k * 8 + 1], res.v[quad * 32 + k * 8 + 2]);
            if (joint.contains(s)) into.add(s);
        }
    }

    @Test void coverageStillHoldsWithOffsets() {
        for (double w : new double[]{3, 16, 48}) for (double[] pl : PLANES) {
            int n = WormMath.faceCount(w);
            double r = WormMath.circumradius(n, 1.0);
            ShingleMath.Result res = ShingleMath.build(curve(w, 10, BendLimit.MIN_BEND_RADIUS_WIDTHS, pl[0], pl[1]), n, r, 1, opt(true, 1, 0));
            assertEquals(0, res.uncovered);
            assertEquals(0, res.uncutIntersections);
            for (int k = 0; k < 10; k++) assertTrue(Math.max(res.linerRadius[k], res.linerRadius[k + 1]) <= res.deepest[k] - ShingleMath.LINER_MARGIN + 1e-9);
        }
    }

    @Test void buildTimeWithOffsets() {
        for (double w : new double[]{16, 48}) {
            int n = WormMath.faceCount(w);
            double r = WormMath.circumradius(n, 1.0);
            ChainMath c = curve(w, 100, 4, 1, 0);
            ShingleMath.build(c, n, r, 1, opt(true, 5, 0));
            long best = Long.MAX_VALUE;
            ShingleMath.Result res = null;
            for (int i = 0; i < 5; i++) {
                long t0 = System.nanoTime();
                res = ShingleMath.build(c, n, r, 1, opt(true, 5, 0));
                best = Math.min(best, System.nanoTime() - t0);
            }
            System.out.printf("[offset] build time width %.0f, 100 segments: %.1f ms, %d quads%n", w, best / 1e6, res.quads);
        }
    }

    // ---- projectile crash ----

    private static Moves.Move proj(double w, double apex, double angle, boolean force) {
        return Moves.projectile(w, 3000, apex, angle, 12, 1.0, force);
    }

    @Test void parabolaIsContinuousWithUnitSpeedAndCorrectGeometry() {
        for (double w : new double[]{3, 16, 48}) for (double apex : new double[]{1.0, 2.5, 4.0}) {
            Moves.Move m = proj(w, apex, 40, false);
            double H = apex * w, a = Math.toRadians(m.angleDeg);
            double h = 1e-4;
            for (double s = m.sCross1 - 3; s < m.sCross2 + 3; s += 0.37) {
                double[] p = m.path.pos(s), q = m.path.pos(s + h);
                assertEquals(1.0, Math.hypot(q[0] - p[0], q[1] - p[1]) / h, 1e-3, "|dP/ds| = 1");
            }
            for (var pr : m.path.prims()) {
                double[] e = m.path.pos(pr.s0() - 1e-9), f = m.path.pos(pr.s0() + 1e-9);
                assertEquals(0, Math.hypot(e[0] - f[0], e[1] - f[1]), 1e-6, "continuous");
                assertEquals(0, Math.abs(m.path.theta(pr.s0() - 1e-9) - m.path.theta(pr.s0() + 1e-9)), 1e-6, "tangent continuous");
            }
            double top = -1e9;
            for (double s = m.sCross1; s <= m.sCross2; s += 0.05) top = Math.max(top, m.tip(s)[1]);
            assertEquals(H, top, 0.1 * w, "head tip apex, width " + w + " apex " + apex);   // prompt 08: tolerance 0.06 w -> 0.1 w (the head is 0.4 R longer; W3 apex 1.0: tip 3.235 vs 3.0)
            assertEquals(H, m.apexY, 1e-6);
            assertEquals(4 * H / Math.tan(a), m.span(), 0.05, "range");
            assertEquals(-a, m.path.theta(m.sCross2), 1e-9, "landing heading");
            assertEquals(a, m.path.theta(m.sCross1), 1e-9, "launch heading");
            assertEquals(0, m.path.pos(m.sCross1)[1], 1e-9);
            assertEquals(0, m.path.pos(m.sCross2)[1], 1e-9);
            double Rm = 2 * H / (Math.tan(a) * Math.tan(a));
            assertEquals(Rm, m.topRadius, 1e-9);
            double hh = 0.01;
            double kappa = (m.path.theta(m.sTurn + hh) - m.path.theta(m.sTurn - hh)) / (2 * hh);
            assertEquals(Rm, 1.0 / Math.abs(kappa), Rm * 2e-3, "radius at the apex");
        }
    }

    @Test void speedProfile() {
        Moves.Move m = proj(16, 2.5, 40, false);
        double a = Math.toRadians(m.angleDeg);
        double tApex = -1;
        for (double t = 0; t < m.advanceTime; t += 0.005) if (m.at(t)[0] >= m.sTurn) { tApex = t; break; }
        double vTop = (m.at(tApex + 0.05)[0] - m.at(tApex - 0.05)[0]) / 0.1;
        assertEquals(m.v0 * Math.cos(a), vTop, 0.03 * m.v0, "speed at the apex");
        double v = (m.at(0.55)[0] - m.at(0.45)[0]) / 0.1;
        assertEquals(m.v0, v, 0.02 * m.v0, "underground speed is v0");
        double tHole = -1;
        for (double t = 0; t < m.advanceTime; t += 0.002) if (m.at(t)[0] >= m.sCross1) { tHole = t; break; }
        double vHole = (m.at(tHole + 0.01)[0] - m.at(tHole - 0.01)[0]) / 0.02;
        assertEquals(m.v0, vHole, 0.03 * m.v0, "speed at the surface");
        System.out.printf("[proj] speeds W16 default: v0=%.2f, at the apex %.2f (v0 cos a = %.2f), flightTime=%.2f%n", m.v0, vTop, m.v0 * Math.cos(a), m.flightTime);
    }

    @Test void angleClampAndLimitRadius() {
        for (double apex : new double[]{1.0, 2.5, 4.0}) {
            double max = Moves.maxProjectileAngle(apex);
            System.out.printf("[proj] largest angle for apex %.1f: %.2f degrees%n", apex, max);
            Moves.Move clamped = proj(16, apex, 85, false);
            assertEquals(max, clamped.angleDeg, 1e-9);
            assertFalse(clamped.note.isEmpty());
            assertEquals(85, clamped.angleRequested, 1e-9);
            assertEquals(BendLimit.MIN_BEND_RADIUS_WIDTHS * 16, clamped.topRadius, 1e-6, "R_apex is exactly 2 widths at the limit angle");
            Moves.Move atLimit = proj(16, apex, max, false);
            assertTrue(atLimit.note.isEmpty());
            Moves.Move forced = proj(16, apex, 85, true);
            assertEquals(85, forced.angleDeg, 1e-9);
            assertTrue(forced.topRadius < 2 * 16);
        }
        assertEquals(57.7, Moves.maxProjectileAngle(2.5), 0.05, "57.7 degrees for the default apex");
    }

    @Test void noVisibleJointExceedsTheBendLimitOnTheFlight() {
        for (double w : new double[]{3, 16, 48}) for (double apex : new double[]{1.0, 2.5, 4.0}) {
            Moves.Move m = Moves.projectile(w, 70, apex, Math.min(40, Moves.maxProjectileAngle(apex)), 12, 1.0, true);
            double limit = BendLimit.maxJointAngle(w, 1.0) * (1 + 5e-3);
            double step = Math.max(0.5, m.paraLength / 60);
            for (double s = m.sCross1 - 2; s <= m.sCross2 + 30; s += step) {
                WormPose p = m.pose(s);
                for (int k = 1; k < p.segments; k++) {
                    boolean visible = Math.max(p.centreY(k), p.centreY(k - 1)) + m.r0 + 0.1 > 0;
                    if (!visible) continue;
                    assertTrue(Math.abs(p.pitchAngle[k]) <= limit, "w" + w + " apex " + apex + " s=" + s + " joint " + k + " angle " + Math.toDegrees(p.pitchAngle[k]));
                }
            }
        }
    }

    @Test void bodyUsesTheSameHolesAndTheShortWormIsRefused() {
        for (double w : new double[]{3, 16}) {
            Moves.Move m = Moves.projectile(w, 3000, 2.5, 40, 12, 1.0, false);
            Moves.Move fit = Moves.projectile(w, m.segmentsNeeded, 2.5, 40, 12, 1.0, false);
            double x1 = fit.path.pos(fit.sCross1)[0], x2 = fit.path.pos(fit.sCross2)[0];
            double step = Math.max(1.0, fit.sEnd / 150);
            for (double s = fit.s0; s <= fit.sEnd; s += step) {
                WormPose p = fit.pose(s);
                for (int k = 0; k < p.segments; k++) {
                    double y0 = p.joints[k][1], y1 = p.joints[k + 1][1];
                    if ((y0 > 0) != (y1 > 0)) {
                        double z = p.joints[k][2] + (0 - y0) / (y1 - y0) * (p.joints[k + 1][2] - p.joints[k][2]);
                        assertTrue(Math.abs(z - x1) <= 1.0 || Math.abs(z - x2) <= 1.0, "joint crossing at z=" + z + " not near a hole (" + x1 + ", " + x2 + ")");
                    }
                }
            }
            Moves.Rejected e = assertThrows(Moves.Rejected.class, () -> Moves.projectile(w, m.segmentsNeeded - 1, 2.5, 40, 12, 1.0, false));
            assertTrue(e.getMessage().contains("needs at least " + m.segmentsNeeded + " segments"), e.getMessage());
            assertDoesNotThrow(() -> Moves.projectile(w, m.segmentsNeeded - 1, 2.5, 40, 12, 1.0, true));
        }
        assertThrows(Moves.Rejected.class, () -> Moves.projectile(16, 1000, 6.5, 40, 12, 1.0, false));
    }

    @Test void numbersAndFrameTables() {
        System.out.println("[proj] width apex angle -> v0 flightTime range(W) apexRadius(W) exposedMax segmentsNeeded flight(blocks)");
        for (double w : new double[]{16, 48}) {
            Moves.Move m = proj(w, 2.5, 40, false);
            System.out.printf("[proj] %4.0f 2.5 40 -> v0=%.2f flightTime=%.2f range=%.2fW apexRadius=%.2fW exposedMax=%d segmentsNeeded=%d flight=%.1f%n", w, m.v0, m.flightTime, m.range / w, m.topRadius / w, m.exposedMax, m.segmentsNeeded, m.paraLength);
        }
        for (double angle : new double[]{40, 34}) {
            Moves.Move m = Moves.projectile(16, 260, 2.5, angle, 12, 1.0, false);
            for (int i = 0; i <= 4; i++) System.out.println("[proj] crash 16 260 angle=" + angle + " " + m.frame(i / 4.0).line());
        }
        Moves.Move d = Moves.projectile(16, 200, 2.5, 40, 12, 1.0, true);
        for (int i = 0; i <= 4; i++) System.out.println("[proj] crash 16 200 (force) " + d.frame(i / 4.0).line());
        assertTrue(HeadSpec.headLength(16) > 0);
    }
}
