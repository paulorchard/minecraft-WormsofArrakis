package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Prompt 14 part 3: the corridor test of the crest and the disc test of the engulf, engine-free. The game side answers {@link Terrain#top}: the top block facing air of a column
 * (the height map), whether it is sand, and whether the chunk is loaded at all. Buildings and placed blocks are not a concern (they start another event that removes them).
 */
public final class Corridor {
    private Corridor() {}

    public enum Kind { SAND, OTHER, UNLOADED }

    /** The top block of a column: its kind and the y of the block. */
    public record Top(Kind kind, int y) {}

    public interface Terrain { Top top(int x, int z); }

    /** The numbers of a crest check: blocks ahead, blocks behind, the height tolerance (blocks) and the side distance r0. */
    public record Params(int ahead, int behind, int tolerance, int r0) {}

    /** One failed sample, for the chat and the log. */
    public record Fail(String reason, int x, int z, int along, int side, boolean unloaded) {
        public String text() {
            return String.format(Locale.ROOT, "%s at x=%d z=%d (%d blocks %s, %d blocks to the side)", reason, x, z, Math.abs(along), along >= 0 ? "ahead" : "behind", side);
        }
    }

    /** The result of a check: ok, the first failure, and how many columns were read. */
    public record Result(boolean ok, Fail fail, int reads) {}

    /** The sample offsets along the line: 0, 4, 8, ... up to the limit, and the limit itself when it is not a multiple of 4. */
    public static List<Integer> offsets(int limit) {
        List<Integer> l = new ArrayList<>();
        for (int k = 0; k <= limit; k += 4) l.add(k);
        if (limit % 4 != 0) l.add(limit);
        return l;
    }

    /** The columns a crest check reads: the line every 4 blocks at the centre and at plus and minus r0 sideways. */
    public static int sampleCount(Params p) {
        int n = (offsets(p.ahead()).size() + offsets(p.behind()).size() - 1);   // the offset 0 is shared
        return n * (p.r0() > 0 ? 3 : 1);
    }

    /**
     * The crest check for one direction. The worm travels along {@code dir}; samples from {@code -behind} to {@code +ahead} at the centre line and {@code +-r0} sideways, all of which must be
     * sand within the tolerance of the victim's ground level {@code groundY}. An unloaded column fails.
     */
    public static Result crest(Terrain t, double vx, double vz, int groundY, StrikeType.Dir dir, Params p) {
        int reads = 0;
        int[] sides = p.r0() > 0 ? new int[]{0, -p.r0(), p.r0()} : new int[]{0};
        List<int[]> order = new ArrayList<>();   // {along, side}: nearest the victim first, so a failure close by is found with few reads
        List<Integer> ahead = offsets(p.ahead()), behind = offsets(p.behind());
        for (int k : ahead) for (int s : sides) order.add(new int[]{k, s});
        for (int k : behind) if (k != 0) for (int s : sides) order.add(new int[]{-k, s});
        for (int[] o : order) {
            int along = o[0], side = o[1];
            // sideways is perpendicular to the direction: (dx, dz) -> (-dz, dx)
            int x = (int) Math.floor(vx + dir.dx * along - dir.dz * side);
            int z = (int) Math.floor(vz + dir.dz * along + dir.dx * side);
            Top top = t.top(x, z);
            reads++;
            if (top.kind() == Kind.UNLOADED) return new Result(false, new Fail("chunk not loaded", x, z, along, side, true), reads);
            if (top.kind() != Kind.SAND) return new Result(false, new Fail("not sand", x, z, along, side, false), reads);
            if (Math.abs(top.y() - groundY) > p.tolerance()) return new Result(false, new Fail("height " + (top.y() - groundY) + " off the victim's ground (tolerance " + p.tolerance() + ")", x, z, along, side, false), reads);
        }
        return new Result(true, null, reads);
    }

    /** The columns an engulf check reads: the centre, and r0/2 and r0 in eight directions. */
    public static final int DISC_SAMPLES = 17;

    /** The engulf check: every sample of the disc (centre, r0/2 and r0 in eight directions) must have sand as its top block; there is no flatness test. */
    public static Result disc(Terrain t, double vx, double vz, double r0) {
        int reads = 0;
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[]{0, 0});
        for (double r : new double[]{r0 / 2, r0}) for (int k = 0; k < 8; k++) pts.add(new double[]{r * Math.cos(k * Math.PI / 4), r * Math.sin(k * Math.PI / 4)});
        for (double[] q : pts) {
            int x = (int) Math.floor(vx + q[0]), z = (int) Math.floor(vz + q[1]);
            Top top = t.top(x, z);
            reads++;
            int along = (int) Math.round(Math.hypot(q[0], q[1]));
            if (top.kind() == Kind.UNLOADED) return new Result(false, new Fail("chunk not loaded", x, z, along, 0, true), reads);
            if (top.kind() != Kind.SAND) return new Result(false, new Fail("not sand", x, z, along, 0, false), reads);
        }
        return new Result(true, null, reads);
    }
}
