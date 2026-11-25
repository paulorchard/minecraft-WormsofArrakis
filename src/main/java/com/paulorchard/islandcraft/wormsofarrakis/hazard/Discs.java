package com.paulorchard.islandcraft.wormsofarrakis.hazard;

/**
 * Prompt 18 part 1: the two invisible thin discs a worm carries, the only things that act on a player. Both are thin cylinders on the head axis in the head frame of the renderer (origin the head
 * ring centre, +z the direction the worm travels): the throat disc at z = 0 (where the throat funnel begins, its rim) and the mouth disc in front of it at MouthFraction x HEAD_LENGTH, inside the open
 * mouth. Engine-free.
 */
public final class Discs {
    private Discs() {}

    public static final String THROAT = "throat", MOUTH = "mouth";

    /** Every number of the discs (the toml keys ThroatDisc*, MouthDisc*, DiscThickness). */
    public record Spec(boolean throatOn, boolean mouthOn, double throatRadiusFraction, double mouthFraction, double mouthRadiusFraction, double thickness) {
        public static final Spec DEFAULTS = new Spec(true, true, 0.95, 0.5, 0.8, 0.5);
    }

    /** One disc in the head frame: the plane z = {@code z}, the radius, the thickness centred on the plane. */
    public record Disc(String name, double z, double radius, double thickness) {
        /** True when the head-frame point is inside the thin cylinder. */
        public boolean contains(double[] q) { return Math.abs(q[2] - z) <= 0.5 * thickness && Math.hypot(q[0], q[1]) <= radius; }
    }

    public static Disc throat(Spec s, double R) { return new Disc(THROAT, 0, s.throatRadiusFraction() * R, s.thickness()); }

    public static Disc mouth(Spec s, double R, double headLength) { return new Disc(MOUTH, s.mouthFraction() * headLength, s.mouthRadiusFraction() * R, s.thickness()); }
}
