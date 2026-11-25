package com.paulorchard.islandcraft.wormsofarrakis.hazard;

import com.paulorchard.islandcraft.wormsofarrakis.test.MoveFactory;

/** Prompt 18: the expected instants, engine-free, from the move's own frames, at which a disc first meets a 0.6 x 1.8 box standing at the entry crossing (model z = {@code entryZ}, x = 0, sand level y = 0). */
public final class DiscTimes {
    private DiscTimes() {}

    /** Seconds after the move started (a whole tick) at which the disc first meets the box, or -1 when it never does. */
    public static double first(WormHazard h, Discs.Disc d, double entryZ) {
        Box b = Box.of(0, 0, entryZ, 0.6, 1.8);
        int last = (int) (h.move.playTime() * 20) + 40;
        for (int tick = 1; tick <= last; tick++) {
            if (h.discContact(b, MoveFactory.tau(h.spec, tick - 1), MoveFactory.tau(h.spec, tick), d) != null) return tick / 20.0;
        }
        return -1;
    }
}
