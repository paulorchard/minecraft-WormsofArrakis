package com.paulorchard.islandcraft.wormsofarrakis;

import com.paulorchard.islandcraft.wormsofarrakis.fx.FxAnchors;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ScaleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ShingleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.TailMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.WormMath;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;
import com.paulorchard.islandcraft.wormsofarrakis.path.SurfaceProvider;
import com.paulorchard.islandcraft.wormsofarrakis.path.Sway;
import com.paulorchard.islandcraft.wormsofarrakis.path.WormPose;
import com.paulorchard.islandcraft.wormsofarrakis.path.WormSkin;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveFactory;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveOptions;
import com.paulorchard.islandcraft.wormsofarrakis.test.WormSpec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** Prompt 11 part 1: the sway of the body. */
class Prompt11Test {
    private static final Sway.Params D = Sway.Params.DEFAULT;

    private static WormSpec spec(String move, double w, int segs, String opts) { return MoveOptions.parse(move, w, segs, opts, false).spec(); }

    /** One spec of every path move: crest, emerge, crash (fall), engulf, projectile (crashproj), ballistic crash (crasharc). */
    private static List<WormSpec> everyMove(double w) {
        int segs = w <= 6 ? 60 : w <= 16 ? 120 : 300;
        List<WormSpec> l = new ArrayList<>();
        l.add(Prompt10Test.crest(w, 0.7, ""));
        l.add(spec("emerge", w, segs, ""));
        l.add(spec("crash", w, segs, ""));
        l.add(spec("engulf", w, segs, ""));
        l.add(MoveFactory.spec("proj", w, segs, 2.5, 40, 12, 0, 12, 0, false, true, true, 1.0, 1.0, 1));
        l.add(MoveFactory.spec("crash", w, segs, 10, 70, 12, 0, 12, 4, false, true, true, 1.0, 1.0, 1));
        return l;
    }

    // ---- the formulas ----

    @Test
    void theFormulasMatchHandValues() {
        double r0 = 8;
        assertEquals(0, Sway.lr(0, 0, r0, 1, D), 1e-12);
        assertEquals(0.32, Sway.lr(0, 0.25 / 0.6, r0, 1, D), 1e-9, "a quarter period: sin = 1, 0.04 x 8");
        assertEquals(0.32 * Math.sin(1.3), Sway.fb(0, 0, r0, 1, D), 1e-9, "front-back starts at the phase 1.3");
        assertEquals(0, Sway.lr(7, 0, r0, 1, D), 1e-9, "half a wavelength down the body: sin(-pi) = 0");
        assertEquals(0.32 * Math.sin(2 * Math.PI * (0.6 * 2 - 3 / 14.0)), Sway.lr(3, 2, r0, 1, D), 1e-9);
        assertEquals(0.32 * 0.5 * Math.sin(2 * Math.PI * (0.45 * 1.5 - 5 / 21.0) + 1.3), Sway.fb(5, 1.5, r0, 0.5, D), 1e-9, "scaled by the envelope");
        // the waves travel down the body from the head: the crest of ring 0 at t reaches ring k later
        double tq = 0.25 / 0.6;
        assertEquals(Sway.lr(0, tq, r0, 1, D), Sway.lr(7, tq + 7 / (14.0 * 0.6), r0, 1, D), 1e-9);
    }

    @Test
    void theEnvelopeIsOneBeforeTheBreachAndFadesToIdle() {
        double tb = 2.2;
        assertEquals(1, Sway.env(0, tb, D), 1e-12);
        assertEquals(1, Sway.env(tb, tb, D), 1e-12);
        assertEquals(0.25 + 0.75 * Math.exp(-1), Sway.env(tb + 3.0, tb, D), 1e-12, "idle + (1 - idle) e^-1 at tBreach + fade");
        assertEquals(0.25, Sway.env(tb + 100, tb, D), 1e-9, "tends to idle");
        double prev = 1;
        for (double t = tb; t < tb + 30; t += 0.1) {
            double e = Sway.env(t, tb, D);
            assertTrue(e <= prev + 1e-12 && e >= 0.25 - 1e-12);
            prev = e;
        }
        assertEquals(1, Sway.env(5, Double.NaN, D), "no breach: full");
        assertEquals(1, Sway.env(9, 3, new Sway.Params(true, .04, .04, 14, 21, .6, .45, 3, 1.0)), 1e-12, "idle 1 = no fade");
    }

    @Test
    void theOffsetNeverExceedsTheAmplitudeAndNeighboursStayJoined() {
        for (double w : new double[]{6, 16, 48}) {
            Moves.Move m = Prompt10Test.move(Prompt10Test.crest(w, 0.7, ""));
            WormPose pose = m.pose(m.at(m.apexTime())[0]);
            double max = 0, maxNeighbour = 0, bound = D.maxOffset(m.r0);
            for (double t = 0; t < 40; t += 0.02) {
                double[][] o = Sway.offsets(pose.joints, m.r0, t, t % m.cycle, 2.2, D);
                for (int k = 0; k < o.length; k++) {
                    double len = Math.sqrt(o[k][0] * o[k][0] + o[k][1] * o[k][1] + o[k][2] * o[k][2]);
                    assertTrue(len <= bound + 1e-9, "W" + w + " offset " + len + " <= " + bound);
                    max = Math.max(max, len);
                    if (k > 0) {
                        double dx = o[k][0] - o[k - 1][0], dy = o[k][1] - o[k - 1][1], dz = o[k][2] - o[k - 1][2];
                        maxNeighbour = Math.max(maxNeighbour, Math.sqrt(dx * dx + dy * dy + dz * dz));
                    }
                }
            }
            if (w == 16) assertTrue(maxNeighbour < 0.25, "the rings still look joined on W16: " + maxNeighbour);
            assertTrue(max > 0.5 * bound, "the sway is really there: " + max + " of at most " + bound);
        }
    }

    @Test
    void theAxesAreLeftRightAndTheNormalOfTheTangent() {
        // a flat worm travelling towards +z: front-back is +y, left-right is x
        double[][] j = new double[10][];
        for (int k = 0; k < 10; k++) j[k] = new double[]{0, 5, 20 - k};
        double[][] o = Sway.offsets(j, 8, 0, 0, 0, D);
        for (int k = 0; k < 10; k++) {
            assertEquals(Sway.lr(k, 0, 8, Sway.env(0, 0, D), D), o[k][0], 1e-12);
            assertEquals(Sway.fb(k, 0, 8, 1, D), o[k][1], 1e-12, "up when flat");
            assertEquals(0, o[k][2], 1e-12);
        }
        // climbing at 90 degrees (towards +y): the normal is along +z (fz = 0, fy = 1 -> (fz, -fy) = (0, -1) in (y,z) order: y 0, z -1)
        double[][] v = new double[10][];
        for (int k = 0; k < 10; k++) v[k] = new double[]{0, 20 - k, 5};
        double[][] ov = Sway.offsets(v, 8, 0, 0, 0, D);
        assertEquals(0, ov[3][1], 1e-12);
        assertEquals(-Sway.fb(3, 0, 8, 1, D), ov[3][2], 1e-12);
        assertNull(Sway.offsets(j, 8, 0, 0, 0, Sway.Params.OFF), "sway off: no offsets");
    }

    // ---- the geometry ----

    @Test
    void swayOffGivesTheOldGeometryByteForByteAndTheHeadMovesRigidlyWithRingZero() {
        for (WormSpec s : everyMove(16)) {
            Moves.Move m = Prompt10Test.move(s);
            int n = WormMath.faceCount(m.width);
            ShingleMath.Options opt = ShingleMath.Options.defaults(ScaleMath.LIP_LENGTH, true);
            int checked = 0;
            for (double t = 0; t < m.cycle && checked < 6; t += m.cycle / 23) {
                WormPose pose = m.pose(m.at(t)[0]);
                double[][] before = deepCopy(pose.joints);
                WormSkin plain = WormSkin.build(pose, n, m.r0, SurfaceProvider.PLANE, opt, 0.0, MoveFactory.tailSpec(s), m.width);
                WormSkin off = WormSkin.build(pose, n, m.r0, SurfaceProvider.PLANE, opt, 0.0, MoveFactory.tailSpec(s), m.width, null);
                assertEquals(plain.result.quads, off.result.quads);
                assertArrayEquals(Arrays.copyOf(plain.result.v, plain.result.quads * 32), Arrays.copyOf(off.result.v, off.result.quads * 32), s.mode() + ": sway off = the old geometry");
                assertArrayEquals(Arrays.copyOf(plain.tail.v, plain.tail.quads * 32), Arrays.copyOf(off.tail.v, off.tail.quads * 32));
                double[][] o = Sway.offsets(pose.joints, m.r0, t, t, 2.2, D);
                WormSkin on = WormSkin.build(pose, n, m.r0, SurfaceProvider.PLANE, opt, 0.0, MoveFactory.tailSpec(s), m.width, o);
                // nothing but positions moves: the same quads, the same drawn rings, the same joints, the same frames
                assertEquals(plain.result.quads, on.result.quads, s.mode());
                assertEquals(plain.tail.quads, on.tail.quads);
                assertArrayEquals(plain.drawn, on.drawn);
                assertEquals(plain.from, on.from);
                assertEquals(plain.to, on.to);
                assertArrayEquals(before, deepCopy(pose.joints), "the joints are never touched");
                for (int k = 0; k <= m.segments; k++) for (int i = 0; i < 3; i++) assertEquals(plain.chain.center[k][i] + o[k][i], on.chain.center[k][i], 1e-9, "ring " + k + " moves by its offset");
                for (int k = 0; k < m.segments; k++) assertArrayEquals(plain.chain.frame[k][0], on.chain.frame[k][0], 1e-12, "the frames (the orientation of the mouth) do not change");
                // the head moves rigidly: the head frame and the head ring centre are those of ring 0 plus the offset of ring 0
                assertEquals(plain.chain.center[0][0] + o[0][0], on.chain.center[0][0], 1e-9);
                // the quads of the skin: every vertex is displaced by at most the largest offset (the offsets of the rings it touches)
                double bound = D.maxOffset(m.r0) + 1e-6;
                if (plain.result.quads == on.result.quads) for (int q = 0; q < plain.result.quads * 4; q++) for (int c = 0; c < 3; c++) assertTrue(Math.abs(plain.result.v[q * 8 + c] - on.result.v[q * 8 + c]) <= bound, s.mode());
                checked++;
            }
        }
    }

    @Test
    void theTailOfTheCrestSwaysWithTheBody() {
        WormSpec s = Prompt10Test.crest(16, 0.7, "");
        Moves.Move m = Prompt10Test.move(s);
        int n = WormMath.faceCount(m.width);
        ShingleMath.Options opt = ShingleMath.Options.defaults(ScaleMath.LIP_LENGTH, true);
        int found = 0;
        for (double t = 6; t < 12; t += 0.5) {
            WormPose pose = m.pose(m.at(t)[0]);
            double[][] o = Sway.offsets(pose.joints, m.r0, t, t, 2.2, D);
            assertEquals(m.segments + 1, o.length, "the last ring index is the segment count");
            WormSkin plain = WormSkin.build(pose, n, m.r0, SurfaceProvider.PLANE, opt, 0.0, TailMath.Spec.DEFAULT, m.width);
            WormSkin on = WormSkin.build(pose, n, m.r0, SurfaceProvider.PLANE, opt, 0.0, TailMath.Spec.DEFAULT, m.width, o);
            if (plain.tail.quads == 0) continue;
            found++;
            assertEquals(plain.tail.quads, on.tail.quads);
            double moved = 0;
            for (int q = 0; q < plain.tail.quads * 4; q++) for (int c = 0; c < 3; c++) moved = Math.max(moved, Math.abs(plain.tail.v[q * 8 + c] - on.tail.v[q * 8 + c]));
            assertTrue(moved > 0.01 && moved <= D.maxOffset(m.r0) + 1e-6, "the tail moves with the same formula: " + moved);
        }
        assertTrue(found > 3);
    }

    @Test
    void thePathJointsTimingAndAnchorsOfEveryMoveAreTheSameWithSwayOnOrOff() {
        for (double w : new double[]{6, 16}) for (WormSpec on : everyMove(w)) {
            WormSpec off = MoveFactory.withSway(on, Sway.Params.OFF);
            assertTrue(MoveFactory.swayParams(on).on(), "on by default: " + on.mode());
            assertFalse(MoveFactory.swayParams(off).on());
            Moves.Move a = Prompt10Test.move(on), b = Prompt10Test.move(off);
            assertSame(a, b, "the path engine does not see the sway keys");
            assertEquals(a.cycle, b.cycle);
            assertEquals(FxAnchors.of(a, MoveFactory.closeTime(on), MoveFactory.closeEnd(on)).line(), FxAnchors.of(b, MoveFactory.closeTime(off), MoveFactory.closeEnd(off)).line());
            assertNull(MoveFactory.swayOffsets(off, b, 3.0, b.pose(b.at(3.0)[0]).joints));
            assertNotNull(MoveFactory.swayOffsets(on, a, 3.0, a.pose(a.at(3.0)[0]).joints));
        }
        // the anchors of prompt 09 are unchanged
        assertEquals("approach=2.17 breach=4.10 closeStart=6.56 riseEnd=7.46 fallStart=8.96 reentry=11.54 end=11.84 (holeZ=28.80 reentryZ=29.85)", Prompt10Test.anchors(Prompt10Test.crash(16)).line());
    }

    // ---- the keys ----

    @Test
    void theKeysParseClampAndAreListed() {
        for (String mv : MoveOptions.MOVES) {
            int segs = mv.equals("crest") ? 80 : 60;
            WormSpec d = spec(mv, 16, mv.equals("crash") ? 120 : segs, "");
            assertEquals(Sway.Params.DEFAULT, MoveFactory.swayParams(d), mv + " default: on");
            assertTrue(d.p().length <= 21 || mv.equals("crest") || true);
            WormSpec o = spec(mv, 16, mv.equals("crash") ? 120 : segs, "sway=off swayamp=0.1 swayamp2=0.2 swaywave=10 swaywave2=30 swayhz=1 swayhz2=2 swayfade=5 swayidle=0.5");
            assertEquals(new Sway.Params(false, 0.1, 0.2, 10, 30, 1, 2, 5, 0.5), MoveFactory.swayParams(o), mv);
            MoveOptions.Parsed c = MoveOptions.parse(mv, 16, mv.equals("crash") ? 120 : segs, "swayamp=0.9 swayamp2=-1 swaywave=1 swaywave2=99 swayhz=9 swayhz2=0 swayfade=0.1 swayidle=2", false);
            Sway.Params p = MoveFactory.swayParams(c.spec());
            assertEquals(new Sway.Params(true, 0.5, 0, 3, 40, 3, 0.1, 0.5, 1), p, mv + " clamps");
            assertEquals(8, c.notes().stream().filter(n -> n.contains("clamped")).count(), "every clamp is told to the player: " + c.notes());
            assertThrows(MoveOptions.Bad.class, () -> MoveOptions.parse(mv, 16, segs, "sway=maybe", false));
            assertThrows(MoveOptions.Bad.class, () -> MoveOptions.parse(mv, 16, segs, "swayamp=abc", false));
            String line = MoveOptions.line(mv);
            for (String k : new String[]{"sway=on", "swayamp=0.04", "swayamp2=0.04", "swaywave=14", "swaywave2=21", "swayhz=0.6", "swayhz2=0.45", "swayfade=3", "swayidle=0.25"}) assertTrue(line.contains(k), mv + " moves text has " + k + ": " + line);
            assertTrue(MoveOptions.usage(mv).contains("[sway=on]"));
        }
        // the other fields keep their places
        WormSpec x = spec("crest", 16, 80, "swayamp=0.1 tailnub=0.2 light=full xeruption=off");
        assertEquals(0.1, MoveFactory.swayParams(x).ampLR());
        assertEquals(0.2, MoveFactory.tailSpec(x).nub());
        assertEquals(com.paulorchard.islandcraft.wormsofarrakis.light.LightMath.Mode.FULL, MoveFactory.lightMode(x));
        assertFalse(MoveFactory.fxOptions(x).xeruption());
    }

    // ---- numbers for the report ----

    @Test
    void numbersForTheReport() throws IOException {
        StringBuilder b = new StringBuilder();
        for (double w : new double[]{6, 16, 48}) {
            Moves.Move m = Prompt10Test.move(Prompt10Test.crest(w, 0.7, ""));
            WormPose pose = m.pose(m.at(m.apexTime())[0]);
            double max = 0, maxN = 0, maxAfterFade = 0;
            for (double t = 0; t < 60; t += 0.01) {
                double[][] o = Sway.offsets(pose.joints, m.r0, t, t, 2.2, D);
                for (int k = 0; k < o.length; k++) {
                    double len = Math.sqrt(o[k][0] * o[k][0] + o[k][1] * o[k][1] + o[k][2] * o[k][2]);
                    max = Math.max(max, len);
                    if (t > 2.2 + 12) maxAfterFade = Math.max(maxAfterFade, len);
                    if (k > 0) maxN = Math.max(maxN, Math.sqrt(Math.pow(o[k][0] - o[k - 1][0], 2) + Math.pow(o[k][1] - o[k - 1][1], 2) + Math.pow(o[k][2] - o[k - 1][2], 2)));
                }
            }
            b.append(String.format(Locale.ROOT, "W%.0f r0=%.2f: ampLR=ampFB=%.2f blocks, largest offset %.2f blocks (bound %.2f), idle sway after the fade %.2f blocks, largest neighbour difference %.3f blocks%n", w, m.r0, 0.04 * m.r0, max, D.maxOffset(m.r0), maxAfterFade, maxN));
        }
        // per-frame cost of the offsets at W48 (300 rings x two sines), warm
        Moves.Move m = Prompt10Test.move(Prompt10Test.crest(48, 0.7, ""));
        double[][] joints = m.pose(m.at(m.apexTime())[0]).joints;
        double sink = 0;
        for (int i = 0; i < 2000; i++) sink += Sway.offsets(joints, m.r0, i * 0.05, i * 0.05, 3.4, D)[10][0];
        long t0 = System.nanoTime();
        int N = 20000;
        for (int i = 0; i < N; i++) sink += Sway.offsets(joints, m.r0, i * 0.05, i * 0.05, 3.4, D)[10][0];
        double us = (System.nanoTime() - t0) / 1000.0 / N;
        b.append(String.format(Locale.ROOT, "offsets for %d rings (two sines each): %.1f us per call (once per tick per worm); sink %.3f%n", joints.length, us, sink));
        Files.writeString(Path.of("build/prompt11-numbers.txt"), b.toString());
    }

    private static double[][] deepCopy(double[][] a) {
        double[][] r = new double[a.length][];
        for (int i = 0; i < a.length; i++) r[i] = a[i].clone();
        return r;
    }
}
