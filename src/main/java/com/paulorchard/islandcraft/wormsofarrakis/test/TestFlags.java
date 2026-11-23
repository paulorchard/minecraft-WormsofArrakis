package com.paulorchard.islandcraft.wormsofarrakis.test;

/** Test-only switches shared by the command (any side) and the client renderer (same JVM in single player). */
public final class TestFlags {
    /** /arrakis_worm_test stats on|off */
    public static volatile boolean stats;
    /** /arrakis_worm_test surface plane|world: plane = the entity height, world = the client level height map. */
    public static volatile boolean worldSurface;

    private TestFlags() {}
}
