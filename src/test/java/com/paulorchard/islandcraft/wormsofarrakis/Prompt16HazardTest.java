package com.paulorchard.islandcraft.wormsofarrakis;

import static org.junit.jupiter.api.Assertions.*;

import com.paulorchard.islandcraft.wormsofarrakis.geometry.HeadMouth;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ScaleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ShingleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.WormMath;
import com.paulorchard.islandcraft.wormsofarrakis.hazard.Box;
import com.paulorchard.islandcraft.wormsofarrakis.hazard.HazardRules;
import com.paulorchard.islandcraft.wormsofarrakis.hazard.WormHazard;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.DevourGate;
import com.paulorchard.islandcraft.wormsofarrakis.path.HeadSpec;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;
import com.paulorchard.islandcraft.wormsofarrakis.path.Sway;
import com.paulorchard.islandcraft.wormsofarrakis.path.SurfaceProvider;
import com.paulorchard.islandcraft.wormsofarrakis.path.WormPose;
import com.paulorchard.islandcraft.wormsofarrakis.path.WormSkin;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveFactory;
import com.paulorchard.islandcraft.wormsofarrakis.test.Presets;
import com.paulorchard.islandcraft.wormsofarrakis.test.WormSpec;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Prompt 16 part 2: the hit shapes (the renderer's pose, the capsules, the shell, the throat, the sampling, the substeps) and the kill rules. No Minecraft type is used. */
class Prompt16HazardTest {
    static final String[] SIZES = {"small", "medium", "large", "huge"};

    static WormSpec preset(String size, String move) { return Presets.of(Presets.Size.of(size), move).spec(); }

    static WormSpec noSway(WormSpec s) { return MoveFactory.withSway(s, Sway.Params.OFF); }

    static WormHazard hazard(WormSpec s, WormHazard.Settings st) { return new WormHazard(s, 0, 0, 0, 0, st); }

    static Box point(WormHazard h, double[] model) {
        double[] w = h.toWorld(model[0], model[1], model[2]);
        return new Box(w[0], w[1], w[2], w[0], w[1], w[2]);
    }

    /** The first tau of the move at which the head is open by at least {@code deg}. */
    static double openTau(WormSpec s, double deg) {
        Moves.Move m = MoveFactory.get(s);
        for (double t = 0; t < m.cycle; t += 0.01) if (MoveFactory.mouthAngle(s, t) >= deg) return t;
        fail("the mouth never opens by " + deg);
        return 0;
    }

    // ---- the pose is the renderer's ----

    @Test
    void thePoseIsTheRenderersWithin1e4ForTheThroatApexTheRimAndABodyRing() {
        for (String size : SIZES) for (String move : new String[]{"crest", "crash", "emerge", "engulf"}) {
            WormSpec s = preset(size, move);
            Moves.Move m = MoveFactory.get(s);
            WormHazard h = hazard(s, WormHazard.Settings.DEFAULTS);
            for (double tau : new double[]{m.apexTime(), 0.5 * m.playTime()}) {
                // what TestWormRenderer does
                double sHead = m.at(tau)[0];
                WormPose wp = m.pose(sHead);
                int n = WormMath.faceCount(m.width);
                ShingleMath.Options opt = ShingleMath.Options.defaults(ScaleMath.LIP_LENGTH, true);
                WormSkin skin = WormSkin.build(wp, n, m.r0, SurfaceProvider.PLANE, opt, 0.0, MoveFactory.tailSpec(s), m.width, MoveFactory.swayOffsets(s, m, tau, wp.joints));
                double[][] F = skin.chain.frame[0];
                double[] C = skin.chain.center[0];
                HeadMouth.Mesh thr = HeadMouth.throat(m.width, n, m.r0, HeadMouth.roll(n), false);
                WormHazard.Pose p = h.poseAt(tau);
                double[] apexL = thr.apexOuter[0], rimL = thr.arcOuter[0][0];
                for (double[] local : new double[][]{apexL, rimL}) {
                    double[] expect = new double[3];
                    for (int r = 0; r < 3; r++) expect[r] = C[r] + F[r][0] * local[0] + F[r][1] * local[1] + F[r][2] * local[2];   // the renderer's l2w matrix
                    double[] mine = WormHazard.localToModel(p, local);
                    for (int r = 0; r < 3; r++) assertEquals(expect[r], mine[r], 1e-4, size + " " + move + " head frame");
                }
                // the throat apex really is THROAT_APEX_R ring radii behind the rim plane
                assertEquals(HeadMouth.THROAT_APEX_R * m.r0, apexL[2], 1e-9);
                // a body ring: the plain ring of the chain lies on the capsule of the joint (distance r0 from its axis)
                int k = Math.min(skin.chain.segments - 1, Math.max(1, wp.segments / 2));
                for (int j = 0; j <= wp.segments; j++) for (int i = 0; i < 3; i++) assertEquals(skin.chain.center[j][i], p.centre[j][i], 1e-4, "joint " + j);
                assertEquals(m.r0, p.radius[0], 1e-9);
                assertEquals(m.r0, p.radius[k], 1e-9, "the body radius of the skin (the tail tapers only on a crest)");
                // the mandible flaps are the mesh's
                HeadMouth.Mesh mand = HeadMouth.mandibles(m.width, n, m.r0, HeadMouth.roll(n), HeadSpec.coneHeight(m.width), p.mouthDeg, opt, false);
                for (int j = 0; j < 3; j++) {
                    for (int i = 0; i < 3; i++) assertEquals(mand.apexOuter[j][i], p.flaps.apex[j][i], 1e-9);
                    for (int a = 0; a < mand.arcOuter[j].length; a++) for (int i = 0; i < 3; i++) assertEquals(mand.arcOuter[j][a][i], p.flaps.arc[j][a][i], 1e-9);
                }
            }
        }
    }

    @Test
    void theRadiusPerSizeIsTheRingRadiusAndTheTailTapersOnACrest() {
        double[] want = {3.99, 7.96, 15.92, 23.88};
        for (int i = 0; i < 4; i++) {
            WormSpec s = preset(SIZES[i], "crest");
            WormHazard h = hazard(s, WormHazard.Settings.DEFAULTS);
            WormHazard.Pose p = h.poseAt(MoveFactory.get(s).apexTime());
            assertEquals(want[i], p.radius[0], 0.05, SIZES[i] + " r0");
            assertTrue(p.radius[p.radius.length - 1] < p.radius[0] * 0.5, "the crest tail tapers exactly as the skin does");
            WormSpec e = preset(SIZES[i], "emerge");
            WormHazard.Pose pe = hazard(e, WormHazard.Settings.DEFAULTS).poseAt(MoveFactory.get(e).apexTime());
            assertEquals(pe.radius[0], pe.radius[pe.radius.length - 1], 1e-9, "no tail on the other moves");
        }
    }

    // ---- the body ----

    @Test
    void theBodyCapsuleIsInAtTheSurfaceAndWithinTheSkinAndOutJustBeyond() {
        for (String size : SIZES) {
            WormSpec s = noSway(preset(size, "emerge"));
            Moves.Move m = MoveFactory.get(s);
            double skin = 0.3;
            WormHazard h = hazard(s, WormHazard.Settings.DEFAULTS.withParts(true, false, false));
            double tau = m.apexTime();
            WormHazard.Pose p = h.poseAt(tau);
            int k = 5;
            assertTrue(p.drawn[k], "an exposed ring");
            double[] c = p.centre[k];
            double r = p.radius[k];
            // sideways (model x) is perpendicular to every axis of the planar worm
            assertTrue(h.body().touches(point(h, new double[]{c[0] + r - 0.01, c[1], c[2]}), tau), size + " inside the surface");
            assertTrue(h.body().touches(point(h, new double[]{c[0] + r + skin - 0.01, c[1], c[2]}), tau), size + " inside the skin");
            assertFalse(h.body().touches(point(h, new double[]{c[0] + r + skin + 0.02, c[1], c[2]}), tau), size + " just beyond the skin");
            assertFalse(h.body().touches(point(h, new double[]{c[0] + r + 1.0, c[1], c[2]}), tau), size + " a block out");
        }
    }

    @Test
    void anEntityHighAboveTheWormOrFarFromItIsNeverTouched() {
        WormSpec s = preset("large", "crest");
        WormHazard h = hazard(s, WormHazard.Settings.DEFAULTS);
        double tau = MoveFactory.get(s).apexTime();
        assertFalse(h.touches(Box.of(0, 200, 0, 0.6, 1.8), tau));
        assertFalse(h.touches(Box.of(500, 0, 500, 0.6, 1.8), tau));
    }

    // ---- the head: a hollow shell ----

    @Test
    void aBoxInTheOpenMouthIsNotTouchedUntilAFlapReachesIt() {
        for (String size : SIZES) {
            WormSpec s = noSway(preset(size, "emerge"));
            Moves.Move m = MoveFactory.get(s);
            double tau = openTau(s, 30);
            WormHazard h = hazard(s, WormHazard.Settings.DEFAULTS.withParts(false, true, false));
            WormHazard.Pose p = h.poseAt(tau);
            double R = m.r0;
            // between the flaps on the axis, half way to the apex: inside the mouth
            double[] inside = WormHazard.localToModel(p, new double[]{0, 0, 0.5 * HeadSpec.coneHeight(m.width)});
            assertFalse(h.head().touches(point(h, inside), tau), size + " a point in the open mouth is not touched");
            Box small = new Box(h.toWorld(inside[0], inside[1], inside[2])[0] - 0.3, h.toWorld(inside[0], inside[1], inside[2])[1] - 0.3, h.toWorld(inside[0], inside[1], inside[2])[2] - 0.3,
                    h.toWorld(inside[0], inside[1], inside[2])[0] + 0.3, h.toWorld(inside[0], inside[1], inside[2])[1] + 0.9, h.toWorld(inside[0], inside[1], inside[2])[2] + 0.3);
            assertFalse(h.head().touches(small, tau), size + " a player-sized box in the open mouth");
            // a flap tip: touched
            double[] tip = WormHazard.localToModel(p, p.flaps.apex[0]);
            assertTrue(h.head().touches(point(h, tip), tau), size + " the apex of a flap");
            // a point on the outer skin of a triangle, and just outside it within the skin and beyond
            double[] a0 = p.flaps.arc[0][1], a1 = p.flaps.arc[0][2], ap = p.flaps.apex[0];
            double[] mid = {(a0[0] + a1[0] + ap[0]) / 3, (a0[1] + a1[1] + ap[1]) / 3, (a0[2] + a1[2] + ap[2]) / 3};
            double[] nrm = p.flaps.normal[0][1];
            assertTrue(h.head().touches(point(h, WormHazard.localToModel(p, mid)), tau), size + " on the outer skin");
            assertTrue(h.head().touches(point(h, WormHazard.localToModel(p, new double[]{mid[0] + 0.25 * nrm[0], mid[1] + 0.25 * nrm[1], mid[2] + 0.25 * nrm[2]})), tau), size + " inside HazardSkin");
            assertFalse(h.head().touches(point(h, WormHazard.localToModel(p, new double[]{mid[0] + 0.5 * nrm[0], mid[1] + 0.5 * nrm[1], mid[2] + 0.5 * nrm[2]})), tau), size + " beyond HazardSkin");
            assertTrue(R > 3);
        }
    }

    @Test
    void theHeadApproximationErrorIsTheStaggerOfTheRingAndTheCircleOfTheFunnel() {
        // the shell is exact to the mesh; the only differences are the ring stagger of the head (a client look setting) and the polygon of the funnel replaced by its circle
        for (String size : SIZES) {
            Moves.Move m = MoveFactory.get(preset(size, "emerge"));
            int n = WormMath.faceCount(m.width);
            double sagitta = m.r0 * (1 - Math.cos(Math.PI / n));
            double halfFace = Math.PI * m.r0 / n;
            System.out.printf("hazard numbers: %s R=%.2f n=%d circle-vs-polygon %.3f blocks, stagger at most %.2f blocks along the lip%n", size, m.r0, n, sagitta, halfFace);
            assertTrue(sagitta < 0.2 && halfFace < 1.6);
        }
    }

    // ---- the throat ----

    @Test
    void theThroatFunnelAndSlabForEverySizeR() {
        for (double R : new double[]{3.99, 7.96, 15.92, 23.88}) {
            double reach = 0.4;
            assertTrue(WormHazard.inThroat(new double[]{0, 0, -0.5 * R}, R, reach), "the apex");
            assertFalse(WormHazard.inThroat(new double[]{0, 0, -0.5 * R - 0.01}, R, reach), "behind the apex");
            assertTrue(WormHazard.inThroat(new double[]{R - 1e-6, 0, 0}, R, reach), "the rim");
            assertFalse(WormHazard.inThroat(new double[]{R + 0.01, 0, 0}, R, reach), "outside the rim");
            assertTrue(WormHazard.inThroat(new double[]{0, 0, -0.25 * R}, R, reach), "the centre of the funnel");
            assertTrue(WormHazard.inThroat(new double[]{0.5 * R - 1e-6, 0, -0.25 * R}, R, reach), "half way down the radius is R / 2");
            assertFalse(WormHazard.inThroat(new double[]{0.5 * R + 0.01, 0, -0.25 * R}, R, reach));
            assertTrue(WormHazard.inThroat(new double[]{R - 0.01, 0, reach}, R, reach), "the skin over the rim disc at the reach");
            assertFalse(WormHazard.inThroat(new double[]{R - 0.01, 0, reach + 0.01}, R, reach), "beyond ThroatReach");
            assertFalse(WormHazard.inThroat(new double[]{R + 0.01, 0, 0.1}, R, reach), "outside the disc");
        }
    }

    @Test
    void theThroatIsTouchedThroughTheRealPoseAndOnlyWhenTheShapeIsOn() {
        WormSpec s = noSway(preset("medium", "emerge"));
        double tau = openTau(s, 30);
        WormHazard h = hazard(s, WormHazard.Settings.DEFAULTS);
        WormHazard.Pose p = h.poseAt(tau);
        double R = h.R;
        Box inFunnel = point(h, WormHazard.localToModel(p, new double[]{0, 0, -0.25 * R}));
        assertTrue(h.throat().touches(inFunnel, tau));
        assertTrue(h.touches(inFunnel, tau));
        WormHazard off = hazard(s, WormHazard.Settings.DEFAULTS.withParts(true, true, false));
        assertFalse(off.shapes().stream().anyMatch(x -> x.part().equals("throat")), "HazardParts without throat has no throat shape");
        assertEquals(java.util.List.of("body", "head", "throat"), h.shapes().stream().map(x -> x.part()).toList());
        assertEquals(java.util.List.of("head"), hazard(s, WormHazard.Settings.DEFAULTS.withParts(false, true, false)).shapes().stream().map(x -> x.part()).toList());
        assertEquals(0, hazard(s, WormHazard.Settings.DEFAULTS.withParts(false, false, false)).shapes().size());
        WormHazard.Settings parsed = WormHazard.Settings.of(0.3, 0.4, "body, throat", 4, 0);
        assertTrue(parsed.body() && !parsed.head() && parsed.throat());
    }

    // ---- the sampling ----

    @Test
    void theSamplesAreTheCornersTheFaceCentresTheCentreAndTheVerticalLine() {
        for (double height : new double[]{1.8, 1.5}) {   // standing, sneaking
            Box b = Box.of(10, 64, 20, 0.6, height);
            double[][] s = b.samples();
            assertTrue(s.length >= 15 + (int) Math.floor((height - 1e-9) / 0.3) - 1);
            java.util.function.Predicate<double[]> has = q -> { for (double[] p : s) if (Math.abs(p[0] - q[0]) < 1e-9 && Math.abs(p[1] - q[1]) < 1e-9 && Math.abs(p[2] - q[2]) < 1e-9) return true; return false; };
            for (int i = 0; i < 8; i++) assertTrue(has.test(new double[]{(i & 1) == 0 ? 9.7 : 10.3, (i & 2) == 0 ? 64 : 64 + height, (i & 4) == 0 ? 19.7 : 20.3}), "corner " + i);
            assertTrue(has.test(new double[]{10, 64 + height / 2, 20}), "centre");
            assertTrue(has.test(new double[]{9.7, 64 + height / 2, 20}) && has.test(new double[]{10, 64 + height, 20}) && has.test(new double[]{10, 64, 20}), "face centres");
            double prev = 64;
            java.util.List<Double> ys = new java.util.ArrayList<>();
            for (double[] p : s) if (Math.abs(p[0] - 10) < 1e-9 && Math.abs(p[2] - 20) < 1e-9) ys.add(p[1]);
            java.util.Collections.sort(ys);
            for (double y : ys) { assertTrue(y - prev <= 0.3 + 1e-9, "no gap above 0.3 on the line (" + prev + " to " + y + ")"); prev = y; }
            assertEquals(64 + height, ys.get(ys.size() - 1), 1e-9);
        }
        assertEquals(1.5, Box.of(0, 0, 0, 0.6, 1.5).height(), 1e-9, "a sneaking box is lower: its top samples are lower");
    }

    @Test
    void aStandingAndASneakingBoxAreCaughtAtTheEdgeAndAMissedOneJustOutsideIsNot() {
        WormSpec s = noSway(preset("small", "emerge"));
        double tau = openTau(s, 30);
        WormHazard h = hazard(s, WormHazard.Settings.DEFAULTS.withParts(false, false, true));
        WormHazard.Pose p = h.poseAt(tau);
        double R = h.R;
        // a flat sheet of throat: put the throat rim disc plane (head z = reach) at the top of a box: only the top face centre / corners are inside the disc
        for (double height : new double[]{1.8, 1.5}) {
            // a box whose lowest points are 0.1 below the funnel slab edge z = reach; build in head-local, tall along local z is not axis-aligned, so use the line: the box centre line passes through the funnel
            double[] c = WormHazard.localToModel(p, new double[]{0, 0, -0.25 * R});
            double[] w = h.toWorld(c[0], c[1], c[2]);
            Box around = Box.of(w[0], w[1] - height / 2, w[2], 0.6, height);
            assertTrue(h.throat().touches(around, tau), "a box round a point of the funnel (height " + height + ")");
            double[] far = h.toWorld(c[0] + 3 * R, c[1], c[2]);
            assertFalse(h.throat().touches(Box.of(far[0], far[1], far[2], 0.6, height), tau), "far outside");
        }
        // just outside the rim disc's skin: the box face centre 0.05 beyond ThroatReach is not touched
        double[] beyond = WormHazard.localToModel(p, new double[]{0, 0, 0.45});
        double[] bw = h.toWorld(beyond[0], beyond[1], beyond[2]);
        assertFalse(h.throat().touches(new Box(bw[0], bw[1], bw[2], bw[0], bw[1], bw[2]), tau), "0.05 beyond ThroatReach");
    }

    // ---- no tunnelling ----

    @Test
    void theSubstepsCatchAFastWormThatSkipsABoxInOneTick() {
        WormSpec base = noSway(preset("small", "crest"));
        double[] q = java.util.Arrays.copyOf(base.p(), base.p().length);
        q[4] = 900;   // blocks per second: 45 per tick, the whole small body (about 40 blocks) passes a point inside one tick
        WormSpec fast = new WormSpec(base.kind(), base.mode(), q);
        Moves.Move m = MoveFactory.get(fast);
        WormHazard sub = hazard(fast, WormHazard.Settings.DEFAULTS.withParts(true, true, true));
        double dt = 0.05;
        int boundaryOnly = 0, withSubsteps = 0, wrong = 0;
        for (double z = -60; z <= 120; z += 0.5) for (double y : new double[]{0.0, 2.0, 5.0}) {
            Box b = Box.of(0, y, z, 0.6, 1.8);
            boolean atTicks = false, atSub = false;
            for (double t = dt; t <= m.cycle; t += dt) {
                if (sub.touches(b, t)) atTicks = true;
                if (sub.firstContact(b, t - dt, t) != null) atSub = true;
            }
            if (atTicks) boundaryOnly++;
            if (atSub) withSubsteps++;
            if (atTicks && !atSub) wrong++;
        }
        System.out.printf("hazard numbers: fast worm 900 blocks/s: boxes caught at tick boundaries only %d, with 4 substeps %d%n", boundaryOnly, withSubsteps);
        assertEquals(0, wrong, "the last substep is the tick boundary: a boundary hit is never lost");
        assertTrue(withSubsteps > boundaryOnly, "boxes that are skipped over at the tick boundaries are caught by the substeps");
    }

    @Test
    void theTimeOffsetShiftsTheInstantAndSubstepsEndAtTheTick() {
        WormSpec s = noSway(preset("small", "emerge"));
        Moves.Move m = MoveFactory.get(s);
        double tau = m.apexTime();
        WormHazard h0 = hazard(s, WormHazard.Settings.DEFAULTS);
        WormHazard.Pose p = h0.poseAt(tau);
        Box b = point(h0, p.centre[5]);
        WormHazard.Contact c0 = h0.firstContact(b, tau - 0.05, tau);
        assertNotNull(c0);
        assertEquals("body", c0.part());
        assertTrue(c0.t() > tau - 0.05 && c0.t() <= tau + 1e-12);
        WormHazard h1 = hazard(s, WormHazard.Settings.DEFAULTS.withTimeOffset(0.5));
        WormHazard.Contact c1 = h1.firstContact(b, tau - 0.05, tau);
        if (c1 != null) assertEquals(tau + 0.5, c1.t() + 0.05 * (4 - c1.substep()) / 4, 1e-9, "the offset is added to every instant");
        // an offset that shifts the instants away from the contact (the pose at tau + 5 s is back under the sand)
        WormHazard h2 = hazard(s, WormHazard.Settings.DEFAULTS.withTimeOffset(m.playTime() + 5));
        assertNull(h2.firstContact(b, tau - 0.05, tau), "the offset moves the tested instant: after the move is over nothing is touched");
        // the instants of the substeps: 4 of them, the last at tNow + offset
        WormHazard h3 = hazard(s, WormHazard.Settings.DEFAULTS.withSubsteps(4));
        double firstT = h3.firstContact(point(h3, p.centre[5]), tau - 0.04, tau).t();
        assertEquals(tau - 0.04 + 0.01 * Math.round((firstT - (tau - 0.04)) / 0.01), firstT, 1e-9);
    }

    // ---- the kill rules ----

    static HazardRules.Subject sub(boolean living, boolean player, boolean creative, boolean spectator, boolean alive, boolean worm, boolean camera, boolean exempt, boolean refused) {
        return new HazardRules.Subject(living, player, creative, spectator, alive, worm, camera, exempt, refused);
    }

    static final HazardRules.Subject SURVIVAL = sub(true, true, false, false, true, false, false, false, false);
    static final HazardRules.Subject MOB = sub(true, false, false, false, true, false, false, false, false);

    @Test
    void playersAndMobsDieAndTheConfigFlagsSwitchThemOff() {
        assertEquals(HazardRules.Verdict.KILL, HazardRules.decide(SURVIVAL, true, true));
        assertEquals(HazardRules.Verdict.KILL, HazardRules.decide(MOB, true, true));
        assertEquals(HazardRules.Verdict.PLAYERS_OFF, HazardRules.decide(SURVIVAL, false, true));
        assertEquals(HazardRules.Verdict.KILL, HazardRules.decide(MOB, false, true));
        assertEquals(HazardRules.Verdict.MOBS_OFF, HazardRules.decide(MOB, true, false));
        assertEquals(HazardRules.Verdict.KILL, HazardRules.decide(SURVIVAL, true, false));
    }

    @Test
    void creativeSpectatorDeadWormsAndNonLivingAreIgnored() {
        assertEquals(HazardRules.Verdict.CREATIVE, HazardRules.decide(sub(true, true, true, false, true, false, false, false, false), true, true));
        assertEquals(HazardRules.Verdict.SPECTATOR, HazardRules.decide(sub(true, true, false, true, true, false, false, false, false), true, true));
        assertEquals(HazardRules.Verdict.DEAD, HazardRules.decide(sub(true, true, false, false, false, false, false, false, false), true, true));
        assertEquals(HazardRules.Verdict.WORM, HazardRules.decide(sub(true, false, false, false, true, true, false, false, false), true, true));
        assertEquals(HazardRules.Verdict.NOT_LIVING, HazardRules.decide(sub(false, false, false, false, true, false, false, false, false), true, true), "armor stands, items, projectiles, boats");
        assertEquals(HazardRules.Verdict.CAMERA, HazardRules.decide(sub(true, false, false, false, true, false, true, false, false), true, true), "the camera entity is never a target");
    }

    @Test
    void anExemptEntityIsSkippedByPredicateAndTag() {
        HazardRules.Exemptions<String> ex = new HazardRules.Exemptions<>();
        assertFalse(ex.exempt("rider"));
        ex.register(s -> s.equals("rider"));
        ex.register(s -> { throw new IllegalStateException("broken"); });
        assertTrue(ex.exempt("rider"));
        assertFalse(ex.exempt("cow"), "a broken predicate neither exempts nor breaks the rest");
        assertEquals(2, ex.size());
        // the tag and the predicate arrive in the Subject as exempt
        assertEquals(HazardRules.Verdict.EXEMPT, HazardRules.decide(sub(true, true, false, false, true, false, false, true, false), true, true));
        assertEquals(HazardRules.Verdict.EXEMPT, HazardRules.decide(sub(true, false, false, false, true, false, false, true, false), true, true));
    }

    @Test
    void theFirstTouchKillsExactlyOnceAndAMobThatRefusesIsLoggedOnceAndIgnored() {
        HazardRules.Ledger l = new HazardRules.Ledger();
        UUID a = new UUID(1, 1), b = new UUID(1, 2);
        assertTrue(l.firstKill(a));
        assertFalse(l.firstKill(a));
        assertTrue(l.killed(a));
        assertTrue(l.firstKill(b));
        assertTrue(l.firstRefusal(a), "logged the first time");
        assertFalse(l.firstRefusal(a), "never again");
        assertTrue(l.refused(a));
        assertEquals(HazardRules.Verdict.REFUSED_BEFORE, HazardRules.decide(sub(true, false, false, false, true, false, false, false, true), true, true));
        // and in the shapes: the first contact of a box that stays inside the worm is reported by firstContact each tick, the ledger decides it kills once
        WormSpec s = noSway(preset("small", "emerge"));
        WormHazard h = hazard(s, WormHazard.Settings.DEFAULTS);
        double tau = MoveFactory.get(s).apexTime();
        Box bx = point(h, h.poseAt(tau).centre[5]);
        int kills = 0;
        HazardRules.Ledger lg = new HazardRules.Ledger();
        for (double t = tau - 0.2; t <= tau; t += 0.05) if (h.firstContact(bx, t - 0.05, t) != null && lg.firstKill(a)) kills++;
        assertEquals(1, kills);
    }

    @Test
    void testWormsAreDeadlyOnlyWithTheFlagAndTheStrikeWormAlways() {
        assertTrue(HazardRules.deadlyWorm(true, false));
        assertTrue(HazardRules.deadlyWorm(true, true));
        assertFalse(HazardRules.deadlyWorm(false, false));
        assertTrue(HazardRules.deadlyWorm(false, true));
    }

    @Test
    void theBackupKillsAtTheTableTimePlusTheBackupOnlyIfNothingWasTouched() {
        double table = 4.58, backup = 1.5;
        DevourGate g = new DevourGate(table + backup);
        assertEquals(DevourGate.Action.WAIT, g.check((table + backup) * 20 - 1, true), "not at the table time any more");
        assertEquals(DevourGate.Action.WAIT, g.check(table * 20, true));
        assertEquals(DevourGate.Action.KILL, g.check((table + backup) * 20, true));
        assertEquals(DevourGate.Action.WAIT, g.check((table + backup) * 20 + 100, true), "exactly once");
        DevourGate t = new DevourGate(table + backup);
        t.touched();   // the worm touched the victim before
        assertEquals(DevourGate.Action.WAIT, t.check(1000, true), "no backup after a touch");
        DevourGate gone = new DevourGate(table + backup);
        assertEquals(DevourGate.Action.SKIP, gone.check(1000, false), "the victim is gone: nobody is killed");
    }

    @Test
    void theLogLinesNameThePartTheTimesAndTheKind() {
        String l = HazardRules.killLine("Alex", "throat", 3.2, 4.58, "victim");
        assertEquals("hazard: Alex killed by throat at t=3.20 after the move started (devour table said 4.58) kind=victim", l);
        assertTrue(HazardRules.killLine("minecraft:cow", "body", 1.0, 4.58, "mob").endsWith("kind=mob"));
        assertTrue(HazardRules.killLine("Bo", "head", 1.0, 4.58, HazardRules.kind(false, true)).endsWith("kind=other"));
        assertEquals("victim", HazardRules.kind(true, true));
        assertTrue(HazardRules.backupLine("Alex", 6.08, 4.58, 1.5).contains("backup"));
    }

    @Test
    void numbersForTheReportContactCostAndTheWormsOfThePresets() {
        // the cost of one tick of one worm against 1, 20 and 100 boxes standing on the move (4 substeps, all three shapes), W8 and W48, a crash, an emerge and a crest
        StringBuilder out = new StringBuilder();
        for (String size : new String[]{"small", "huge"}) for (String move : new String[]{"crest", "crash", "emerge", "engulf"}) {
            WormSpec s = preset(size, move);
            Moves.Move m = MoveFactory.get(s);
            WormHazard h = hazard(s, WormHazard.Settings.DEFAULTS);
            for (int count : new int[]{1, 20, 100}) {
                java.util.Random rnd = new java.util.Random(5);
                Box[] boxes = new Box[count];
                for (int i = 0; i < count; i++) boxes[i] = Box.of((rnd.nextDouble() - 0.5) * 4 * m.width, 0, (rnd.nextDouble() - 0.5) * 8 * m.width, 0.6, 1.8);
                long best = Long.MAX_VALUE;
                for (int rep = 0; rep < 30; rep++) {
                    long t0 = System.nanoTime();
                    double t = 0.5 + 0.05 * rep;
                    for (Box b : boxes) h.firstContact(b, t - 0.05, t);
                    h.boundsWorld(t, 2.3);
                    if (rep >= 10) best = Math.min(best, System.nanoTime() - t0);
                }
                out.append(String.format("hazard cost %s %s %d entities: %.1f us per tick%n", size, move, count, best / 1000.0));
            }
        }
        System.out.print(out);
        assertTrue(out.length() > 0);
    }
}
