package com.paulorchard.islandcraft.wormsofarrakis.path;

import com.paulorchard.islandcraft.wormsofarrakis.geometry.ChainMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.HeadMouth;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ScaleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ShingleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.Stagger;

/**
 * Builds the shingle skin of a path-driven worm for the drawn segments only (plus one neighbour on each side so the
 * joint at the edge still has its gap data). Segment 0 is the head ring; when it is built, the three flaps are added on
 * its front edge. Pure code.
 */
public final class WormSkin {
    public final WormPose pose;
    public final ChainMath chain;
    public final boolean[] drawn;
    /** Built segment range, or from = 0, to = -1 when nothing is drawn. */
    public final int from, to;
    public final ShingleMath.Result result;
    /** Quads of the flaps (part of result.quads). */
    public final int flapQuads;

    private WormSkin(WormPose pose, ChainMath chain, boolean[] drawn, int from, int to, ShingleMath.Result result, int flapQuads) {
        this.pose = pose;
        this.chain = chain;
        this.drawn = drawn;
        this.from = from;
        this.to = to;
        this.result = result;
        this.flapQuads = flapQuads;
    }

    public static WormSkin build(WormPose pose, int n, double r0, SurfaceProvider surface, ShingleMath.Options options) {
        return build(pose, n, r0, surface, options, 0.0);
    }

    public static WormSkin build(WormPose pose, int n, double r0, SurfaceProvider surface, ShingleMath.Options options, double mouthOpenDeg) {
        boolean[] drawn = pose.drawn(r0, surface);
        int first = -1, last = -1;
        for (int k = 0; k < drawn.length; k++) if (drawn[k]) { if (first < 0) first = k; last = k; }
        ChainMath chain = pose.chain(r0);
        if (first < 0) return new WormSkin(pose, chain, drawn, 0, -1, new ShingleMath.Result(), 0);
        int from = Math.max(0, first - 1), to = Math.min(pose.segments - 1, last + 1);
        // prompt 08: every ring takes the one uniform roll that puts the upper jaw on top; the mouth itself (shell, throat, teeth) is HeadMouth,
        // built in the head's local frame and cached per angle by the renderer, so the flaps are no longer part of this result
        ShingleMath.Result res = ShingleMath.build(chain, n, r0, 1, options.withRoll(HeadMouth.roll(n)), from, to);
        return new WormSkin(pose, chain, drawn, from, to, res, 0);
    }

    /** True when the head ring (segment 0) is part of this skin, i.e. the mouth is to be drawn. */
    public boolean hasHead() { return to >= from && from == 0; }

    /** The angle of ring vertex 0 of the head ring in its local frame: the ring's own stagger plus the uniform roll. */
    public static double headDelta(ShingleMath.Options options, int n) {
        return Stagger.ringDelta(options.seed(), 0, n, options.stagger()) + HeadMouth.roll(n);
    }

    public static ShingleMath.Options defaultOptions(boolean liner) {
        return ShingleMath.Options.defaults(ScaleMath.LIP_LENGTH, liner);
    }
}
