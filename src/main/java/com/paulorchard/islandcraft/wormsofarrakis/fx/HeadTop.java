package com.paulorchard.islandcraft.wormsofarrakis.fx;

import com.paulorchard.islandcraft.wormsofarrakis.geometry.ChainMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.HeadMouth;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ScaleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ShingleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.WormMath;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;
import com.paulorchard.islandcraft.wormsofarrakis.path.WormPose;

import java.util.HashMap;
import java.util.Map;

/**
 * Prompt 10 part 2: where the head of a crest really is (not just its closed tip). The mandible shells of the mouth are built exactly as the renderer builds them
 * (HeadMouth.mandibles, in the head's local frame, rolled so that the upper jaw is on top) and moved with the frame of the head ring, so the highest point of the
 * head is the highest vertex of the open upper mandible (or the head ring's own rim). The breach of a crest is the instant that point reaches the base level.
 * Pure code. The mesh is built without stagger (delta0 = the uniform roll): the stagger of ring 0 turns the jaw by less than one face, which moves the highest point by less than a block.
 */
public final class HeadTop {
    private HeadTop() {}

    /** The vertices of a mandible shell in the head's local frame. */
    private record Verts(float[] x, float[] y, float[] z) {}

    private static final Map<String, Verts> CACHE = new HashMap<>();

    private static synchronized Verts verts(double width, int n, double r0, double openDeg) {
        int deg = (int) Math.round(Math.max(0, openDeg));
        String key = width + "|" + n + "|" + r0 + "|" + deg;
        Verts v = CACHE.get(key);
        if (v != null) return v;
        if (CACHE.size() > 200) CACHE.clear();
        ShingleMath.Options opt = ShingleMath.Options.defaults(ScaleMath.LIP_LENGTH, true);
        HeadMouth.Mesh mesh = HeadMouth.mandibles(width, n, r0, HeadMouth.roll(n), com.paulorchard.islandcraft.wormsofarrakis.path.HeadSpec.coneHeight(width), deg, opt, false);
        int nv = mesh.shell.quads * 4;
        float[] x = new float[nv], y = new float[nv], z = new float[nv];
        for (int q = 0; q < mesh.shell.quads; q++)
            for (int k = 0; k < 4; k++) {
                int at = q * 32 + k * 8, i = q * 4 + k;
                x[i] = mesh.shell.v[at];
                y[i] = mesh.shell.v[at + 1];
                z[i] = mesh.shell.v[at + 2];
            }
        v = new Verts(x, y, z);
        CACHE.put(key, v);
        return v;
    }

    /** What the head looks like at one instant: the world heights and model z of its points. */
    public record Head(double top, double contactZ) {}

    /**
     * The head at script time t with the mouth open by mouthDeg degrees: {@code top} = the highest point above the base level; {@code contactZ} = the model z of the
     * centre of the head's points within 0.6 blocks of the base level (NaN when none is that close).
     */
    public static Head at(Moves.Move m, double t, double mouthDeg) {
        double s = m.at(t)[0];
        WormPose p = m.poseLimited(s, 2);
        ChainMath ch = p.chain(m.r0);
        double[][] f = ch.frame[0];
        double[] c = ch.center[0];
        int n = WormMath.faceCount(m.width);
        Verts v = verts(m.width, n, m.r0, mouthDeg);
        double top = c[1] + m.r0 * Math.hypot(f[1][0], f[1][1]);   // the rim of the head ring
        double zs = 0, zTop = c[2];
        int near = 0;
        double best = -1e18;
        for (int i = 0; i < v.x.length; i++) {
            double y = c[1] + f[1][0] * v.x[i] + f[1][1] * v.y[i] + f[1][2] * v.z[i];
            if (y > top) top = y;
            if (y > best) {
                best = y;
                zTop = c[2] + f[2][0] * v.x[i] + f[2][1] * v.y[i] + f[2][2] * v.z[i];
            }
            if (Math.abs(y) <= CONTACT) {
                zs += c[2] + f[2][0] * v.x[i] + f[2][1] * v.y[i] + f[2][2] * v.z[i];
                near++;
            }
        }
        return new Head(top, near > 0 ? zs / near : top > 0 ? zTop : Double.NaN);
    }

    /** Head points this close to the base level count as touching the sand (blocks). */
    public static final double CONTACT = 0.6;
}
