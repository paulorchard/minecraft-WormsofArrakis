package com.paulorchard.islandcraft.wormsofarrakis;

import com.paulorchard.islandcraft.wormsofarrakis.geometry.ChainMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.WormMath;
import com.paulorchard.islandcraft.wormsofarrakis.test.TestAnim;
import com.paulorchard.islandcraft.wormsofarrakis.test.WormSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChainTest {
    private static ChainMath chain(int segs, double len, double yawDeg, double pitchDeg, double r) {
        double[] yaw = new double[segs], pitch = new double[segs], rad = new double[segs + 1];
        for (int k = 1; k < segs; k++) { yaw[k] = Math.toRadians(yawDeg); pitch[k] = Math.toRadians(pitchDeg); }
        java.util.Arrays.fill(rad, r);
        return new ChainMath(segs, len, r, yaw, pitch, rad);
    }

    @Test void straightChainLength() {
        ChainMath c = chain(12, 2, 0, 0, 8);
        assertEquals(-24, c.center[12][2], 1e-9);
        assertEquals(0, c.center[12][0], 1e-9);
    }

    @Test void mitreRingsCoincideInAPureBend() {
        int n = 50;
        for (double deg : new double[]{2, 5, 10, 20, 40}) {
            ChainMath c = chain(4, 2, deg, 0, 8);
            double[][] in = c.miterRing(2, true, n), out = c.miterRing(2, false, n);
            for (int k = 0; k < n; k++) for (int a = 0; a < 3; a++) assertEquals(in[k][a], out[k][a], 1e-9, "yaw " + deg);
            c = chain(4, 2, 0, deg, 8);
            in = c.miterRing(2, true, n); out = c.miterRing(2, false, n);
            for (int k = 0; k < n; k++) for (int a = 0; a < 3; a++) assertEquals(in[k][a], out[k][a], 1e-9, "pitch " + deg);
        }
    }

    @Test void plainFrustaDoNotMeetInABend() {
        int n = 50;
        ChainMath c = chain(4, 2, 10, 0, 8);
        double[][] rear = c.plainRing(1, 1, n), front = c.plainRing(2, 0, n);
        double max = 0;
        for (int k = 0; k < n; k++) max = Math.max(max, Math.abs(rear[k][2] - front[k][2]) + Math.abs(rear[k][0] - front[k][0]));
        assertTrue(max > 0.5, "straight frusta leave a wedge at a 10 degree bend, r=8: " + max);
    }

    /** Prints the table used in the report: wedge depth on the outside of the bend, r * tan(bend). */
    @Test void gapTable() {
        StringBuilder sb = new StringBuilder("[chain] outer wedge depth (blocks), plain hoops, R=7.97 (W16) / 23.87 (W48)\n");
        for (double deg : new double[]{0, 2, 5, 10, 20}) {
            sb.append(String.format("[chain] bend %4.0f deg: W16 gap %.3f  W48 gap %.3f  (one texture tile = 1.0)%n", deg,
                    ChainMath.outerGap(WormMath.circumradius(50, 1), Math.toRadians(deg)),
                    ChainMath.outerGap(WormMath.circumradius(150, 1), Math.toRadians(deg))));
        }
        System.out.print(sb);
    }

    @Test void waveAnglesTravelTowardsTheTail() {
        WormSpec s = WormSpec.of("wave", "lip", 16, 12, 8, 0, 2, 1);
        // a crest at joint 1 at time t reaches joint 2 a little later
        double period = TestAnim.PERIOD_TICKS, dt = period / TestAnim.WAVELENGTH_SEGMENTS;
        double a1 = TestAnim.angles(s, 0)[0][1], a2 = TestAnim.angles(s, dt)[0][2];
        assertEquals(a1, a2, 1e-9);
    }

    @Test void specRoundTrip() {
        WormSpec s = WormSpec.of("chain", "loft", 16, 12, 5, 0, 2.5, 0.97);
        assertEquals(s.serialize(), WormSpec.parse(s.serialize()).serialize());
        assertEquals("chain_loft_16_12_5_0_2.5_0.97", s.key());
    }
}
