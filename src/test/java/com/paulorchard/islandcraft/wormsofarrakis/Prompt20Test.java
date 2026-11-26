package com.paulorchard.islandcraft.wormsofarrakis;

import com.paulorchard.islandcraft.wormsofarrakis.hunt.AggroEngine.PlayerState;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.Corridor;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.Corridor.Kind;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.Corridor.Top;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntConfig;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntEvent;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntManager;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.OpenDesert;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.SizeTable;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.SizeTable.Size;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.StrikeChoice;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.StrikeType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

/** Prompt 20: the size mode (depth or players), the open-desert verdict, the weights per mode (sizemode-1, sizemode-2). Engine-free. */
class Prompt20Test {
    private static OpenDesert.Verdict v(boolean open) {
        return new OpenDesert.Verdict(open, 441, open ? 441 : 100, 0, open ? 100.0 : 22.7, 98, List.of());
    }

    private static Corridor.Terrain sandWhere(BiFunction<Integer, Integer, Boolean> sand) {
        return (x, z) -> sand.apply(x, z) ? new Top(Kind.SAND, 64) : new Top(Kind.OTHER, 64);
    }

    // ---- the table ----

    @Test
    void thePlayersTable() {
        HuntConfig c = new HuntConfig();
        int[] n = {1, 2, 5, 6, 12};
        Size[] open = {Size.MEDIUM, Size.LARGE, Size.LARGE, Size.HUGE, Size.HUGE};
        Size[] closed = {Size.SMALL, Size.MEDIUM, Size.MEDIUM, Size.MEDIUM, Size.MEDIUM};
        for (int i = 0; i < n.length; i++) {
            assertEquals(open[i], SizeTable.forPlayers(n[i], true, c), n[i] + " open");
            assertEquals(closed[i], SizeTable.forPlayers(n[i], false, c), n[i] + " not open");
        }
        assertEquals(Size.MEDIUM, SizeTable.forPlayers(0, true, c), "a count of 0 never occurs; it is treated as one player");
        assertEquals(Size.SMALL, SizeTable.forPlayers(0, false, c));
    }

    @Test
    void theThresholdsAndSizesComeFromTheConfig() {
        HuntConfig c = new HuntConfig();
        c.playersLargeAt = 3; c.playersHugeAt = 4;
        assertEquals(Size.MEDIUM, SizeTable.forPlayers(2, true, c));
        assertEquals(Size.LARGE, SizeTable.forPlayers(3, true, c));
        assertEquals(Size.HUGE, SizeTable.forPlayers(4, true, c));
        c.playersSizeOpen = new String[]{"small", "small", "large"};
        c.playersSizeClosed = new String[]{"huge", "medium", "bogus"};
        assertEquals(Size.SMALL, SizeTable.forPlayers(1, true, c));
        assertEquals(Size.LARGE, SizeTable.forPlayers(9, true, c));
        assertEquals(Size.HUGE, SizeTable.forPlayers(1, false, c));
        assertEquals(Size.MEDIUM, SizeTable.forPlayers(9, false, c), "a bad word gives the default of that row");
    }

    // ---- the open-desert verdict ----

    @Test
    void theGridCoversTheDiscAndReadsEachColumnOnce() {
        HuntConfig c = new HuntConfig();
        assertEquals(441, OpenDesert.grid(48, 4).size(), "about 450 columns");
        Set<Long> seen = new HashSet<>();
        int[] reads = {0};
        Corridor.Terrain t = (x, z) -> { reads[0]++; assertTrue(seen.add(((long) x << 32) ^ (z & 0xffffffffL)), "a column read twice"); return new Top(Kind.SAND, 64); };
        OpenDesert.Verdict r = OpenDesert.check(t, 100.5, -20.5, null, c);
        assertEquals(441, reads[0]);
        assertEquals(441, r.samples());
        assertEquals(100.0, r.percent(), 1e-9);
        assertTrue(r.open());
        for (int[] o : OpenDesert.grid(48, 4)) assertTrue((long) o[0] * o[0] + (long) o[1] * o[1] <= 48L * 48L);
        assertTrue(OpenDesert.grid(48, 4).stream().anyMatch(o -> o[0] == 48 && o[1] == 0), "the rim is sampled");
    }

    @Test
    void exactlyTheThresholdPassesAndJustBelowFails() {
        HuntConfig c = new HuntConfig();
        List<int[]> g = OpenDesert.grid(c.openDesertRadius, c.openDesertSpacing);
        // 98 percent of 441 samples is 432.18: 433 pass, 432 do not
        assertTrue(failFirst(c, g, 8).open(), "8 failing: 98.19 percent");
        assertFalse(failFirst(c, g, 9).open(), "9 failing: 97.96 percent");
        // a threshold met exactly
        c.openDesertPercent = 100.0 * (441 - 5) / 441;
        assertTrue(failFirst(c, g, 5).open(), "exactly the threshold passes");
        assertFalse(failFirst(c, g, 6).open());
    }

    private static OpenDesert.Verdict failFirst(HuntConfig c, List<int[]> g, int fails) {
        Set<Long> bad = new HashSet<>();
        for (int i = 0; i < fails; i++) bad.add(((long) (g.get(i)[0] + 0) << 32) ^ ((g.get(i)[1] + 0) & 0xffffffffL));
        // the centre is 0,0 in block coordinates (a position of 0.5 floors to 0)
        return OpenDesert.check((x, z) -> bad.contains(((long) x << 32) ^ (z & 0xffffffffL)) ? new Top(Kind.OTHER, 64) : new Top(Kind.SAND, 64), 0.5, 0.5, null, c);
    }

    @Test
    void anUnloadedSampleCountsAsAFail() {
        HuntConfig c = new HuntConfig();
        OpenDesert.Verdict r = OpenDesert.check((x, z) -> x > 30 ? new Top(Kind.UNLOADED, 0) : new Top(Kind.SAND, 64), 0.5, 0.5, null, c);
        assertFalse(r.open());
        assertTrue(r.unloaded() > 0);
        assertEquals(r.samples() - r.sand(), r.unloaded());
        assertTrue(r.firstFails().get(0).text().contains("chunk not loaded"));
        OpenDesert.Verdict rock = OpenDesert.check(sandWhere((x, z) -> !(x == 8 && z == 0)), 0.5, 0.5, null, c);
        assertTrue(rock.open(), "one rock block of 441 is 99.8 percent");
        OpenDesert.Verdict many = OpenDesert.check(sandWhere((x, z) -> x < 10), 0.5, 0.5, null, c);
        assertFalse(many.open());
        assertEquals(5, many.firstFails().size(), "the first failing samples are named");
        assertTrue(many.firstFails().get(0).text().contains("offset"));
    }

    // ---- the mode ----

    @Test
    void depthModeGivesExactlyThePrompt13Sizes() {
        HuntConfig c = new HuntConfig();
        int[] d = {5, 8, 9, 16, 17, 32, 33, 60};
        Size[] s = {Size.SMALL, Size.SMALL, Size.MEDIUM, Size.MEDIUM, Size.LARGE, Size.LARGE, Size.HUGE, Size.HUGE};
        for (int i = 0; i < d.length; i++) {
            SizeTable.Decision dec = SizeTable.decide("depth", d[i], 9, v(false), c);
            assertEquals(s[i], dec.size(), "depth " + d[i]);
            assertEquals(SizeTable.forDepth(d[i], c), dec.size());
            assertTrue(dec.line().startsWith("size: mode=depth depth=" + d[i]));
        }
        assertEquals("depth", SizeTable.mode("nonsense"));
        assertEquals("players", SizeTable.mode(" Players "));
    }

    @Test
    void theShallowSafetyAppliesInBothModes() {
        HuntConfig c = new HuntConfig();
        for (String mode : new String[]{"depth", "players"})
            for (int d = 0; d <= 4; d++) assertNull(SizeTable.decide(mode, d, 3, v(true), c).size(), mode + " depth " + d);
        assertNotNull(SizeTable.decide("players", 5, 1, v(true), c).size(), "a depth of 5 is enough in players mode");
        assertEquals(Size.MEDIUM, SizeTable.decide("players", 5, 1, v(true), c).size());
        assertEquals(Size.HUGE, SizeTable.decide("players", 60, 6, v(true), c).size(), "depth is not used for the size in players mode");
        assertEquals(Size.SMALL, SizeTable.decide("players", 60, 1, v(false), c).size());
    }

    @Test
    void theLogLineOfThePlayersRule() {
        HuntConfig c = new HuntConfig();
        String line = SizeTable.decide("players", 12, 3, new OpenDesert.Verdict(true, 441, 441, 0, 100.0, 98, List.of()), c).line();
        assertEquals("size: mode=players list=3 open=yes sand=100.0% of 441 samples depth=12 -> large W32", line);
    }

    // ---- an event keeps its mode ----

    private static final class World implements HuntManager.Env {
        final List<PlayerState> ps = new ArrayList<>();
        boolean open = true;
        int depth = 12;
        public List<PlayerState> players() { return ps; }
        public int depth(UUID id) { return depth; }
        @Override public OpenDesert.Verdict openDesert(UUID id, Size size) { return v(open); }
    }

    private static PlayerState player(int n, double x) { return new PlayerState(new UUID(0, n), "p" + n, "overworld", x, 65, 0, true, false, false, false, false, false, false, true, true); }

    private static HuntEvent trigger(HuntConfig cfg, World w, int players) {
        w.ps.clear();
        for (int i = 1; i <= players; i++) w.ps.add(player(i, i * 3));
        HuntManager m = new HuntManager(cfg, new HuntManager.Listener() {}, (e, vv, ww, s) -> {});
        m.tick(0.05, w);
        assertEquals(HuntManager.TriggerResult.STARTED, m.forceTrigger(new UUID(0, 1), null, w));
        HuntEvent e = m.eventOf(new UUID(0, 1));
        assertNotNull(e);
        return e;
    }

    @Test
    void aFakeWorldGivesTheExpectedSizeInEachModeAndTheEventKeepsItsMode() {
        HuntConfig cfg = new HuntConfig();
        World w = new World();
        // depth mode: depth 12 -> medium whatever the players
        cfg.sizeMode = "depth";
        HuntEvent a = trigger(cfg, w, 3);
        assertEquals(Size.MEDIUM, a.size);
        assertEquals("depth", a.sizeMode());
        // players mode: alone, open: medium with a 24 s stalk; 3 players open: large
        cfg.sizeMode = "players";
        HuntEvent b = trigger(cfg, w, 1);
        assertEquals(Size.MEDIUM, b.size);
        assertEquals(24.0, b.stalkSeconds, 1e-9, "the stalk formula is unchanged: width + 8 s");
        assertTrue(b.sizeInfo().contains("mode=players list=1 open=yes"));
        HuntEvent c3 = trigger(cfg, w, 3);
        assertEquals(Size.LARGE, c3.size);
        // not open: small alone
        w.open = false;
        assertEquals(Size.SMALL, trigger(cfg, w, 1).size);
        assertEquals(Size.MEDIUM, trigger(cfg, w, 3).size);
        // the same depth 12 in depth mode again: the old rule
        cfg.sizeMode = "depth";
        assertEquals(Size.MEDIUM, trigger(cfg, w, 6).size);
        // the mode is stored on the event and a later change does not touch it
        cfg.sizeMode = "players";
        assertEquals("depth", a.sizeMode());
        cfg.sizeMode = "depth";
        assertEquals("players", b.sizeMode());
        assertEquals(Size.MEDIUM, b.size);
        // the shallow safety: no event in either mode
        w.depth = 3;
        for (String mode : new String[]{"depth", "players"}) {
            cfg.sizeMode = mode;
            w.ps.clear(); w.ps.add(player(1, 0));
            HuntManager m = new HuntManager(cfg, new HuntManager.Listener() {}, (e, vv, ww, s) -> {});
            m.tick(0.05, w);
            assertEquals(HuntManager.TriggerResult.SHALLOW, m.forceTrigger(new UUID(0, 1), null, w), mode);
        }
        // a forced size is used in both modes
        w.depth = 12;
        cfg.sizeMode = "players";
        w.ps.clear(); w.ps.add(player(1, 0));
        HuntManager m = new HuntManager(cfg, new HuntManager.Listener() {}, (e, vv, ww, s) -> {});
        m.tick(0.05, w);
        assertEquals(HuntManager.TriggerResult.STARTED, m.forceTrigger(new UUID(0, 1), Size.HUGE, w));
        assertEquals(Size.HUGE, m.eventOf(new UUID(0, 1)).size);
    }

    // ---- the weights per mode ----

    @Test
    void depthModeKeepsThePrompt19Weights() {
        HuntConfig c = new HuntConfig();
        double[][] p19 = {{10, 70, 10, 10}, {70, 5, 5, 20}, {70, 5, 5, 20}, {0, 0, 0, 100}};
        for (Size s : Size.values()) {
            assertArrayEquals(p19[s.ordinal()], c.weightsFor("depth", s));
            assertArrayEquals(c.weightsFor(s), c.weightsFor("depth", s));
            assertArrayEquals(c.weightsFor(s), c.weightsFor("nonsense", s), "any other word is depth");
        }
        // the old keys StrikeWeights.<size> are the depth mode's: changing them moves depth only
        c.strikeWeightsBySize[0] = new double[]{1, 1, 1, 1};
        assertArrayEquals(new double[]{1, 1, 1, 1}, c.weightsFor("depth", Size.SMALL));
        assertArrayEquals(new double[]{70, 5, 5, 20}, c.weightsFor("players", Size.SMALL));
    }

    @Test
    void playersWeightsAreCrestHeavyAndHugeIsEngulfWithTheMediumCrashFallback() {
        HuntConfig c = new HuntConfig();
        for (Size s : new Size[]{Size.SMALL, Size.MEDIUM, Size.LARGE}) assertArrayEquals(new double[]{70, 5, 5, 20}, c.weightsFor("players", s));
        assertArrayEquals(new double[]{0, 0, 0, 100}, c.weightsFor("players", Size.HUGE));
        StrikeChoice.Availability all = new StrikeChoice.Availability();
        all.available.addAll(List.of(StrikeType.CREST, StrikeType.CRASH, StrikeType.EMERGE, StrikeType.ENGULF));
        Random r = new Random(11);
        int crest = 0, n = 40000;
        for (int i = 0; i < n; i++) if (StrikeChoice.decide(all, c, "players", Size.SMALL, r, null).choice().type() == StrikeType.CREST) crest++;
        assertEquals(70.0, 100.0 * crest / n, 1.0, "players small is crest-heavy");
        int crestDepth = 0;
        for (int i = 0; i < n; i++) if (StrikeChoice.decide(all, c, "depth", Size.SMALL, r, null).choice().type() == StrikeType.CREST) crestDepth++;
        assertEquals(10.0, 100.0 * crestDepth / n, 1.0, "depth small keeps the prompt 19 share");
        StrikeChoice.Decision huge = StrikeChoice.decide(all, c, "players", Size.HUGE, r, null);
        assertEquals(StrikeType.ENGULF, huge.choice().type());
        StrikeChoice.Availability noEngulf = new StrikeChoice.Availability();
        noEngulf.available.addAll(List.of(StrikeType.CRASH, StrikeType.EMERGE));
        StrikeChoice.Decision fb = StrikeChoice.decide(noEngulf, c, "players", Size.HUGE, r, null);
        assertTrue(fb.fallback());
        assertEquals(Size.MEDIUM, fb.strikeSize());
        assertEquals(StrikeType.CRASH, fb.choice().type());
        assertTrue(StrikeChoice.skipSet(c, "players", Size.HUGE, null).contains(StrikeType.CREST), "players huge needs no crest columns");
        assertTrue(StrikeChoice.skipSet(c, "players", Size.MEDIUM, null).isEmpty());
    }

    @Test
    void badPlayersWeightsFallBackToTheirDefaultsWithALine() {
        HuntConfig c = new HuntConfig();
        c.playersWeights[1] = new double[]{-5, 1, 1, 1};
        c.playersWeights[2] = new double[]{0, 0, 0, 0};
        assertArrayEquals(new double[]{70, 5, 5, 20}, c.weightsFor("players", Size.MEDIUM));
        assertArrayEquals(new double[]{70, 5, 5, 20}, c.weightsFor("players", Size.LARGE));
        long lines = c.weightProblems().stream().filter(s -> s.contains("size.PlayersMode.StrikeWeights")).count();
        assertEquals(2, lines);
        c.playersSizeOpen[0] = "gigantic";
        assertTrue(c.weightProblems().stream().anyMatch(s -> s.contains("size.PlayersMode.Open")));
        assertTrue(new HuntConfig().weightProblems().isEmpty());
        assertTrue(String.join(" ", new HuntConfig().describe()).contains("SizeMode=depth"));
    }
}
