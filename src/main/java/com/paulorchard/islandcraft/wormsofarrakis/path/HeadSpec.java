package com.paulorchard.islandcraft.wormsofarrakis.path;

import com.paulorchard.islandcraft.wormsofarrakis.geometry.ScaleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.WormMath;

/**
 * The head, kept in one place. Since prompt 04 the head is the lead ring of the chain (segment 0, built exactly like every
 * other ring) with three flaps hinged on its front edge. HEAD_LENGTH = pitch (the head ring) + CONE_HEIGHT x R (the closed
 * flaps), measured from the rear of the head ring to the flap tip.
 */
public final class HeadSpec {
    /** Closed flap cone height as a fraction of the ring radius. */
    public static final double CONE_HEIGHT = 0.6;
    /** Default opening angle of the flaps (degrees), the same as the prompt 01 mouth_open animation. */
    public static final double OPEN_DEGREES = 70.0;
    public static final int FLAPS = 3;

    private HeadSpec() {}

    public static double ringRadius(double width) {
        return WormMath.circumradius(WormMath.faceCount(width), WormMath.FACE_WIDTH);
    }

    /** Height of the closed flap cone in front of the head ring's front edge. */
    public static double coneHeight(double width) {
        return CONE_HEIGHT * ringRadius(width);
    }

    /** HEAD_LENGTH: the head ring plus the closed flaps. */
    public static double headLength(double width) {
        return ScaleMath.PITCH + coneHeight(width);
    }
}
