package com.paulorchard.islandcraft.wormsofarrakis.light;

import com.paulorchard.islandcraft.wormsofarrakis.fx.FxPlayer.FxGeometry;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;

import java.util.ArrayList;
import java.util.List;

/**
 * Prompt 10 part 3: where the light of the worm is read. The points are in the model frame (x lateral, y above the base level, z along the move) and
 * are always outside the body (one and a half blocks outside the surface of a ring, on the sky side, never inside it) and never below the base level
 * + 1. Five of them are the reference points of the light log (hole, top ring, a middle ring, head tip, entry); the others are rings evenly spaced
 * along the exposed length. Pure code: the light is read through a {@link LightGrid}, so a test can use a fake world.
 */
public final class LightProbe {
    private LightProbe() {}

    /** A sample point of the model frame; {@code ref} marks the five reference points of the log. */
    public record Pt(String name, boolean ref, double x, double y, double z) {}

    /** The light of the world at integer block positions (a test uses a fake grid, the client the level). */
    public interface LightGrid {
        int block(int x, int y, int z);
        int sky(int x, int y, int z);
        boolean solid(int x, int y, int z);
    }

    /** Where the model frame is in the world: the entity origin (on the base level) and the heading, as the renderer turns the model (world = origin + (x cos - z sin, y, x sin + z cos)). */
    public record Frame(double ox, double oy, double oz, double yawRad) {
        public double wx(double x, double z) { return ox + x * Math.cos(yawRad) - z * Math.sin(yawRad); }
        public double wz(double x, double z) { return oz + x * Math.sin(yawRad) + z * Math.cos(yawRad); }
    }

    /** How far outside the surface of a ring the point is (blocks); the distance from the axis is r0 + this, always more than r0 + 0.5. */
    public static final double OUTSIDE = 1.5;
    /** A point inside a solid block is lifted until it is in the open, at most this many blocks. */
    public static final int MAX_LIFT = 8;

    /**
     * The sample points at script loop time t. {@code holeZ} is the model z of the hole (the tip at the breach). The first five are the references
     * (hole, entry, top, mid, tip); the rest are rings along the exposed length. With no exposed ring (the worm is under the sand) only the hole point
     * is returned: one block above the hole at the base level.
     */
    public static List<Pt> points(Moves.Move m, double t, double holeZ) {
        FxGeometry.Snapshot g = FxGeometry.at(m, t);
        double R = m.r0;
        List<Pt> l = new ArrayList<>();
        Pt hole = new Pt("hole", true, R + OUTSIDE, 1, holeZ);   // one block above the hole at the base level, beside the body (the axis passes through the hole itself)
        List<Integer> ex = FxGeometry.bodyRings(g);
        boolean tipUp = g.tipY > 0;
        if (ex.isEmpty() && !tipUp) {
            l.add(hole);
            return l;
        }
        l.add(hole);
        l.add(new Pt("entry", true, -(R + OUTSIDE), 1, Double.isNaN(g.entryZ) ? holeZ : g.entryZ));   // the other side of the entry crossing
        if (!ex.isEmpty()) {
            int top = ex.get(0);
            for (int k : ex) if (g.joints[k][1] > g.joints[top][1]) top = k;
            l.add(skySide("top", true, g, top, R));
            l.add(skySide("mid", true, g, ex.get(ex.size() / 2), R));
        }
        l.add(farSide("tip", true, g, g.tipY, g.tipZ, g.axY, g.axZ, R));
        if (!ex.isEmpty()) {
            for (int i = 1; i <= LightMath.LIGHT_SAMPLES - 5 && ex.size() > 1; i++) l.add(skySide("ring" + i, false, g, ex.get((ex.size() - 1) * i / (LightMath.LIGHT_SAMPLES - 4)), R));
        }
        return l;
    }

    static Pt skySide(String name, boolean ref, FxGeometry.Snapshot g, int k, double R) {
        double[] c = g.joints[k];
        double[] tg = FxGeometry.tangent(g, k);   // {ty, tz}
        return farSide(name, ref, g, c[1], c[2], tg[0], tg[1], R);
    }

    /** The point OUTSIDE blocks beyond the surface (distance r0 + OUTSIDE from the centre) on the sky side of a ring (or of the tip) at (cy, cz) with the axis direction (ty, tz); moved out further if a bend of the body would be closer than r0 + 0.5; at least one block above the base level. */
    static Pt farSide(String name, boolean ref, FxGeometry.Snapshot g, double cy, double cz, double ty, double tz, double R) {
        double ny, nz;
        if (tz >= 0) {   // the normals are (tz, -ty) and (-tz, ty): take the one that points up
            ny = tz;
            nz = -ty;
        } else {
            ny = -tz;
            nz = ty;
        }
        double d = R + OUTSIDE, y = 1, z = cz;
        for (int i = 0; i < 6; i++, d += 1.0) {
            y = Math.max(1, cy + ny * d);
            z = cz + nz * d;
            if (distToAxis(g, y, z) > R + 0.5) break;
        }
        return new Pt(name, ref, 0, y, z);
    }

    /** Distance of a point (y, z) of the model plane to the axis of the worm: the polyline of the tip and the joints. */
    public static double distToAxis(FxGeometry.Snapshot g, double y, double z) {
        double best = Double.MAX_VALUE, py = g.tipY, pz = g.tipZ;
        for (double[] j : g.joints) {
            best = Math.min(best, segDist(y, z, py, pz, j[1], j[2]));
            py = j[1];
            pz = j[2];
        }
        return best;
    }

    private static double segDist(double y, double z, double ay, double az, double by, double bz) {
        double dy = by - ay, dz = bz - az, l2 = dy * dy + dz * dz;
        double f = l2 < 1e-12 ? 0 : Math.max(0, Math.min(1, ((y - ay) * dy + (z - az) * dz) / l2));
        return Math.hypot(y - (ay + f * dy), z - (az + f * dz));
    }

    /** The light of one point: block and sky level read at the block containing it, lifted out of solid blocks. {@code solidLifted} is set when it had to be lifted. */
    public record Reading(Pt p, int block, int sky, int x, int y, int z, boolean lifted) {
        public int max() { return Math.max(block, sky); }
    }

    public static Reading read(LightGrid grid, Frame f, double baseY, Pt p) {
        int x = (int) Math.floor(f.wx(p.x(), p.z())), z = (int) Math.floor(f.wz(p.x(), p.z())), y = (int) Math.floor(baseY + p.y());
        boolean lifted = false;
        for (int i = 0; i < MAX_LIFT && grid.solid(x, y, z); i++) {
            y++;
            lifted = true;
        }
        return new Reading(p, grid.block(x, y, z), grid.sky(x, y, z), x, y, z, lifted);
    }

    /** All the readings and their combination. */
    public record Result(List<Reading> readings, int packed, int bestRef) {}

    public static Result sample(LightGrid grid, Frame f, double baseY, List<Pt> pts) {
        List<Reading> rs = new ArrayList<>();
        int[] packed = new int[pts.size()];
        int bestRef = 0;
        for (int i = 0; i < pts.size(); i++) {
            Reading r = read(grid, f, baseY, pts.get(i));
            rs.add(r);
            packed[i] = LightMath.pack(r.block(), r.sky());
            if (pts.get(i).ref()) bestRef = Math.max(bestRef, r.max());
        }
        return new Result(rs, LightMath.maxCombine(packed, packed.length), bestRef);
    }
}
