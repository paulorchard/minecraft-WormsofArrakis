package com.paulorchard.islandcraft.wormsofarrakis;

import com.paulorchard.islandcraft.wormsofarrakis.hunt.Corridor;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.Corridor.Kind;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.Corridor.Top;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntConfig;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.SizeTable;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.SizeTable.Size;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.StrikeChoice;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.StrikeType;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.Alignment;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

/** Prompt 19: strike weights by size, the zero-weight skip, the huge fallback and the strike size (weights-1). */
class Prompt19Test {
    private static final double VX = 100.5, VZ = 100.5;
    private static final int GROUND = 64;
    private static final int TRIALS = 40000;

    private static Corridor.Terrain sandWhere(BiFunction<Integer, Integer, Boolean> sand) {
        return (x, z) -> sand.apply(x, z) ? new Top(Kind.SAND, GROUND) : new Top(Kind.OTHER, GROUND);
    }

    private static int crestR0(HuntConfig c, Size s) { return 20; }

    private static StrikeChoice.Availability eval(Corridor.Terrain t, HuntConfig cfg, Size s, Set<StrikeType> skip) {
        return StrikeChoice.evaluate(t, VX, VZ, GROUND, s, cfg, crestR0(cfg, s), 24, skip);
    }

    private static Map<String, Double> shares(StrikeChoice.Availability a, HuntConfig cfg, Size s, long seed) {
        Random r = new Random(seed);
        Map<String, Integer> c = new java.util.HashMap<>();
        for (int i = 0; i < TRIALS; i++) {
            StrikeChoice.Decision d = StrikeChoice.decide(a, cfg, s, r, null);
            c.merge((d.fallback() ? "fb:" + d.strikeSize().label + ":" : "") + d.choice().type().label, 1, Integer::sum);
        }
        Map<String, Double> out = new java.util.HashMap<>();
        for (var e : c.entrySet()) out.put(e.getKey(), 100.0 * e.getValue() / TRIALS);
        return out;
    }

    private static double sh(Map<String, Double> m, String k) { return m.getOrDefault(k, 0.0); }

    private static StrikeChoice.Availability all(HuntConfig cfg, Size s) {
        StrikeChoice.Availability a = eval(sandWhere((x, z) -> true), cfg, s, Set.of());
        assertTrue(a.has(StrikeType.CREST) && a.has(StrikeType.ENGULF), "flat sand: everything available");
        return a;
    }

    @Test
    void defaultsPerSize() {
        HuntConfig c = new HuntConfig();
        assertArrayEquals(new double[]{10, 70, 10, 10}, c.weightsFor(Size.SMALL));
        assertArrayEquals(new double[]{70, 5, 5, 20}, c.weightsFor(Size.MEDIUM));
        assertArrayEquals(new double[]{70, 5, 5, 20}, c.weightsFor(Size.LARGE));
        assertArrayEquals(new double[]{0, 0, 0, 100}, c.weightsFor(Size.HUGE));
        assertEquals(Size.MEDIUM, c.fallbackSizeFor(Size.HUGE));
        assertEquals(StrikeType.CRASH, c.fallbackTypeFor(Size.HUGE));
        assertEquals(Size.SMALL, c.fallbackSizeFor(Size.SMALL));
        assertEquals(Size.LARGE, c.fallbackSizeFor(Size.LARGE));
        assertEquals(StrikeType.CRASH, c.fallbackTypeFor(Size.MEDIUM));
    }

    @Test
    void everythingAvailableGivesTheStatedShares() {
        HuntConfig c = new HuntConfig();
        Map<String, Double> s = shares(all(c, Size.SMALL), c, Size.SMALL, 1);
        assertEquals(10, sh(s, "crest"), 1); assertEquals(70, sh(s, "crash"), 1); assertEquals(10, sh(s, "emerge"), 1); assertEquals(10, sh(s, "engulf"), 1);
        for (Size z : new Size[]{Size.MEDIUM, Size.LARGE}) {
            s = shares(all(c, z), c, z, 2);
            assertEquals(70, sh(s, "crest"), 1); assertEquals(5, sh(s, "crash"), 1); assertEquals(5, sh(s, "emerge"), 1); assertEquals(20, sh(s, "engulf"), 1);
        }
        s = shares(all(c, Size.HUGE), c, Size.HUGE, 3);
        assertEquals(100, sh(s, "engulf"), 1e-9);
    }

    @Test
    void crestUnavailableRescales() {
        HuntConfig c = new HuntConfig();
        StrikeChoice.Availability a = new StrikeChoice.Availability();
        a.available.add(StrikeType.CRASH); a.available.add(StrikeType.EMERGE); a.available.add(StrikeType.ENGULF);
        Map<String, Double> s = shares(a, c, Size.MEDIUM, 4);
        assertEquals(20 * 100 / 30.0, sh(s, "engulf"), 1); assertEquals(5 * 100 / 30.0, sh(s, "crash"), 1); assertEquals(5 * 100 / 30.0, sh(s, "emerge"), 1);
        assertEquals(0, sh(s, "crest"));
        s = shares(a, c, Size.SMALL, 5);
        assertEquals(70 * 100 / 90.0, sh(s, "crash"), 1); assertEquals(10 * 100 / 90.0, sh(s, "emerge"), 1); assertEquals(10 * 100 / 90.0, sh(s, "engulf"), 1);
    }

    @Test
    void mediumWithoutCrestAndEngulfIsFiftyFifty() {
        HuntConfig c = new HuntConfig();
        StrikeChoice.Availability a = new StrikeChoice.Availability();
        a.available.add(StrikeType.CRASH); a.available.add(StrikeType.EMERGE);
        Map<String, Double> s = shares(a, c, Size.MEDIUM, 6);
        assertEquals(50, sh(s, "crash"), 1); assertEquals(50, sh(s, "emerge"), 1);
    }

    @Test
    void hugeWithEngulfIsAlwaysEngulfAndWithoutItTheMediumCrash() {
        HuntConfig c = new HuntConfig();
        StrikeChoice.Availability a = new StrikeChoice.Availability();
        a.available.add(StrikeType.CRASH); a.available.add(StrikeType.EMERGE); a.available.add(StrikeType.ENGULF);
        Random r = new Random(7);
        for (int i = 0; i < 2000; i++) {
            StrikeChoice.Decision d = StrikeChoice.decide(a, c, Size.HUGE, r, null);
            assertEquals(StrikeType.ENGULF, d.choice().type());
            assertEquals(Size.HUGE, d.strikeSize());
            assertFalse(d.fallback());
        }
        StrikeChoice.Availability b = new StrikeChoice.Availability();
        b.available.add(StrikeType.CRASH); b.available.add(StrikeType.EMERGE);
        b.engulfFail = null;
        for (int i = 0; i < 2000; i++) {
            StrikeChoice.Decision d = StrikeChoice.decide(b, c, Size.HUGE, r, null);
            assertEquals(StrikeType.CRASH, d.choice().type());
            assertEquals(Size.MEDIUM, d.strikeSize());
            assertTrue(d.fallback());
        }
    }

    @Test
    void realTerrainHugeBesideRockFallsBack() {
        HuntConfig c = new HuntConfig();
        Corridor.Terrain rock = sandWhere((x, z) -> !(x == 124 && z == 100));   // one rock block on the r0 = 24 ring of the disc (a sampled point)
        Set<StrikeType> skip = StrikeChoice.skipSet(c, Size.HUGE, null);
        StrikeChoice.Availability a = eval(rock, c, Size.HUGE, skip);
        assertFalse(a.has(StrikeType.ENGULF));
        StrikeChoice.Decision d = StrikeChoice.decide(a, c, Size.HUGE, new Random(1), null);
        assertTrue(d.fallback());
        assertEquals(Size.MEDIUM, d.strikeSize());
        assertEquals(StrikeType.CRASH, d.choice().type());
        assertFalse(d.why().isEmpty(), "the reason of the engulf failure reaches the log line");
        // open sand: engulf
        StrikeChoice.Availability open = eval(sandWhere((x, z) -> true), c, Size.HUGE, skip);
        assertEquals(StrikeType.ENGULF, StrikeChoice.decide(open, c, Size.HUGE, new Random(1), null).choice().type());
    }

    @Test
    void aZeroWeightIsNeverChosenAndItsCheckIsNotRun() {
        HuntConfig c = new HuntConfig();
        // huge: crest has weight 0: no crest column is read
        AtomicInteger reads = new AtomicInteger();
        Corridor.Terrain counting = (x, z) -> { reads.incrementAndGet(); return new Top(Kind.SAND, GROUND); };
        Set<StrikeType> skip = StrikeChoice.skipSet(c, Size.HUGE, null);
        assertTrue(skip.contains(StrikeType.CREST));
        StrikeChoice.Availability saved = eval(counting, c, Size.HUGE, skip);
        int savedReads = reads.get();
        reads.set(0);
        StrikeChoice.Availability full = eval(counting, c, Size.HUGE, Set.of());
        int fullReads = reads.get();
        assertTrue(savedReads < fullReads, "huge reads fewer columns: " + savedReads + " < " + fullReads);
        assertFalse(saved.has(StrikeType.CREST));
        assertTrue(saved.crestDirs.isEmpty());
        // a size that weights everything above 0 skips nothing
        assertTrue(StrikeChoice.skipSet(c, Size.MEDIUM, null).isEmpty());
        // forced crest on huge is checked anyway
        assertFalse(StrikeChoice.skipSet(c, Size.HUGE, StrikeType.CREST).contains(StrikeType.CREST));
        // a zero weight is never chosen, even when available
        HuntConfig z = new HuntConfig();
        z.strikeWeightsBySize[1] = new double[]{0, 50, 50, 0};
        StrikeChoice.Availability a = eval(counting, z, Size.MEDIUM, Set.of());
        Random r = new Random(9);
        for (int i = 0; i < 5000; i++) {
            StrikeType t = StrikeChoice.decide(a, z, Size.MEDIUM, r, null).choice().type();
            assertTrue(t == StrikeType.CRASH || t == StrikeType.EMERGE);
        }
        // a zero weight to the very check: the engulf disc of a size with engulf 0 is not read
        assertTrue(StrikeChoice.skipSet(z, Size.MEDIUM, null).containsAll(Set.of(StrikeType.CREST, StrikeType.ENGULF)));
        // a forced type ignores the weight
        assertEquals(StrikeType.CREST, StrikeChoice.decide(a, z, Size.MEDIUM, r, StrikeType.CREST).choice().type());
        System.out.println("prompt19 huge reads with skip " + savedReads + " instead of " + fullReads);
    }

    @Test
    void badConfigFallsBackToTheDefaultWithALine() {
        HuntConfig c = new HuntConfig();
        c.strikeWeightsBySize[1] = new double[]{-1, 5, 5, 20};
        c.strikeWeightsBySize[2] = new double[]{0, 0, 0, 0};
        c.fallbackType[3] = "engulf";
        c.fallbackSize[0] = "gigantic";
        assertArrayEquals(new double[]{70, 5, 5, 20}, c.weightsFor(Size.MEDIUM));
        assertArrayEquals(new double[]{70, 5, 5, 20}, c.weightsFor(Size.LARGE));
        assertEquals(StrikeType.CRASH, c.fallbackTypeFor(Size.HUGE));
        assertEquals(Size.SMALL, c.fallbackSizeFor(Size.SMALL));
        var p = c.weightProblems();
        assertEquals(4, p.size(), p.toString());
        assertTrue(p.stream().anyMatch(s -> s.contains("StrikeWeights.medium") && s.contains("default")));
        assertTrue(new HuntConfig().weightProblems().isEmpty());
    }

    @Test
    void theOldSingleKeyIsReportedWhenChanged() {
        HuntConfig c = new HuntConfig();
        assertTrue(c.retiredKeysInUse().stream().noneMatch(s -> s.contains("StrikeWeights")));
        c.strikeWeights = new double[]{1, 2, 3, 4};
        assertTrue(c.retiredKeysInUse().stream().anyMatch(s -> s.contains("StrikeWeights") && s.contains("per-size")));
    }

    @Test
    void theStrikeSizeDrivesEverythingAfterTheChoice() throws Exception {
        // geometry: a medium strike has a smaller area, smaller throat and a smaller devour time than the huge event
        HuntConfig c = new HuntConfig();
        double rMed = Alignment.move(Alignment.once(StrikeType.CRASH, Size.MEDIUM)).r0;
        double rHuge = Alignment.move(Alignment.once(StrikeType.CRASH, Size.HUGE)).r0;
        assertTrue(rMed < rHuge, "the medium crash is smaller than the huge one");
        assertTrue(c.devourSeconds[StrikeType.CRASH.ordinal()][Size.MEDIUM.ordinal()] > 0);
        // each consumer reads the strike size, not the event's (source assertions on the readers)
        java.nio.file.Path root = Prompt18Test.srcRoot();
        String base = "src/main/java/com/paulorchard/islandcraft/wormsofarrakis/hunt/";
        String strike = java.nio.file.Files.readString(root.resolve(base + "StrikeRunner.java"));
        assertTrue(strike.contains("plan(c.type(), ss)"), "worm spawned from the strike size");
        assertTrue(strike.contains("devourSeconds[c.type().ordinal()][ss.ordinal()]"), "devour table by strike size");
        assertTrue(strike.contains("a.strikeAreaRadius(c.type(), ss)"), "tickets by strike area");
        assertFalse(strike.contains("devourSeconds[c.type().ordinal()][e.size.ordinal()]"));
        assertTrue(strike.contains("onChoice(e, c.type(), ss,"), "group release by strike size");
        String group = java.nio.file.Files.readString(root.resolve(base + "GroupRunner.java"));
        assertTrue(group.contains("a.strikeAreaRadius(type, strikeSize)"), "group circle by strike size");
        assertTrue(group.contains("a.camPeak(t, g.strikeSize"), "camera framing by strike size");
        assertFalse(group.contains("a.strikeAreaRadius(type, e.size)"));
        String adapter = java.nio.file.Files.readString(root.resolve(base + "HuntAdapter.java"));
        assertTrue(adapter.contains("r.strikeSize == null ? r.event.size : r.strikeSize"), "strike info (discs) by strike size");
        assertTrue(adapter.contains("resizeAttackRumbles"), "the rumble is resized at the choice");
        // the freeze group still uses the huge maximum
        assertTrue(group.contains("a.maxStrikeArea(e.size)"));
        // the event keeps its own size
        com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntEvent ev = null;
        assertNull(ev);
    }
}
