package com.paulorchard.islandcraft.wormsofarrakis.geometry;

import java.util.Locale;

/**
 * Experiment 2a: a row of scales as a Bedrock geo model of zero-thickness cuboids, 2 per angular position per scale
 * (skin and lip). Each angular position is one bone turned about the worm axis (parallel to Z through the ring centre)
 * by 360/n * i; its cubes are tilted about their own X axis. Faces are rectangles of width s sitting on the apothem
 * (cuboids cannot be trapezoids; at 5.25 degrees the difference is about 0.2 px of tile).
 * Axis convention: the tail is towards -Z (same as {@link ScaleMath}); the tilt sign that raises the tail edge is
 * {@link #TILT_SIGN}, checked by ScaleBakeTest against the baked vertices.
 */
public final class ScaleGeoJson {
    /** Sign of the cube X rotation (Bedrock degrees) that raises the edge towards -Z. Verified by ScaleBakeTest. */
    public static final double TILT_SIGN = -1;

    private ScaleGeoJson() {}

    public static String generate(String identifier, int n, double circumradius, int count, ScaleMath.Params p) {
        double s = 2.0 * circumradius * Math.sin(Math.PI / n);
        double apo = WormMath.apothem(n, circumradius);
        double cy = circumradius;
        double px = 16.0;
        boolean head = p.headward();
        double ext = p.extent();
        // rotation that raises the tail edge, mirrored when the scale is mirrored
        double rot = (head ? -1 : 1) * TILT_SIGN * p.tiltDeg();
        StringBuilder b = new StringBuilder(n * count * 700 + 600);
        b.append("{\"format_version\":\"1.12.0\",\"minecraft:geometry\":[{\"description\":{");
        b.append("\"identifier\":\"").append(identifier).append("\",\"texture_width\":16,\"texture_height\":16,");
        double vb = Math.max(4.0, circumradius * 2 + 2);
        b.append(String.format(Locale.ROOT, "\"visible_bounds_width\":%.3f,\"visible_bounds_height\":%.3f,\"visible_bounds_offset\":[0,%.3f,0]},", vb, vb, vb / 2));
        b.append("\"bones\":[{\"name\":\"row\",\"pivot\":[0,").append(num(cy * px)).append(",0]}");
        for (int i = 0; i < n; i++) {
            b.append(",{\"name\":\"f").append(i).append("\",\"parent\":\"row\",\"pivot\":[0,").append(num(cy * px)).append(",0],\"rotation\":[0,0,")
                    .append(num(WormMath.faceAngleDegrees(n) * i)).append("],\"cubes\":[");
            for (int k = 0; k < count; k++) {
                if (k > 0) b.append(',');
                double u0 = k * ScaleMath.PITCH;
                double y = cy + apo;
                // the low edge of the skin: at u0 (tailward) or at u0 + ext (headward), measured towards the tail
                double lowZ = -(head ? u0 + ext : u0);
                double dir = head ? 1 : -1;                    // z direction from the low edge to the raised edge
                double rearZ = lowZ + dir * p.skinAxial();
                double zMin = Math.min(lowZ, lowZ + dir * p.skinLength());
                b.append("{\"origin\":[").append(num(-s * px / 2)).append(',').append(num(y * px)).append(',').append(num(zMin * px))
                        .append("],\"size\":[").append(num(s * px)).append(",0,").append(num(p.skinLength() * px))
                        .append("],\"pivot\":[0,").append(num(y * px)).append(',').append(num(lowZ * px))
                        .append("],\"rotation\":[").append(num(rot)).append(",0,0],\"uv\":{\"up\":{\"uv\":[0,0],\"uv_size\":[16,16]}}}");
                // lip: a strip hanging inward from the raised edge, facing away from the low edge (north = -Z, south = +Z)
                double yRear = y + p.rise();
                b.append(",{\"origin\":[").append(num(-s * px / 2)).append(',').append(num((yRear - p.lipLength()) * px)).append(',').append(num(rearZ * px))
                        .append("],\"size\":[").append(num(s * px)).append(',').append(num(p.lipLength() * px))
                        .append(",0],\"pivot\":[0,").append(num(yRear * px)).append(',').append(num(rearZ * px))
                        .append("],\"rotation\":[").append(num(rot)).append(",0,0],\"uv\":{\"")
                        .append(head ? "south" : "north").append("\":{\"uv\":[0,0],\"uv_size\":[16,2]}}}");
            }
            b.append("]}");
        }
        b.append("]}]}");
        return b.toString();
    }

    private static String num(double v) {
        return String.format(Locale.ROOT, "%.5f", v);
    }
}
