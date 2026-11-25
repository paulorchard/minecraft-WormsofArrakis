package com.paulorchard.islandcraft.wormsofarrakis;

import com.paulorchard.islandcraft.wormsofarrakis.hunt.Alignment;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.AggroEngine.PlayerState;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.Corridor;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.Corridor.Kind;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.Corridor.Top;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.DevourGate;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntConfig;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntEvent;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntManager;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntTexts;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.ScanTerrain;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.SizeTable;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.StrikeChoice;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.StrikeType;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.StrikeType.Dir;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveFactory;
import com.paulorchard.islandcraft.wormsofarrakis.test.WormSpec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

/** Prompt 14 parts 2 to 5: alignment, devour, corridor, disc, odds, scan budget, strike phase (engine-free). */
class Prompt14StrikeTest {
    private static final double DT = 0.05;
    private static final double VX = 100.5, VZ = 100.5;
    private static final int GROUND = 64;
    private static final HuntConfig CFG = new HuntConfig();
    /** The single weight set of prompts 14 to 18, kept here to test the rescaling maths (prompt 19 made the weights per size). */
    private static final double[] LEGACY = {30, 25, 25, 20};

    private static SizeTable.Size[] sizes() { return SizeTable.Size.values(); }

    private static int crestR0(SizeTable.Size s) { return (int) Math.round(Alignment.move(Alignment.once(StrikeType.CREST, s)).r0); }

    private static Corridor.Params params(SizeTable.Size s) { return StrikeChoice.crestParams(s, CFG, crestR0(s)); }

    private static Corridor.Terrain flat() { return (x, z) -> new Top(Kind.SAND, GROUND); }

    private static Corridor.Terrain sandWhere(BiFunction<Integer, Integer, Boolean> sand) { return (x, z) -> sand.apply(x, z) ? new Top(Kind.SAND, GROUND) : new Top(Kind.OTHER, GROUND); }

    // ================= part 2: alignment =================

    @Test
    void theEntryCrossingIsAtTheVictimForEveryMoveSizeAndYaw() {
        for (StrikeType t : StrikeType.values()) for (SizeTable.Size s : sizes()) {
            Moves.Move m = Alignment.move(Alignment.once(t, s));
            double tau = Alignment.entryTau(m);
            double[] tip = m.tip(m.at(tau)[0]);
            assertEquals(0.0, tip[1], 0.02, t + " " + s + ": the head tip meets the sand surface at the entry crossing");
            assertEquals(tip[0], Alignment.entryZ(m), 1e-9);
            assertEquals(m.window()[0], tau, 0.011 + 1e-9, "the move's own window starts at the entry crossing");
            for (double yaw : new double[]{0, 90, 180, 270, 37.5}) {
                double[] o = Alignment.origin(VX, VZ, Alignment.entryZ(m), yaw);
                double[] w = Alignment.worldOf(o, Alignment.entryZ(m), yaw);
                assertEquals(VX, w[0], 1e-9, t + " " + s + " yaw " + yaw);
                assertEquals(VZ, w[1], 1e-9, t + " " + s + " yaw " + yaw);
            }
        }
    }

    @Test
    void theYawsPointTheMoveAlongTheCardinalDirections() {
        for (Dir d : Dir.values()) {
            double[] o = Alignment.origin(0, 0, 10, d.yaw);
            double[] w = Alignment.worldOf(o, 20, d.yaw);   // 10 blocks past the entry along the move
            assertEquals(d.dx * 10.0, w[0], 1e-9, d.name());
            assertEquals(d.dz * 10.0, w[1], 1e-9, d.name());
        }
    }

    @Test
    void theStrikeMoveIsTheCommandsPresetPlayedOnce() {
        for (StrikeType t : StrikeType.values()) for (SizeTable.Size s : sizes()) {
            WormSpec once = Alignment.once(t, s);
            assertEquals(0, once.p(19, 1), "loop off: the worm removes itself");
            assertEquals(-1, once.p(17, -1), "not frozen");
            assertEquals(s.width, once.p(0, 0));
            Moves.Move m = Alignment.move(once);
            assertFalse(MoveFactory.finished(once, 0));
            assertTrue(MoveFactory.finished(once, (m.playTime() + MoveFactory.REMOVE_MARGIN) * 20 + 1), "ends by itself at play time + the margin");
        }
    }

    // ================= part 2: devour =================

    @Test
    void theConfiguredDevourTimesAreWhatTheMovesFramesGive() {
        for (StrikeType t : StrikeType.values()) for (SizeTable.Size s : sizes()) {
            double derived = Alignment.round2(Alignment.devourSeconds(t, Alignment.move(Alignment.once(t, s))));
            assertEquals(derived, CFG.devourSeconds[t.ordinal()][s.ordinal()], 0.011, t + " " + s);
            assertTrue(derived > Alignment.entryTau(Alignment.move(Alignment.once(t, s))), "after the head meets the sand");
        }
    }

    @Test
    void devourHappensOnceAtTheMomentAndNotWhenTheVictimIsGone() {
        DevourGate g = new DevourGate(3.0);
        assertEquals(DevourGate.Action.WAIT, g.check(0, true));
        assertEquals(DevourGate.Action.WAIT, g.check(59, true));
        assertEquals(DevourGate.Action.KILL, g.check(60, true), "at the configured moment (60 ticks)");
        assertEquals(DevourGate.Action.WAIT, g.check(61, true), "once");
        assertEquals(DevourGate.Action.WAIT, g.check(200, true));
        DevourGate gone = new DevourGate(3.0);
        assertEquals(DevourGate.Action.SKIP, gone.check(60, false), "already dead or gone: nobody is killed");
        assertEquals(DevourGate.Action.WAIT, gone.check(61, true));
        assertTrue(gone.done());
    }

    @Test
    void theDamageTypeBypassesTheTotemAndTheOtherProtections() throws IOException {
        // LivingEntity.checkTotemDeathProtection returns false at once for a source in the tag bypasses_invulnerability (read in the 26.1.2 sources); the same tag is how /kill is not saved.
        String dt = read("/data/islandcraft_wormsofarrakis/damage_type/swallowed.json");
        assertTrue(dt.contains("\"message_id\": \"islandcraft_wormsofarrakis.swallowed\""));
        for (String tag : new String[]{"bypasses_invulnerability", "bypasses_armor", "bypasses_effects", "bypasses_resistance", "bypasses_enchantments", "bypasses_shield", "bypasses_cooldown"}) {
            String j = read("/data/minecraft/tags/damage_type/" + tag + ".json");
            assertTrue(j.contains("\"islandcraft_wormsofarrakis:swallowed\""), tag);
            assertTrue(j.contains("\"replace\": false"), tag + " adds to the vanilla list, never replaces it");
        }
        String lang = read("/assets/islandcraft_wormsofarrakis/lang/en_us.json");
        assertTrue(lang.contains("\"death.attack.islandcraft_wormsofarrakis.swallowed\": \"%1$s was swallowed by a sandworm\""));
        assertEquals("death.attack." + "islandcraft_wormsofarrakis.swallowed", HuntTexts.DEATH_KEY);
    }

    private static String read(String path) throws IOException {
        try (InputStream in = Prompt14StrikeTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ================= part 3: the crest corridor =================

    @Test
    void aLongFlatSandLinePassesAtEverySizeInEveryDirection() {
        for (SizeTable.Size s : sizes()) for (Dir d : Dir.values()) {
            Corridor.Result r = Corridor.crest(flat(), VX, VZ, GROUND, d, params(s));
            assertTrue(r.ok(), s + " " + d);
            assertEquals(Corridor.sampleCount(params(s)), r.reads(), "reads = the samples of the line");
        }
    }

    /** Sand only along the line of a direction: from -behind to +ahead (plus the side lines), rock elsewhere; dir E: x along. */
    private static Corridor.Terrain strip(Dir d, int ahead, int behind, int r0) {
        return sandWhere((x, z) -> {
            int along = (x - 100) * d.dx + (z - 100) * d.dz;
            int side = Math.abs((x - 100) * d.dz + (z - 100) * d.dx);
            return along >= -behind && along <= ahead && side <= r0;
        });
    }

    @Test
    void exactlyTheAheadAndBehindLimitsPassAndOneBlockShortFails() {
        for (SizeTable.Size s : sizes()) {
            Corridor.Params p = params(s);
            for (Dir d : Dir.values()) {
                assertTrue(Corridor.crest(strip(d, p.ahead(), p.behind(), p.r0()), VX, VZ, GROUND, d, p).ok(), s + " " + d + " exact limits pass");
                Corridor.Result a = Corridor.crest(strip(d, p.ahead() - 1, p.behind(), p.r0()), VX, VZ, GROUND, d, p);
                assertFalse(a.ok(), s + " " + d + " one block short ahead");
                assertTrue(a.fail().along() == p.ahead() && a.fail().reason().equals("not sand"), a.fail().text());
                Corridor.Result b = Corridor.crest(strip(d, p.ahead(), p.behind() - 1, p.r0()), VX, VZ, GROUND, d, p);
                assertFalse(b.ok(), s + " " + d + " one block short behind");
                assertEquals(-p.behind(), b.fail().along());
            }
        }
    }

    @Test
    void theTableValuesAreFivePointTwoWTwoWAndAQuarterW() {
        int[] w = {8, 16, 32, 48};
        for (int i = 0; i < 4; i++) {
            assertEquals(Math.round(5.2 * w[i]), CFG.crestAhead[i]);
            assertEquals(2 * w[i], CFG.crestBehind[i]);
            assertEquals(w[i] / 4, CFG.crestTolerance[i]);
        }
        assertArrayEquals(new int[]{42, 83, 166, 250}, CFG.crestAhead);
        assertArrayEquals(new int[]{16, 32, 64, 96}, CFG.crestBehind);
        assertArrayEquals(new int[]{2, 4, 8, 12}, CFG.crestTolerance);
    }

    @Test
    void heightToleranceAtTheLimitPassesAndOneOverFails() {
        for (SizeTable.Size s : sizes()) {
            Corridor.Params p = params(s);
            int tol = p.tolerance();
            for (int delta : new int[]{tol, -tol}) {
                Corridor.Terrain at = (x, z) -> x == 108 && z == 100 ? new Top(Kind.SAND, GROUND + delta) : new Top(Kind.SAND, GROUND);
                assertTrue(Corridor.crest(at, VX, VZ, GROUND, Dir.E, p).ok(), s + " delta " + delta);
            }
            for (int delta : new int[]{tol + 1, -(tol + 1)}) {
                Corridor.Terrain over = (x, z) -> x == 108 && z == 100 ? new Top(Kind.SAND, GROUND + delta) : new Top(Kind.SAND, GROUND);
                Corridor.Result r = Corridor.crest(over, VX, VZ, GROUND, Dir.E, p);
                assertFalse(r.ok(), s + " delta " + delta);
                assertTrue(r.fail().reason().startsWith("height"), r.fail().text());
            }
        }
    }

    @Test
    void aNonSandTopBlockOnTheCentreLineAndOnEachSideLineFails() {
        Corridor.Params p = params(SizeTable.Size.MEDIUM);
        int[] sides = {0, -p.r0(), p.r0()};
        for (int side : sides) {
            for (int along : new int[]{8, -8}) {
                Corridor.Terrain rock = (x, z) -> x == 100 + along && z == 100 + side ? new Top(Kind.OTHER, GROUND) : new Top(Kind.SAND, GROUND);
                Corridor.Result r = Corridor.crest(rock, VX, VZ, GROUND, Dir.E, p);
                assertFalse(r.ok(), "side " + side + " along " + along);
                assertEquals("not sand", r.fail().reason());
            }
        }
    }

    @Test
    void anUnloadedChunkFailsTheSample() {
        Corridor.Terrain unloaded = (x, z) -> x == 112 && z == 100 ? new Top(Kind.UNLOADED, 0) : new Top(Kind.SAND, GROUND);
        Corridor.Result r = Corridor.crest(unloaded, VX, VZ, GROUND, Dir.E, params(SizeTable.Size.MEDIUM));
        assertFalse(r.ok());
        assertTrue(r.fail().unloaded());
        StrikeChoice.Availability a = StrikeChoice.evaluate(unloaded, VX, VZ, GROUND, SizeTable.Size.MEDIUM, CFG, crestR0(SizeTable.Size.MEDIUM), 7.9);
        assertTrue(a.unloaded, "logged once per attack");
        assertTrue(a.crestFails.containsKey(Dir.E) && !a.crestDirs.contains(Dir.E), "the direction with the unloaded column is out; the others may still pass");
    }

    @Test
    void fourDirectionsOneValidManyValidNoneValid() {
        SizeTable.Size s = SizeTable.Size.SMALL;
        Corridor.Params p = params(s);
        // one valid: a strip along z from -ahead to +behind: only N (ahead = -z) fits
        Corridor.Terrain onlyN = strip(Dir.N, p.ahead(), p.behind(), p.r0());
        StrikeChoice.Availability one = StrikeChoice.evaluate(onlyN, VX, VZ, GROUND, s, CFG, p.r0(), 3.9);
        assertEquals(List.of(Dir.N), one.crestDirs);
        assertTrue(one.has(StrikeType.CREST));
        assertEquals(Dir.N, StrikeChoice.choose(one, CFG, new Random(1), StrikeType.CREST).dir());
        // many valid: everything sand; a seeded pick among the four, all four appear
        StrikeChoice.Availability many = StrikeChoice.evaluate(flat(), VX, VZ, GROUND, s, CFG, p.r0(), 3.9);
        assertEquals(List.of(Dir.N, Dir.E, Dir.S, Dir.W), many.crestDirs);
        Random a = new Random(42), b = new Random(42);
        Set<Dir> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            Dir d1 = StrikeChoice.choose(many, CFG, a, StrikeType.CREST).dir();
            assertEquals(d1, StrikeChoice.choose(many, CFG, b, StrikeType.CREST).dir(), "seeded: reproducible");
            seen.add(d1);
        }
        assertEquals(4, seen.size());
        // none valid: rock round the victim
        StrikeChoice.Availability none = StrikeChoice.evaluate(sandWhere((x, z) -> x == 100 && z == 100), VX, VZ, GROUND, s, CFG, p.r0(), 3.9);
        assertTrue(none.crestDirs.isEmpty());
        assertFalse(none.has(StrikeType.CREST), "crest absent");
        assertTrue(none.has(StrikeType.CRASH) && none.has(StrikeType.EMERGE), "crash and emerge are always available");
        assertNotNull(none.why(StrikeType.CREST));
        assertEquals(4, none.crestFails.size());
    }

    @Test
    void aVictimOnAHighDuneOrInADipDropsTheCrest() {
        for (SizeTable.Size s : sizes()) {
            int tol = params(s).tolerance();
            for (int off : new int[]{tol + 1, -(tol + 1)}) {
                // the surroundings are at the victim's level + off (a dune above them, or a dip): every sample is out of tolerance
                Corridor.Terrain t = (x, z) -> x == 100 && z == 100 ? new Top(Kind.SAND, GROUND) : new Top(Kind.SAND, GROUND + off);
                StrikeChoice.Availability a = StrikeChoice.evaluate(t, VX, VZ, GROUND, s, CFG, crestR0(s), 3.9);
                assertFalse(a.has(StrikeType.CREST), s + " off " + off);
            }
        }
    }

    // ================= part 3: the engulf disc =================

    @Test
    void theDiscPassesOnAnyUnevenSandAndFailsOnOneNonSandSample() {
        Random rnd = new Random(7);
        Corridor.Terrain uneven = (x, z) -> new Top(Kind.SAND, GROUND + (int) (Math.floorMod(x * 31 + z * 17, 9)) - 4);
        Corridor.Result ok = Corridor.disc(uneven, VX, VZ, 15.9);
        assertTrue(ok.ok(), "no flatness test");
        assertEquals(Corridor.DISC_SAMPLES, ok.reads());
        // every one of the 17 samples, made rock in turn, fails
        List<int[]> pts = new ArrayList<>();
        pts.add(new int[]{(int) Math.floor(VX), (int) Math.floor(VZ)});
        for (double r : new double[]{15.9 / 2, 15.9}) for (int k = 0; k < 8; k++) pts.add(new int[]{(int) Math.floor(VX + r * Math.cos(k * Math.PI / 4)), (int) Math.floor(VZ + r * Math.sin(k * Math.PI / 4))});
        assertEquals(17, pts.size());
        for (int[] q : pts) {
            Corridor.Terrain one = (x, z) -> x == q[0] && z == q[1] ? new Top(Kind.OTHER, GROUND) : new Top(Kind.SAND, GROUND);
            assertFalse(Corridor.disc(one, VX, VZ, 15.9).ok(), q[0] + "," + q[1]);
        }
        Corridor.Terrain unl = (x, z) -> x == pts.get(3)[0] && z == pts.get(3)[1] ? new Top(Kind.UNLOADED, 0) : new Top(Kind.SAND, GROUND);
        assertTrue(Corridor.disc(unl, VX, VZ, 15.9).fail().unloaded());
        assertNotNull(rnd);
    }

    // ================= part 3: the odds =================

    private static Map<StrikeType, Double> share(List<StrikeType> avail, int n, long seed) {
        Random rng = new Random(seed);
        Map<StrikeType, Integer> c = new EnumMap<>(StrikeType.class);
        for (int i = 0; i < n; i++) c.merge(StrikeChoice.pick(avail, LEGACY, rng), 1, Integer::sum);
        Map<StrikeType, Double> m = new EnumMap<>(StrikeType.class);
        for (StrikeType t : avail) m.put(t, 100.0 * c.getOrDefault(t, 0) / n);
        return m;
    }

    @Test
    void theOddsAreTheWeightsRescaledOverWhatIsAvailable() {
        List<StrikeType> all = List.of(StrikeType.CREST, StrikeType.CRASH, StrikeType.EMERGE, StrikeType.ENGULF);
        Map<StrikeType, Double> o = StrikeChoice.odds(all, LEGACY);
        assertEquals(30.0, o.get(StrikeType.CREST), 1e-9);
        assertEquals(25.0, o.get(StrikeType.CRASH), 1e-9);
        assertEquals(25.0, o.get(StrikeType.EMERGE), 1e-9);
        assertEquals(20.0, o.get(StrikeType.ENGULF), 1e-9);
        Map<StrikeType, Double> noCrest = StrikeChoice.odds(List.of(StrikeType.CRASH, StrikeType.EMERGE, StrikeType.ENGULF), LEGACY);
        assertEquals(25 / 70.0 * 100, noCrest.get(StrikeType.CRASH), 1e-9);
        assertEquals(25 / 70.0 * 100, noCrest.get(StrikeType.EMERGE), 1e-9);
        assertEquals(20 / 70.0 * 100, noCrest.get(StrikeType.ENGULF), 1e-9);
        Map<StrikeType, Double> noEngulf = StrikeChoice.odds(List.of(StrikeType.CREST, StrikeType.CRASH, StrikeType.EMERGE), LEGACY);
        assertEquals(37.5, noEngulf.get(StrikeType.CREST), 1e-9);
        assertEquals(31.25, noEngulf.get(StrikeType.CRASH), 1e-9);
        Map<StrikeType, Double> two = StrikeChoice.odds(List.of(StrikeType.CRASH, StrikeType.EMERGE), LEGACY);
        assertEquals(50.0, two.get(StrikeType.CRASH), 1e-9);
        assertEquals(50.0, two.get(StrikeType.EMERGE), 1e-9);
    }

    @Test
    void aSeededRunOfManyTrialsMatchesTheWeights() {
        int n = 40000;
        Map<StrikeType, Double> all = share(List.of(StrikeType.CREST, StrikeType.CRASH, StrikeType.EMERGE, StrikeType.ENGULF), n, 1);
        assertEquals(30, all.get(StrikeType.CREST), 1.0);
        assertEquals(25, all.get(StrikeType.CRASH), 1.0);
        assertEquals(25, all.get(StrikeType.EMERGE), 1.0);
        assertEquals(20, all.get(StrikeType.ENGULF), 1.0);
        Map<StrikeType, Double> two = share(List.of(StrikeType.CRASH, StrikeType.EMERGE), n, 2);
        assertEquals(50, two.get(StrikeType.CRASH), 1.0);
        Map<StrikeType, Double> noCrest = share(List.of(StrikeType.CRASH, StrikeType.EMERGE, StrikeType.ENGULF), n, 3);
        assertEquals(35.7, noCrest.get(StrikeType.CRASH), 1.0);
        assertEquals(28.6, noCrest.get(StrikeType.ENGULF), 1.0);
    }

    @Test
    void theChoiceLineAndTheYawModes() {
        StrikeChoice.Availability a = new StrikeChoice.Availability();
        a.available.add(StrikeType.CRASH);
        a.available.add(StrikeType.EMERGE);
        StrikeChoice.Choice c = new StrikeChoice.Choice(StrikeType.CRASH, null, 12);
        assertEquals("strike: size=small W8 available=[crash,emerge] weights=[crash=70,emerge=10] chosen=crash dir=-", StrikeChoice.line(SizeTable.Size.SMALL, a, CFG, c));
        a.available.add(0, StrikeType.CREST);
        assertTrue(StrikeChoice.line(SizeTable.Size.HUGE, a, CFG, new StrikeChoice.Choice(StrikeType.CREST, Dir.E, 270)).endsWith("chosen=crest dir=E"));
        Random rng = new Random(5);
        Set<Double> yaws = new HashSet<>();
        for (int i = 0; i < 200; i++) { StrikeChoice.Choice x = StrikeChoice.choose(a, CFG, rng, StrikeType.EMERGE); yaws.add(x.yawDeg()); assertNotNull(x.dir()); }
        assertEquals(Set.of(0.0, 90.0, 180.0, 270.0), yaws, "cardinal: one of the four");
        HuntConfig r = new HuntConfig();
        r.strikeYawMode = "random";
        Set<Double> free = new HashSet<>();
        for (int i = 0; i < 50; i++) { StrikeChoice.Choice x = StrikeChoice.choose(a, r, rng, StrikeType.EMERGE); free.add(x.yawDeg()); assertNull(x.dir()); }
        assertTrue(free.size() > 40, "random yaw");
        assertEquals(StrikeType.ENGULF, StrikeChoice.choose(a, CFG, rng, StrikeType.ENGULF).type(), "forced");
    }

    // ================= part 3: the work stays off the tick =================

    @Test
    void theScanIsSpreadOverTicksAndReadsEachColumnOnce() {
        for (SizeTable.Size s : sizes()) {
            int[] sourceReads = {0};
            Corridor.Terrain src = (x, z) -> { sourceReads[0]++; return new Top(Kind.SAND, GROUND); };
            ScanTerrain st = new ScanTerrain(src);
            int ticks = 0;
            StrikeChoice.Availability res = null;
            while (res == null && ticks < 1000) {
                st.newTick(CFG.scanColumnsPerTick);
                int before = sourceReads[0];
                res = st.step(() -> StrikeChoice.evaluate(st, VX, VZ, GROUND, s, CFG, crestR0(s), 7.9));
                assertTrue(sourceReads[0] - before <= CFG.scanColumnsPerTick, "never more than the budget in a tick");
                ticks++;
            }
            assertNotNull(res);
            assertEquals(sourceReads[0], st.columns(), "no column read twice");
            StrikeChoice.Availability direct = StrikeChoice.evaluate(flat(), VX, VZ, GROUND, s, CFG, crestR0(s), 7.9);
            assertEquals(direct.available, res.available, "the same result as reading everything at once");
            assertTrue(ticks <= 20, s + ": " + ticks + " ticks (the scan has the last 20 of the freeze's 80)");
            assertTrue(res.reads <= 1100, "a few hundred to about a thousand columns at most: " + res.reads);
        }
    }

    // ================= part 4: the strike phase =================

    private static UUID u(int n) { return new UUID(0, n); }

    private static final class Pl {
        final UUID id; final String name; String dim = "overworld"; boolean eligible = true, onSand = true;
        Pl(int n, String name) { id = u(n); this.name = name; }
        PlayerState state() { return new PlayerState(id, name, dim, 0, 65, 0, true, false, false, false, false, false, false, eligible, onSand); }
    }

    private static final class Env implements HuntManager.Env {
        final List<Pl> ps = new ArrayList<>();
        public List<PlayerState> players() { List<PlayerState> l = new ArrayList<>(); for (Pl p : ps) l.add(p.state()); return l; }
        public int depth(UUID id) { return 10; }
    }

    /** A listener that runs a strike which the test finishes by hand. */
    private static final class Rec implements HuntManager.Listener {
        final List<HuntEvent.EndReason> ends = new ArrayList<>();
        final List<HuntEvent.Phase> phases = new ArrayList<>();
        boolean cleaned;
        boolean autoFinish;
        public void onPhaseChange(HuntEvent e, HuntEvent.Phase from, HuntEvent.Phase to) { phases.add(to); }
        public void onStrike(HuntEvent e) { e.setStrikeInfo("emerge", "N"); e.setStrikeLimit(30); if (autoFinish) e.finishStrike(); }
        public void onEnd(HuntEvent e, HuntEvent.EndReason r) { ends.add(r); cleaned = true; }
    }

    private static HuntEvent inStrike(HuntManager m, Env env, Rec rec) {
        m.tick(DT, env);
        assertEquals(HuntManager.TriggerResult.STARTED, m.forceTrigger(env.ps.get(0).id, SizeTable.Size.SMALL, env));
        HuntEvent e = m.eventOf(env.ps.get(0).id);
        for (int i = 0; i < 4000 && e.phase() != HuntEvent.Phase.STRIKE; i++) m.tick(DT, env);
        assertEquals(HuntEvent.Phase.STRIKE, e.phase());
        return e;
    }

    @Test
    void thePhasesRunStalkingFreezeStrikeEndedAndTheEventWaitsForTheMove() {
        Env env = new Env();
        env.ps.add(new Pl(1, "victim"));
        env.ps.add(new Pl(2, "other"));
        Rec rec = new Rec();
        HuntManager m = new HuntManager(new HuntConfig(), rec, (e, v, w, s) -> {});
        HuntEvent e = inStrike(m, env, rec);
        assertEquals(List.of(HuntEvent.Phase.FREEZE, HuntEvent.Phase.STRIKE), rec.phases);
        for (int i = 0; i < 400; i++) m.tick(DT, env);   // 20 s of strike: the event is still waiting for the move
        assertEquals(HuntEvent.Phase.STRIKE, e.phase());
        assertEquals("emerge", e.strikeType());
        assertEquals("N", e.strikeDir());
        e.finishStrike();   // the move reports it is over
        m.tick(DT, env);
        assertEquals(List.of(HuntEvent.EndReason.DONE), rec.ends);
        assertEquals(List.of(HuntEvent.Phase.FREEZE, HuntEvent.Phase.STRIKE, HuntEvent.Phase.ENDED), rec.phases);
        m.aggro().setAggro(u(2), 0);
        assertEquals(0, m.aggro().getAggro(u(2)), "scores are 0 and no cooldown: another trigger is possible at once");
        assertEquals(HuntManager.TriggerResult.STARTED, m.forceTrigger(u(1), SizeTable.Size.SMALL, env));
    }

    @Test
    void theSafetyLimitEndsAStrikeThatNeverReportsItsEnd() {
        Env env = new Env();
        env.ps.add(new Pl(1, "victim"));
        Rec rec = new Rec();
        HuntManager m = new HuntManager(new HuntConfig(), rec, (e, v, w, s) -> {});
        inStrike(m, env, rec);
        for (int i = 0; i < 700; i++) m.tick(DT, env);   // 35 s > the limit of 30 s
        assertEquals(List.of(HuntEvent.EndReason.DONE), rec.ends);
    }

    private void strikeCancel(java.util.function.Consumer<Object[]> cancel, HuntEvent.EndReason expect) {
        Env env = new Env();
        env.ps.add(new Pl(1, "victim"));
        env.ps.add(new Pl(2, "other"));
        Rec rec = new Rec();
        HuntManager m = new HuntManager(new HuntConfig(), rec, (e, v, w, s) -> {});
        HuntEvent e = inStrike(m, env, rec);
        cancel.accept(new Object[]{m, env, e});
        m.tick(DT, env);
        for (int i = 0; i < 5; i++) m.tick(DT, env);
        assertEquals(List.of(expect), rec.ends);
        assertTrue(rec.cleaned);
        assertTrue(m.events().isEmpty(), "no event is left");
        assertNull(m.eventOf(u(1)));
        assertNull(m.eventOf(u(2)));
    }

    @Test
    void victimLeavesBeforeTheDevourDuringTheStrikeTheEventEndsCleanly() {
        strikeCancel(o -> ((HuntManager) o[0]).logoff(u(1)), HuntEvent.EndReason.FIZZLED);
        strikeCancel(o -> { ((Env) o[1]).ps.get(0).eligible = false; ((HuntManager) o[0]).died(u(1)); }, HuntEvent.EndReason.FIZZLED);
        strikeCancel(o -> ((Env) o[1]).ps.get(0).eligible = false, HuntEvent.EndReason.FIZZLED);   // creative or spectator
        strikeCancel(o -> ((Env) o[1]).ps.get(0).dim = "the_nether", HuntEvent.EndReason.FIZZLED);
    }

    @Test
    void theDeathAtTheDevourIsNotACancelAndTheMoveRunsOn() {
        Env env = new Env();
        env.ps.add(new Pl(1, "victim"));
        env.ps.add(new Pl(2, "other"));
        Rec rec = new Rec();
        HuntManager m = new HuntManager(new HuntConfig(), rec, (e, v, w, s) -> {});
        HuntEvent e = inStrike(m, env, rec);
        e.markDevoured();
        env.ps.get(0).eligible = false;   // dead
        m.died(u(1));
        for (int i = 0; i < 100; i++) m.tick(DT, env);
        assertTrue(rec.ends.isEmpty(), "the worm runs on to the end of the move");
        assertEquals(HuntEvent.Phase.STRIKE, e.phase());
        e.finishStrike();
        m.tick(DT, env);
        assertEquals(List.of(HuntEvent.EndReason.DONE), rec.ends);
        assertEquals(0, m.aggro().getAggro(u(1)), "the victim's score is zeroed too");
    }

    @Test
    void stopDuringTheStrikeEndsItAndCleansUp() {
        strikeCancel(o -> ((HuntManager) o[0]).stop(null), HuntEvent.EndReason.STOPPED);
    }

    @Test
    void withoutAStrikeRunnerTheOldBehaviourHolds() {
        Env env = new Env();
        env.ps.add(new Pl(1, "victim"));
        List<HuntEvent.EndReason> ends = new ArrayList<>();
        HuntManager m = new HuntManager(new HuntConfig(), new HuntManager.Listener() {
            public void onEnd(HuntEvent e, HuntEvent.EndReason r) { ends.add(r); }
        }, (e, v, w, s) -> {});
        m.tick(DT, env);
        m.forceTrigger(u(1), SizeTable.Size.SMALL, env);
        for (int i = 0; i < 4000 && ends.isEmpty(); i++) m.tick(DT, env);
        assertEquals(List.of(HuntEvent.EndReason.DONE), ends);
    }
}
