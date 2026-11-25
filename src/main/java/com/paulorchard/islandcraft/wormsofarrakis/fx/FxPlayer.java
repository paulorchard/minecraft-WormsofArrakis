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
    /** Prompt 12: the share of the previs numbers for this width (volume, count, non-block size). */
    private final double sizeFactor;
    /** One stream of particles: a row of the sheet at one site. site 0 = the entry crossing (the only one of crash, emerge, engulf), 1 = the exit crossing (crest). */
    public record Lane(int row, boolean eruption, int site, double start, double end, boolean instant, double perSecond, int count, double half) {}

    private final List<Lane> lanes = new ArrayList<>();
    private final double[] acc;
    private final long seed;
    private final Random rngE;
    private final Jets[] jets = new Jets[2];
    private final long[] jetEpochs = {Long.MIN_VALUE, Long.MIN_VALUE};
    private final List<Cue> cues;
    private final double closeTime, closeEnd;
    private double lastEntryZ = Double.NaN, lastExitZ = Double.NaN;

    private boolean cancelled;
    private boolean soundOn = true, particlesOn = true;
    private CrashFx.Group solo;
    private double lastTau = Double.NaN, lastT = -1;
    private int budget = CrashFx.BUDGET;
    private long lastLoop;
    private int next;
    private long tickNo;
    private final int[] dying = new int[256];
    private int aliveEst;

    // statistics
    private long spawned, thinned, cappedTicks, soundsPlayed;
    private int peakAlive;
    private final long[] spawnedByRow = new long[CrashFx.EMITTERS.size() + EruptionFx.ROWS.size()];
    private boolean capLogged;

    public FxPlayer(Moves.Move m, FxOptions opt, double closeTime, double closeEnd, long seed) {
        this.m = m;
        this.opt = opt;
        this.a = FxAnchors.of(m, closeTime, closeEnd);
        this.closeTime = closeTime;
        this.closeEnd = closeEnd;
        this.cues = cuesFor(a);
        this.schedule = expand(a, opt, seed, SizeFactor.of(m.width));
        this.rng = new Random(seed * 7919L + 17);
        this.sizeFactor = SizeFactor.of(m.width);
        this.scale = opt.countScale(m.width);
        this.seed = seed;
        this.rngE = new Random(seed * 104729L + 31);
        buildLanes();
        this.acc = new double[lanes.size()];
    }

    /** The streams of this move: the rows of prompt 09 (adapted for a crest), then the eruption rows. */
    private void buildLanes() {
        boolean crest = a.crest();
        for (int i = 0; i < CrashFx.EMITTERS.size(); i++) {
            Emitter e = CrashFx.EMITTERS.get(i);
            double start = a.at(e.from()) + e.off();
            Anchor until = e.until();
            int site = 0;
            if (crest) {   // prompt 10 part 2: the crest has no hold rows; its fall is the dive into the exit hole; the rise rows go on until the tail is under
                if (e.from() == Anchor.RISE_END && e.until() == Anchor.FALL_START) continue;
                if (e.from() == Anchor.FALL_START) {
                    start = a.reentry() + e.off();
                    until = Anchor.END;
                    site = 1;
                } else if (e.until() == Anchor.RISE_END) until = Anchor.END;
                else if (e.from() == Anchor.REENTRY) site = 1;
                else if (CrashFx.isBaseMask(e)) addLane(i, false, 1, start, a.end(), e.rate(), 0, 0);   // any: every crossing that exists
            }
            if (Double.isNaN(start)) continue;
            if (e.isBurst()) addLane(i, false, site, start, Double.NaN, 0, e.burst(), 0);
            else addLane(i, false, site, start, a.at(until), e.rate(), 0, 0);
        }
        if (!opt.eruption() || Double.isNaN(a.breach())) return;
        for (int j = 0; j < EruptionFx.ROWS.size(); j++) {
            EruptionFx.Row r = EruptionFx.ROWS.get(j);
            double start = a.breach() + r.start();
            int row = CrashFx.EMITTERS.size() + j;
            if (r.isBurst()) addLane(row, true, 0, start, start + r.over(), r.amount() / r.over(), (int) r.amount(), start + r.over() / 2);
            else addLane(row, true, 0, start, a.breach() + EruptionFx.ERUPTION_SUSTAIN, r.amount(), 0, 0);
            if (a.crest() && opt.xeruption() && !Double.isNaN(a.reentry())) {   // the smaller eruption at the exit hole: 40 percent, the sustain rows at most 3 s
                double xs = a.reentry() + r.start(), f = EruptionFx.EXIT_FACTOR;
                if (r.isBurst()) addLane(row, true, 1, xs, xs + r.over(), r.amount() * f / r.over(), (int) Math.round(r.amount() * f), xs + r.over() / 2);
                else addLane(row, true, 1, xs, a.reentry() + EruptionFx.EXIT_SUSTAIN, r.amount() * f, 0, 0);
            }
        }
    }

    private void addLane(int row, boolean eruption, int site, double start, double end, double perSecond, int count, double half) {
        boolean instant = !eruption && Double.isNaN(end) && count > 0;
        if (!instant && (Double.isNaN(end) || end <= start)) return;
        lanes.add(new Lane(row, eruption, site, start, end, instant, perSecond, count, half));
    }

    /** The streams of this player (for tests and the report). */
    public List<Lane> lanes() { return lanes; }

    /** The 14 jets of one 0.6 s epoch; deterministic from the seed and the epoch. */
    public record Jets(double[] angle, double[] strength) {}

    public static Jets jetsFor(long seed, long epoch) {
        Random r = new Random(seed * 1299709L + epoch * 15485863L + 7);
        double[] ang = new double[EruptionFx.ERUPTION_JETS], str = new double[EruptionFx.ERUPTION_JETS];
        for (int i = 0; i < ang.length; i++) {
            ang[i] = r.nextDouble() * 2 * Math.PI;
            str[i] = EruptionFx.JET_STRENGTH_MIN + r.nextDouble() * (EruptionFx.JET_STRENGTH_MAX - EruptionFx.JET_STRENGTH_MIN);
        }
        return new Jets(ang, str);
    }

    /** Index of the jets epoch at loop time t: the jets are re-rolled every {@link EruptionFx#JET_REROLL} seconds from the breach. */
    public static long jetEpoch(double t, double breach) { return (long) Math.floor((t - breach) / EruptionFx.JET_REROLL); }

    /** The jets in use at the last tick (null before the first eruption tick). */
    public Jets currentJets() { return jets[0]; }

    public FxAnchors.Anchors anchors() { return a; }
    public List<Play> schedule() { return schedule; }
    public double scale() { return scale; }
    public double sizeFactor() { return sizeFactor; }
    public long spawned() { return spawned; }
    public long spawnedByRow(int row) { return spawnedByRow[row]; }
    public long thinned() { return thinned; }
    public long cappedTicks() { return cappedTicks; }
    public long soundsPlayed() { return soundsPlayed; }
    public int peakAlive() { return peakAlive; }
    public boolean cancelled() { return cancelled; }

    /** The budget of live particles the sheet keeps under (default {@link CrashFx#BUDGET}); a test or a tuning can change it. */
    public void setBudget(int live) { this.budget = live; }

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
    public static List<Play> expand(FxAnchors.Anchors a, FxOptions opt, long seed) { return expand(a, opt, seed, 1.0); }

    /** As above with the size factor of the worm: every volume is vol x sizeFactor x fxvol (pitch, repeat counts and timing do not change). */
    public static List<Play> expand(FxAnchors.Anchors a, FxOptions opt, long seed, double sizeFactor) {
        List<Play> l = new ArrayList<>();
        if (!opt.on() || !opt.sound() || opt.vol() <= 0) return l;
        List<Cue> cs = cuesFor(a);
        for (int c = 0; c < cs.size(); c++) {
            Cue cue = cs.get(c);
            if (a.crest() && cue.group() == CrashFx.Group.SLIDE) continue;   // the crest has no hold
            double base = a.at(effAnchor(a, cue));
            if (Double.isNaN(base)) continue;
            for (int i = 0; i < cue.rep(); i++) {
                Random r = new Random(seed * 1000003L + c * 131L + i);
                double pitch = cue.pitch() * (1 + (r.nextDouble() * 2 - 1) * cue.jit());
                l.add(new Play(base + cue.off() + i * cue.every(), c, cue.vol() * sizeFactor * opt.vol(), pitch));
            }
        }
        l.sort((p, q) -> Double.compare(p.time(), q.time()));
        return l;
    }

    /** The sound rows of a move: the crash sheet, or the crest sheet of prompt 10 for a crest (its "until end" rows with their computed repeat counts). */
    public static List<Cue> cuesFor(FxAnchors.Anchors a) { return a.crest() ? CrestFx.cues(a) : CrashFx.CUES; }

    /** The sound rows of this player (the index of {@link Play#cue()}). */
    public List<Cue> cues() { return cues; }

    /** The anchor a cue is really fixed to: on a crest the fall (rushing sand) starts at the reentry, the dive into the exit hole. */
    public static Anchor effAnchor(FxAnchors.Anchors a, Cue c) { return a.crest() && c.anchor() == Anchor.FALL_START ? Anchor.REENTRY : c.anchor(); }

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
                lastEntryZ = lastExitZ = Double.NaN;
                next = 0;
            } else {
                lastT = t;
                next = 0;
                while (next < schedule.size() && schedule.get(next).time() <= t) next++;
                lastEntryZ = lastExitZ = Double.NaN;
            }
        }
        double a0 = lastT, b0 = t;
        lastT = t;
        if (b0 <= a0) return;   // a frozen clock plays nothing

        while (next < schedule.size() && schedule.get(next).time() <= b0) {
            Play p = schedule.get(next++);
            Cue cue = cues.get(p.cue());
            if (!soundOn || (solo != null && solo != cue.group())) continue;
            double z = effAnchor(a, cue) == Anchor.REENTRY ? a.reentryZ() : a.holeZ();
            soundsPlayed++;
            sink.sound(cue, 0, 0, z, p.volume(), p.pitch());
        }

        if (!opt.on() || !opt.particles() || !particlesOn || scale <= 0) return;
        FxGeometry.Snapshot g = FxGeometry.at(m, t, a.crest() ? com.paulorchard.islandcraft.wormsofarrakis.path.MouthAnim.angleAt(m, t, com.paulorchard.islandcraft.wormsofarrakis.path.HeadSpec.OPEN_DEGREES, closeTime, closeEnd) : Double.NaN);
        if (!Double.isNaN(g.entryZ)) lastEntryZ = g.entryZ;
        if (!Double.isNaN(g.exitZ)) lastExitZ = g.exitZ;
        boolean[] live = {!Double.isNaN(g.entryZ), !Double.isNaN(g.exitZ)};
        int E = lanes.size();
        int[] want = new int[E];
        boolean[] exempt = new boolean[E];
        int total = 0, exemptTotal = 0;
        for (int i = 0; i < E; i++) {
            Lane ln = lanes.get(i);
            if (ln.instant()) {
                if (ln.start() > a0 && ln.start() <= b0) {
                    int n = (int) Math.floor(ln.count() * scale + rng.nextDouble());
                    if (n == 0 && ln.count() >= 1 && scale > 0 && sizeFactor >= SizeFactor.MIN_FACTOR_FOR_ONE) n = 1;   // a row never rounds to zero (above a factor of 0.1)
                    want[i] = n;
                }
            } else {
                double ov = Math.min(b0, ln.end()) - Math.max(a0, ln.start());
                if (ov > 0) {
                    acc[i] += ln.perSecond() * scale * ov;
                    int n = (int) Math.floor(acc[i]);
                    acc[i] -= n;
                    want[i] = a.crest() && !live[ln.site()] ? 0 : n;   // a crest: a crossing emits only while some part of the body still passes through it
                }
                // the first half of an eruption burst is the part that reads as an explosion: the budget never thins it
                if (ln.eruption() && ln.count() > 0 && (a0 + b0) / 2 <= ln.half()) exempt[i] = true;
            }
            total += want[i];
            if (exempt[i]) exemptTotal += want[i];
        }
        if (total == 0) return;

        int alive = Math.max(aliveEst, externalLive);
        int room = budget - alive;
        if (total > room) {
            int thinnable = total - exemptTotal;
            double ratio = thinnable <= 0 ? 1 : Math.max(0, room - exemptTotal) / (double) thinnable;
            int before = total;
            total = 0;
            for (int i = 0; i < E; i++) {
                if (want[i] > 0 && !exempt[i]) want[i] = Math.max(1, (int) Math.round(want[i] * ratio));   // every row keeps at least one: thinned evenly, never dropped
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

        double[] siteZ = {Double.isNaN(g.entryZ) ? lastEntryZ : g.entryZ, Double.isNaN(g.exitZ) ? lastExitZ : g.exitZ};
        double R = m.r0;
        for (int st = 0; st < 2; st++) {   // the jets of each hole: re-rolled every 0.6 s from the breach (entry) or the reentry (exit)
            long ep = jetEpoch(t, st == 0 ? a.breach() : a.reentry());
            if (jets[st] == null || ep != jetEpochs[st]) {
                jets[st] = jetsFor(seed + st * 977L, ep);
                jetEpochs[st] = ep;
            }
        }
        List<Integer> bodyRings = null;
        for (int i = 0; i < E; i++) {
            Lane ln = lanes.get(i);
            int row = ln.row();
            for (int k = 0; k < want[i]; k++) {
                FxSink.Spawn s;
                if (ln.eruption()) s = erupt(EruptionFx.ROWS.get(row - CrashFx.EMITTERS.size()), row, siteZ[ln.site()], R, jets[ln.site()]);
                else {
                    Emitter e = CrashFx.EMITTERS.get(row);
                    switch (e.place()) {
                        case CROSSING -> s = crossing(e, row, siteZ[ln.site()], R);
                        case OVERHEAD -> s = overhead(e, row, g, R);
                        case MOUTH -> s = mouth(e, row, g, R);
                        default -> {
                            if (bodyRings == null) bodyRings = FxGeometry.bodyRings(g);
                            s = body(e, row, g, bodyRings, R);
                        }
                    }
                }
                if (s == null) continue;
                if (sizeFactor < 1 && s.type() != CrashFx.PType.BLOCK && s.type() != CrashFx.PType.CHUNK) s = s.withSize(s.size() * sizeFactor);   // prompt 12: sand blocks keep their size, the rest follows the worm
                spawned++;
                spawnedByRow[row]++;
                aliveEst++;
                dying[(int) ((tickNo + s.life()) & 255)]++;
                sink.particle(s);
            }
        }
        peakAlive = Math.max(peakAlive, aliveEst);
    }

    private double ur(double lo, double hi) { return lo + rngE.nextDouble() * (hi - lo); }

    /**
     * One particle of an eruption row (prompt 10). ring/jets/curtain: radius (ring + uniform(-radJit, radJit)) x r0 round the crossing, disc: sqrt(uniform)
     * x ring x r0; the height is 0.05 + uniform(0, yJit) (curtain 0.5 + ...). Velocity: the outward unit vector x out + up x uniform(0.75, 1.25) + a gaussian
     * x spread on each axis (the vertical x 0.5); on jets out and up are multiplied by the strength of the jet the particle picked (its angle is the jet's
     * angle plus a gaussian of 0.12 rad).
     */
    private FxSink.Spawn erupt(EruptionFx.Row r, int row, double z0, double R, Jets jets) {
        if (Double.isNaN(z0)) return null;   // no crossing yet
        double th, str = 1, rad;
        switch (r.shape()) {
            case JETS -> {
                int j = rngE.nextInt(EruptionFx.ERUPTION_JETS);
                th = jets.angle()[j] + rngE.nextGaussian() * EruptionFx.JET_ANGLE_SIGMA;
                str = jets.strength()[j];
                rad = (r.ring() + ur(-r.radJit(), r.radJit())) * R;
            }
            case DISC -> {
                th = rngE.nextDouble() * 2 * Math.PI;
                rad = Math.sqrt(rngE.nextDouble()) * r.ring() * R;
            }
            default -> {
                th = rngE.nextDouble() * 2 * Math.PI;
                rad = (r.ring() + ur(-r.radJit(), r.radJit())) * R;
            }
        }
        double ux = Math.cos(th), uz = Math.sin(th);
        double y = (r.shape() == EruptionFx.Shape.CURTAIN ? 0.5 : BASE_PUSH) + rngE.nextDouble() * r.yJit();
        double vx = ux * r.out() * str + rngE.nextGaussian() * r.spread();
        double vy = r.up() * ur(0.75, 1.25) * str + rngE.nextGaussian() * r.spread() * 0.5;
        double vz = uz * r.out() * str + rngE.nextGaussian() * r.spread();
        int life = r.lifeMin() + (r.lifeMax() > r.lifeMin() ? rngE.nextInt(r.lifeMax() - r.lifeMin() + 1) : 0);
        double shade = ur(EruptionFx.SHADE_MIN, EruptionFx.SHADE_MAX);
        return new FxSink.Spawn(r.type(), ux * rad, y, z0 + uz * rad, vx, vy, vz, life, r.size(), r.grav(), r.tint(), row, r.endScale(), r.alpha(), shade, r.drag(), true);
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
        return String.format(Locale.ROOT, "fx of %s W%s: fx=%s fxsound=%s fxparticles=%s fxvol=%s fxcount=%s | size factor=%.2f (W%s: the share of the sheet's sound volume, particle counts and non-block particle sizes; count scale %.2f with fxcount) | runtime: sound %s, particles %s, solo %s",
                label, t(width), o.on() ? "on" : "off", o.sound() ? "on" : "off", o.particles() ? "on" : "off", t(o.vol()), t(o.count()), SizeFactor.of(width), t(width), o.countScale(width),
                sound ? "on" : "off", particles ? "on" : "off", solo == null ? "all" : solo.label());
    }

    /** Chat lines of the fx command: one per sound group (cue names with their times and repeats), then one per particle row. */
    public static List<String> chatLines(FxAnchors.Anchors an) {
        List<String> l = new ArrayList<>();
        for (CrashFx.Group g : CrashFx.Group.values()) {
            StringBuilder b = new StringBuilder("sounds [").append(g.label()).append("]: ");
            for (Cue c : cuesFor(an)) if (c.group() == g) b.append(c.name()).append(String.format(Locale.ROOT, " (%.2fs x%d) ", an.at(c.anchor()) + c.off(), c.rep()));
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
        for (Cue c : cuesFor(an))
            l.add(String.format(Locale.ROOT, "sound [%s] %s: %s at %s%+.2f (%.2fs) x%d%s vol %.1f pitch %.2f", c.group().label(), c.name(), c.event(), c.anchor().label, c.off(), an.at(c.anchor()) + c.off(), c.rep(),
                    c.rep() > 1 ? " every " + c.every() + "s" : "", c.vol(), c.pitch()));
        for (Emitter e : CrashFx.EMITTERS)
            l.add(String.format(Locale.ROOT, "particles %s: %s at %s from %s%+.2f (%.2fs)%s", e.name(), e.type().id, e.place().name().toLowerCase(Locale.ROOT), e.from().label, e.off(), an.at(e.from()) + e.off(),
                    e.isBurst() ? " burst " + e.burst() : " " + e.rate() + "/s until " + e.until().label + " (" + String.format(Locale.ROOT, "%.2fs", an.at(e.until())) + ")"));
        return l;
    }

    /** Prompt 10: one chat line per eruption row (times of this move), and a first line with the constants. */
    public static List<String> eruptionChatLines(FxAnchors.Anchors an) {
        List<String> l = new ArrayList<>();
        l.add(String.format(Locale.ROOT, "eruption: %d rows from the breach (%.2fs), sustain until +%.1fs, %d jets re-rolled every %.1fs", EruptionFx.ROWS.size(), an.breach(), EruptionFx.ERUPTION_SUSTAIN,
                EruptionFx.ERUPTION_JETS, EruptionFx.JET_REROLL));
        for (EruptionFx.Row r : EruptionFx.ROWS)
            l.add(String.format(Locale.ROOT, "eruption: %s (%s, %s) from %.2fs%s", r.name().replace("Eruption: ", ""), r.type().id, r.shape().name().toLowerCase(Locale.ROOT), an.breach() + r.start(),
                    r.isBurst() ? ", burst " + t(r.amount()) + " over " + t(r.over()) + "s" : ", " + t(r.amount()) + "/s until " + String.format(Locale.ROOT, "%.2fs", an.breach() + EruptionFx.ERUPTION_SUSTAIN)));
        return l;
    }

    /** The eruption rows as log lines, every number of the row. */
    public static List<String> eruptionSheetLines(FxAnchors.Anchors an) {
        List<String> l = new ArrayList<>();
        for (EruptionFx.Row r : EruptionFx.ROWS)
            l.add(String.format(Locale.ROOT, "eruption %s: %s %s %s start %.2fs %s ring %s radJit %s yJit %s up %s out %s spread %s grav %s drag %s size %s endScale %s life %d-%d tint #%06X alpha %s", r.name(), r.type().id,
                    r.mode().name().toLowerCase(Locale.ROOT), r.shape().name().toLowerCase(Locale.ROOT), an.breach() + r.start(), r.isBurst() ? "count " + t(r.amount()) + " over " + t(r.over()) + "s" : "rate " + t(r.amount()) + "/s",
                    t(r.ring()), t(r.radJit()), t(r.yJit()), t(r.up()), t(r.out()), t(r.spread()), t(r.grav()), t(r.drag()), t(r.size()), t(r.endScale()), r.lifeMin(), r.lifeMax(), r.tint(), t(r.alpha())));
        return l;
    }

    // ---- the geometry of the worm at one instant ----

    public static final class FxGeometry {
        private FxGeometry() {}

        /** Tip and axis of the head, the joints, and the first point where the axis (from the tip) passes the base level. Model frame: (y, z). */
        public static final class Snapshot {
            public double tipZ, tipY, axY, axZ, crossZ = Double.NaN;
            /** The entry and the exit crossing of the axis with the base level (prompt 10). Crash, emerge, engulf: entryZ = crossZ, no exit. Crest: the entry hole and the exit hole. */
            public double entryZ = Double.NaN, exitZ = Double.NaN;
            public double[][] joints;
        }

        public static Snapshot at(Moves.Move m, double t) { return at(m, t, Double.NaN); }

        /**
         * As above; on a crest before the apex, while no part of the axis crosses the base level yet but the head already touches the sand (the upper mandible is out), the entry
         * crossing is the centre of the head points within 0.6 blocks of the base level (prompt 10 part 2). {@code mouthDeg} = the mouth opening at t (NaN = do not look at the head).
         */
        public static Snapshot at(Moves.Move m, double t, double mouthDeg) {
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
            if (!m.kind.equals("crest")) g.entryZ = g.crossZ;
            else {
                // all crossings from the tip: tip above the sand -> the first is the entry; tip below -> the first is the exit and a second (if the body still passes it) the entry
                double[] zs = new double[2];
                int n = 0;
                double pz = tip[0], py = tip[1];
                for (int k = 0; k <= p.segments && n < 2; k++) {
                    double bz = p.joints[k][2], by = p.joints[k][1];
                    if ((py > 0) != (by > 0)) zs[n++] = pz + py / (py - by) * (bz - pz);
                    pz = bz;
                    py = by;
                }
                if (tip[1] > 0) g.entryZ = n > 0 ? zs[0] : Double.NaN;
                else if (n == 1) g.exitZ = zs[0];
                else if (n == 2) {
                    g.exitZ = zs[0];
                    g.entryZ = zs[1];
                }
                if (Double.isNaN(g.entryZ) && Double.isNaN(g.exitZ) && !Double.isNaN(mouthDeg) && t < m.apexTime()) {
                    HeadTop.Head h = HeadTop.at(m, t, mouthDeg);
                    if (h.top() > 0) g.entryZ = h.contactZ();
                }
            }
            return g;
        }

        /** Body rings (joints 1..) whose centre is above the base level + 0.5. */
        public static List<Integer> bodyRings(Snapshot g) {
            List<Integer> l = new ArrayList<>();
            for (int k = 1; k < g.joints.length; k++) if (g.joints[k][1] > 0.5) l.add(k);
            return l;
        }

        /** Unit tangent {ty, tz} of the body at joint k. */
        public static double[] tangent(Snapshot g, int k) {
            double[] a = g.joints[Math.max(0, k - 1)], b = g.joints[Math.min(g.joints.length - 1, k + 1)];
            double dy = b[1] - a[1], dz = b[2] - a[2], len = Math.hypot(dy, dz);
            return len < 1e-12 ? new double[]{1, 0} : new double[]{dy / len, dz / len};
        }
    }
}
