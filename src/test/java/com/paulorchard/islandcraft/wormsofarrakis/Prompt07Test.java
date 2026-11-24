package com.paulorchard.islandcraft.wormsofarrakis;

import com.paulorchard.islandcraft.wormsofarrakis.geometry.BendLimit;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ShingleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.WormMath;
import com.paulorchard.islandcraft.wormsofarrakis.path.HeadSpec;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;
import com.paulorchard.islandcraft.wormsofarrakis.path.SurfaceProvider;
import com.paulorchard.islandcraft.wormsofarrakis.path.WormPose;
import com.paulorchard.islandcraft.wormsofarrakis.path.WormSkin;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveFactory;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveOptions;
import com.paulorchard.islandcraft.wormsofarrakis.test.WormSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Prompt 07: the same variables on every move (Part 1) and engulf (Part 2). */
class Prompt07Test {
    private static final double[] WIDTHS = {3, 16, 48};

    private static MoveOptions.Parsed parse(String move, double w, int segs, String opts) { return MoveOptions.parse(move, w, segs, opts, false); }

    private static Moves.Move build(String move, double w, int segs, String opts) { return MoveFactory.build(parse(move, w, segs, opts).spec()); }

    // ---------------------------------------------------------------- Part 1

    private static final String[][] ALL_KEYS = {
            {"crest", "height=1.3 radius=5 radius_down=3 speed=8 pause=1 loop=off mouth=open"},
            {"emerge", "n=7 angle=75 hold=3 speed=9 retreat_speed=5 pause=2 loop=off mouth=closed"},
            {"crash", "height=3 lean=10 hold=1 speed=15 gravity=9 min_radius=2.5 pause=3 loop=off mouth=closed"},
            {"engulf", "exposed=3 lean=5 hold=0.7 speed=20 gravity=15 pause=0.5 loop=off mouth=closed"}};

    @Test void eachMoveParsesItsKeysInAnyOrder() {
        for (String[] mk : ALL_KEYS) {
            List<String> toks = new ArrayList<>(Arrays.asList(mk[1].split(" ")));
            String ref = parse(mk[0], 16, 400, mk[1]).spec().serialize();
            for (int rot = 0; rot < toks.size(); rot++) {
                List<String> l = new ArrayList<>(toks);
                Collections.rotate(l, rot);
                assertEquals(ref, parse(mk[0], 16, 400, String.join(" ", l)).spec().serialize(), mk[0] + " rotated by " + rot);
                Collections.reverse(l);
                assertEquals(ref, parse(mk[0], 16, 400, String.join(" ", l)).spec().serialize(), mk[0] + " reversed after " + rot);
            }
            assertDoesNotThrow(() -> MoveFactory.build(parse(mk[0], 16, 400, mk[1]).spec()), mk[0] + " builds with all keys set");
            WormSpec s = WormSpec.parse(ref);
            assertEquals(0, s.p(19, 1), "loop=off is in the spec");
            assertTrue(s.p(18, -1) >= 0, "pause is in the spec");
        }
    }

    private static WormSpec anim(WormSpec s) { return MoveFactory.withMouth(s, MoveFactory.MOUTH_ANIM, 0.9, -999, 45, true); }

    @Test void theOldPositionalFormsGiveTheSameSpecsAsBefore() {
        // crest 16 60 0.5 12 (height, speed) and the defaults
        assertEquals(anim(MoveFactory.spec("crest", 16, 60, 0.5, 6, 12, 0, 0, 0, false, true, false, 1.0, 1.0, 1)).serialize(), parse("crest", 16, 60, "0.5 12").spec().serialize());
        assertEquals(anim(MoveFactory.spec("crest", 16, 60, 0.7, 6, 12, 0, 0, 0, false, true, false, 1.0, 1.0, 1)).serialize(), parse("crest", 16, 60, "").spec().serialize());
        assertEquals(parse("crest", 16, 60, "0.5 12").spec().serialize(), parse("crest", 16, 60, "speed=12 height=0.5").spec().serialize());
        assertEquals(parse("crest", 16, 60, "height=0.5").spec().serialize(), parse("crest", 16, 60, "crest_fraction=0.5").spec().serialize());
        assertEquals(anim(MoveFactory.spec("crest", 16, 80, 1.3, 6, 6, 0, 3, 0, false, true, false, 1.0, 1.0, 1)).serialize(), parse("crest", 16, 80, "height=1.3 radius=6 radius_down=3 speed=6").spec().serialize());
        // emerge 16 60 10 60 (n, angle) and the longer form
        String old = anim(MoveFactory.spec("emerge", 16, 60, 10, 60, 12, 2, 0, 0, true, true, false, 1.0, 1.0, 1)).serialize();
        String oldStatic = MoveFactory.spec("emerge", 16, 60, 10, 60, 12, 2, 0, 0, true, true, false, 1.0, 1.0, 1).serialize();   // prompt 08: the default mouth is now the animation (p8 = 2); open stays the static one
        assertEquals(old, parse("emerge", 16, 60, "10 60").spec().serialize());
        assertEquals(oldStatic, parse("emerge", 16, 60, "10 60 12 2 open").spec().serialize());
        assertEquals(old, parse("emerge", 16, 60, "n=10 angle=60").spec().serialize());
        assertEquals(anim(MoveFactory.spec("emerge", 16, 60, 10, 90, 12, 2, 0, 0, true, true, false, 1.0, 1.0, 1)).serialize(), parse("emerge", 16, 60, "").spec().serialize());
        assertEquals(MoveFactory.spec("emerge", 16, 60, 6, 80, 20, 3, 0, 0, false, true, true, 1.0, 1.0, 1).serialize(), parse("emerge", 16, 60, "6 80 20 3 closed force").spec().serialize());
        // crash: the prompt 06 syntax
        String cStatic = MoveFactory.spec("fall", 16, 120, 2.5, 7, 12, 1.5, 12, 0, true, true, false, 1.0, 2.0, 1).serialize();
        String c = anim(MoveFactory.spec("fall", 16, 120, 2.5, 7, 12, 1.5, 12, 0, true, true, false, 1.0, 2.0, 1)).serialize();
        assertEquals(c, parse("crash", 16, 120, "").spec().serialize());
        assertEquals(c, parse("crash", 16, 120, "2.5").spec().serialize());
        assertEquals(cStatic, parse("crash", 16, 120, "height=2.5 lean=7 hold=1.5 speed=12 gravity=12 min_radius=2 mouth=open").spec().serialize());
        // and the moves they build are the old moves
        assertEquals(Moves.emerge(16, 60, 10, 60, 12, 2, 1.0, false).cycle, build("emerge", 16, 60, "10 60").cycle, 1e-9);
        assertEquals(Moves.crest(16, 60, 0.5, 6, 6, 12, false).cycle, build("crest", 16, 60, "0.5 12").cycle, 1e-9);
        assertEquals(Moves.fall(16, 120, 2.5, 7, 1.5, 12, 12, 2.0, 1.0, false).cycle, build("crash", 16, 120, "").cycle, 1e-9);
    }

    @Test void defaultsKeepTheApprovedBehaviour() {
        Moves.Move e = build("emerge", 16, 60, "");
        Moves.Move e0 = Moves.emerge(16, 60, 10, 90, 12, 2, 1.0, false);
        assertEquals(e0.cycle, e.cycle, 1e-9);
        assertEquals(1.0, e.pause, 0);
        for (int i = 0; i <= 200; i++) assertEquals(e0.at(e0.cycle * i / 200)[0], e.at(e0.cycle * i / 200)[0], 1e-9, "emerge path position");
        assertEquals(0.0, build("crest", 16, 80, "height=1.3").pause, 0, "crest default pause 0");
        assertEquals(2.0, build("crash", 16, 120, "").pause, 0, "crash default pause 2");
        assertEquals(1.5, build("engulf", 16, 60, "").pause, 0, "engulf default pause 1.5");
    }

    @Test void emergeHeightAndNAgree() {
        for (double w : WIDTHS) {
            double hl = HeadSpec.headLength(w);
            for (double h : new double[]{0.8, 1.0, 1.5}) {
                if (Moves.emergeRingsForHeight(w, h, 90) < 0.5) {   // not even one ring fits under this head tip height
                    assertThrows(MoveOptions.Bad.class, () -> parse("emerge", w, 400, "height=" + h));
                    continue;
                }
                int n = (int) Math.max(1, Math.round(h * w - hl));
                MoveOptions.Parsed byHeight = parse("emerge", w, 400, "height=" + h);
                assertEquals(n, (int) byHeight.spec().p(2, -1), "n from height " + h + " at W" + w);
                assertEquals(parse("emerge", w, 400, "n=" + n).spec().serialize(), byHeight.spec().serialize(), "height= and n= give the same spec");
                assertFalse(byHeight.notes().isEmpty(), "the solved n is told to the player");
                Moves.Move m = MoveFactory.build(byHeight.spec());
                assertEquals(h * w, m.tip(m.sHold)[1], 0.5 + 0.05, "the head tip at the hold is the height within the rounding of n");
            }
        }
        // height 1.0 at W16 and a slanted angle
        double nReal = Moves.emergeRingsForHeight(16, 1.0, 60);
        assertEquals((int) Math.round(nReal), (int) parse("emerge", 16, 400, "height=1 angle=60").spec().p(2, -1));
        MoveOptions.Bad both = assertThrows(MoveOptions.Bad.class, () -> parse("emerge", 16, 60, "n=3 height=1"));
        assertTrue(both.getMessage().contains("not both"), both.getMessage());
        assertThrows(MoveOptions.Bad.class, () -> parse("emerge", 16, 60, "3 height=1"));
        assertThrows(MoveOptions.Bad.class, () -> parse("emerge", 16, 60, "height=0.1"), "height too low for one ring");
    }

    @Test void loopOffEndsTheMoveOnce() {
        for (String[] mk : ALL_KEYS) {
            WormSpec off = parse(mk[0], 16, 400, mk[1]).spec();
            WormSpec on = parse(mk[0], 16, 400, mk[1].replace("loop=off", "loop=on")).spec();
            Moves.Move m = MoveFactory.get(off);
            double play = m.playTime();
            assertFalse(MoveFactory.finished(off, 0), mk[0] + " not finished at the start");
            assertFalse(MoveFactory.finished(off, (play - 0.5) * 20), mk[0] + " not finished before the end");
            assertTrue(MoveFactory.finished(off, (play + MoveFactory.REMOVE_MARGIN) * 20 + 2), mk[0] + " finished after the end");
            assertFalse(MoveFactory.finished(on, (play + 100) * 20), mk[0] + " loop on never finishes");
            // after the end the worm stays under the sand and does not start again
            for (double age : new double[]{play * 20 + 5, (m.cycle + 1) * 20, (3 * m.cycle + 1) * 20}) {
                double tau = MoveFactory.tau(off, age);
                assertTrue(m.tip(m.at(tau)[0])[1] < 0, mk[0] + " stays under the sand after its run (age " + age + ")");
            }
            // with the loop on it comes round again
            double a = MoveFactory.tau(on, 7), b = MoveFactory.tau(on, 7 + m.cycle * 20);
            assertEquals(m.at(a)[0], m.at(b)[0], 1e-6, mk[0] + " loops");
        }
    }

    @Test void pauseChangesTheLoopPeriodByExactlyTheDifference() {
        for (String move : MoveOptions.MOVES) {
            double c0 = build(move, 16, 400, "pause=0").cycle, c25 = build(move, 16, 400, "pause=2.5").cycle, c7 = build(move, 16, 400, "pause=7").cycle;
            assertEquals(2.5, c25 - c0, 1e-9, move);
            assertEquals(4.5, c7 - c25, 1e-9, move);
            Moves.Move d = build(move, 16, 400, "");
            assertEquals(d.cycle - d.pause + 7, c7, 1e-9, move + " default pause replaced by 7");
        }
    }

    private static double riseSeconds(Moves.Move m) { return m.phases().get(0).duration(); }

    @Test void speedChangesTheRiseDurationInversely() {
        for (String move : MoveOptions.MOVES) {
            double r6 = riseSeconds(build(move, 16, 400, "speed=6")), r12 = riseSeconds(build(move, 16, 400, "speed=12")), r24 = riseSeconds(build(move, 16, 400, "speed=24"));
            assertEquals(2.0, r6 / r12, 1e-9, move + " 6 vs 12");
            assertEquals(2.0, r12 / r24, 1e-9, move + " 12 vs 24");
            Moves.Move m = build(move, 16, 400, "speed=24");
            assertEquals(24, m.speed, 0);
        }
        // emerge: the way back has its own speed, default the same
        Moves.Move e = build("emerge", 16, 60, "speed=20 retreat_speed=6");
        assertEquals(e.advanceTime * 20 / 6, e.retreatTime, 1e-9);
        assertEquals(build("emerge", 16, 60, "speed=20").advanceTime, build("emerge", 16, 60, "speed=20").retreatTime, 1e-9);
        // crash: speed is the rise only, the fall is gravity
        assertEquals(build("crash", 16, 120, "speed=6").fallTime, build("crash", 16, 120, "speed=24").fallTime, 1e-12);
    }

    @Test void theMovesTextContainsEveryKeyOfEveryMove() {
        List<String> lines = MoveOptions.lines();
        assertEquals(4, lines.size());
        for (int i = 0; i < lines.size(); i++) {
            String move = MoveOptions.MOVES.get(i);
            assertTrue(lines.get(i).startsWith(move + ":"), lines.get(i));
            for (MoveOptions.Key k : MoveOptions.keys(move)) {
                assertTrue(lines.get(i).contains(k.name() + "=" + k.dfltText()), move + " line has " + k.name() + "=" + k.dfltText() + ": " + lines.get(i));
                assertTrue(MoveOptions.usage(move).contains("[" + k.name() + "="), "usage has " + k.name());
            }
            assertTrue(lines.get(i).contains("force"));
            for (String common : new String[]{"speed", "pause", "loop", "mouth"}) assertTrue(lines.get(i).contains(common + "="), move + " has " + common);
        }
        assertTrue(lines.get(0).contains("radius_down=") && lines.get(0).contains("height=0.7"));
        assertTrue(lines.get(1).contains("retreat_speed=") && lines.get(1).contains("n=10") && lines.get(1).contains("height="));
        assertTrue(lines.get(2).contains("min_radius=") && lines.get(2).contains("lean=7") && lines.get(2).contains("gravity=12"));
        assertTrue(lines.get(3).contains("exposed=2") && lines.get(3).contains("hold=0.5"));
        for (String l : lines) System.out.println("[moves] " + l);
    }

    @Test void refusalsAndClampsAreMessages() {
        assertTrue(assertThrows(MoveOptions.Bad.class, () -> parse("engulf", 16, 60, "foo=1")).getMessage().contains("unknown key 'foo'"));
        assertTrue(assertThrows(MoveOptions.Bad.class, () -> parse("engulf", 16, 60, "2 3")).getMessage().contains("unknown word"));
        assertTrue(assertThrows(MoveOptions.Bad.class, () -> parse("crash", 16, 60, "speed=fast")).getMessage().contains("not a number"));
        assertTrue(assertThrows(MoveOptions.Bad.class, () -> parse("crash", 16, 60, "mouth=wide")).getMessage().contains("open or closed"));
        assertTrue(assertThrows(MoveOptions.Bad.class, () -> parse("crash", 16, 60, "loop=maybe")).getMessage().contains("on or off"));
        assertTrue(assertThrows(MoveOptions.Bad.class, () -> parse("crash", 16, 60, "speed=5 speed=6")).getMessage().contains("twice"));
        assertTrue(assertThrows(MoveOptions.Bad.class, () -> parse("engulf", 16, 60, "exposed=2.5")).getMessage().contains("whole number"));
        assertTrue(assertThrows(MoveOptions.Bad.class, () -> parse("crest", 16, 60, "radius=0")).getMessage().contains("radius"));
        // clamped into the range, with a note
        MoveOptions.Parsed hi = parse("emerge", 16, 60, "speed=100 retreat_speed=0.1 pause=-3 hold=500");
        assertEquals(4, hi.notes().size(), hi.notes().toString());
        assertEquals(64, hi.values().get("speed"), 0);
        assertEquals(0, hi.values().get("pause"), 0);
        assertEquals(120, hi.values().get("hold"), 0);
        assertEquals(0.5, WormSpec.parse(hi.spec().serialize()).p(20, 0), 0);
        assertEquals(80, parse("engulf", 16, 60, "gravity=500").values().get("gravity"), 0);
        assertEquals(0.5, parse("crash", 16, 120, "speed=0.01").values().get("speed"), 0);
        // the limits that say force live in the moves and reach the player as refusals
        assertTrue(assertThrows(Moves.Rejected.class, () -> build("engulf", 16, 60, "exposed=4")).getMessage().contains("force"));
        assertDoesNotThrow(() -> MoveFactory.build(MoveOptions.parse("engulf", 16, 60, "exposed=4 force", false).spec()));
        assertDoesNotThrow(() -> build("engulf", 16, 60, "exposed=4 force"));
        assertThrows(Moves.Rejected.class, () -> build("engulf", 16, 60, "exposed=7 force"));
        assertThrows(Moves.Rejected.class, () -> build("engulf", 16, 60, "exposed=0"));
        assertThrows(Moves.Rejected.class, () -> build("engulf", 16, 60, "lean=60"));
        assertThrows(Moves.Rejected.class, () -> build("crest", 16, 60, "height=4"));
        assertThrows(Moves.Rejected.class, () -> build("crash", 16, 120, "height=1.5"));
        assertFalse(build("crest", 16, 80, "radius=1 height=0.4").note.isEmpty(), "crest radius below the limit is clamped with a note");
    }

    // ---------------------------------------------------------------- Part 2: engulf

    private static Moves.Move eng(double w, int exposed) { return Moves.engulf(w, 200, exposed, 7, 0.5, 12, 12, 1.0, false); }

    @Test void exposedRingsAndTheTipHeightAtTheHold() {
        for (double w : WIDTHS) for (int ex = 1; ex <= 3; ex++) {
            Moves.Move m = eng(w, ex);
            assertEquals(ex, m.pose(m.sHold).exposedBody(0), "exposed rings at the hold, W" + w);
            double expect = (ex + HeadSpec.headLength(w)) * Math.sin(Math.toRadians(83));
            assertEquals(expect, m.tip(m.sHold)[1], 0.05, "tip height at the hold, W" + w + " exposed " + ex);
            assertEquals(m.tip(m.sHold)[1], m.h0, 1e-9);
            assertEquals(ex + 2, m.segmentsNeeded);
            System.out.printf("[engulf] W%.0f exposed %d: tip height at the hold %.2f blocks (formula %.2f), rise %.2f s, drop %.2f s, cycle %.2f s, segmentsNeeded %d%n",
                    w, ex, m.h0, expect, m.riseTime, m.fallTime, m.cycle, m.segmentsNeeded);
        }
    }

    @Test void theDropFollowsTheFreeFallLaw() {
        for (double w : WIDTHS) for (int ex = 1; ex <= 3; ex++) {
            Moves.Move m = eng(w, ex);
            double t0 = m.advanceTime + m.hold;
            assertEquals(Math.sqrt(2 * m.dropEnd / m.gravity), m.fallTime, 1e-12);
            for (int i = 1; i <= 5; i++) {
                double tf = m.fallTime * i / 5.0 * 0.999;
                assertEquals(m.h0 - 0.5 * m.gravity * tf * tf, m.tip(m.at(t0 + tf)[0])[1], 1e-6, "h(t) = h0 - g t^2/2 at " + tf);
            }
            assertEquals(m.h0, m.tip(m.at(t0 + 1e-9)[0])[1], 1e-6, "the drop starts from rest at the hold height");
            double dh = m.h0 - m.tip(m.at(t0 + 1e-3)[0])[1];
            assertEquals(0.5 * 12 * 1e-6, dh, 1e-6, "from rest: no initial jump");
        }
    }

    @Test void theHeadFallsAndTheExposedCountNeverIncreases() {
        for (double w : WIDTHS) for (int ex = 1; ex <= 3; ex++) {
            Moves.Move m = eng(w, ex);
            double t0 = m.advanceTime + m.hold;
            double prevTip = Double.POSITIVE_INFINITY;
            int prevExposed = Integer.MAX_VALUE;
            for (int i = 0; i <= 300; i++) {
                double s = m.at(t0 + m.fallTime * i / 300.0 * 0.9999)[0];
                double tip = m.tip(s)[1];
                int e = m.pose(s).exposedBody(0);
                assertTrue(tip < prevTip + 1e-12, "head height strictly decreases");
                if (i > 0) assertTrue(tip < prevTip, "strictly");
                assertTrue(e <= prevExposed, "exposed rings never increase");
                prevTip = tip;
                prevExposed = e;
            }
        }
    }

    @Test void theEndHasNothingAboveTheSandAndTheTunnelIsStraightEnough() {
        for (double w : WIDTHS) for (int ex = 1; ex <= 3; ex++) {
            Moves.Move m = eng(w, ex);
            double t0 = m.advanceTime + m.hold;
            double s = m.at(t0 + m.fallTime + 0.2)[0];
            WormPose p = m.pose(s);
            assertEquals(0, p.exposedBody(0));
            assertEquals(-(HeadSpec.headLength(w) + 1), m.tip(s)[1], 1e-6, "the head tip rests HEAD_LENGTH + 1 under the sand");
            for (int k = 0; k <= Math.min(p.segments, 150); k++) assertTrue(p.centreY(k) < 0, "ring " + k + " is under the sand at the end (W" + w + ")");
            double a = Math.toRadians(83);
            for (double back : new double[]{0, 1, 2, 3}) assertEquals(a, m.path.theta(s - back), 1e-9, "the tunnel is still straight " + back + " blocks behind the head at the end");
            assertTrue(m.path.theta(m.sHold) > 0 && Math.abs(m.path.theta(m.sHold) - a) < 1e-9);
            // the head tip is above the sand at the hold and below it at the end
            assertTrue(m.tip(m.sHold)[1] > 0);
        }
    }

    @Test void theSamePathUpAndDown() {
        for (double w : WIDTHS) for (int ex = 1; ex <= 3; ex++) {
            Moves.Move m = eng(w, ex);
            double t0 = m.advanceTime + m.hold;
            double sEnd = m.sHold - m.dropEnd / m.dropAngleSin;
            double sin = Math.sin(Math.toRadians(83));
            for (int i = 0; i <= 40; i++) {
                double s = sEnd + (m.sHold - sEnd) * i / 40.0;
                double tUp = (s - m.s0) / m.speed;
                double tf = Math.sqrt(2 * (m.sHold - s) * sin / m.gravity);
                double sUp = m.at(tUp)[0], sDown = m.at(t0 + tf)[0];
                assertEquals(s, sUp, 1e-9);
                assertEquals(s, sDown, 1e-9);
                WormPose up = m.pose(sUp), down = m.pose(sDown);
                for (int k = 0; k <= Math.min(up.segments, 60); k++) {
                    assertEquals(up.joints[k][1], down.joints[k][1], 1e-3, "y of ring " + k);
                    assertEquals(up.joints[k][2], down.joints[k][2], 1e-3, "z of ring " + k);
                }
            }
        }
    }

    @Test void theBendLimitHoldsForEveryVisibleJoint() {
        double worst = 0;
        for (double w : WIDTHS) for (int ex = 1; ex <= 3; ex++) {
            Moves.Move m = eng(w, ex);
            double lim = BendLimit.maxJointAngle(w, 1.0);
            for (int i = 0; i <= 400; i++) {
                WormPose p = m.pose(m.at(m.playTime() * i / 400.0)[0]);
                for (int k = 1; k < p.segments; k++) {
                    if (p.centreY(k) <= 0 || p.centreY(k - 1) <= 0) continue;
                    double a = Math.abs(p.pitchAngle[k]);
                    worst = Math.max(worst, a / lim);
                    assertTrue(a <= lim * (1 + 5e-3), "W" + w + " exposed " + ex + " step " + i + " joint " + k + " angle " + Math.toDegrees(a));
                }
            }
        }
        System.out.printf("[engulf] largest visible joint angle during the whole move: %.4f of the limit (a straight tunnel: only the joint at the sand crossing can bend)%n", worst);
    }

    @Test void noVisibleRingIsDroppedByTheUndergroundRule() {
        for (double w : new double[]{3, 16}) for (int ex = 1; ex <= 3; ex++) {
            Moves.Move m = eng(w, ex);
            int n = WormMath.faceCount(w);
            double t0 = m.advanceTime;
            for (int i = 0; i <= 12; i++) {
                double tt = i <= 2 ? t0 + 0.25 * i * m.hold : t0 + m.hold + m.fallTime * (i - 2) / 10.0;
                WormPose p = m.pose(m.at(tt)[0]);
                boolean[] drawn = p.drawn(m.r0, SurfaceProvider.PLANE);
                for (int k = 0; k < Math.min(p.segments, 40); k++) {
                    var r = ShingleMath.build(p.chain(m.r0), n, m.r0, 1, ShingleMath.Options.defaults(0.132, true), k, k);
                    double top = -1e9;
                    for (int q = 0; q < r.quads; q++) for (int c = 0; c < 4; c++) top = Math.max(top, r.v[q * 32 + c * 8 + 1]);
                    if (!drawn[k]) assertTrue(top <= -0.9, "w" + w + " ex" + ex + " step " + i + " segment " + k + " skipped but reaches y=" + top);
                    if (top > 0) assertTrue(drawn[k], "w" + w + " ex" + ex + " step " + i + " segment " + k + " is above the sand but not drawn");
                }
            }
        }
    }

    @Test void thePhasesComeInOrderWithTheRightDurations() {
        for (double w : WIDTHS) for (int ex = 1; ex <= 3; ex++) {
            Moves.Move m = eng(w, ex);
            List<Moves.Move.Phase> ph = m.phases();
            assertEquals(List.of("rise", "hold", "drop", "pause"), ph.stream().map(Moves.Move.Phase::name).toList());
            assertEquals((m.sHold - m.s0) / m.speed, ph.get(0).duration(), 1e-9, "rise = distance / speed");
            assertEquals(0.5, ph.get(1).duration(), 1e-12);
            assertEquals(Math.sqrt(2 * m.dropEnd / 12.0), ph.get(2).duration(), 1e-12);
            assertEquals(1.5, ph.get(3).duration(), 1e-12);
            assertEquals(m.cycle, ph.stream().mapToDouble(Moves.Move.Phase::duration).sum(), 1e-9);
            double start = 0;
            for (Moves.Move.Phase p : ph) {
                Moves.Move.PhaseAt at = m.phaseAt(start + 0.5 * p.duration());
                assertEquals(p.name(), at.name());
                assertEquals(0.5 * p.duration(), at.t(), 1e-9);
                assertEquals(p.duration(), at.duration(), 1e-12);
                start += p.duration();
            }
            assertEquals("rise", m.phaseAt(0).name());
            assertEquals("rise", m.phaseAt(m.cycle * 7 + 0.1).name(), "loops");
        }
    }

    @Test void everyMoveReportsItsPhasesInOrder() {
        String[][] expect = {{"crest", "rise,fall"}, {"emerge", "rise,hold,retreat,pause"}, {"crash", "rise,hold,fall,pause"}, {"engulf", "rise,hold,drop,pause"}};
        for (String[] e : expect) {
            Moves.Move m = build(e[0], 16, 400, e[0].equals("crest") ? "height=1.3" : "");
            double sum = m.phases().stream().mapToDouble(Moves.Move.Phase::duration).sum();
            assertEquals(m.cycle, sum, 1e-9, e[0] + " phases add up to the loop");
            List<String> seen = new ArrayList<>();
            for (double tau = 0; tau < m.cycle; tau += 0.02) {
                String name = m.phaseAt(tau).name();
                if (seen.isEmpty() || !seen.get(seen.size() - 1).equals(name)) seen.add(name);
            }
            assertEquals(e[1], String.join(",", seen), e[0] + " phase order");
        }
        Moves.Move c = build("crest", 16, 80, "height=1.3 pause=2");
        assertEquals("pause", c.phaseAt(c.cycle - 0.5).name(), "a crest with a pause ends with it");
        assertEquals((c.sApex - c.s0) / c.speed, c.phases().get(0).duration(), 1e-9, "crest rise = distance to the apex / speed");
        assertEquals("rise", build("crest", 16, 80, "height=1.3").phaseAt(1).name());
    }

    @Test void theMinimumSegmentCountIsRefusedWithTheNumberNeeded() {
        for (double w : WIDTHS) for (int ex = 1; ex <= 3; ex++) {
            final int e = ex;
            Moves.Rejected r = assertThrows(Moves.Rejected.class, () -> Moves.engulf(w, e + 1, e, 7, 0.5, 12, 12, 1.0, false));
            assertTrue(r.getMessage().contains("at least exposed + 2 = " + (ex + 2) + " segments"), r.getMessage());
            Moves.Move ok = assertDoesNotThrow(() -> Moves.engulf(w, e + 2, e, 7, 0.5, 12, 12, 1.0, false));
            assertEquals(ex, ok.pose(ok.sHold).exposedBody(0));
            assertDoesNotThrow(() -> Moves.engulf(w, e + 1, e, 7, 0.5, 12, 12, 1.0, true));
        }
    }

    @Test void theFrameLineHasExposedTipHeightAndPhase() {
        Moves.Move m = eng(16, 2);
        String hold = m.frame(9).line(), mid = m.frame(0.5).line(), zero = m.frame(0).line();
        assertTrue(hold.contains("exposed=2 tipHeight=") && hold.contains("phase=hold"), hold);
        assertTrue(mid.contains("phase=drop"), mid);
        assertTrue(zero.contains("phase=hold") || zero.contains("phase=drop"), zero);
        assertEquals(m.h0, m.tip(m.at(m.tauAt(9))[0])[1], 1e-9, "freeze hold is the hold pose");
        assertEquals(m.tip(m.at(m.tauAt(0))[0])[1], m.h0, 1e-3, "freeze 0 is the hold pose");
        assertTrue(m.tip(m.at(m.tauAt(1))[0])[1] < 0, "freeze 1 is under the sand");
        for (double t : new double[]{0, 0.25, 0.5, 0.75, 1}) System.out.println("[engulf] W16 " + m.frame(t).line());
        System.out.println("[engulf] W16 hold " + hold);
    }

    @Test void buildTimeAndQuadsOfTheExposedPartDuringTheDrop() {
        for (double w : new double[]{16, 48}) {
            Moves.Move m = eng(w, 2);
            int n = WormMath.faceCount(w);
            var opt = WormSkin.defaultOptions(true);
            int prevQuads = Integer.MAX_VALUE;
            for (int i = 0; i <= 4; i++) {
                double s = m.at(m.advanceTime + m.hold + m.fallTime * i / 4.0 * 0.999)[0];
                WormPose p = m.pose(s);
                WormSkin.build(p, n, m.r0, SurfaceProvider.PLANE, opt, 0);
                long best = Long.MAX_VALUE;
                WormSkin sk = null;
                for (int r = 0; r < 4; r++) {
                    long a = System.nanoTime();
                    sk = WormSkin.build(p, n, m.r0, SurfaceProvider.PLANE, opt, 0);
                    best = Math.min(best, System.nanoTime() - a);
                }
                System.out.printf("[engulf] W%.0f drop t=%.2f: exposed rings %d, built segments %d..%d, quads %d, build %.2f ms%n", w, i / 4.0, p.exposedBody(0), sk.from, sk.to, sk.result.quads, best / 1e6);
                assertTrue(sk.result.quads <= prevQuads, "quads never increase during the drop");
                prevQuads = sk.result.quads;
            }
        }
    }
}
