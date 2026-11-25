package com.paulorchard.islandcraft.wormsofarrakis;

import com.paulorchard.islandcraft.wormsofarrakis.fx.CrashFx;
import com.paulorchard.islandcraft.wormsofarrakis.fx.CrashFx.PType;
import com.paulorchard.islandcraft.wormsofarrakis.fx.CrestFx;
import com.paulorchard.islandcraft.wormsofarrakis.fx.EruptionFx;
import com.paulorchard.islandcraft.wormsofarrakis.fx.FxAnchors;
import com.paulorchard.islandcraft.wormsofarrakis.fx.FxOptions;
import com.paulorchard.islandcraft.wormsofarrakis.fx.FxPlayer;
import com.paulorchard.islandcraft.wormsofarrakis.fx.FxSink;
import com.paulorchard.islandcraft.wormsofarrakis.fx.ParticleModel;
import com.paulorchard.islandcraft.wormsofarrakis.fx.PuffTracker;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;
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

/** Prompt 10: the eruption rows (part 1) and the effects of the crest (part 2). */
class Prompt10Test {
    /** Prompt 12: the size factor of W16 is 0.5 (the old automatic scale was 1.0); fxcount 2 restores the old W16 counts these assertions were written for. */
    private static final FxOptions TWICE = new FxOptions(true, true, true, 1, 2, true, true);
    private static final double DT = 0.05;
    private static final int E0 = CrashFx.EMITTERS.size();

    static WormSpec spec(String move, double w, int segs, String opts) { return MoveOptions.parse(move, w, segs, opts, false).spec(); }
    static Moves.Move move(WormSpec s) { return MoveFactory.get(s); }
    static FxAnchors.Anchors anchors(WormSpec s) { return FxAnchors.of(move(s), MoveFactory.closeTime(s), MoveFactory.closeEnd(s)); }
    static WormSpec crash(double w) { return spec("crash", w, w <= 6 ? 60 : w <= 16 ? 120 : 300, ""); }

    /** A crest spec that is long enough for the width (tries growing segment counts). */
    static WormSpec crest(double w, double h, String extra) {
        for (int segs : new int[]{w <= 6 ? 40 : w <= 16 ? 80 : 240, 120, 200, 300, 400, 600, 900}) {
            try {
                return spec("crest", w, segs, "height=" + h + (extra.isEmpty() ? "" : " " + extra));
            } catch (RuntimeException e) { /* too short: try a longer one */ }
        }
        throw new IllegalStateException("no crest for W" + w + " h" + h);
    }

    static final class Run {
        final List<FxSink.Spawn> spawns = new ArrayList<>();
        final List<Double> times = new ArrayList<>();
        final List<String> sounds = new ArrayList<>();
        final List<String> logs = new ArrayList<>();
        double at;
        FxPlayer player;

        FxSink sink() {
            return new FxSink() {
                @Override public void sound(CrashFx.Cue cue, double x, double y, double z, double volume, double pitch) { sounds.add(cue.name()); }
                @Override public void particle(Spawn p) { spawns.add(p); times.add(at); }
                @Override public void log(String line) { logs.add(line); }
            };
        }

        long byRow(int row) { return player.spawnedByRow(row); }
    }

    static Run run(WormSpec s, FxOptions o, long seed) { return run(s, o, seed, CrashFx.BUDGET); }

    static Run run(WormSpec s, FxOptions o, long seed, int budget) {
        Moves.Move m = move(s);
        Run r = new Run();
        r.player = new FxPlayer(m, o, MoveFactory.closeTime(s), MoveFactory.closeEnd(s), seed);
        r.player.setBudget(budget);
        for (double tau = DT; tau <= m.cycle - 1e-9; tau += DT) {
            r.at = tau;
            r.player.step(tau, 0, r.sink());
        }
        return r;
    }

    static int erow(String start) {
        for (int i = 0; i < EruptionFx.ROWS.size(); i++) if (EruptionFx.ROWS.get(i).name().contains(start)) return E0 + i;
        throw new IllegalArgumentException(start);
    }

    // ================= part 1: the eruption =================

    @Test
    void theEruptionRowsAreTheOwnersTable() {
        // name, type, mode, shape, start, amount, over, ring, radJit, yJit, up, out, spread, grav, drag, size, endScale, life0, life1, tint, alpha
        Object[][] t = {
                {"ground dust bank", PType.CLOUD, "SUSTAIN", "RING", 0.0, 520.0, 0.0, 1.05, 0.4, 2.5, 0.08, 0.09, 0.05, -0.01, 0.95, 3.6, 0.333, 30, 80, 0xCDB07A, 0.6},
                {"rising dust columns", PType.DUST_PLUME, "SUSTAIN", "JETS", 0.05, 260.0, 0.0, 1.0, 0.15, 0.5, 0.95, 0.16, 0.07, -0.01, 0.975, 5.0, 0.333, 40, 100, 0xD8BB86, 0.55},
                {"spiky sand plumes", PType.BLOCK, "SUSTAIN", "JETS", 0.0, 2400.0, 0.0, 1.0, 0.1, 0.3, 1.0, 0.2, 0.14, 1.0, 0.98, 0.15, 1.0, 20, 70, 0xB8A888, 1.0},
                {"sand fountain", PType.BLOCK, "BURST", "RING", 0.0, 3500.0, 0.45, 0.9, 0.3, 0.3, 1.3, 0.6, 0.22, 1.0, 0.98, 0.15, 1.0, 25, 80, 0xB8A888, 1.0},
                {"central dust column", PType.DUST_PLUME, "BURST", "DISC", 0.0, 260.0, 0.5, 0.6, 0.0, 0.5, 1.1, 0.1, 0.12, -0.01, 0.975, 5.5, 0.333, 40, 90, 0xD8BB86, 0.6},
                {"dust ring", PType.DUST_PLUME, "BURST", "RING", 0.0, 420.0, 0.35, 1.0, 0.15, 0.8, 0.12, 0.65, 0.08, -0.01, 0.95, 5.0, 0.333, 25, 60, 0xD8BB86, 0.6},
                {"shock ring", PType.DUST_PLUME, "BURST", "RING", 0.0, 220.0, 0.12, 1.2, 0.05, 0.2, 0.03, 0.9, 0.05, 0.0, 0.94, 3.2, 0.333, 15, 35, 0xE0C690, 0.55},
                {"flying sand blocks", PType.CHUNK, "BURST", "RING", 0.0, 260.0, 0.4, 0.9, 0.3, 0.3, 1.1, 0.55, 0.3, 1.0, 0.99, 0.25, 1.0, 40, 120, 0xB8A888, 1.0},
                {"sand blocks in the plumes", PType.CHUNK, "SUSTAIN", "JETS", 0.1, 60.0, 0.0, 1.0, 0.1, 0.3, 0.9, 0.25, 0.2, 1.0, 0.99, 0.25, 1.0, 40, 100, 0xB8A888, 1.0},
                {"sand curtain off the body", PType.BLOCK, "SUSTAIN", "CURTAIN", 0.3, 900.0, 0.0, 0.98, 0.03, 14.0, 0.0, 0.0, 0.01, 1.0, 0.98, 0.15, 1.0, 30, 70, 0xB8A888, 1.0},
                {"high haze", PType.DUST_PLUME, "SUSTAIN", "DISC", 0.2, 80.0, 0.0, 1.8, 0.0, 1.0, 0.45, 0.08, 0.06, -0.01, 0.98, 7.0, 0.333, 50, 110, 0xE0C690, 0.32},
                {"fine grain haze", PType.BLOCK, "SUSTAIN", "DISC", 0.0, 700.0, 0.0, 2.4, 0.0, 1.0, 0.28, 0.2, 0.16, 1.0, 0.98, 0.10, 1.0, 20, 90, 0xC8B898, 1.0}};
        assertEquals(t.length, EruptionFx.ROWS.size());
        assertEquals(5.0, EruptionFx.ERUPTION_SUSTAIN);
        assertEquals(14, EruptionFx.ERUPTION_JETS);
        assertEquals(0.6, EruptionFx.JET_REROLL);
        for (int i = 0; i < t.length; i++) {
            EruptionFx.Row r = EruptionFx.ROWS.get(i);
            Object[] x = t[i];
            String c = r.name();
            assertTrue(r.name().endsWith((String) x[0]), c);
            assertEquals(x[1], r.type(), c);
            assertEquals(x[2], r.mode().name(), c);
            assertEquals(x[3], r.shape().name(), c);
            double[] want = {(double) x[4], (double) x[5], (double) x[6], (double) x[7], (double) x[8], (double) x[9], (double) x[10], (double) x[11], (double) x[12], (double) x[13], (double) x[14], (double) x[15], (double) x[16]};
            double[] got = {r.start(), r.amount(), r.over(), r.ring(), r.radJit(), r.yJit(), r.up(), r.out(), r.spread(), r.grav(), r.drag(), r.size(), r.endScale()};
            for (int k = 0; k < want.length; k++) assertEquals(want[k], got[k], 1e-9, c + " column " + k);
            assertEquals((int) x[17], r.lifeMin(), c);
            assertEquals((int) x[18], r.lifeMax(), c);
            assertEquals((int) x[19], r.tint(), c);
            assertEquals((double) x[20], r.alpha(), 1e-9, c);
        }
    }

    @Test
    void particleCountsPerEruptionRowAtW16WithinFivePercent() {
        WormSpec s = crash(16);
        Run r = run(s, FxOptions.full(true), 4, 1_000_000);
        assertEquals(0, r.player.thinned());
        Run capped = run(s, FxOptions.full(true), 4);
        assertTrue(capped.player.thinned() < 0.05 * capped.player.spawned(), "at W16 the budget of 14000 thins the approved eruption by less than 5 percent: " + capped.player.thinned() + " of " + capped.player.spawned());
        assertTrue(capped.player.peakAlive() <= CrashFx.BUDGET + 40);
        double scale = r.player.scale();
        for (int i = 0; i < EruptionFx.ROWS.size(); i++) {
            EruptionFx.Row row = EruptionFx.ROWS.get(i);
            double want = EruptionFx.countAtScaleOne(row) * scale;
            assertEquals(want, r.byRow(E0 + i), Math.max(3, want * 0.05), row.name());
        }
    }

    @Test
    void eruptionOffMakesNoEruptionRowsAndLeavesTheOldRowsUntouched() {
        for (double w : new double[]{6, 16}) {
            WormSpec s = crash(w);
            Run off = run(s, new FxOptions(true, true, true, 1, 1, false), 4), on = run(s, FxOptions.full(true), 4, 1_000_000), legacy = run(s, FxOptions.defaults(true), 4);
            for (int i = 0; i < E0; i++) {
                assertEquals(legacy.byRow(i), off.byRow(i), "legacy options = eruption off, row " + i);
                if (on.player.thinned() == 0) assertEquals(off.byRow(i), on.byRow(i), "W" + w + " old row " + i + " the same with the eruption on");
            }
            for (int i = 0; i < EruptionFx.ROWS.size(); i++) {
                assertEquals(0, off.byRow(E0 + i));
                assertTrue(on.byRow(E0 + i) > 0);
            }
        }
        MoveOptions.Parsed p = MoveOptions.parse("crash", 16, 120, "eruption=off", false);
        assertFalse(MoveFactory.fxOptions(p.spec()).eruption());
        assertTrue(MoveFactory.fxOptions(spec("crash", 16, 120, "")).eruption(), "default on");
        assertTrue(spec("crash", 16, 120, "").p().length <= 21, "the default spec keeps its older length");
        assertSame(MoveFactory.get(spec("crash", 16, 120, "")), MoveFactory.get(p.spec()), "no path change");
        assertThrows(MoveOptions.Bad.class, () -> MoveOptions.parse("crash", 16, 120, "eruption=maybe", false));
        for (String line : MoveOptions.lines()) assertTrue(line.contains("eruption="), line);
    }

    @Test
    void theJetsAreRerolledEvery06SecondsAndEveryJetTickUsesAJetOfTheMoment() {
        long seed = 6;
        assertEquals(14, FxPlayer.jetsFor(seed, 0).angle().length);
        assertFalse(java.util.Arrays.equals(FxPlayer.jetsFor(seed, 0).angle(), FxPlayer.jetsFor(seed, 1).angle()), "a new set every epoch");
        assertArrayEquals(FxPlayer.jetsFor(seed, 3).angle(), FxPlayer.jetsFor(seed, 3).angle(), "deterministic");
        for (double st : FxPlayer.jetsFor(seed, 5).strength()) assertTrue(st >= 0.5 && st <= 1.4);
        assertEquals(0, FxPlayer.jetEpoch(4.10, 4.10));
        assertEquals(0, FxPlayer.jetEpoch(4.69, 4.10));
        assertEquals(1, FxPlayer.jetEpoch(4.71, 4.10));

        WormSpec s = crash(16);
        Moves.Move m = move(s);
        Run r = run(s, FxOptions.full(true), seed);
        double breach = r.player.anchors().breach();
        int row = erow("spiky sand plumes");
        java.util.Set<Long> epochs = new java.util.TreeSet<>();
        int n = 0;
        for (int i = 0; i < r.spawns.size(); i++) {
            FxSink.Spawn p = r.spawns.get(i);
            if (p.row() != row) continue;
            double t = r.times.get(i);
            long ep = FxPlayer.jetEpoch(t, breach);
            epochs.add(ep);
            FxPlayer.FxGeometry.Snapshot g = FxPlayer.FxGeometry.at(m, t);
            double ang = Math.atan2(p.z() - g.entryZ, p.x());
            double best = 9;
            for (double ja : FxPlayer.jetsFor(seed, ep).angle()) {
                double d = Math.abs(Math.atan2(Math.sin(ang - ja), Math.cos(ang - ja)));
                best = Math.min(best, d);
            }
            assertTrue(best < 0.12 * 5.5, "a plume particle is within a few sigma of a current jet: " + best);
            n++;
        }
        assertTrue(n > 5000);
        assertTrue(epochs.size() >= 8 && epochs.size() <= 10, "about 5 s / 0.6 s epochs: " + epochs.size());
    }

    @Test
    void curtainHeightsAreBetweenHalfABlockAndFourteenAndAHalf() {
        Run r = run(crash(16), TWICE, 2);
        int row = erow("curtain");
        double lo = 99, hi = -99;
        int n = 0;
        for (FxSink.Spawn p : r.spawns) {
            if (p.row() != row) continue;
            lo = Math.min(lo, p.y());
            hi = Math.max(hi, p.y());
            n++;
            assertEquals(0, p.vx() * 0 + Math.abs(p.vy()) > 0.2 ? 1 : 0, "the curtain has no up speed, only the small spread");
        }
        assertTrue(n > 3000);
        assertTrue(lo >= 0.5 && hi <= 14.5, lo + " .. " + hi);
        assertTrue(hi > 13 && lo < 1, "the whole range is used");
    }

    @Test
    void chunkRowsMakeQuarterBlockSandParticles() {
        Run r = run(crash(16), TWICE, 2);
        int n = 0;
        for (FxSink.Spawn p : r.spawns) {
            if (p.type() != PType.CHUNK) continue;
            n++;
            assertEquals(0.25, p.size(), 1e-9);
            assertEquals(1.0, p.endScale(), 1e-9);
            assertEquals(1.0, p.alpha(), 1e-9, "opaque");
            assertTrue(p.row() == erow("flying sand blocks") || p.row() == erow("blocks in the plumes"));
        }
        assertTrue(n > 400, "260 + 60/s x 4.9 s: " + n);
        assertEquals(0.25 / 0.15, ParticleModel.scaleFor(PType.CHUNK, 0.25), 1e-9, "a sand block particle of vanilla width 0.15 scaled to 0.25");
        // the grains are 0.15 and 0.10 wide, the puffs have their sizes
        for (FxSink.Spawn p : r.spawns) if (p.row() >= E0 && p.type() == PType.BLOCK) assertTrue(p.size() == 0.15 || p.size() == 0.10);
    }

    @Test
    void thePuffsShrinkLinearlyToAThirdAndFadeInAndOut() {
        for (double f : new double[]{0, 0.2, 0.5, 0.8, 1}) assertEquals(1 + (0.333 - 1) * f, ParticleModel.sizeAt(1, 0.333, f), 1e-12);
        assertEquals(0.333, ParticleModel.sizeAt(3.6, 0.333, 1) / 3.6, 0.02 * 0.333, "size at death is endScale x start within 2 percent");
        double prev = 9;
        for (double f = 0; f <= 1.0001; f += 0.01) {
            double v = ParticleModel.sizeAt(5, 0.333, f);
            assertTrue(v <= prev + 1e-12, "monotone");
            prev = v;
        }
        assertEquals(0, ParticleModel.fade(0), 1e-12);
        assertEquals(0.5, ParticleModel.fade(0.075), 1e-12);
        assertEquals(1, ParticleModel.fade(0.15), 1e-12);
        assertEquals(1, ParticleModel.fade(0.4), 1e-12);
        assertEquals(1, ParticleModel.fade(0.6), 1e-12);
        assertEquals(0.5, ParticleModel.fade(0.8), 1e-12);
        assertEquals(0, ParticleModel.fade(1), 1e-12);

        // the bookkeeping on a fake puff: the last tick sets the size to endScale x start, the puff is then dropped
        final class Fake implements PuffTracker.Puff {
            float size = 1, alpha = 1;
            boolean alive = true;
            double prevSize = 9;
            boolean monotone = true;
            @Override public boolean alive() { return alive; }
            @Override public void setSize(float s) { if (s > prevSize + 1e-6) monotone = false; prevSize = s; size = s; }
            @Override public void setAlpha(float a) { alpha = a; }
        }
        PuffTracker tr = new PuffTracker();
        Fake f = new Fake();
        tr.add(f, 2.0f, 0.333f, 0.6f, 40);
        assertEquals(0, f.alpha);
        float maxAlpha = 0;
        for (int i = 0; i < 39; i++) {
            tr.tick();
            maxAlpha = Math.max(maxAlpha, f.alpha);
        }
        assertEquals(1, tr.size());
        tr.tick();   // age 40 = life
        assertEquals(0, tr.size(), "dropped at death");
        assertEquals(2.0 * 0.333, f.size, 0.02 * 2.0 * 0.333, "size at f = 1");
        assertTrue(f.monotone);
        assertEquals(0.6, maxAlpha, 1e-6, "the row's alpha at full");
        Fake dead = new Fake();
        tr.add(dead, 1f, 0.5f, 1f, 100);
        dead.alive = false;
        tr.tick();
        assertEquals(0, tr.size(), "a puff the game removed is dropped");
    }

    /** Spawns of the tick that ends at tau, for a player stepped up to it with externalLive only on that last tick. */
    private static int[] lastTick(WormSpec s, double tau, int externalLive, long seed) {
        Moves.Move m = move(s);
        FxPlayer p = new FxPlayer(m, TWICE, MoveFactory.closeTime(s), MoveFactory.closeEnd(s), seed);
        Run r = new Run();
        r.player = p;
        double t = DT;
        for (; t < tau - DT / 2; t += DT) p.step(t, 0, r.sink());
        long[] before = new long[E0 + EruptionFx.ROWS.size()];
        for (int i = 0; i < before.length; i++) before[i] = p.spawnedByRow(i);
        p.step(t, externalLive, r.sink());
        int[] d = new int[before.length];
        for (int i = 0; i < d.length; i++) d[i] = (int) (p.spawnedByRow(i) - before[i]);
        return d;
    }

    @Test
    void theBudgetThinsEveryRowByOneRatioKeepsEveryRowAndSparesTheFirstHalfOfABurst() {
        WormSpec s = crash(16);
        double breach = anchors(s).breach();
        // after the first halves of the bursts: every row is thinned by the same ratio and none is dropped
        double late = breach + 0.8;
        int[] full = lastTick(s, late, 0, 3), thin = lastTick(s, late, 13900, 3);   
        int sumFull = 0, sumThin = 0;
        for (int i = 0; i < full.length; i++) {
            sumFull += full[i];
            sumThin += thin[i];
            if (full[i] > 0) assertTrue(thin[i] >= 1, "a row is never dropped: " + i);
        }
        assertTrue(sumThin < sumFull * 0.5, sumThin + " of " + sumFull);
        double ratio = -1;
        for (int i = 0; i < full.length; i++) {
            if (full[i] < 60) continue;
            double q = thin[i] / (double) full[i];
            if (ratio < 0) ratio = q;
            assertEquals(ratio, q, 0.06, "one ratio for all rows, row " + i);
        }
        assertTrue(ratio > 0 && ratio < 0.6);
        // inside the first half of the fountain (0.45 s): the burst keeps all it wanted, the others are thinned
        double early = breach + 0.1;
        int[] fullE = lastTick(s, early, 0, 3), thinE = lastTick(s, early, 13900, 3);
        for (String n : new String[]{"sand fountain", "flying sand blocks", "central dust column"}) {
            int row = erow(n);
            assertEquals(fullE[row], thinE[row], n + " is spared in the first half of its burst");
            assertTrue(fullE[row] > 5);
        }
        assertTrue(thinE[erow("spiky sand plumes")] < fullE[erow("spiky sand plumes")]);
        assertTrue(thinE[erow("spiky sand plumes")] >= 1);
    }

    // ================= part 2: the crest =================

    @Test
    void crestAnchorsAreInOrderForTheWidthsAndHeights() {
        for (double w : new double[]{6, 16, 48}) {
            WormSpec s = crest(w, 1.3, "");
            check(anchors(s), "W" + w + " h1.3");
        }
        for (double h : new double[]{0.7, 1.3, 2.0}) {
            WormSpec s = crest(16, h, "");
            FxAnchors.Anchors a = anchors(s);
            check(a, "W16 h" + h);
            assertEquals(a.apex() + 0.3, a.riseEnd(), 1e-6, "riseEnd = apex + close_end (0.3 on a crest)");
            assertEquals(a.apex(), a.fallStart(), 1e-9);
        }
    }

    private static void check(FxAnchors.Anchors a, String ctx) {
        ctx += " " + a.line();
        assertTrue(a.approach() <= a.breach(), ctx);
        assertTrue(a.breach() < a.apex(), ctx);
        assertTrue(a.apex() < a.reentry(), ctx);
        assertTrue(a.reentry() <= a.end() + 1e-9, ctx);
        assertTrue(a.closeStart() < a.apex() + 0.3, ctx);
        assertTrue(a.crest(), ctx);
    }

    @Test
    void theCrossingSearchFindsTwoPointsOnACrestAfterTheReentryAndOneBefore() {
        WormSpec s = spec("crest", 16, 300, "height=1.3");   // a body long enough to span the whole arc
        Moves.Move m = move(s);
        FxAnchors.Anchors a = anchors(s);
        FxPlayer.FxGeometry.Snapshot before = FxPlayer.FxGeometry.at(m, (a.breach() + a.apex()) / 2);
        assertFalse(Double.isNaN(before.entryZ));
        assertTrue(Double.isNaN(before.exitZ), "one crossing before the reentry");
        boolean two = false;
        for (double t = a.reentry() + 0.05; t < a.end(); t += 0.05) {
            FxPlayer.FxGeometry.Snapshot g = FxPlayer.FxGeometry.at(m, t);
            if (!Double.isNaN(g.entryZ) && !Double.isNaN(g.exitZ)) {
                two = true;
                assertTrue(g.entryZ < g.exitZ, "the entry hole is behind the exit hole");
            }
            assertFalse(Double.isNaN(g.exitZ) && Double.isNaN(g.entryZ) && t < a.reentry());
        }
        assertTrue(two, "after the reentry the body passes both holes");
        // crash, emerge, engulf: one crossing, no exit
        for (String mv : new String[]{"crash", "emerge", "engulf"}) {
            WormSpec c = spec(mv, 16, mv.equals("crash") ? 120 : 60, "");
            Moves.Move cm = move(c);
            FxAnchors.Anchors ca = anchors(c);
            for (double t = ca.breach(); t < ca.reentry(); t += 0.1) assertTrue(Double.isNaN(FxPlayer.FxGeometry.at(cm, t).exitZ), mv);
        }
    }

    @Test
    void entryRowsNeverEmitAtTheExitAndTheReverse() {
        WormSpec s = crest(16, 1.3, "");
        Moves.Move m = move(s);
        Run r = run(s, TWICE, 5);
        FxAnchors.Anchors a = r.player.anchors();
        double R = m.r0;
        int fountain = row("Breach: sand fountain"), burst = row("Fall: re-entry burst"), pour = row("Fall: sand pouring into the hole");
        double mo = 45;
        java.util.function.DoubleFunction<FxPlayer.FxGeometry.Snapshot> geo = t -> FxPlayer.FxGeometry.at(m, t, com.paulorchard.islandcraft.wormsofarrakis.path.MouthAnim.angleAt(m, t, mo, MoveFactory.closeTime(s), MoveFactory.closeEnd(s)));
        FxPlayer.FxGeometry.Snapshot atReentry = geo.apply(a.reentry() + 0.04);
        int nf = 0, nb = 0, np = 0;
        for (int i = 0; i < r.spawns.size(); i++) {
            FxSink.Spawn p = r.spawns.get(i);
            double t = r.times.get(i);
            if (p.row() == fountain) {
                nf++;
                FxPlayer.FxGeometry.Snapshot atBreach = geo.apply(t);   // the entry hole at the instant of the burst (the head still touches the sand there)
                assertTrue(Math.abs(Math.hypot(p.x(), p.z() - atBreach.entryZ) - 0.95 * R) < 0.6, "fountain on the entry ring");
                assertTrue(Math.abs(p.z() - atReentry.exitZ) > R, "never at the exit");
            }
            if (p.row() == burst) {
                nb++;
                assertTrue(Math.abs(Math.hypot(p.x(), p.z() - atReentry.exitZ) - 0.9 * R) < 0.6, "re-entry burst on the exit ring");
                assertTrue(Math.abs(p.z() - geo.apply(t).entryZ) > R || Double.isNaN(geo.apply(t).entryZ));
            }
            if (p.row() == pour) {
                np++;
                assertTrue(t >= a.reentry() - 1e-9, "the fall pouring starts at the reentry");
                assertTrue(Math.abs(p.z() - atReentry.exitZ) < 1.5 * R + 3, "and is at the exit");
            }
        }
        assertTrue(nf > 400 && nb > 250 && np > 100, nf + " " + nb + " " + np);
    }

    @Test
    void theCrestHasNoHoldRowsAndTheFallRowsAreAtTheExit() {
        WormSpec s = crest(16, 1.3, "");
        FxAnchors.Anchors a = anchors(s);
        Run r = run(s, TWICE, 5);
        assertEquals(0, r.byRow(row("Hold: sand sliding off the body")), "no hold rows");
        for (FxPlayer.Lane l : r.player.lanes()) if (!l.eruption()) assertFalse(CrashFx.EMITTERS.get(l.row()).name().startsWith("Hold"));
        List<FxPlayer.Play> plays = FxPlayer.expand(a, FxOptions.full(true), 1);
        List<CrashFx.Cue> cs = FxPlayer.cuesFor(a);
        for (FxPlayer.Play p : plays) assertNotEquals(CrashFx.Group.SLIDE, cs.get(p.cue()).group());
        assertEquals(CrestFx.cues(a).stream().mapToInt(CrashFx.Cue::rep).sum(), plays.size(), "the crest plays the repeats of its own sheet");
        int rush = -1;
        for (int i = 0; i < cs.size(); i++) if (cs.get(i).name().equals("Fall: rushing sand")) rush = i;
        double first = 1e9;
        for (FxPlayer.Play p : plays) if (p.cue() == rush) first = Math.min(first, p.time());
        assertEquals(a.reentry() + 0.1, first, 1e-9, "rushing sand from the reentry");
        // the base mask is at both crossings: at the entry from the breach, and at the exit from the reentry
        int skirt = row("Base mask: sand skirt");
        long before = 0, after = 0;
        for (int i = 0; i < r.spawns.size(); i++) if (r.spawns.get(i).row() == skirt) { if (r.times.get(i) < a.reentry()) before++; else after++; }
        assertTrue(before > 1000, "at the entry: " + before);
        assertTrue(after > 100, "at the exit after the reentry: " + after);
    }

    @Test
    void fxOffOnACrestPlaysNothingAndTheOtherMovesAreUnchanged() {
        WormSpec off = crest(16, 1.3, "fx=off");
        assertFalse(MoveFactory.fxOptions(off).on());
        Run r = run(off, MoveFactory.fxOptions(off), 1);
        assertEquals(0, r.spawns.size() + r.sounds.size());
        assertTrue(FxPlayer.expand(anchors(off), MoveFactory.fxOptions(off), 1).isEmpty());
        // crash, emerge, engulf: the anchors of prompt 09 (the line is unchanged for them)
        assertEquals("approach=2.17 breach=4.10 closeStart=6.56 riseEnd=7.46 fallStart=8.96 reentry=11.54 end=11.84 (holeZ=28.80 reentryZ=29.85)", anchors(crash(16)).line());
        assertEquals("approach=2.33 breach=4.26 closeStart=4.94 riseEnd=5.84 fallStart=7.84 reentry=9.42 end=13.68 (holeZ=26.00 reentryZ=26.00)", anchors(spec("emerge", 16, 60, "")).line());
        assertEquals("approach=2.17 breach=4.10 closeStart=4.11 riseEnd=5.01 fallStart=5.51 reentry=6.86 end=7.38 (holeZ=28.80 reentryZ=28.80)", anchors(spec("engulf", 16, 60, "")).line());
        assertEquals(79, FxPlayer.expand(anchors(crash(16)), FxOptions.full(true), 1).size());
    }

    private static int row(String start) {
        for (int i = 0; i < CrashFx.EMITTERS.size(); i++) if (CrashFx.EMITTERS.get(i).name().startsWith(start)) return i;
        throw new IllegalArgumentException(start);
    }

    @Test
    void theTextOfTheFxCommandFormatsTheEruptionRows() {
        FxAnchors.Anchors a = anchors(crash(16));
        List<String> l = FxPlayer.eruptionChatLines(a), sheet = FxPlayer.eruptionSheetLines(a);
        assertEquals(1 + EruptionFx.ROWS.size(), l.size());
        assertEquals(EruptionFx.ROWS.size(), sheet.size());
        assertTrue(l.get(0).contains("14 jets re-rolled every 0.6s"), l.get(0));
        assertTrue(sheet.get(3).contains("count 3500 over 0.45s"), sheet.get(3));
    }

    // ================= the numbers of the report =================

    @Test
    void numbersForTheReport() throws IOException {
        StringBuilder b = new StringBuilder();
        for (String mv : new String[]{"crash", "emerge", "engulf"}) {
            for (double w : new double[]{6, 16, 48}) {
                WormSpec s = mv.equals("crash") ? crash(w) : spec(mv, w, w <= 6 ? 40 : w <= 16 ? 60 : 300, "");
                for (boolean er : new boolean[]{false, true}) {
                    Run r = run(s, new FxOptions(true, true, true, 1, 1, er), 7);
                    FxAnchors.Anchors a = r.player.anchors();
                    b.append(String.format(Locale.ROOT, "%s W%.0f eruption=%s: scale=%.2f total=%d peakAlive=%d avgPerSecond(breach..end)=%.0f thinned=%d cappedTicks=%d%n", mv, w, er ? "on " : "off", r.player.scale(), r.player.spawned(),
                            r.player.peakAlive(), r.player.spawned() / Math.max(0.1, a.end() - a.breach()), r.player.thinned(), r.player.cappedTicks()));
                    if (er && mv.equals("crash")) {
                        double[] perSec = new double[(int) Math.ceil(r.player.anchors().end()) + 2];
                        for (int i = 0; i < r.spawns.size(); i++) perSec[(int) (double) r.times.get(i)]++;
                        double best = 0;
                        for (double v : perSec) best = Math.max(best, v);
                        b.append(String.format(Locale.ROOT, "  busiest second: %.0f spawns%n", best));
                        for (String l : r.logs) b.append("  ").append(l).append('\n');
                    }
                }
            }
        }
        // which rows are thinned at W48 (crash, eruption on) and by how much
        WormSpec s48 = crash(48);
        Run on = run(s48, FxOptions.full(true), 7);
        Run unthinned = null;
        b.append("rows at crash W48, eruption on: made / wanted (rate x duration x scale)\n");
        double sc = on.player.scale();
        for (int i = 0; i < E0; i++) b.append(String.format(Locale.ROOT, "  %-44s %7d%n", CrashFx.EMITTERS.get(i).name(), on.byRow(i)));
        for (int i = 0; i < EruptionFx.ROWS.size(); i++) {
            EruptionFx.Row r = EruptionFx.ROWS.get(i);
            double want = EruptionFx.countAtScaleOne(r) * sc;
            b.append(String.format(Locale.ROOT, "  %-44s %7d / %7.0f = %3.0f%%%n", r.name(), on.byRow(E0 + i), want, 100.0 * on.byRow(E0 + i) / want));
        }
        // the same at W16 with the counts at scale 1 (what the owner approved)
        Run w16 = run(crash(16), FxOptions.full(true), 7);
        b.append("rows at crash W16, eruption on:\n");
        for (int i = 0; i < E0; i++) b.append(String.format(Locale.ROOT, "  %-44s %7d%n", CrashFx.EMITTERS.get(i).name(), w16.byRow(i)));
        for (int i = 0; i < EruptionFx.ROWS.size(); i++) b.append(String.format(Locale.ROOT, "  %-44s %7d%n", EruptionFx.ROWS.get(i).name(), w16.byRow(E0 + i)));
        // the crest: sound plays, particles, peak alive
        for (double h : new double[]{0.7, 1.3, 2.0}) {
            WormSpec c = crest(16, h, "");
            Run cr = run(c, FxOptions.full(true), 7);
            FxAnchors.Anchors a = cr.player.anchors();
            b.append(String.format(Locale.ROOT, "crest W16 h%.1f: %s%n  sound plays=%d particles=%d peakAlive=%d thinned=%d%n", h, a.line(), FxPlayer.expand(a, FxOptions.full(true), 1).size(), cr.player.spawned(), cr.player.peakAlive(), cr.player.thinned()));
            Run old = run(c, new FxOptions(true, true, true, 1, 1, false), 7);
            b.append(String.format(Locale.ROOT, "  without the eruption: particles=%d peakAlive=%d%n", old.player.spawned(), old.player.peakAlive()));
        }
        for (double w : new double[]{6, 48}) {
            WormSpec c = crest(w, 1.3, "");
            Run cr = run(c, FxOptions.full(true), 7);
            b.append(String.format(Locale.ROOT, "crest W%.0f h1.3: %s%n  sound plays=%d particles=%d peakAlive=%d thinned=%d%n", w, cr.player.anchors().line(), FxPlayer.expand(cr.player.anchors(), FxOptions.full(true), 1).size(), cr.player.spawned(), cr.player.peakAlive(), cr.player.thinned()));
        }
        // anchors of the crest at heights 0.7, 1.3, 2.0 at W16, and W6, W48
        for (double w : new double[]{6, 16, 48}) b.append(String.format(Locale.ROOT, "crest W%.0f h1.3 anchors: %s%n", w, anchors(crest(w, 1.3, "")).line()));
        // the cost of the shrink and fade bookkeeping: 6000 puffs, 100 ticks
        final class P implements PuffTracker.Puff {
            float s, a;
            @Override public boolean alive() { return true; }
            @Override public void setSize(float x) { s = x; }
            @Override public void setAlpha(float x) { a = x; }
        }
        for (int n : new int[]{2000, 6000, 12000}) {
            double best = 1e9;
            for (int rep = 0; rep < 5; rep++) {
                PuffTracker tr = new PuffTracker();
                for (int i = 0; i < n; i++) tr.add(new P(), 3.6f, 0.333f, 0.6f, 1000);
                long t0 = System.nanoTime();
                for (int k = 0; k < 100; k++) tr.tick();
                best = Math.min(best, (System.nanoTime() - t0) / 100.0 / 1000.0);
            }
            b.append(String.format(Locale.ROOT, "puff bookkeeping: %d puffs followed: %.0f us per tick (pure part, JIT warm; the game's two field writes included)%n", n, best));
        }
        Path out = Path.of("build/prompt10-numbers.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString());
    }
}
