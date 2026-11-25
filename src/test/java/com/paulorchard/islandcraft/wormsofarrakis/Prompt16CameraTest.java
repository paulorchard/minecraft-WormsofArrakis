package com.paulorchard.islandcraft.wormsofarrakis;

import static org.junit.jupiter.api.Assertions.*;

import com.paulorchard.islandcraft.wormsofarrakis.camera.CameraLifecycle;
import com.paulorchard.islandcraft.wormsofarrakis.camera.DeathCam;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Prompt 16 part 3: the orbit maths of the death camera (engine-free) and what each way of ending owes the player. */
class Prompt16CameraTest {
    static final DeathCam.Params P = DeathCam.Params.DEFAULTS;

    static DeathCam.Setup setup(double cx, double sy, double cz, double yaw, int size, int view, long seed) {
        return DeathCam.setup(P, cx, sy, cz, yaw, DeathCam.radius(P, size, view).value(), seed);
    }

    @Test
    void theRadiusPerSizeAndTheClampToTheViewDistance() {
        double[] want = {24, 40, 70, 100};
        for (int s = 0; s < 4; s++) {
            DeathCam.Radius r = DeathCam.radius(P, s, 10);
            assertEquals(want[s], r.value(), 1e-9);
            assertFalse(r.clamped(), "view distance 10 allows 144 blocks");
        }
        assertEquals(144, DeathCam.viewLimit(10), 1e-9);
        DeathCam.Radius c = DeathCam.radius(P, 3, 4);   // huge at view distance 4: 16 x 3 = 48
        assertTrue(c.clamped());
        assertEquals(48, c.value(), 1e-9);
        assertEquals(100, c.wanted(), 1e-9);
        assertFalse(DeathCam.radius(P, 0, 4).clamped(), "24 is inside 48");
        assertEquals(16, DeathCam.radius(P, 3, 2).value(), 1e-9, "the smallest view distance leaves 16 blocks");
        assertEquals(16, DeathCam.radius(P, 3, 1).value(), 1e-9, "a view distance under 2 is treated as 2");
    }

    @Test
    void heightIsPointFourFiveRadiusAtLeastEightAndNeverUnderThreeBlocksAboveTheGround() {
        assertEquals(10.8, DeathCam.height(P, 24), 1e-9);
        assertEquals(45, DeathCam.height(P, 100), 1e-9);
        assertEquals(8, DeathCam.height(P, 10), 1e-9, "the minimum");
        DeathCam.Setup s = setup(0, 64, 0, 0, 0, 10, 1);
        assertEquals(64 + 10.8, DeathCam.pose(s, 0, 10, null).y(), 1e-9, "no height map: the plain height");
        assertEquals(64 + 10.8, DeathCam.pose(s, 0, 10, (x, z) -> Double.NaN).y(), 1e-9, "unloaded column: the plain height");
        // a dune under the camera: 3 blocks above its top
        for (double t = 0; t <= 10; t += 0.5) assertTrue(DeathCam.pose(s, t, 10, (x, z) -> 90.0).y() >= 93.0 - 1e-9);
        assertEquals(93.0, DeathCam.pose(s, 0, 10, (x, z) -> 90.0).y(), 1e-9);
        // ground below the plain height changes nothing
        assertEquals(64 + 10.8, DeathCam.pose(s, 0, 10, (x, z) -> 60.0).y(), 1e-9);
    }

    @Test
    void theStartIsSideOnToTheTravelDirectionOnASeededSide() {
        java.util.Set<Integer> sides = new java.util.HashSet<>(), dirs = new java.util.HashSet<>();
        for (long seed = 0; seed < 40; seed++) for (double yaw : new double[]{0, 90, 180, 270, 37, -120}) {
            DeathCam.Setup s = setup(100, 64, -50, yaw, 2, 10, seed);
            DeathCam.Pose p = DeathCam.pose(s, 0, 10, null);
            double ox = p.x() - 100, oz = p.z() + 50;
            double dx = -Math.sin(Math.toRadians(yaw)), dz = Math.cos(Math.toRadians(yaw));   // the way the worm travels
            assertEquals(0, ox * dx + oz * dz, 1e-6, "perpendicular to the travel direction: side-on");
            assertEquals(70, Math.hypot(ox, oz), 1e-9, "at the radius");
            sides.add(s.side());
            dirs.add(s.direction());
        }
        assertEquals(Set.of(-1, 1), sides, "both sides occur");
        assertEquals(Set.of(-1, 1), dirs, "both directions occur");
    }

    @Test
    void theSameSeedGivesTheSameSideAndDirectionAndOtherSeedsDiffer() {
        DeathCam.Setup a = setup(0, 0, 0, 33, 1, 10, 42), b = setup(0, 0, 0, 33, 1, 10, 42);
        assertEquals(a, b);
        boolean differs = false;
        for (long seed = 0; seed < 20; seed++) if (!setup(0, 0, 0, 33, 1, 10, seed).equals(a)) differs = true;
        assertTrue(differs);
    }

    @Test
    void theTurnIsOneHundredTwentyDegreesWithSmoothstepEndsAndZeroAngularSpeedAtBoth() {
        for (long seed = 0; seed < 6; seed++) {
            DeathCam.Setup s = setup(0, 64, 0, 10, 3, 10, seed);
            double run = 12;
            double a0 = DeathCam.azimuth(s, 0, run), a1 = DeathCam.azimuth(s, run, run);
            assertEquals(Math.toRadians(120), Math.abs(a1 - a0), 1e-9);
            assertEquals(s.direction() * Math.toRadians(120), a1 - a0, 1e-9, "the seeded direction");
            assertEquals(a0, DeathCam.azimuth(s, -5, run), 1e-12, "before the death: the start");
            assertEquals(0.5, (DeathCam.azimuth(s, run / 2, run) - a0) / (a1 - a0), 1e-9, "half way at half the time");
            double h = 1e-4;
            assertEquals(0, (DeathCam.azimuth(s, h, run) - a0) / h, 1e-5, "angular speed zero at the start");
            assertEquals(0, (a1 - DeathCam.azimuth(s, run - h, run)) / h, 1e-5, "angular speed zero at the end");
            double prev = a0;
            for (double t = 0; t <= run; t += 0.05) { double x = DeathCam.azimuth(s, t, run); assertTrue((x - prev) * s.direction() >= -1e-12, "monotone"); prev = x; }
            assertEquals(0.0, DeathCam.smoothstep(0), 1e-12);
            assertEquals(1.0, DeathCam.smoothstep(1), 1e-12);
            assertEquals(0.5, DeathCam.smoothstep(0.5), 1e-12);
        }
    }

    @Test
    void theCameraAlwaysLooksAtTheLookAtPointAndHoldsAfterTheMove() {
        DeathCam.Setup s = setup(10, 70, -20, 200, 3, 10, 9);
        double run = 8;
        for (double t = 0; t <= run + 5; t += 0.1) {
            DeathCam.Pose p = DeathCam.pose(s, t, run, null);
            double yaw = Math.toRadians(p.yaw()), pitch = Math.toRadians(p.pitch());
            double[] look = {-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)};   // Minecraft's view vector
            double[] to = {s.lookX() - p.x(), s.lookY() - p.y(), s.lookZ() - p.z()};
            double l = Math.sqrt(to[0] * to[0] + to[1] * to[1] + to[2] * to[2]);
            for (int i = 0; i < 3; i++) assertEquals(to[i] / l, look[i], 1e-5, "t=" + t + " axis " + i);
            assertTrue(p.pitch() > 0, "the camera is above the point it looks at: it looks down");
        }
        assertEquals(70 + 0.25 * 100, s.lookY(), 1e-9, "LookHeight 0.25 x radius above the spot (huge: 100)");
        DeathCam.Pose end = DeathCam.pose(s, run, run, null), hold = DeathCam.pose(s, run + 30, run, null);
        assertEquals(end, hold, "after the move the pose no longer changes");
        assertEquals(1.0, end.progress(), 1e-12);
    }

    @Test
    void theOrbitStepPerTickIsSmallAndZeroAtBothEnds() {
        StringBuilder out = new StringBuilder();
        for (int size = 0; size < 4; size++) {
            DeathCam.Setup s = setup(0, 64, 0, 0, size, 10, 3);
            for (double run : new double[]{6, 20}) {
                double max = 0;
                DeathCam.Pose prev = DeathCam.pose(s, 0, run, null);
                double first = 0, last = 0;
                for (int k = 1; k <= run * 20 + 10; k++) {
                    DeathCam.Pose p = DeathCam.pose(s, k * 0.05, run, null);
                    double d = DeathCam.step(prev, p);
                    if (k == 1) first = d;
                    if (k <= run * 20) { max = Math.max(max, d); last = d; }
                    prev = p;
                }
                out.append(String.format("camera numbers: %s radius %.0f run %.0f s: at most %.3f blocks per tick (%.1f blocks/s), first step %.5f, last step %.5f%n", new String[]{"small", "medium", "large", "huge"}[size], s.radius(), run, max, max * 20, first, last));
                assertTrue(first < max * 0.05 && last < max * 0.05, "the steps start and end near zero");
                double peak = 1.5 * s.radius() * Math.toRadians(120) / (run * 20);
                assertEquals(peak, max, peak * 0.02, "the peak step of a smoothstep is 1.5 x the mean step: no jump");
            }
        }
        System.out.print(out);
    }

    @Test
    void everyEndOwesTheRightThingToThePlayer() {
        Set<CameraLifecycle.EndPath> noSend = EnumSet.of(CameraLifecycle.EndPath.RESPAWN, CameraLifecycle.EndPath.LOGOFF, CameraLifecycle.EndPath.PLAYER_GONE, CameraLifecycle.EndPath.SEND_FAILED);
        for (CameraLifecycle.EndPath p : CameraLifecycle.EndPath.values()) assertEquals(!noSend.contains(p), CameraLifecycle.sendsViewBack(p), p.toString());
        for (CameraLifecycle.EndPath p : new CameraLifecycle.EndPath[]{CameraLifecycle.EndPath.TIMEOUT, CameraLifecycle.EndPath.SERVER_STOP, CameraLifecycle.EndPath.DIMENSION, CameraLifecycle.EndPath.EVENT_END, CameraLifecycle.EndPath.STOP_COMMAND, CameraLifecycle.EndPath.PREVIEW_OVER, CameraLifecycle.EndPath.ENTITY_REMOVED, CameraLifecycle.EndPath.NEW_CAMERA})
            assertTrue(CameraLifecycle.sendsViewBack(p), "the same connected player gets their own view back: " + p);
        assertEquals(12, CameraLifecycle.EndPath.values().length, "respawn, log-off, gone, send failed, dimension, timeout, server stop, event end, stop, preview over, removed, new camera");
    }
}
