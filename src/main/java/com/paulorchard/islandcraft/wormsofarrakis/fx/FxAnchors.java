package com.paulorchard.islandcraft.wormsofarrakis.fx;

import com.paulorchard.islandcraft.wormsofarrakis.fx.CrashFx.Anchor;
import com.paulorchard.islandcraft.wormsofarrakis.path.MouthAnim;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;

import java.util.Locale;

/**
 * Prompt 09 part 1: the anchors of a move, in seconds on the loop clock, computed from the move (nothing typed). Pure code.
 * <pre>
 * approach   first instant the head tip is within 3 R of the sand from below (tip height > -3 R, R = r0)
 * breach     first instant the head tip is above the base level (tip height > 0)
 * closeStart start of the mouth closing (MouthAnim): riseEnd - close_time, or later when the rise is shorter
 * riseEnd    end of the rise = start of the hold (crash, emerge, engulf); undefined (NaN) on crest
 * fallStart  end of the hold: the fall (crash), the retreat (emerge), the drop (engulf); undefined on crest
 * reentry    first instant after fallStart when the head tip is below the base level again (crest: after the highest point)
 * end        playTime: everything is under the sand (the pause starts)
 * </pre>
 * A row whose anchor is NaN plays nothing. holeZ is the model z of the head tip at the breach (the hole: every sound before the reentry is placed
 * there so it does not wander), reentryZ that of the tip at the reentry.
 */
public final class FxAnchors {
    private FxAnchors() {}

    public record Anchors(double approach, double breach, double closeStart, double riseEnd, double fallStart, double reentry, double end, double holeZ, double reentryZ, double apex, String kind) {
        public boolean crest() { return "crest".equals(kind); }

        public double at(Anchor a) {
            return switch (a) {
                case APPROACH -> approach;
                case BREACH -> breach;
                case CLOSE_START -> closeStart;
                case RISE_END -> riseEnd;
                case FALL_START -> fallStart;
                case REENTRY -> reentry;
                case END -> end;
                case APEX -> apex;
            };
        }

        public String line() {
            String s = String.format(Locale.ROOT, "approach=%.2f breach=%.2f closeStart=%.2f riseEnd=%.2f fallStart=%.2f reentry=%.2f end=%.2f (holeZ=%.2f reentryZ=%.2f)",
                    approach, breach, closeStart, riseEnd, fallStart, reentry, end, holeZ, reentryZ);
            return crest() ? s + String.format(Locale.ROOT, " apex=%.2f", apex) : s;
        }
    }

    public static final double SCAN_STEP = 0.005;

    /** Head tip height above the base level at loop time t. */
    public static double tipHeight(Moves.Move m, double t) { return m.tip(m.at(t)[0])[1]; }

    private static double tipZ(Moves.Move m, double t) { return m.tip(m.at(t)[0])[0]; }

    /** First time in [from, to] where the tip height crosses above {@code level} (> level), refined by interpolation; NaN if none. */
    private static double firstAbove(Moves.Move m, double level, double from, double to) {
        double prevT = from, prev = tipHeight(m, from);
        if (prev > level) return from;
        for (double t = from + SCAN_STEP; t <= to + 1e-9; t += SCAN_STEP) {
            double y = tipHeight(m, t);
            if (y > level) return prevT + SCAN_STEP * (level - prev) / (y - prev);
            prevT = t;
            prev = y;
        }
        return Double.NaN;
    }

    /** First time in [from, to] where the tip height is at or below 0 after having been above; NaN if none. */
    private static double firstBelow(Moves.Move m, double from, double to) {
        double prevT = from, prev = tipHeight(m, from);
        for (double t = from + SCAN_STEP; t <= to + 1e-9; t += SCAN_STEP) {
            double y = tipHeight(m, t);
            if (y <= 0 && prev > 0) return prevT + SCAN_STEP * prev / (prev - y);
            prevT = t;
            prev = y;
        }
        return Double.NaN;
    }

    /** Time of the highest head tip in [from, to]. */
    private static double highest(Moves.Move m, double from, double to) {
        double bt = from, by = -1e18;
        for (double t = from; t <= to + 1e-9; t += SCAN_STEP) {
            double y = tipHeight(m, t);
            if (y > by) {
                by = y;
                bt = t;
            }
        }
        return bt;
    }

    /** The crest breaches 4 s after the approach starts (the approach is clamped at the start of the move). */
    public static final double CREST_APPROACH_LEAD = 4.0;
    /** The crest effects stop this long after the last joint of the body has gone under the sand. */
    public static final double CREST_END_TAIL = 0.3;
    public static final double HEAD_SCAN_STEP = 0.01, END_SCAN_STEP = 0.05;

    /** First time in [0, to] where the highest point of the head is above the base level (the upper mandible comes out); NaN if never. */
    private static double headBreach(Moves.Move m, double to, double openDeg, double closeTime, double closeEnd) {
        double prevT = 0, prev = headTop(m, 0, openDeg, closeTime, closeEnd);
        if (prev > 0) return 0;
        for (double t = HEAD_SCAN_STEP; t <= to + 1e-9; t += HEAD_SCAN_STEP) {
            double y = headTop(m, t, openDeg, closeTime, closeEnd);
            if (y > 0) return prevT + HEAD_SCAN_STEP * (0 - prev) / (y - prev);
            prevT = t;
            prev = y;
        }
        return Double.NaN;
    }

    /** Height of the highest point of the head (upper mandible as the mouth animation has it at t, or the rim of the head ring). */
    public static double headTop(Moves.Move m, double t, double openDeg, double closeTime, double closeEnd) {
        return HeadTop.at(m, t, MouthAnim.angleAt(m, t, openDeg, closeTime, closeEnd)).top();
    }

    /** The last instant (scanned every {@link #END_SCAN_STEP}) at which the last joint of the body is above the base level, from {@code from}; NaN if never. */
    private static double lastJointUp(Moves.Move m, double from, double to) {
        double last = Double.NaN, prevT = from, prev = Double.NaN;
        for (double t = from; t <= to + 1e-9; t += END_SCAN_STEP) {
            double[][] j = m.pose(m.at(t)[0]).joints;
            double y = j[j.length - 1][1];
            if (y > 0) last = t;
            else if (prev > 0) last = prevT + END_SCAN_STEP * prev / (prev - y);
            prevT = t;
            prev = y;
        }
        return last;
    }

    /** closeEnd: {@link MouthAnim#CLOSE_END_DEFAULT_MARK} for the move's default. */
    public static Anchors of(Moves.Move m, double closeTime, double closeEnd) { return of(m, closeTime, closeEnd, com.paulorchard.islandcraft.wormsofarrakis.path.HeadSpec.OPEN_DEGREES); }

    /**
     * As above; {@code openDeg} is the mouth opening of the head the crest breach is measured with. A crest is not like the other moves (prompt 10): its breach is the instant
     * the highest point of the head, the open upper mandible, reaches the base level, its approach is 4 s before that (at least 0), the apex is the highest tip, the
     * reentry the first instant after it that the tip is below the base level, and its end the instant the last joint has gone under plus 0.3 s.
     */
    public static Anchors of(Moves.Move m, double closeTime, double closeEnd, double openDeg) {
        // a crest takes 0.05 s (W16) to 0.3 s (W48) to compute: the anchors of one move object (the game keeps them in a cache) are computed once per set of numbers
        String key = closeTime + "|" + closeEnd + "|" + openDeg;
        synchronized (MEMO) {
            Anchors hit = MEMO.computeIfAbsent(m, k -> new java.util.HashMap<>()).get(key);
            if (hit != null) return hit;
        }
        Anchors a = compute(m, closeTime, closeEnd, openDeg);
        synchronized (MEMO) {
            MEMO.computeIfAbsent(m, k -> new java.util.HashMap<>()).put(key, a);
        }
        return a;
    }

    private static final java.util.Map<Moves.Move, java.util.Map<String, Anchors>> MEMO = new java.util.WeakHashMap<>();

    private static Anchors compute(Moves.Move m, double closeTime, double closeEnd, double openDeg) {
        boolean crest = m.kind.equals("crest");
        double end = m.playTime();
        double approach = firstAbove(m, -3 * m.r0, 0, end);
        double breach = crest ? headBreach(m, end, openDeg, closeTime, closeEnd) : firstAbove(m, 0, 0, end);
        if (crest && !Double.isNaN(breach)) approach = Math.max(0, breach - CREST_APPROACH_LEAD);
        boolean hasHold = m.kind.equals("emerge") || m.kind.equals("fall") || m.kind.equals("engulf");
        double apex = crest ? m.apexTime() : highest(m, Double.isNaN(breach) ? 0 : breach, end);
        MouthAnim.Timing mt = MouthAnim.timing(m, closeTime, closeEnd);
        // crest (prompt 10): no hold; the end of the rise is the end of the mouth closing (apex + close_end) and the fall starts at the apex
        double riseEnd = hasHold ? m.advanceTime : crest ? mt.tEnd() : Double.NaN;
        double fallStart = hasHold ? m.advanceTime + m.hold : crest ? apex : Double.NaN;
        double reentryFrom = hasHold ? fallStart : crest ? apex : Double.isNaN(breach) ? 0 : breach;
        double reentry = firstBelow(m, reentryFrom, end);
        if (crest) {
            double up = lastJointUp(m, Double.isNaN(reentry) ? apex : reentry, end);
            if (!Double.isNaN(up)) end = Math.min(end, up + CREST_END_TAIL);
        }
        double closeStart = mt.tStart();
        double holeZ = Double.isNaN(breach) ? 0 : tipZ(m, breach);
        if (crest) {   // the entry hole: where the body meets the sand once the head is up (the crossing stays put while the body slides through)
            double ez = FxPlayer.FxGeometry.at(m, apex).entryZ;
            if (!Double.isNaN(ez)) holeZ = ez;
        }
        double reentryZ = Double.isNaN(reentry) ? holeZ : tipZ(m, reentry);
        return new Anchors(approach, breach, closeStart, riseEnd, fallStart, reentry, end, holeZ, reentryZ, apex, m.kind);
    }
}
