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
public final class PlanarPath implements PosCurve {
    public record Prim(double s0, double len, double x0, double y0, double th0, double curv, double kpar) {
        public Prim(double s0, double len, double x0, double y0, double th0, double curv) { this(s0, len, x0, y0, th0, curv, 0); }
        public boolean isArc() { return curv != 0; }
        /** A parabola y = x tan(th0) - kpar x^2 / 2 (x horizontal from the start), travelled from slope tan(th0) down to slope -tan(th0). */
        public boolean isPara() { return kpar != 0; }
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
    @Override
    public double[] pos(double s) {
        Prim p = find(s);
        double d = s - p.s0();
        if (p.isPara()) return paraPos(p, d);
        if (!p.isArc()) return new double[]{p.x0() + d * Math.cos(p.th0()), p.y0() + d * Math.sin(p.th0())};
        double th = p.th0() + p.curv() * d;
        return new double[]{p.x0() + (Math.sin(th) - Math.sin(p.th0())) / p.curv(), p.y0() - (Math.cos(th) - Math.cos(p.th0())) / p.curv()};
    }

    /** Direction of travel in radians from horizontal. */
    public double theta(double s) {
        Prim p = find(s);
        if (p.isPara()) return Math.atan(paraSlope(p, s - p.s0()));
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

    /** F(w) = integral of sqrt(1 + w^2): arc length of a parabola is (F(w0) - F(w)) / k. */
    static double arcF(double w) { return 0.5 * (w * Math.sqrt(1 + w * w) + Math.log(w + Math.sqrt(1 + w * w))); }

    private static double paraSlope(Prim p, double d) {
        double w0 = Math.tan(p.th0());
        double target = arcF(w0) - p.kpar() * Math.min(Math.max(d, 0), p.len());
        double w = w0 - 2 * w0 * Math.min(Math.max(d / p.len(), 0), 1);
        for (int i = 0; i < 40; i++) {
            double dw = (arcF(w) - target) / Math.sqrt(1 + w * w);
            w -= dw;
            if (w > w0) w = w0;
            if (w < -w0) w = -w0;
            if (Math.abs(dw) < 1e-13) break;
        }
        return w;
    }

    private static double[] paraPos(Prim p, double d) {
        double w0 = Math.tan(p.th0()), w = paraSlope(p, d);
        return new double[]{p.x0() + (w0 - w) / p.kpar(), p.y0() + (w0 * w0 - w * w) / (2 * p.kpar())};
    }

    private static double[] endState(Prim p) {
        double d = p.len();
        if (p.isPara()) return new double[]{p.x0() + 2 * Math.tan(p.th0()) / p.kpar(), p.y0(), -p.th0()};
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

        /** Parabola launched at the current heading (must be upward) with y = x tan(a) - k x^2 / 2; ends at the same height heading -a. */
        public Builder parabola(double k) {
            double w0 = Math.tan(th);
            Prim p = new Prim(s, 2 * arcF(w0) / k, x, y, th, 0, k);
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
