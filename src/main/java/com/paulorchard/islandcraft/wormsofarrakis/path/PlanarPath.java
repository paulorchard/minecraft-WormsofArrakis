package com.paulorchard.islandcraft.wormsofarrakis.path;

import java.util.ArrayList;
import java.util.List;

/**
 * A path in one vertical plane made of straight lines and circular arcs, parametrised by arc length s (exact:
 * |dP/ds| = 1). x is the horizontal distance along the heading, y the height above the sand surface, theta the
 * direction of travel measured from horizontal (up positive). Before s = 0 and after the total length the path
 * continues straight (tails), so a worm may lie in a tunnel behind the start and run on after the end.
 * Pure code, no Minecraft classes. Being planar by type, a path has no yaw and no roll, so the joint angles derived
 * from it are exact; a non-planar path cannot be expressed here (refused by construction).
 */
public final class PlanarPath {
    public record Prim(double s0, double len, double x0, double y0, double th0, double curv) {
        public boolean isArc() { return curv != 0; }
        public double radius() { return curv == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(curv); }
    }

    private final List<Prim> prims;
    private final double total;

    private PlanarPath(List<Prim> prims, double total) {
        this.prims = prims;
        this.total = total;
    }

    public double length() { return total; }

    public List<Prim> prims() { return prims; }

    /** {x, y} at arc length s. */
    public double[] pos(double s) {
        Prim p = find(s);
        double d = s - p.s0();
        if (!p.isArc()) return new double[]{p.x0() + d * Math.cos(p.th0()), p.y0() + d * Math.sin(p.th0())};
        double th = p.th0() + p.curv() * d;
        return new double[]{p.x0() + (Math.sin(th) - Math.sin(p.th0())) / p.curv(), p.y0() - (Math.cos(th) - Math.cos(p.th0())) / p.curv()};
    }

    /** Direction of travel in radians from horizontal. */
    public double theta(double s) {
        Prim p = find(s);
        return p.th0() + p.curv() * (s - p.s0());
    }

    /** Smallest radius of curvature (blocks) of the arcs overlapping [sa, sb]; infinity if straight. */
    public double minRadius(double sa, double sb) {
        double r = Double.POSITIVE_INFINITY;
        for (Prim p : prims) if (p.isArc() && p.s0() < sb && p.s0() + p.len() > sa) r = Math.min(r, p.radius());
        return r;
    }

    private Prim find(double s) {
        if (s <= 0) return extend(prims.get(0), s);
        if (s >= total) return extend(prims.get(prims.size() - 1), s);
        int lo = 0, hi = prims.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (prims.get(mid).s0() <= s) lo = mid; else hi = mid - 1;
        }
        return prims.get(lo);
    }

    /** Straight tail: a line starting where the first primitive starts (s < 0) or the last one ends. */
    private Prim extend(Prim p, double s) {
        if (s <= 0) return new Prim(0, 0, p.x0(), p.y0(), p.th0(), 0);
        double[] e = endState(p);
        return new Prim(total, 0, e[0], e[1], e[2], 0);
    }

    private static double[] endState(Prim p) {
        double d = p.len();
        if (!p.isArc()) return new double[]{p.x0() + d * Math.cos(p.th0()), p.y0() + d * Math.sin(p.th0()), p.th0()};
        double th = p.th0() + p.curv() * d;
        return new double[]{p.x0() + (Math.sin(th) - Math.sin(p.th0())) / p.curv(), p.y0() - (Math.cos(th) - Math.cos(p.th0())) / p.curv(), th};
    }

    public static Builder start(double x, double y, double thetaDeg) {
        return new Builder(x, y, Math.toRadians(thetaDeg));
    }

    public static final class Builder {
        private final List<Prim> list = new ArrayList<>();
        private double x, y, th, s;

        private Builder(double x, double y, double th) {
            this.x = x; this.y = y; this.th = th;
        }

        public Builder line(double len) {
            Prim p = new Prim(s, len, x, y, th, 0);
            list.add(p);
            advance(p);
            return this;
        }

        /** Arc of the given radius turning by angleDeg (positive = turns upward, negative = downward). */
        public Builder arc(double radius, double angleDeg) {
            double sweep = Math.toRadians(angleDeg);
            double curv = Math.signum(sweep) / radius;
            Prim p = new Prim(s, Math.abs(sweep) * radius, x, y, th, curv);
            list.add(p);
            advance(p);
            return this;
        }

        private void advance(Prim p) {
            double[] e = endState(p);
            x = e[0]; y = e[1]; th = e[2];
            s += p.len();
        }

        public PlanarPath build() {
            return new PlanarPath(list, s);
        }
    }
}
