package com.paulorchard.islandcraft.wormsofarrakis.camera;

import java.util.Random;
import java.util.function.DoubleBinaryOperator;

/**
 * Prompt 16 part 3: the orbit of the death camera, engine-free. The centre is the death spot (x and z; y = the sand surface there), the camera looks at the centre raised by
 * LookHeight x radius, sits at a radius that depends on the size (clamped to the view distance), at a height of HeightFactor x radius (at least MinHeight, and at least Clearance above the
 * ground under it), starts side-on to the strike's travel direction on a side chosen by a seeded random, and turns TurnDegrees round the spot over the remaining run time of the move with a
 * smoothstep (zero angular speed at both ends), then holds.
 */
public final class DeathCam {
    private DeathCam() {}

    /** Every number of the orbit (the toml keys). {@code distance} is indexed small, medium, large, huge. */
    public record Params(double[] distance, double turnDegrees, double lookHeight, double heightFactor, double minHeight, double clearance, double maxSeconds) {
        public static final Params DEFAULTS = new Params(new double[]{24, 40, 70, 100}, 120, 0.25, 0.45, 8, 3, 60);
    }

    /** The orbit radius of a size and why it is what it is. */
    public record Radius(double value, double wanted, boolean clamped, double limit) {}

    /** The horizontal reach of the entity tracking: the camera must be inside the loaded and tracked area of the victim, 16 x (viewDistance - 1) blocks. */
    public static double viewLimit(int viewDistanceChunks) { return 16.0 * (Math.max(2, viewDistanceChunks) - 1); }

    public static Radius radius(Params p, int sizeIndex, int viewDistanceChunks) {
        double wanted = p.distance()[Math.max(0, Math.min(3, sizeIndex))];
        double limit = viewLimit(viewDistanceChunks);
        return new Radius(Math.min(wanted, limit), wanted, wanted > limit, limit);
    }

    /** The camera height above the centre (the sand surface): HeightFactor x radius, at least MinHeight. */
    public static double height(Params p, double radius) { return Math.max(p.minHeight(), p.heightFactor() * radius); }

    /** 0 at 0, 1 at 1, derivative 0 at both. */
    public static double smoothstep(double u) {
        double x = Math.max(0, Math.min(1, u));
        return x * x * (3 - 2 * x);
    }

    /** What is fixed when the camera starts. {@code az0} is the start azimuth (radians, the camera's offset direction from the centre is (cos az, sin az) in (x, z)). */
    public record Setup(double cx, double sandY, double cz, double radius, double height, double az0, int direction, double turnRad, double lookHeightBlocks, double clearance, int side) {
        public double lookX() { return cx; }
        public double lookY() { return sandY + lookHeightBlocks; }
        public double lookZ() { return cz; }
    }

    /**
     * The start: side-on to the strike (perpendicular to its travel direction, which for the entity yaw {@code strikeYawDeg} is (-sin yaw, cos yaw)) on the side given by the seed, turning
     * clockwise or counter-clockwise by the seed. The same seed gives the same side and direction.
     */
    public static Setup setup(Params p, double cx, double sandY, double cz, double strikeYawDeg, double radius, long seed) {
        long z = seed + 0x9E3779B97F4A7C15L; z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L; z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL; z ^= z >>> 31;   // splitmix: small seeds must not give alike sides
        Random rnd = new Random(z);
        int side = rnd.nextBoolean() ? 1 : -1;
        int dir = rnd.nextBoolean() ? 1 : -1;
        double y = Math.toRadians(strikeYawDeg);
        double px = Math.cos(y) * side, pz = Math.sin(y) * side;   // the travel direction rotated by a quarter turn
        double az0 = Math.atan2(pz, px);
        return new Setup(cx, sandY, cz, radius, height(p, radius), az0, dir, Math.toRadians(p.turnDegrees()), p.lookHeight() * radius, p.clearance(), side);
    }

    /** One camera pose: position, the look angles (Minecraft yaw and pitch in degrees), and the turn so far. */
    public record Pose(double x, double y, double z, float yaw, float pitch, double azimuth, double progress) {}

    /** The azimuth at the time: the start plus the direction x the turn x the smoothstep of the fraction of {@code runSeconds} elapsed. */
    public static double azimuth(Setup s, double t, double runSeconds) {
        double u = runSeconds <= 1e-9 ? 1 : t / runSeconds;
        return s.az0() + s.direction() * s.turnRad() * smoothstep(u);
    }

    /**
     * The pose at {@code t} seconds after the death. {@code groundAt} gives the height of the top of the ground at a column (NaN or null when unknown): the camera is kept at least
     * {@code clearance} above it. After {@code runSeconds} the pose no longer changes (the hold).
     */
    public static Pose pose(Setup s, double t, double runSeconds, DoubleBinaryOperator groundAt) {
        double az = azimuth(s, t, runSeconds);
        double x = s.cx() + s.radius() * Math.cos(az), z = s.cz() + s.radius() * Math.sin(az);
        double y = s.sandY() + s.height();
        if (groundAt != null) {
            double g = groundAt.applyAsDouble(x, z);
            if (!Double.isNaN(g)) y = Math.max(y, g + s.clearance());
        }
        double dx = s.lookX() - x, dy = s.lookY() - y, dz = s.lookZ() - z;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        double u = runSeconds <= 1e-9 ? 1 : Math.max(0, Math.min(1, t / runSeconds));
        return new Pose(x, y, z, (float) yaw, (float) pitch, az, u);
    }

    /** The distance the camera moves between two poses (blocks): the step per tick that the report quotes. */
    public static double step(Pose a, Pose b) { return Math.sqrt((a.x() - b.x()) * (a.x() - b.x()) + (a.y() - b.y()) * (a.y() - b.y()) + (a.z() - b.z()) * (a.z() - b.z())); }
}
