package com.paulorchard.islandcraft.wormsofarrakis.test.client;

import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.GeoLocator;
import com.geckolib.cache.model.ModelProperties;
import com.geckolib.loading.definition.geometry.Geometry;
import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.Frustum;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.RingGeoJson;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ScaleGeoJson;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ScaleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.WormMath;
import com.paulorchard.islandcraft.wormsofarrakis.test.WormSpec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds the baked GeckoLib model of a test worm in code (nothing is read from the jar). */
final class WormModels {
    /** Sleeve radius of the overlap lip (closer "lip"): slightly smaller than the skin so it sits under it. */
    static final double LIP_RADIUS = 0.96;

    private WormModels() {}

    static int faces(WormSpec s) {
        int override = s.kind().equals("ring") ? (int) s.p(1, 0) : 0;
        return override > 0 ? override : WormMath.faceCount(s.p(0, 16));
    }

    static BakedGeoModel build(WormSpec s, Identifier id) {
        int n = faces(s);
        double r = WormMath.circumradius(n, WormMath.FACE_WIDTH);
        switch (s.kind()) {
            case "ring": {
                String json = RingGeoJson.generate("geometry.arrakis_ring_" + n, n, r);
                BakedGeoModel m = Geometry.GSON.fromJson(json, Geometry.class).bake(id);
                WormsOfArrakis.LOGGER.info("[WormsOfArrakis] exp1 ring: faces={} R={} cubes={} (GeckoLib baked Bedrock JSON, {} chars)",
                        n, String.format("%.3f", r), n, json.length());
                return m;
            }
            case "scale": case "scales": {
                int count = s.kind().equals("scale") ? 1 : (int) s.p(1, 8);
                boolean mesh = s.p(2, 0) > 0;
                ScaleMath.Params prm = ScaleMath.Params.defaults(s.mode().equals("headward"));
                if (mesh) {
                    // 2b stand-in: true trapezoid quads in a GeoBone subclass (GeckoMesh does not load on this NeoForge)
                    LoftBone b = new LoftBone(null, "scales", new GeoBone[0], 0, (float) r, 0f, ScaleMath.row(n, r, count, prm), (float) r);
                    WormsOfArrakis.LOGGER.info("[WormsOfArrakis] exp2b scales(mesh quads): dir={} faces={} scales={} quads={} bones=1 cubes=0 tilt={} skin={} lip={}",
                            s.mode(), n, count, b.quadCount(), prm.tiltDeg(), prm.skinLength(), prm.lipLength());
                    return model(id, new GeoBone[]{b}, r);
                }
                String json = ScaleGeoJson.generate("geometry.arrakis_scales", n, r, count, prm);
                BakedGeoModel m = Geometry.GSON.fromJson(json, Geometry.class).bake(id);
                WormsOfArrakis.LOGGER.info("[WormsOfArrakis] exp2a scales(cuboids): dir={} faces={} scales={} cubes={} bones={} tilt={} skin={} lip={}",
                        s.mode(), n, count, 2 * n * count, n + 1, prm.tiltDeg(), prm.skinLength(), prm.lipLength());
                return m;
            }
            case "segment": {
                double len = s.p(1, 2), taper = s.p(2, 1);
                LoftBone b = new LoftBone(null, "segment", new GeoBone[0], 0, (float) r, 0f,
                        List.of(Frustum.build(n, r, r * taper, 0, -len)), (float) r);
                WormsOfArrakis.LOGGER.info("[WormsOfArrakis] exp2 segment: faces={} R1={} R2={} length={} quads={} bones=1 cubes=0",
                        n, String.format("%.3f", r), String.format("%.3f", r * taper), len, b.quadCount());
                return model(id, new GeoBone[]{b}, r);
            }
            case "chain": case "wave": {
                int segs = (int) s.p(1, 12);
                double len = s.p(4, 2), taper = s.p(5, 1);
                boolean lip = s.mode().equals("lip");
                boolean scaleMode = s.mode().equals("scale");
                int per = Math.max(1, (int) s.p(6, 1));
                ScaleMath.Params prm = new ScaleMath.Params(ScaleMath.TILT_DEG, ScaleMath.SKIN_LENGTH, s.p(7, ScaleMath.LIP_LENGTH), false);
                if (scaleMode) { len = per * ScaleMath.PITCH; taper = 1; }
                GeoBone[] top = new GeoBone[1];
                LoftBone parent = null;
                LoftBone[] all = new LoftBone[segs];
                for (int k = 0; k < segs; k++) {
                    double r1 = r * Math.pow(taper, k), r2 = r * Math.pow(taper, k + 1);
                    List<Frustum> parts = new java.util.ArrayList<>();
                    if (scaleMode) {
                        for (int j = 0; j < per; j++) parts.addAll(ScaleMath.scale(n, r, (k * per + j) * ScaleMath.PITCH, prm));
                    } else parts.add(Frustum.build(n, r1, r2, -k * len, -len));
                    if (lip && k > 0) {
                        double l = Math.min(len * 0.5, r1 * 0.5);
                        // sleeve across the joint, half inside the previous segment and half inside this one
                        parts.add(Frustum.build(n, r1 * LIP_RADIUS, r1 * LIP_RADIUS, -k * len + l, -2 * l));
                    }
                    GeoBone[] kids = new GeoBone[k < segs - 1 ? 1 : 0];
                    all[k] = new LoftBone(parent, "seg_" + k, kids, 0f, (float) r, (float) (-k * len), parts, (float) r);
                    if (parent != null) parent.children()[0] = all[k];
                    else top[0] = all[k];
                    parent = all[k];
                }
                int quads = 0;
                for (LoftBone b : all) quads += b.quadCount();
                WormsOfArrakis.LOGGER.info("[WormsOfArrakis] exp4 {}: faces={} segments={} seglen={} closer={} scalesPerBone={} lipLength={} bones={} quads={}",
                        s.kind(), n, segs, len, s.mode(), scaleMode ? per : 0, prm.lipLength(), segs, quads);
                return model(id, top, r);
            }
            case "mouth": case "move":
                return MouthModel.build(s, id, n, r);
            default:
                throw new IllegalArgumentException("unknown worm kind " + s.kind());
        }
    }

    static BakedGeoModel model(Identifier id, GeoBone[] top, double r) {
        float vb = (float) Math.max(4, r * 2 + 2);
        ModelProperties props = new ModelProperties(id, "arrakis_worm", vb, vb, new Vec3(0, vb / 2, 0), 16, 16);
        return new BakedGeoModel(top, new HashMap<String, GeoLocator>(), props);
    }

    private static final Map<String, BakedGeoModel> CACHE = new HashMap<>();

    static synchronized BakedGeoModel get(Identifier id, WormSpec spec) {
        return CACHE.computeIfAbsent(spec.serialize(), k -> build(spec, id));
    }

    static synchronized void clearCache() {
        CACHE.clear();
    }
}
