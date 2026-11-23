package com.paulorchard.islandcraft.wormsofarrakis.path;

/**
 * The Minecraft sea level, the base of the sand: every height in the worm code is relative to it (local y = 0).
 * BASE_Y is the fallback; where a level is available the game's own sea level is read (Level.getSeaLevel()).
 * The worm test entity is placed with its origin at the base level, so the engine stays relative and the renderer needs
 * no extra translation.
 */
public final class WorldBase {
    public static final int BASE_Y = 63;

    /** What /arrakis_worm_test base set: "sea" (default), "here" or "y" (a fixed number). */
    public static volatile String mode = "sea";
    public static volatile double fixedY = BASE_Y;

    private WorldBase() {}

    /**
     * World Y of the base level for a spawn.
     * @param seaLevel the level's sea level, or a negative number when no level is available
     * @param feetY the player's feet Y
     */
    public static double resolve(int seaLevel, double feetY) {
        return switch (mode) {
            case "here" -> feetY - 1.0;
            case "y" -> fixedY;
            default -> seaLevel > Integer.MIN_VALUE && seaLevel != 0 ? seaLevel : BASE_Y;
        };
    }
}
