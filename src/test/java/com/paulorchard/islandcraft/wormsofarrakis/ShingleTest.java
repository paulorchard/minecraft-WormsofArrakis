package com.paulorchard.islandcraft.wormsofarrakis;

import com.paulorchard.islandcraft.wormsofarrakis.geometry.ChainMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ScaleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ShingleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.WormMath;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ShingleTest {
    private static ChainMath chain(int segs, double len, double r, double yawDeg, double pitchDeg) {
        double[] yaw = new double[segs], pitch = new double[segs], rad = new double[segs + 1];
        for (int k = 1; k < segs; k++) { yaw[k] = Math.toRadians(yawDeg); pitch[k] = Math.toRadians(pitchDeg); }
        Arrays.fill(rad, r);
        return new ChainMath(segs, len, r, yaw, pitch, rad);
    }

    private static ShingleMath.Options opts(boolean liner) { return ShingleMath.Options.defaults(ScaleMath.LIP_LENGTH, liner); }

    /** Constant-curvature chain: per-joint angle = segment length / radius of curvature. */
    private static ChainMath curve(int width, int segs, double radiusWidths, int per, double yawShare, double pitchShare) {
        int n = WormMath.faceCount(width);
        double r = WormMath.circumradius(n, 1.0), len = per * ScaleMath.PITCH;
        double theta = Math.toDegrees(len / (radiusWidths * width));
        return chain(segs, len, r, theta * yawShare, theta * pitchShare);
    }

    @Test void straightChainHasNoGapAndBaseLips() {
        int n = 50; double r = WormMath.circumradius(n, 1.0);
        ShingleMath.Result res = ShingleMath.build(chain(12, 1, r, 0, 0), n, r, 1, opts(true));
        for (int j = 1; j < 12; j++) for (int i = 0; i < n; i++) {
            assertEquals(0, res.gap[j][i], 1e-9);
            assertEquals(ScaleMath.LIP_LENGTH, res.lip[j][i], 1e-12);
        }
        assertEquals(0, res.cutVertices);
        System.out.printf("[liner] straight chain width 16 (n=50): inset %.4f (min %.4f), deepest plate edge radius %.4f%n", res.linerInsetUsed, res.linerInsetMin, res.minPlateRadius);
        assertTrue(res.linerInsetUsed > 0.05 && res.linerInsetUsed < 0.2, "tight liner: " + res.linerInsetUsed);
        assertEquals(r - res.minPlateRadius + ShingleMath.LINER_MARGIN, res.linerInsetUsed, 1e-9);
    }

    @Test void pureYawAndPitchGapIsRTanOutsideAndNegativeInside() {
        int n = 50; double r = WormMath.circumradius(n, 1.0);
        for (double deg : new double[]{2, 5, 10, 20}) for (int axis = 0; axis < 2; axis++) {
            ChainMath c = chain(6, 1, r, axis == 0 ? deg : 0, axis == 1 ? deg : 0);
            double[] g = ShingleMath.jointGap(c, 2, n, r);
            int max = 0, min = 0;
            for (int i = 0; i < n; i++) { if (g[i] > g[max]) max = i; if (g[i] < g[min]) min = i; }
            double expect = r * Math.tan(Math.toRadians(deg));
            assertEquals(expect, g[max], 0.08 * expect, "outside gap ~ r tan(angle), deg=" + deg);
            assertEquals(-g[max], g[min], 0.1 * expect, "inside overlap mirrors it");
            int diff = Math.abs(max - min);
            assertTrue(Math.abs(diff - n / 2) <= 1, "outside and inside are opposite, diff=" + diff);
            System.out.printf("[shingle] n=50 %s %4.0f deg: outside gap %.3f (r tan %.3f), inside %.3f%n", axis == 0 ? "yaw  " : "pitch", deg, g[max], expect, g[min]);
        }
    }

    @Test void diagonalBendPeaksWhereTheBendPointsOutward() {
        int n = 150; double r = WormMath.circumradius(n, 1.0);
        ChainMath c = chain(6, 1, r, 8, 8);
        for (int j = 1; j < 5; j++) {
            double[] g = ShingleMath.jointGap(c, j, n, r);
            int max = 0;
            for (int i = 0; i < n; i++) if (g[i] > g[max]) max = i;
            // direction the spine turns to (inside), in the frame of segment j-1
            double[] ax = c.axis(j);
            double[][] f = c.frame[j - 1];
            double mx = f[0][0] * ax[0] + f[1][0] * ax[1] + f[2][0] * ax[2];
            double my = f[0][1] * ax[0] + f[1][1] * ax[1] + f[2][1] * ax[2];
            double inside = Math.atan2(mx, my);
            double outside = inside + Math.PI;
            double got = 2 * Math.PI * max / n;
            double d = Math.abs(((got - outside) % (2 * Math.PI) + 3 * Math.PI) % (2 * Math.PI) - Math.PI);
            assertTrue(d <= 2 * Math.PI / n * 1.5, "joint " + j + ": peak at " + Math.toDegrees(got) + " deg, outside is " + Math.toDegrees(outside));
        }
    }

    @Test void lipsCoverTheGapWhereTheCapAllowsAndTableOfCurves() {
        System.out.println("[shingle] width radiusWidths deg/joint maxGap minGap  lipMax  uncoveredVerts cutVerts uncutIntersect linerInset");
        for (int width : new int[]{16, 48}) {
            int n = WormMath.faceCount(width); double r = WormMath.circumradius(n, 1.0);
            for (double rw : new double[]{8, 4, 2, 1, 0.5}) {
                ChainMath c = curve(width, 8, rw, 1, 1, 0);
                ShingleMath.Result res = ShingleMath.build(c, n, r, 1, opts(true));
                double maxLip = ShingleMath.MAX_LIP_FRACTION * ScaleMath.PITCH, lipMax = 0;
                for (int j = 1; j < 8; j++) for (int i = 0; i < n; i++) {
                    double g = res.gap[j][i], lip = res.lip[j][i];
                    lipMax = Math.max(lipMax, lip);
                    if (g > 0) assertTrue(lip >= Math.min(g + ShingleMath.OVERLAP_MARGIN, maxLip) - 1e-9 && lip >= ScaleMath.LIP_LENGTH - 1e-12);
                    assertTrue(lip <= Math.max(maxLip, ScaleMath.LIP_LENGTH) + 1e-9);
                    assertTrue(lip >= ShingleMath.MIN_LIP - 1e-12);
                }
                // liner inside the plates, with margin
                for (int k = 0; k < 8; k++) assertTrue(Math.max(res.linerRadius[k], res.linerRadius[k + 1]) <= res.deepest[k] - ShingleMath.LINER_MARGIN + 1e-9, "liner pokes out at width " + width + " rw " + rw + " ring " + k);
                System.out.printf("[shingle] %5d %11.1f %8.2f %7.3f %7.3f %7.3f %8d %8d %8d %9.3f%n", width, rw, Math.toDegrees(1.0 / (rw * width)),
                        res.maxGap, res.minGap, lipMax, res.uncovered, res.cutVertices, res.uncutIntersections, res.linerInsetUsed);
            }
        }
    }

    @Test void linerStaysInsideForEveryBendAndPlane() {
        for (int width : new int[]{16, 48}) {
            int n = WormMath.faceCount(width); double r = WormMath.circumradius(n, 1.0);
            for (double deg : new double[]{0, 2, 5, 10, 20}) for (double[] share : new double[][]{{1, 0}, {0, 1}, {0.7071, 0.7071}}) for (int per : new int[]{1, 4}) {
                ChainMath c = chain(8, per, r, deg * share[0], deg * share[1]);
                ShingleMath.Result res = ShingleMath.build(c, n, r, per, opts(true));
                for (int k = 0; k < 8; k++) assertTrue(Math.max(res.linerRadius[k], res.linerRadius[k + 1]) < res.deepest[k], "width " + width + " deg " + deg + " ring " + k);
            }
        }
    }

    @Test void normalsAreConsistentWithWindingAndPointOutwardOrTailward() {
        int n = 50; double r = WormMath.circumradius(n, 1.0);
        ShingleMath.Result res = ShingleMath.build(chain(4, 1, r, 0, 0), n, r, 1, opts(true));
        int[] seen = new int[5];
        for (int q = 0; q < res.quads; q++) {
            float[] v = res.v;
            int b = q * 32;
            double[] a = {v[b + 8] - v[b], v[b + 9] - v[b + 1], v[b + 10] - v[b + 2]}, c = {v[b + 24] - v[b], v[b + 25] - v[b + 1], v[b + 26] - v[b + 2]};
            double[] cr = {a[1] * c[2] - a[2] * c[1], a[2] * c[0] - a[0] * c[2], a[0] * c[1] - a[1] * c[0]};
            double[] nm = {v[b + 5], v[b + 6], v[b + 7]};
            assertTrue(cr[0] * nm[0] + cr[1] * nm[1] + cr[2] * nm[2] > 0, "winding agrees with the stored normal");
            double mx = 0, my = 0;
            for (int k = 0; k < 4; k++) { mx += v[b + k * 8] / 4; my += (v[b + k * 8 + 1] - r) / 4; }
            double ml = Math.hypot(mx, my);
            double radialDot = (nm[0] * mx + nm[1] * my) / ml;
            byte kind = res.kind[q];
            seen[kind]++;
            switch (kind) {
                case ShingleMath.LINER, ShingleMath.SKIN -> assertTrue(radialDot > Math.cos(Math.toRadians(5.25)) - 1e-3, "radially outward, dot " + radialDot);
                case ShingleMath.LIP, ShingleMath.RIM_LIP -> assertTrue(nm[2] < -0.99, "lip faces the tail (-Z), nz " + nm[2]);
                case ShingleMath.RIM_SKIN -> assertTrue(nm[2] > 0.99, "leading rim faces the head, nz " + nm[2]);
                default -> fail();
            }
        }
        System.out.println("[shingle] normals checked on quads: liner=" + seen[0] + " skin=" + seen[1] + " lip=" + seen[2] + " rimSkin=" + seen[3] + " rimLip=" + seen[4]);
    }

    @Test void quadCountsAndBuildTime() {
        System.out.println("[shingle] quads/time: width segs liner plates lips rims total  buildMs(avg of 5, bent 10deg)");
        for (int width : new int[]{16, 48}) for (int segs : new int[]{60, 100}) {
            int n = WormMath.faceCount(width); double r = WormMath.circumradius(n, 1.0);
            ChainMath c = chain(segs, 1, r, 3, 1);
            ShingleMath.Result res = null;
            long best = 0, tot = 0;
            for (int rep = 0; rep < 6; rep++) {
                long t0 = System.nanoTime();
                res = ShingleMath.build(c, n, r, 1, opts(true));
                long dt = System.nanoTime() - t0;
                if (rep > 0) tot += dt;   // first run is warm-up
            }
            assertEquals(segs * n, res.byKind[ShingleMath.LINER]);
            assertEquals(segs * n, res.byKind[ShingleMath.SKIN]);
            System.out.printf("[shingle] %5d %4d %5d %6d %5d %5d %6d  %.2f ms%n", width, segs, res.byKind[0], res.byKind[1], res.byKind[2],
                    res.byKind[3] + res.byKind[4], res.quads, tot / 5 / 1e6);
        }
    }
}
