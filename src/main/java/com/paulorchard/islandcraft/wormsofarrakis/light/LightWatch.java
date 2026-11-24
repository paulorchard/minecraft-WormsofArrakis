package com.paulorchard.islandcraft.wormsofarrakis.light;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Prompt 10 part 3: the listener for the black faces. The pure part decides what is "dark", writes the log lines, keeps the last 200 samples of a
 * worm and limits the warnings; the client part ({@code LightClient}) reads the world and feeds it.
 * <pre>
 * not-dark            max(block, sky) of the light the worm has is above 2, or nothing around it is bright either
 * probe-in-solid      the worm's light is 2 or less although a reference point is 8 or more, and the block of the light probe is a full solid block
 * probe-dark-air      the same, the probe block is not solid (air that the light engine has not lit, or a covered spot)
 * reference-also-dark the worm's light is 2 or less and every reference point is dark too (night, a cave): legitimately dark, not a warning
 * </pre>
 */
public final class LightWatch {
    private LightWatch() {}

    public static final int DARK_MAX = 2, BRIGHT_MIN = 8, RING = 200, WARN_EVERY_TICKS = 100;

    public enum Verdict {
        NOT_DARK("ok"), PROBE_IN_SOLID("probe-in-solid"), PROBE_DARK_AIR("probe-dark-air"), REFERENCE_ALSO_DARK("reference-also-dark");
        public final String label;
        Verdict(String label) { this.label = label; }
        public boolean warn() { return this == PROBE_IN_SOLID || this == PROBE_DARK_AIR; }
    }

    /** One reference point of a sample (name, block light, sky light). */
    public record Ref(String name, int block, int sky) {
        public int max() { return Math.max(block, sky); }
    }

    /**
     * One reading of the light of a worm. {@code vanillaBlock/Sky}: what the renderer would use with light=vanilla (the entity's own probe);
     * {@code usedBlock/Sky}: what it uses now (the mode of the move, or the forced value).
     */
    public record Sample(long tick, int worm, String label, String mode, int vanillaBlock, int vanillaSky, int usedBlock, int usedSky, int probeX, int probeY, int probeZ, String probeBlock,
                         boolean probeSolid, String dimension, long dayTime, double skyDarken, double wx, double wy, double wz, double baseY, int exposedRings, List<Ref> refs) {
        public int vanillaMax() { return Math.max(vanillaBlock, vanillaSky); }
        public int bestRef() {
            int b = 0;
            for (Ref r : refs) b = Math.max(b, r.max());
            return b;
        }
        public int packedVanilla() { return LightMath.pack(vanillaBlock, vanillaSky); }
        public int packedUsed() { return LightMath.pack(usedBlock, usedSky); }
    }

    /** What is wrong, judged on the light vanilla would give the worm. */
    public static Verdict classify(Sample s) {
        if (s.vanillaMax() > DARK_MAX) return Verdict.NOT_DARK;
        if (s.bestRef() >= BRIGHT_MIN) return s.probeSolid() ? Verdict.PROBE_IN_SOLID : Verdict.PROBE_DARK_AIR;
        return Verdict.REFERENCE_ALSO_DARK;
    }

    private static String f1(double v) { return String.format(Locale.ROOT, "%.1f", v); }

    /** The numbers of a sample in one line (shared by the INFO, the WARN, the chat and the dump). */
    public static String describe(Sample s) {
        StringBuilder b = new StringBuilder();
        b.append(String.format(Locale.ROOT, "worm %d %s mode=%s vanilla=b%d/s%d used=b%d/s%d probe=(%d,%d,%d) %s solid=%s dim=%s day=%d skyDarken=%.2f pos=(%s,%s,%s) base=%s exposed=%d refs[",
                s.worm(), s.label(), s.mode(), s.vanillaBlock(), s.vanillaSky(), s.usedBlock(), s.usedSky(), s.probeX(), s.probeY(), s.probeZ(), s.probeBlock(), s.probeSolid(), s.dimension(),
                s.dayTime(), s.skyDarken(), f1(s.wx()), f1(s.wy()), f1(s.wz()), f1(s.baseY()), s.exposedRings()));
        for (int i = 0; i < s.refs().size(); i++) {
            Ref r = s.refs().get(i);
            b.append(i > 0 ? " " : "").append(r.name()).append("=b").append(r.block()).append("/s").append(r.sky());
        }
        return b.append(']').toString();
    }

    /** A line for the log: its level and text (the text carries the [WormsOfArrakis] prefix). */
    public record Line(boolean warn, String text) {}

    public enum LogMode {
        ON, OFF, ALL;
        public String label() { return name().toLowerCase(Locale.ROOT); }
        public static LogMode byLabel(String s) {
            for (LogMode m : values()) if (m.label().equals(s)) return m;
            return null;
        }
    }

    /** The memory of one worm: the last 200 samples, the last logged light and the warning rate limit. */
    public static final class Tracker {
        private final ArrayDeque<Sample> ring = new ArrayDeque<>();
        private int lastVanilla = -1, lastUsed = -1;
        private long lastWarn = Long.MIN_VALUE / 2;
        private int suppressed;
        private long totalWarned, totalSuppressed;

        public List<Sample> samples() { return new ArrayList<>(ring); }
        public Sample last() { return ring.peekLast(); }
        public long warned() { return totalWarned; }
        public long suppressedTotal() { return totalSuppressed; }

        /** Takes one sample and returns the lines to write for it under the log mode. */
        public List<Line> accept(Sample s, LogMode mode) {
            ring.addLast(s);
            while (ring.size() > RING) ring.removeFirst();
            List<Line> out = new ArrayList<>();
            Verdict v = classify(s);
            boolean changed = s.packedVanilla() != lastVanilla || s.packedUsed() != lastUsed;
            lastVanilla = s.packedVanilla();
            lastUsed = s.packedUsed();
            if (mode == LogMode.OFF) return out;
            if (v.warn()) {
                if (s.tick() - lastWarn >= WARN_EVERY_TICKS) {
                    lastWarn = s.tick();
                    totalWarned++;
                    out.add(new Line(true, "[WormsOfArrakis] light: DARK verdict=" + v.label + " " + describe(s) + " (" + suppressed + " warnings suppressed since the last one)"));
                    suppressed = 0;
                } else {
                    suppressed++;
                    totalSuppressed++;
                }
            }
            if (mode == LogMode.ALL || changed) out.add(new Line(false, "[WormsOfArrakis] light: " + (v == Verdict.NOT_DARK ? "" : "dark verdict=" + v.label + " ") + describe(s)));
            return out;
        }
    }
}
