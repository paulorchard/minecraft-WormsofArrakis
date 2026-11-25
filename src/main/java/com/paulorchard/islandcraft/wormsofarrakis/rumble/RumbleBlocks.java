package com.paulorchard.islandcraft.wormsofarrakis.rumble;

import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Prompt 15 part 1: registers arrakis_rumble_sand. No block item is registered (it is not part of the game for players). */
public final class RumbleBlocks {
    private RumbleBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WormsOfArrakis.MOD_ID);

    /** Sand sounds (the step sound group of sand), no loot, not pushable, not opaque, no random ticks, no block entity. */
    public static final DeferredBlock<RumbleSandBlock> RUMBLE_SAND = BLOCKS.registerBlock("arrakis_rumble_sand", RumbleSandBlock::new,
            () -> BlockBehaviour.Properties.ofFullCopy(Blocks.SAND).noOcclusion().noLootTable().pushReaction(PushReaction.BLOCK).isViewBlocking((s, l, p) -> false));

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        NeoForge.EVENT_BUS.register(RumbleGuard.class);
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] rumble: block {}:arrakis_rumble_sand registered (levels 1 to 15, no item, never in a real world; a leaked one becomes air when its chunk loads)", WormsOfArrakis.MOD_ID);
    }
}
