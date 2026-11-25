package com.paulorchard.islandcraft.wormsofarrakis.test;

import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;

/**
 * Prompt 14 part 4: puts a worm of a spec into a level at an exact place and yaw, the same entity and the same calls as {@code TestCommand.place} (the command computes the place in front of
 * the player; the hunt computes it from the victim). Nothing of the move is duplicated: the entity carries the spec string and the client does the rest.
 */
public final class WormSpawner {
    private WormSpawner() {}

    /** Spawns the worm with its origin at (x, y, z) and the entity yaw (model +Z is the way it travels); null when the entity could not be created or added. */
    public static TestWormEntity spawn(ServerLevel level, WormSpec spec, double x, double y, double z, float yawDeg) {
        TestWormEntity worm = TestSetup.WORM_TEST.get().create(level, EntitySpawnReason.COMMAND);
        if (worm == null) return null;
        worm.setSpec(spec);
        worm.snapTo(x, y, z, yawDeg, 0.0F);
        boolean added = level.addFreshEntity(worm);
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] strike worm addFreshEntity -> {} pos={} yaw={} chunkLoaded={}", added, worm.position(), yawDeg, level.hasChunkAt(worm.blockPosition()));
        return added ? worm : null;
    }
}
