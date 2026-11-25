package com.paulorchard.islandcraft.wormsofarrakis.hazard;

import java.util.ArrayList;
import java.util.List;

/**
 * Prompt 16 part 2: the real bounding box of an entity in world coordinates (a sneaking player's box is smaller, a baby's too), and the points the contact test samples in it: the 8 corners,
 * the 6 face centres, the centre and a vertical line through the centre every {@link #LINE_STEP} blocks (so a thin shell between the corners and the centre is still found). Engine-free.
 */
public final class Box {
    public static final double LINE_STEP = 0.3;

    public final double minX, minY, minZ, maxX, maxY, maxZ;
    private double[][] samples;

    public Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    /** The box of an entity at (x, y, z) (feet, centre of the base) with a width and a height. */
    public static Box of(double x, double y, double z, double width, double height) {
        return new Box(x - width / 2, y, z - width / 2, x + width / 2, y + height, z + width / 2);
    }

    public double cx() { return 0.5 * (minX + maxX); }
    public double cy() { return 0.5 * (minY + maxY); }
    public double cz() { return 0.5 * (minZ + maxZ); }
    public double height() { return maxY - minY; }

    /** The sample points (computed once). */
    public double[][] samples() {
        if (samples != null) return samples;
        List<double[]> l = new ArrayList<>();
        for (int i = 0; i < 8; i++) l.add(new double[]{(i & 1) == 0 ? minX : maxX, (i & 2) == 0 ? minY : maxY, (i & 4) == 0 ? minZ : maxZ});
        double cx = cx(), cy = cy(), cz = cz();
        l.add(new double[]{minX, cy, cz});
        l.add(new double[]{maxX, cy, cz});
        l.add(new double[]{cx, minY, cz});
        l.add(new double[]{cx, maxY, cz});
        l.add(new double[]{cx, cy, minZ});
        l.add(new double[]{cx, cy, maxZ});
        l.add(new double[]{cx, cy, cz});
        for (double y = minY + LINE_STEP; y < maxY - 1e-9; y += LINE_STEP) l.add(new double[]{cx, y, cz});
        samples = l.toArray(new double[0][]);
        return samples;
    }

    /** The part of the box at or above {@code y}, or null when the whole box is below it (prompt 17: what is under the sand is not touched). */
    public Box above(double y) {
        if (maxY < y) return null;
        return minY >= y ? this : new Box(minX, y, minZ, maxX, maxY, maxZ);
    }

    /** Half the diagonal of the box: every sample is within this of the centre. */
    public double halfDiagonal() {
        double dx = maxX - minX, dy = maxY - minY, dz = maxZ - minZ;
        return 0.5 * Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
