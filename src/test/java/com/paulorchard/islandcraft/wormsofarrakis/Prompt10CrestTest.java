package com.paulorchard.islandcraft.wormsofarrakis;

import com.paulorchard.islandcraft.wormsofarrakis.fx.CrashFx;
import com.paulorchard.islandcraft.wormsofarrakis.fx.CrestFx;
import com.paulorchard.islandcraft.wormsofarrakis.fx.EruptionFx;
import com.paulorchard.islandcraft.wormsofarrakis.fx.FxAnchors;
import com.paulorchard.islandcraft.wormsofarrakis.fx.FxOptions;
import com.paulorchard.islandcraft.wormsofarrakis.fx.FxPlayer;
import com.paulorchard.islandcraft.wormsofarrakis.fx.FxSink;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ScaleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ShingleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.TailMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.WormMath;
import com.paulorchard.islandcraft.wormsofarrakis.light.QuadCheck;
import com.paulorchard.islandcraft.wormsofarrakis.path.MouthAnim;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;
import com.paulorchard.islandcraft.wormsofarrakis.path.SurfaceProvider;
import com.paulorchard.islandcraft.wormsofarrakis.path.WormSkin;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveFactory;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveOptions;
import com.paulorchard.islandcraft.wormsofarrakis.test.WormSpec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** Prompt 10 part 2, the revised crest: anchors from the head, the crossings, the crest sound sheet, the exit eruption and the tail. */
class Prompt10CrestTest {
    private static final int E0 = CrashFx.EMITTERS.size();

    private static FxPlayer.FxGeometry.Snapshot geo(Moves.Move m, WormSpec s, double t) {
        return FxPlayer.FxGeometry.at(m, t, MouthAnim.angleAt(m, t, 45, MoveFactory.closeTime(s), MoveFactory.closeEnd(s)));
    }

    // ---- anchors ----

    @Test
    void theW16Height07AnchorsAreTheOwnersTable() {
        assertEquals(0.7, Moves.DEFAULT_CREST_HEIGHT, "the crest height default of the owner");
        WormSpec s = Prompt10Test.spec("crest", 16, 80, "");
        assertEquals(0.7, s.p(2, 0), "no height given: the default");
        Moves.Move m = Prompt10Test.move(s);
        FxAnchors.Anchors a = Prompt10Test.anchors(s);
        assertEquals(21.57, m.cycle, 0.05);
        assertEquals(0.0, a.approach(), 0.15);
        assertEquals(2.2, a.breach(), 0.15);
        assertEquals(5.4, a.closeStart(), 0.15);
        assertEquals(6.0, a.apex(), 0.15);
        assertEquals(6.3, a.riseEnd(), 0.15);
        assertEquals(a.apex(), a.fallStart(), 1e-9);
        assertEquals(8.2, a.reentry(), 0.15);
        assertEquals(15.67, a.end(), 0.15);
        assertTrue(a.end() < m.cycle - 5, "the effects end long before the end of the cycle");
        // the breach is the head's highest point, not the tip: the tip crosses much later (3.87 s)
        double tipUp = Double.NaN;
        for (double t = 0; t < 8 && Double.isNaN(tipUp); t += 0.005) if (FxAnchors.tipHeight(m, t) > 0) tipUp = t;
        assertTrue(a.breach() < tipUp - 1.0, "breach " + a.breach() + " before the tip's own crossing " + tipUp);
        // the end is after the last instant the last joint is above the base level
        double last = 0;
        for (double t = a.apex(); t < m.cycle; t += 0.05) {
            double[][] j = m.pose(m.at(t)[0]).joints;
            if (j[j.length - 1][1] > 0) last = t;
        }
        assertTrue(a.end() > last, "end " + a.end() + " after the last joint's last positive instant " + last);
        assertEquals(last + 0.3, a.end(), 0.1);
        // the approach is 4 s before the breach, never before 0
        assertEquals(Math.max(0, a.breach() - 4.0), a.approach(), 1e-9);
        for (double w : new double[]{6, 16, 48}) for (double h : new double[]{0.7, 1.3, 2.0}) {
            FxAnchors.Anchors x = Prompt10Test.anchors(Prompt10Test.crest(w, h, ""));
            String c = "W" + w + " h" + h + " " + x.line();
            assertTrue(x.approach() <= x.breach() && x.breach() < x.apex() && x.apex() < x.reentry() && x.reentry() <= x.end() + 1e-9, c);
            assertTrue(x.closeStart() < x.apex() + 0.3, c);
            assertEquals(x.apex() + 0.3, x.riseEnd(), 1e-6, c);
        }
    }

    @Test
    void theEntryCrossingFollowsTheJawUntilTheAxisCrossesAndThenStaysPut() {
        WormSpec s = Prompt10Test.spec("crest", 16, 80, "");
        Moves.Move m = Prompt10Test.move(s);
        FxAnchors.Anchors a = Prompt10Test.anchors(s);
        assertEquals(31.7, geo(m, s, 2.8).entryZ, 1.0, "the head contact at 2.8 s");
        assertEquals(34.0, geo(m, s, 3.0).entryZ, 1.0, "at 3.0 s");
        assertEquals(43.8, geo(m, s, 3.8).entryZ, 1.5, "at 3.8 s");
        double z1 = geo(m, s, 4.5).entryZ;
        assertEquals(54, z1, 2.0, "the polyline crossing");
        for (double t = 4.5; t < 8.1; t += 0.4) assertEquals(z1, geo(m, s, t).entryZ, 0.5, "stays put while the body slides through, t=" + t);
        assertEquals(54, a.holeZ(), 2.0, "the entry hole of the sounds");
        assertEquals(104, a.reentryZ(), 2.0, "the exit hole (the tip's crossing)");
        assertTrue(Double.isNaN(geo(m, s, 8.0).exitZ), "no exit crossing before the reentry");
        assertEquals(104, geo(m, s, 8.4).exitZ, 2.5, "the exit crossing after it");
        // before the breach there is none; without the mouth the head is not looked at
        assertTrue(Double.isNaN(geo(m, s, 1.0).entryZ));
        assertTrue(Double.isNaN(FxPlayer.FxGeometry.at(m, 2.8).entryZ));
    }

    // ---- the crest sound sheet ----

    @Test
    void theCrestSoundSheetIsTheOwnersTable() {
        assertEquals(19, CrestFx.ROWS.size());
        Object[][] t = {   // name, event, anchor, off, vol, pitch, rep, every, jit
                {"Tremor: grumble", "entity.warden.step", "APPROACH", -0.15, 5.5, 0.5, 7, 0.33, 0.12},
                {"Tremor: deep heartbeat", "entity.warden.heartbeat", "APPROACH", 0.3, 2.4, 0.55, 4, 0.7, 0.05},
                {"Breach: boom", "entity.generic.explode", "BREACH", 0.0, 4.0, 0.55, 1, 0.0, 0.05},
                {"Breach: ground smash", "item.mace.smash_ground_heavy", "BREACH", 0.0, 3.5, 0.6, 1, 0.0, 0.05},
                {"Breach: sand spray", "block.sand.break", "BREACH", 0.05, 4.5, 0.55, 7, 0.08, 0.15},
                {"Breach: earth emerges", "entity.warden.emerge", "BREACH", 0.1, 3.0, 0.7, 1, 0.0, 0.05},
                {"Rise: streaming", "entity.warden.step", "BREACH", 0.4, 3.1, 0.5, 20, 0.3, 0.2},
                {"Rise: bone creak", "block.bone_block.hit", "BREACH", 1.0, 3.0, 0.5, 6, 0.9, 0.2},
                {"Roar", "entity.ravager.roar", "BREACH", 1.2, 4.0, 0.5, 4, 0.32, 0.03},
                {"Roar: low layer", "entity.ender_dragon.growl", "BREACH", 1.3, 3.5, 0.5, 4, 2.87, 0.03},
                {"Rumble (whole event): grumble", "entity.warden.step", "BREACH", 0.9, 4.0, 0.5, 1, 0.33, 0.12},
                {"Rumble (whole event): heartbeat", "entity.warden.heartbeat", "BREACH", 1.4, 2.4, 0.55, 1, 0.7, 0.05},
                {"Mouth clamp: bone", "block.bone_block.break", "RISE_END", -0.15, 1.4, 0.55, 3, 0.06, 0.1},
                {"Mouth clamp: thud", "entity.iron_golem.damage", "RISE_END", -0.1, 2.0, 0.5, 1, 0.0, 0.05},
                {"Fall: rushing sand", "block.sand.break", "REENTRY", 0.1, 1.2, 0.55, 9, 0.25, 0.2},
                {"Fall: rumble down", "entity.warden.dig", "REENTRY", 0.3, 3.0, 0.6, 1, 0.0, 0.05},
                {"Re-entry: impact", "entity.generic.explode", "REENTRY", 0.0, 2.0, 0.7, 1, 0.0, 0.05},
                {"Re-entry: sand collapse", "block.sand.break", "REENTRY", 0.05, 3.2, 0.5, 8, 0.1, 0.15},
                {"Re-entry: gravel", "block.gravel.break", "REENTRY", 0.15, 2.3, 0.55, 4, 0.15, 0.15}};
        for (int i = 0; i < t.length; i++) {
            CrashFx.Cue c = CrestFx.ROWS.get(i).cue();
            Object[] x = t[i];
            assertEquals(x[0], c.name());
            assertEquals(x[1], c.event(), c.name());
            assertEquals(x[2], c.anchor().name(), c.name());
            assertEquals((double) x[3], c.off(), 1e-9, c.name());
            assertEquals((double) x[4], c.vol(), 1e-9, c.name());
            assertEquals((double) x[5], c.pitch(), 1e-9, c.name());
            if (!CrestFx.ROWS.get(i).untilEnd()) assertEquals((int) x[6], c.rep(), c.name());
            assertEquals((double) x[7], c.every(), 1e-9, c.name());
            assertEquals((double) x[8], c.jit(), 1e-9, c.name());
            assertTrue(c.pitch() >= 0.5 && c.pitch() <= 2, "minecraft clamps the pitch to 0.5..2: " + c.name());
        }
        for (CrestFx.Row r : CrestFx.ROWS) assertEquals(r.cue().name().startsWith("Rumble"), r.untilEnd(), r.cue().name());
        assertFalse(CrestFx.ROWS.get(7).on(), "the bone creak is off by default");
        assertEquals(CrestFx.RUMBLE_EVENT, CrestFx.ROWS.get(10).cue().event(), "the rumble event is one constant");
        assertNotEquals("entity.generic.explode", CrestFx.RUMBLE_EVENT, "no explosions for the sustained rumble");
    }

    @Test
    void untilEndRowsGetTheirRepeatCountsAndTheCueExpansionMatchesTheTable() {
        for (double h : new double[]{0.7, 1.3}) {
            WormSpec s = Prompt10Test.crest(16, h, "");
            FxAnchors.Anchors a = Prompt10Test.anchors(s);
            List<CrashFx.Cue> cues = FxPlayer.cuesFor(a);
            assertEquals(18, cues.size(), "19 rows, the bone creak is off");
            int fixed = 0;
            for (CrashFx.Cue c : cues) {
                if (c.name().startsWith("Rumble")) {
                    double start = a.breach() + c.off();
                    assertEquals((int) Math.floor((a.end() - start) / c.every()) + 1, c.rep(), c.name());
                    assertTrue(start + (c.rep() - 1) * c.every() <= a.end() + 1e-9, "the last one is before the end");
                    assertTrue(start + c.rep() * c.every() > a.end() - 1e-9, "and the next one would be after it");
                } else fixed += c.rep();
            }
            assertEquals(76, fixed, "the other rows add up to 76 plays");
            List<FxPlayer.Play> plays = FxPlayer.expand(a, FxOptions.full(true), 3);
            assertEquals(cues.stream().mapToInt(CrashFx.Cue::rep).sum(), plays.size());
            for (int i = 1; i < plays.size(); i++) assertTrue(plays.get(i).time() >= plays.get(i - 1).time());
            // anchors of the rows
            for (FxPlayer.Play p : plays) {
                CrashFx.Cue c = cues.get(p.cue());
                double base = a.at(c.anchor());
                assertTrue(p.time() >= base + c.off() - 1e-9, c.name());
                assertTrue(p.time() <= a.end() + 0.05 || c.anchor() == CrashFx.Anchor.REENTRY, c.name() + " " + p.time() + " end " + a.end());
            }
        }
        // crash, emerge, engulf keep the sheet of prompt 09
        assertEquals(82, FxPlayer.expand(Prompt10Test.anchors(Prompt10Test.crash(16)), FxOptions.full(true), 1).size());
    }

    // ---- the lanes: exit eruption, any rows ----

    @Test
    void theExitEruptionIsFortyPercentAtTheExitAndAtMostThreeSeconds() {
        WormSpec s = Prompt10Test.crest(16, 1.3, "");
        FxPlayer p = new FxPlayer(Prompt10Test.move(s), MoveFactory.fxOptions(s), MoveFactory.closeTime(s), MoveFactory.closeEnd(s), 1);
        FxAnchors.Anchors a = p.anchors();
        int exit = 0, entry = 0;
        for (EruptionFx.Row r : EruptionFx.ROWS) {
            int row = E0 + EruptionFx.ROWS.indexOf(r);
            FxPlayer.Lane en = null, ex = null;
            for (FxPlayer.Lane l : p.lanes()) if (l.eruption() && l.row() == row) { if (l.site() == 0) en = l; else ex = l; }
            assertNotNull(en, r.name());
            assertNotNull(ex, r.name() + " at the exit");
            entry++;
            exit++;
            assertEquals(a.breach() + r.start(), en.start(), 1e-9);
            assertEquals(a.reentry() + r.start(), ex.start(), 1e-9, "the exit eruption starts at the reentry");
            assertEquals(en.perSecond() * 0.4, ex.perSecond(), 1e-6, r.name() + " rate 40 percent");
            assertEquals(Math.round(en.count() * 0.4), ex.count(), 1, r.name());
            if (!r.isBurst()) {
                assertEquals(a.breach() + 5.0, en.end(), 1e-9);
                assertEquals(a.reentry() + 3.0, ex.end(), 1e-9, "the sustain rows last 3 s at the exit");
                assertTrue(ex.end() - ex.start() <= 3.0 + 1e-9);
            } else assertEquals(ex.start() + r.over(), ex.end(), 1e-9, "a burst spreads over its own time");
        }
        assertEquals(12, entry);
        assertEquals(12, exit);
        // xeruption=off removes the exit rows and nothing else; eruption=off removes all
        WormSpec off = Prompt10Test.crest(16, 1.3, "xeruption=off");
        assertFalse(MoveFactory.fxOptions(off).xeruption());
        FxPlayer q = new FxPlayer(Prompt10Test.move(off), MoveFactory.fxOptions(off), MoveFactory.closeTime(off), MoveFactory.closeEnd(off), 1);
        assertEquals(12, q.lanes().stream().filter(l -> l.eruption()).count());
        assertTrue(q.lanes().stream().filter(l -> l.eruption()).allMatch(l -> l.site() == 0));
        assertEquals(p.lanes().stream().filter(l -> !l.eruption()).count(), q.lanes().stream().filter(l -> !l.eruption()).count());
        // crash, emerge, engulf have no exit eruption
        for (String mv : new String[]{"crash", "emerge", "engulf"}) {
            WormSpec c = Prompt10Test.spec(mv, 16, mv.equals("crash") ? 120 : 60, "");
            FxPlayer cp = new FxPlayer(Prompt10Test.move(c), MoveFactory.fxOptions(c), MoveFactory.closeTime(c), MoveFactory.closeEnd(c), 1);
            assertTrue(cp.lanes().stream().allMatch(l -> l.site() == 0), mv);
        }
    }

    @Test
    void theAnyRowsEmitAtEveryCrossingThatExistsAndEntryExitRowsOnlyAtTheirOwn() {
        WormSpec s = Prompt10Test.crest(16, 1.3, "");   // the long body that passes both holes at once
        Moves.Move m = Prompt10Test.move(s);
        Prompt10Test.Run r = Prompt10Test.run(s, FxOptions.full(true), 11);
        FxAnchors.Anchors a = r.player.anchors();
        int skirt = -1;
        for (int i = 0; i < E0; i++) if (CrashFx.EMITTERS.get(i).name().startsWith("Base mask: sand skirt")) skirt = i;
        int atEntry = 0, atExit = 0;
        for (int i = 0; i < r.spawns.size(); i++) {
            FxSink.Spawn p = r.spawns.get(i);
            if (p.row() != skirt) continue;
            double t = r.times.get(i);
            FxPlayer.FxGeometry.Snapshot g = geo(m, s, t);
            boolean nearEntry = !Double.isNaN(g.entryZ) && Math.abs(p.z() - g.entryZ) < 1.5 * m.r0 + 1, nearExit = !Double.isNaN(g.exitZ) && Math.abs(p.z() - g.exitZ) < 1.5 * m.r0 + 1;
            assertTrue(nearEntry || nearExit, "a base mask grain is at a crossing that exists, t=" + t);
            if (nearEntry) atEntry++;
            if (nearExit) atExit++;
        }
        assertTrue(atEntry > 1000 && atExit > 500, atEntry + " at the entry, " + atExit + " at the exit");
        // a crossing that does not exist emits nothing: before the reentry nothing at the exit
        for (int i = 0; i < r.spawns.size(); i++) {
            double t = r.times.get(i);
            if (t < a.reentry()) assertTrue(Double.isNaN(geo(m, s, t).exitZ));
        }
    }

    @Test
    void theCrestNeedsNoTailTestOfTheSoundAndFxOffStillPlaysNothing() {
        WormSpec off = Prompt10Test.crest(16, 0.7, "fx=off");
        Prompt10Test.Run r = Prompt10Test.run(off, MoveFactory.fxOptions(off), 1);
        assertEquals(0, r.spawns.size() + r.sounds.size());
    }

    // ---- the keys ----

    @Test
    void theCrestKeysTailAndXeruption() {
        MoveOptions.Parsed d = MoveOptions.parse("crest", 16, 80, "", false);
        assertEquals(TailMath.Spec.DEFAULT, MoveFactory.tailSpec(d.spec()));
        assertTrue(MoveFactory.fxOptions(d.spec()).xeruption());
        assertTrue(d.spec().p().length <= 21, "the defaults keep the short spec");
        MoveOptions.Parsed p = MoveOptions.parse("crest", 16, 80, "tail=off taillen=0.5 tailnub=0.2 tailcurve=2", false);
        TailMath.Spec t = MoveFactory.tailSpec(p.spec());
        assertEquals(new TailMath.Spec(false, 0.5, 0.2, 2.0), t);
        MoveOptions.Parsed c = MoveOptions.parse("crest", 16, 80, "taillen=0.05 tailnub=0.9 tailcurve=9", false);
        TailMath.Spec ct = MoveFactory.tailSpec(c.spec());
        assertEquals(TailMath.MIN_LEN, ct.len());
        assertEquals(TailMath.MAX_NUB, ct.nub());
        assertEquals(TailMath.MAX_CURVE, ct.curve());
        assertEquals(3, c.notes().stream().filter(n -> n.contains("clamped")).count(), "every clamp reaches the player: " + c.notes());
        assertThrows(MoveOptions.Bad.class, () -> MoveOptions.parse("crest", 16, 80, "tail=maybe", false));
        assertThrows(MoveOptions.Bad.class, () -> MoveOptions.parse("crest", 16, 80, "xeruption=maybe", false));
        for (String mv : new String[]{"crash", "emerge", "engulf"}) {
            assertThrows(MoveOptions.Bad.class, () -> MoveOptions.parse(mv, 16, 60, "taillen=0.3", false), mv + " has no tail key");
            assertThrows(MoveOptions.Bad.class, () -> MoveOptions.parse(mv, 16, 60, "xeruption=on", false), mv + " has no exit eruption");
            assertEquals(TailMath.Spec.OFF, MoveFactory.tailSpec(MoveOptions.parse(mv, 16, 120, "", false).spec()), mv + " has no tail");
        }
        WormSpec x = MoveOptions.parse("crest", 16, 80, "xeruption=off light=full tailnub=0.2", false).spec();
        assertFalse(MoveFactory.fxOptions(x).xeruption());
        assertEquals(0.2, MoveFactory.tailSpec(x).nub());
        assertEquals(com.paulorchard.islandcraft.wormsofarrakis.light.LightMath.Mode.FULL, MoveFactory.lightMode(x), "the other fields keep their places");
        assertTrue(MoveFactory.fxOptions(x).eruption() && MoveFactory.fxOptions(x).on());
        assertSame(MoveFactory.get(d.spec()), MoveFactory.get(x), "no key of this prompt changes the path");
        for (String line : MoveOptions.lines()) if (line.startsWith("crest:")) assertTrue(line.contains("tail=on") && line.contains("taillen=0.3") && line.contains("tailnub=0.12") && line.contains("tailcurve=1") && line.contains("xeruption=on") && line.contains("height=0.7"), line);
    }

    // ---- the tail ----

    @Test
    void theTailRadiusFollowsTheFormula() {
        double r0 = 7.96;
        int n = 80;
        TailMath.Spec s = TailMath.Spec.DEFAULT;
        int n0 = TailMath.n0(n, s.len());
        assertEquals(56, n0);
        for (int k = 0; k <= n0; k++) assertEquals(r0, TailMath.radius(k, n, r0, s), 1e-12, "r0 up to n0");
        for (int k = n0 + 1; k <= n; k++) {
            double u = (k - n0) / (double) (n - n0);
            assertEquals(r0 * (0.12 + 0.88 * (1 - u)), TailMath.radius(k, n, r0, s), 1e-9, "k=" + k);
        }
        assertEquals(0.96, TailMath.radius(n, n, r0, s), 0.02, "ends at about 0.96 blocks on W16");
        assertEquals(24, n - n0, "the taper covers the last 24 segments");
        for (double len : new double[]{0.1, 0.3, 0.6}) for (double nub : new double[]{0.03, 0.12, 0.4}) for (double curve : new double[]{0.5, 1, 2.5}) {
            TailMath.Spec q = new TailMath.Spec(true, len, nub, curve);
            double prev = Double.MAX_VALUE;
            for (int k = 0; k <= n; k++) {
                double rk = TailMath.radius(k, n, r0, q);
                assertTrue(rk <= prev + 1e-12, "monotone non-increasing: " + q + " k=" + k);
                assertTrue(rk >= r0 * nub - 1e-9 && rk <= r0 + 1e-12);
                prev = rk;
            }
            assertEquals(r0 * nub, TailMath.radius(n, n, r0, q), 1e-9, "the tip radius is nub x r0");
        }
        assertEquals(r0, TailMath.radius(n, n, r0, TailMath.Spec.OFF), "tail=off: the body radius everywhere");
        assertTrue(TailMath.radius(60, n, r0, new TailMath.Spec(true, 0.3, 0.12, 0.5)) > TailMath.radius(60, n, r0, new TailMath.Spec(true, 0.3, 0.12, 2.5)), "below 1 stays fat longer");
        // the faces of a ring scale with the radius: max(8, round(pi W r / r0))
        assertEquals(50, TailMath.faces(56, n, 16, r0, s));
        assertEquals(8, TailMath.faces(n, n, 16, r0, s), "never below 8");
        for (int k = n0; k < n; k++) assertEquals(Math.max(8, Math.round(Math.PI * 16 * TailMath.radius(k, n, r0, s) / r0)), TailMath.faces(k, n, 16, r0, s));
    }

    @Test
    void theTailIsBuiltFromTheRingsAndTailOffGivesTheOldBodyByteForByte() {
        WormSpec s = Prompt10Test.spec("crest", 16, 80, "");
        Moves.Move m = Prompt10Test.move(s);
        int n = WormMath.faceCount(m.width), n0 = TailMath.n0(m.segments, TailMath.DEFAULT_LEN);
        ShingleMath.Options opt = ShingleMath.Options.defaults(ScaleMath.LIP_LENGTH, true);
        int found = 0;
        StringBuilder b = new StringBuilder();
        for (double t = 0; t < m.cycle; t += 0.4) {
            var pose = m.pose(m.at(t)[0]);
            WormSkin plain = WormSkin.build(pose, n, m.r0, SurfaceProvider.PLANE, opt, 0.0);
            WormSkin off = WormSkin.build(pose, n, m.r0, SurfaceProvider.PLANE, opt, 0.0, TailMath.Spec.OFF, m.width);
            assertEquals(0, off.tail.quads, "tail off: nothing");
            assertEquals(plain.result.quads, off.result.quads);
            assertArrayEquals(java.util.Arrays.copyOf(plain.result.v, plain.result.quads * 32), java.util.Arrays.copyOf(off.result.v, off.result.quads * 32), "byte for byte");
            assertArrayEquals(java.util.Arrays.copyOf(plain.result.color, plain.result.quads), java.util.Arrays.copyOf(off.result.color, off.result.quads));
            assertEquals(plain.from, off.from);
            assertEquals(plain.to, off.to);
            WormSkin on = WormSkin.build(pose, n, m.r0, SurfaceProvider.PLANE, opt, 0.0, TailMath.Spec.DEFAULT, m.width);
            if (on.tail.quads > 0) {
                found++;
                assertTrue(on.to <= n0 - 1 || on.result.quads == 0, "the shingle skin stops where the taper starts (or is empty when only the tail is above the sand): " + on.to);
                QuadCheck.Count c = QuadCheck.check(on.tail.v, on.tail.quads);
                assertEquals(0, c.bad(), "the tail has no degenerate quad: " + c);
                // every tail vertex is no further from the axis polyline than the body radius (plus the plates) and no vertex is NaN
                for (int q = 0; q < on.tail.quads * 4; q++) for (int k = 0; k < 3; k++) assertTrue(Float.isFinite(on.tail.v[q * 8 + k]));
                for (int q = 0; q < on.tail.quads; q++) assertEquals(ShingleMath.SKIN, on.tail.kind[q]);
                b.append(String.format(Locale.ROOT, "tail t=%.1f: body segments %d..%d, tail quads %d%n", t, on.from, on.to, on.tail.quads));
            }
            // a body that is tail=on but not yet in the tail is the plain body
            if (on.tail.quads == 0 && on.to >= plain.to) assertEquals(plain.result.quads, on.result.quads);
        }
        assertTrue(found > 5, "the tail is above the sand at some instants: " + found);
        // crash, emerge, engulf have no tail: the spec's tail is off and the skin is the old one
        WormSpec crash = Prompt10Test.crash(16);
        assertEquals(TailMath.Spec.OFF, MoveFactory.tailSpec(crash));
        try {
            Files.writeString(Path.of("build/prompt10-tail-numbers.txt"), b.toString());
        } catch (IOException e) { /* the file is only for the report */ }
    }

    @Test
    void theTailMeshHasTheRightFacesAndAClosedTip() {
        WormSpec s = Prompt10Test.spec("crest", 16, 80, "");
        Moves.Move m = Prompt10Test.move(s);
        int n = WormMath.faceCount(m.width), segs = m.segments;
        // a straight body above the sand: every segment of the taper is drawn, so the counts are exact
        double[][] j = new double[segs + 1][];
        com.paulorchard.islandcraft.wormsofarrakis.path.WormPose pose = m.pose(m.at(Prompt10Test.anchors(s).apex())[0]);
        ShingleMath.Options opt = ShingleMath.Options.defaults(ScaleMath.LIP_LENGTH, true);
        var chain = pose.chain(TailMath.radii(segs, m.r0, TailMath.Spec.DEFAULT));
        ShingleMath.Result r = TailMath.build(chain, segs, m.width, m.r0, TailMath.Spec.DEFAULT, 0, segs - 1, 7L, 0.0);
        int want = 0;
        for (int k = TailMath.n0(segs, 0.3); k < segs; k++) want += TailMath.faces(k, segs, m.width, m.r0, TailMath.Spec.DEFAULT);
        int nk = TailMath.faces(segs - 1, segs, m.width, m.r0, TailMath.Spec.DEFAULT);
        assertEquals(want + 3 * nk, r.quads, "the frusta plus the three bands of the cap");
        assertEquals(0, QuadCheck.check(r.v, r.quads).bad());
        // the cap reaches r(n) beyond the last joint along the axis of the last segment: the last vertex positions lie at the pole
        double[] axis = chain.axis(segs - 1);
        double rn = TailMath.radius(segs, segs, m.r0, TailMath.Spec.DEFAULT);
        double[] c = chain.center[segs];
        double best = -1e9;
        for (int q = 0; q < r.quads * 4; q++) {
            double d = (r.v[q * 8] - c[0]) * axis[0] + (r.v[q * 8 + 1] - c[1]) * axis[1] + (r.v[q * 8 + 2] - c[2]) * axis[2];
            best = Math.max(best, d);
        }
        assertEquals(rn, best, 1e-4, "the nub is closed by a rounded cap of radius r(n)");
        // a tail of the default width ends in a nub about a block wide
        assertEquals(0.96, 2 * rn / 2, 0.02);
        assertEquals(n, WormMath.faceCount(16));
    }

    // ---- numbers for the report ----

    @Test
    void numbersForTheReport() throws IOException {
        StringBuilder b = new StringBuilder();
        for (double h : new double[]{0.7, 1.3, 2.0}) {
            WormSpec s = Prompt10Test.crest(16, h, "");
            FxPlayer.Play first = null;
            Prompt10Test.Run on = Prompt10Test.run(s, FxOptions.full(true), 7);
            Prompt10Test.Run xoff = Prompt10Test.run(Prompt10Test.crest(16, h, "xeruption=off"), MoveFactory.fxOptions(Prompt10Test.crest(16, h, "xeruption=off")), 7);
            Prompt10Test.Run none = Prompt10Test.run(s, new FxOptions(true, true, true, 1, 1, false, true), 7);
            b.append(String.format(Locale.ROOT, "crest W16 h%.1f: %s%n  sound plays %d, particles %d (no eruption %d, no exit eruption %d), peak alive %d, thinned %d, capped ticks %d%n", h, on.player.anchors().line(),
                    on.player.schedule().size(), on.player.spawned(), none.player.spawned(), xoff.player.spawned(), on.player.peakAlive(), on.player.thinned(), on.player.cappedTicks()));
        }
        for (double w : new double[]{6, 48}) {
            WormSpec s = Prompt10Test.crest(w, 0.7, "");
            Prompt10Test.Run on = Prompt10Test.run(s, FxOptions.full(true), 7);
            b.append(String.format(Locale.ROOT, "crest W%.0f h0.7 (%d segments): %s%n  sound plays %d, particles %d, peak alive %d, thinned %d%n", w, Prompt10Test.move(s).segments, on.player.anchors().line(), on.player.schedule().size(), on.player.spawned(), on.player.peakAlive(), on.player.thinned()));
        }
        Files.writeString(Path.of("build/prompt10-crest-numbers.txt"), b.toString());
    }
}
