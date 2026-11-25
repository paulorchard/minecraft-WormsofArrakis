package com.paulorchard.islandcraft.wormsofarrakis;

import static org.junit.jupiter.api.Assertions.*;

import com.paulorchard.islandcraft.wormsofarrakis.camera.StrikeCam;
import com.paulorchard.islandcraft.wormsofarrakis.hazard.Box;
import com.paulorchard.islandcraft.wormsofarrakis.hazard.DiscRules;
import com.paulorchard.islandcraft.wormsofarrakis.hazard.Discs;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.DiscListener;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.DiscRecord;
import com.paulorchard.islandcraft.wormsofarrakis.hazard.WormHazard;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.Alignment;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.SizeTable;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.StrikeType;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveFactory;
import com.paulorchard.islandcraft.wormsofarrakis.test.WormSpec;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Prompt 18: the discs, the expected times from the move's own frames, the camera framing and the clearance simulation. Engine-free. */
class Prompt18Test {
    static WormHazard hz(StrikeType t, SizeTable.Size s) {
        WormSpec spec = Alignment.once(t, s);
        return new WormHazard(spec, 0, 0, 0, 0, WormHazard.Settings.DEFAULTS);
    }

    static double entryZ(StrikeType t, SizeTable.Size s) { return Alignment.entryZ(MoveFactory.get(Alignment.once(t, s))); }

    /** First tick time (s) at which the disc meets a 0.6 x 1.8 box standing at the entry crossing, or -1. */
    static double firstTime(WormHazard h, Discs.Disc d, double ez) {
        Box b = Box.of(0, 0, ez, 0.6, 1.8);
        for (int tick = 1; tick <= (int) (h.move.playTime() * 20) + 40; tick++) {
            double tn = MoveFactory.tau(h.spec, tick), tp = MoveFactory.tau(h.spec, tick - 1);
            WormHazard.Contact c = h.discContact(b, tp, tn, d);
            if (c != null) return tick / 20.0;
        }
        return -1;
    }

    @Test
    void discGeometryInBlocksForTheFourSizes() {
        StringBuilder out = new StringBuilder("disc geometry (blocks):\n");
        for (SizeTable.Size s : SizeTable.Size.values()) {
            WormHazard h = hz(StrikeType.CRASH, s);
            Discs.Disc th = h.throatDisc(), mo = h.mouthDisc();
            double HL = h.headLength();
            assertEquals(0, th.z(), 1e-12);
            assertEquals(0.95 * h.R, th.radius(), 1e-9);
            assertEquals(0.5 * HL, mo.z(), 1e-9);
            assertEquals(0.8 * h.R, mo.radius(), 1e-9);
            assertEquals(0.5, th.thickness(), 1e-12);
            out.append(String.format(Locale.ROOT, "  %s: R %.2f HL %.2f throat disc z 0 radius %.2f, mouth disc z %.2f radius %.2f, thickness %.2f%n", s.label, h.R, HL, th.radius(), mo.z(), mo.radius(), th.thickness()));
        }
        System.out.print(out);
    }

    @Test
    void aBoxAtTheCentreOfEachDiscCrossesItAndOneOutsideTheRadiusOrOffThePlaneDoesNot() {
        WormHazard h = hz(StrikeType.EMERGE, SizeTable.Size.MEDIUM);
        double tau = 0;
        for (double t = 0; t < h.move.playTime(); t += 0.05) { if (h.poseAt(t).centre[0][1] > 4) { tau = t; break; } }
        for (Discs.Disc d : new Discs.Disc[]{h.throatDisc(), h.mouthDisc()}) {
            double[] c = WormHazard.localToModel(h.poseAt(tau), new double[]{0, 0, d.z()});
            double[] w = h.toWorld(c[0], c[1], c[2]);
            Box at = new Box(w[0] - 0.3, w[1] - 0.9, w[2] - 0.3, w[0] + 0.3, w[1] + 0.9, w[2] + 0.3);
            assertNotNull(h.discContact(at, tau - 1e-6, tau, d), d.name() + ": a box on the disc's centre crosses it");
            double[] o = WormHazard.localToModel(h.poseAt(tau), new double[]{d.radius() + 1.5, 0, d.z()});
            double[] ow = h.toWorld(o[0], o[1], o[2]);
            assertNull(h.discContact(new Box(ow[0] - 0.3, ow[1] - 0.9, ow[2] - 0.3, ow[0] + 0.3, ow[1] + 0.9, ow[2] + 0.3), tau - 1e-6, tau, d), d.name() + ": outside the radius");
            double[] f = WormHazard.localToModel(h.poseAt(tau), new double[]{0, 0, d.z() + 2.0});
            double[] fw = h.toWorld(f[0], f[1], f[2]);
            assertNull(h.discContact(new Box(fw[0] - 0.05, fw[1] - 0.05, fw[2] - 0.05, fw[0] + 0.05, fw[1] + 0.05, fw[2] + 0.05), tau - 1e-6, tau, d), d.name() + ": off the plane");
        }
    }

    @Test
    void theThinPlaneCrossingCatchesAFastWormThatJumpsOverABoxInOneTick() {
        WormHazard h = hz(StrikeType.EMERGE, SizeTable.Size.SMALL);
        // a tiny box far from any sample test instant: the worm moves 0.6 blocks or more per tick, the disc is 0.5 thick; the first time the box centre is on the other side of the plane the crossing must fire
        double ez = entryZ(StrikeType.EMERGE, SizeTable.Size.SMALL);
        Discs.Disc th = h.throatDisc();
        Box b = new Box(-0.01, 0, ez - 0.01, 0.01, 0.02, ez + 0.01);
        int found = 0;
        double hitAt = -1;
        for (int tick = 1; tick < 400 && hitAt < 0; tick++) {
            WormHazard.Contact c = h.discContact(b, MoveFactory.tau(h.spec, tick - 1), MoveFactory.tau(h.spec, tick), th);
            if (c != null) { hitAt = tick; found++; }
        }
        assertTrue(found > 0, "the throat disc crossed the tiny box");
        // one substep only: the sampling alone (instants) would see the disc only when an instant falls inside the 0.5 slab; the crossing test must also see it
        WormHazard one = new WormHazard(h.spec, 0, 0, 0, 0, WormHazard.Settings.DEFAULTS.withSubsteps(1));
        double hit1 = -1;
        for (int tick = 1; tick < 400 && hit1 < 0; tick++) if (one.discContact(b, MoveFactory.tau(h.spec, tick - 1), MoveFactory.tau(h.spec, tick), th) != null) hit1 = tick;
        assertTrue(hit1 > 0 && Math.abs(hit1 - hitAt) <= 2, "with one instant per tick the crossing is still found, within a tick or two of the 4-substep result");
    }

    @Test
    void thePoseIsTheRenderersAndTheThroatDiscIsTheRimOfTheThroatFunnel() {
        WormHazard h = hz(StrikeType.CRASH, SizeTable.Size.MEDIUM);
        double tau = 2.0;
        WormHazard.Pose p = h.poseAt(tau);
        double[] rim = WormHazard.localToModel(p, new double[]{h.R, 0, 0});
        double[] back = h.headLocal(h.toWorld(rim[0], rim[1], rim[2])[0], h.toWorld(rim[0], rim[1], rim[2])[1], h.toWorld(rim[0], rim[1], rim[2])[2], tau);
        assertEquals(h.R, back[0], 1e-4);
        assertEquals(0, back[2], 1e-4, "the rim of the funnel is on the plane z = 0 of the throat disc");
        assertTrue(WormHazard.inThroat(new double[]{h.R * 0.99, 0, -1e-6}, h.R, 0.4), "the funnel's widest section is the rim, radius R, at z = 0");
        assertEquals(0, h.throatDisc().z(), 1e-12);
        assertSame(p, h.poseAt(tau), "the same cached pose the renderer function gives");
    }

    @Test
    void theExpectedDiscTimesForAPlayerAtTheEntryCrossingForAllSixteenMoves() {
        StringBuilder out = new StringBuilder("disc times (a 0.6 x 1.8 box at the entry crossing; seconds after the move started):\n");
        for (StrikeType t : StrikeType.values()) for (SizeTable.Size s : SizeTable.Size.values()) {
            WormHazard h = hz(t, s);
            double ez = entryZ(t, s);
            double m = firstTime(h, h.mouthDisc(), ez), th = firstTime(h, h.throatDisc(), ez);
            out.append(String.format(Locale.ROOT, "  %s %s: mouth %s throat %s gap %s%n", t.label, s.label, f(m), f(th), m < 0 || th < 0 ? "-" : f(th - m)));
            assertTrue(th > 0, t.label + " " + s.label + ": the throat disc reaches a player at the entry crossing");
        }
        System.out.print(out);
    }

    static String f(double v) { return v < 0 ? "never" : String.format(Locale.ROOT, "%.2f", v); }

    // ---- the camera ----

    @Test
    void theDistanceTableBaseFitAndFinalForEveryMoveAndSize() {
        StrikeCam.Params P = StrikeCam.Params.DEFAULTS;
        StringBuilder out = new StringBuilder("camera distance (blocks): R, HL, n, peak, D_base, D_fit, D (view clamp at distance 10 chunks = 144):\n");
        for (StrikeType t : StrikeType.values()) for (SizeTable.Size s : SizeTable.Size.values()) {
            WormHazard h = hz(t, s);
            double peak = h.peak(0.1);
            StrikeCam.Dist d = StrikeCam.distance(P, h.R, h.headLength(), peak, 10);
            assertEquals(Math.max(d.base(), d.fit()), d.wanted(), 1e-9);
            assertTrue(d.value() <= P.maxDistance() + 1e-9);
            out.append(String.format(Locale.ROOT, "  %s %s: R %.2f HL %.2f n %.2f peak %.1f D_base %.1f D_fit %.1f D %.1f%s%n", t.label, s.label, h.R, h.headLength(), d.clearance(), peak, d.base(), d.fit(), d.value(), d.cappedMax() ? " (capped by the maximum)" : d.cappedView() ? " (capped by the loaded area)" : ""));
        }
        System.out.print(out);
        StrikeCam.Dist c = StrikeCam.distance(P, 10, 5, 400, 10);
        assertTrue(c.cappedMax() && c.value() == 120);
        StrikeCam.Dist v = StrikeCam.distance(P, 10, 5, 100, 3);
        assertTrue(v.cappedView() && v.value() == 32, "the loaded area: 16 x (3 - 1)");
        assertEquals(3, StrikeCam.heightAbove(P, 2), 1e-12);
        assertEquals(10, StrikeCam.heightAbove(P, 20), 1e-12);
    }

    @Test
    void theCameraNeverComesCloserThanTheClearanceNeverIntoTerrainAndNeverTooFastOverAllSixteenMoves() {
        StrikeCam.Params P = StrikeCam.Params.DEFAULTS;
        StringBuilder out = new StringBuilder("camera clearance over the whole move (flat sand at y = 0): min distance to the worm surface, n, max push per tick, max step per tick, first position clear:\n");
        int bad = 0;
        for (StrikeType t : StrikeType.values()) for (SizeTable.Size s : SizeTable.Size.values()) {
            WormHazard h = hz(t, s);
            double ez = entryZ(t, s);
            double peak = h.peak(0.1);
            StrikeCam.Dist d = StrikeCam.distance(P, h.R, h.headLength(), peak, 10);
            double tm = firstTime(h, h.mouthDisc(), ez);
            if (tm < 0) tm = firstTime(h, h.throatDisc(), ez);
            int startTick = (int) Math.round(tm * 20);
            int endTick = (int) Math.round(h.move.playTime() * 20) + 20;
            final int[] tk = {startTick};
            StrikeCam.Surface surf = (x, y, z) -> h.nearestSurface(x, y, z, MoveFactory.tau(h.spec, tk[0]));
            StrikeCam.Path path = new StrikeCam.Path(P, 0, 0, ez, 0, d.value(), d.clearance(), peak, 7, (endTick - startTick) / 20.0, surf, (x, z) -> 0.0);
            double[] p0 = path.position();
            double s0 = surf.nearest(p0[0], p0[1], p0[2])[0];
            double minClear = s0, maxPush = 0, maxStep = 0, minY = p0[1];
            double[] last = p0;
            for (int tick = startTick + 1; tick <= endTick; tick++) {
                tk[0] = tick;
                StrikeCam.Pose p = path.step((tick - startTick) / 20.0, surf, (x, z) -> 0.0);
                minClear = Math.min(minClear, p.clearance());
                minY = Math.min(minY, p.y());
                maxStep = Math.max(maxStep, Math.sqrt(Math.pow(p.x() - last[0], 2) + Math.pow(p.y() - last[1], 2) + Math.pow(p.z() - last[2], 2)));
                last = new double[]{p.x(), p.y(), p.z()};
            }
            maxPush = path.maxPushStep;
            out.append(String.format(Locale.ROOT, "  %s %s: min %.2f n %.2f max push %.2f max step %.2f first clear %s (%d pushes) start %.2f s%n", t.label, s.label, minClear, d.clearance(), maxPush, maxStep, path.firstSolved, path.firstPushes, tm));
            assertTrue(maxStep <= P.maxSpeed() + 1e-9, t.label + " " + s.label + ": speed cap");
            assertTrue(minY >= 0.99, t.label + " " + s.label + ": never inside terrain (the sand is at y = 0)");
            assertTrue(maxPush <= P.pushPerTick() * Math.sqrt(3) + 1e-9, "push per tick");
            if (minClear < d.clearance() - 0.25) bad++;
        }
        System.out.print(out);
        assertEquals(0, bad, "every move keeps the camera at least n - 0.25 from the surface (see the printed table)");
    }

    @Test
    void theSweepIsSymmetricAboutSideOnHasSmoothstepEndsAndZeroDegreesIsLocked() {
        StrikeCam.Params P = StrikeCam.Params.DEFAULTS;
        StrikeCam.Path a = new StrikeCam.Path(P, 0, 0, 0, 0, 40, 4, 20, 5, 10, null, null);
        double sideOn = a.azSide;
        assertEquals(sideOn - a.direction * Math.toRadians(60), a.azimuthAt(0), 1e-9);
        assertEquals(sideOn + a.direction * Math.toRadians(60), a.azimuthAt(10), 1e-9);
        assertEquals(sideOn, a.azimuthAt(5), 1e-9, "side-on at the middle: symmetric");
        assertEquals(Math.abs(a.azimuthAt(0) - sideOn), Math.abs(a.azimuthAt(10) - sideOn), 1e-9);
        assertEquals(0, a.azimuthAt(0.001) - a.azimuthAt(0), 1e-5, "zero angular speed at the start");
        assertTrue(Math.abs(Math.sin(sideOn)) < 1e-9, "side-on to a worm travelling along +z (yaw 0): the camera is on the x axis");
        StrikeCam.Params locked = new StrikeCam.Params(0.7, 0.5, 75, 0.85, 120, 0, 1, 1.5, 2, 3, 0.5, 60);
        StrikeCam.Path l = new StrikeCam.Path(locked, 0, 0, 0, 0, 40, 4, 20, 5, 10, null, null);
        assertEquals(l.azimuthAt(0), l.azimuthAt(10), 1e-12);
        StrikeCam.Path b = new StrikeCam.Path(P, 0, 0, 0, 0, 40, 4, 20, 5, 10, null, null);
        assertEquals(a.side, b.side);
        assertEquals(a.direction, b.direction);
        boolean differs = false;
        for (long sd = 0; sd < 20; sd++) { StrikeCam.Path c = new StrikeCam.Path(P, 0, 0, 0, 0, 40, 4, 20, sd, 10, null, null); if (c.side != a.side || c.direction != a.direction) differs = true; }
        assertTrue(differs, "other seeds give other sides and directions");
    }

    // ---- what the discs do and to whom ----

    @Test
    void theMouthDiscStartsTheCameraForPlayersOnlyAndTheThroatDiscSwallowsPlayersAndKillsMobs() {
        var M = Discs.MOUTH;
        var T = Discs.THROAT;
        assertEquals(DiscRules.Outcome.CAMERA, DiscRules.outcome(M, true, true, true, true, true, true));
        assertEquals(DiscRules.Outcome.IGNORE, DiscRules.outcome(M, false, true, true, true, true, true), "mobs ignore the mouth disc");
        assertEquals(DiscRules.Outcome.SWALLOW, DiscRules.outcome(T, true, true, true, true, true, true), "the throat swallows a player (and the camera starts if it had not)");
        assertEquals(DiscRules.Outcome.KILL, DiscRules.outcome(T, false, true, true, true, true, true), "mobs die on the throat disc, no camera");
        assertEquals(DiscRules.Outcome.SPARED, DiscRules.outcome(M, true, false, true, true, true, true));
        assertEquals(DiscRules.Outcome.SPARED, DiscRules.outcome(T, true, false, true, true, true, true));
        assertEquals(DiscRules.Outcome.SPARED, DiscRules.outcome(T, false, false, true, true, true, true));
        assertEquals(DiscRules.Outcome.SWALLOW, DiscRules.outcome(T, true, false, false, true, true, true), "players' immunity switched off");
        assertEquals(DiscRules.Outcome.KILL, DiscRules.outcome(T, false, false, true, false, true, true), "mobs' immunity switched off");
        assertEquals(DiscRules.Outcome.IGNORE, DiscRules.outcome(T, true, true, true, true, false, true), "ThroatDiscEnabled=false");
        assertEquals(DiscRules.Outcome.IGNORE, DiscRules.outcome(M, true, true, true, true, true, false), "MouthDiscEnabled=false");
        assertEquals(DiscRules.Outcome.SWALLOW, DiscRules.outcome(T, true, true, true, true, true, false), "the throat still works without the mouth disc");
        for (String part : new String[]{"body", "head", "mandible", "funnel"}) assertEquals(DiscRules.Outcome.IGNORE, DiscRules.outcome(part, true, true, true, true, true, true), part);
    }

    @Test
    void aBoxInTheBodyAtAMandibleOrBesideTheFunnelTriggersNeitherDiscWhileTheGeometryStillSeesIt() {
        WormHazard h = hz(StrikeType.EMERGE, SizeTable.Size.MEDIUM);
        int checked = 0;
        for (double tau = 0.5; tau < h.move.playTime() && checked < 12; tau += 0.5) {
            WormHazard.Pose p = h.poseAt(tau);

            int kb = -1;
            for (int k = 4; k < p.drawn.length - 2; k++) if (p.drawn[k] && p.centre[k][1] > 1.0) { kb = k; break; }
            if (kb >= 0) {
                double[] w = h.toWorld(p.centre[kb][0], p.centre[kb][1], p.centre[kb][2]);
                Box inBody = new Box(w[0] - 0.3, w[1] - 0.9, w[2] - 0.3, w[0] + 0.3, w[1] + 0.9, w[2] + 0.3);
                if (h.touches(inBody, tau)) {
                    checked++;
                    assertNull(h.discContact(inBody, tau - 1e-6, tau, h.mouthDisc()), "standing in the body: the mouth disc is not crossed");
                    assertNull(h.discContact(inBody, tau - 1e-6, tau, h.throatDisc()), "standing in the body: the throat disc is not crossed");
                }
            }
            double[] v = WormHazard.localToModel(p, p.flaps.arc[0][p.flaps.arc[0].length / 2]);
            double[] vw = h.toWorld(v[0], v[1], v[2]);
            if (vw[1] > 0.5) {
                Box onMandible = new Box(vw[0] - 0.2, vw[1] - 0.2, vw[2] - 0.2, vw[0] + 0.2, vw[1] + 0.2, vw[2] + 0.2);
                assertNull(h.discContact(onMandible, tau - 1e-6, tau, h.mouthDisc()), "touching a mandible: nothing");
                assertNull(h.discContact(onMandible, tau - 1e-6, tau, h.throatDisc()), "touching a mandible: nothing");
            }
            double[] f = WormHazard.localToModel(p, new double[]{h.R * 1.2, 0, 0});
            double[] fw = h.toWorld(f[0], f[1], f[2]);
            if (fw[1] > 0.5) assertNull(h.discContact(new Box(fw[0] - 0.2, fw[1] - 0.2, fw[2] - 0.2, fw[0] + 0.2, fw[1] + 0.2, fw[2] + 0.2), tau - 1e-6, tau, h.throatDisc()), "beside the funnel's rim: outside the disc");
        }
        assertTrue(checked > 0, "found moments with a box in the body");
    }

    @Test
    void theRecordHoldsBothTimesAndTheGapAndTheListenerFires() {
        DiscRecord r = new DiscRecord();
        java.util.List<String> heard = new java.util.ArrayList<>();
        DiscListener l = new DiscListener() {
            @Override public void onMouth(long event, java.util.UUID player, double t) { heard.add("mouth " + event + " " + t); }
            @Override public void onThroat(long event, java.util.UUID player, double t) { heard.add("throat " + event + " " + t); }
        };
        java.util.UUID u = new java.util.UUID(0, 1);
        assertEquals("neither disc crossed", r.describe());
        assertTrue(r.mouth(5, u, 4.1, java.util.List.of(l)));
        assertFalse(r.mouth(5, u, 4.2, java.util.List.of(l)), "once");
        assertEquals("mouth only at t=4.10 (the throat disc was not crossed)", r.describe());
        assertTrue(r.throat(5, u, 4.5, java.util.List.of(l)));
        assertEquals(0.4, r.gap(), 1e-9);
        assertEquals("mouth at t=4.10 throat at t=4.50 gap=0.40", r.describe());
        assertEquals(java.util.List.of("mouth 5 4.1", "throat 5 4.5"), heard);
        DiscRecord t = new DiscRecord();
        t.throat(1, u, 3.0, java.util.List.of(new DiscListener() {}));
        assertEquals("throat only at t=3.00 (the mouth disc was not crossed)", t.describe());
        DiscRecord b = new DiscRecord();
        b.backup(5.65);
        assertEquals("neither disc crossed; swallowed by the backup at t=5.65", b.describe());
        assertEquals(-1, b.gap(), 0);
    }

    @Test
    void noCameraStartsAtTheEndOfTheFreezeAnyMoreTheOnlyStartersAreTheDiscsAndTheBackup() throws Exception {
        String src = java.nio.file.Files.readString(srcRoot().resolve("src/main/java/com/paulorchard/islandcraft/wormsofarrakis/hunt/GroupRunner.java"));
        int on = src.indexOf("void onChoice("), end = src.indexOf("// ---- the touch ----");
        assertTrue(on > 0 && end > on);
        assertFalse(src.substring(on, end).contains("startStrikeCamera"), "onChoice (the strike start) starts no camera");
        int starts = src.split("startStrikeCamera", -1).length - 1;
        assertEquals(1, starts, "one place starts the strike camera: startCamera(), called from the mouth disc, the throat disc and the backup swallow");
        assertTrue(src.contains("startCamera(e, g, m, p, \"the mouth disc was crossed\""));
        assertTrue(src.contains("backup swallow"));
        assertTrue(src.contains("the throat disc was crossed"));
    }

    @Test
    void everyEndPathOfTheCameraStillExistsAndTheOnesWithoutALivePlayerSendNothing() {
        var paths = com.paulorchard.islandcraft.wormsofarrakis.camera.CameraLifecycle.EndPath.values();
        assertTrue(paths.length >= 10);
        for (var p : paths) {
            boolean back = com.paulorchard.islandcraft.wormsofarrakis.camera.CameraLifecycle.sendsViewBack(p);
            if (p.name().equals("RESPAWN") || p.name().equals("LOGOFF") || p.name().equals("PLAYER_GONE")) assertFalse(back, p + ": the player object is new or gone");
        }
    }

    static java.nio.file.Path srcRoot() {
        java.nio.file.Path p = java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (p != null && !java.nio.file.Files.exists(p.resolve("src/main/java"))) p = p.getParent();
        return p;
    }
}
