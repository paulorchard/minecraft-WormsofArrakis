package com.paulorchard.islandcraft.wormsofarrakis.test.client;

import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.GeoQuad;
import com.geckolib.cache.model.GeoVertex;
import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.Frustum;
import com.paulorchard.islandcraft.wormsofarrakis.test.WormSpec;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Experiment 5: a short straight body (4 segments) with three triangle-fan flaps on its front ring.
 * <pre>
 * seg_0 ── seg_1 ── seg_2 ── seg_3
 *   ├─ flap_frame_0 ── flap_0     frame: pivot on the axis, turned about Z by 120 deg * j (set every frame by the renderer)
 *   ├─ flap_frame_1 ── flap_1     flap:  pivot on the hinge chord, animated (rotation X) by the GeckoLib animation
 *   └─ flap_frame_2 ── flap_2
 * </pre>
 * A flap covers 120 degrees of the front ring: a fan of m = round(n/3) triangles from an apex on the axis (h in front of
 * the ring) to the rim. The hinge is the chord between the ends of the flap's rim arc, at distance R/2 from the axis.
 * Closed, the three flaps form a three-petal cone. Each triangle is a quad with its last vertex repeated.
 */
final class MouthModel {
    static final int FLAPS = 3;
    static final int BODY_SEGMENTS = com.paulorchard.islandcraft.wormsofarrakis.path.HeadSpec.BODY_SEGMENTS;
    static final double BODY_SEGMENT_LENGTH = com.paulorchard.islandcraft.wormsofarrakis.path.HeadSpec.BODY_SEGMENT_LENGTH;
    /** Height of the closed cone in front of the ring, as a fraction of the ring radius. */
    static final double CONE_HEIGHT = com.paulorchard.islandcraft.wormsofarrakis.path.HeadSpec.CONE_HEIGHT;

    private MouthModel() {}

    static BakedGeoModel build(WormSpec s, Identifier id, int n, double r) {
        float cy = (float) r;
        LoftBone[] seg = new LoftBone[BODY_SEGMENTS];
        LoftBone[] frame = new LoftBone[FLAPS];
        LoftBone[] flap = new LoftBone[FLAPS];
        for (int k = 0; k < BODY_SEGMENTS; k++) {
            int kids = (k < BODY_SEGMENTS - 1 ? 1 : 0) + (k == 0 ? FLAPS : 0);
            seg[k] = new LoftBone(k == 0 ? null : seg[k - 1], "seg_" + k, new GeoBone[kids], 0f, cy, (float) (-k * BODY_SEGMENT_LENGTH),
                    List.of(Frustum.build(n, r, r, -k * BODY_SEGMENT_LENGTH, -BODY_SEGMENT_LENGTH)), cy);
            if (k > 0) seg[k - 1].children()[0] = seg[k];
        }
        int perFlap = Math.max(3, Math.round(n / (float) FLAPS));
        int triangles = 0;
        for (int j = 0; j < FLAPS; j++) {
            frame[j] = new LoftBone(seg[0], "flap_frame_" + j, new GeoBone[1], 0f, cy, 0f, new GeoQuad[0]);
            GeoQuad[] fan = fan(perFlap, r, cy);
            triangles += fan.length;
            flap[j] = new LoftBone(frame[j], "flap_" + j, new GeoBone[0], 0f, (float) (cy + r / 2), 0f, fan);
            frame[j].children()[0] = flap[j];
            int slot = (BODY_SEGMENTS > 1 ? 1 : 0) + j;
            seg[0].children()[slot] = frame[j];
        }
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] exp5 mouth: faces={} R={} flaps={} triangles/flap={} (quad with repeated vertex) hinge=chord at R/2, bones={}",
                n, String.format("%.3f", r), FLAPS, perFlap, BODY_SEGMENTS + 2 * FLAPS);
        return WormModels.model(id, new GeoBone[]{seg[0]}, r);
    }

    /** Fan of triangles covering the rim arc from -60 to +60 degrees around +Y, apex on the axis in front of the ring. */
    private static GeoQuad[] fan(int m, double r, double cy) {
        double h = r * CONE_HEIGHT;
        List<GeoQuad> out = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            double a0 = Math.toRadians(-60 + 120.0 * i / m), a1 = Math.toRadians(-60 + 120.0 * (i + 1) / m);
            double[] apex = {0, cy, h};
            double[] p0 = {r * Math.sin(a0), cy + r * Math.cos(a0), 0};
            double[] p1 = {r * Math.sin(a1), cy + r * Math.cos(a1), 0};
            // geometric normal of (apex, p0, p1); the outside is away from the axis (towards +Y and forward)
            double[] u = {p0[0] - apex[0], p0[1] - apex[1], p0[2] - apex[2]};
            double[] v = {p1[0] - apex[0], p1[1] - apex[1], p1[2] - apex[2]};
            double[] nrm = {u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]};
            double[] mid = {(apex[0] + p0[0] + p1[0]) / 3, (apex[1] + p0[1] + p1[1]) / 3 - cy, (apex[2] + p0[2] + p1[2]) / 3};
            boolean flip = nrm[0] * mid[0] + nrm[1] * mid[1] < 0;
            double[] a = apex, b = flip ? p1 : p0, c = flip ? p0 : p1;
            float[] ua = {0.5f, 0f}, ub = flip ? new float[]{1f, 1f} : new float[]{0f, 1f}, uc = flip ? new float[]{0f, 1f} : new float[]{1f, 1f};
            if (flip) { nrm[0] = -nrm[0]; nrm[1] = -nrm[1]; nrm[2] = -nrm[2]; }
            double len = Math.sqrt(nrm[0] * nrm[0] + nrm[1] * nrm[1] + nrm[2] * nrm[2]);
            float nx = (float) (nrm[0] / len), ny = (float) (nrm[1] / len), nz = (float) (nrm[2] / len);
            GeoVertex[] verts = {
                    new GeoVertex((float) a[0], (float) a[1], (float) a[2], ua[0], ua[1]),
                    new GeoVertex((float) b[0], (float) b[1], (float) b[2], ub[0], ub[1]),
                    new GeoVertex((float) c[0], (float) c[1], (float) c[2], uc[0], uc[1]),
                    new GeoVertex((float) c[0], (float) c[1], (float) c[2], uc[0], uc[1]),
            };
            out.add(new GeoQuad(verts, nx, ny, nz, Direction.getApproximateNearest(nx, ny, nz)));
        }
        return out.toArray(new GeoQuad[0]);
    }
}
