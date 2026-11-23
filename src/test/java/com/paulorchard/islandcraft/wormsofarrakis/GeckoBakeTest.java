package com.paulorchard.islandcraft.wormsofarrakis;

import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoQuad;
import com.geckolib.cache.model.GeoVertex;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.cache.model.cuboid.GeoCube;
import com.geckolib.loading.definition.geometry.Geometry;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.RingGeoJson;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.WormMath;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Bakes the generated ring JSON with GeckoLib's own loader (headless, no client). */
class GeckoBakeTest {
    private static BakedGeoModel bake(int n) {
        String json = RingGeoJson.generate("geometry.arrakis_ring_" + n, n, WormMath.circumradius(n, 1.0));
        return Geometry.GSON.fromJson(json, Geometry.class).bake(Identifier.parse("islandcraft_wormsofarrakis:test_ring"));
    }

    @Test void ringBakesToNCubesWithOneQuadEachAndArbitraryAngles() {
        for (int n : new int[]{10, 50, 150}) {
            BakedGeoModel m = bake(n);
            assertEquals(1, m.topLevelBones().length);
            CuboidGeoBone bone = (CuboidGeoBone) m.topLevelBones()[0];
            assertEquals(n, bone.cubes.length);
            int quads = 0;
            for (GeoCube c : bone.cubes) for (GeoQuad q : c.quads()) if (q != null) quads++;
            System.out.println("[bake] n=" + n + " cubes=" + bone.cubes.length + " non-null quads=" + quads);
            assertEquals(n, quads, "one outward quad per face");
        }
        CuboidGeoBone b50 = (CuboidGeoBone) bake(50).topLevelBones()[0];
        double zRot = Math.abs(b50.cubes[1].rotation().z());
        System.out.println("[bake] cube 1 rotation z (rad)=" + zRot + " deg=" + Math.toDegrees(zRot));
        assertEquals(7.2, Math.toDegrees(zRot), 1e-3);
    }

    @Test void printWindingAndNormalOfGeckoLibQuad() {
        CuboidGeoBone bone = (CuboidGeoBone) bake(10).topLevelBones()[0];
        GeoCube c = bone.cubes[0];
        for (GeoQuad q : c.quads()) {
            if (q == null) continue;
            GeoVertex[] v = q.vertices();
            double[] u = {v[1].posX() - v[0].posX(), v[1].posY() - v[0].posY(), v[1].posZ() - v[0].posZ()};
            double[] w = {v[3].posX() - v[0].posX(), v[3].posY() - v[0].posY(), v[3].posZ() - v[0].posZ()};
            double[] cr = {u[1] * w[2] - u[2] * w[1], u[2] * w[0] - u[0] * w[2], u[0] * w[1] - u[1] * w[0]};
            System.out.println("[bake] face0 dir=" + q.direction() + " stored normal=(" + q.normalX() + "," + q.normalY() + "," + q.normalZ()
                    + ") (v1-v0)x(v3-v0)=(" + cr[0] + "," + cr[1] + "," + cr[2] + ")");
            for (GeoVertex g : v) System.out.println("[bake]   v " + g.posX() + "," + g.posY() + "," + g.posZ() + " uv " + g.texU() + "," + g.texV());
            System.out.println("[bake]   pivot=" + c.pivot() + " size=" + c.size());
        }
    }
}
