package com.paulorchard.islandcraft.wormsofarrakis.test;

import java.util.Locale;

/**
 * What a test worm should look like, as one string kept in the entity's synched data: "kind;mode;p0;p1;...".
 * No Minecraft classes here, so it can be unit tested.
 * <pre>
 * ring    mode -        p0 width, p1 face-count override (0 = auto)
 * segment mode -        p0 width, p1 length, p2 taper (R2/R1)
 * chain   mode closer   p0 width, p1 segments, p2 yaw deg/joint, p3 pitch deg/joint, p4 segment length, p5 taper per joint
 * wave    mode closer   same as chain; p2 = yaw amplitude deg/joint, p3 = pitch amplitude deg/joint
 * mouth   mode open|close|cycle   p0 width
 * </pre>
 * closer for chain/wave: none (plain bones), lip (overlap lip), loft (code-generated skin).
 */
public record WormSpec(String kind, String mode, double[] p) {

    public static WormSpec of(String kind, String mode, double... p) {
        return new WormSpec(kind, mode, p);
    }

    public double p(int i, double dflt) {
        return i < p.length ? p[i] : dflt;
    }

    public String serialize() {
        StringBuilder b = new StringBuilder(kind).append(';').append(mode);
        for (double v : p) b.append(';').append(String.format(Locale.ROOT, "%s", trim(v)));
        return b.toString();
    }

    public static WormSpec parse(String s) {
        String[] t = s.split(";");
        double[] p = new double[Math.max(0, t.length - 2)];
        for (int i = 2; i < t.length; i++) p[i - 2] = Double.parseDouble(t[i]);
        return new WormSpec(t[0], t.length > 1 ? t[1] : "-", p);
    }

    /** Safe as the path part of an Identifier (lowercase, digits, underscore, dot, dash). */
    public String key() {
        return serialize().replace(';', '_').toLowerCase(Locale.ROOT);
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }
}
