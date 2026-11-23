package com.paulorchard.islandcraft.wormsofarrakis.geometry;

import java.util.Locale;

/**
 * Experiment 1: generates a Bedrock-format geo model (JSON text) for one ring of n flat faces.
 * Each face is a zero-thickness cube (size [s, 0, s] in 1/16 block units) with only its outward face ("up")
 * given a UV, rotated about the ring axis (parallel to Z through the ring centre) by 360/n degrees times its index.
 * The text is fed to GeckoLib's own loader at runtime; nothing is written to the jar.
 */
public final class RingGeoJson {
    private RingGeoJson() {}

    public static String generate(String identifier, int n, double circumradius) {
        double s = 2.0 * circumradius * Math.sin(Math.PI / n);
        double apo = WormMath.apothem(n, circumradius);
        double cy = circumradius;                     // ring centre height: ring touches the ground
        double px = 16.0;
        StringBuilder b = new StringBuilder(n * 260 + 600);
        b.append("{\"format_version\":\"1.12.0\",\"minecraft:geometry\":[{\"description\":{");
        b.append("\"identifier\":\"").append(identifier).append("\",\"texture_width\":16,\"texture_height\":16,");
        double vb = Math.max(4.0, circumradius * 2 + 2);
        b.append(String.format(Locale.ROOT, "\"visible_bounds_width\":%.3f,\"visible_bounds_height\":%.3f,\"visible_bounds_offset\":[0,%.3f,0]}", vb, vb, vb / 2));
        b.append(",\"bones\":[{\"name\":\"ring\",\"pivot\":[0,").append(num(cy * px)).append(",0],\"cubes\":[");
        for (int i = 0; i < n; i++) {
            if (i > 0) b.append(',');
            double deg = WormMath.faceAngleDegrees(n) * i;
            b.append("{\"origin\":[").append(num(-s * px / 2)).append(',').append(num((cy + apo) * px)).append(',').append(num(-s * px / 2))
                    .append("],\"size\":[").append(num(s * px)).append(",0,").append(num(s * px))
                    .append("],\"pivot\":[0,").append(num(cy * px)).append(",0],\"rotation\":[0,0,").append(num(deg))
                    .append("],\"uv\":{\"up\":{\"uv\":[0,0],\"uv_size\":[16,16]}}}");
        }
        b.append("]}]}]}");
        return b.toString();
    }

    private static String num(double v) { return String.format(Locale.ROOT, "%.5f", v); }
}
