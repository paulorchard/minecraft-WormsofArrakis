package com.paulorchard.islandcraft.wormsofarrakis.path;

import com.paulorchard.islandcraft.wormsofarrakis.geometry.ChainMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ScaleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ShingleMath;

/**
 * Builds the shingle skin of a path-driven worm for the drawn segments only (plus one neighbour on each side so the
 * joint at the edge still has its gap data). Pure code.
 */
public final class WormSkin {
    public final WormPose pose;
    public final ChainMath chain;
    public final boolean[] drawn;
    /** Built segment range, or from = 0, to = -1 when nothing is drawn. */
    public final int from, to;
    public final ShingleMath.Result result;

    private WormSkin(WormPose pose, ChainMath chain, boolean[] drawn, int from, int to, ShingleMath.Result result) {
        this.pose = pose;
        this.chain = chain;
        this.drawn = drawn;
        this.from = from;
        this.to = to;
        this.result = result;
    }

    public static WormSkin build(WormPose pose, int n, double r0, SurfaceProvider surface, ShingleMath.Options options) {
        boolean[] drawn = pose.drawn(r0, surface);
        int first = -1, last = -1;
        for (int k = 0; k < drawn.length; k++) if (drawn[k]) { if (first < 0) first = k; last = k; }
        ChainMath chain = pose.chain(r0);
        if (first < 0) return new WormSkin(pose, chain, drawn, 0, -1, new ShingleMath.Result());
        int from = Math.max(0, first - 1), to = Math.min(pose.segments - 1, last + 1);
        return new WormSkin(pose, chain, drawn, from, to, ShingleMath.build(chain, n, r0, 1, options, from, to));
    }

    public static ShingleMath.Options defaultOptions(boolean liner) {
        return ShingleMath.Options.defaults(ScaleMath.LIP_LENGTH, liner);
    }
}
