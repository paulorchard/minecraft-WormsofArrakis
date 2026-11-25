package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import java.util.HashMap;
import java.util.Map;

/**
 * Prompt 14 part 3: a terrain that reads at most {@code budget} new columns per tick, engine-free. The checks of {@link Corridor} are run again every tick on top of it: columns already
 * read come from the cache (free), a column over the budget throws {@link OutOfBudget} and the scan goes on next tick. So the check never blocks the server tick and no column is read twice.
 */
public final class ScanTerrain implements Corridor.Terrain {
    /** Thrown when this tick's budget of new columns is used up. No stack trace (it is control flow). */
    public static final class OutOfBudget extends RuntimeException {
        public OutOfBudget() { super("column budget used up for this tick", null, false, false); }
    }

    private final Corridor.Terrain source;
    private final Map<Long, Corridor.Top> cache = new HashMap<>();
    private int budget, readsThisTick;

    public ScanTerrain(Corridor.Terrain source) { this.source = source; }

    /** Starts a tick with a budget of new columns. */
    public void newTick(int budget) { this.budget = budget; this.readsThisTick = 0; }

    /** Columns read from the world so far (the cost of the scan). */
    public int columns() { return cache.size(); }

    @Override
    public Corridor.Top top(int x, int z) {
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        Corridor.Top t = cache.get(key);
        if (t != null) return t;
        if (readsThisTick >= budget) throw new OutOfBudget();
        readsThisTick++;
        t = source.top(x, z);
        cache.put(key, t);
        return t;
    }

    /** Runs the evaluation on this terrain for one tick; null when the budget ran out (call again next tick after {@link #newTick}). */
    public StrikeChoice.Availability step(java.util.function.Supplier<StrikeChoice.Availability> evaluation) {
        try {
            return evaluation.get();
        } catch (OutOfBudget e) {
            return null;
        }
    }
}
