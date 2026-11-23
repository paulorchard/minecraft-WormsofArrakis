package com.paulorchard.islandcraft.wormsofarrakis;

import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.GeoQuad;
import com.geckolib.cache.model.GeoVertex;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.cache.model.cuboid.GeoCube;
import com.geckolib.loading.definition.geometry.Geometry;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.Frustum;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ScaleGeoJson;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ScaleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.WormMath;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScaleTest {
    private static final ScaleMath.Params TAIL = ScaleMath.Params.defaults(false);
    private static final ScaleMath.Params HEAD = ScaleMath.Params.defaults(true);

    @Test void ownerNumbers() {
        assertEquals(0.0915, TAIL.rise(), 0.0005);
        assertEquals(1.008, TAIL.extent(), 0.001);          // scale pitch measured 1.008
        assertEquals(-0.0400, TAIL.lipEndOffset(), 0.001);  // lip reaches about 0.04 inside the start radius
    }

    @Test void tailwardRampRisesTowardsTailAndLipFacesTail() {
        int n = 50;
        double r0 = WormMath.circumradius(n, 1.0);
        List<Frustum> f = ScaleMath.scale(n, r0, 0, TAIL);
        assertEquals(2, f.size());
        float[] skin = f.get(0).quads[0], lip = f.get(1).quads[0];
        // vertices 0,1 = front ring (z 0), 2,3 = rear ring (z < 0): radius grows towards the tail
        double rFront = Math.hypot(skin[0], skin[1]), rRear = Math.hypot(skin[10], skin[11]);
        assertTrue(skin[2] > skin[12], "rear ring is towards -Z (tail)");
        assertEquals(0.0915, rRear - rFront, 0.002);
        // lip normal has a tail (-Z) component, skin normal is radial
        assertTrue(f.get(1).normals[0][2] < 0, "lip faces the tail");
        assertTrue(Math.abs(f.get(0).normals[0][2]) < 0.1);
        // lip strip UV is 2 px of 16
        float vmax = 0;
        for (int k = 0; k < 4; k++) vmax = Math.max(vmax, lip[k * 5 + 4]);
        assertEquals(0.125f, vmax, 1e-6);
    }

    @Test void headwardMirrorsTheRamp() {
        int n = 50;
        double r0 = WormMath.circumradius(n, 1.0);
        List<Frustum> f = ScaleMath.scale(n, r0, 0, HEAD);
        assertTrue(f.get(1).normals[0][2] > 0, "lip faces the head");
        float[] skin = f.get(0).quads[0];
        double rFront = Math.hypot(skin[0], skin[1]), rRear = Math.hypot(skin[10], skin[11]);
        // low edge is still the "front" ring of the fromRings call, now at the tail side
        assertTrue(skin[2] < skin[12], "low edge towards tail");
        assertEquals(0.0915, rRear - rFront, 0.002);
    }

    @Test void rowQuadCountsAndSawtooth() {
        int n = 50;
        double r0 = WormMath.circumradius(n, 1.0);
        int quads = 0;
        for (Frustum f : ScaleMath.row(n, r0, 8, TAIL)) quads += f.n;
        assertEquals(8 * 2 * n, quads);
    }

    private static BakedGeoModel bake(int n, int count, ScaleMath.Params p) {
        String json = ScaleGeoJson.generate("geometry.arrakis_scale", n, WormMath.circumradius(n, 1.0), count, p);
        return Geometry.GSON.fromJson(json, Geometry.class).bake(Identifier.parse("islandcraft_wormsofarrakis:test_scale"));
    }

    /** Finds the bone of the face at angle 0 (top, pointing up) and returns {front z, rear z, front y, rear y} of the skin quad in model space. */
    private static double[] skinOfTopFace(ScaleMath.Params p) {
        BakedGeoModel m = bake(10, 1, p);
        CuboidGeoBone row = (CuboidGeoBone) m.topLevelBones()[0];
        GeoBone f0 = row.children()[0];
        CuboidGeoBone bone = (CuboidGeoBone) f0;
        GeoCube skin = bone.cubes[0];
        double minZ = 1e9, maxZ = -1e9, yAtMin = 0, yAtMax = 0;
        for (GeoQuad q : skin.quads()) {
            if (q == null) continue;
            for (GeoVertex v : q.vertices()) {
                if (v.posZ() < minZ) { minZ = v.posZ(); yAtMin = v.posY(); }
                if (v.posZ() > maxZ) { maxZ = v.posZ(); yAtMax = v.posY(); }
            }
        }
        System.out.printf("[scale-bake] headward=%s skin z %.4f..%.4f  y at minZ %.4f  y at maxZ %.4f%n", p.headward(), minZ, maxZ, yAtMin, yAtMax);
        return new double[]{minZ, maxZ, yAtMin, yAtMax};
    }

    @Test void bakedCuboidSkinIsRaisedTowardsTheTailWhenTailward() {
        double[] t = skinOfTopFace(TAIL);
        // GeckoLib bakes with the bone/cube transforms still pending (rotation is applied at render time), so this checks the cube's own rotation value
        BakedGeoModel m = bake(10, 1, TAIL);
        CuboidGeoBone f0 = (CuboidGeoBone) ((CuboidGeoBone) m.topLevelBones()[0]).children()[0];
        double rx = f0.cubes[0].rotation().x();
        System.out.println("[scale-bake] skin cube rotation x (rad) = " + rx + " pivot=" + f0.cubes[0].pivot() + " size=" + f0.cubes[0].size());
        assertEquals(2, f0.cubes.length, "skin + lip per angular position per scale");
        // MC convention (Axis.XP): a point at -Z (the tail) rises for positive x rotation
        assertEquals(TAIL.tiltDeg(), Math.toDegrees(rx), 1e-3);
        CuboidGeoBone h0 = (CuboidGeoBone) ((CuboidGeoBone) bake(10, 1, HEAD).topLevelBones()[0]).children()[0];
        assertEquals(-HEAD.tiltDeg(), Math.toDegrees(h0.cubes[0].rotation().x()), 1e-3);
    }
}
