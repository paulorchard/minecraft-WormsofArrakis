package com.paulorchard.islandcraft.wormsofarrakis.rumble;

import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Prompt 15 part 1: the guard. When a chunk loads (the spawn chunks at boot included) each section is asked whether its palette may hold arrakis_rumble_sand
 * (a cheap check of the palette, not of 4,096 blocks); only then the section is scanned and every such block becomes air. Nothing happens for the vast majority of chunks.
 */
public final class RumbleGuard {
    private RumbleGuard() {}

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        ChunkAccess chunk = event.getChunk();
        int removed = clean(chunk);
        if (removed > 0) WormsOfArrakis.LOGGER.warn("[WormsOfArrakis] rumble: {} arrakis_rumble_sand block(s) found in the real chunk {} (a bug or a leaked save) and replaced with air", removed, chunk.getPos());
    }

    static int clean(ChunkAccess chunk) {
        BlockState air = Blocks.AIR.defaultBlockState();
        int removed = 0;
        for (LevelChunkSection section : chunk.getSections()) {
            if (section == null || section.hasOnlyAir() || !section.maybeHas(s -> s.is(RumbleBlocks.RUMBLE_SAND.get()))) continue;
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                if (section.getBlockState(x, y, z).is(RumbleBlocks.RUMBLE_SAND.get())) { section.setBlockState(x, y, z, air, false); removed++; }
            }
        }
        if (removed > 0) chunk.markUnsaved();
        return removed;
    }
}
