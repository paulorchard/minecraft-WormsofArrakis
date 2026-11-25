package com.paulorchard.islandcraft.wormsofarrakis.camera;

import java.util.Random;
import java.util.function.DoubleBinaryOperator;

/**
 * Prompt 18 part 3: the strike camera framed OUTSIDE the worm, engine-free. The camera sits {@code n} blocks outside the worm's outer surface at about half the worm's height, round the player's held
 * spot: base distance R + FlareFactor x HL + n, backed off so the worm's peak fills StrikeCamFill of the screen height at the assumed field of view, capped by StrikeCamMaxDistance and the loaded
 * area. Every tick the position is checked against the worm's surface and pushed outward (at most PushPerTick) and the camera never moves faster than MaxSpeed. The orbit is a symmetric sweep about
 * the side-on direction with smoothstep ends.
 */
public final class StrikeCam {
    private StrikeCam() {}

    /** Every number (the toml keys StrikeCam*). */
    public record Params(double flareFactor, double clearanceFactor, double fov, double fill, double maxDistance, double sweepDegrees, double lookHeight, double pushPerTick, double maxSpeed,
                         double minHeight, double heightFactor, double maxSeconds) {
        public static final Params DEFAULTS = new Params(0.7, 0.5, 75, 0.85, 120, 120, 1.0, 1.5, 2.0, 3, 0.5, 60);
    }

    /** The distance of a size and why it is what it is. */
    public record Dist(double base, double fit, double wanted, double value, boolean cappedMax, boolean cappedView, double viewLimit, double clearance) {}

    /** The clearance n = StrikeCamClearanceFactor x R. */
    public static double clearance(Params p, double R) { return p.clearanceFactor() * R; }

    /** D_base = R + FlareFactor x HL + n; D_fit = peak / (Fill x 2 tan(FOV / 2)); D = max, capped by the maximum and the loaded area. */
    public static Dist distance(Params p, double R, double headLength, double peak, int viewDistanceChunks) {
        double n = clearance(p, R);
        double base = R + p.flareFactor() * headLength + n;
        double fit = peak / (p.fill() * 2 * Math.tan(Math.toRadians(p.fov()) / 2));
        double wanted = Math.max(base, fit);
        double view = DeathCam.viewLimit(viewDistanceChunks);
        double v = Math.min(wanted, p.maxDistance());
        boolean capMax = wanted > p.maxDistance();
        boolean capView = v > view;
        v = Math.min(v, view);
        return new Dist(base, fit, wanted, v, capMax, capView, view, n);
    }

    /** The camera height above the ground under it: halfway up the worm, at least MinHeight. */
    public static double heightAbove(Params p, double peak) { return Math.max(p.minHeight(), p.heightFactor() * peak); }

    public static double smoothstep(double u) { return DeathCam.smoothstep(u); }

    /** The nearest point of the worm's surface to a world point: {distance, x, y, z}, or null when there is no worm any more. */
    public interface Surface { double[] nearest(double x, double y, double z); }

    /** One camera pose. */
    public record Pose(double x, double y, double z, float yaw, float pitch, double azimuth, double clearance, double push) {}

    /** The state of one camera: fixed at the start, advanced one tick at a time. */
    public static final class Path {
        public final Params p;
        public final double cx, sandY, cz, d, n, peak, runSeconds, azSide, sweepRad;
        public final int side, direction;
        public final boolean firstSolved;
        public final int firstPushes;
        double[] pos = new double[3], applied = new double[3];
        public double maxStep, maxPushStep, minClearance = Double.MAX_VALUE;
        public int steps;
        private double[] lastPush = new double[3];

        /** Starts the camera: the first position is solved clear of the worm by the same rule as every tick (at most 20 pushes). */
        public Path(Params p, double cx, double sandY, double cz, double strikeYawDeg, double distance, double clearance, double peak, long seed, double runSeconds, Surface surface, DoubleBinaryOperator groundAt) {
            this.p = p;
            this.cx = cx;
            this.sandY = sandY;
            this.cz = cz;
            this.d = distance;
            this.n = clearance;
            this.peak = peak;
            this.runSeconds = Math.max(1e-6, runSeconds);
            long z = seed + 0x9E3779B97F4A7C15L; z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L; z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL; z ^= z >>> 31;
            Random rnd = new Random(z);
            this.side = rnd.nextBoolean() ? 1 : -1;
            this.direction = rnd.nextBoolean() ? 1 : -1;
            double y = Math.toRadians(strikeYawDeg);
            this.azSide = Math.atan2(Math.sin(y) * side, Math.cos(y) * side);   // the travel direction rotated by a quarter turn, on the chosen side
            this.sweepRad = Math.toRadians(p.sweepDegrees());
            double[] nom = nominal(0, groundAt);
            double[][] sol = solve(nom, surface);
            applied = sol[0];
            pos = new double[]{nom[0] + applied[0], Math.max(nom[1] + applied[1], groundFloor(nom[0] + applied[0], nom[2] + applied[2], groundAt)), nom[2] + applied[2]};
            this.firstSolved = sol[1][0] > 0;
            this.firstPushes = (int) sol[1][1];
        }

        private double groundFloor(double x, double z, DoubleBinaryOperator groundAt) {
            if (groundAt == null) return sandY + 1;
            double g = groundAt.applyAsDouble(x, z);
            return Double.isNaN(g) ? sandY + 1 : g + 1.0;
        }

        /** The azimuth at {@code t} seconds after the camera started: symmetric about side-on, +- sweep/2, smoothstep, 0 degrees = locked at side-on. */
        public double azimuthAt(double t) { return azSide + direction * sweepRad * (smoothstep(t / runSeconds) - 0.5); }

        double[] nominal(double t, DoubleBinaryOperator groundAt) {
            double az = azimuthAt(t);
            double x = cx + d * Math.cos(az), z = cz + d * Math.sin(az);
            double g = sandY;
            if (groundAt != null) { double gg = groundAt.applyAsDouble(x, z); if (!Double.isNaN(gg)) g = gg; }
            return new double[]{x, g + heightAbove(p, peak), z};
        }

        /** The push (relative to the nominal position) that makes the position at least n clear of the surface, at most 20 iterations: {push[3], {solved(1/0), iterations}}. */
        double[][] solve(double[] nom, Surface surface) {
            double[] push = new double[3];
            int it = 0;
            boolean clear = false;
            for (; it < 20; it++) {
                double[] s = surface == null ? null : surface.nearest(nom[0] + push[0], nom[1] + push[1], nom[2] + push[2]);
                if (s == null || s[0] >= n) { clear = true; break; }
                double need = n - s[0] + 0.05;
                double dx = nom[0] + push[0] - s[1], dy = nom[1] + push[1] - s[2], dz = nom[2] + push[2] - s[3];
                double h = Math.hypot(dx, dz);
                if (h < 1e-6) { dx = nom[0] + push[0] - cx; dz = nom[2] + push[2] - cz; h = Math.hypot(dx, dz); if (h < 1e-6) { dx = 1; dz = 0; h = 1; } }
                double vy = it < 4 ? 0.3 * dy : dy;   // outward first, then up if needed
                double len = Math.sqrt(dx * dx + vy * vy + dz * dz);
                if (it >= 4 && dy < 0.5) { vy = Math.max(vy, 0.5 * h); len = Math.sqrt(dx * dx + vy * vy + dz * dz); }
                push[0] += need * dx / len;
                push[1] += need * vy / len;
                push[2] += need * dz / len;
            }
            return new double[][]{push, {clear ? 1 : 0, it}};
        }

        /** One tick: {@code t} seconds after the start. The nominal orbit position plus the push (rate limited), above the terrain, moved at most MaxSpeed from the last position. */
        public Pose step(double t, Surface surface, DoubleBinaryOperator groundAt) {
            double[] nom = nominal(t, groundAt);
            double[] req = solve(nom, surface)[0];
            double[] prevApplied = applied.clone();
            for (int i = 0; i < 3; i++) {
                double dlt = req[i] - applied[i];
                applied[i] += Math.max(-p.pushPerTick(), Math.min(p.pushPerTick(), dlt));
            }
            double pl = Math.sqrt(Math.pow(applied[0] - prevApplied[0], 2) + Math.pow(applied[1] - prevApplied[1], 2) + Math.pow(applied[2] - prevApplied[2], 2));
            maxPushStep = Math.max(maxPushStep, pl);
            double tx = nom[0] + applied[0], tz = nom[2] + applied[2];
            double ty = Math.max(nom[1] + applied[1], groundFloor(tx, tz, groundAt));
            double dx = tx - pos[0], dy = ty - pos[1], dz = tz - pos[2];
            double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double f = dl > p.maxSpeed() ? p.maxSpeed() / dl : 1.0;
            double[] before = pos.clone();
            pos = new double[]{pos[0] + dx * f, pos[1] + dy * f, pos[2] + dz * f};
            maxStep = Math.max(maxStep, Math.sqrt(Math.pow(pos[0] - before[0], 2) + Math.pow(pos[1] - before[1], 2) + Math.pow(pos[2] - before[2], 2)));
            steps++;
            return pose(surface, t);
        }

        /** The pose now, looking at the held spot raised by LookHeight. */
        public Pose pose(Surface surface, double t) {
            double lx = cx - pos[0], ly = sandY + p.lookHeight() - pos[1], lz = cz - pos[2];
            float yaw = (float) Math.toDegrees(Math.atan2(-lx, lz));
            float pitch = (float) -Math.toDegrees(Math.atan2(ly, Math.hypot(lx, lz)));
            double[] s = surface == null ? null : surface.nearest(pos[0], pos[1], pos[2]);
            double cl = s == null ? Double.NaN : s[0];
            if (!Double.isNaN(cl)) minClearance = Math.min(minClearance, cl);
            return new Pose(pos[0], pos[1], pos[2], yaw, pitch, azimuthAt(t), cl, Math.sqrt(applied[0] * applied[0] + applied[1] * applied[1] + applied[2] * applied[2]));
        }

        public double[] position() { return pos.clone(); }
    }
}
