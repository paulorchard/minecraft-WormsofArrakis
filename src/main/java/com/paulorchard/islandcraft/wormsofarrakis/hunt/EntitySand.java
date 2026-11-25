package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import java.util.function.IntFunction;

/**
 * Prompt 17 part 1: is an entity standing on Dune Sand (the worm's ground)? Engine-free. The block directly under the feet must itself be a sand block (a block placed on the sand is not
 * sand: the entity is not on sand); on the ground the sand tolerance (a step) is allowed as in prompt 13; in the air the first block that is not air within {@link #AIR_REACH} blocks below must be sand.
 */
public final class EntitySand {
    private EntitySand() {}

    public static final double AIR_REACH = 1.5;

    public static boolean onSand(boolean onGround, double y, double tolerance, IntFunction<SandProbe.Cls> blockAt) {
        int by = (int) Math.floor(y - 1e-4);
        if (onGround) {
            if (blockAt.apply(by) == SandProbe.Cls.SAND) return true;
            int b2 = (int) Math.floor(y - tolerance - 1e-4);
            return b2 != by && blockAt.apply(b2) == SandProbe.Cls.SAND;
        }
        int steps = (int) Math.ceil(AIR_REACH);
        for (int i = 0; i <= steps; i++) {
            // the block i below the first one is only looked at when part of it lies within AIR_REACH of the feet
            if (i > 0 && (y - 1e-4) - (by - i + 1) > AIR_REACH + 1e-9) break;
            SandProbe.Cls c = blockAt.apply(by - i);
            if (c == SandProbe.Cls.SAND) return true;
            if (c != SandProbe.Cls.AIR) return false;
        }
        return false;
    }
}
