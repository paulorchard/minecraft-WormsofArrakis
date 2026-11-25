package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import com.paulorchard.islandcraft.wormsofarrakis.hunt.StrikeType.Dir;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Prompt 14 part 3: which strike types are available at the victim's place and which one is chosen, engine-free. Crash and emerge are always available; the crest needs one cardinal
 * direction that passes the corridor; the engulf needs the whole disc to be sand. The odds are the config weights rescaled over the available types.
 */
public final class StrikeChoice {
    private StrikeChoice() {}

    /** What is available at a place and why the others are not. */
    public static final class Availability {
        public final List<StrikeType> available = new ArrayList<>();
        public final List<Dir> crestDirs = new ArrayList<>();
        public final Map<Dir, Corridor.Fail> crestFails = new EnumMap<>(Dir.class);
        public Corridor.Fail engulfFail;
        public int reads;
        public boolean unloaded;
        public final Set<StrikeType> skipped = EnumSet.noneOf(StrikeType.class);   // not checked at all: weight 0 for the size (prompt 19)

        public boolean has(StrikeType t) { return available.contains(t); }

        /** The reason a type is not available (one line), or null when it is. */
        public String why(StrikeType t) {
            if (has(t)) return null;
            if (skipped.contains(t)) return "not checked: its weight is 0 for this size";
            if (t == StrikeType.ENGULF) return engulfFail == null ? "not evaluated" : "disc: " + engulfFail.text();
            if (t == StrikeType.CREST) {
                StringBuilder b = new StringBuilder("no direction passes the corridor:");
                for (Map.Entry<Dir, Corridor.Fail> e : crestFails.entrySet()) b.append(' ').append(e.getKey()).append(": ").append(e.getValue().text()).append(';');
                return b.toString();
            }
            return null;
        }
    }

    /** The crest corridor numbers of a size from the config tables, with the side distance r0 of the preset. */
    public static Corridor.Params crestParams(SizeTable.Size size, HuntConfig cfg, int r0) {
        int i = size.ordinal();
        return new Corridor.Params(cfg.crestAhead[i], cfg.crestBehind[i], cfg.crestTolerance[i], r0);
    }

    /** Evaluates every type at the victim's place (feet x, z and the y of the sand block under them). */
    public static Availability evaluate(Corridor.Terrain t, double vx, double vz, int groundY, SizeTable.Size size, HuntConfig cfg, int crestR0, double engulfR0) {
        return evaluate(t, vx, vz, groundY, size, cfg, crestR0, engulfR0, Set.of());
    }

    /** The types with weight 0 for the size, except the forced one: their availability is not read at all (prompt 19). */
    public static Set<StrikeType> skipSet(HuntConfig cfg, SizeTable.Size size, StrikeType forced) {
        Set<StrikeType> s = EnumSet.noneOf(StrikeType.class);
        double[] w = cfg.weightsFor(size);
        for (StrikeType t : StrikeType.values()) if (w[t.ordinal()] <= 0 && t != forced && (t == StrikeType.CREST || t == StrikeType.ENGULF)) s.add(t);   // crash and emerge cost no column reads
        return s;
    }

    /** As above; the types in {@code skip} are not evaluated (no column of theirs is read) and are not available. */
    public static Availability evaluate(Corridor.Terrain t, double vx, double vz, int groundY, SizeTable.Size size, HuntConfig cfg, int crestR0, double engulfR0, Set<StrikeType> skip) {
        Availability a = new Availability();
        a.skipped.addAll(skip);
        Corridor.Params p = crestParams(size, cfg, crestR0);
        for (Dir d : Dir.values()) {
            if (skip.contains(StrikeType.CREST)) break;
            Corridor.Result r = Corridor.crest(t, vx, vz, groundY, d, p);
            a.reads += r.reads();
            if (r.ok()) a.crestDirs.add(d); else { a.crestFails.put(d, r.fail()); if (r.fail().unloaded()) a.unloaded = true; }
        }
        Corridor.Result disc = skip.contains(StrikeType.ENGULF) ? null : Corridor.disc(t, vx, vz, engulfR0);
        if (disc != null) {
            a.reads += disc.reads();
            if (!disc.ok()) { a.engulfFail = disc.fail(); if (disc.fail().unloaded()) a.unloaded = true; }
        }
        if (!a.crestDirs.isEmpty()) a.available.add(StrikeType.CREST);
        a.available.add(StrikeType.CRASH);
        a.available.add(StrikeType.EMERGE);
        if (disc != null && disc.ok()) a.available.add(StrikeType.ENGULF);
        return a;
    }

    /** The chosen type and direction (a crest: the cardinal direction it travels; the others: the yaw direction, or null for a free random yaw). */
    public record Choice(StrikeType type, Dir dir, double yawDeg) {}

    /** A weighted pick among {@code available} by the config weights (rescaled: only the available ones count). */
    public static StrikeType pick(List<StrikeType> available, double[] w, Random rng) {
        double sum = 0;
        for (StrikeType t : available) sum += Math.max(0, w[t.ordinal()]);
        if (sum <= 0) return available.get(rng.nextInt(available.size()));
        double r = rng.nextDouble() * sum;
        for (StrikeType t : available) {
            r -= Math.max(0, w[t.ordinal()]);
            if (r < 0) return t;
        }
        return available.get(available.size() - 1);
    }

    /** The odds in percent of each available type after rescaling. */
    public static Map<StrikeType, Double> odds(List<StrikeType> available, double[] w) {
        Map<StrikeType, Double> m = new EnumMap<>(StrikeType.class);
        double sum = 0;
        for (StrikeType t : available) sum += Math.max(0, w[t.ordinal()]);
        for (StrikeType t : available) m.put(t, sum <= 0 ? 100.0 / available.size() : 100.0 * Math.max(0, w[t.ordinal()]) / sum);
        return m;
    }

    /** Chooses type, direction and yaw. A crest: one of the passing directions at random. The others: a random cardinal direction (StrikeYawMode cardinal) or any yaw (random). */
    public static Choice choose(Availability a, HuntConfig cfg, Random rng, StrikeType forced) {
        return decide(a, cfg, SizeTable.Size.MEDIUM, rng, forced).choice();
    }

    /**
     * The decision of prompt 19: the strike type and the SIZE OF THE STRIKE. A forced type is used whatever its weight (the event's size). Otherwise the weights of the event's size, rescaled over the types
     * that are available and have a weight above 0; when there is none, the fallback of that size (FallbackSize, FallbackType: huge strikes as a medium crash).
     */
    public record Decision(Choice choice, SizeTable.Size strikeSize, boolean fallback, String why) {}

    public static Decision decide(Availability a, HuntConfig cfg, SizeTable.Size size, Random rng, StrikeType forced) {
        double[] w = cfg.weightsFor(size);
        SizeTable.Size strikeSize = size;
        StrikeType t;
        boolean fb = false;
        String why = "";
        if (forced != null) t = forced;
        else {
            List<StrikeType> cand = new ArrayList<>();
            for (StrikeType x : a.available) if (w[x.ordinal()] > 0) cand.add(x);
            if (cand.isEmpty()) {
                fb = true;
                strikeSize = cfg.fallbackSizeFor(size);
                t = cfg.fallbackTypeFor(size);
                StringBuilder b = new StringBuilder();
                for (StrikeType x : StrikeType.values()) if (w[x.ordinal()] > 0 && a.why(x) != null) b.append(b.length() > 0 ? "; " : "").append(x.label).append(": ").append(a.why(x));
                why = b.toString();
            } else t = pick(cand, w, rng);
        }
        Choice c;
        if (t == StrikeType.CREST) {
            Dir d = a.crestDirs.isEmpty() ? Dir.values()[rng.nextInt(4)] : a.crestDirs.get(rng.nextInt(a.crestDirs.size()));   // forced on rock: any direction
            c = new Choice(t, d, d.yaw);
        } else if ("random".equals(cfg.strikeYawMode)) c = new Choice(t, null, rng.nextDouble() * 360.0);
        else {
            Dir d = Dir.values()[rng.nextInt(4)];
            c = new Choice(t, d, d.yaw);
        }
        return new Decision(c, strikeSize, fb, why);
    }

    /** The log line of one choice: {@code strike: size=<name> W<n> available=[...] weights=[...] chosen=<type> dir=<N|E|S|W|->}. */
    public static String line(SizeTable.Size size, Availability a, HuntConfig cfg, Choice c) { return line(size, a, cfg.weightsFor(size), c); }

    public static String line(SizeTable.Size size, Availability a, double[] weights, Choice c) {
        StringBuilder av = new StringBuilder(), w = new StringBuilder();
        for (StrikeType t : a.available) {
            if (av.length() > 0) { av.append(','); w.append(','); }
            av.append(t.label);
            w.append(t.label).append('=').append(trim(weights[t.ordinal()]));
        }
        return String.format(Locale.ROOT, "strike: size=%s W%s available=[%s] weights=[%s] chosen=%s dir=%s", size.label, trim(size.width), av, w, c.type().label, c.dir() == null ? "-" : c.dir().name());
    }

    private static String trim(double v) { return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%.2f", v); }
}
