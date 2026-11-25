package com.paulorchard.islandcraft.wormsofarrakis;

import com.mojang.logging.LogUtils;
import com.paulorchard.islandcraft.wormsofarrakis.test.TestSetup;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(WormsOfArrakis.MOD_ID)
public class WormsOfArrakis {
    public static final String MOD_ID = "islandcraft_wormsofarrakis";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WormsOfArrakis(IEventBus modBus, ModContainer container) {
        LOGGER.info("[WormsOfArrakis] loaded: prompt 01 geometry experiments (GeckoLib only; GeckoMesh does not load on 26.1.2)");
        LOGGER.info("[WormsOfArrakis] {}", com.paulorchard.islandcraft.wormsofarrakis.fx.SizeFactor.logLine());
        container.registerConfig(ModConfig.Type.COMMON, com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntConfigSpec.SPEC);
        com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntAdapter.init();
        LOGGER.info("[WormsOfArrakis] hunt: aggro, sand depth, roster, stalk and tremors loaded (prompt 13); config islandcraft_wormsofarrakis-common.toml; commands under /arrakis_worm_test hunt");
        TestSetup.init(modBus);
    }
}
