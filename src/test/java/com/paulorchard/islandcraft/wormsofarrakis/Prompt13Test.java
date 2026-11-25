package com.paulorchard.islandcraft.wormsofarrakis;

import com.paulorchard.islandcraft.wormsofarrakis.hunt.AggroEngine;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.AggroEngine.PlayerState;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntConfig;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntEvent;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntManager;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.ListScan;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.SandProbe;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.SizeTable;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.StrikeHook;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.Tremor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Prompt 13: aggro, depth and size, roster, stalk and tremors (engine-free). */
class Prompt13Test {
    private static final double DT = 0.05;

    private static UUID u(int n) { return new UUID(0, n); }

    /** A player on sand moving +x at a speed (blocks per second) in a state. */
    private static final class P {
        final UUID id; final String name; double x, z; boolean sprint, sneak, ground = true, flying, swim, riding, fall, eligible = true, onSand = true; String dim = "overworld"; int depth = 10;
        P(int n, String name) { id = u(n); this.name = name; }
        PlayerState state() { return new PlayerState(id, name, dim, x, 65, z, ground, sprint, sneak, flying, swim, riding, fall, eligible, onSand); }
        void move(double blocksPerSecond) { x += blocksPerSecond * DT; }
    }

    private static final class World implements HuntManager.Env {
        final List<P> ps = new ArrayList<>();
        public List<PlayerState> players() { List<PlayerState> l = new ArrayList<>(); for (P p : ps) l.add(p.state()); return l; }
        public int depth(UUID id) { for (P p : ps) if (p.id.equals(id)) return p.onSand ? p.depth : 0; return 0; }
    }

    private static class Rec implements HuntManager.Listener {
        final List<String> log = new ArrayList<>();
        final List<Object[]> tremors = new ArrayList<>();
        final List<HuntEvent.EndReason> ends = new ArrayList<>();
        final List<Object[]> attacks = new ArrayList<>();
        int shallow;
        public void onStart(HuntEvent e, PlayerState t) { log.add("start " + e.size.label); }
        public void onShallow(PlayerState p, int d, double s) { shallow++; }
        public void onEnd(HuntEvent e, HuntEvent.EndReason r) { ends.add(r); }
        public void onTremor(HuntEvent e, UUID p, double s, double pr) { tremors.add(new Object[]{e.id, p, s, pr, e.phaseTime()}); }
    }

    private static HuntManager mgr(Rec rec, List<Object[]> attacks) {
        return new HuntManager(new HuntConfig(), rec, (e, v, w, s) -> attacks.add(new Object[]{e.victimName(), s, w, e.victimScore()}));
    }

    private static void run(HuntManager m, World w, double seconds, java.util.function.Consumer<Integer> each) {
        int n = (int) Math.round(seconds / DT);
        for (int i = 0; i < n; i++) { if (each != null) each.accept(i); m.tick(DT, w); }
    }

    // ================= part 1: aggro =================

    @Test
    void movementBandsAndTheirMultipliers() {
        HuntConfig c = new HuntConfig();
        P p = new P(1, "a");
        assertEquals(1.0, AggroEngine.movementMultiplier(c, p.state(), 4.3));
        p.sprint = true;
        assertEquals(2.0, AggroEngine.movementMultiplier(c, p.state(), 5.6));
        p.ground = false;
        assertEquals(2.5, AggroEngine.movementMultiplier(c, p.state(), 7));
        p.ground = true; p.sprint = false;
        assertEquals(0, AggroEngine.movementMultiplier(c, p.state(), 1.99), "below MinGainSpeed");
        assertEquals(1.0, AggroEngine.movementMultiplier(c, p.state(), 2.0));
        p.sneak = true;
        assertEquals(0, AggroEngine.movementMultiplier(c, p.state(), 4.3), "sneaking");
        p.sneak = false; p.flying = true;
        assertEquals(0, AggroEngine.movementMultiplier(c, p.state(), 4.3));
        p.flying = false; p.swim = true;
        assertEquals(0, AggroEngine.movementMultiplier(c, p.state(), 4.3));
        p.swim = false; p.riding = true;
        assertEquals(0, AggroEngine.movementMultiplier(c, p.state(), 4.3), "vehicle, mount, boat");
        p.riding = false; p.fall = true;
        assertEquals(0, AggroEngine.movementMultiplier(c, p.state(), 4.3), "elytra");
        p.fall = false; p.eligible = false;
        assertEquals(0, AggroEngine.movementMultiplier(c, p.state(), 4.3), "creative, spectator, dead, ignored");
    }

    @Test
    void secondsToOneHundredAtEachBand() {
        double[][] bands = {{4.317, 1.0, 23.2}, {5.612, 2.0, 8.9}, {7.0, 2.5, 5.7}};
        for (double[] b : bands) {
            AggroEngine a = new AggroEngine(new HuntConfig());
            P p = new P(1, "a");
            p.sprint = b[1] >= 2.0; p.ground = b[1] != 2.5;
            int ticks = 0;
            while (a.getAggro(p.id) < 100 && ticks < 2000) { p.move(b[0]); a.update(p.state(), ticks, DT, false); ticks++; }
            assertEquals(b[2], ticks * DT, 0.15, "band " + b[1]);
        }
    }

    @Test
    void decayStillAndOffSandAndNoDecayInAnEvent() {
        AggroEngine a = new AggroEngine(new HuntConfig());
        P p = new P(1, "a");
        a.addAggro(p.id, 50, "test");
        for (int i = 0; i < 100; i++) a.update(p.state(), i, DT, false);   // 5 s standing
        assertEquals(45, a.getAggro(p.id), 1e-6, "1 per second");
        p.onSand = false;
        for (int i = 0; i < 100; i++) a.update(p.state(), 100 + i, DT, false);   // 5 s off sand
        assertEquals(45 - 50, Math.max(a.getAggro(p.id), -5), 1e-6 + 5, "10 times faster off sand: floor at 0");
        assertEquals(0, a.getAggro(p.id), 1e-9);
        a.setAggro(p.id, 30);
        for (int i = 0; i < 200; i++) a.update(p.state(), i, DT, true);
        assertEquals(30, a.getAggro(p.id), 1e-9, "no decay inside an event");
        a.addAggro(p.id, -100, "t");
        assertEquals(0, a.getAggro(p.id), "floor 0");
    }

    @Test
    void theTeleportGuardAndSlowMoves() {
        AggroEngine a = new AggroEngine(new HuntConfig());
        P p = new P(1, "a");
        a.update(p.state(), 0, DT, false);
        p.x += 31 * DT;   // 31 blocks per second
        assertEquals(0, a.update(p.state(), 1, DT, false), "a teleport is never aggro");
        p.x += 29 * DT;
        assertTrue(a.update(p.state(), 2, DT, false) > 0, "29 blocks per second still counts");
        p.x += 1.5 * DT;
        assertEquals(0, a.update(p.state(), 3, DT, false), "slower than MinGainSpeed = standing still");
        p.dim = "nether"; p.x += 100;
        assertEquals(0, a.update(p.state(), 4, DT, false), "another dimension: no move");
    }

    @Test
    void effectMultiplierNoneOneSeveralCapMissingAndOneOffAddsNotMultiplied() {
        HuntConfig c = new HuntConfig();
        AggroEngine a = new AggroEngine(c);
        Set<String> active = new HashSet<>();
        a.setEffectProbe((id, e) -> active.contains(e));
        P p = new P(1, "a");
        assertEquals(1.0, a.effectFactor(p.id));
        a.registerEffectMultiplier("equipment:holtzman_shield", 10);
        assertEquals(1.0, a.effectFactor(p.id), "registered but not active");
        active.add("equipment:holtzman_shield");
        assertEquals(10.0, a.effectFactor(p.id));
        a.registerEffectMultiplier("minecraft:speed", 3);
        active.add("minecraft:speed");
        assertEquals(20.0, a.effectFactor(p.id), "30 capped at AggroMultiplierMax 20");
        a.registerEffectMultiplier("minecraft:haste", 1.5);
        active.remove("minecraft:speed");
        active.add("minecraft:haste");
        assertEquals(15.0, a.effectFactor(p.id), 1e-9, "several: the product");
        a.registerEffectMultiplier("nobody:missing_effect", 100);
        assertEquals(15.0, a.effectFactor(p.id), 1e-9, "an id that does not exist is skipped (never active)");
        // the built-in source is multiplied, a one-off add is not
        P q = new P(2, "b");
        a.update(q.state(), 0, DT, false);
        q.move(4.317);
        double g = a.update(q.state(), 1, DT, false);
        assertEquals(4.317 * DT * 15, g, 1e-6);
        double before = a.getAggro(q.id);
        a.addAggro(q.id, 10, "x");
        assertEquals(before + 10, a.getAggro(q.id), 1e-9);
        assertTrue(a.unregisterEffectMultiplier("minecraft:haste"));
        assertFalse(a.effectMultipliers().containsKey("minecraft:haste"));
    }

    @Test
    void theSourcesAreARegistry() {
        AggroEngine a = new AggroEngine(new HuntConfig());
        assertEquals(List.of("move"), a.sourceIds());
        a.registerSource(new AggroEngine.AggroSource("totem", t -> 1.0, t -> t.state().onSand()));
        P p = new P(1, "a");
        assertTrue(a.update(p.state(), 0, DT, false) >= 1.0);
        assertEquals(List.of("totem"), a.feedsOf(p.id));
        assertTrue(a.unregisterSource("totem"));
        assertFalse(a.unregisterSource("totem"));
    }

    // ================= part 2: depth and size =================

    @Test
    void depthIsTheConsecutiveSandUnderTheFeetAndStopsAtAnythingElse() {
        java.util.function.IntFunction<SandProbe.Cls> col = y -> y >= 60 && y <= 64 ? SandProbe.Cls.SAND : y == 59 ? SandProbe.Cls.OTHER : y > 64 ? SandProbe.Cls.AIR : SandProbe.Cls.SAND;
        int s = SandProbe.surface(true, 65.0, 0.3, col);
        assertEquals(64, s);
        assertEquals(5, SandProbe.depth(s, col), "sand from 64 down to 60, rock at 59");
        assertEquals(64, SandProbe.surface(true, 65.2, 0.3, col), "the tolerance of a step");
        assertEquals(SandProbe.NONE, SandProbe.surface(true, 65.6, 0.3, col));
        assertEquals(64, SandProbe.surface(false, 66.5, 0.3, col), "in the air after a jump");
        assertEquals(SandProbe.NONE, SandProbe.surface(false, 70, 0.3, col));
        assertEquals(0, SandProbe.depth(SandProbe.NONE, col));
        java.util.function.IntFunction<SandProbe.Cls> water = y -> y <= 64 && y >= 62 ? SandProbe.Cls.SAND : SandProbe.Cls.OTHER;
        assertEquals(3, SandProbe.depth(64, water));
    }

    @Test
    void depthGivesTheSizeAndTheStalkLength() {
        HuntConfig c = new HuntConfig();
        int[] d = {1, 4, 5, 8, 9, 16, 17, 32, 33, 500};
        String[] want = {null, null, "small", "small", "medium", "medium", "large", "large", "huge", "huge"};
        for (int i = 0; i < d.length; i++) {
            SizeTable.Size s = SizeTable.forDepth(d[i], c);
            assertEquals(want[i], s == null ? null : s.label, "D=" + d[i]);
        }
        assertEquals(16, SizeTable.stalkSeconds(SizeTable.Size.SMALL, c));
        assertEquals(24, SizeTable.stalkSeconds(SizeTable.Size.MEDIUM, c));
        assertEquals(40, SizeTable.stalkSeconds(SizeTable.Size.LARGE, c));
        assertEquals(56, SizeTable.stalkSeconds(SizeTable.Size.HUGE, c));
        assertEquals(8, SizeTable.Size.SMALL.width);
        assertEquals(48, SizeTable.Size.HUGE.width);
    }

    @Test
    void shallowSandPenaltyKeepsCountingAndDeepSandTriggersAtOnce() {
        Rec rec = new Rec();
        List<Object[]> att = new ArrayList<>();
        HuntManager m = mgr(rec, att);
        World w = new World();
        P p = new P(1, "a");
        p.depth = 3;
        w.ps.add(p);
        m.aggro().setAggro(p.id, 99.9);
        p.sprint = true;
        run(m, w, 3, i -> p.move(5.6));
        assertTrue(rec.log.isEmpty(), "shallow sand never starts an event");
        assertTrue(rec.shallow >= 1 && rec.shallow <= 1, "logged once per ShallowLogSeconds: " + rec.shallow);
        assertTrue(m.aggro().getAggro(p.id) < 100);
        // the score is reduced by 10 each time it reaches 100 and keeps counting
        m.aggro().setAggro(p.id, 100.5);
        m.tick(DT, w);
        assertTrue(m.aggro().getAggro(p.id) <= 90 + 1, "dropped by ShallowPenalty");
        // step onto deep sand at 100: starts at once
        p.depth = 20;
        m.aggro().setAggro(p.id, 100.5);
        m.tick(DT, w);
        assertEquals(List.of("start large"), rec.log);
        assertEquals(0, m.aggro().getAggro(p.id), "the score is 0 at the lock");
    }

    // ================= part 3: the roster =================

    private static ListScan.Candidate cand(int n, double x, double z, boolean sand, boolean inEv, boolean el) { return new ListScan.Candidate(u(n), "overworld", x, z, sand, inEv, el); }

    @Test
    void theScanKeepsEligiblePlayersOnSandInRangeAndNotInAnEvent() {
        ListScan.Candidate t = cand(1, 0, 0, true, false, true);
        List<ListScan.Candidate> all = List.of(t, cand(2, 150, 100, true, false, true), cand(3, 201, 0, true, false, true), cand(4, 10, 10, false, false, true),
                cand(5, 10, 10, true, true, true), cand(6, 10, 10, true, false, false), cand(7, 0, 200, true, false, true));
        assertEquals(List.of(u(1), u(2), u(7)), ListScan.scan(t, all, 200), "the trigger first; 200 blocks is in, 201 out");
        ListScan.Candidate other = new ListScan.Candidate(u(8), "nether", 1, 1, true, false, true);
        assertEquals(List.of(u(1)), ListScan.scan(t, List.of(t, other), 200), "another dimension");
    }

    @Test
    void theListIsFixedScoresAreZeroAndRockPlayersKeepTheirs() {
        Rec rec = new Rec();
        List<Object[]> att = new ArrayList<>();
        HuntManager m = mgr(rec, att);
        World w = new World();
        P a = new P(1, "a"), b = new P(2, "b"), rock = new P(3, "rock"), late = new P(4, "late");
        rock.onSand = false;
        w.ps.addAll(List.of(a, b, rock));
        for (P p : w.ps) m.aggro().setAggro(p.id, 40);
        m.aggro().setAggro(a.id, 100.5);
        m.tick(DT, w);
        HuntEvent e = m.eventOf(a.id);
        assertNotNull(e);
        assertEquals(List.of(a.id, b.id), e.members());
        assertEquals(0, m.aggro().getAggro(a.id));
        assertEquals(0, m.aggro().getAggro(b.id));
        assertTrue(m.aggro().getAggro(rock.id) > 39, "a player on rock keeps their score (decay only)");
        assertNull(m.eventOf(rock.id));
        // nobody joins later
        w.ps.add(late);
        late.sprint = true;
        run(m, w, 2, i -> late.move(5.6));
        assertFalse(e.has(late.id));
        // scores rise inside the event and do not decay or trigger
        a.sprint = true;
        run(m, w, 15, i -> a.move(5.6));
        assertTrue(m.aggro().getAggro(a.id) > 100, "it may pass 100 with no effect");
        assertSame(e, m.eventOf(a.id));
        assertEquals(1, m.events().size());
    }

    @Test
    void twoEventsAtOnceHaveDisjointListsAndHighestScoreFirst() {
        Rec rec = new Rec();
        HuntManager m = mgr(rec, new ArrayList<>());
        World w = new World();
        P a = new P(1, "a"), b = new P(2, "b"), far = new P(3, "far"), far2 = new P(4, "far2");
        far.x = 1000; far2.x = 1005;
        w.ps.addAll(List.of(a, b, far, far2));
        m.aggro().setAggro(far.id, 110);
        m.aggro().setAggro(a.id, 105);
        m.aggro().setAggro(b.id, 100.5);
        m.tick(DT, w);
        assertEquals(2, m.events().size());
        HuntEvent ea = m.eventOf(a.id), ef = m.eventOf(far.id);
        assertEquals(ef.id, m.events().get(0).id, "the highest score is taken first");
        assertEquals(List.of(far.id, far2.id), ef.members());
        assertEquals(List.of(a.id, b.id), ea.members());
        assertTrue(ea.members().stream().noneMatch(ef.members()::contains));
    }

    @Test
    void logOffGivesNinetyNineBackAtLoginAndOtherLeaversAndTheEmptyList() {
        Rec rec = new Rec();
        HuntManager m = mgr(rec, new ArrayList<>());
        World w = new World();
        P a = new P(1, "a"), b = new P(2, "b"), c = new P(3, "c");
        w.ps.addAll(List.of(a, b, c));
        m.aggro().setAggro(a.id, 100.5);
        m.tick(DT, w);
        HuntEvent e = m.eventOf(a.id);
        assertEquals(3, e.members().size());
        m.logoff(b.id);
        w.ps.remove(b);
        assertFalse(e.has(b.id));
        assertTrue(m.aggro().hasPendingLogin(b.id));
        w.ps.add(b);
        m.login(b.id);
        assertEquals(99, m.aggro().getAggro(b.id));
        assertNull(m.eventOf(b.id), "the ordinary rules apply to it");
        // a player who logs off while not in an event loses the score
        P free = new P(9, "free"); free.x = 5000;
        w.ps.add(free);
        m.aggro().setAggro(free.id, 50);
        m.logoff(free.id);
        m.login(free.id);
        assertEquals(0, m.aggro().getAggro(free.id));
        // a member who goes creative or dies leaves; dimension change too
        c.eligible = false;
        m.tick(DT, w);
        assertFalse(e.has(c.id));
        a.dim = "nether";
        m.tick(DT, w);
        assertTrue(m.events().isEmpty() || m.events().stream().noneMatch(x -> x.has(a.id)));
        assertEquals(HuntEvent.EndReason.FIZZLED, rec.ends.get(0), "an empty list fizzles");
    }

    // ================= part 4: stalk, victim, end =================

    private static HuntEvent.View view(Map<UUID, double[]> d) {
        return new HuntEvent.View() {
            public double score(UUID id) { return d.get(id)[0]; }
            public boolean onSand(UUID id) { return d.get(id)[1] > 0; }
            public long lastGainTick(UUID id) { return (long) d.get(id)[2]; }
            public String name(UUID id) { return "p" + id.getLeastSignificantBits(); }
        };
    }

    @Test
    void theVictimIsTheHighestScoreOnSandTiesToTheLatestGainer() {
        Map<UUID, double[]> d = new HashMap<>();
        d.put(u(1), new double[]{50, 1, 5});
        d.put(u(2), new double[]{80, 0, 9});   // higher but off sand
        d.put(u(3), new double[]{50, 1, 7});   // ties with 1: more recent
        assertEquals(u(3), HuntEvent.chooseVictim(List.of(u(1), u(2), u(3)), view(d)));
        d.get(u(2))[1] = 1;
        assertEquals(u(2), HuntEvent.chooseVictim(List.of(u(1), u(2), u(3)), view(d)), "back on sand before the end");
        d.get(u(1))[1] = 0; d.get(u(2))[1] = 0; d.get(u(3))[1] = 0;
        assertNull(HuntEvent.chooseVictim(List.of(u(1), u(2), u(3)), view(d)));
    }

    @Test
    void theWholeEventStalksAttacksAndEndsWithNoCooldown() {
        for (SizeTable.Size size : SizeTable.Size.values()) {
            Rec rec = new Rec();
            List<Object[]> att = new ArrayList<>();
            HuntManager m = mgr(rec, att);
            World w = new World();
            P a = new P(1, "alice"), b = new P(2, "bob");
            a.depth = b.depth = size == SizeTable.Size.SMALL ? 6 : size == SizeTable.Size.MEDIUM ? 12 : size == SizeTable.Size.LARGE ? 20 : 40;
            w.ps.addAll(List.of(a, b));
            m.aggro().setAggro(a.id, 100.5);
            m.tick(DT, w);
            HuntEvent e = m.eventOf(a.id);
            assertEquals(size, e.size);
            double stalk = e.stalkSeconds;
            assertEquals(size.width + 8, stalk);
            b.sprint = true;
            double t = 0;
            while (e.phase() == HuntEvent.Phase.STALKING) { b.move(5.6); m.tick(DT, w); t += DT; assertTrue(t < stalk + 1); }
            assertEquals(stalk, t, 0.1);
            assertEquals(1, att.size());
            assertEquals("bob", att.get(0)[0]);
            assertEquals(size.label, att.get(0)[1]);
            assertEquals(size.width, (double) att.get(0)[2]);
            assertEquals(HuntEvent.Phase.ATTACK, e.phase());
            double ta = 0;
            while (!e.ended()) { m.tick(DT, w); ta += DT; assertTrue(ta < 5); }
            assertEquals(4, ta, 0.1, "the 4 s placeholder");
            assertEquals(HuntEvent.EndReason.DONE, rec.ends.get(0));
            assertEquals(0, m.aggro().getAggro(a.id));
            assertEquals(0, m.aggro().getAggro(b.id));
            assertNull(m.eventOf(a.id));
            // no cooldown: free at once and can trigger again by moving
            m.aggro().setAggro(a.id, 100.5);
            m.tick(DT, w);
            assertNotNull(m.eventOf(a.id));
        }
    }

    @Test
    void nobodyOnSandFizzlesAndAStrikeHookLineExists() {
        Rec rec = new Rec();
        List<Object[]> att = new ArrayList<>();
        HuntManager m = mgr(rec, att);
        World w = new World();
        P a = new P(1, "a");
        w.ps.add(a);
        m.aggro().setAggro(a.id, 100.5);
        m.tick(DT, w);
        HuntEvent e = m.eventOf(a.id);
        a.onSand = false;
        run(m, w, 30, null);
        assertEquals(HuntEvent.EndReason.FIZZLED, rec.ends.get(0));
        assertTrue(att.isEmpty());
        assertTrue(m.events().isEmpty());
        // a player off sand stays in the list during the stalk
        P b = new P(2, "b"); b.x = 3000; w.ps.add(b);
        m.aggro().setAggro(b.id, 100.5);
        a.onSand = true;
        m.tick(DT, w);
        HuntEvent e2 = m.eventOf(b.id);
        b.onSand = false;
        run(m, w, 3, null);
        assertTrue(e2.has(b.id));
        assertEquals("hunt: attack victim=p1 size=small W8 score=41", StrikeHook.attackLine(fakeVictim(), 8, "small"));
    }

    private static HuntEvent fakeVictim() {
        HuntConfig c = new HuntConfig();
        c.stalkExtraSeconds = 0;
        HuntEvent e = new HuntEvent(1, u(1), "overworld", SizeTable.Size.SMALL, List.of(u(1)), c, new HuntEvent.Hooks() {}, (ev, v, ww, s) -> {});
        e.tick(8.0, view(Map.of(u(1), new double[]{41, 1, 1})));
        return e;
    }

    @Test
    void commandTriggerWithASizeIgnoresDepthAndWithoutOneRefusesShallow() {
        Rec rec = new Rec();
        HuntManager m = mgr(rec, new ArrayList<>());
        World w = new World();
        P a = new P(1, "a");
        a.depth = 2;
        w.ps.add(a);
        m.tick(DT, w);
        assertEquals(HuntManager.TriggerResult.SHALLOW, m.forceTrigger(a.id, null, w));
        assertEquals(HuntManager.TriggerResult.STARTED, m.forceTrigger(a.id, SizeTable.Size.HUGE, w));
        assertEquals(SizeTable.Size.HUGE, m.eventOf(a.id).size);
        assertEquals(HuntManager.TriggerResult.IN_EVENT, m.forceTrigger(a.id, null, w));
        assertEquals(1, m.stop(a.id));
        assertEquals(0, m.aggro().getAggro(a.id));
        assertEquals(HuntEvent.EndReason.STOPPED, rec.ends.get(0));
        assertTrue(m.toggleIgnore(a.id));
        assertFalse(m.toggleIgnore(a.id));
    }

    // ================= part 5: tremors =================

    @Test
    void tremorGapsAreInRangeSchedulesDifferAndTheFirstIsWithinTheFirstGap() {
        HuntConfig c = new HuntConfig();
        Tremor.Clock c1 = new Tremor.Clock(1, u(1), c), c2 = new Tremor.Clock(1, u(2), c);
        assertNotEquals(c1.next(), c2.next());
        assertTrue(c1.next() >= 0 && c1.next() <= c.tremorMaxSeconds);
        double t = c1.next();
        double prevT = t;
        List<Double> a = new ArrayList<>(), b = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            c1.fired(prevT);
            double gap = c1.next() - prevT;
            assertTrue(gap >= 3.0 - 1e-9 && gap <= 6.0 + 1e-9, "gap " + gap);
            a.add(gap);
            prevT = c1.next();
            c2.fired(c2.next());
            b.add(c2.next());
        }
        assertNotEquals(a, b);
        double mean = a.stream().mapToDouble(x -> x).average().orElse(0);
        assertEquals(4.5, mean, 0.3);
        // the same seed gives the same rhythm
        assertEquals(new Tremor.Clock(7, u(5), c).next(), new Tremor.Clock(7, u(5), c).next());
    }

    @Test
    void strengthBuildUpAndSizeScaling() {
        HuntConfig c = new HuntConfig();
        assertEquals(0.3, Tremor.strength(0), 1e-12);
        assertEquals(0.65, Tremor.strength(0.5), 1e-12);
        assertEquals(1.0, Tremor.strength(1), 1e-12);
        assertEquals(0, Tremor.buildUpRate(0.5, 1, c));
        assertEquals(0, Tremor.buildUpRate(0.3, 1, c));
        assertEquals(30, Tremor.buildUpRate(0.75, 1, c), 1e-9);
        assertEquals(60, Tremor.buildUpRate(1.0, 1, c), 1e-9);
        assertEquals(15, Tremor.buildUpRate(1.0, 0.25, c), 1e-9, "a small worm: a quarter");
        Tremor.Plan small = Tremor.plan(1.0, 0.25, c), huge = Tremor.plan(1.0, 1.0, c), half = Tremor.plan(0.5, 1.0, c);
        assertEquals(huge.radius() * 0.25, small.radius(), 1e-9);
        assertEquals(huge.particles() / 4, small.particles(), 1);
        assertEquals(huge.volume() * 0.25, small.volume(), 1e-9);
        assertEquals(16 * small.volume(), small.range(), 1e-9, "range = 16 x volume");
        assertTrue(half.radius() < huge.radius() && half.volume() < huge.volume() && half.particles() < huge.particles());
        assertTrue(Tremor.plan(0.3, 0.25, c).radius() >= Tremor.MIN_RADIUS);
    }

    @Test
    void theBuildUpStartsAtHalfTheStalkAndTheEventGivesTremorsAndGrainsToEveryListedPlayerOnSand() {
        Rec rec = new Rec() {
            final Map<UUID, Integer> grains = new HashMap<>();
            public void onBuildUp(HuntEvent e, UUID p, int n, double rate, double pr) { grains.merge(p, n, Integer::sum); log.add("grain " + pr); }
        };
        HuntManager m = mgr(rec, new ArrayList<>());
        World w = new World();
        P a = new P(1, "a"), b = new P(2, "b"), off = new P(3, "off");
        w.ps.addAll(List.of(a, b, off));
        m.aggro().setAggro(a.id, 100.5);
        m.tick(DT, w);
        off.onSand = false;
        run(m, w, 15.9, null);
        double firstGrain = Double.NaN;
        for (String s : rec.log) if (s.startsWith("grain ")) { firstGrain = Double.parseDouble(s.substring(6)); break; }
        assertTrue(firstGrain > 0.5 && firstGrain < 0.6, "the build-up starts at half the stalk: " + firstGrain);
        Set<UUID> who = new HashSet<>();
        double lastS = 0;
        for (Object[] t : rec.tremors) { who.add((UUID) t[1]); }
        assertTrue(who.contains(a.id) && who.contains(b.id) && !who.contains(off.id), "listed players on sand only");
        double prev = 0;
        for (Object[] t : rec.tremors) {
            double s = (double) t[2], p = (double) t[3];
            assertEquals(0.3 + 0.7 * p, s, 1e-9);
            if (t[1].equals(a.id)) { assertTrue(s >= prev); prev = s; }
        }
        assertTrue(rec.tremors.size() >= 4 && rec.tremors.size() <= 16, "3 to 6 s apart for two players over 16 s: " + rec.tremors.size());
    }

    @Test
    void theBudgetThinsByOneRatioAndNeverDropsAPlayerToZero() {
        assertEquals(1.0, Tremor.ratio(100, 400));
        assertEquals(0.5, Tremor.ratio(800, 400));
        int[] req = {1, 3, 100, 700};
        int total = 0, got = 0;
        double r = Tremor.ratio(804, 400);
        for (int n : req) { total += n; int g = Tremor.thin(n, r); assertTrue(g >= 1 && g <= n); got += g; }
        assertTrue(got <= 400 + req.length, "close to the cap: " + got);
        assertEquals(0, Tremor.thin(0, 0.1));
        assertEquals(1, Tremor.thin(1, 0.001));
    }

    @Test
    void theRippleSettlesToZeroAndFakeBlocksAreAlwaysRestored() {
        HuntConfig c = new HuntConfig();
        int ticks = c.tremorRippleTicks;
        int maxSeen = 0;
        for (int tick = 0; tick <= ticks; tick++) for (double d = 0; d < 20; d += 0.5) maxSeen = Math.max(maxSeen, Tremor.Ripple.layers(d, tick, ticks, 12, 2));
        assertEquals(2, maxSeen, "the full height is reached");
        for (double d = 0; d < 20; d += 0.25) {
            assertEquals(0, Tremor.Ripple.layers(d, ticks, ticks, 12, 2), "the last frame is flat");
            assertEquals(0, Tremor.Ripple.layers(1.0, 5, ticks, 12, 2), "the feet stay on real blocks");
        }
        Tremor.FakeBlocks fb = new Tremor.FakeBlocks();
        for (long p = 0; p < 50; p++) fb.show(p, u(1));
        fb.restore(3);
        Set<Long> sentReal = new HashSet<>();
        int n = fb.restoreAll(sentReal::add);
        assertEquals(49, n);
        assertEquals(49, sentReal.size());
        assertFalse(fb.any(), "an early end restores everything");
    }

    @Test
    void numbersForTheReport() throws java.io.IOException {
        StringBuilder b = new StringBuilder();
        HuntConfig c = new HuntConfig();
        for (com.paulorchard.islandcraft.wormsofarrakis.hunt.SizeTable.Size s : com.paulorchard.islandcraft.wormsofarrakis.hunt.SizeTable.Size.values()) {
            double f = com.paulorchard.islandcraft.wormsofarrakis.fx.SizeFactor.of(s.width);
            for (double st : new double[]{0.3, 1.0}) {
                var p = com.paulorchard.islandcraft.wormsofarrakis.hunt.Tremor.plan(st, f, c);
                int cols = (int) Math.round(Math.PI * (p.radius() * p.radius() - 1.5 * 1.5));
                b.append(String.format(java.util.Locale.ROOT, "%s W%.0f stalk %.0f s: S=%.1f radius %.2f layers %d particles %d volume %.2f range %.0f blocks, ripple columns about %d%n", s.label, s.width, s.width + c.stalkExtraSeconds, st, p.radius(), p.layers(), p.particles(), p.volume(), p.range(), cols));
            }
            b.append(String.format(java.util.Locale.ROOT, "%s: build-up at the end of the stalk %.0f grains per second per player (%.0f at the half)%n", s.label, com.paulorchard.islandcraft.wormsofarrakis.hunt.Tremor.buildUpRate(1.0, f, c), com.paulorchard.islandcraft.wormsofarrakis.hunt.Tremor.buildUpRate(0.75, f, c)));
        }
        for (int players : new int[]{1, 5, 20}) {
            Rec rec = new Rec();
            HuntManager m = mgr(rec, new ArrayList<>());
            World w = new World();
            for (int i = 0; i < players; i++) { P p = new P(i + 1, "p" + i); p.x = i * 3; p.sprint = true; w.ps.add(p); }
            m.aggro().setAggro(w.ps.get(0).id, 100.5);
            m.tick(DT, w);
            for (int i = 0; i < 2000; i++) { for (P p : w.ps) p.move(5.6); m.tick(DT, w); }
            long t0 = System.nanoTime();
            int n = 20000;
            for (int i = 0; i < n; i++) { for (P p : w.ps) p.move(5.6); m.tick(DT, w); }
            b.append(String.format(java.util.Locale.ROOT, "brain tick with %d players (one event, all moving): %.1f us (without the game reads)%n", players, (System.nanoTime() - t0) / 1000.0 / n));
        }
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/prompt13-numbers.txt"), b.toString());
    }
}
