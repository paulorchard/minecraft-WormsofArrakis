package com.paulorchard.islandcraft.wormsofarrakis.geometry;

/**
 * The one place for the visible-bend limit: the part of the worm above the sand may not curve tighter than
 * MIN_BEND_RADIUS_WIDTHS body widths (radius of curvature of the spine). At that radius the telescoped lip is about
 * 0.298 of the pitch and the inside overlap 0.248, so the skin stays closed and nothing intersects (prompt 02 table).
 */
public final class BendLimit {
    public static final double MIN_BEND_RADIUS_WIDTHS = 2.0;
    private static final double EPS = 1e-9;

    private BendLimit() {}

    /** Smallest allowed radius of curvature in blocks. */
    public static double minRadius(double width) {
        return MIN_BEND_RADIUS_WIDTHS * width;
    }

    /** Largest joint angle in radians for segments of the given length (pitch): pitch / (MIN_BEND_RADIUS_WIDTHS * width). */
    public static double maxJointAngle(double width, double pitch) {
        return pitch / minRadius(width);
    }

    public static double maxJointAngleDegrees(double width, double pitch) {
        return Math.toDegrees(maxJointAngle(width, pitch));
    }

    /** True when a visible part of the path with this radius of curvature (blocks; use Double.POSITIVE_INFINITY for straight) is allowed. */
    public static boolean visibleCurvatureOk(double radius, double width) {
        return radius + EPS >= minRadius(width);
    }

    /** True when a visible joint of yaw and pitch (radians, combined as a vector) is within the limit. */
    public static boolean visibleJointOk(double yawRad, double pitchRad, double width, double pitch) {
        return Math.hypot(yawRad, pitchRad) <= maxJointAngle(width, pitch) + EPS;
    }

    /** Scales a (yaw, pitch) pair in degrees so its combined magnitude does not exceed the limit; returns {yaw, pitch}. */
    public static double[] clampDegrees(double yawDeg, double pitchDeg, double width, double pitch) {
        double max = maxJointAngleDegrees(width, pitch), mag = Math.hypot(yawDeg, pitchDeg);
        if (mag <= max || mag == 0) return new double[]{yawDeg, pitchDeg};
        double k = max / mag;
        return new double[]{yawDeg * k, pitchDeg * k};
    }

    /** Radius clamped up to the limit. */
    public static double clampRadius(double radius, double width) {
        return Math.max(radius, minRadius(width));
    }
}
