package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Prompt 20: is the surrounding desert open? The sampled surface within {@code OpenDesertRadius} of a place is on a grid every {@code OpenDesertSampleSpacing} blocks; a sample passes when the top block facing
 * air (the heightmap, as the crest corridor reads it) is Dune Sand (the sand blocks of the config); a sample in an unloaded chunk fails (nothing is ever force-loaded). The disc is open when at least
 * {@code OpenDesertPercent} percent of the samples pass. Engine-free: the terrain is the same {@link Corridor.Terrain} the strike checks read.
 *
 * <p>{@link #check} is the one function: a position and a size (the size is not used by the numbers yet: a later prompt that re-checks at the freeze can scale the disc by the size without changing callers).
 */
public final class OpenDesert {
    private OpenDesert() {}

    /** One failed sample: its offset from the centre and what the top block was. */
    public record Fail(int dx, int dz, Corridor.Kind kind) {
        public String text() { return (kind == Corridor.Kind.UNLOADED ? "chunk not loaded" : "not sand") + " at offset " + dx + "," + dz; }
    }

    public record Verdict(boolean open, int samples, int sand, int unloaded, double percent, double needPercent, List<Fail> firstFails) {
        public String percentText() { return String.format(Locale.ROOT, "%.1f", percent); }
    }

    /** The verdict used where there is no world to read (engine-free tests, an Env with no terrain): open, nothing sampled. */
    public static final Verdict NOT_READ = new Verdict(true, 0, 0, 0, 100.0, 0, List.of());

    /** The grid offsets of the disc: every {@code spacing} blocks, the points with dx^2 + dz^2 <= radius^2. */
    public static List<int[]> grid(int radius, int spacing) {
        List<int[]> g = new ArrayList<>();
        int sp = Math.max(1, spacing);
        int n = radius / sp;
        for (int i = -n; i <= n; i++) for (int j = -n; j <= n; j++) {
            int dx = i * sp, dz = j * sp;
            if ((long) dx * dx + (long) dz * dz <= (long) radius * radius) g.add(new int[]{dx, dz});
        }
        return g;
    }

    /** The open-desert verdict at (x, z) for a worm of this size (every grid column is read once). */
    public static Verdict check(Corridor.Terrain t, double x, double z, SizeTable.Size size, HuntConfig cfg) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        List<int[]> g = grid(cfg.openDesertRadius, cfg.openDesertSpacing);
        int sand = 0, unloaded = 0;
        List<Fail> fails = new ArrayList<>();
        for (int[] o : g) {
            Corridor.Top top = t.top(bx + o[0], bz + o[1]);
            if (top.kind() == Corridor.Kind.SAND) sand++;
            else {
                if (top.kind() == Corridor.Kind.UNLOADED) unloaded++;
                if (fails.size() < 5) fails.add(new Fail(o[0], o[1], top.kind()));
            }
        }
        int n = g.size();
        double pc = n == 0 ? 100.0 : 100.0 * sand / n;
        boolean open = n == 0 || sand * 100.0 >= cfg.openDesertPercent * n - 1e-9;
        return new Verdict(open, n, sand, unloaded, pc, cfg.openDesertPercent, fails);
    }
}
