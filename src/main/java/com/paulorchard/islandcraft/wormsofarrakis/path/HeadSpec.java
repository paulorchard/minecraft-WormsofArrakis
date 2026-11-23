package com.paulorchard.islandcraft.wormsofarrakis.path;

import com.paulorchard.islandcraft.wormsofarrakis.geometry.WormMath;

/**
 * The head assembly (mouth), kept in one place so the mouth rework only has to change this:
 * BODY_LENGTH = the rigid body part of the head (4 body segments of 2 blocks), plus the folded flaps which stick out
 * CONE_HEIGHT x ring radius in front of it. headLength(width) = BODY_LENGTH + CONE_HEIGHT x R.
 */
public final class HeadSpec {
    public static final int BODY_SEGMENTS = 4;
    public static final double BODY_SEGMENT_LENGTH = 2.0;
    /** Rigid length of the head body in blocks (the named HEAD_LENGTH of the prompt, without the flaps). */
    public static final double BODY_LENGTH = BODY_SEGMENTS * BODY_SEGMENT_LENGTH;
    /** Closed flap cone height as a fraction of the ring radius. */
    public static final double CONE_HEIGHT = 0.6;

    private HeadSpec() {}

    public static double ringRadius(double width) {
        return WormMath.circumradius(WormMath.faceCount(width), WormMath.FACE_WIDTH);
    }

    /** Whole head length: body plus the closed flaps. */
    public static double headLength(double width) {
        return BODY_LENGTH + CONE_HEIGHT * ringRadius(width);
    }
}
