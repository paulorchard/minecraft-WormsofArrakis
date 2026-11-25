package com.paulorchard.islandcraft.wormsofarrakis;

import static org.junit.jupiter.api.Assertions.*;

import com.paulorchard.islandcraft.wormsofarrakis.camera.DeathCam;
import com.paulorchard.islandcraft.wormsofarrakis.fx.EruptionFx;
import com.paulorchard.islandcraft.wormsofarrakis.hazard.Box;
import com.paulorchard.islandcraft.wormsofarrakis.hazard.HazardRules;
import com.paulorchard.islandcraft.wormsofarrakis.hazard.WormHazard;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.Alignment;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.ChunkTickets;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.EntitySand;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.RumbleFollow;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.SandProbe;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.SizeTable;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.StrikeArea;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.StrikeGroup;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.StrikeType;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.SwallowRules;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;
import com.paulorchard.islandcraft.wormsofarrakis.rumble.RumbleCore;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveFactory;
import com.paulorchard.islandcraft.wormsofarrakis.test.WormSpec;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Prompt 17: the strike area, the group, the swallowed state, the rumble that follows, the sand-only immunity, the clipping and the parts. No Minecraft type is used. */
class Prompt17Test {
    static final String[] MOVES = {"crest", "crash", "emerge", "engulf"};

    static double r0Of(StrikeType t, SizeTable.Size s) { return MoveFactory.get(Alignment.once(t, s)).r0; }

    // ---- the strike area ----

    @Test
    void theSixteenRadiiEqualTheLargestRingOrDiscOfTheEruptionRowsTimesR0AndTheMaximumIsTheLargest() {
        double largest = 0;
        for (EruptionFx.Row r : EruptionFx.ROWS) if (r.shape() == EruptionFx.Shape.RING || r.shape() == EruptionFx.Shape.DISC) largest = Math.max(largest, r.ring());   // an independent read of the rows
        assertTrue(largest > 0);
        assertEquals(largest, StrikeArea.largestRingUnits(), 1e-12, "drift test: the area follows the rows");
        StringBuilder out = new StringBuilder("strike area numbers:\n");
        for (SizeTable.Size s : SizeTable.Size.values()) {
            double[] radii = new double[4];
            for (StrikeType t : StrikeType.values()) {
                double r0 = r0Of(t, s);
                radii[t.ordinal()] = StrikeArea.radius(r0, 0, 0);
                assertEquals(largest * r0, radii[t.ordinal()], 1e-9, t.label + " " + s.label);
                out.append(String.format("  %s %s: r0 %.2f -> radius %.2f%n", t.label, s.label, r0, radii[t.ordinal()]));
            }
            double max = 0;
            for (double r : radii) max = Math.max(max, r);
            assertEquals(max, StrikeArea.max(radii), 1e-12);
            for (double r : radii) assertTrue(r <= max + 1e-12);
        }
        System.out.print(out);
        // r0 are the preset ring radii 3.99 / 7.96 / 15.92 / 23.88
        assertEquals(3.99, r0Of(StrikeType.CRASH, SizeTable.Size.SMALL), 0.01);
        assertEquals(23.88, r0Of(StrikeType.ENGULF, SizeTable.Size.HUGE), 0.01);
    }

    @Test
    void theMarginAddsAndAnOverrideReplaces() {
        double r0 = 7.96;
        double d = StrikeArea.derived(r0, 0);
        assertEquals(d + 3, StrikeArea.derived(r0, 3), 1e-9);
        assertEquals(d + 3, StrikeArea.radius(r0, 0, 3), 1e-9, "no override: derived + margin");
        assertEquals(20, StrikeArea.radius(r0, 20, 3), 1e-9, "an override above zero is the radius");
    }

    // ---- the selection ----

    static UUID id(int n) { return new UUID(0, n); }

    static StrikeGroup.Cand cand(int n, double x, double z, boolean alive, boolean mode, boolean sand, long held) {
        return new StrikeGroup.Cand(id(n), "p" + n, x, z, "overworld", alive, mode, sand, held);
    }

    @Test
    void selectionInsideAndOutsideTheEdgeAndTheModesAndSandAndHeldAreAllRespected() {
        StrikeGroup.Cand v = cand(1, 0, 0, true, true, true, StrikeGroup.Cand.FREE);
        double R = 40;
        List<StrikeGroup.Cand> all = new ArrayList<>();
        all.add(v);
        all.add(cand(2, R, 0, true, true, true, StrikeGroup.Cand.FREE));          // on the edge: in
        all.add(cand(3, R + 1, 0, true, true, true, StrikeGroup.Cand.FREE));      // one block over: out (far, no reason)
        all.add(cand(4, 10, 0, true, false, true, StrikeGroup.Cand.FREE));        // Creative or Spectator
        all.add(cand(5, 10, 0, false, true, true, StrikeGroup.Cand.FREE));        // dead
        all.add(cand(6, 10, 0, true, true, false, StrikeGroup.Cand.FREE));        // rock, a placed block, mid-air without sand
        all.add(cand(7, 10, 0, true, true, true, 9));                              // held by event 9
        all.add(new StrikeGroup.Cand(id(8), "p8", 5, 0, "the_nether", true, true, true, StrikeGroup.Cand.FREE));   // another dimension
        StrikeGroup.Selection sel = StrikeGroup.select(v, all, R);
        assertEquals(List.of(id(1), id(2)), sel.members(), "the victim first, then the one on the edge");
        assertEquals("not in Survival or Adventure", sel.skipped().get(id(4)));
        assertEquals("dead", sel.skipped().get(id(5)));
        assertEquals("not on Dune Sand", sel.skipped().get(id(6)));
        assertEquals("held by event 9", sel.skipped().get(id(7)));
        assertFalse(sel.skipped().containsKey(id(3)), "far away: not even a reason");
        assertFalse(sel.skipped().containsKey(id(8)));
        assertFalse(sel.members().contains(id(3)) || sel.members().contains(id(8)));
    }

    @Test
    void theVictimIsInEvenAloneEvenOffSandAndAHundredMembersAreAllSelected() {
        StrikeGroup.Cand v = cand(1, 0, 0, true, true, false, StrikeGroup.Cand.FREE);
        assertEquals(List.of(id(1)), StrikeGroup.select(v, List.of(v), 30).members());
        List<StrikeGroup.Cand> all = new ArrayList<>();
        all.add(v);
        for (int i = 2; i <= 101; i++) all.add(cand(i, (i % 10) * 3, (i / 10) * 3 - 15, true, true, true, StrikeGroup.Cand.FREE));
        StrikeGroup.Selection sel = StrikeGroup.select(v, all, 60);
        assertEquals(101, sel.members().size(), "no cap: nobody is dropped");
        assertEquals(new HashSet<>(sel.members()).size(), sel.members().size());
        assertTrue(sel.skipped().isEmpty());
    }

    @Test
    void theGroupIsNeverExtendedLater() {
        StrikeGroup.Cand v = cand(1, 0, 0, true, true, true, StrikeGroup.Cand.FREE);
        List<UUID> members = new ArrayList<>(StrikeGroup.select(v, List.of(v), 40).members());
        List<UUID> frozen = List.copyOf(members);
        // a player walks in afterwards: the selection is a pure function called once, the group is a fixed list
        StrikeGroup.Selection later = StrikeGroup.select(v, List.of(v, cand(2, 5, 0, true, true, true, StrikeGroup.Cand.FREE)), 40);
        assertEquals(2, later.members().size());
        assertEquals(frozen, members, "the group fixed at the start is not changed by a later call");
    }

    @Test
    void theReleaseAtTheChoiceKeepsTheVictimAndWhoIsInsideAndLetsGoWhoIsOutside() {
        Map<UUID, double[]> spots = new LinkedHashMap<>();
        spots.put(id(1), new double[]{0, 0});
        spots.put(id(2), new double[]{10, 0});
        spots.put(id(3), new double[]{31, 0});
        spots.put(id(4), new double[]{-30, 0});
        assertEquals(List.of(id(3)), StrikeGroup.releasedAtChoice(id(1), spots, 0, 0, 30, true), "only the one past the radius");
        assertTrue(StrikeGroup.releasedAtChoice(id(1), spots, 0, 0, 30, false).isEmpty(), "GroupReleaseOutside false keeps everyone");
        // the victim is never released, even if the circle is tiny and centred elsewhere
        assertFalse(StrikeGroup.releasedAtChoice(id(1), spots, 100, 100, 1, true).contains(id(1)));
        assertEquals(3, StrikeGroup.releasedAtChoice(id(1), spots, 100, 100, 1, true).size());
    }

    // ---- the swallowed state ----

    @Test
    void theKillComesAtTheEndAndEveryOtherWayOutReleasesAliveExceptLeavingWhileSwallowed() {
        for (boolean nokill : new boolean[]{false, true}) {
            for (SwallowRules.End why : SwallowRules.End.values()) {
                SwallowRules.Action held = SwallowRules.actionFor(SwallowRules.State.HELD, why, nokill);
                assertEquals(why == SwallowRules.End.DIED_OTHER ? SwallowRules.Action.NONE : SwallowRules.Action.RELEASE, held, "a held player is only ever released: " + why);
                SwallowRules.Action sw = SwallowRules.actionFor(SwallowRules.State.SWALLOWED, why, nokill);
                boolean killsAtOnce = why == SwallowRules.End.STRIKE_END || why == SwallowRules.End.SAFETY_TIME || why == SwallowRules.End.LOGOFF || why == SwallowRules.End.DIMENSION;
                SwallowRules.Action want = why == SwallowRules.End.DIED_OTHER ? SwallowRules.Action.NONE : killsAtOnce && !nokill ? SwallowRules.Action.KILL : SwallowRules.Action.RELEASE;
                assertEquals(want, sw, "swallowed, " + why + ", nokill=" + nokill);
            }
        }
        for (SwallowRules.End why : SwallowRules.End.values()) {
            assertEquals(SwallowRules.Action.NONE, SwallowRules.actionFor(SwallowRules.State.KILLED, why, false));
            assertEquals(SwallowRules.Action.NONE, SwallowRules.actionFor(SwallowRules.State.RELEASED, why, false));
        }
        assertEquals(SwallowRules.Action.RELEASE, SwallowRules.actionFor(SwallowRules.State.SWALLOWED, SwallowRules.End.STOPPED, false), "stopped event: alive");
        assertEquals(SwallowRules.Action.RELEASE, SwallowRules.actionFor(SwallowRules.State.SWALLOWED, SwallowRules.End.FIZZLED, false), "fizzled event: alive");
        assertEquals(SwallowRules.Action.RELEASE, SwallowRules.actionFor(SwallowRules.State.SWALLOWED, SwallowRules.End.SERVER_STOP, false), "server stop: alive");
    }

    @Test
    void theSafetyTimeIsTheRunPlusThreeUnlessConfigured() {
        assertEquals(23.0, SwallowRules.safetySeconds(20, 0), 1e-9);
        assertEquals(40.0, SwallowRules.safetySeconds(20, 40), 1e-9);
    }

    @Test
    void aSwallowedPlayerCannotDoOrSufferAnythingButChatAndTheFinalKill() {
        for (SwallowRules.Interaction i : SwallowRules.Interaction.values()) {
            boolean ok = i == SwallowRules.Interaction.CHAT || i == SwallowRules.Interaction.COMMAND;
            assertEquals(!ok, SwallowRules.blocked(SwallowRules.State.SWALLOWED, i), i.toString());
            for (SwallowRules.State s : new SwallowRules.State[]{SwallowRules.State.HELD, SwallowRules.State.KILLED, SwallowRules.State.RELEASED}) assertFalse(SwallowRules.blocked(s, i), s + " " + i);
        }
        assertFalse(SwallowRules.damageAllowed(SwallowRules.State.SWALLOWED, false), "void, fall, drowning, suffocation: cancelled");
        assertTrue(SwallowRules.damageAllowed(SwallowRules.State.SWALLOWED, true), "the final kill passes");
        assertTrue(SwallowRules.damageAllowed(SwallowRules.State.HELD, false));
        assertTrue(SwallowRules.damageAllowed(SwallowRules.State.KILLED, false));
    }

    /** A fake body: everything the strike puts on a player, and the way each end path takes it off (the rules are SwallowRules; this applies them as the game side does). */
    static final class Body {
        boolean frozen = true, hidden, protectedFromDamage, camera = true, rumble = true, dead;
        SwallowRules.State state = SwallowRules.State.HELD;

        void swallow() { state = SwallowRules.State.SWALLOWED; hidden = true; protectedFromDamage = true; }

        void end(SwallowRules.End why, boolean nokill) {
            SwallowRules.Action a = SwallowRules.actionFor(state, why, nokill);
            switch (a) {
                case KILL -> { state = SwallowRules.State.KILLED; protectedFromDamage = false; frozen = false; rumble = false; dead = true; /* camera stays behind the death screen */ }
                case RELEASE -> { state = SwallowRules.State.RELEASED; hidden = false; protectedFromDamage = false; frozen = false; rumble = false; camera = false; }
                case NONE -> { if (state == SwallowRules.State.HELD || state == SwallowRules.State.SWALLOWED) { state = SwallowRules.State.RELEASED; hidden = false; protectedFromDamage = false; frozen = false; rumble = false; camera = false; } }
            }
        }
    }

    @Test
    void nothingIsLeftOnAnyPlayerAfterAnyPath() {
        for (SwallowRules.End why : SwallowRules.End.values()) for (boolean swallowed : new boolean[]{false, true}) for (boolean nokill : new boolean[]{false, true}) {
            Body b = new Body();
            if (swallowed) b.swallow();
            b.end(why, nokill);
            String at = why + " swallowed=" + swallowed + " nokill=" + nokill;
            assertFalse(b.frozen, "no freeze: " + at);
            assertFalse(b.protectedFromDamage, "vulnerable again: " + at);
            assertFalse(b.rumble, "no rumble: " + at);
            if (!b.dead) { assertFalse(b.hidden, "visible: " + at); assertFalse(b.camera, "their own view back: " + at); }
            if (b.dead) assertTrue(swallowed && !nokill, "only a swallowed player is killed, and not with nokill: " + at);
        }
    }

    @Test
    void membersLeaveOnTheirOwnAndOnlyTheVictimLeavingCancelsTheEvent() {
        // the hunt's rule (HuntEvent.removeMember): the victim leaving before the devour ends the event, a member leaving ends only their own state
        Map<UUID, Body> bodies = new LinkedHashMap<>();
        for (int i = 1; i <= 4; i++) bodies.put(id(i), new Body());
        bodies.get(id(2)).end(SwallowRules.End.LOGOFF, false);
        bodies.get(id(3)).end(SwallowRules.End.MODE_CHANGE, false);
        bodies.get(id(4)).end(SwallowRules.End.DIMENSION, false);
        for (int i = 2; i <= 4; i++) assertEquals(SwallowRules.State.RELEASED, bodies.get(id(i)).state, "p" + i + " released alone");
        assertEquals(SwallowRules.State.HELD, bodies.get(id(1)).state, "the victim and the strike go on");
        // the victim is cancelled: FIZZLED releases everyone
        for (Body b : bodies.values()) b.end(SwallowRules.End.FIZZLED, false);
        for (Body b : bodies.values()) assertEquals(SwallowRules.State.RELEASED, b.state);
    }

    @Test
    void survivorsAreReleasedAtTheEndAndTheSwallowedAreKilled() {
        Body survivor = new Body(), taken = new Body();
        taken.swallow();
        survivor.end(SwallowRules.End.STRIKE_END, false);
        taken.end(SwallowRules.End.STRIKE_END, false);
        assertEquals(SwallowRules.State.RELEASED, survivor.state);
        assertEquals(SwallowRules.State.KILLED, taken.state);
        assertTrue(taken.camera, "the killed keep the camera behind the death screen");
        assertFalse(survivor.camera);
    }

    // ---- the rumble that follows the player ----

    @Test
    void aPlayerWhoMovesEightBlocksWhileSlowingGetsARumbleCentredOnWhereTheyAreHeldAndTheOldOnesLeaveNothing() {
        Prompt15RumbleTest.FakeHost h = new Prompt15RumbleTest.FakeHost();
        List<String> log = new ArrayList<>();
        RumbleCore c = Prompt15RumbleTest.core(h, 6000, log);
        UUID A = Prompt15RumbleTest.A;
        // the freeze: 4 s = 80 ticks, the speed falls linearly, so the distance walked is (1 - (1 - f)^2) x 8 blocks (a linear ramp integrates to a parabola)
        double total = 8;
        RumbleFollow follow = new RumbleFollow(0.25, 0, 0);
        RumbleCore.Ev current = c.start(Prompt15RumbleTest.sched(8, 60, 8, 1), 0.5, 0.5, Prompt15RumbleTest.grid(8), List.of(A), 0);
        List<RumbleCore.Ev> retired = new ArrayList<>();
        List<Double> retireAt = new ArrayList<>();
        long tick = 0;
        double px = 0;
        int starts = 1;
        for (int k = 1; k <= 120; k++) {   // 6 s: 4 s of freeze then held
            double f = Math.min(1, k / 80.0);
            px = total * (1 - (1 - f) * (1 - f));
            boolean held = k >= 80;
            double[] nc = follow.update(0.05, px, 0, held);
            tick++;
            if (nc != null) {
                retired.add(current);
                retireAt.add(k * 0.05 + 0.25);
                current = c.start(Prompt15RumbleTest.sched(8, 60, 8, 100 + starts), nc[0] + 0.5, 0.5, shifted(Prompt15RumbleTest.grid(8), (int) Math.round(nc[0])), List.of(A), tick);
                starts++;
            }
            for (int i = retired.size() - 1; i >= 0; i--) if (k * 0.05 >= retireAt.get(i)) { c.stop(retired.get(i)); retired.remove(i); retireAt.remove(i); }
            c.tick(tick);
        }
        assertTrue(follow.pinned(), "pinned once held");
        assertEquals(px, follow.x(), 0.5, "centred within half a block of the held position at 100 % and from then on");
        assertEquals(8, follow.x(), 0.5);
        int pinnedAfter = follow.recentred;
        for (int k = 0; k < 40; k++) assertNull(follow.update(0.05, px + 0.3, 0.2, true), "it does not move any more once pinned");
        assertEquals(pinnedAfter, follow.recentred);
        assertTrue(starts > 5, "it followed (" + starts + " rumbles started in all)");
        assertTrue(starts <= 1 + 16 + 1, "at most one per follow period while slowing, and the pin");
        assertTrue(retired.isEmpty(), "every old rumble was stopped after its overlap");
        c.stop(current);
        assertEquals(0, h.shownAll(), "the sender is empty: the old rumbles leave nothing behind");
        assertTrue(c.empty());
    }

    static List<RumbleCore.Col> shifted(List<RumbleCore.Col> l, int dx) {
        List<RumbleCore.Col> o = new ArrayList<>();
        for (RumbleCore.Col c : l) o.add(new RumbleCore.Col(c.x() + dx, c.z(), c.surfaceY()));
        return o;
    }

    @Test
    void aPlayerWhoDoesNotMoveIsNotRecentredAndAMoveUnderABlockIsIgnored() {
        RumbleFollow f = new RumbleFollow(0.25, 5, 5);
        for (int k = 1; k <= 60; k++) assertNull(f.update(0.05, 5.3, 5.2, false), "under one block: left alone");
        assertEquals(0, f.recentred);
        assertNotNull(f.update(0.05, 9, 5, true), "held: pinned at once to where they stand");
        assertEquals(9, f.x(), 1e-9);
    }

    // ---- sand only ----

    static SandProbe.Cls sandTo(int top, int y) { return y <= top ? SandProbe.Cls.SAND : SandProbe.Cls.AIR; }

    @Test
    void onDuneSandOnTheGroundARockAPlacedBlockAndTheAirAreRead() {
        // the sand top block is y = 63, the player's feet are at y = 64
        assertTrue(EntitySand.onSand(true, 64.0, 0.3, y -> y <= 63 ? SandProbe.Cls.SAND : SandProbe.Cls.AIR));
        assertFalse(EntitySand.onSand(true, 64.0, 0.3, y -> y == 63 ? SandProbe.Cls.OTHER : y < 63 ? SandProbe.Cls.SAND : SandProbe.Cls.AIR), "rock under the feet");
        assertFalse(EntitySand.onSand(true, 65.0, 0.3, y -> y == 64 ? SandProbe.Cls.OTHER : y <= 63 ? SandProbe.Cls.SAND : SandProbe.Cls.AIR), "a placed block on the sand: the block under the feet is not sand");
        assertTrue(EntitySand.onSand(true, 64.2, 0.3, y -> y <= 63 ? SandProbe.Cls.SAND : SandProbe.Cls.AIR), "a step above: the tolerance");
        // in the air: sand within 1.5 blocks below
        assertTrue(EntitySand.onSand(false, 65.0, 0.3, y -> y <= 63 ? SandProbe.Cls.SAND : SandProbe.Cls.AIR), "a jump: 1 block of air above the sand");
        assertTrue(EntitySand.onSand(false, 65.4, 0.3, y -> y <= 63 ? SandProbe.Cls.SAND : SandProbe.Cls.AIR), "1.4 blocks above the sand");
        assertFalse(EntitySand.onSand(false, 66.0, 0.3, y -> y <= 63 ? SandProbe.Cls.SAND : SandProbe.Cls.AIR), "2 blocks above: not on sand");
        assertFalse(EntitySand.onSand(false, 65.0, 0.3, y -> y == 64 ? SandProbe.Cls.AIR : y == 63 ? SandProbe.Cls.OTHER : SandProbe.Cls.SAND), "rock below the air");
        assertFalse(EntitySand.onSand(false, 70.0, 0.3, y -> SandProbe.Cls.AIR), "high in the air over nothing");
    }

    static HazardRules.Subject subject(boolean player, boolean onSand) {
        return new HazardRules.Subject(true, player, false, false, true, false, false, false, false, onSand);
    }

    @Test
    void aPlayerAndAMobOffSandAreSparedAndBackOnSandTheyAreKilledAndEachKindSwitchesOffByItsOwnKey() {
        assertEquals(HazardRules.Verdict.NOT_ON_SAND, HazardRules.decide(subject(true, false), true, true, true, true));
        assertEquals(HazardRules.Verdict.NOT_ON_SAND, HazardRules.decide(subject(false, false), true, true, true, true));
        assertEquals(HazardRules.Verdict.KILL, HazardRules.decide(subject(true, true), true, true, true, true), "back on sand: vulnerable from that instant");
        assertEquals(HazardRules.Verdict.KILL, HazardRules.decide(subject(false, true), true, true, true, true));
        // each kind has its own key
        assertEquals(HazardRules.Verdict.KILL, HazardRules.decide(subject(true, false), true, true, false, true), "players' immunity off: killed off sand");
        assertEquals(HazardRules.Verdict.NOT_ON_SAND, HazardRules.decide(subject(false, false), true, true, false, true));
        assertEquals(HazardRules.Verdict.KILL, HazardRules.decide(subject(false, false), true, true, true, false), "mobs' immunity off");
        assertEquals(HazardRules.Verdict.NOT_ON_SAND, HazardRules.decide(subject(true, false), true, true, true, false));
        // the old form of the rule (prompt 16) is unchanged and spares nobody for sand
        assertEquals(HazardRules.Verdict.KILL, HazardRules.decide(subject(true, false), true, true));
    }

    // ---- the clipping and the parts ----

    static WormSpec crash(String size) { return Prompt16HazardTest.preset(size, "crash"); }

    @Test
    void whatIsUnderTheSandLevelIsNotTouchedAndTheSameBoxAboveItIs() {
        int found = 0;
        for (String size : new String[]{"small", "medium", "large", "huge"}) for (String move : MOVES) {
            WormSpec s = Prompt16HazardTest.preset(size, move);
            Moves.Move m = MoveFactory.get(s);
            WormHazard hz = new WormHazard(s, 0, 0, 0, 0, WormHazard.Settings.DEFAULTS.withSubsteps(1));
            for (double tau = 0.2; tau < m.playTime(); tau += 0.37) {
                WormHazard.Pose p = hz.poseAt(tau);
                for (int k = 1; k < p.drawn.length - 1; k++) {
                    if (!p.drawn[k] || p.centre[k][1] > -0.3 || p.radius[k] < 0.5) continue;
                    double[] w = hz.toWorld(p.centre[k][0], p.centre[k][1], p.centre[k][2]);
                    Box under = new Box(w[0], w[1], w[2], w[0], w[1], w[2]);
                    if (!hz.touches(under, tau)) continue;   // the joint's own centre is inside its capsule
                    found++;
                    assertNull(hz.firstContact(under, tau - 1e-6, tau), "under the sand level: inside the buried body, not touched (" + size + " " + move + ")");
                    Box above = new Box(w[0], 0.0, w[2], w[0], 0.0, w[2]);   // the same column, at the sand level
                    // a box that stands on the sand and reaches down into the body: only its part above the level counts
                    Box tall = new Box(w[0] - 0.3, w[1], w[2] - 0.3, w[0] + 0.3, 1.8, w[2] + 0.3);
                    assertEquals(hz.touches(above.above(-1e-6), tau) || hz.touches(tall.above(-1e-6), tau), hz.firstContact(tall, tau - 1e-6, tau) != null, "a tall box counts by its part above the level");
                    if (found > 40) return;
                }
            }
        }
        assertTrue(found > 0, "the test found buried parts of the body to check");
    }

    @Test
    void aBoxAboveTheLevelInsideTheBodyIsTouchedAndTheBoxItselfIsClippedToTheLevel() {
        Box b = new Box(0, -2, 0, 1, 3, 1);
        Box up = b.above(0);
        assertEquals(0, up.minY, 1e-12);
        assertEquals(3, up.maxY, 1e-12);
        assertNull(new Box(0, -2, 0, 1, -0.5, 1).above(0), "wholly below: nothing");
        assertSame(b, b.above(-5), "wholly above: unchanged");
    }

    @Test
    void theVictimIsSwallowedOnlyByTheThroatAndOthersByAnyVisiblePart() {
        WormSpec s = crash("medium");
        Moves.Move m = MoveFactory.get(s);
        WormHazard hz = new WormHazard(s, 0, 0, 0, 0, WormHazard.Settings.DEFAULTS.withSubsteps(1));
        // a body point that is visible (above the level) and far from the head: the middle of a drawn joint
        double tauBody = -1;
        int kBody = -1;
        for (double tau = 0.3; tau < m.playTime() && kBody < 0; tau += 0.1) {
            WormHazard.Pose p = hz.poseAt(tau);
            for (int k = 5; k < p.drawn.length - 2; k++) if (p.drawn[k] && p.centre[k][1] > 0.5) { tauBody = tau; kBody = k; break; }
        }
        assertTrue(kBody >= 0, "found a visible body joint");
        WormHazard.Pose p = hz.poseAt(tauBody);
        double[] w = hz.toWorld(p.centre[kBody][0], p.centre[kBody][1], p.centre[kBody][2]);
        Box inBody = new Box(w[0], w[1], w[2], w[0], w[1], w[2]);
        Set<String> victim = Set.of("throat"), others = Set.of("body", "head", "throat");
        double hx = hz.toWorld(p.headC[0], p.headC[1], p.headC[2])[0];
        assertNull(hz.firstContact(inBody, tauBody - 1e-6, tauBody, victim), "the victim touched by the body only is not swallowed");
        assertEquals("body", hz.firstContact(inBody, tauBody - 1e-6, tauBody, others).part(), "another player touched by the body is swallowed");
        assertEquals("body", hz.firstContact(inBody, tauBody - 1e-6, tauBody, Set.of("body")).part(), "OthersParts changes it");
        assertNull(hz.firstContact(inBody, tauBody - 1e-6, tauBody, Set.of("head", "throat")), "OthersParts without the body spares the body touch");
        assertNotNull(hx);
        // the throat: a point inside the funnel behind the rim, with the head above the sand
        double tauThroat = -1;
        double[] wt = null;
        for (double tau = 0.1; tau < m.playTime() && wt == null; tau += 0.02) {
            WormHazard.Pose q = hz.poseAt(tau);
            if (q.mouthDeg < 20) continue;
            double R = hz.R;
            double[] local = {0, 0, -0.25 * R};
            double[] mm = WormHazard.localToModel(q, local);
            if (mm[1] < 1.0) continue;
            double[] cand = hz.toWorld(mm[0], mm[1], mm[2]);
            if (hz.touches(new Box(cand[0], cand[1], cand[2], cand[0], cand[1], cand[2]), tau)) { wt = cand; tauThroat = tau; }
        }
        assertNotNull(wt, "found a moment when the funnel is open above the sand");
        Box inMouth = new Box(wt[0], wt[1], wt[2], wt[0], wt[1], wt[2]);
        WormHazard.Contact c = hz.firstContact(inMouth, tauThroat - 1e-6, tauThroat, victim);
        assertNotNull(c, "the victim is swallowed when the funnel touches them");
        assertEquals("throat", c.part());
    }

    @Test
    void theTouchTimeOfTheThroatBodyAndHeadPerMoveAndSizeAgainstTheOldDevourTable() {
        StringBuilder out = new StringBuilder("touch times (a standing player at the entry crossing, sand level y = 0), seconds after the move started; the old devour table in brackets:\n");
        com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntConfig cfg = new com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntConfig();
        for (StrikeType t : StrikeType.values()) for (SizeTable.Size sz : SizeTable.Size.values()) {
            WormSpec s = Alignment.once(t, sz);
            Moves.Move m = MoveFactory.get(s);
            double entryZ = Alignment.entryZ(m);
            WormHazard hz = new WormHazard(s, 0, 0, 0, 0, WormHazard.Settings.DEFAULTS.withSubsteps(1));
            double[] first = {-1, -1, -1};   // body, head, throat
            String[] parts = {"body", "head", "throat"};
            Box player = Box.of(0 + 0.0, 0.0, entryZ, 0.6, 1.8);   // the model frame of the strike: the entry crossing is at model z = entryZ, x = 0 (world = model at yaw 0)
            for (double tau = 0; tau <= m.playTime(); tau += 0.02) {
                for (int i = 0; i < 3; i++) {
                    if (first[i] >= 0) continue;
                    WormHazard.Contact c = hz.firstContact(player, tau - 1e-6, tau, Set.of(parts[i]));
                    if (c != null) first[i] = tau;
                }
            }
            out.append(String.format("  %s %s: body %s head %s throat %s (devour table %.2f)%n", t.label, sz.label, fmtT(first[0]), fmtT(first[1]), fmtT(first[2]), cfg.devourSeconds[t.ordinal()][sz.ordinal()]));
        }
        System.out.print(out);
    }

    static String fmtT(double t) { return t < 0 ? "never" : String.format(java.util.Locale.ROOT, "%.2f", t); }

    // ---- the strike cameras of several players ----

    @Test
    void severalPlayersEachGetTheirOwnCameraRoundTheirOwnSpotAndTheSeedGivesTheSameSideAndDirection() {
        DeathCam.Params P = DeathCam.Params.DEFAULTS;
        long event = 7;
        Map<UUID, DeathCam.Setup> setups = new LinkedHashMap<>();
        double[][] spots = {{0, 0}, {10, -4}, {-6, 12}};
        for (int i = 0; i < 3; i++) {
            long seed = event * 0x9E3779B97F4A7C15L ^ id(i + 1).getLeastSignificantBits();
            setups.put(id(i + 1), DeathCam.setup(P, spots[i][0], 64, spots[i][1], 90, 40, seed));
        }
        for (int i = 0; i < 3; i++) {
            DeathCam.Setup s = setups.get(id(i + 1));
            DeathCam.Pose p0 = DeathCam.pose(s, 0, 20, null), p1 = DeathCam.pose(s, 20, 20, null);
            assertEquals(40, Math.hypot(p0.x() - spots[i][0], p0.z() - spots[i][1]), 1e-6, "p" + (i + 1) + " orbits their own spot");
            assertEquals(40, Math.hypot(p1.x() - spots[i][0], p1.z() - spots[i][1]), 1e-6);
            long seed = event * 0x9E3779B97F4A7C15L ^ id(i + 1).getLeastSignificantBits();
            assertEquals(s, DeathCam.setup(P, spots[i][0], 64, spots[i][1], 90, 40, seed), "the same seed, the same side and direction");
        }
        // the turn is spread over the whole run (a 20 s move): the camera is at half the angle at half the time, and its angular speed is zero at both ends
        DeathCam.Setup s = setups.get(id(1));
        double a0 = DeathCam.azimuth(s, 0, 20), a1 = DeathCam.azimuth(s, 20, 20), am = DeathCam.azimuth(s, 10, 20);
        assertEquals(0.5, (am - a0) / (a1 - a0), 1e-9);
        assertEquals(Math.toRadians(120), Math.abs(a1 - a0), 1e-9);
        // DeathCamTurnDegrees = 0: a locked camera
        DeathCam.Params locked = new DeathCam.Params(P.distance(), 0, P.lookHeight(), P.heightFactor(), P.minHeight(), P.clearance(), P.maxSeconds());
        DeathCam.Setup ls = DeathCam.setup(locked, 0, 64, 0, 0, 40, 3);
        DeathCam.Pose l0 = DeathCam.pose(ls, 0, 20, null), l1 = DeathCam.pose(ls, 20, 20, null);
        assertEquals(l0.x(), l1.x(), 1e-9);
        assertEquals(l0.z(), l1.z(), 1e-9);
        assertEquals(l0.yaw(), l1.yaw(), 1e-6, "locked");
    }

    // ---- the chunk tickets ----

    @Test
    void theChunksOfTheTicketAreTheLoadedOnesNearestFirstAndNeverMoreThanTheLimit() {
        Set<Long> loaded = new HashSet<>();
        for (int x = -20; x <= 20; x++) for (int z = -20; z <= 20; z++) loaded.add(((long) x << 32) ^ (z & 0xffffffffL));
        List<int[]> all = ChunkTickets.select(8, 8, 100, 400, (cx, cz) -> loaded.contains(((long) cx << 32) ^ (cz & 0xffffffffL)));
        assertTrue(all.size() > 100);
        double prev = -1;
        for (int[] c : all) {
            double d = Math.hypot(c[0] * 16 + 8 - 8, c[1] * 16 + 8 - 8);
            assertTrue(d >= prev - 1e-9, "nearest first");
            prev = d;
        }
        assertEquals(10, ChunkTickets.select(8, 8, 100, 10, (cx, cz) -> true).size(), "the limit");
        assertEquals(0, ChunkTickets.select(8, 8, 100, 400, (cx, cz) -> false).size(), "unloaded chunks are never ticketed (nothing is generated)");
        assertEquals(0, ChunkTickets.select(8, 8, 100, 0, (cx, cz) -> true).size());
    }

    // ---- the cost of many members ----

    @Test
    void theCostOfTheRumblesOfOneFourSixteenAndAHundredMembers() {
        StringBuilder out = new StringBuilder("group cost (one attack rumble per member, radius 6, 4 blocks apart so they overlap, 120 ticks, budget 600 cells per tick):\n");
        for (int n : new int[]{1, 4, 16, 100}) {
            Prompt15RumbleTest.FakeHost h = new Prompt15RumbleTest.FakeHost();
            RumbleCore c = Prompt15RumbleTest.core(h, 600, new ArrayList<>());
            List<UUID> viewers = new ArrayList<>();
            for (int i = 0; i < n; i++) viewers.add(id(1000 + i));
            int side = (int) Math.ceil(Math.sqrt(n));
            for (int i = 0; i < n; i++) {
                int px = (i % side) * 4, pz = (i / side) * 4;
                c.start(Prompt15RumbleTest.sched(6, 30, 8, 500 + i), px + 0.5, pz + 0.5, shifted2(Prompt15RumbleTest.grid(6), px, pz), List.copyOf(viewers), 0);
            }
            long t0 = System.nanoTime();
            long peak = 0;
            for (int k = 1; k <= 120; k++) { c.tick(k); peak = Math.max(peak, c.peakUpdates); }
            double ms = (System.nanoTime() - t0) / 1e6 / 120;
            out.append(String.format("  %3d members: %.3f ms per tick, at most %d cell updates in a tick (budget 600), %d updates and %d bytes in all%n", n, ms, c.peakUpdates, c.totalUpdates, c.totalBytes));
            assertTrue(c.peakUpdates <= 600, "the budget holds: " + c.peakUpdates);
            c.stopAll("end of the test");
            assertEquals(0, h.shownAll(), "nothing is left: " + n);
        }
        System.out.print(out);
    }

    static List<RumbleCore.Col> shifted2(List<RumbleCore.Col> l, int dx, int dz) {
        List<RumbleCore.Col> o = new ArrayList<>();
        for (RumbleCore.Col c : l) o.add(new RumbleCore.Col(c.x() + dx, c.z() + dz, c.surfaceY()));
        return o;
    }

    @Test
    void theSmokeCaseAWorldPlacedHazardTouchesAPlayerAtTheEntryCrossing() {
        for (double yaw : new double[]{0, 180}) {
            WormSpec s = Alignment.once(StrikeType.CRASH, SizeTable.Size.SMALL);
            Moves.Move m = MoveFactory.get(s);
            double ez = Alignment.entryZ(m);
            double[] o = Alignment.origin(0.5, 0.5, ez, yaw);
            WormHazard hz = new WormHazard(s, o[0], -58, o[1], (float) yaw, WormHazard.Settings.DEFAULTS);
            Box player = Box.of(0.5, -58, 0.5, 0.6, 1.8);
            double first = -1;
            String part = null;
            for (int tick = 1; tick < 200 && first < 0; tick++) {
                double tn = MoveFactory.tau(s, tick), tp = MoveFactory.tau(s, tick - 1);
                WormHazard.Contact c = hz.firstContact(player, tp, tn, Set.of("throat"));
                if (c != null) { first = tick / 20.0; part = c.part(); }
            }
            System.out.println("world-placed yaw " + yaw + ": throat touch at " + first + " s " + part + " origin " + o[0] + "," + o[1]);
            assertTrue(first > 0, "the throat touches a standing player at the entry crossing, yaw " + yaw);
        }
    }
}
