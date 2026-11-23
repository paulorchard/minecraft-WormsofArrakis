package com.paulorchard.islandcraft.wormsofarrakis.test.client;

import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dev only (prompt 01): with -Darrakis.autotest=true the client takes a screenshot whenever a file arrakis_shot.txt
 * appears in the game directory (its first line is the screenshot name), and quits when arrakis_quit.txt appears.
 * A dev script drives the world from the server console. Off by default; the property is never set in the owner's
 * instance, so this class does nothing there.
 */
@EventBusSubscriber(modid = WormsOfArrakis.MOD_ID, value = Dist.CLIENT)
public final class AutoTest {
    private static final boolean ON = Boolean.getBoolean("arrakis.autotest");
    private static int joined;

    private AutoTest() {}

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!ON) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (joined++ == 100) WormsOfArrakis.LOGGER.info("[WormsOfArrakis] autotest ready");
        mc.options.hideGui = true;
        Path dir = mc.gameDirectory.toPath();
        Path shot = dir.resolve("arrakis_shot.txt"), quit = dir.resolve("arrakis_quit.txt");
        try {
            if (Files.exists(shot)) {
                String name = Files.readString(shot).trim();
                Files.delete(shot);
                Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), 1,
                        c -> WormsOfArrakis.LOGGER.info("[WormsOfArrakis] autotest screenshot {}", name));
            }
            if (Files.exists(quit)) {
                Files.delete(quit);
                mc.stop();
            }
        } catch (IOException e) {
            WormsOfArrakis.LOGGER.warn("[WormsOfArrakis] autotest file error {}", e.toString());
        }
    }
}
