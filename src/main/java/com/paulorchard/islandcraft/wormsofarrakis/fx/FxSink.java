package com.paulorchard.islandcraft.wormsofarrakis.fx;

/**
 * Prompt 09: where the sheet is played. The pure {@link FxPlayer} says what happens and when; a sink does it. All positions and velocities are in
 * the worm's model frame (x lateral, y above the base level of the sand, z the direction of the move), so the sink only has to apply its origin and
 * heading. The client sink ({@code FxClient}) plays them with playLocalSound and ParticleEngine; a server-side sink can replace it without any change
 * to the sheet (see the report for the mapping to Level.playSound and ServerLevel.sendParticles).
 */
public interface FxSink {
    /** One sound play; volume is vol x fxvol (the sound entry's own volume is applied by the engine), pitch is the row's pitch with the random jitter. */
    void sound(CrashFx.Cue cue, double x, double y, double z, double volume, double pitch);

    /** One particle. */
    void particle(Spawn p);

    /** An INFO line for the log. */
    default void log(String line) {}

    /** One particle to make: type, position, velocity (blocks per tick), lifetime (ticks), full width (blocks), gravity factor of the sheet, tint (0xRRGGBB or -1) and the row. */
    record Spawn(CrashFx.PType type, double x, double y, double z, double vx, double vy, double vz, int life, double size, double grav, int tint, int row) {}
}
