package com.paulorchard.islandcraft.wormsofarrakis.client;

import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Prompt 20 part 3: the Mods menu "Config" button of this mod opens NeoForge's built-in configuration screen (every setting of
 * islandcraft_wormsofarrakis-common.toml, SizeMode among them). A second {@code @Mod} entry of the same mod that loads on the physical client only, so a
 * dedicated server never loads this class (it has no client classes).
 */
@Mod(value = WormsOfArrakis.MOD_ID, dist = Dist.CLIENT)
public final class WormsOfArrakisClient {
    public WormsOfArrakisClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] config: the Mods menu config screen is registered (client side); SizeMode and every other setting are editable there");
    }
}
