package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import com.paulorchard.islandcraft.wormsofarrakis.fx.SizeFactor;

/** Prompt 13 part 2: the depth of the sand D gives the size of the worm (the size presets of prompt 12). Engine-free. */
public final class SizeTable {
    private SizeTable() {}

    public enum Size {
        SMALL("small", SizeFactor.SMALL), MEDIUM("medium", SizeFactor.MEDIUM), LARGE("large", SizeFactor.LARGE), HUGE("huge", SizeFactor.HUGE);
        public final String label;
        public final double width;
        Size(String label, double width) { this.label = label; this.width = width; }
        public static Size of(String label) { for (Size s : values()) if (s.label.equals(label)) return s; return null; }
    }

    /** The size for a depth D, or null when the sand is too shallow (D at most DepthShallowMax: no event). */
    public static Size forDepth(int d, HuntConfig c) {
        if (d <= c.depthShallowMax) return null;
        if (d <= c.depthSmallMax) return Size.SMALL;
        if (d <= c.depthMediumMax) return Size.MEDIUM;
        if (d <= c.depthLargeMax) return Size.LARGE;
        return Size.HUGE;
    }

    /** The stalk length: the width of the worm plus StalkExtraSeconds (no shortening for more players). */
    public static final String DEPTH = "depth", PLAYERS = "players";

    /** The mode word of the config, normalised: players or (anything else) depth. */
    public static String mode(String word) { return word != null && PLAYERS.equals(word.trim().toLowerCase(java.util.Locale.ROOT)) ? PLAYERS : DEPTH; }

    /** Prompt 20: how many players the rule counts (one function so that vehicles can change it later). */
    public static int countPlayers(java.util.List<java.util.UUID> roster) { return roster.size(); }

    /** The size of the players rule: the list size and whether the desert is open (PlayersLargeAt, PlayersHugeAt, the two size rows of the config). */
    public static Size forPlayers(int players, boolean open, HuntConfig c) {
        int tier = players >= c.playersHugeAt ? 2 : players >= c.playersLargeAt ? 1 : 0;   // a count of 0 never occurs; it is treated as one player
        String w = (open ? c.playersSizeOpen : c.playersSizeClosed)[tier];
        Size s = Size.of(w == null ? "" : w.trim().toLowerCase(java.util.Locale.ROOT));
        if (s != null) return s;
        return Size.of((open ? new String[]{"medium", "large", "huge"} : new String[]{"small", "medium", "medium"})[tier]);   // a bad word in the config: the default of the table
    }

    /** The decision of one trigger: the size (null = too shallow, no event) and the log line. */
    public record Decision(Size size, String mode, String line) {}

    /**
     * The size of the event. Depth mode: the table of prompt 13, unchanged. Players mode: the table of the players rule. In both the shallow sand (depth at most DepthShallowMax) gives no event.
     * {@code verdict} is read only in players mode (null in depth mode).
     */
    public static Decision decide(String modeWord, int depth, int players, OpenDesert.Verdict verdict, HuntConfig c) {
        String mode = mode(modeWord);
        if (depth <= c.depthShallowMax) return new Decision(null, mode, "size: mode=" + mode + " depth=" + depth + " -> too shallow, no event");
        if (DEPTH.equals(mode)) {
            Size s = forDepth(depth, c);
            return new Decision(s, mode, "size: mode=depth depth=" + depth + " -> " + s.label + " W" + trimW(s.width));
        }
        Size s = forPlayers(players, verdict.open(), c);
        return new Decision(s, mode, "size: mode=players list=" + players + " open=" + (verdict.open() ? "yes" : "no") + " sand=" + verdict.percentText() + "% of " + verdict.samples() + " samples depth=" + depth + " -> " + s.label + " W" + trimW(s.width));
    }

    private static String trimW(double w) { return w == Math.rint(w) ? Long.toString((long) w) : Double.toString(w); }

    public static double stalkSeconds(Size s, HuntConfig c) { return s.width + c.stalkExtraSeconds; }
}
