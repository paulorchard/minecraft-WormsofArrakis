package com.paulorchard.islandcraft.wormsofarrakis.test;

import com.paulorchard.islandcraft.wormsofarrakis.fx.SizeFactor;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Prompt 12 part 2: the four size presets (small 8, medium 16, large 32, huge 48) of the five events, pure code. A preset is a width plus the shortest worm that the move accepts
 * without force, everything else at its default (the defaults of the test command). The shortest length is computed from the move's own checks (it is searched, never typed in a
 * table); a crest has no length check and takes 5 W (the length the owner approved in the previs).
 */
public final class Presets {
    private Presets() {}

    public enum Size {
        SMALL("small", SizeFactor.SMALL), MEDIUM("medium", SizeFactor.MEDIUM), LARGE("large", SizeFactor.LARGE), HUGE("huge", SizeFactor.HUGE);
        public final String label;
        public final double width;
        Size(String label, double width) { this.label = label; this.width = width; }
        public static Size of(String label) { for (Size s : values()) if (s.label.equals(label)) return s; return null; }
    }

    /** The five events of a preset, in the order of the command's completion. */
    public static final List<String> MOVES = List.of("crash", "ballistic", "crest", "emerge", "engulf");
    /** The most segments the test command accepts. */
    public static final int MAX_SEGMENTS = 1000;
    /** A crest is five body widths long. */
    public static final double CREST_LENGTH_WIDTHS = 5;

    /** One preset: the kind the test command calls the move (crasharc for the ballistic crash), the spec and the numbers. */
    public record Preset(Size size, String move, String testKind, double width, int segments, WormSpec spec) {
        /** The line shown in chat and written to the log. */
        public String describe() {
            return String.format(Locale.ROOT, "preset %s %s = %s W%s segments=%d (sizeFactor %.2f) = /arrakis_worm_test %s %s %d", size.label, move, testKind.equals("crasharc") ? "ballistic crash" : testKind, trim(width), segments,
                    SizeFactor.of(width), testKind, trim(width), segments);
        }
    }

    private static String trim(double v) { return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v); }

    /** The kind of the test command for a preset move. */
    public static String testKind(String move) { return move.equals("ballistic") ? "crasharc" : move; }

    /** The spec of a move with every default, at a width and a segment count (not yet checked by the move). */
    public static WormSpec specFor(String move, double width, int segments) {
        if (move.equals("ballistic")) return TestCommand.ballisticSpec(width, segments, 10, 70, 12, 12, 4, true, true, false);
        return MoveOptions.parse(move, width, segments, "", false).spec();
    }

    /** Builds the move of a spec; null when it is accepted, else the message of the refusal. */
    public static String refusal(String move, double width, int segments) {
        try {
            MoveFactory.get(specFor(move, width, segments));
            return null;
        } catch (Moves.Rejected r) {
            return r.getMessage();
        } catch (RuntimeException e) {
            return String.valueOf(e.getMessage());
        }
    }

    private static final Map<String, Integer> CACHE = new LinkedHashMap<>();

    /** The shortest segment count the move accepts without force at this width and the defaults; -1 when none up to {@link #MAX_SEGMENTS} works. */
    public static synchronized int minSegments(String move, double width) {
        return CACHE.computeIfAbsent(move + "/" + width, k -> search(move, width));
    }

    private static int search(String move, double width) {
        if (move.equals("crest")) {
            int s = (int) Math.ceil(CREST_LENGTH_WIDTHS * width - 1e-9);
            for (; s <= MAX_SEGMENTS; s++) if (refusal(move, width, s) == null) return s;
            return -1;
        }
        int hi = 2;
        while (refusal(move, width, hi) != null) {
            if (hi >= MAX_SEGMENTS) return -1;
            hi = Math.min(hi * 2, MAX_SEGMENTS);
        }
        int lo = Math.max(2, hi / 2 + (hi > 2 ? 1 : 0));
        while (lo < hi) {   // the smallest accepted count: a shorter worm is refused (monotone; the unit test checks segments - 1)
            int mid = (lo + hi) >>> 1;
            if (refusal(move, width, mid) == null) hi = mid; else lo = mid + 1;
        }
        return lo;
    }

    /** The preset of a size and move; refused (never forced) with the move's own message when no segment count works. */
    public static Preset of(Size size, String move) {
        int segs = minSegments(move, size.width);
        if (segs < 0) {
            String why = refusal(move, size.width, MAX_SEGMENTS);
            throw new Moves.Rejected(why == null ? "no worm length works" : why + " (no segment count up to " + MAX_SEGMENTS + " is accepted at W" + trim(size.width) + ")");
        }
        return new Preset(size, move, testKind(move), size.width, segs, specFor(move, size.width, segs));
    }

    /** The text of the presets for the moves command. */
    public static List<String> lines() {
        List<String> l = new ArrayList<>();
        l.add("presets: /arrakis-worm <small|medium|large|huge> <crash|ballistic|crest|emerge|engulf> (no other values; op level; small W8, medium W16, large W32, huge W48; the shortest worm the move accepts, every other key at its default; the fine control stays in /arrakis_worm_test)");
        for (String mv : MOVES) {
            StringBuilder b = new StringBuilder("preset ").append(mv).append(" segments:");
            for (Size s : Size.values()) b.append(' ').append(s.label).append('=').append(minSegments(mv, s.width));
            l.add(b.toString());
        }
        return l;
    }
}
