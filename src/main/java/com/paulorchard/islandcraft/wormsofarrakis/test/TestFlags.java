package com.paulorchard.islandcraft.wormsofarrakis.test;

/** Test-only switches shared by the command (any side) and the client renderer (same JVM in single player). */
public final class TestFlags {
    /** /arrakis_worm_test stats on|off */
    public static volatile boolean stats;
    /** /arrakis_worm_test surface plane|world: plane = flat at the base level (default), world = the client level height map. */
    public static volatile boolean worldSurface;
    /** Stagger: fraction of a face the rings are rotated by (0 = off). */
    public static volatile double stagger = 0.0;
    /** Seed of the variety; negative = take the entity id. */
    public static volatile long seed = -1L;
    /** Brightness jitter, 0..0.1. */
    public static volatile double jitter = 0.04;
    /** One texture offset per ring (even rings normal, odd rings shifted). */
    public static volatile boolean offset = true;

    private TestFlags() {}

    /** Part of the geometry cache keys: changes whenever a visual switch changes. */
    public static String visualKey() {
        return stagger + "/" + seed + "/" + jitter + "/" + offset + "/" + worldSurface;
    }
}
