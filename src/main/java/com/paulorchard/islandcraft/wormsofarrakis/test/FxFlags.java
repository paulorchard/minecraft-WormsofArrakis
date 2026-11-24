package com.paulorchard.islandcraft.wormsofarrakis.test;

import com.paulorchard.islandcraft.wormsofarrakis.fx.CrashFx;

/**
 * Prompt 09: the runtime switches of the {@code fx} command (like {@link TestFlags}: set by the command, read by the client's effect player in the
 * same game; a dedicated server has no client to read them). Not part of any spec, so they never change a move.
 */
public final class FxFlags {
    private FxFlags() {}

    public static volatile boolean sound = true;
    public static volatile boolean particles = true;
    /** The one sound group that plays (fx solo), or null for all. */
    public static volatile CrashFx.Group solo = null;
}
