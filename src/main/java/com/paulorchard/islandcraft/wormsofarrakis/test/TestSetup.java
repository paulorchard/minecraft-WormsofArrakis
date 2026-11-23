package com.paulorchard.islandcraft.wormsofarrakis.test;

import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers the test entity type and the test command. Delete this package to remove all test content. */
public final class TestSetup {
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(WormsOfArrakis.MOD_ID);

    /**
     * Tracking range 64 chunks (1024 blocks) is what the server will request; the effective range is capped by the
     * server's view distance and entity-broadcast-range-percentage (see the report). Position updates are never sent
     * (the test worm never moves).
     */
    public static final DeferredHolder<EntityType<?>, EntityType<TestWormEntity>> WORM_TEST = ENTITIES.registerEntityType(
            "arrakis_worm_test", TestWormEntity::new, MobCategory.MISC,
            b -> b.sized(1.0F, 1.0F).clientTrackingRange(64).updateInterval(Integer.MAX_VALUE).noSave().noSummon().fireImmune());

    private TestSetup() {}

    public static void init(IEventBus modBus) {
        ENTITIES.register(modBus);
        NeoForge.EVENT_BUS.addListener(TestCommand::onRegisterCommands);
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] test content registered: entity arrakis_worm_test, command /arrakis_worm_test");
    }
}
