package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import com.paulorchard.islandcraft.wormsofarrakis.fx.EruptionFx;

/**
 * Prompt 17 part 2: the strike area, engine-free. It is the footprint circle of the eruption of the move: the largest ring or disc radius among the rows of {@link EruptionFx#ROWS}
 * (the {@code ring} of a row is in units of r0, the preset's ring radius 3.99 / 7.96 / 15.92 / 23.88), plus StrikeAreaMargin. A config value above zero replaces the derived radius of a
 * move and size. At the start of the freeze, before the move is chosen, the largest of the four moves of the size is used.
 */
public final class StrikeArea {
    private StrikeArea() {}

    /** The largest ring of a RING or DISC row of the eruption, in units of r0. Read from the rows, never typed in. */
    public static double largestRingUnits() {
        double m = 0;
        for (EruptionFx.Row r : EruptionFx.ROWS) if (r.shape() == EruptionFx.Shape.RING || r.shape() == EruptionFx.Shape.DISC) m = Math.max(m, r.ring());
        return m;
    }

    /** The row that gives the largest ring (for the report and the log). */
    public static String largestRingRow() {
        EruptionFx.Row best = null;
        for (EruptionFx.Row r : EruptionFx.ROWS) if ((r.shape() == EruptionFx.Shape.RING || r.shape() == EruptionFx.Shape.DISC) && (best == null || r.ring() > best.ring())) best = r;
        return best == null ? "-" : best.name() + " (" + best.shape() + " ring " + best.ring() + ")";
    }

    /** The derived radius for a move whose ring radius is {@code r0}: largest ring x r0 + margin. */
    public static double derived(double r0, double margin) { return largestRingUnits() * r0 + margin; }

    /** The radius used: the config override when it is above zero, else the derived one. */
    public static double radius(double r0, double override, double margin) { return override > 0 ? override : derived(r0, margin); }

    /** The largest of the radii of the moves of a size (the group at the start of the freeze). */
    public static double max(double... radii) {
        double m = 0;
        for (double r : radii) m = Math.max(m, r);
        return m;
    }
}
