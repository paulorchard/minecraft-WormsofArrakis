package com.paulorchard.islandcraft.wormsofarrakis;

import com.paulorchard.islandcraft.wormsofarrakis.hunt.AggroEngine.PlayerState;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.Freeze;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntConfig;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntEvent;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntManager;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.SizeTable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Prompt 14 part 1: the freeze (engine-free). */
class Prompt14FreezeTest {
    private static final double DT = 0.05;

    /** A fake body: records what the freeze puts on it. */
    private static final class FakeBody implements Freeze.Body {
        double cut = 0;
        boolean jumpBlocked, released;
        int applies, holds;
        double firstCut = -1;
        boolean jumpBlockedAtFirst;
        public void apply(double c, boolean j) {
            if (applies++ == 0) { firstCut = c; jumpBlockedAtFirst = j; }
            cut = c; jumpBlocked = j;
        }
        public void hold() { holds++; }
        public void release() { released = true; cut = 0; jumpBlocked = false; }
        boolean clean() { return cut == 0 && !jumpBlocked && released; }
    }

    @Test
    void theSpeedFallsLinearlyToZeroOverAttackSeconds() {
        double T = new HuntConfig().attackSeconds;
        assertEquals(4.0, T);
        assertEquals(0.0, Freeze.cut(0, T), 1e-12);
        assertEquals(0.25, Freeze.cut(1, T), 1e-12);
        assertEquals(0.5, Freeze.cut(2, T), 1e-12);
        assertEquals(0.75, Freeze.cut(3, T), 1e-12);
        assertEquals(1.0, Freeze.cut(4, T), 1e-12);
        assertEquals(1.0, Freeze.cut(9, T), 1e-12, "stays at zero speed");
        FakeBody b = new FakeBody();
        Freeze f = new Freeze(T, b);
        double prev = 0;
        for (int i = 1; i <= 80; i++) {
            f.tick(DT);
            assertEquals(i * DT / T, b.cut, 1e-9, "tick " + i);
            assertEquals(1.0 / 80, b.cut - prev, 1e-9, "equal steps: linear");
            prev = b.cut;
        }
        assertEquals(1.0, b.cut, 1e-9);
        assertTrue(f.held());
    }

    @Test
    void jumpingIsBlockedFromTheFirstTickAndTheHoldOnlyAtZero() {
        FakeBody b = new FakeBody();
        Freeze f = new Freeze(4, b);
        f.tick(DT);
        assertTrue(b.jumpBlockedAtFirst, "from the first tick");
        assertEquals(0, b.holds, "no hold while still slowing");
        for (int i = 0; i < 100; i++) f.tick(DT);
        assertTrue(b.holds > 0, "held at zero");
        assertTrue(b.jumpBlocked);
    }

    @Test
    void releaseLeavesNothingAndIsSafeTwice() {
        FakeBody b = new FakeBody();
        Freeze f = new Freeze(4, b);
        for (int i = 0; i < 30; i++) f.tick(DT);
        assertFalse(b.clean());
        f.release();
        assertTrue(b.clean());
        f.release();
        f.tick(DT);
        assertTrue(b.clean(), "a released freeze never puts anything back");
        assertTrue(f.released());
    }

    // ---- the event around it: cancel paths through the manager ----

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

    private static final class Rec implements HuntManager.Listener {
        final List<HuntEvent.EndReason> ends = new ArrayList<>();
        final List<HuntEvent.Phase> phases = new ArrayList<>();
        Freeze freeze;
        FakeBody body;
        public void onPhaseChange(HuntEvent e, HuntEvent.Phase from, HuntEvent.Phase to) { phases.add(to); }
        public void onEnd(HuntEvent e, HuntEvent.EndReason r) { ends.add(r); if (freeze != null) freeze.release(); }
    }

    /** An event in the freeze phase with the victim frozen. */
    private static Object[] frozenEvent(Env env, Rec rec, HuntManager[] out) {
        HuntManager m = new HuntManager(new HuntConfig(), rec, (e, v, w, s) -> {
            rec.body = new FakeBody();
            rec.freeze = new Freeze(e.attackSeconds, rec.body);
            rec.freeze.tick(0);
        });
        out[0] = m;
        m.tick(DT, env);
        assertEquals(HuntManager.TriggerResult.STARTED, m.forceTrigger(env.ps.get(0).id, SizeTable.Size.SMALL, env));
        HuntEvent e = m.eventOf(env.ps.get(0).id);
        for (int i = 0; i < 4000 && e.phase() == HuntEvent.Phase.STALKING; i++) m.tick(DT, env);
        assertEquals(HuntEvent.Phase.FREEZE, e.phase());
        for (int i = 0; i < 20; i++) { rec.freeze.tick(DT); m.tick(DT, env); }
        assertFalse(rec.body.clean());
        return new Object[]{m, e};
    }

    private void cancelTest(java.util.function.Consumer<Object[]> cancel) {
        Env env = new Env();
        env.ps.add(new Pl(1, "victim"));
        env.ps.add(new Pl(2, "other"));
        env.ps.get(1).onSand = true;
        Rec rec = new Rec();
        HuntManager[] mm = new HuntManager[1];
        Object[] ev = frozenEvent(env, rec, mm);
        cancel.accept(new Object[]{mm[0], env, ev[1]});
        mm[0].tick(DT, env);
        assertEquals(List.of(HuntEvent.EndReason.FIZZLED), rec.ends, "the strike is cancelled");
        assertTrue(rec.body.clean(), "no modifier is left");
        assertNull(mm[0].eventOf(env.ps.get(0).id));
        assertFalse(mm[0].events().stream().anyMatch(x -> !x.ended()), "no event is left");
    }

    @Test
    void victimLogsOffDuringTheFreeze() {
        cancelTest(o -> ((HuntManager) o[0]).logoff(u(1)));
    }

    @Test
    void victimDiesDuringTheFreeze() {
        cancelTest(o -> { ((Env) o[1]).ps.get(0).eligible = false; ((HuntManager) o[0]).died(u(1)); });
    }

    @Test
    void victimGoesCreativeOrSpectatorDuringTheFreeze() {
        cancelTest(o -> ((Env) o[1]).ps.get(0).eligible = false);
    }

    @Test
    void victimChangesDimensionDuringTheFreeze() {
        cancelTest(o -> ((Env) o[1]).ps.get(0).dim = "the_nether");
    }

    @Test
    void victimStepsOffSandDuringTheFreezeTheFreezeGoesOn() {
        Env env = new Env();
        env.ps.add(new Pl(1, "victim"));
        Rec rec = new Rec();
        HuntManager[] mm = new HuntManager[1];
        Object[] ev = frozenEvent(env, rec, mm);
        env.ps.get(0).onSand = false;   // pushed off the sand
        for (int i = 0; i < 20; i++) { rec.freeze.tick(DT); mm[0].tick(DT, env); }
        assertEquals(HuntEvent.Phase.FREEZE, ((HuntEvent) ev[1]).phase());
        assertTrue(rec.ends.isEmpty(), "the freeze goes on");
    }

    @Test
    void anOtherMemberLeavingDoesNotCancelTheFreeze() {
        Env env = new Env();
        env.ps.add(new Pl(1, "victim"));
        env.ps.add(new Pl(2, "other"));
        Rec rec = new Rec();
        HuntManager[] mm = new HuntManager[1];
        Object[] ev = frozenEvent(env, rec, mm);
        env.ps.get(1).eligible = false;
        mm[0].tick(DT, env);
        assertEquals(HuntEvent.Phase.FREEZE, ((HuntEvent) ev[1]).phase());
        assertTrue(rec.ends.isEmpty());
    }

    @Test
    void stopReleasesTheFreezeAndEveryEndReasonCleans() {
        Env env = new Env();
        env.ps.add(new Pl(1, "victim"));
        Rec rec = new Rec();
        HuntManager[] mm = new HuntManager[1];
        frozenEvent(env, rec, mm);
        assertEquals(1, mm[0].stop(null));
        assertEquals(List.of(HuntEvent.EndReason.STOPPED), rec.ends);
        assertTrue(rec.body.clean());
    }

    @Test
    void theFreezeEndsTheEventDoneAndTheScoresAreZero() {
        Env env = new Env();
        env.ps.add(new Pl(1, "victim"));
        env.ps.add(new Pl(2, "other"));
        Rec rec = new Rec();
        HuntManager[] mm = new HuntManager[1];
        Object[] ev = frozenEvent(env, rec, mm);
        mm[0].aggro().setAggro(u(2), 55);
        for (int i = 0; i < 200; i++) { rec.freeze.tick(DT); mm[0].tick(DT, env); }
        assertEquals(List.of(HuntEvent.EndReason.DONE), rec.ends);
        assertEquals(0, mm[0].aggro().getAggro(u(2)));
        assertTrue(rec.body.clean());
    }
}
