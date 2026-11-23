package com.paulorchard.islandcraft.wormsofarrakis;

import com.paulorchard.islandcraft.wormsofarrakis.geometry.BendLimit;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ChainMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ScaleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ShingleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.WormMath;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class BendLimitTest {
    @Test void maxJointAngleTable() {
        assertEquals(1.79, BendLimit.maxJointAngleDegrees(16, 1), 0.01);
        assertEquals(0.60, BendLimit.maxJointAngleDegrees(48, 1), 0.01);
        assertEquals(9.55, BendLimit.maxJointAngleDegrees(3, 1), 0.06);
        assertEquals(32, BendLimit.minRadius(16), 1e-9);
        assertTrue(BendLimit.visibleCurvatureOk(32, 16));
        assertFalse(BendLimit.visibleCurvatureOk(31.9, 16));
        assertTrue(BendLimit.visibleCurvatureOk(Double.POSITIVE_INFINITY, 16));
    }

    @Test void clampNeverExceedsTheLimit() {
        for (double w : new double[]{3, 16, 48}) for (double pitch : new double[]{1, 2, 4}) {
            double max = BendLimit.maxJointAngleDegrees(w, pitch);
            for (double yaw = -30; yaw <= 30; yaw += 3.7) for (double pit = -30; pit <= 30; pit += 4.1) {
                double[] c = BendLimit.clampDegrees(yaw, pit, w, pitch);
                assertTrue(Math.hypot(c[0], c[1]) <= max + 1e-9);
                if (Math.hypot(yaw, pit) <= max) assertArrayEquals(new double[]{yaw, pit}, c, 1e-12);
            }
        }
        assertEquals(32, BendLimit.clampRadius(8, 16), 1e-9);
    }

    /** At exactly the limit radius the prompt 02 coverage rules still hold with one scale per bone (the gap grows with the bone length, so 2 or 4 scales per bone need a gentler radius). */
    @Test void coverageHoldsAtTheLimitRadius() {
        for (int width : new int[]{3, 16, 48}) for (int per : new int[]{1}) for (double[] share : new double[][]{{1, 0}, {0, 1}, {0.7071, 0.7071}}) {
            int n = WormMath.faceCount(width);
            double r = WormMath.circumradius(n, 1.0), len = per;
            double theta = Math.toDegrees(BendLimit.maxJointAngle(width, len));
            int segs = 8;
            double[] yaw = new double[segs], pitch = new double[segs], rad = new double[segs + 1];
            for (int k = 1; k < segs; k++) { yaw[k] = Math.toRadians(theta * share[0]); pitch[k] = Math.toRadians(theta * share[1]); }
            Arrays.fill(rad, r);
            ChainMath c = new ChainMath(segs, len, r, yaw, pitch, rad);
            ShingleMath.Result res = ShingleMath.build(c, n, r, per, ShingleMath.Options.defaults(ScaleMath.LIP_LENGTH, true));
            assertEquals(0, res.uncovered, "width " + width + " per " + per);
            assertEquals(0, res.uncutIntersections, "width " + width + " per " + per);
        }
    }

    @Test void linerSeamsAreHalfAFaceAwayFromPlateEdges() {
        int n = 50;
        double r = WormMath.circumradius(n, 1.0);
        double[] z = new double[3], rad = new double[3];
        Arrays.fill(rad, r);
        ChainMath c = new ChainMath(2, 1, r, z, z, rad);
        ShingleMath.Result res = ShingleMath.build(c, n, r, 1, ShingleMath.Options.defaults(ScaleMath.LIP_LENGTH, true));
        double face = 2 * Math.PI / n;
        boolean sawLiner = false, sawSkin = false;
        for (int q = 0; q < res.quads; q++) {
            int b = q * 32;
            double ang = Math.atan2(res.v[b], res.v[b + 1] - r);   // vertex 0 angle from +Y
            double frac = ((ang / face) % 1 + 1) % 1;
            if (res.kind[q] == ShingleMath.LINER) { sawLiner = true; assertEquals(0.5, frac, 1e-4); }
            if (res.kind[q] == ShingleMath.SKIN) { sawSkin = true; assertTrue(frac < 1e-4 || frac > 1 - 1e-4); }
        }
        assertTrue(sawLiner && sawSkin);
        assertEquals(0xFFC0C0C0, ShingleMath.LINER_TINT);
    }
}
