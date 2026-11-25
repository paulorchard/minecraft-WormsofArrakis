package com.paulorchard.islandcraft.wormsofarrakis;

import com.paulorchard.islandcraft.wormsofarrakis.fx.CrashFx;
import com.paulorchard.islandcraft.wormsofarrakis.fx.CrashFx.PType;
import com.paulorchard.islandcraft.wormsofarrakis.fx.EruptionFx;
import com.paulorchard.islandcraft.wormsofarrakis.fx.FxAnchors;
import com.paulorchard.islandcraft.wormsofarrakis.fx.FxOptions;
import com.paulorchard.islandcraft.wormsofarrakis.fx.FxPlayer;
import com.paulorchard.islandcraft.wormsofarrakis.fx.FxSink;
import com.paulorchard.islandcraft.wormsofarrakis.fx.SizeFactor;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveFactory;
import com.paulorchard.islandcraft.wormsofarrakis.test.WormSpec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** Prompt 12 part 1: the effects scaled by the size of the worm. */
class Prompt12Test {
    private static final int E0 = CrashFx.EMITTERS.size();

    private static double sizeOfRow(int row) { return row < E0 ? CrashFx.EMITTERS.get(row).size() : EruptionFx.ROWS.get(row - E0).size(); }
    private static PType typeOfRow(int row) { return row < E0 ? CrashFx.EMITTERS.get(row).type() : EruptionFx.ROWS.get(row - E0).type(); }
    private static double endScaleOfRow(int row) { return row < E0 ? 1.0 : EruptionFx.ROWS.get(row - E0).endScale(); }

    @Test
    void theFactorMatchesTheOwnersPointsAndHandValues() {
        double[][] t = {{-5, 0}, {0, 0}, {6, 6 / 32.0}, {8, 0.25}, {12, 0.375}, {16, 0.5}, {24, 0.5 + 8 / 64.0}, {32, 0.75}, {40, 0.75 + 8 / 64.0}, {48, 1.0}, {64, 1.0}, {1000, 1.0}};
        for (double[] x : t) assertEquals(x[1], SizeFactor.of(x[0]), 1e-12, "W" + x[0]);
        assertEquals(0.1875, SizeFactor.of(6), 1e-12);
        double prev = 0;
        for (double w = 0; w <= 80; w += 0.25) {
            double f = SizeFactor.of(w);
            assertTrue(f >= prev - 1e-12 && f <= 1.0, "monotone and never above 1 at " + w);
            prev = f;
        }
        for (int i = 0; i < SizeFactor.W.length; i++) assertEquals(SizeFactor.F[i], SizeFactor.of(SizeFactor.W[i]), 1e-12, "the anchors");
        assertEquals("size factor: W8=0.25 W16=0.5 W32=0.75 W48=1.0", SizeFactor.logLine());
    }

    @Test
    void theCountScaleIsTheFactorTimesFxcountAndTheOldScaleIsGone() {
        assertEquals(0.5, FxOptions.defaults(true).countScale(16), 1e-12);
        assertEquals(1.5, new FxOptions(true, true, true, 1, 2).countScale(32), 1e-12);
        assertEquals(1.0, FxOptions.defaults(true).countScale(200), 1e-12, "no 2.0 any more");
    }

    @Test
    void volumesAreVolTimesFactorTimesFxvolAndTheRangeFollows() {
        for (double w : new double[]{8, 16, 32, 48}) {
            WormSpec s = Prompt10Test.crash(w);
            FxAnchors.Anchors a = Prompt10Test.anchors(s);
            double f = SizeFactor.of(w);
            var ref = FxPlayer.expand(a, FxOptions.defaults(true), 3, 1.0);
            var got = FxPlayer.expand(a, new FxOptions(true, true, true, 1.5, 1), 3, f);
            assertEquals(ref.size(), got.size(), "the number of sound plays does not change with the factor");
            for (int i = 0; i < ref.size(); i++) {
                assertEquals(ref.get(i).volume() * f * 1.5, got.get(i).volume(), 1e-9);
                assertEquals(ref.get(i).pitch(), got.get(i).pitch(), 1e-12, "pitch unchanged");
                assertEquals(ref.get(i).time(), got.get(i).time(), 1e-12, "timing unchanged");
            }
        }
        double loud = CrashFx.CUES.stream().mapToDouble(CrashFx.Cue::vol).max().getAsDouble();
        assertEquals(0.5, (16 * loud * SizeFactor.of(16)) / (16 * loud * SizeFactor.of(48)), 1e-12, "the range at W16 is half of that at W48");
        FxPlayer p16 = new FxPlayer(MoveFactory.get(Prompt10Test.crash(16)), FxOptions.defaults(true), 0.9, 0, 1);
        assertEquals(0.5, p16.sizeFactor(), 1e-12);
        FxPlayer.Play first = p16.schedule().get(0);
        assertEquals(CrashFx.CUES.get(first.cue()).vol() * 0.5, first.volume(), 1e-9);
    }

    @Test
    void countsPerRowAreTheSheetCountTimesTheFactorAtEverySize() {
        for (double w : new double[]{8, 16, 32, 48}) {
            WormSpec s = Prompt10Test.crash(w);
            double f = SizeFactor.of(w);
            Prompt10Test.Run r = Prompt10Test.run(s, FxOptions.full(true), 2, 1_000_000);
            assertEquals(f, r.player.scale(), 1e-12);
            FxAnchors.Anchors a = r.player.anchors();
            for (int i = 0; i < E0; i++) {
                CrashFx.Emitter e = CrashFx.EMITTERS.get(i);
                if (e.place() == CrashFx.Place.OVERHEAD) continue;
                double ref = e.isBurst() ? e.burst() : e.rate() * (a.at(e.until()) - (a.at(e.from()) + e.off()));
                double want = ref * f;
                assertEquals(want, r.byRow(i), Math.max(e.isBurst() ? 3 : 1, 0.05 * want), "W" + w + " " + e.name());
            }
            for (int j = 0; j < EruptionFx.ROWS.size(); j++) {
                EruptionFx.Row row = EruptionFx.ROWS.get(j);
                double want = EruptionFx.countAtScaleOne(row) * f;
                assertEquals(want, r.byRow(E0 + j), Math.max(3, 0.05 * want), "W" + w + " " + row.name());
            }
        }
    }

    @Test
    void fxcountStaysAMultiplierOnTopAndTheCrestScalesLinearly() {
        for (double w : new double[]{8, 16, 48}) {
            WormSpec s = Prompt10Test.crest(w, 0.7, "");
            long one = Prompt10Test.run(s, FxOptions.full(true), 4, 1_000_000).player.spawned();
            long two = Prompt10Test.run(s, new FxOptions(true, true, true, 1, 2, true, true), 4, 1_000_000).player.spawned();
            assertEquals(2.0, (double) two / one, 0.03, "crest W" + w + ": fxcount x2 doubles it");
        }
        long sand16 = 0, sand48 = 0;
        for (double w : new double[]{16, 48}) {
            Prompt10Test.Run r = Prompt10Test.run(Prompt10Test.crest(w, 0.7, ""), FxOptions.full(true), 4, 1_000_000);
            int fountain = -1;
            for (int j = 0; j < EruptionFx.ROWS.size(); j++) if (EruptionFx.ROWS.get(j).name().contains("sand fountain")) fountain = E0 + j;
            long n = r.byRow(fountain);
            double f = SizeFactor.of(w);
            assertTrue(n >= 3500 * f * 0.95 && n <= 3500 * f * 1.45, "crest W" + w + " fountain " + n);
            if (w == 16) sand16 = n; else sand48 = n;
        }
        assertTrue(sand48 > 1.6 * sand16 * 0.9 && sand48 < 2.8 * sand16, sand16 + " " + sand48);
        FxAnchors.Anchors ca = Prompt10Test.anchors(Prompt10Test.crest(16, 0.7, ""));
        assertEquals(FxPlayer.expand(ca, FxOptions.full(true), 1, 1.0).size(), FxPlayer.expand(ca, FxOptions.full(true), 1, 0.5).size());
    }

    @Test
    void nonBlockSizesFollowTheFactorBlocksAndChunksKeepTheirSizeAndTheShrinkIsUnchanged() {
        Prompt10Test.Run r8 = Prompt10Test.run(Prompt10Test.crash(8), FxOptions.full(true), 2, 1_000_000);
        Prompt10Test.Run r16 = Prompt10Test.run(Prompt10Test.crash(16), FxOptions.full(true), 2, 1_000_000);
        Prompt10Test.Run r48 = Prompt10Test.run(Prompt10Test.crash(48), FxOptions.full(true), 2, 1_000_000);
        int scaled = 0, kept = 0;
        for (Prompt10Test.Run run : new Prompt10Test.Run[]{r8, r16, r48}) {
            double f = run.player.sizeFactor();
            for (FxSink.Spawn p : run.spawns) {
                boolean block = typeOfRow(p.row()) == PType.BLOCK || typeOfRow(p.row()) == PType.CHUNK;
                assertEquals(typeOfRow(p.row()), p.type());
                if (block) {
                    assertEquals(sizeOfRow(p.row()), p.size(), 1e-12, "sand blocks keep their size at every width");
                    kept++;
                } else {
                    assertEquals(sizeOfRow(p.row()) * f, p.size(), 1e-12, "non-blocks follow the factor");
                    scaled++;
                }
                assertEquals(endScaleOfRow(p.row()), p.endScale(), 1e-12, "the shrink to endScale is unchanged");
            }
        }
        assertTrue(scaled > 1000 && kept > 1000, scaled + " " + kept);
        for (FxSink.Spawn p : r16.spawns) if (p.type() == PType.DUST_PLUME) { assertEquals(sizeOfRow(p.row()) * 0.5, p.size(), 1e-12); break; }
        for (FxSink.Spawn p : r48.spawns) if (p.type() == PType.DUST_PLUME) { assertEquals(sizeOfRow(p.row()), p.size(), 1e-12); break; }
    }

    @Test
    void theBudgetStillHoldsAtW48WithTheNewCounts() {
        Prompt10Test.Run r = Prompt10Test.run(Prompt10Test.crash(48), FxOptions.full(true), 4);
        assertTrue(r.player.peakAlive() <= CrashFx.BUDGET + 40, "peak " + r.player.peakAlive());
        assertTrue(r.player.spawned() > 30000);
        Prompt10Test.Run big = Prompt10Test.run(Prompt10Test.crash(48), new FxOptions(true, true, true, 1, 3, true, true), 4);
        assertTrue(big.player.thinned() > 0 && big.player.peakAlive() <= CrashFx.BUDGET + 40, "fxcount 3 is thinned to the budget");
    }

    @Test
    void aBurstRowNeverRoundsToZeroAboveAFactorOfOneTenth() {
        Prompt10Test.Run r = Prompt10Test.run(Prompt10Test.crash(8), FxOptions.full(true), 11, 1_000_000);
        for (int i = 0; i < E0; i++) {
            CrashFx.Emitter e = CrashFx.EMITTERS.get(i);
            if (e.isBurst() && e.burst() >= 1 && e.place() != CrashFx.Place.OVERHEAD) assertTrue(r.byRow(i) >= 1, e.name());
        }
    }

    @Test
    void numbersForTheReport() throws IOException {
        StringBuilder b = new StringBuilder();
        double loud = CrashFx.CUES.stream().mapToDouble(CrashFx.Cue::vol).max().getAsDouble();
        for (String mv : new String[]{"crash", "crest"}) for (double w : new double[]{8, 16, 32, 48}) {
            WormSpec s = mv.equals("crash") ? Prompt10Test.crash(w) : Prompt10Test.crest(w, 0.7, "");
            double f = SizeFactor.of(w), old = Math.min(MoveFactory.get(s).r0 / 8.0, 2.0);
            Prompt10Test.Run after = Prompt10Test.run(s, FxOptions.full(true), 7);
            Prompt10Test.Run before = Prompt10Test.run(s, new FxOptions(true, true, true, 1, old / f, true, true), 7);
            b.append(String.format(Locale.ROOT, "%s W%.0f: factor %.3f, old scale %.2f | before particles %d peak %d thinned %d | after particles %d peak %d thinned %d | loudest cue %.1f range before %.0f after %.0f blocks%n",
                    mv, w, f, old, before.player.spawned(), before.player.peakAlive(), before.player.thinned(), after.player.spawned(), after.player.peakAlive(), after.player.thinned(), loud, 16 * loud, 16 * loud * f));
        }
        b.append("sheet rows (crash sheet, then eruption rows): type, size\n");
        for (CrashFx.Emitter e : CrashFx.EMITTERS) b.append(String.format(Locale.ROOT, "  %s: %s size %s -> %s%n", e.name(), e.type().id, e.size(), e.type() == PType.BLOCK || e.type() == PType.CHUNK ? "kept" : "x factor"));
        for (EruptionFx.Row e : EruptionFx.ROWS) b.append(String.format(Locale.ROOT, "  %s: %s size %s -> %s%n", e.name(), e.type().id, e.size(), e.type() == PType.BLOCK || e.type() == PType.CHUNK ? "kept" : "x factor"));
        Files.writeString(Path.of("build/prompt12-numbers.txt"), b.toString());
    }
}
