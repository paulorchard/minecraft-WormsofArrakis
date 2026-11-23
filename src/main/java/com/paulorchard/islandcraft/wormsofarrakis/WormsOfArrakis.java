package com.paulorchard.islandcraft.wormsofarrakis;

import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(WormsOfArrakis.MOD_ID)
public class WormsOfArrakis {
    public static final String MOD_ID = "islandcraft_wormsofarrakis";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WormsOfArrakis() {
        LOGGER.info("[WormsOfArrakis] loaded (Part 0 smoke)");
    }
}
