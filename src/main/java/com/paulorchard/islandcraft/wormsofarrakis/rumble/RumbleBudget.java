package com.paulorchard.islandcraft.wormsofarrakis.rumble;

/**
 * Prompt 15 part 2: the budget of cell updates per tick for the whole server, engine-free. When the cells wanted in a tick are over the budget, every event is thinned by one ratio
 * (budget / wanted) and no event with something waiting gets less than one (nobody is starved to zero); the cells that waited longest are the ones sent (the caller orders by age).
 */
public final class RumbleBudget {
    private RumbleBudget() {}

    /** How many cells each event may send this tick. {@code waiting[i]}: the cells event i has pending. */
    public static int[] allocate(int[] waiting, int budget) {
        int total = 0;
        for (int w : waiting) total += w;
        int[] out = new int[waiting.length];
        double ratio = ratio(total, budget);
        for (int i = 0; i < waiting.length; i++) out[i] = waiting[i] <= 0 ? 0 : (ratio >= 1 ? waiting[i] : Math.max(1, (int) Math.floor(waiting[i] * ratio)));
        return out;
    }

    public static double ratio(int wanted, int budget) { return wanted <= budget || wanted <= 0 ? 1.0 : (double) budget / wanted; }
}
