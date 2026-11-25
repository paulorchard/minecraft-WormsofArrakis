package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import com.paulorchard.islandcraft.wormsofarrakis.path.HeadSpec;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveFactory;
import com.paulorchard.islandcraft.wormsofarrakis.test.Presets;
import com.paulorchard.islandcraft.wormsofarrakis.test.WormSpec;

/**
 * Prompt 14 part 2: where a move goes so that its entry crossing is at the victim, and when the mouth encloses them. Engine-free (the moves are pure). The move is the preset of
 * {@code /arrakis-worm <size> <move>} ({@link Presets}); the spawn is the same as the command's (model +Z is the way the worm travels, the entity origin is shifted back along it).
 */
public final class Alignment {
    private Alignment() {}

    /** The preset spec of a type and size, as one run (loop off: the worm removes itself when its run is over). */
    public static WormSpec once(StrikeType t, SizeTable.Size size) {
        WormSpec s = Presets.of(Presets.Size.of(size.label), t.label).spec();
        double[] p = java.util.Arrays.copyOf(s.p(), Math.max(21, s.p().length));
        for (int i = s.p().length; i < 21; i++) p[i] = (i == 17 || i == 18) ? -1 : 0;   // the defaults of the missing fields (freeze off, the move's own pause)
        p[19] = 0;
        return new WormSpec(s.kind(), s.mode(), p);
    }

    public static Moves.Move move(WormSpec s) { return MoveFactory.get(s); }

    private static double tipHeight(Moves.Move m, double tau) { return m.tip(m.at(tau)[0])[1]; }

    /** The script time (seconds after the move starts) at which the head tip first meets the sand surface (height 0), refined by bisection from the move's own window. */
    public static double entryTau(Moves.Move m) {
        double a = m.window()[0];
        double lo = Math.max(0, a - 0.011), hi = a;
        if (tipHeight(m, lo) > 0) return lo;   // already above at the start of the script
        for (int i = 0; i < 50; i++) {
            double mid = 0.5 * (lo + hi);
            if (tipHeight(m, mid) > 0) hi = mid; else lo = mid;
        }
        return hi;
    }

    /** The model z of the entry crossing: where the head first meets the sand surface along the path. */
    public static double entryZ(Moves.Move m) { return m.tip(m.at(entryTau(m))[0])[0]; }

    /** The world x and z of the entity origin so that the entry crossing (model z = {@code entryZ}) is at {@code (vx, vz)}, for an entity yaw in degrees (model +Z points along (-sin yaw, cos yaw)). */
    public static double[] origin(double vx, double vz, double entryZ, double yawDeg) {
        double y = Math.toRadians(yawDeg);
        return new double[]{vx + Math.sin(y) * entryZ, vz - Math.cos(y) * entryZ};
    }

    /** Where the model z lands in the world for an origin and a yaw (the inverse of {@link #origin}; used by the tests). */
    public static double[] worldOf(double[] origin, double z, double yawDeg) {
        double y = Math.toRadians(yawDeg);
        return new double[]{origin[0] - Math.sin(y) * z, origin[1] + Math.cos(y) * z};
    }

    /**
     * The moment (seconds after the move starts) the mouth encloses the victim, derived from the move's own frames: a crest: the tip crossing plus the time the head takes to pass
     * the player (head length / speed); crash, emerge, engulf: the instant the head tip is at its highest during the rise (the mouth is open round the player).
     */
    public static double devourSeconds(StrikeType t, Moves.Move m) {
        double entry = entryTau(m);
        if (t == StrikeType.CREST) return entry + HeadSpec.headLength(m.width) / m.speed;
        double best = -1e18, at = entry;
        double end = m.advanceTime;
        for (double tau = entry; tau <= end + 1e-9; tau += 0.005) {
            double h = tipHeight(m, tau);
            if (h > best + 1e-9) { best = h; at = tau; }
        }
        return at;
    }

    /** Rounded to 0.01 s, the value written to the config as the default. */
    public static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
