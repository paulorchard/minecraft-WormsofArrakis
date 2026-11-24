package com.paulorchard.islandcraft.wormsofarrakis.test;

import com.paulorchard.islandcraft.wormsofarrakis.light.LightWatch;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Prompt 10 part 3: the switches of the light listener (light log, light force), shared by the command (any side) and the client in the same JVM
 * (single player), like {@link FxFlags}. The client registers {@code reporter} (the current samples as chat lines) and {@code dumper} (writes the
 * ring buffers to the log, returns the number of lines) when it loads; on a dedicated server they stay null.
 */
public final class LightFlags {
    private LightFlags() {}

    /** light log on|off|all: on = changes and warnings only (default). */
    public static volatile LightWatch.LogMode log = LightWatch.LogMode.ON;
    /** light force: the packed light the renderer uses for every worm, or -1 for auto (the mode of each move). */
    public static volatile int forced = -1;
    public static volatile String forcedLabel = "auto";

    public static volatile Supplier<List<String>> reporter;
    public static volatile IntSupplier dumper;
}
