package com.paulorchard.islandcraft.wormsofarrakis.fx;

import com.paulorchard.islandcraft.wormsofarrakis.fx.CrashFx.Anchor;
import com.paulorchard.islandcraft.wormsofarrakis.fx.CrashFx.Cue;
import com.paulorchard.islandcraft.wormsofarrakis.fx.CrashFx.Emitter;
import com.paulorchard.islandcraft.wormsofarrakis.fx.CrashFx.Place;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;
import com.paulorchard.islandcraft.wormsofarrakis.path.WormPose;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Prompt 09: plays the sheet of one worm. Pure code: it is fed the script time of the worm (the loop clock of the phases) once per game tick and says
 * to a {@link FxSink} which sounds and which particles happen in that tick. Deterministic for a given seed and a given sequence of calls.
 *
 * <p>Rules of the clock: tau is the script time (it keeps counting through the loops; the loop time is tau modulo the cycle). A new loop starts the
 * sheet again from the top (cues that had not started are dropped); a jump of the clock (freeze at a time, or a gap longer than a second) does not
 * play what was skipped; a frozen clock plays nothing. {@link #cancel()} drops every cue that has not started and all emission.
 */
public final class FxPlayer {
    /** One scheduled sound play: loop time, the cue (index into {@link CrashFx#CUES}), volume (vol x fxvol) and pitch (with the jitter). */
    public record Play(double time, int cue, double volume, double pitch) {}

    private final Moves.Move m;
    private final FxOptions opt;
    private final FxAnchors.Anchors a;
    private final List<Play> schedule;
    private final Random rng;
    private final double scale;
    private final double[] acc = new double[CrashFx.EMITTERS.size()];

    private boolean cancelled;
    private boolean soundOn = true, particlesOn = true;
    private CrashFx.Group solo;
    private double lastTau = Double.NaN, lastT = -1, lastCrossZ = Double.NaN;
    private long lastLoop;
    private int next;
    private long tickNo;
    private final int[] dying = new int[256];
    private int aliveEst;

    // statistics
    private long spawned, thinned, cappedTicks, soundsPlayed;
    private int peakAlive;
    private final long[] spawnedByRow = new long[CrashFx.EMITTERS.size()];
    private boolean capLogged;

    public FxPlayer(Moves.Move m, FxOptions opt, double closeTime, double closeEnd, long seed) {
        this.m = m;
        this.opt = opt;
        this.a = FxAnchors.of(m, closeTime, closeEnd);
        this.schedule = expand(a, opt, seed);
        this.rng = new Random(seed * 7919L + 17);
        this.scale = opt.countScale(m.r0);
    }

    public FxAnchors.Anchors anchors() { return a; }
    public List<Play> schedule() { return schedule; }
    public double scale() { return scale; }
    public long spawned() { return spawned; }
    public long spawnedByRow(int row) { return spawnedByRow[row]; }
    public long thinned() { return thinned; }
    public long cappedTicks() { return cappedTicks; }
    public long soundsPlayed() { return soundsPlayed; }
    public int peakAlive() { return peakAlive; }
    public boolean cancelled() { return cancelled; }

    /** Runtime switches (the fx command): sound on/off, particles on/off, and the one sound group that plays (null = all). */
    public void setRuntime(boolean sound, boolean particles, CrashFx.Group solo) {
        this.soundOn = sound;
        this.particlesOn = particles;
        this.solo = solo;
    }

    /** Drops every cue that has not started and all emission, now. Particles already made die by themselves. */
    public void cancel() { cancelled = true; }

    /**
     * The sound plays of a loop: every cue expanded to from + off + i x every, i < rep, sorted by time. Nothing when fx, fxsound is off or fxvol is 0.
     * Cues whose anchor is undefined (NaN) play nothing. The pitch jitter is seeded per (cue, i), so it does not depend on what was cancelled.
     */
    public static List<Play> expand(FxAnchors.Anchors a, FxOptions opt, long seed) {
        List<Play> l = new ArrayList<>();
        if (!opt.on() || !opt.sound() || opt.vol() <= 0) return l;
        for (int c = 0; c < CrashFx.CUES.size(); c++) {
            Cue cue = CrashFx.CUES.get(c);
            double base = a.at(cue.anchor());
            if (Double.isNaN(base)) continue;
            for (int i = 0; i < cue.rep(); i++) {
                Random r = new Random(seed * 1000003L + c * 131L + i);
                double pitch = cue.pitch() * (1 + (r.nextDouble() * 2 - 1) * cue.jit());
                l.add(new Play(base + cue.off() + i * cue.every(), c, cue.vol() * opt.vol(), pitch));
            }
        }
        l.sort((p, q) -> Double.compare(p.time(), q.time()));
        return l;
    }

    /** Number of plays after loop time t (what a cancel at t drops). */
    public static int countAfter(List<Play> plays, double t) {
        int n = 0;
        for (Play p : plays) if (p.time() > t) n++;
        return n;
    }

    /**
     * One game tick. tau = script time of the worm now; externalLive = the real number of live quad particles of the client (0 in a test); the
     * sheet stays under {@link CrashFx#BUDGET} of them by thinning every row evenly.
     */
    public void step(double tau, int externalLive, FxSink sink) {
        if (cancelled) return;
        double cyc = m.cycle;
        double t = tau % cyc;
        if (t < 0) t += cyc;
        long loop = (long) Math.floor(tau / cyc);
        boolean restart = false, seek = false;
        if (Double.isNaN(lastTau)) {
            if (t < 0.5) restart = true; else seek = true;   // a worm met in the middle of its run does not replay the past
        } else {
            double d = tau - lastTau;
            if (d < -1e-9 || d > 1.0) seek = true;
            else if (loop != lastLoop) restart = true;
        }
        lastTau = tau;
        lastLoop = loop;
        tickNo++;
        aliveEst -= dying[(int) (tickNo & 255)];
        dying[(int) (tickNo & 255)] = 0;
        if (restart || seek) {
            java.util.Arrays.fill(acc, 0);
            if (restart) {
                lastT = -1;
                lastCrossZ = Double.NaN;
                next = 0;
            } else {
                lastT = t;
                next = 0;
                while (next < schedule.size() && schedule.get(next).time() <= t) next++;
                lastCrossZ = Double.NaN;
            }
        }
        double a0 = lastT, b0 = t;
        lastT = t;
        if (b0 <= a0) return;   // a frozen clock plays nothing

        while (next < schedule.size() && schedule.get(next).time() <= b0) {
            Play p = schedule.get(next++);
            Cue cue = CrashFx.CUES.get(p.cue());
            if (!soundOn || (solo != null && solo != cue.group())) continue;
            double z = cue.anchor() == Anchor.REENTRY ? a.reentryZ() : a.holeZ();
            soundsPlayed++;
            sink.sound(cue, 0, 0, z, p.volume(), p.pitch());
        }

        if (!opt.on() || !opt.particles() || !particlesOn || scale <= 0) return;
        int E = CrashFx.EMITTERS.size();
        int[] want = new int[E];
        int total = 0;
        for (int i = 0; i < E; i++) {
            Emitter e = CrashFx.EMITTERS.get(i);
            double start = a.at(e.from()) + e.off();
            if (Double.isNaN(start)) continue;
            if (e.isBurst()) {
                if (start > a0 && start <= b0) want[i] = (int) Math.floor(e.burst() * scale + rng.nextDouble());
            } else {
                double end = a.at(e.until());
                if (Double.isNaN(end)) continue;
                double ov = Math.min(b0, end) - Math.max(a0, start);
                if (ov > 0) {
                    acc[i] += e.rate() * scale * ov;
                    int n = (int) Math.floor(acc[i]);
                    acc[i] -= n;
                    want[i] = n;
                }
            }
            total += want[i];
        }
        if (total == 0) return;

        int alive = Math.max(aliveEst, externalLive);
        int room = CrashFx.BUDGET - alive;
        if (total > room) {
            double ratio = Math.max(0, room) / (double) total;
            int before = total;
            total = 0;
            for (int i = 0; i < E; i++) {
                if (want[i] > 0) want[i] = Math.max(1, (int) Math.round(want[i] * ratio));   // every row keeps at least one: thinned evenly, never dropped
                total += want[i];
            }
            thinned += before - total;
            cappedTicks++;
            if (!capLogged) {
                capLogged = true;
                sink.log(String.format(Locale.ROOT, "[WormsOfArrakis] fx: capped %d -> %d particles in one tick at script time %.2fs (alive %d, budget %d, vanilla cap %d); every row thinned by %.0f%%",
                        before, total, t, alive, CrashFx.BUDGET, CrashFx.VANILLA_PARTICLE_CAP, 100 * (1 - ratio)));
            }
        }

        FxGeometry.Snapshot g = FxGeometry.at(m, t);
        if (!Double.isNaN(g.crossZ)) lastCrossZ = g.crossZ;
        double crossZ = Double.isNaN(g.crossZ) ? lastCrossZ : g.crossZ;
        double R = m.r0;
        List<Integer> bodyRings = null;
        for (int i = 0; i < E; i++) {
            Emitter e = CrashFx.EMITTERS.get(i);
            for (int k = 0; k < want[i]; k++) {
                FxSink.Spawn s;
                switch (e.place()) {
                    case CROSSING -> s = crossing(e, i, crossZ, R);
                    case OVERHEAD -> s = overhead(e, i, g, R);
                    case MOUTH -> s = mouth(e, i, g, R);
                    default -> {
                        if (bodyRings == null) bodyRings = FxGeometry.bodyRings(g);
                        s = body(e, i, g, bodyRings, R);
                    }
                }
                if (s == null) continue;
                spawned++;
                spawnedByRow[i]++;
                aliveEst++;
                dying[(int) ((tickNo + s.life()) & 255)]++;
                sink.particle(s);
            }
        }
        peakAlive = Math.max(peakAlive, aliveEst);
    }

    private double gauss(double spread) { return rng.nextGaussian() * spread; }

    private int life(Emitter e) { return e.lifeMin() + (e.lifeMax() > e.lifeMin() ? rng.nextInt(e.lifeMax() - e.lifeMin() + 1) : 0); }

    /** On a horizontal ring of radius ring x r0 around the crossing at the base level; outward x out + up + gaussian spread. */
    private FxSink.Spawn crossing(Emitter e, int row, double crossZ, double R) {
        if (Double.isNaN(crossZ)) return null;   // before the breach there is no crossing
        double th = rng.nextDouble() * 2 * Math.PI, ux = Math.cos(th), uz = Math.sin(th), rad = e.ring() * R;
        return new FxSink.Spawn(e.type(), ux * rad, BASE_PUSH, crossZ + uz * rad, ux * e.out() + gauss(e.spread()), e.up() + gauss(e.spread()), uz * e.out() + gauss(e.spread()),
                life(e), e.size(), e.grav(), e.tint(), row);
    }

    /** A grain stays this far above the sand so it does not start inside the sand block. */
    public static final double BASE_PUSH = 0.05;

    /** Uniform in a disc of radius ring x r0 around the head tip's place on the sand, thinned by proximity (1 - depth / 3 r0); none when the tip is above the sand. */
    private FxSink.Spawn overhead(Emitter e, int row, FxGeometry.Snapshot g, double R) {
        double depth = -g.tipY;
        if (depth <= 0 || depth >= 3 * R) return null;
        double prox = 1 - depth / (3 * R);
        if (rng.nextDouble() > prox) return null;
        double th = rng.nextDouble() * 2 * Math.PI, ux = Math.cos(th), uz = Math.sin(th), rho = e.ring() * R * Math.sqrt(rng.nextDouble());
        return new FxSink.Spawn(e.type(), ux * rho, BASE_PUSH, g.tipZ + uz * rho, ux * e.out() + gauss(e.spread()), e.up() * (0.5 + prox) + gauss(e.spread()), uz * e.out() + gauss(e.spread()),
                life(e), e.size(), e.grav(), e.tint(), row);
    }

    /** Near the head tip, over a disc of radius ring x r0 x 0.5 normal to the head axis; velocity along the axis x out x 3 + up. */
    private FxSink.Spawn mouth(Emitter e, int row, FxGeometry.Snapshot g, double R) {
        double rho = e.ring() * R * 0.5 * Math.sqrt(rng.nextDouble()), ph = rng.nextDouble() * 2 * Math.PI;
        double e1 = Math.cos(ph) * rho, e2 = Math.sin(ph) * rho;   // e1 lateral, e2 along the in-plane normal (y: axZ, z: -axY)
        double vAx = e.out() * 3;
        return new FxSink.Spawn(e.type(), e1, g.tipY + e2 * g.axZ, g.tipZ - e2 * g.axY, gauss(e.spread()), g.axY * vAx + e.up() + gauss(e.spread()), g.axZ * vAx + gauss(e.spread()),
                life(e), e.size(), e.grav(), e.tint(), row);
    }

    /** A random point on the surface of a random body ring that is above the base level (+0.5); radially outward x out. */
    private FxSink.Spawn body(Emitter e, int row, FxGeometry.Snapshot g, List<Integer> rings, double R) {
        if (rings.isEmpty()) return null;
        int k = rings.get(rng.nextInt(rings.size()));
        double[] c = g.joints[k];
        double[] tg = FxGeometry.tangent(g, k);
        double ph = rng.nextDouble() * 2 * Math.PI, dl = Math.cos(ph), dn = Math.sin(ph), rad = e.ring() * R;
        // ring in the plane normal to the tangent (tz, ty): lateral x and the in-plane normal (y: tz, z: -ty)
        double y = c[1] + dn * tg[1 /* tz */] * rad, z = c[2] - dn * tg[0 /* ty */] * rad;
        if (y < 0) return null;   // a grain on the part of the ring that is under the sand
        double ox = dl, oy = dn * tg[1], oz = -dn * tg[0];
        return new FxSink.Spawn(e.type(), dl * rad, y, z, ox * e.out() + gauss(e.spread()), oy * e.out() + e.up() + gauss(e.spread()), oz * e.out() + gauss(e.spread()),
                life(e), e.size(), e.grav(), e.tint(), row);
    }

    private static String t(double v) { return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%.2f", v); }

    /** First chat line of the fx command: the options of the move and the runtime switches. */
    public static String headerLine(String label, double width, FxOptions o, double r0, boolean sound, boolean particles, CrashFx.Group solo) {
        return String.format(Locale.ROOT, "fx of %s W%s: fx=%s fxsound=%s fxparticles=%s fxvol=%s fxcount=%s (count scale %.2f) | runtime: sound %s, particles %s, solo %s",
                label, t(width), o.on() ? "on" : "off", o.sound() ? "on" : "off", o.particles() ? "on" : "off", t(o.vol()), t(o.count()), o.countScale(r0),
                sound ? "on" : "off", particles ? "on" : "off", solo == null ? "all" : solo.label());
    }

    /** Chat lines of the fx command: one per sound group (cue names with their times and repeats), then one per particle row. */
    public static List<String> chatLines(FxAnchors.Anchors an) {
        List<String> l = new ArrayList<>();
        for (CrashFx.Group g : CrashFx.Group.values()) {
            StringBuilder b = new StringBuilder("sounds [").append(g.label()).append("]: ");
            for (Cue c : CrashFx.CUES) if (c.group() == g) b.append(c.name()).append(String.format(Locale.ROOT, " (%.2fs x%d) ", an.at(c.anchor()) + c.off(), c.rep()));
            l.add(b.toString().trim());
        }
        for (Emitter e : CrashFx.EMITTERS)
            l.add(String.format(Locale.ROOT, "particles: %s (%s, %s) from %.2fs%s", e.name(), e.type().id, e.place().name().toLowerCase(Locale.ROOT), an.at(e.from()) + e.off(),
                    e.isBurst() ? ", burst " + e.burst() : ", " + t(e.rate()) + "/s until " + String.format(Locale.ROOT, "%.2fs", an.at(e.until()))));
        return l;
    }

    /** The sheet as text lines for the fx command (cues, then emitters), with the times of this move. */
    public static List<String> sheetLines(FxAnchors.Anchors an) {
        List<String> l = new ArrayList<>();
        for (Cue c : CrashFx.CUES)
            l.add(String.format(Locale.ROOT, "sound [%s] %s: %s at %s%+.2f (%.2fs) x%d%s vol %.1f pitch %.2f", c.group().label(), c.name(), c.event(), c.anchor().label, c.off(), an.at(c.anchor()) + c.off(), c.rep(),
                    c.rep() > 1 ? " every " + c.every() + "s" : "", c.vol(), c.pitch()));
        for (Emitter e : CrashFx.EMITTERS)
            l.add(String.format(Locale.ROOT, "particles %s: %s at %s from %s%+.2f (%.2fs)%s", e.name(), e.type().id, e.place().name().toLowerCase(Locale.ROOT), e.from().label, e.off(), an.at(e.from()) + e.off(),
                    e.isBurst() ? " burst " + e.burst() : " " + e.rate() + "/s until " + e.until().label + " (" + String.format(Locale.ROOT, "%.2fs", an.at(e.until())) + ")"));
        return l;
    }

    // ---- the geometry of the worm at one instant ----

    public static final class FxGeometry {
        private FxGeometry() {}

        /** Tip and axis of the head, the joints, and the first point where the axis (from the tip) passes the base level. Model frame: (y, z). */
        public static final class Snapshot {
            public double tipZ, tipY, axY, axZ, crossZ = Double.NaN;
            public double[][] joints;
        }

        public static Snapshot at(Moves.Move m, double t) {
            double s = m.at(t)[0];
            WormPose p = m.pose(s);
            double[] tip = m.tip(s);
            Snapshot g = new Snapshot();
            g.tipZ = tip[0];
            g.tipY = tip[1];
            g.joints = p.joints;
            double dz = tip[0] - p.joints[0][2], dy = tip[1] - p.joints[0][1], len = Math.hypot(dz, dy);
            if (len < 1e-9) {
                dz = p.joints[0][2] - p.joints[1][2];
                dy = p.joints[0][1] - p.joints[1][1];
                len = Math.hypot(dz, dy);
            }
            g.axZ = dz / len;
            g.axY = dy / len;
            double az = tip[0], ay = tip[1];
            for (int k = 0; k <= p.segments; k++) {
                double bz = p.joints[k][2], by = p.joints[k][1];
                if ((ay > 0) != (by > 0)) {
                    double f = ay / (ay - by);
                    g.crossZ = az + f * (bz - az);
                    break;
                }
                az = bz;
                ay = by;
            }
            return g;
        }

        /** Body rings (joints 1..) whose centre is above the base level + 0.5. */
        static List<Integer> bodyRings(Snapshot g) {
            List<Integer> l = new ArrayList<>();
            for (int k = 1; k < g.joints.length; k++) if (g.joints[k][1] > 0.5) l.add(k);
            return l;
        }

        /** Unit tangent {ty, tz} of the body at joint k. */
        static double[] tangent(Snapshot g, int k) {
            double[] a = g.joints[Math.max(0, k - 1)], b = g.joints[Math.min(g.joints.length - 1, k + 1)];
            double dy = b[1] - a[1], dz = b[2] - a[2], len = Math.hypot(dy, dz);
            return len < 1e-12 ? new double[]{1, 0} : new double[]{dy / len, dz / len};
        }
    }
}
