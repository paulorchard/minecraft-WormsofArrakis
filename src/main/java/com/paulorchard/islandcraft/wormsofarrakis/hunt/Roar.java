package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import com.paulorchard.islandcraft.wormsofarrakis.fx.CrashFx;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Prompt 15 part 4: the distant roar at the start of an event, engine-free. The sounds are the roar rows of the crash sheet ({@link CrashFx#CUES}, group ROAR: the ravager roar and the ender dragon growl),
 * not retyped here; only the volume (computed so that the trigger player is inside the range with a margin) and the pitch (times RoarFarPitch) change.
 * <p>
 * Vanilla: a sound at a position reaches the players within 16 x volume blocks (volume above 1; 16 below) on the server, and the client attenuates it linearly to zero at that distance
 * (OpenAL linear distance clamped, reference 0, max 16 x volume), so the loudness for a listener at distance d from the origin is {@code min(volume, 1) x (1 - d / (16 x max(volume, 1)))}.
 */
public final class Roar {
    private Roar() {}

    /** The game limits of a sound pitch (SoundEngine.calculatePitch). */
    public static final double MIN_PITCH = 0.5, MAX_PITCH = 2.0;

    public static double distance(SizeTable.Size size, HuntConfig c) { return c.roarDistance[size.ordinal()]; }

    /** The volume that makes the range (16 x volume) reach the distance with the margin: volume = distance x margin / 16 (never below 1). */
    public static double volume(double distance, double margin) { return Math.max(1.0, distance * margin / 16.0); }

    /** How far the sound is sent and heard: 16 x volume above 1, 16 below. */
    public static double range(double volume) { return volume > 1.0 ? 16.0 * volume : 16.0; }

    /** The loudness (0 to 1, before the player's volume sliders) for a listener at distance d from the origin. */
    public static double loudness(double d, double volume) {
        double a = range(volume);
        return Math.min(Math.max(volume, 0), 1.0) * Math.max(0, 1 - d / a);
    }

    /** The bearing (degrees, 0 = north, 90 = east) of an event, from a seed. */
    public static double bearing(long seed) { return new Random(seed).nextDouble() * 360.0; }

    /** The point at the distance on the bearing from (px, pz): {x, z}. */
    public static double[] origin(double px, double pz, double bearingDeg, double distance) {
        double b = Math.toRadians(bearingDeg);
        return new double[]{px + Math.sin(b) * distance, pz - Math.cos(b) * distance};
    }

    /** One sound of the roar: seconds after the start, the sound event id, the volume and the pitch as sent. */
    public record Shot(double t, String sound, double volume, double pitch) {}

    /** The roar rows of the crash sheet. */
    public static List<CrashFx.Cue> rows() {
        List<CrashFx.Cue> l = new ArrayList<>();
        for (CrashFx.Cue c : CrashFx.CUES) if (c.group() == CrashFx.Group.ROAR) l.add(c);
        return l;
    }

    /** The sounds of the roar at the given volume and pitch factor: every repeat of every row, the first at 0 s, the pitch varied by the row's jitter from the seed. */
    public static List<Shot> shots(double volume, double pitchFactor, long seed) {
        List<CrashFx.Cue> rows = rows();
        double first = Double.MAX_VALUE;
        for (CrashFx.Cue c : rows) first = Math.min(first, c.off());
        Random rng = new Random(seed ^ 0x5DEECE66DL);
        List<Shot> out = new ArrayList<>();
        for (CrashFx.Cue c : rows) {
            for (int r = 0; r < c.rep(); r++) {
                double pitch = c.pitch() * pitchFactor * (1 + c.jit() * (rng.nextDouble() * 2 - 1));
                out.add(new Shot(c.off() - first + r * c.every(), c.event(), volume, pitch));
            }
        }
        out.sort((a, b) -> Double.compare(a.t(), b.t()));
        return out;
    }

    /** The pitch the game really plays (it limits it to 0.5 .. 2). */
    public static double playedPitch(double pitch) { return Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch)); }

    /** The log line: [WormsOfArrakis] hunt: roar origin=<x,y,z> bearing=<deg> distance=<n> volume=<v> size=<name> W<n> (the prefix is added by the logger call). */
    public static String line(double x, double y, double z, double bearing, double distance, double volume, SizeTable.Size size) {
        return String.format(Locale.ROOT, "hunt: roar origin=%.1f,%.1f,%.1f bearing=%.0f distance=%.0f volume=%.2f range=%.0f size=%s W%s", x, y, z, bearing, distance, volume, range(volume), size.label, trim(size.width));
    }

    private static String trim(double v) { return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%.2f", v); }
}
