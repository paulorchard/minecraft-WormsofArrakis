package com.paulorchard.islandcraft.wormsofarrakis.path;

import com.paulorchard.islandcraft.wormsofarrakis.geometry.ChainMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.HeadMouth;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ScaleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ShingleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.Stagger;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.TailMath;

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
    /** Prompt 10: the tapering tail of a crest (empty for every other move and when the tail is off). */
    public final ShingleMath.Result tail;

    private WormSkin(WormPose pose, ChainMath chain, boolean[] drawn, int from, int to, ShingleMath.Result result, int flapQuads, ShingleMath.Result tail) {
        this.pose = pose;
        this.chain = chain;
        this.drawn = drawn;
        this.from = from;
        this.to = to;
        this.result = result;
        this.flapQuads = flapQuads;
        this.tail = tail;
    }

    public static WormSkin build(WormPose pose, int n, double r0, SurfaceProvider surface, ShingleMath.Options options) {
        return build(pose, n, r0, surface, options, 0.0);
    }

    public static WormSkin build(WormPose pose, int n, double r0, SurfaceProvider surface, ShingleMath.Options options, double mouthOpenDeg) {
        return build(pose, n, r0, surface, options, mouthOpenDeg, null, 0);
    }

    public static WormSkin build(WormPose pose, int n, double r0, SurfaceProvider surface, ShingleMath.Options options, double mouthOpenDeg, TailMath.Spec tailSpec, double width) {
        return build(pose, n, r0, surface, options, mouthOpenDeg, tailSpec, width, null);
    }

    /**
     * As above with the tail of a crest (prompt 10): from joint n0 = round(segments x (1 - taillen)) the body is not the shingle skin but the tapering frusta of
     * {@link TailMath} (and the cap); with a null or an off tail it is exactly the old skin.
     */
    /**
     * As above with the sway of prompt 11: {@code offsets[k]} = {x, y, z} added to the centre of joint k of the chain (and of the tail chain), so every ring, the liner and
     * the head (the mouth is placed with chain.center[0]) move rigidly with their ring; null = the old geometry byte for byte. The drawn flags and the gaps do not change.
     */
    public static WormSkin build(WormPose pose, int n, double r0, SurfaceProvider surface, ShingleMath.Options options, double mouthOpenDeg, TailMath.Spec tailSpec, double width, double[][] offsets) {
        boolean[] drawn = pose.drawn(r0, surface);
        int first = -1, last = -1;
        for (int k = 0; k < drawn.length; k++) if (drawn[k]) { if (first < 0) first = k; last = k; }
        ChainMath chain = bodyChain(pose, r0, offsets);
        if (first < 0) return new WormSkin(pose, chain, drawn, 0, -1, new ShingleMath.Result(), 0, new ShingleMath.Result());
        int from = Math.max(0, first - 1), to = Math.min(pose.segments - 1, last + 1);
        // prompt 08: every ring takes the one uniform roll that puts the upper jaw on top; the mouth itself (shell, throat, teeth) is HeadMouth,
        // built in the head's local frame and cached per angle by the renderer, so the flaps are no longer part of this result
        int toBody = to;
        ShingleMath.Result tail = new ShingleMath.Result();
        if (tailSpec != null && tailSpec.on()) {
            int n0 = TailMath.n0(pose.segments, tailSpec.len());
            if (to >= n0) {
                toBody = n0 - 1;
                ChainMath tc = pose.chain(TailMath.radii(pose.segments, r0, tailSpec));
                shift(tc, offsets);
                tail = TailMath.build(tc, pose.segments, width, r0, tailSpec, from, to, options.seed(), options.jitter());
            }
        }
        ShingleMath.Result res = toBody >= from ? ShingleMath.build(chain, n, r0, 1, options.withRoll(HeadMouth.roll(n)), from, toBody) : new ShingleMath.Result();
        return new WormSkin(pose, chain, drawn, from, toBody >= from ? toBody : to, res, 0, tail);
    }

    /** The chain of the body skin: the pose's spine with the uniform ring radius and the sway offsets on every joint (also what the hazard of prompt 16 reads, so there is one pose). */
    public static ChainMath bodyChain(WormPose pose, double r0, double[][] offsets) {
        ChainMath chain = pose.chain(r0);
        shift(chain, offsets);
        return chain;
    }

    private static void shift(ChainMath c, double[][] offsets) {
        if (offsets == null) return;
        for (int k = 0; k < c.center.length && k < offsets.length; k++) for (int i = 0; i < 3; i++) c.center[k][i] += offsets[k][i];
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
