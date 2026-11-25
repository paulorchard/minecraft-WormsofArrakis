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
    public static double stalkSeconds(Size s, HuntConfig c) { return s.width + c.stalkExtraSeconds; }
}
