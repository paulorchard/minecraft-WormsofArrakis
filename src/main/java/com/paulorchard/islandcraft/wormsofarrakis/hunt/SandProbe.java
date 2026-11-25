package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import java.util.function.IntFunction;

/**
 * Prompt 13 part 2: where the player stands on sand and how deep the sand is under them, engine-free (the game side answers "what is the block at this height of the column").
 */
public final class SandProbe {
    private SandProbe() {}

    public enum Cls { SAND, AIR, OTHER }

    public static final int NONE = Integer.MIN_VALUE;
    /** A player in the air after a jump still counts as on sand when the sand is this far below at most. */
    public static final int AIRBORNE_REACH = 3;
    public static final int MAX_DEPTH = 128;

    /**
     * The y of the sand block the player stands on, or {@link #NONE}. On the ground: the block directly under the feet, or (SandTolerance, a step) the block that is
     * under the feet when they are {@code tolerance} lower. In the air: the first block that is not air within {@link #AIRBORNE_REACH} blocks below, if it is sand.
     */
    public static int surface(boolean onGround, double y, double tolerance, IntFunction<Cls> blockAt) {
        int by = (int) Math.floor(y - 1e-4);
        if (onGround) {
            if (blockAt.apply(by) == Cls.SAND) return by;
            int b2 = (int) Math.floor(y - tolerance - 1e-4);
            if (b2 != by && blockAt.apply(b2) == Cls.SAND) return b2;
            return NONE;
        }
        for (int i = 0; i <= AIRBORNE_REACH; i++) {
            Cls c = blockAt.apply(by - i);
            if (c == Cls.SAND) return by - i;
            if (c != Cls.AIR) return NONE;
        }
        return NONE;
    }

    /** D: the consecutive sand blocks from {@code surfaceY} downwards in the one column, stopping at the first block that is not sand (air, rock, water, anything). */
    public static int depth(int surfaceY, IntFunction<Cls> blockAt) {
        if (surfaceY == NONE) return 0;
        int d = 0;
        for (int y = surfaceY; d < MAX_DEPTH; y--) {
            if (blockAt.apply(y) != Cls.SAND) break;
            d++;
        }
        return d;
    }
}
