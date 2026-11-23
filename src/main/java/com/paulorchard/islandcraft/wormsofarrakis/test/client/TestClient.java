package com.paulorchard.islandcraft.wormsofarrakis.test.client;

import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
import com.paulorchard.islandcraft.wormsofarrakis.test.TestSetup;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client only: registers the test worm renderer. Never loaded on a dedicated server. */
@EventBusSubscriber(modid = WormsOfArrakis.MOD_ID, value = Dist.CLIENT)
public final class TestClient {
    private TestClient() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TestSetup.WORM_TEST.get(), TestWormRenderer::new);
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] client: test worm renderer registered (GeckoLib GeoEntityRenderer, texture minecraft:textures/block/dripstone_block.png by reference)");
    }
}
