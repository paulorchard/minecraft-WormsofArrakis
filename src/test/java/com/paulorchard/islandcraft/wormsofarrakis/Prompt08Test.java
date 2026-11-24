package com.paulorchard.islandcraft.wormsofarrakis;

import com.paulorchard.islandcraft.wormsofarrakis.geometry.ChainMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.HeadFlaps;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.HeadMouth;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ScaleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ShingleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.Stagger;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.WormMath;
import com.paulorchard.islandcraft.wormsofarrakis.path.HeadSpec;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;
import com.paulorchard.islandcraft.wormsofarrakis.path.MouthAnim;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveFactory;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveOptions;
import com.paulorchard.islandcraft.wormsofarrakis.test.WormSpec;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Prompt 08: the head geometry (part 1), the teeth (part 2) and the mouth animation (part 3). */
class Prompt08Test {
    private static final double[] WIDTHS = {3, 16, 48};
    private static final double DEG_CONE = HeadSpec.CONE_HEIGHT;

    private static int nOf(double w) { return WormMath.faceCount(w); }

    private static double rOf(double w) { return HeadSpec.ringRadius(w); }

    private static ShingleMath.Options opts() { return ShingleMath.Options.defaults(ScaleMath.LIP_LENGTH, true); }

    private static HeadMouth.Mesh mand(double w, double openDeg, boolean teeth) {
        int n = nOf(w);
        return HeadMouth.mandibles(w, n, rOf(w), HeadMouth.roll(n), HeadSpec.coneHeight(w), openDeg, opts(), teeth);
    }

    private static HeadMouth.Mesh thr(double w, boolean teeth) {
        int n = nOf(w);
        return HeadMouth.throat(w, n, rOf(w), HeadMouth.roll(n), teeth);
    }

    /** The distinct corner points of quad q (a triangle is stored with its last vertex repeated). */
    private static java.util.List<double[]> poly(ShingleMath.Result r, int q) {
        java.util.List<double[]> out = new java.util.ArrayList<>();
        for (int k = 0; k < 4; k++) {
            int at = q * 32 + k * 8;
            double[] p = {r.v[at], r.v[at + 1], r.v[at + 2], r.v[at + 3], r.v[at + 4]};
            if (!out.isEmpty()) {
                double[] l = out.get(out.size() - 1);
                if (Math.abs(l[0] - p[0]) < 1e-9 && Math.abs(l[1] - p[1]) < 1e-9 && Math.abs(l[2] - p[2]) < 1e-9) continue;
            }
            out.add(p);
        }
        if (out.size() > 1) {
            double[] a = out.get(0), b = out.get(out.size() - 1);
            if (Math.abs(a[0] - b[0]) < 1e-9 && Math.abs(a[1] - b[1]) < 1e-9 && Math.abs(a[2] - b[2]) < 1e-9) out.remove(out.size() - 1);
        }
        return out;
    }

    private static double area3(java.util.List<double[]> p) {
        double[] s = new double[3];
        for (int i = 1; i + 1 < p.size(); i++) {
            double[] a = {p.get(i)[0] - p.get(0)[0], p.get(i)[1] - p.get(0)[1], p.get(i)[2] - p.get(0)[2]};
            double[] b = {p.get(i + 1)[0] - p.get(0)[0], p.get(i + 1)[1] - p.get(0)[1], p.get(i + 1)[2] - p.get(0)[2]};
            s[0] += a[1] * b[2] - a[2] * b[1];
            s[1] += a[2] * b[0] - a[0] * b[2];
            s[2] += a[0] * b[1] - a[1] * b[0];
        }
        return 0.5 * Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]);
    }

    private static double areaUv(java.util.List<double[]> p) {
        double s = 0;
        for (int i = 1; i + 1 < p.size(); i++) {
            double ax = p.get(i)[3] - p.get(0)[3], ay = p.get(i)[4] - p.get(0)[4], bx = p.get(i + 1)[3] - p.get(0)[3], by = p.get(i + 1)[4] - p.get(0)[4];
            s += ax * by - ay * bx;
        }
        return 0.5 * Math.abs(s);
    }

    private static String key(double[] p) { return Math.round(p[0] * 1e5) + "," + Math.round(p[1] * 1e5) + "," + Math.round(p[2] * 1e5); }

    // ================================================================================================== Part 1

    @Test void headLengthAndOpenAngle() {
        double[] expect = {2.62, 8.96, 24.9}, old = {1.97, 5.78, 15.3};
        for (int i = 0; i < 3; i++) {
            double w = WIDTHS[i];
            System.out.printf(Locale.ROOT, "[head2] HEAD_LENGTH W%.0f: old %.2f -> new %.2f (cone %.2f blocks)%n", w, old[i], HeadSpec.headLength(w), HeadSpec.coneHeight(w));
            assertEquals(expect[i], HeadSpec.headLength(w), 0.05);
            assertEquals(ScaleMath.PITCH + 1.0 * rOf(w), HeadSpec.headLength(w), 1e-12);
        }
        assertEquals(45.0, HeadSpec.OPEN_DEGREES, 0);
        assertEquals(1.0, HeadSpec.CONE_HEIGHT, 0);
        // everything that uses HEAD_LENGTH follows: tip height at the hold, segments needed (engulf, exposed 2) and the emerge tip height
        for (double w : WIDTHS) {
            Moves.Move e = Moves.engulf(w, 80, 2, 7, 0.5, 12, 12, 1.0, false);
            double h = (2 + HeadSpec.headLength(w)) * Math.sin(Math.toRadians(83));
            assertEquals(h, e.h0, 1e-6, "engulf tip height at the hold follows HEAD_LENGTH");
            assertEquals(-(HeadSpec.headLength(w) + 1), e.tip(e.sHold - e.dropEnd / e.dropAngleSin)[1], 1e-6, "the drop ends HEAD_LENGTH + 1 under the sand");
            System.out.printf(Locale.ROOT, "[head2] engulf W%.0f exposed 2: tip at the hold %.2f (was %.2f), segments needed %d, rise %.2f s, drop %.2f s%n", w, e.h0,
                    (2 + (ScaleMath.PITCH + 0.6 * rOf(w))) * Math.sin(Math.toRadians(83)), e.segmentsNeeded, e.advanceTime, e.fallTime);
        }
    }

    @Test void theUpperJawIsOnTopWithOneUniformRoll() {
        for (double w : new double[]{3, 4, 5, 6, 7, 10, 16, 48}) {
            int n = nOf(w);
            double R = rOf(w), spacing = 2 * R * Math.sin(Math.PI / n);
            double roll = HeadMouth.roll(n);
            assertTrue(roll <= 0, "the roll is negative: " + roll);
            assertEquals(-(HeadFlaps.split(n)[0] / 2.0) * 2 * Math.PI / n, roll, 1e-12);
            HeadMouth.Mesh m = HeadMouth.mandibles(w, n, R, roll, HeadSpec.coneHeight(w), 0, opts(), false);
            double[][] cen = new double[3][2];
            for (int j = 0; j < 3; j++) {
                for (double[] p : m.arcOuter[j]) { cen[j][0] += p[0] / m.arcOuter[j].length; cen[j][1] += p[1] / m.arcOuter[j].length; }
            }
            System.out.printf(Locale.ROOT, "[head2] upper jaw W%.0f n=%d (%s by 3): roll %.4f rad, centroid of the larger flap x=%.4f y=%.4f (R=%.3f, vertex spacing %.3f); the smaller flaps y=%.3f / %.3f%n",
                    w, n, n % 3 == 0 ? "divisible" : "not divisible", roll, cen[0][0], cen[0][1], R, spacing, cen[1][1], cen[2][1]);
            assertTrue(cen[0][1] > 0, "the centroid of the larger flap is on +y");
            assertEquals(0, cen[0][0], spacing, "within one vertex spacing of the +y axis");
            assertTrue(cen[1][1] < 0 && cen[2][1] < 0, "the two smaller flaps are at the bottom");
            // without the roll the larger flap is NOT on top (the roll is what puts it there)
            HeadMouth.Mesh m0 = HeadMouth.mandibles(w, n, R, 0, HeadSpec.coneHeight(w), 0, opts(), false);
            double x0 = 0; for (double[] p : m0.arcOuter[0]) x0 += p[0] / m0.arcOuter[0].length;
            assertTrue(Math.abs(x0) > 0.2 * R, "unrolled, the larger flap sits to the side");
        }
    }

    @Test void theRollTurnsEveryRingAndStaggerStillWorks() {
        double w = 16;
        int n = nOf(w);
        double R = rOf(w);
        ChainMath c = new ChainMath(4, 1.0, R, new double[4], new double[4], new double[]{R, R, R, R, R});
        ShingleMath.Options o = opts().withVariety(0.5, 11, true, 0.0).withRoll(HeadMouth.roll(n));
        ShingleMath.Options o0 = opts().withVariety(0.5, 11, true, 0.0);
        ShingleMath.Result a = ShingleMath.build(c, n, R, 1, o), b = ShingleMath.build(c, n, R, 1, o0);
        for (int k = 0; k < 4; k++) assertEquals(b.ringDelta[k] + HeadMouth.roll(n), a.ringDelta[k], 1e-12, "uniform roll added to the stagger of ring " + k);
        assertEquals(b.offsetPx[1], a.offsetPx[1], "the texture offset is untouched");
        assertEquals(b.quads, a.quads);
    }

    @Test void seamVerticesOfRingAndFlapsCoincide() {
        for (double w : WIDTHS) {
            int n = nOf(w);
            double R = rOf(w);
            ChainMath c = new ChainMath(3, 1.0, R, new double[3], new double[3], new double[]{R, R, R, R});
            ShingleMath.Options o = opts().withVariety(0.0, 5, false, 0.0).withRoll(HeadMouth.roll(n));
            ShingleMath.Result ring = ShingleMath.build(c, n, R, 1, o, 0, 1);
            double delta0 = ring.ringDelta[0];
            for (double open : new double[]{0, 22.5, 45}) {
                HeadMouth.Mesh m = HeadMouth.mandibles(w, n, R, delta0, HeadSpec.coneHeight(w), open, o, false);
                java.util.List<double[]> front = new java.util.ArrayList<>();
                for (int q = 0; q < ring.quads; q++) {
                    if (ring.kind[q] != ShingleMath.SKIN) continue;
                    for (double[] p : poly(ring, q)) if (Math.abs(p[2] - c.center[0][2]) < 1e-5) front.add(new double[]{p[0] - c.center[0][0], p[1] - c.center[0][1], 0});
                }
                int checked = 0;
                for (int j = 0; j < 3; j++) for (double[] p : m.arcOuter[j]) {
                    assertTrue(front.stream().anyMatch(f -> dist(f, new double[]{p[0], p[1], 0}) < 1e-4), "flap hinge vertex lies on the ring front vertex within 1e-4, W" + w + " open " + open);
                    assertEquals(0, p[2], 1e-9);
                    checked++;
                }
                assertTrue(checked > n);
            }
        }
    }

    @Test void unrolledUvKeepsTheTileScaleAndDoesNotSwim() {
        for (double w : WIDTHS) {
            HeadMouth.Mesh closed = mand(w, 0, false);
            double worst = 0, best = 1e9;
            int tris = 0;
            for (int q = 0; q < closed.shell.quads; q++) {
                if (closed.shell.kind[q] != HeadMouth.K_OUTER && closed.shell.kind[q] != HeadMouth.K_INNER) continue;
                if (closed.shell.kind[q] == HeadMouth.K_INNER) continue;
                double ratio = areaUv(poly(closed.shell, q)) / area3(poly(closed.shell, q));
                worst = Math.max(worst, ratio);
                best = Math.min(best, ratio);
                tris++;
            }
            System.out.printf(Locale.ROOT, "[head2] UV area / 3D area, closed, W%.0f: %d triangles, ratio %.4f .. %.4f%n", w, tris, best, worst);
            assertEquals(nOf(w), tris);
            assertTrue(best >= 0.9 && worst <= 1.1, "uv area ratio within 0.9..1.1");
            // the UVs stay fixed while the mouth moves
            HeadMouth.Mesh open = mand(w, 45, false);
            int a = 0, b = 0;
            for (int q = 0; q < closed.shell.quads && q < open.shell.quads; q++) {
                if (closed.shell.kind[q] != HeadMouth.K_OUTER) continue;
                var pc = poly(closed.shell, q);
                var po = poly(open.shell, q);
                for (int i = 0; i < pc.size(); i++) { assertEquals(pc.get(i)[3], po.get(i)[3], 1e-6); assertEquals(pc.get(i)[4], po.get(i)[4], 1e-6); }
                a++;
            }
            assertEquals(nOf(w), a);
        }
    }

    @Test void shellThickness() {
        double[][] expect = {{3, 0.02}, {6, 0.05}, {16, 0.05 + 10 * 0.45 / 42}, {48, 0.05 + 42 * 0.45 / 42}, {96, 0.05 + 90 * 0.45 / 42}};
        for (double[] e : expect) {
            assertEquals(e[1], HeadMouth.thickness(e[0]), 0.005, "thickness W" + e[0]);
            System.out.printf(Locale.ROOT, "[head2] shell thickness W%.0f: %.4f blocks%n", e[0], HeadMouth.thickness(e[0]));
        }
        assertEquals(0.02, HeadMouth.thickness(3), 1e-12, "the floor");
        assertEquals(1.0, HeadMouth.thickness(96), 0.02, "W96 is about 1.0");
        assertEquals(1.0, HeadMouth.THICKNESS_MULTIPLIER, 0);
        // the inner skin sits one thickness below the outer skin at every vertex
        for (double w : WIDTHS) for (double open : new double[]{0, 22.5, 45}) {
            HeadMouth.Mesh m = mand(w, open, false);
            for (int j = 0; j < 3; j++) {
                for (int a = 0; a < m.arcOuter[j].length; a++) assertEquals(m.thickness, dist(m.arcOuter[j][a], m.arcInner[j][a]), 1e-9);
                assertEquals(m.thickness, dist(m.apexOuter[j], m.apexInner[j]), 1e-9);
            }
        }
    }

    private static double dist(double[] a, double[] b) { return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2])); }

    @Test void theShellIsWatertightClosedAndOpen() {
        for (double w : WIDTHS) for (double open : new double[]{0, 22.5, 45}) {
            HeadMouth.Mesh m = mand(w, open, false);
            int edges = 0;
            for (int j = 0; j < 3; j++) {
                Map<String, Integer> count = new HashMap<>();
                for (int q = m.flapShell[j]; q < m.flapShell[j + 1]; q++) {
                    var p = poly(m.shell, q);
                    for (int i = 0; i < p.size(); i++) {
                        String a = key(p.get(i)), b = key(p.get((i + 1) % p.size()));
                        count.merge(a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a, 1, Integer::sum);
                    }
                }
                for (var e : count.entrySet()) assertEquals(2, e.getValue(), "every edge is shared by exactly two faces (W" + w + " open " + open + " flap " + j + " edge " + e.getKey() + ")");
                edges += count.size();
            }
            if (open == 0) System.out.printf(Locale.ROOT, "[head2] watertight W%.0f: %d edges, every one shared by exactly two faces (outer, inner, lip wall, ridge walls)%n", w, edges);
        }
    }

    @Test void throatIsFixedFlatAndBehindTheRing() {
        for (double w : WIDTHS) {
            int n = nOf(w);
            double R = rOf(w);
            HeadMouth.Mesh t = thr(w, false);
            assertEquals(n, t.flat.quads, "one triangle per ring front vertex, the same split and arcs as the mandibles");
            for (int q = 0; q < t.flat.quads; q++) {
                var p = poly(t.flat, q);
                assertEquals(3, p.size());
                boolean apex = false;
                for (double[] v : p) if (Math.abs(v[2] - (-0.5 * R)) < 1e-4 && Math.abs(v[0]) < 1e-4 && Math.abs(v[1]) < 1e-4) apex = true;
                assertTrue(apex, "throat apex at -0.5 R on the axis");
            }
            assertEquals(-0.5 * R, HeadMouth.THROAT_APEX_R * R, 1e-12);
            int tint = HeadMouth.flatTint(HeadMouth.THROAT_RGB, 1.0);
            for (int ch = 0; ch < 3; ch++) {
                double v = ((tint >> (16 - 8 * ch)) & 255) * HeadMouth.FLAT_TEXEL[ch];
                assertEquals(0x3A, v, 2.0, "the tint times the texel is #3A3A3A");
            }
            for (int j = 0; j < 3; j++) assertArrayEquals(mand(w, 0, false).arcOuter[j][0], t.arcOuter[j][0], 1e-9, "same arcs as the mandibles");
        }
    }

    @Test void throatDoesNotPokeThroughTheShellAtAnyAngle() {
        for (double w : WIDTHS) {
            double R = rOf(w), th = HeadMouth.thickness(w);
            for (double open : new double[]{0, 22.5, 45}) {
                HeadMouth.Mesh m = mand(w, open, false);
                double behind = 0, minZ = 0;
                for (int j = 0; j < 3; j++) {
                    java.util.List<double[]> pts = new java.util.ArrayList<>(java.util.Arrays.asList(m.arcInner[j]));
                    pts.add(m.apexInner[j]);
                    for (double[] p : pts) {
                        double r = Math.hypot(p[0], p[1]);
                        double throatZ = r >= R ? 0 : -0.5 * R * (1 - r / R);
                        behind = Math.max(behind, throatZ - p[2]);
                        minZ = Math.min(minZ, p[2]);
                    }
                }
                System.out.printf(Locale.ROOT, "[head2] overlap throat vs shell W%.0f open %.1f: lowest inner-skin z=%.4f blocks, deepest the inner skin goes behind the throat surface=%.4f blocks (thickness %.3f)%n", w, open, minZ, behind, th);
                assertTrue(behind <= th, "the inner skin stays within one thickness of the throat surface");
            }
        }
    }

    // ================================================================================================== Part 2

    @Test void teethAreDeterministicAndCountedAtTheLockedParameters() {
        for (double w : WIDTHS) {
            HeadMouth.Mesh a = mand(w, 45, true), b = mand(w, 45, true);
            assertEquals(a.bristles.quads, b.bristles.quads);
            assertArrayEquals(java.util.Arrays.copyOf(a.bristles.v, a.bristles.quads * 32), java.util.Arrays.copyOf(b.bristles.v, b.bristles.quads * 32), "same seed, same teeth");
            HeadMouth.Mesh t1 = thr(w, true), t2 = thr(w, true);
            assertArrayEquals(java.util.Arrays.copyOf(t1.bristles.v, t1.bristles.quads * 32), java.util.Arrays.copyOf(t2.bristles.v, t2.bristles.quads * 32));
            System.out.printf(Locale.ROOT, "[teeth] W%.0f: mandible teeth %d (%d quads), throat teeth %d (%d quads)%n", w, a.mandibleTeeth, a.toothQuads, t1.throatTeeth, t1.toothQuads);
            assertEquals(2 * a.mandibleTeeth, a.toothQuads);
            if (w == 16) {
                assertEquals(629, a.mandibleTeeth, 0.15 * 629, "W16 mandible teeth within 15% of the previs's 629");
                assertEquals(346, t1.throatTeeth, 0.15 * 346, "W16 throat teeth within 15% of the previs's 346");
            }
        }
        // a different width gives different teeth (the seed comes from the width and the flap)
        assertNotEquals(mand(16, 45, true).bristles.v[0], mand(15.5, 45, true).bristles.v[0]);
        assertEquals(0, mand(16, 45, false).bristles.quads, "teeth=off builds no bristles");
    }

    private static double[] unit(double[] a) { double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }

    private static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    /** Distance from p to the nearest triangle of the fan (plane distance where the projection falls inside the triangle). */
    private static double distToFan(double[] p, double[][] arc, double[] apex) {
        double best = 1e9;
        for (int f = 0; f + 1 < arc.length; f++) {
            double[] a = arc[f], b = arc[f + 1];
            double[] e1 = {b[0] - a[0], b[1] - a[1], b[2] - a[2]}, e2 = {apex[0] - a[0], apex[1] - a[1], apex[2] - a[2]};
            double[] nrm = unit(new double[]{e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]});
            double[] ap = {p[0] - a[0], p[1] - a[1], p[2] - a[2]};
            double d = Math.abs(dot(ap, nrm));
            double[] q = {ap[0] - nrm[0] * dot(ap, nrm), ap[1] - nrm[1] * dot(ap, nrm), ap[2] - nrm[2] * dot(ap, nrm)};
            double d11 = dot(e1, e1), d12 = dot(e1, e2), d22 = dot(e2, e2), q1 = dot(q, e1), q2 = dot(q, e2);
            double den = d11 * d22 - d12 * d12;
            double u = (d22 * q1 - d12 * q2) / den, v = (d11 * q2 - d12 * q1) / den;
            if (u >= -1e-6 && v >= -1e-6 && u + v <= 1 + 1e-6) best = Math.min(best, d);
        }
        return best;
    }

    @Test void teethSitOnTheInnerSkinAndPointTheRightWay() {
        for (double w : WIDTHS) for (double open : new double[]{0, 22.5, 45}) {
            double R = rOf(w);
            HeadMouth.Mesh m = mand(w, open, true);
            double longest = 0;
            for (HeadMouth.Tooth t : m.teeth) {
                double d = distToFan(t.base(), m.arcInner[t.flap()], m.apexInner[t.flap()]);
                assertTrue(d <= 0.03, "tooth base on the inner skin within 0.03 (" + d + ")");
                assertTrue(dot(t.dir(), t.nin()) > 0.5, "the tooth leans into the cavity");
                assertTrue(dot(t.dir(), scaled(t.nin(), -1)) < 0, "negative dot product against the outward normal: the mandible teeth point inward");
                longest = Math.max(longest, t.length());
                assertEquals(1.0, Math.sqrt(dot(t.dir(), t.dir())), 1e-9);
            }
            assertTrue(longest <= 0.7 * R * 1.15 + 1e-9, "longest tooth at most 0.7 R x 1.15");
            if (w == 16 && open == 45) System.out.printf(Locale.ROOT, "[teeth] W16 longest mandible tooth %.3f blocks (0.7 R = %.3f, max %.3f)%n", longest, 0.7 * R, 0.7 * R * 1.15);
        }
        for (double w : WIDTHS) {
            double R = rOf(w);
            HeadMouth.Mesh t = thr(w, true);
            double[] apex = t.apexOuter[0];
            for (HeadMouth.Tooth tooth : t.teeth) {
                double d = distToFan(tooth.base(), t.arcOuter[tooth.flap()], apex);
                assertTrue(d <= 0.03, "throat tooth base on the throat within 0.03 (" + d + ")");
                assertTrue(tooth.v() <= HeadMouth.T_REACH + 1e-12, "reach cut: no throat tooth with v > 0.5 (" + tooth.v() + ")");
                assertTrue(dot(tooth.dir(), tooth.nin()) > 0, "the throat teeth stand out of the throat into the cavity");
                // direction: down the throat, toward its apex
                double[] toApex = unit(new double[]{apex[0] - tooth.base()[0], apex[1] - tooth.base()[1], apex[2] - tooth.base()[2]});
                assertTrue(dot(tooth.dir(), toApex) > 0, "the throat teeth point down the throat (toward its apex)");
                assertTrue(tooth.length() <= 0.55 * R * 1.15 + 1e-9);
            }
        }
    }

    private static double[] scaled(double[] a, double f) { return new double[]{a[0] * f, a[1] * f, a[2] * f}; }

    @Test void teethRecomputeAtEveryAngleWithoutNaN() {
        for (double w : WIDTHS) for (double open : new double[]{0, 22.5, 45}) {
            HeadMouth.Mesh m = mand(w, open, true);
            for (HeadMouth.Mesh x : new HeadMouth.Mesh[]{m}) {
                for (var res : new ShingleMath.Result[]{x.shell, x.bristles}) for (int i = 0; i < res.quads * 32; i++) assertTrue(Float.isFinite(res.v[i]), "finite vertex data at " + open);
            }
        }
    }

    @Test void costOfOneRebuild() {
        for (double w : WIDTHS) {
            mand(w, 45, true); thr(w, true);   // warm up
            long t0 = System.nanoTime();
            HeadMouth.Mesh m = mand(w, 30, true);
            double ms = (System.nanoTime() - t0) / 1e6;
            long t1 = System.nanoTime();
            HeadMouth.Mesh t = thr(w, true);
            double tms = (System.nanoTime() - t1) / 1e6;
            System.out.printf(Locale.ROOT, "[teeth] cost W%.0f: one mandible rebuild %.2f ms (shell %d quads + %d teeth quads, of which teeth %.2f ms); throat once %.2f ms (%d quads + %d teeth quads)%n",
                    w, ms, m.shell.quads, m.toothQuads, m.teethMs, tms, t.flat.quads, t.toothQuads);
            assertTrue(ms < 200, "a rebuild is cheap enough to do while the mouth moves");
        }
    }

    // ================================================================================================== Part 3

    private static Moves.Move crest() { return Moves.crest(16, 80, 1.3, 6, 6, 12, false); }

    private static Moves.Move[] defaults() {
        return new Moves.Move[]{Moves.emerge(16, 60, 10, 90, 12, 2, 1.0, false), Moves.fall(16, 120, 2.5, 7, 1.5, 12, 12, 2.0, 1.0, false),
                Moves.engulf(16, 60, 2, 7, 0.5, 12, 12, 1.0, false), crest()};
    }

    @Test void theMouthClosesOnceAndStaysClosedPerMove() {
        for (Moves.Move m : defaults()) {
            MouthAnim.Timing g = MouthAnim.timing(m, 0.9, MouthAnim.CLOSE_END_DEFAULT_MARK);
            System.out.printf(Locale.ROOT, "[mouth] %s W16: apex %.3f s, closing from %.3f s to %.3f s (%.3f s), clamped=%b, loop %.2f s%n", m.label(), g.apex(), g.tStart(), g.tEnd(), g.closeTime(), g.clamped(), m.cycle);
            assertEquals(45.0, MouthAnim.angleAt(m, 0, 45, 0.9, MouthAnim.CLOSE_END_DEFAULT_MARK), 1e-12, "open at t = 0");
            assertEquals(0.0, MouthAnim.angleAt(m, g.tEnd(), 45, 0.9, MouthAnim.CLOSE_END_DEFAULT_MARK), 0, "closed at t_end");
            double prev = 45;
            for (double t = g.tStart() + 0.001; t < g.tEnd(); t += 0.001) {
                double a = MouthAnim.angleAt(m, t, 45, 0.9, MouthAnim.CLOSE_END_DEFAULT_MARK);
                assertTrue(a < prev, "strictly decreasing at " + t);
                prev = a;
            }
            for (double t = g.tEnd(); t < m.cycle; t += 0.01) assertEquals(0.0, MouthAnim.angleAt(m, t, 45, 0.9, MouthAnim.CLOSE_END_DEFAULT_MARK), 0, "exactly 0 until the loop reset, t=" + t);
            assertEquals(45.0, MouthAnim.angleAt(m, m.cycle + 1e-6, 45, 0.9, MouthAnim.CLOSE_END_DEFAULT_MARK), 0.01, "open again at the loop reset");
            if (!m.kind.equals("crest")) {
                assertEquals(m.advanceTime, g.tEnd(), 1e-3, "closed within 1 ms of the start of the hold");
                assertTrue(MouthAnim.angleAt(m, m.advanceTime - 0.001, 45, 0.9, MouthAnim.CLOSE_END_DEFAULT_MARK) > 0);
                assertEquals(0.9, g.closeTime(), 1e-9);
                assertFalse(g.clamped(), m.label() + " needs no clamp at the defaults");
            }
        }
    }

    @Test void theCrestClosesJustPastTheHighestPointOfTheHeadTip() {
        Moves.Move m = crest();
        double best = -1e9, at = 0;
        for (double t = 0; t <= m.advanceTime; t += 0.001) {
            double y = m.tip(m.s0 + m.speed * t)[1];
            if (y > best) { best = y; at = t; }
        }
        assertEquals(at, m.apexTime(), 0.01, "apex = the instant the head tip is highest");
        MouthAnim.Timing g = MouthAnim.timing(m, 0.9, MouthAnim.CLOSE_END_DEFAULT_MARK);
        assertEquals(m.apexTime() + 0.3, g.tEnd(), 1e-9, "close_end defaults to +0.3 s on the crest");
        assertEquals(0.0, MouthAnim.angleAt(m, m.apexTime() + 0.3, 45, 0.9, MouthAnim.CLOSE_END_DEFAULT_MARK), 0);
        assertTrue(MouthAnim.angleAt(m, m.apexTime(), 45, 0.9, MouthAnim.CLOSE_END_DEFAULT_MARK) > 0, "still closing at the apex");
        for (double ce : new double[]{0, 0.3, 0.6}) assertEquals(m.apexTime() + ce, MouthAnim.timing(m, 0.9, ce).tEnd(), 1e-9);
    }

    @Test void aShortRiseClampsTheClosingStart() {
        Moves.Move m = Moves.engulf(3, 20, 1, 7, 0.5, 64, 12, 1.0, false);   // fast and short: the rise is well under 0.9 s
        assertTrue(m.advanceTime < 0.9, "rise " + m.advanceTime);
        MouthAnim.Timing g = MouthAnim.timing(m, 0.9, MouthAnim.CLOSE_END_DEFAULT_MARK);
        assertTrue(g.clamped());
        assertEquals(0, g.tStart(), 0);
        assertEquals(g.tEnd(), g.closeTime(), 1e-12, "the mouth closes faster than the default");
        assertEquals(0.0, MouthAnim.angleAt(m, g.tEnd(), 45, 0.9, MouthAnim.CLOSE_END_DEFAULT_MARK), 0);
        // and the player is told
        WormSpec s = MoveOptions.parse("engulf", 3, 20, "exposed=1 speed=64", false).spec();
        assertTrue(MoveFactory.mouthTiming(s).clamped());
    }

    @Test void mouthKeysAreParsedClampedAndPrinted() {
        MoveOptions.Parsed p = MoveOptions.parse("engulf", 16, 60, "close_time=100 close_end=99 open_deg=90 teeth=off", false);
        assertEquals(3, p.notes().size(), "every clamp reaches the player: " + p.notes());
        assertEquals(30.0, p.spec().p(21, 0), 0);
        assertEquals(30.0, p.spec().p(22, 0), 0);
        assertEquals(45.0, p.spec().p(23, 0), 0);
        assertFalse(MoveFactory.teethOn(p.spec()));
        assertEquals(0.05, MoveOptions.parse("crash", 16, 120, "close_time=0", false).spec().p(21, 0), 0);
        assertThrows(MoveOptions.Bad.class, () -> MoveOptions.parse("emerge", 16, 60, "mouth=wide", false));
        assertThrows(MoveOptions.Bad.class, () -> MoveOptions.parse("emerge", 16, 60, "teeth=maybe", false));
        String all = String.join("\n", MoveOptions.lines());
        for (String k : new String[]{"mouth=anim", "close_time=0.9", "close_end=0.3", "close_end=0", "open_deg=45", "teeth=on"}) assertTrue(all.contains(k), "the moves text has " + k);
        for (String mv : MoveOptions.MOVES) for (String k : new String[]{"mouth=", "close_time=", "close_end=", "open_deg=", "teeth="}) assertTrue(MoveOptions.line(mv).contains(k), mv + " has " + k);
        // defaults leave the spec short; only p8 says anim
        WormSpec d = MoveOptions.parse("emerge", 16, 60, "", false).spec();
        assertEquals(MoveFactory.MOUTH_ANIM, (int) d.p(8, 0));
        assertEquals(17, d.p().length, "defaults add nothing to the spec");
    }

    @Test void staticMouthsAndTheHeldPose() {
        for (Moves.Move m : defaults()) {
            for (double tau = 0; tau < m.cycle; tau += 0.37) {
                WormSpec open = MoveOptions.parse(m.label(), 16, 120, "mouth=open", false).spec();
                WormSpec closed = MoveOptions.parse(m.label(), 16, 120, "mouth=closed", false).spec();
                WormSpec open30 = MoveOptions.parse(m.label(), 16, 120, "mouth=open open_deg=30", false).spec();
                assertEquals(45.0, MoveFactory.mouthAngle(open, tau), 0);
                assertEquals(0.0, MoveFactory.mouthAngle(closed, tau), 0);
                assertEquals(30.0, MoveFactory.mouthAngle(open30, tau), 0);
            }
        }
        WormSpec s = MoveOptions.parse("engulf", 16, 60, "", false).spec();
        assertEquals(45.0, MoveFactory.mouthAngle(MoveFactory.withMouthPose(s, 0), 3), 1e-12);
        assertEquals(22.5, MoveFactory.mouthAngle(MoveFactory.withMouthPose(s, 0.5), 3), 1e-12);
        assertEquals(0.0, MoveFactory.mouthAngle(MoveFactory.withMouthPose(s, 1), 3), 1e-12);
        assertEquals(MoveFactory.mouthAngle(s, 3), MoveFactory.mouthAngle(MoveFactory.withMouthPose(MoveFactory.withMouthPose(s, 0.5), -1), 3), 1e-12, "off returns to the animation");
        // freeze hold shows the closed mouth, freeze <t> the pose of that instant (closed for the drop)
        for (String mv : new String[]{"engulf", "crash"}) {
            WormSpec f = MoveOptions.parse(mv, 16, 120, "", false).spec();
            Moves.Move m = MoveFactory.get(f);
            assertEquals(0.0, MoveFactory.mouthAngle(f, m.tauAt(9)), 0, mv + " freeze hold: closed");
            assertEquals(0.0, MoveFactory.mouthAngle(f, m.tauAt(0.5)), 0, mv + " freeze 0.5: closed");
        }
    }

    @Test void nothingIsRebuiltAfterTheMouthClosed() {
        for (Moves.Move m : defaults()) {
            MouthAnim.Timing g = MouthAnim.timing(m, 0.9, MouthAnim.CLOSE_END_DEFAULT_MARK);
            int builds = 0, buildsAfter = 0, last = -1;
            for (double t = 0; t < m.cycle; t += 1.0 / 60) {
                int k = MouthAnim.angleKey(MouthAnim.angleAt(m, t, 45, 0.9, MouthAnim.CLOSE_END_DEFAULT_MARK));
                if (k != last) {
                    builds++;
                    if (t > g.tEnd() + 1e-9 && last >= 0) buildsAfter++;
                    last = k;
                }
            }
            System.out.printf(Locale.ROOT, "[mouth] %s: %d mandible builds over one loop at 60 fps (the closing takes %.2f s), %d after the closed instant%n", m.label(), builds, g.closeTime(), buildsAfter);
            assertEquals(0, buildsAfter, "no rebuild after the closed instant");
            assertTrue(builds >= 2 && builds <= 70);
            assertEquals(0, last, "the loop ends closed (key 0)");
        }
    }

    @Test void theMouthAnglesDoNotChangeThePaths() {
        // the mouth must not move a path or a timing: the same move with the mouth open, closed or animated has the same cycle
        for (String mv : new String[]{"crest", "emerge", "crash", "engulf"}) {
            double c0 = MoveFactory.get(MoveOptions.parse(mv, 16, 120, "mouth=open", false).spec()).cycle;
            assertEquals(c0, MoveFactory.get(MoveOptions.parse(mv, 16, 120, "", false).spec()).cycle, 1e-12);
            assertEquals(c0, MoveFactory.get(MoveOptions.parse(mv, 16, 120, "close_time=2 teeth=off", false).spec()).cycle, 1e-12);
        }
    }
}
