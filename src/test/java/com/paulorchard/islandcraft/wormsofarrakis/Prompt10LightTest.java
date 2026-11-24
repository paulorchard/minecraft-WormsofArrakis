package com.paulorchard.islandcraft.wormsofarrakis;

import com.paulorchard.islandcraft.wormsofarrakis.fx.FxAnchors;
import com.paulorchard.islandcraft.wormsofarrakis.fx.FxPlayer.FxGeometry;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.HeadMouth;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ScaleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ShingleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.WormMath;
import com.paulorchard.islandcraft.wormsofarrakis.light.LightMath;
import com.paulorchard.islandcraft.wormsofarrakis.light.LightProbe;
import com.paulorchard.islandcraft.wormsofarrakis.light.LightWatch;
import com.paulorchard.islandcraft.wormsofarrakis.light.QuadCheck;
import com.paulorchard.islandcraft.wormsofarrakis.path.HeadSpec;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;
import com.paulorchard.islandcraft.wormsofarrakis.path.SurfaceProvider;
import com.paulorchard.islandcraft.wormsofarrakis.path.WormPose;
import com.paulorchard.islandcraft.wormsofarrakis.path.WormSkin;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveFactory;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveOptions;
import com.paulorchard.islandcraft.wormsofarrakis.test.TestFlags;
import com.paulorchard.islandcraft.wormsofarrakis.test.WormSpec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** Prompt 10 part 3: the light listener, the probe-based light, and the normals check. */
class Prompt10LightTest {
    // ---- the classifier ----

    private static LightWatch.Sample sample(long tick, int vb, int vs, boolean solid, int... refMax) {
        List<LightWatch.Ref> refs = new ArrayList<>();
        String[] names = {"hole", "entry", "top", "mid", "tip"};
        for (int i = 0; i < refMax.length; i++) refs.add(new LightWatch.Ref(names[i], 0, refMax[i]));
        return new LightWatch.Sample(tick, 7, "crash W16", "probe", vb, vs, vb, vs, 10, 63, 10, solid ? "minecraft:sand" : "minecraft:air", solid, "minecraft:overworld", 6000, 0, 10.5, 63.85, 10.5, 63, 20, refs);
    }

    @Test
    void theClassifierTellsTheFourCasesApart() {
        assertEquals(LightWatch.Verdict.NOT_DARK, LightWatch.classify(sample(0, 0, 15, true, 15, 15, 15)), "sky light 15 at the probe");
        assertEquals(LightWatch.Verdict.NOT_DARK, LightWatch.classify(sample(0, 3, 0, false, 15)), "a dim probe is not dark (above 2)");
        assertEquals(LightWatch.Verdict.PROBE_IN_SOLID, LightWatch.classify(sample(0, 0, 0, true, 0, 15, 15, 12, 9)), "dark probe in a solid block, bright air around");
        assertEquals(LightWatch.Verdict.PROBE_DARK_AIR, LightWatch.classify(sample(0, 1, 2, false, 0, 15, 15)), "dark probe in air, bright air around");
        assertEquals(LightWatch.Verdict.REFERENCE_ALSO_DARK, LightWatch.classify(sample(0, 0, 0, false, 1, 2, 0, 3, 7)), "every reference is dark: night");
        assertEquals(LightWatch.Verdict.PROBE_DARK_AIR, LightWatch.classify(sample(0, 0, 0, false, 8)), "8 is bright enough to warn");
        assertEquals(LightWatch.Verdict.REFERENCE_ALSO_DARK, LightWatch.classify(sample(0, 0, 0, false, 7)), "7 is not");
        assertTrue(LightWatch.Verdict.PROBE_IN_SOLID.warn() && LightWatch.Verdict.PROBE_DARK_AIR.warn());
        assertFalse(LightWatch.Verdict.REFERENCE_ALSO_DARK.warn() || LightWatch.Verdict.NOT_DARK.warn());
        String d = LightWatch.describe(sample(5, 0, 0, true, 0, 15, 15));
        assertTrue(d.contains("worm 7 crash W16") && d.contains("vanilla=b0/s0") && d.contains("solid=true") && d.contains("refs[hole=b0/s0"), d);
    }

    @Test
    void theWarningIsRateLimitedAndTheLogWritesChangesOnly() {
        LightWatch.Tracker t = new LightWatch.Tracker();
        int warns = 0, infos = 0;
        for (long tick = 0; tick < 400; tick += 4) {   // 20 s of samples, always dark with a bright reference
            for (LightWatch.Line l : t.accept(sample(tick, 0, 0, true, 0, 15, 15, 15, 15), LightWatch.LogMode.ON)) {
                if (l.warn()) {
                    warns++;
                    assertTrue(l.text().contains("light: DARK verdict=probe-in-solid"), l.text());
                    if (warns > 1) assertTrue(l.text().contains("(24 warnings suppressed since the last one)"), l.text());
                } else infos++;
            }
        }
        assertEquals(4, warns, "one per 5 s (100 ticks) per worm over 20 s");
        assertEquals(1, infos, "the info line only when the light changes: the first sample");
        assertEquals(96, t.suppressedTotal());
        // a change writes a line, an unchanged sample does not; all writes every sample
        LightWatch.Tracker u = new LightWatch.Tracker();
        assertEquals(1, u.accept(sample(0, 0, 15, false, 15), LightWatch.LogMode.ON).size());
        assertEquals(0, u.accept(sample(4, 0, 15, false, 15), LightWatch.LogMode.ON).size());
        assertEquals(1, u.accept(sample(8, 0, 14, false, 15), LightWatch.LogMode.ON).size());
        assertEquals(1, u.accept(sample(12, 0, 14, false, 15), LightWatch.LogMode.ALL).size());
        assertEquals(0, u.accept(sample(16, 0, 3, false, 15), LightWatch.LogMode.OFF).size());
    }

    @Test
    void theRingBufferKeepsTheLast200Samples() {
        LightWatch.Tracker t = new LightWatch.Tracker();
        for (int i = 0; i < 350; i++) t.accept(sample(i * 4L, 0, 15, false, 15), LightWatch.LogMode.OFF);
        List<LightWatch.Sample> s = t.samples();
        assertEquals(200, s.size());
        assertEquals(150 * 4L, s.get(0).tick());
        assertEquals(349 * 4L, s.get(199).tick());
        assertEquals(349 * 4L, t.last().tick());
    }

    // ---- the points ----

    private static Moves.Move move(String mv, double w) {
        WormSpec s = mv.equals("crest") ? Prompt10Test.crest(w, 1.3, "") : MoveOptions.parse(mv, w, mv.equals("crash") ? (w <= 6 ? 60 : w <= 16 ? 120 : 300) : (w <= 16 ? 60 : 300), "", false).spec();
        return MoveFactory.get(s);
    }

    @Test
    void theSamplePointsAreNeverInsideTheBodyNorBelowTheBaseLevel() {
        int checked = 0;
        for (String mv : new String[]{"crash", "emerge", "engulf", "crest"}) {
            for (double w : new double[]{6, 16, 48}) {
                Moves.Move m = move(mv, w);
                FxAnchors.Anchors a = FxAnchors.of(m, 1.0, 0);
                for (double t = 0; t < m.cycle; t += 0.25) {
                    List<LightProbe.Pt> pts = LightProbe.points(m, t, a.holeZ());
                    FxGeometry.Snapshot g = FxGeometry.at(m, t);
                    assertFalse(pts.isEmpty());
                    assertTrue(pts.size() <= LightMath.LIGHT_SAMPLES, "at most 8 points");
                    for (LightProbe.Pt p : pts) {
                        String ctx = mv + " W" + w + " t=" + t + " " + p;
                        assertTrue(p.y() >= 1 - 1e-9, "never below the base level + 1: " + ctx);
                        double d = Math.hypot(p.x(), LightProbe.distToAxis(g, p.y(), p.z()));
                        assertTrue(d > m.r0 + 0.5 - 1e-6, "distance from the axis " + d + " > r0 + 0.5: " + ctx);
                        checked++;
                    }
                }
            }
        }
        assertTrue(checked > 3000, "points checked: " + checked);
    }

    @Test
    void withNoExposedBodyTheHoleIsTheOnlyPoint() {
        Moves.Move m = move("crash", 16);
        FxAnchors.Anchors a = FxAnchors.of(m, 1.0, 0);
        List<LightProbe.Pt> pts = LightProbe.points(m, 0.1, a.holeZ());   // the head is far under the sand
        assertEquals(1, pts.size());
        assertEquals("hole", pts.get(0).name());
        assertEquals(1.0, pts.get(0).y(), 1e-9, "one block above the base level");
        // while the worm stands in the open there are 8 points and the five references are marked
        List<LightProbe.Pt> up = LightProbe.points(m, a.fallStart() - 0.2, a.holeZ());
        assertEquals(LightMath.LIGHT_SAMPLES, up.size());
        assertEquals(5, up.stream().filter(LightProbe.Pt::ref).count());
    }

    // ---- a fake world: the entity probe is inside solid sand, the air around is bright ----

    private static LightProbe.LightGrid slope(int groundTop) {
        return new LightProbe.LightGrid() {
            @Override public int block(int x, int y, int z) { return 0; }
            @Override public int sky(int x, int y, int z) { return y > groundTop ? 15 : 0; }
            @Override public boolean solid(int x, int y, int z) { return y <= groundTop; }
        };
    }

    @Test
    void theProbeFindsTheLightThatVanillaMissesOnASlope() {
        Moves.Move m = move("crash", 16);
        FxAnchors.Anchors a = FxAnchors.of(m, 1.0, 0);
        double t = a.fallStart() - 0.2;
        // the worm was spawned at y = 63 on ground that rises to 66 under it: the entity origin and its eye (63.85) are inside solid blocks
        LightProbe.Frame f = new LightProbe.Frame(10.5, 63, 10.5, 0.7);
        LightProbe.LightGrid g = slope(65);
        int vanillaSky = g.sky(10, 63, 10);
        assertEquals(0, vanillaSky, "the light vanilla reads at the eye is 0: a black worm");
        assertTrue(g.solid(10, 63, 10));
        LightProbe.Result r = LightProbe.sample(g, f, 63, LightProbe.points(m, t, a.holeZ()));
        assertEquals(15, LightMath.sky(r.packed()), "one lit point lights the whole worm");
        assertEquals(15, r.bestRef());
        assertTrue(r.readings().stream().anyMatch(LightProbe.Reading::lifted), "a point inside the slope is lifted into the open");
    }

    @Test
    void probeAndVanillaAgreeOnAFlatWorldInTheOpen() {
        Moves.Move m = move("crash", 16);
        FxAnchors.Anchors a = FxAnchors.of(m, 1.0, 0);
        LightProbe.LightGrid flat = new LightProbe.LightGrid() {
            @Override public int block(int x, int y, int z) { return 0; }
            @Override public int sky(int x, int y, int z) { return 15; }
            @Override public boolean solid(int x, int y, int z) { return y < 63; }
        };
        LightProbe.Frame f = new LightProbe.Frame(0.5, 63, 0.5, 1.2);
        int vanilla = LightMath.pack(flat.block(0, 63, 0), flat.sky(0, 63, 0));
        for (double t = a.breach(); t < a.reentry(); t += 0.3) assertEquals(vanilla, LightProbe.sample(flat, f, 63, LightProbe.points(m, t, a.holeZ())).packed(), "t=" + t);
    }

    // ---- the combination, the smoothing, the modes ----

    @Test
    void theMaxCombineTakesEachChannelOnItsOwn() {
        int[] p = {LightMath.pack(3, 9), LightMath.pack(12, 2), LightMath.pack(0, 15), LightMath.pack(7, 7)};
        assertEquals(LightMath.pack(12, 15), LightMath.maxCombine(p, 4));
        assertEquals(LightMath.pack(12, 9), LightMath.maxCombine(p, 2));
        assertEquals(0, LightMath.maxCombine(p, 0));
        assertEquals(15, LightMath.block(LightMath.FULL));
        assertEquals(15, LightMath.sky(LightMath.FULL));
        assertEquals(15728880, LightMath.FULL, "the game's FULL_BRIGHT");
    }

    @Test
    void theSmoothingMovesAtMostOneLevelPerTickAndConverges() {
        LightMath.Smoother s = new LightMath.Smoother();
        s.target(LightMath.pack(0, 0));
        assertEquals(0, s.tick());
        s.target(LightMath.pack(10, 15));
        int prevB = 0, prevS = 0, ticks = 0;
        while (s.packed() != LightMath.pack(10, 15)) {
            int p = s.tick();
            assertTrue(LightMath.block(p) - prevB <= 1 && LightMath.sky(p) - prevS <= 1 && LightMath.block(p) >= prevB && LightMath.sky(p) >= prevS);
            prevB = LightMath.block(p);
            prevS = LightMath.sky(p);
            assertTrue(++ticks <= 15, "converges within 15 ticks");
        }
        assertEquals(15, ticks);
        s.target(LightMath.pack(4, 3));
        int t2 = 0;
        while (s.packed() != LightMath.pack(4, 3)) {
            int before = s.packed();
            int now = s.tick();
            assertTrue(LightMath.block(before) - LightMath.block(now) <= 1 && LightMath.sky(before) - LightMath.sky(now) <= 1);
            t2++;
        }
        assertEquals(12, t2);
        assertEquals(5, LightMath.step(4, 9));
        assertEquals(8, LightMath.step(9, 0));
    }

    @Test
    void theModesChooseTheLight() {
        LightMath.Smoother s = new LightMath.Smoother();
        int vanilla = LightMath.pack(2, 3);
        assertEquals(vanilla, LightMath.choose(LightMath.Mode.PROBE, vanilla, s), "probe before the first sample is vanilla");
        s.target(LightMath.pack(1, 14));
        assertEquals(LightMath.pack(1, 14), LightMath.choose(LightMath.Mode.PROBE, vanilla, s));
        assertEquals(vanilla, LightMath.choose(LightMath.Mode.VANILLA, vanilla, s), "light=vanilla returns exactly the input");
        for (int v : new int[]{0, 1, 15728880, 0x00F000F0, 0x00100020}) assertEquals(v, LightMath.choose(LightMath.Mode.VANILLA, v, s));
        assertEquals(LightMath.pack(15, 15), LightMath.choose(LightMath.Mode.FULL, vanilla, s), "light=full is 15/15");
        assertEquals(LightMath.Mode.FULL, LightMath.Mode.byLabel("full"));
        assertNull(LightMath.Mode.byLabel("dim"));
    }

    @Test
    void theLightKeyIsAMoveKeyAndTheDefaultSpecKeepsItsLength() {
        for (String line : MoveOptions.lines()) assertTrue(line.contains("light=probe"), line);
        WormSpec base = MoveOptions.parse("crash", 16, 120, "", false).spec();
        assertTrue(base.p().length <= 21);
        assertEquals(LightMath.Mode.PROBE, MoveFactory.lightMode(base));
        assertEquals(LightMath.Mode.VANILLA, MoveFactory.lightMode(MoveOptions.parse("crash", 16, 120, "light=vanilla", false).spec()));
        WormSpec full = MoveOptions.parse("crest", 16, 80, "light=full height=1.3", false).spec();
        assertEquals(LightMath.Mode.FULL, MoveFactory.lightMode(full));
        assertTrue(MoveFactory.fxOptions(full).on() && MoveFactory.fxOptions(full).eruption(), "the fx fields keep their defaults when light is set");
        assertEquals(MoveFactory.fxOptions(MoveOptions.parse("crest", 16, 80, "height=1.3", false).spec()), MoveFactory.fxOptions(full));
        WormSpec both = MoveOptions.parse("crash", 16, 120, "light=full fxvol=0.5 eruption=off", false).spec();
        assertEquals(LightMath.Mode.FULL, MoveFactory.lightMode(both));
        assertEquals(0.5, MoveFactory.fxOptions(both).vol());
        assertFalse(MoveFactory.fxOptions(both).eruption());
        assertSame(MoveFactory.get(base), MoveFactory.get(full == null ? base : MoveOptions.parse("crash", 16, 120, "light=full", false).spec()), "no key of light changes the path");
        assertThrows(MoveOptions.Bad.class, () -> MoveOptions.parse("crash", 16, 120, "light=dim", false));
        for (String mv : MoveOptions.MOVES) assertTrue(MoveOptions.usage(mv).contains("[light=probe]"));
    }

    // ---- H4: the normals ----

    private static float[] quad(float[] p, float[] n) {
        float[] v = new float[32];
        for (int k = 0; k < 4; k++) {
            v[k * 8] = p[k * 3];
            v[k * 8 + 1] = p[k * 3 + 1];
            v[k * 8 + 2] = p[k * 3 + 2];
            v[k * 8 + 5] = n[0];
            v[k * 8 + 6] = n[1];
            v[k * 8 + 7] = n[2];
        }
        return v;
    }

    @Test
    void theNormalsCheckCountsAZeroAreaQuadAZeroNormalAndANaN() {
        float[] good = quad(new float[]{0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0}, new float[]{0, 0, 1});
        float[] collapsed = quad(new float[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, new float[]{0, 0, 1});
        float[] line = quad(new float[]{0, 0, 0, 1, 0, 0, 2, 0, 0, 3, 0, 0}, new float[]{0, 0, 1});
        float[] zeroN = quad(new float[]{0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0}, new float[]{0, 0, 0});
        float[] nan = quad(new float[]{0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0}, new float[]{0, 0, 1});
        nan[9] = Float.NaN;
        float[] nanNormal = quad(new float[]{0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0}, new float[]{Float.NaN, 0, 1});
        float[] all = new float[32 * 6];
        float[][] qs = {good, collapsed, line, zeroN, nan, nanNormal};
        for (int i = 0; i < qs.length; i++) System.arraycopy(qs[i], 0, all, i * 32, 32);
        QuadCheck.Count c = QuadCheck.check(all, 6);
        assertEquals(6, c.quads());
        assertEquals(1, c.zeroNormal(), "the zero normal");
        assertEquals(2, c.tinyArea(), "the collapsed quad and the degenerate line");
        assertEquals(2, c.nan(), "a NaN position and a NaN normal");
        assertEquals(5, c.bad());
        assertNull(QuadCheck.line("shell", "x", QuadCheck.check(good, 1)), "silent when the count is 0");
        assertTrue(QuadCheck.line("throat", "W16", c).contains("mesh check: throat (W16): 5 of 6"));
        assertEquals(0, QuadCheck.check(new float[0], 0).bad());
    }

    @Test
    void aBuiltMouthHasNoDegenerateNormalsClosedOrOpen() throws IOException {
        StringBuilder b = new StringBuilder();
        for (double w : new double[]{6, 16, 48}) {
            Moves.Move m = MoveFactory.get(MoveOptions.parse("crash", w, w <= 6 ? 60 : w <= 16 ? 120 : 300, "", false).spec());
            int n = WormMath.faceCount(m.width);
            ShingleMath.Options opt = ShingleMath.Options.defaults(ScaleMath.LIP_LENGTH, true).withVariety(TestFlags.stagger, 7L, TestFlags.offset, TestFlags.jitter);
            double delta0 = WormSkin.headDelta(opt, n);
            for (boolean teeth : new boolean[]{true, false}) {
                HeadMouth.Mesh thr = HeadMouth.throat(m.width, n, m.r0, delta0, teeth);
                check(b, "throat", w, teeth, -1, thr.flat);
                check(b, "teeth(throat)", w, teeth, -1, thr.bristles);
                for (double deg : new double[]{0.0, 5.0, 20.0, HeadSpec.OPEN_DEGREES}) {
                    HeadMouth.Mesh mand = HeadMouth.mandibles(m.width, n, m.r0, delta0, HeadSpec.coneHeight(m.width), deg, opt, teeth);
                    check(b, "shell", w, teeth, deg, mand.shell);
                    check(b, "teeth(mandibles)", w, teeth, deg, mand.bristles);
                }
            }
            // the body: a few instants of the crash
            FxAnchors.Anchors a = FxAnchors.of(m, 1.0, 0);
            for (double t : new double[]{a.breach() + 0.3, a.riseEnd(), a.fallStart() + 0.3, a.reentry() - 0.2}) {
                WormPose pose = m.pose(m.at(t)[0]);
                WormSkin skin = WormSkin.build(pose, n, m.r0, SurfaceProvider.PLANE, opt, 0.0);
                QuadCheck.Count c = QuadCheck.check(skin.result.v, skin.result.quads);
                b.append(String.format(Locale.ROOT, "body crash W%.0f t=%.2f: %d quads, degenerate %d (zero normal %d, tiny area %d, NaN %d)%n", w, t, c.quads(), c.bad(), c.zeroNormal(), c.tinyArea(), c.nan()));
                assertEquals(0, c.bad(), "body W" + w + " t=" + t);
            }
        }
        Path out = Path.of("build/prompt10-light-numbers.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString());
    }

    private static void check(StringBuilder b, String part, double w, boolean teeth, double deg, ShingleMath.Result r) {
        QuadCheck.Count c = QuadCheck.check(r.v, r.quads);
        b.append(String.format(Locale.ROOT, "mouth W%.0f %s teeth=%s angle=%s: %d quads, degenerate %d (zero normal %d, tiny area %d, NaN %d)%n", w, part, teeth, deg < 0 ? "-" : String.valueOf(deg), c.quads(), c.bad(), c.zeroNormal(), c.tinyArea(), c.nan()));
        assertEquals(0, c.bad(), part + " W" + w + " teeth=" + teeth + " angle=" + deg);
    }
}
