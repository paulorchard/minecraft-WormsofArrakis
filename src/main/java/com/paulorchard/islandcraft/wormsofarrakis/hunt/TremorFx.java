package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;

/**
 * Prompt 13 part 5: the hook of the camera. Every tremor of a listed player calls {@link #onTremor}; this prompt does nothing with it (no camera shake; the Minecraft camera
 * needs a client-side effect). A later prompt sets {@link #camera} (or calls this from its own code) to shake the camera of the player by the strength 0.3 to 1.
 */
public final class TremorFx {
    private TremorFx() {}

    /** Set by a later prompt: (player, strength). Null = no camera. */
    public static volatile BiConsumer<ServerPlayer, Double> camera;

    /** A tremor of strength S (0.3 to 1) happens under this player. */
    public static void onTremor(ServerPlayer player, double strength) {
        BiConsumer<ServerPlayer, Double> c = camera;
        if (c != null) c.accept(player, strength);
    }
}
