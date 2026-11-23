package com.paulorchard.islandcraft.wormsofarrakis;

import com.paulorchard.islandcraft.wormsofarrakis.geometry.Frustum;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.RingGeoJson;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.WormMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeometryTest {
    @Test void faceCounts() {
        assertEquals(10, WormMath.faceCount(3));
        assertEquals(50, WormMath.faceCount(16));
        assertEquals(150, WormMath.faceCount(48));
    }

    @Test void circumradiusMatchesPrompt() {
        assertEquals(7.97, WormMath.circumradius(50, 1.0), 0.01);
        assertEquals(23.87, WormMath.circumradius(150, 1.0), 0.01);
    }

    @Test void faceWidthIsExactlyOneBlock() {
        for (int n : new int[]{10, 50, 150}) {
            double r = WormMath.circumradius(n, 1.0);
            assertEquals(1.0, 2 * r * Math.sin(Math.PI / n), 1e-9);
        }
    }

    @Test void ringJsonHasNCubesAndRotationsAreArbitrary() {
        String json = RingGeoJson.generate("geometry.test", 50, WormMath.circumradius(50, 1.0));
        assertEquals(50, json.split("\"rotation\"", -1).length - 1);
        assertTrue(json.contains("\"rotation\":[0,0,7.20000]"), "second face is rotated 7.2 degrees");
    }

    @Test void frustumIsClosedOuterFacingAndOneTilePerQuad() {
        int n = 50;
        Frustum f = Frustum.build(n, 8, 7.2, 0, 2);
        assertEquals(n, f.quads.length);
        for (int i = 0; i < n; i++) {
            float[] q = f.quads[i];
            // winding agrees with the stored outward normal and the normal points away from the axis
            double[] u = {q[5] - q[0], q[6] - q[1], q[7] - q[2]};
            double[] v = {q[15] - q[0], q[16] - q[1], q[17] - q[2]};
            double[] c = {u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]};
            float[] nm = f.normals[i];
            assertTrue(c[0] * nm[0] + c[1] * nm[1] + c[2] * nm[2] > 0);
            double cx = (q[0] + q[5] + q[10] + q[15]) / 4, cy = (q[1] + q[6] + q[11] + q[16]) / 4;
            assertTrue(cx * nm[0] + cy * nm[1] > 0, "normal points away from axis");
            float minU = 9, maxU = -9, minV = 9, maxV = -9;
            for (int k = 0; k < 4; k++) {
                minU = Math.min(minU, q[k * 5 + 3]); maxU = Math.max(maxU, q[k * 5 + 3]);
                minV = Math.min(minV, q[k * 5 + 4]); maxV = Math.max(maxV, q[k * 5 + 4]);
            }
            assertEquals(0, minU); assertEquals(1, maxU); assertEquals(0, minV); assertEquals(1, maxV);
        }
    }

    @Test void frustumNeighbourQuadsShareEdges() {
        int n = 150;
        Frustum f = Frustum.build(n, 23.87, 21.5, 0, 4);
        for (int i = 0; i < n; i++) {
            float[] a = f.quads[i], b = f.quads[(i + 1) % n];
            // a's edge between its "next" front/rear vertices equals b's "this" front/rear vertices (order depends on winding)
            int shared = 0;
            for (int ka = 0; ka < 4; ka++) for (int kb = 0; kb < 4; kb++) if (near(a, ka, b, kb)) shared++;
            assertEquals(2, shared, "adjacent quads share exactly one edge (2 vertices)");
        }
    }

    private static boolean near(float[] a, int ka, float[] b, int kb) {
        return Math.abs(a[ka * 5] - b[kb * 5]) < 1e-4 && Math.abs(a[ka * 5 + 1] - b[kb * 5 + 1]) < 1e-4
                && Math.abs(a[ka * 5 + 2] - b[kb * 5 + 2]) < 1e-4;
    }
}
