package com.paulorchard.islandcraft.wormsofarrakis.test.client;

import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
import com.paulorchard.islandcraft.wormsofarrakis.test.TestWormEntity;
import com.paulorchard.islandcraft.wormsofarrakis.test.WormSpec;
import net.minecraft.resources.Identifier;

/**
 * Model of the test worm. The model is not read from a file: getBakedModel is overridden to build it in code from the
 * spec encoded in the model Identifier (islandcraft_wormsofarrakis:worm/ring_16_0 and so on).
 */
final class TestWormModel extends GeoModel<TestWormEntity> {
    /**
     * The one texture of every test worm: vanilla dripstone, by reference. Not shipped by this mod. Because it is a
     * vanilla path, any resource pack that replaces it restyles the worm.
     */
    static final Identifier TEXTURE = Identifier.parse("minecraft:textures/block/dripstone_block.png");

    private static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(WormsOfArrakis.MOD_ID, "arrakis_worm_test");

    @Override
    public Identifier getModelResource(GeoRenderState state) {
        WormSpec spec = WormSpec.parse(state.getGeckolibData(TestWormRenderer.SPEC));
        if (spec.kind().equals("move")) spec = WormSpec.of("move", "-", spec.p(0, 16));   // the head model depends on the width only
        return Identifier.fromNamespaceAndPath(WormsOfArrakis.MOD_ID, "worm/" + spec.key());
    }

    @Override
    public Identifier getTextureResource(GeoRenderState state) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(TestWormEntity animatable) {
        return ANIMATIONS;
    }

    @Override
    public BakedGeoModel getBakedModel(Identifier id) {
        String path = id.getPath();
        String key = path.startsWith("worm/") ? path.substring(5) : path;
        // key is the spec with ';' replaced by '_' (see WormSpec.key); rebuild the spec from it
        String[] t = key.split("_");
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < t.length; i++) s.append(i == 0 ? "" : ";").append(t[i]);
        WormSpec spec = WormSpec.parse(s.toString());
        return WormModels.get(id, spec);
    }
}
