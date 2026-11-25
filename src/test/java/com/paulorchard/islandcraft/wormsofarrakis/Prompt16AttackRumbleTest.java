package com.paulorchard.islandcraft.wormsofarrakis;

import static org.junit.jupiter.api.Assertions.*;

import com.paulorchard.islandcraft.wormsofarrakis.fx.SizeFactor;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.AttackRumble;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntConfig;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.Tremor;
import com.paulorchard.islandcraft.wormsofarrakis.rumble.Rumble;
import com.paulorchard.islandcraft.wormsofarrakis.rumble.RumbleCore;
import com.paulorchard.islandcraft.wormsofarrakis.rumble.RumbleSchedule;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Prompt 16 part 1: the attack rumble round the victim (curve, radius, height, the sound clock) and its end paths on the core. */
class Prompt16AttackRumbleTest {
    static final HuntConfig C = new HuntConfig();
    static final double[] WIDTHS = {SizeFactor.SMALL, SizeFactor.MEDIUM, SizeFactor.LARGE, SizeFactor.HUGE};

    @Test
    void theCurveStartsAtZeroPointSevenRisesLinearlyAndStaysAtOne() {
        assertEquals(0.7, AttackRumble.strength(0, 4, 0.7), 1e-12);
        assertEquals(0.85, AttackRumble.strength(2, 4, 0.7), 1e-12);
        assertEquals(1.0, AttackRumble.strength(4, 4, 0.7), 1e-12);
        assertEquals(1.0, AttackRumble.strength(9, 4, 0.7), 1e-12);
        assertEquals(0.7, AttackRumble.strength(-3, 4, 0.7), 1e-12, "before the start: the start value");
        double prev = 0;
        for (double t = 0; t <= 6; t += 0.05) { double s = AttackRumble.strength(t, 4, 0.7); assertTrue(s >= prev - 1e-12 && s <= 1.0 + 1e-12); prev = s; }
        assertEquals(1.0, AttackRumble.strength(0, 0, 0.7), "no freeze: full at once");
        assertEquals(0.5, AttackRumble.strength(0, 4, 0.5), 1e-12, "AttackRumbleStart is a key");
    }

    @Test
    void theRumbleReadsTheCurveThroughTheFractionOfItsLength() {
        AttackRumble.Plan p = AttackRumble.plan(SizeFactor.of(48), C, 20);
        assertEquals(4 + 20 + 5, p.seconds(), 1e-9, "freeze + the longest run + the 5 s hard timeout");
        var f = p.asFraction();
        assertEquals(0.7, f.applyAsDouble(0), 1e-12);
        assertEquals(1.0, f.applyAsDouble(4.0 / p.seconds()), 1e-12);
        assertEquals(1.0, f.applyAsDouble(1.0), 1e-12);
        assertEquals(0.85, f.applyAsDouble(2.0 / p.seconds()), 1e-12);
    }

    @Test
    void radiusScalesWithTheSizeFactorAndHasAFloorOfFive() {
        assertEquals(10.0, AttackRumble.radius(1.0, C), 1e-9, "huge: AttackRumbleRadius");
        double last = 0;
        for (double w : WIDTHS) {
            double r = AttackRumble.radius(SizeFactor.of(w), C);
            assertTrue(r >= 5 && r <= 10 + 1e-9 && r >= last, "W" + w + " radius " + r);
            last = r;
        }
        assertEquals(5.0, AttackRumble.radius(0.1, C), "the floor");
        HuntConfig c2 = new HuntConfig();
        c2.attackRumbleRadius = 30;
        assertEquals(30 * SizeFactor.of(16), AttackRumble.radius(SizeFactor.of(16), c2), 1e-9);
    }

    @Test
    void heightIsTheConfigTimesHalfPlusHalfTheFactorAndAtMostFifteen() {
        assertEquals(12, AttackRumble.maxSixteenths(1.0, C));
        int last = 0;
        for (double w : WIDTHS) {
            int m = AttackRumble.maxSixteenths(SizeFactor.of(w), C);
            assertTrue(m >= 1 && m <= 15 && m >= last, "W" + w + " height " + m + "/16");
            assertEquals(Math.round(12 * (0.5 + 0.5 * SizeFactor.of(w))), m);
            last = m;
        }
        HuntConfig c2 = new HuntConfig();
        c2.attackRumbleMaxSixteenths = 15;
        assertTrue(AttackRumble.maxSixteenths(1.0, c2) <= 15);
    }

    @Test
    void everyColumnMovesAtStrengthOneAndTheShareFollowsTheSquaredCurveBeforeIt() {
        assertEquals(1.0, Tremor.share(1.0), 1e-12, "all columns at strength 1");
        assertEquals(0.49, Tremor.share(0.7), 1e-12, "S squared at the start of the freeze");
        RumbleSchedule s = new RumbleSchedule(Rumble.Params.of(10, 6, 12, 1.0, 0.25, 77).withStrength(t -> 1.0).withShare(Tremor::share), RumbleSchedule.Numbers.DEFAULTS);
        int moving = 0, n = 800;
        for (long col = 0; col < n; col++) { boolean any = false; for (long period = 0; period < 5; period++) if (s.depth(col, period, 3) > 0) any = true; if (any) moving++; }
        assertEquals(n, moving, "at strength 1 every column pulses (in some period)");
    }

    @Test
    void theSoundClockGivesSeededGapsBetweenHalfAndOneSecond() {
        AttackRumble.FxClock a = new AttackRumble.FxClock(5), b = new AttackRumble.FxClock(5), c = new AttackRumble.FxClock(6);
        assertTrue(a.due(0), "the first one is at the start");
        double ta = 0, tc = 0;
        boolean differs = false;
        for (int i = 0; i < 50; i++) {
            double before = a.next();
            a.fired(a.next());
            b.fired(b.next());
            c.fired(c.next());
            double gap = a.next() - before;
            assertTrue(gap >= 0.5 - 1e-12 && gap <= 1.0 + 1e-12, "gap " + gap);
            assertEquals(a.next(), b.next(), 0, "same seed, same gaps");
            if (a.next() != c.next()) differs = true;
        }
        assertTrue(differs, "another seed, other gaps");
    }

    // ---- the end paths on the core (the adapter stops the Handle; the handle is core.stop) ----

    static RumbleCore core(Prompt15RumbleTest.FakeHost h, List<String> log) { return new RumbleCore(h, 600, 3.0, log::add); }

    static RumbleSchedule attack(double seconds) {
        AttackRumble.Plan p = new AttackRumble.Plan(8, 12, 0.7, 4, seconds, 1.0);
        return new RumbleSchedule(Rumble.Params.of(8, seconds, 12, 1.0, 0.25, 9).withStrength(p.asFraction()).withShare(Tremor::share), RumbleSchedule.Numbers.DEFAULTS);
    }

    static final UUID A = new UUID(0, 1), B = new UUID(0, 2);

    @Test
    void stopAtTheDeathLeavesNothingShownAndTheSenderEmpty() {
        var h = new Prompt15RumbleTest.FakeHost();
        List<String> log = new ArrayList<>();
        RumbleCore c = core(h, log);
        RumbleCore.Ev ev = c.start(attack(40), 0.5, 0.5, Prompt15RumbleTest.grid(8), List.of(A, B), 0);
        Prompt15RumbleTest.run(c, 1, 80);   // 4 s: the freeze is over, strength 1
        assertTrue(h.shownAll() > 0, "the sand moves");
        c.stop(ev);   // the victim is dead
        assertEquals(0, h.shownAll(), "every cell is real again at once");
        assertTrue(c.empty());
        int sends = h.sends;
        Prompt15RumbleTest.run(c, 81, 40);
        assertEquals(sends, h.sends, "nothing more is sent after the stop");
        assertTrue(log.stream().anyMatch(s -> s.contains("ended (stopped)")));
    }

    @Test
    void everyCancelPathRestores() {
        // victim logs off (drop without sending), dimension change (the same drop), server stop (stopAll), event stopped (stop)
        for (String path : new String[]{"logoff", "dimension", "serverstop", "stopped"}) {
            var h = new Prompt15RumbleTest.FakeHost();
            RumbleCore c = core(h, new ArrayList<>());
            RumbleCore.Ev ev = c.start(attack(40), 0.5, 0.5, Prompt15RumbleTest.grid(8), List.of(A, B), 0);
            Prompt15RumbleTest.run(c, 1, 60);
            assertTrue(h.shownAll() > 0, path);
            switch (path) {
                case "logoff", "dimension" -> { c.dropViewer(A, path.equals("dimension")); c.stop(ev); }
                case "serverstop" -> c.stopAll("server stopping");
                default -> c.stop(ev);
            }
            assertEquals(0, c.shownCount(A), path);
            assertEquals(0, c.shownCount(B), path);
            assertTrue(c.empty(), path);
            assertFalse(ev.running(), path);
        }
    }

    @Test
    void theHardTimeoutEndsARumbleThatWasNeverStopped() {
        var h = new Prompt15RumbleTest.FakeHost();
        RumbleCore c = core(h, new ArrayList<>());
        RumbleCore.Ev ev = c.start(attack(10), 0.5, 0.5, Prompt15RumbleTest.grid(8), List.of(A), 0);
        Prompt15RumbleTest.run(c, 1, 100);
        assertTrue(h.shownAll() > 0);
        Prompt15RumbleTest.run(c, 101, 110);   // 10 s = 200 ticks: its own end
        assertFalse(ev.running());
        assertEquals("done", ev.endReason());
        assertEquals(0, h.shownAll());
        assertTrue(c.empty());
        // and the core's own safety net after a stall
        var h2 = new Prompt15RumbleTest.FakeHost();
        RumbleCore c2 = core(h2, new ArrayList<>());
        RumbleCore.Ev e2 = c2.start(attack(10), 0.5, 0.5, Prompt15RumbleTest.grid(8), List.of(A), 0);
        Prompt15RumbleTest.run(c2, 1, 20);
        c2.tick(10_000);
        assertEquals("timeout", e2.endReason());
        assertEquals(0, h2.shownAll());
    }

    @Test
    void viewersAreChosenAgainANewcomerSeesTheCellsAndWhoLeftGetsTheRealBlocks() {
        var h = new Prompt15RumbleTest.FakeHost();
        RumbleCore c = core(h, new ArrayList<>());
        RumbleCore.Ev ev = c.start(attack(40), 0.5, 0.5, Prompt15RumbleTest.grid(8), List.of(A), 0);
        Prompt15RumbleTest.run(c, 1, 60);
        assertTrue(h.shown(A) > 0);
        assertEquals(0, h.shown(B));
        int[] r = c.setViewers(ev, List.of(A, B));
        assertArrayEquals(new int[]{1, 0}, r);
        Prompt15RumbleTest.run(c, 61, 10);
        assertTrue(h.shown(B) > 0, "the one who walked in sees the cells that are up");
        int[] r2 = c.setViewers(ev, List.of(B));
        assertArrayEquals(new int[]{0, 1}, r2);
        assertEquals(0, h.shown(A), "the one who walked out has the real blocks again at once");
        assertEquals(0, c.shownCount(A));
        Prompt15RumbleTest.run(c, 71, 10);
        assertEquals(0, h.shown(A), "and is sent nothing more");
        assertTrue(h.shown(B) > 0);
        c.stop(ev);
        assertTrue(c.empty());
        assertArrayEquals(new int[]{0, 0}, c.setViewers(ev, List.of(A)), "an ended rumble ignores viewer changes");
    }
}
