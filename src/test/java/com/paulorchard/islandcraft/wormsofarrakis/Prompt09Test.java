package com.paulorchard.islandcraft.wormsofarrakis;

import com.paulorchard.islandcraft.wormsofarrakis.fx.CrashFx;
import com.paulorchard.islandcraft.wormsofarrakis.fx.CrashFx.Anchor;
import com.paulorchard.islandcraft.wormsofarrakis.fx.FxAnchors;
import com.paulorchard.islandcraft.wormsofarrakis.fx.FxOptions;
import com.paulorchard.islandcraft.wormsofarrakis.fx.FxPlayer;
import com.paulorchard.islandcraft.wormsofarrakis.fx.FxSink;
import com.paulorchard.islandcraft.wormsofarrakis.fx.ParticleModel;
import com.paulorchard.islandcraft.wormsofarrakis.path.MouthAnim;
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

/** Prompt 09: the crash effects sheet: anchors (part 1), sounds (part 2) and particles (part 3). */
class Prompt09Test {
    private static final double DT = 0.05;   // one game tick

    private static WormSpec spec(String move, double w, int segs, String opts) {
        return MoveOptions.parse(move, w, segs, opts, false).spec();
    }

    private static Moves.Move move(WormSpec s) { return MoveFactory.get(s); }

    private static FxAnchors.Anchors anchors(WormSpec s) { return FxAnchors.of(move(s), MoveFactory.closeTime(s), MoveFactory.closeEnd(s)); }

    private static WormSpec crash(double w) { return spec("crash", w, w <= 6 ? 60 : w <= 16 ? 120 : 300, ""); }

    // ---- part 1: the anchors ----

    @Test
    void anchorsOfCrashAreInOrderAndW16MatchesThePrevis() {
        for (double w : new double[]{6, 16, 48}) {
            FxAnchors.Anchors a = anchors(crash(w));
            String ctx = "W" + w + " " + a.line();
            assertTrue(a.approach() < a.breach(), ctx);
            assertTrue(a.breach() < a.closeStart(), ctx);
            assertTrue(a.closeStart() <= a.riseEnd() + 1e-9, ctx);
            assertTrue(a.riseEnd() < a.fallStart(), ctx);
            assertTrue(a.fallStart() < a.reentry(), ctx);
            assertTrue(a.reentry() <= a.end() + 1e-9, ctx);
        }
        FxAnchors.Anchors a = anchors(crash(16));
        assertEquals(2.20, a.approach(), 0.1);
        assertEquals(4.10, a.breach(), 0.1);
        assertEquals(6.56, a.closeStart(), 0.1);
        assertEquals(7.46, a.riseEnd(), 0.1);
        assertEquals(8.96, a.fallStart(), 0.1);
        assertEquals(11.57, a.reentry(), 0.1);
        assertEquals(11.84, a.end(), 0.1);
    }

    @Test
    void closeStartIsTheMouthAnimationStart() {
        for (String m : new String[]{"crash", "emerge", "engulf"}) {
            WormSpec s = spec(m, 16, m.equals("crash") ? 120 : 60, "");
            assertEquals(MoveFactory.mouthTiming(s).tStart(), anchors(s).closeStart(), 1e-9, m);
        }
        WormSpec s = spec("crash", 16, 120, "close_time=2 close_end=0.5");
        assertEquals(MoveFactory.mouthTiming(s).tStart(), anchors(s).closeStart(), 1e-9);
    }

    @Test
    void emergeAndEngulfHaveFallStartAndReentry() {
        for (String m : new String[]{"emerge", "engulf"}) {
            for (double w : new double[]{6, 16, 48}) {
                WormSpec s = spec(m, w, w <= 16 ? 60 : 300, "");
                FxAnchors.Anchors a = anchors(s);
                Moves.Move mv = move(s);
                String ctx = m + " W" + w + " " + a.line();
                assertFalse(Double.isNaN(a.fallStart()), ctx);
                assertFalse(Double.isNaN(a.reentry()), ctx);
                assertEquals(mv.advanceTime + mv.hold, a.fallStart(), 1e-9, ctx);   // the start of the retreat (emerge) or the drop (engulf)
                assertTrue(a.approach() < a.breach() && a.breach() < a.riseEnd() + 1e-9 && a.riseEnd() < a.fallStart() && a.fallStart() < a.reentry() && a.reentry() <= a.end() + 1e-9, ctx);
                assertTrue(FxAnchors.tipHeight(mv, a.reentry() + 0.02) <= 0, ctx);   // the tip is below the base level on the way down
                assertTrue(FxAnchors.tipHeight(mv, a.reentry() - 0.02) > 0, ctx);
            }
        }
    }

    @Test
    void crestHasOnlyBreachAndReentry() {
        FxAnchors.Anchors a = anchors(spec("crest", 16, 80, "height=1.3"));
        assertFalse(Double.isNaN(a.breach()));
        assertFalse(Double.isNaN(a.reentry()));
        assertTrue(a.breach() < a.reentry());
        assertTrue(Double.isNaN(a.riseEnd()));
        assertTrue(Double.isNaN(a.fallStart()));
        // the rows that need riseEnd or fallStart play nothing on a crest
        List<FxPlayer.Play> plays = FxPlayer.expand(a, FxOptions.defaults(true), 1);
        for (FxPlayer.Play p : plays) assertNotEquals(Anchor.RISE_END, CrashFx.CUES.get(p.cue()).anchor());
        assertTrue(plays.size() > 0);
    }

    @Test
    void theAnchorsAreComputedNotTyped() {
        // a different speed gives different anchors: the rise at 24 blocks per second is shorter
        FxAnchors.Anchors slow = anchors(spec("crash", 16, 120, "speed=12")), fast = anchors(spec("crash", 16, 120, "speed=24"));
        assertTrue(fast.riseEnd() < slow.riseEnd() - 1);
    }

    @Test
    void expandingACueGivesFromPlusOffPlusIEvery() {
        FxAnchors.Anchors a = anchors(crash(16));
        List<FxPlayer.Play> plays = FxPlayer.expand(a, FxOptions.defaults(true), 5);
        for (int c = 0; c < CrashFx.CUES.size(); c++) {
            CrashFx.Cue cue = CrashFx.CUES.get(c);
            List<Double> times = new ArrayList<>();
            for (FxPlayer.Play p : plays) if (p.cue() == c) times.add(p.time());
            assertEquals(cue.rep(), times.size(), cue.name());
            for (int i = 0; i < cue.rep(); i++) assertEquals(a.at(cue.anchor()) + cue.off() + i * cue.every(), times.get(i), 1e-9, cue.name() + " #" + i);
        }
    }

    @Test
    void theRandomIsSeeded() {
        FxAnchors.Anchors a = anchors(crash(16));
        assertEquals(FxPlayer.expand(a, FxOptions.defaults(true), 9), FxPlayer.expand(a, FxOptions.defaults(true), 9));
        assertNotEquals(FxPlayer.expand(a, FxOptions.defaults(true), 9), FxPlayer.expand(a, FxOptions.defaults(true), 10));
        Run r1 = run(move(crash(16)), FxOptions.defaults(true), 3, 0), r2 = run(move(crash(16)), FxOptions.defaults(true), 3, 0);
        assertEquals(r1.spawns.size(), r2.spawns.size());
        for (int i = 0; i < r1.spawns.size(); i += 97) assertEquals(r1.spawns.get(i), r2.spawns.get(i));
        assertEquals(r1.sounds, r2.sounds);
    }

    // ---- part 2: sounds ----

    @Test
    void everySoundEventExistsInVanilla() throws IOException {
        // the registry cannot be started in a unit test without the game's data; the names are checked against the 26.1.2 SoundEvents source text, and by the client sink at play time
        Path src = Path.of("build/moddev/artifacts/minecraft-patched-26.1.2.129-sources.jar");
        if (!Files.exists(src)) return;   // the sources jar is a build artifact of this project
        String text;
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(src.toFile())) {
            text = new String(z.getInputStream(z.getEntry("net/minecraft/sounds/SoundEvents.java")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        for (CrashFx.Cue c : CrashFx.CUES)
            assertTrue(text.contains("register(\"" + c.event() + "\")") || text.contains("registerForHolder(\"" + c.event() + "\")"), c.event());
        assertEquals(14, CrashFx.CUES.stream().map(CrashFx.Cue::event).distinct().count());
    }

    @Test
    void theScheduleOfCrashW16Has82PlaysAndMonotoneRows() {
        assertEquals(82, CrashFx.totalPlays());
        List<FxPlayer.Play> plays = FxPlayer.expand(anchors(crash(16)), FxOptions.defaults(true), 1);
        assertEquals(82, plays.size());
        for (int c = 0; c < CrashFx.CUES.size(); c++) {
            double last = -1e9;
            for (FxPlayer.Play p : plays) {
                if (p.cue() != c) continue;
                assertTrue(p.time() >= last, CrashFx.CUES.get(c).name());
                last = p.time();
            }
        }
        for (int i = 1; i < plays.size(); i++) assertTrue(plays.get(i).time() >= plays.get(i - 1).time());   // and the whole list is sorted
    }

    @Test
    void cancellingAtAnInstantDropsExactlyTheLaterPlays() {
        Moves.Move m = move(crash(16));
        for (double cancelAt : new double[]{1.0, 3.9, 4.3, 6.0, 8.0, 10.0, 11.9}) {
            Run r = new Run();
            FxPlayer p = new FxPlayer(m, FxOptions.defaults(true), MoveFactory.closeTime(crash(16)), MoveFactory.closeEnd(crash(16)), 1);
            List<FxPlayer.Play> all = p.schedule();
            double tau = 0;
            while (tau + DT <= cancelAt + 1e-9) { tau += DT; r.at = tau; p.step(tau, 0, r.sink()); }
            long before = r.sounds.size();
            final double tauC = tau;
            p.cancel();
            while (tau < 13) { tau += DT; r.at = tau; p.step(tau, 0, r.sink()); }
            assertEquals(before, r.sounds.size(), "nothing plays after the cancel at " + cancelAt);
            long played = all.stream().filter(pl -> pl.time() <= tauC + 1e-9).count();
            assertEquals(played, before, "exactly the plays up to " + cancelAt);
            assertEquals(all.size() - played, FxPlayer.countAfter(all, tauC), "the later plays are the ones dropped");
        }
    }

    @Test
    void volumeSwitchesAndFxOff() {
        FxAnchors.Anchors a = anchors(crash(16));
        assertTrue(FxPlayer.expand(a, new FxOptions(true, true, true, 0, 1), 1).isEmpty(), "fxvol=0");
        assertTrue(FxPlayer.expand(a, new FxOptions(true, false, true, 1, 1), 1).isEmpty(), "fxsound=off");
        assertTrue(FxPlayer.expand(a, new FxOptions(false, true, true, 1, 1), 1).isEmpty(), "fx=off");
        List<FxPlayer.Play> half = FxPlayer.expand(a, new FxOptions(true, true, true, 0.5, 1), 1);
        for (FxPlayer.Play p : half) assertEquals(CrashFx.CUES.get(p.cue()).vol() * 0.5, p.volume(), 1e-9);
        // the players of such moves make no sound and (fx off) no particle at all
        for (FxOptions o : new FxOptions[]{new FxOptions(true, true, true, 0, 0), new FxOptions(false, true, true, 1, 1)}) {
            Run r = run(move(crash(16)), o, 1, 0);
            assertEquals(0, r.sounds.size());
            assertEquals(0, r.spawns.size());
        }
    }

    @Test
    void noCueForAMoveWithFxOffAndTheDefaultsOfTheMoves() {
        assertFalse(MoveFactory.fxOptions(spec("crest", 16, 80, "")).on(), "crest default off");
        assertTrue(MoveFactory.fxOptions(spec("crest", 16, 80, "fx=on")).on());
        for (String m : new String[]{"crash", "emerge", "engulf"}) assertTrue(MoveFactory.fxOptions(spec(m, 16, 60, "")).on(), m + " default on");
        WormSpec off = spec("crash", 16, 120, "fx=off");
        assertFalse(MoveFactory.fxOptions(off).on());
        assertTrue(FxPlayer.expand(anchors(off), MoveFactory.fxOptions(off), 1).isEmpty());
        Run r = run(move(off), MoveFactory.fxOptions(off), 1, 0);
        assertEquals(0, r.sounds.size() + r.spawns.size());
    }

    @Test
    void theClampsOfTheKeysAreNotedAndBadValuesRefused() {
        MoveOptions.Parsed p = MoveOptions.parse("crash", 16, 120, "fxvol=5 fxcount=9", false);
        assertEquals(2, p.notes().size());
        assertEquals(CrashFx.MAX_FXVOL, MoveFactory.fxOptions(p.spec()).vol());
        assertEquals(CrashFx.MAX_FXCOUNT, MoveFactory.fxOptions(p.spec()).count());
        for (String bad : new String[]{"fx=maybe", "fxsound=1", "fxparticles=yes", "fxvol=loud", "fxcount=many"})
            assertThrows(MoveOptions.Bad.class, () -> MoveOptions.parse("crash", 16, 120, bad, false), bad);
        MoveOptions.Parsed c = MoveOptions.parse("crest", 16, 80, "fx=on", false);
        assertEquals(1, c.notes().size());
        assertTrue(c.notes().get(0).contains("approach, breach and reentry"));
        // the fx keys are in the generated moves text of the four moves and nowhere else
        for (String line : MoveOptions.lines())
            for (String k : new String[]{"fx=", "fxsound=", "fxparticles=", "fxvol=", "fxcount="}) assertTrue(line.contains(k), line);
        assertTrue(MoveOptions.line("crest").contains("fx=off"));
        assertTrue(MoveOptions.line("crash").contains("fx=on"));
    }

    @Test
    void theFxKeysDoNotChangeThePathAndKeepTheOlderSpecsUnchanged() {
        WormSpec base = spec("crash", 16, 120, "");
        assertTrue(base.p().length <= 21, "a default spec keeps its older length");
        WormSpec tuned = spec("crash", 16, 120, "fxvol=0.5 fxcount=2 fxsound=off fxparticles=off");
        assertSame(MoveFactory.get(base), MoveFactory.get(tuned), "the same cached move: no key of fx changes the path or the timing");
        // the mouth keys and the fx keys together, and mouthpose and freeze on top, keep each other
        WormSpec both = spec("crash", 16, 120, "close_time=2 fxcount=2");
        assertEquals(2, MoveFactory.fxOptions(both).count());
        assertEquals(2, MoveFactory.closeTime(both));
        WormSpec posed = MoveFactory.withMouthPose(both, 0.5);
        assertEquals(2, MoveFactory.fxOptions(posed).count());
        assertEquals(0.5, posed.p(25, -1));
    }

    @Test
    void pitchAndVolumeRules() {
        FxAnchors.Anchors a = anchors(crash(16));
        for (FxPlayer.Play p : FxPlayer.expand(a, FxOptions.defaults(true), 4)) {
            CrashFx.Cue c = CrashFx.CUES.get(p.cue());
            assertTrue(p.pitch() >= c.pitch() * (1 - c.jit()) - 1e-9 && p.pitch() <= c.pitch() * (1 + c.jit()) + 1e-9, c.name());
            assertEquals(c.vol(), p.volume(), 1e-9);
        }
        // the tremor row is stored at 0.25 (the sheet's value); the engine clamps the pitch to 0.5..2, so 0.5 is what is heard
        assertEquals(0.25, CrashFx.CUES.get(0).pitch());
    }

    // ---- part 3: particles ----

    /** One run of the player over a loop at game-tick steps. */
    private static final class Run {
        final List<FxSink.Spawn> spawns = new ArrayList<>();
        final List<double[]> spawnTime = new ArrayList<>();   // {tau}
        final List<String> sounds = new ArrayList<>();
        final List<String> logs = new ArrayList<>();
        double at;
        FxPlayer player;

        FxSink sink() {
            return new FxSink() {
                @Override public void sound(CrashFx.Cue cue, double x, double y, double z, double volume, double pitch) {
                    sounds.add(String.format(Locale.ROOT, "%.2f %s %.3f %.3f z=%.2f", at, cue.name(), volume, pitch, z));
                }
                @Override public void particle(Spawn p) { spawns.add(p); spawnTime.add(new double[]{at}); }
                @Override public void log(String line) { logs.add(line); }
            };
        }
    }

    private static Run run(Moves.Move m, FxOptions o, long seed, int externalLive) {
        Run r = new Run();
        FxPlayer p = new FxPlayer(m, o, MouthAnim.DEFAULT_CLOSE_TIME, MouthAnim.CLOSE_END_DEFAULT_MARK, seed);
        r.player = p;
        for (double tau = DT; tau <= m.cycle - 1e-9; tau += DT) {
            r.at = tau;
            p.step(tau, externalLive, r.sink());
        }
        return r;
    }

    private static int row(String start) {
        for (int i = 0; i < CrashFx.EMITTERS.size(); i++) if (CrashFx.EMITTERS.get(i).name().startsWith(start)) return i;
        throw new IllegalArgumentException(start);
    }

    @Test
    void particleCountsPerRowMatchTheSheetAtW16() {
        Moves.Move m = move(crash(16));
        FxOptions o = FxOptions.defaults(true);
        Run r = run(m, o, 1, 0);
        FxAnchors.Anchors a = r.player.anchors();
        double scale = o.countScale(m.r0);
        assertEquals(m.r0 / 8, scale, 1e-9);
        for (int i = 0; i < CrashFx.EMITTERS.size(); i++) {
            CrashFx.Emitter e = CrashFx.EMITTERS.get(i);
            double expected = e.isBurst() ? e.burst() * scale : e.rate() * (a.at(e.until()) - (a.at(e.from()) + e.off())) * scale;
            long got = r.player.spawnedByRow(i);
            if (e.place() == CrashFx.Place.OVERHEAD) {
                assertTrue(got < expected && got > 0.2 * expected, e.name() + " thinned by proximity: " + got + " of " + expected);
                continue;
            }
            assertEquals(expected, got, Math.max(0.05 * expected, e.isBurst() ? 3 : 0), e.name() + ": " + got + " vs " + expected);
        }
    }

    @Test
    void noCrossingRowEmitsBeforeTheBreachAndTheMouthRowIsNearTheTip() {
        Moves.Move m = move(crash(16));
        Run r = run(m, FxOptions.defaults(true), 1, 0);
        double breach = r.player.anchors().breach();
        for (int k = 0; k < r.spawns.size(); k++) {
            FxSink.Spawn s = r.spawns.get(k);
            CrashFx.Emitter e = CrashFx.EMITTERS.get(s.row());
            double t = r.spawnTime.get(k)[0];
            if (e.place() == CrashFx.Place.CROSSING) assertTrue(t >= breach - 1e-9, e.name() + " at " + t);
            if (e.place() == CrashFx.Place.OVERHEAD) assertTrue(t <= breach + DT, "tremor grains stop at the breach: " + t);
            if (e.place() == CrashFx.Place.MOUTH) {
                FxPlayer.FxGeometry.Snapshot g = FxPlayer.FxGeometry.at(m, t);
                double d = Math.hypot(Math.hypot(s.x(), s.z() - g.tipZ), s.y() - g.tipY);
                assertTrue(d <= e.ring() * m.r0 * 0.5 + 1e-6, "mouth particle " + d + " from the tip");
                // normal to the head axis: the offset from the tip has no component along the axis
                assertEquals(0, (s.z() - g.tipZ) * g.axZ + (s.y() - g.tipY) * g.axY, 1e-6);
            }
        }
        assertTrue(r.player.spawnedByRow(row("Mouth clamp")) > 0);
    }

    /** The crossing z at loop time t, or the last one before it (after the re-entry the whole worm is under the sand: the crossing is the hole). */
    private static double lastCross(Moves.Move m, double t) {
        for (double u = t; u >= 0; u -= DT) {
            double z = FxPlayer.FxGeometry.at(m, u).crossZ;
            if (!Double.isNaN(z)) return z;
        }
        return Double.NaN;
    }

    @Test
    void crossingParticlesStartOnTheRingAndInwardRowsPointInward() {
        Moves.Move m = move(crash(16));
        Run r = run(m, FxOptions.defaults(true), 1, 0);
        int inward = row("Fall: sand pouring"), outward = row("Rise: sand pouring"), burst = row("Fall: re-entry burst");
        int nIn = 0, nInOk = 0, nOut = 0, nOutOk = 0, onRing = 0, nBurst = 0;
        for (int k = 0; k < r.spawns.size(); k++) {
            FxSink.Spawn s = r.spawns.get(k);
            CrashFx.Emitter e = CrashFx.EMITTERS.get(s.row());
            if (e.place() != CrashFx.Place.CROSSING) continue;
            double t = r.spawnTime.get(k)[0];
            FxPlayer.FxGeometry.Snapshot g = FxPlayer.FxGeometry.at(m, t);
            // the crossing of the tick (or the last one while the body leaves): the particle is on the ring around some crossing point
            double cz = lastCross(m, t);   // the crossing of the tick, or the last one once the worm has gone under (the hole it went into)
            if (Double.isNaN(cz)) continue;
            double rho = Math.hypot(s.x(), s.z() - cz);
            if (Math.abs(rho - e.ring() * m.r0) < 1e-6 * m.r0 + 1e-6) onRing++;
            double radial = (s.vx() * s.x() + s.vz() * (s.z() - cz)) / Math.max(rho, 1e-9);
            if (s.row() == inward) { nIn++; if (radial < 0) nInOk++; }
            if (s.row() == outward) { nOut++; if (radial > 0) nOutOk++; }
            if (s.row() == burst) nBurst++;
        }
        assertTrue(nIn > 100 && nOut > 100 && nBurst > 100, "in " + nIn + " out " + nOut + " burst " + nBurst);
        assertTrue(nInOk > 0.95 * nIn, "out < 0 points inward: " + nInOk + "/" + nIn);
        assertTrue(nOutOk > 0.95 * nOut, "out > 0 points outward: " + nOutOk + "/" + nOut);
        assertTrue(onRing > 0.9 * r.spawns.stream().filter(s -> CrashFx.EMITTERS.get(s.row()).place() == CrashFx.Place.CROSSING).count(), "on the ring of radius ring x r0: " + onRing);
    }

    @Test
    void overheadGrainsAreInTheDiscAndThinnedByProximity() {
        Moves.Move m = move(crash(16));
        Run r = run(m, FxOptions.defaults(true), 1, 0);
        int row = row("Tremor");
        int early = 0, late = 0;   // first and second half of the approach: the closer the head, the more grains
        double a0 = r.player.anchors().approach(), b0 = r.player.anchors().breach();
        for (int k = 0; k < r.spawns.size(); k++) {
            FxSink.Spawn s = r.spawns.get(k);
            if (s.row() != row) continue;
            double t = r.spawnTime.get(k)[0];
            FxPlayer.FxGeometry.Snapshot g = FxPlayer.FxGeometry.at(m, t);
            assertTrue(g.tipY < 0, "none once the tip is above the sand: " + t);
            assertTrue(Math.hypot(s.x(), s.z() - g.tipZ) <= CrashFx.EMITTERS.get(row).ring() * m.r0 + 1e-6, "inside the disc");
            assertEquals(FxPlayer.BASE_PUSH, s.y(), 1e-9);
            if (t < 0.5 * (a0 + b0)) early++; else late++;
        }
        assertTrue(early + late > 50);
        assertTrue(late > early, "more grains as the head comes up: " + early + " then " + late);
    }

    @Test
    void theBodyRowMakesGrainsOnRingsAboveTheSand() {
        Moves.Move m = move(crash(16));
        Run r = run(m, FxOptions.defaults(true), 1, 0);
        int row = row("Hold: sand sliding");
        assertTrue(r.player.spawnedByRow(row) > 50);
        for (int k = 0; k < r.spawns.size(); k++) {
            FxSink.Spawn s = r.spawns.get(k);
            if (s.row() == row) assertTrue(s.y() >= 0, "never under the sand");
        }
    }

    @Test
    void countScalingAndSwitches() {
        Moves.Move m = move(crash(16));
        long full = run(m, new FxOptions(true, true, true, 1, 1), 1, 0).player.spawned();
        long doubled = run(m, new FxOptions(true, true, true, 1, 2), 1, 0).player.spawned();
        assertEquals(2.0, doubled / (double) full, 0.05);
        assertEquals(0, run(m, new FxOptions(true, true, true, 1, 0), 1, 0).spawns.size(), "fxcount=0");
        assertEquals(0, run(m, new FxOptions(true, true, false, 1, 1), 1, 0).spawns.size(), "fxparticles=off");
        Run quiet = run(m, new FxOptions(true, false, true, 1, 1), 1, 0);
        assertEquals(0, quiet.sounds.size(), "fxsound=off");
        assertTrue(quiet.spawns.size() > 1000, "the particles do not depend on the sound switch");
        // the default scale follows r0 / 8 and is capped
        assertEquals(m.r0 / 8, FxOptions.defaults(true).countScale(m.r0), 1e-9);
        assertEquals(CrashFx.MAX_AUTO_SCALE, FxOptions.defaults(true).countScale(24), 1e-9);
    }

    @Test
    void runtimeSwitchesAndSolo() {
        Moves.Move m = move(crash(16));
        Run r = new Run();
        FxPlayer p = new FxPlayer(m, FxOptions.defaults(true), MouthAnim.DEFAULT_CLOSE_TIME, MouthAnim.CLOSE_END_DEFAULT_MARK, 1);
        p.setRuntime(true, false, CrashFx.Group.BREACH);
        for (double tau = DT; tau <= m.cycle - 1e-9; tau += DT) { r.at = tau; p.step(tau, 0, r.sink()); }
        assertEquals(0, r.spawns.size(), "fx particles off");
        assertEquals(1 + 1 + 7 + 1, r.sounds.size(), "solo breach: boom, smash, 7 spray, earth emerges");
        for (String s : r.sounds) assertTrue(s.contains("Breach"), s);
    }

    @Test
    void aFrozenClockPlaysNothingAndAJumpDoesNotReplay() {
        Moves.Move m = move(crash(16));
        Run r = new Run();
        FxPlayer p = new FxPlayer(m, FxOptions.defaults(true), MouthAnim.DEFAULT_CLOSE_TIME, MouthAnim.CLOSE_END_DEFAULT_MARK, 1);
        double tau = 0;
        while (tau < 5) { tau += DT; r.at = tau; p.step(tau, 0, r.sink()); }
        int s0 = r.sounds.size(), p0 = r.spawns.size();
        for (int i = 0; i < 100; i++) p.step(tau, 0, r.sink());   // frozen: the same script time again and again
        assertEquals(s0, r.sounds.size());
        assertEquals(p0, r.spawns.size());
        r.at = 9;
        p.step(9.0, 0, r.sink());   // freeze at a time: a jump forward of four seconds
        assertEquals(s0, r.sounds.size(), "nothing skipped is played");
        assertEquals(p0, r.spawns.size());
        tau = 9;
        while (tau < 10) { tau += DT; r.at = tau; p.step(tau, 0, r.sink()); }
        assertTrue(r.sounds.size() > s0, "and it goes on from there");
    }

    @Test
    void aNewLoopStartsTheSheetAgain() {
        Moves.Move m = move(crash(16));
        Run r = new Run();
        FxPlayer p = new FxPlayer(m, FxOptions.defaults(true), MouthAnim.DEFAULT_CLOSE_TIME, MouthAnim.CLOSE_END_DEFAULT_MARK, 1);
        for (double tau = DT; tau <= 2 * m.cycle; tau += DT) { r.at = tau; p.step(tau, 0, r.sink()); }
        assertEquals(2 * 82, r.sounds.size(), "the whole sheet in each loop");
    }

    @Test
    void theBudgetThinsEveryRowEvenlyAndNeverDropsARow() {
        Moves.Move m = move(crash(16));
        Run free = run(m, FxOptions.defaults(true), 1, 0);
        Run tight = run(m, FxOptions.defaults(true), 1, CrashFx.BUDGET - 40);   // the client already holds almost the whole budget
        assertTrue(tight.player.thinned() > 0);
        assertTrue(tight.player.cappedTicks() > 0);
        assertTrue(tight.logs.stream().anyMatch(l -> l.contains("fx: capped")), "the cap is logged");
        assertEquals(1, tight.logs.stream().filter(l -> l.contains("fx: capped")).count(), "once, not every tick");
        for (int i = 0; i < CrashFx.EMITTERS.size(); i++)
            if (free.player.spawnedByRow(i) > 0) assertTrue(tight.player.spawnedByRow(i) > 0, "row kept: " + CrashFx.EMITTERS.get(i).name());
        assertTrue(tight.player.spawned() < free.player.spawned());
    }

    @Test
    void particleModelNumbers() {
        assertEquals(1.0, ParticleModel.driftFactor(CrashFx.PType.BLOCK, 30));
        assertEquals(1.0, ParticleModel.driftFactor(CrashFx.PType.EXPLOSION, 16));
        assertTrue(ParticleModel.driftFactor(CrashFx.PType.CLOUD, 20) > 1.1 && ParticleModel.driftFactor(CrashFx.PType.CLOUD, 20) < 1.5);
        assertTrue(ParticleModel.driftFactor(CrashFx.PType.DUST_PLUME, 25) > 4 && ParticleModel.driftFactor(CrashFx.PType.DUST_PLUME, 25) < 10);
        assertEquals(4.5, 4 * (1 - 0.5 * ParticleModel.explosionParam(4.5)), 1e-9, "the size parameter gives the wanted width (quad = 2 x (1 - size/2) half-size)");
        assertEquals(1.0, ParticleModel.scaleFor(CrashFx.PType.BLOCK, 0.15), 1e-9);
        assertEquals(3.0 / 0.225, ParticleModel.scaleFor(CrashFx.PType.DUST_PLUME, 3.0), 1e-9);
    }

    @Test
    void theTextOfTheFxCommandFormatsForEveryMove() {
        for (WormSpec s : new WormSpec[]{crash(16), spec("emerge", 16, 60, ""), spec("engulf", 16, 60, ""), spec("crest", 16, 80, "fx=on height=1.3")}) {
            Moves.Move m = move(s);
            FxAnchors.Anchors a = anchors(s);
            String h = FxPlayer.headerLine(m.label(), m.width, MoveFactory.fxOptions(s), m.r0, true, false, CrashFx.Group.BREACH);
            assertTrue(h.startsWith("fx of " + m.label() + " W16: fx=on"), h);
            assertTrue(h.contains("particles off, solo breach"), h);
            List<String> lines = FxPlayer.chatLines(a);
            assertEquals(CrashFx.Group.values().length + CrashFx.EMITTERS.size(), lines.size());
            for (CrashFx.Group g : CrashFx.Group.values()) assertTrue(lines.stream().anyMatch(l -> l.startsWith("sounds [" + g.label() + "]: ")), g.label());
            for (CrashFx.Emitter e : CrashFx.EMITTERS) assertTrue(lines.stream().anyMatch(l -> l.startsWith("particles: " + e.name())), e.name());
            assertEquals(CrashFx.CUES.size() + CrashFx.EMITTERS.size(), FxPlayer.sheetLines(a).size());
        }
        assertEquals(CrashFx.Group.TREMOR, CrashFx.Group.byLabel("tremor"));
        assertNull(CrashFx.Group.byLabel("foo"));
    }

    // ---- the numbers of the report (written to build/prompt09-numbers.txt) ----

    @Test
    void numbersForTheReport() throws IOException {
        StringBuilder b = new StringBuilder();
        for (String mv : new String[]{"crash", "emerge", "engulf"}) {
            for (double w : new double[]{6, 16, 48}) {
                WormSpec s = mv.equals("crash") ? crash(w) : spec(mv, w, w <= 16 ? 60 : 300, "");
                FxAnchors.Anchors a = anchors(s);
                b.append(String.format(Locale.ROOT, "%s W%.0f r0=%.2f: %s%n", mv, w, move(s).r0, a.line()));
            }
        }
        b.append(String.format(Locale.ROOT, "crest W16 height=1.3: %s%n", anchors(spec("crest", 16, 80, "height=1.3")).line()));
        for (String mv : new String[]{"crash", "emerge", "engulf"}) {
            for (double w : new double[]{6, 16, 48}) {
                WormSpec s = mv.equals("crash") ? crash(w) : spec(mv, w, w <= 16 ? 60 : 300, "");
                Moves.Move m = move(s);
                FxOptions o = FxOptions.defaults(true);
                Run r = run(m, o, 1, 0);
                FxAnchors.Anchors a = r.player.anchors();
                // spawn per second: the busiest second (a sliding window of 20 ticks), and the average from the breach to the end
                int[] perTick = new int[(int) Math.ceil(m.cycle / DT) + 2];
                for (int k = 0; k < r.spawns.size(); k++) perTick[(int) Math.round(r.spawnTime.get(k)[0] / DT)]++;
                int best = 0, win = 0;
                for (int i = 0; i < perTick.length; i++) { win += perTick[i]; if (i >= 20) win -= perTick[i - 20]; best = Math.max(best, win); }
                double avg = r.player.spawned() / Math.max(1e-9, a.end() - a.breach());
                b.append(String.format(Locale.ROOT, "particles %s W%.0f: scale=%.2f total=%d peakAlive=%d busiestSecond=%d avgPerSecond(breach..end)=%.0f thinned=%d capped=%d%n",
                        mv, w, r.player.scale(), r.player.spawned(), r.player.peakAlive(), best, avg, r.player.thinned(), r.player.cappedTicks()));
                // the base mask rows alone
                long mask = 0;
                for (int i = 0; i < CrashFx.EMITTERS.size(); i++) if (CrashFx.isBaseMask(CrashFx.EMITTERS.get(i))) mask += r.player.spawnedByRow(i);
                b.append(String.format(Locale.ROOT, "  base mask rows: %d particles = %.0f per second%n", mask, mask / Math.max(1e-9, a.end() - a.breach())));
            }
        }
        for (double w : new double[]{6, 16, 48}) {
            Run rr = run(move(crash(w)), FxOptions.defaults(true), 1, 0);
            b.append(String.format(Locale.ROOT, "per row, crash W%.0f (scale %.2f):%n", w, rr.player.scale()));
            for (int i = 0; i < CrashFx.EMITTERS.size(); i++) {
                CrashFx.Emitter e = CrashFx.EMITTERS.get(i);
                b.append(String.format(Locale.ROOT, "  %-44s %-10s %6d%n", e.name(), e.type().id, rr.player.spawnedByRow(i)));
            }
        }
        // uncapped scale at W48 (r0 / 8 without the cap of 2)
        Moves.Move m48 = move(crash(48));
        Run r = run(m48, new FxOptions(true, true, true, 1, 3.0 / 2.0), 1, 0);   // 1.5 x 2.0 = 3.0 = r0/8 at W48
        b.append(String.format(Locale.ROOT, "crash W48 with the uncapped scale 3.0: total=%d peakAlive=%d thinned=%d capped=%d%n", r.player.spawned(), r.player.peakAlive(), r.player.thinned(), r.player.cappedTicks()));
        for (double life : new double[]{20, 30}) b.append(String.format(Locale.ROOT, "driftFactor cloud/dust at life %.0f: %.2f / %.2f%n", life, ParticleModel.driftFactor(CrashFx.PType.CLOUD, (int) life), ParticleModel.driftFactor(CrashFx.PType.DUST_PLUME, (int) life)));
        Path out = Path.of("build/prompt09-numbers.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString());
    }
}
