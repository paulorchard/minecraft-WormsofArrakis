package com.paulorchard.islandcraft.wormsofarrakis.test;

/** Joint angles of a chain/wave test worm at a given time (pure, shared by the bone path and the loft path). */
public final class TestAnim {
    /** Ticks for one full wave cycle at a joint. */
    public static final double PERIOD_TICKS = 60.0;
    /** The wave is one full sine every this many segments, and travels towards the tail. */
    public static final double WAVELENGTH_SEGMENTS = 10.0;

    private TestAnim() {}

    /** Returns {yawRad[segments], pitchRad[segments]} (index 0 is the head: no joint, always 0). */
    public static double[][] angles(WormSpec s, double ageTicks) {
        int segs = (int) s.p(1, 12);
        double[] yaw = new double[segs], pitch = new double[segs];
        for (int k = 1; k < segs; k++) {
            if (s.kind().equals("wave")) {
                double amp = Math.toRadians(s.p(2, 8));
                // phase minus k*delta: crests move from head to tail, like a swimming worm
                double phase = 2 * Math.PI * (ageTicks / PERIOD_TICKS - k / WAVELENGTH_SEGMENTS);
                yaw[k] = amp * Math.sin(phase);
            } else {
                yaw[k] = Math.toRadians(s.p(2, 0));
                pitch[k] = Math.toRadians(s.p(3, 0));
            }
        }
        return new double[][]{yaw, pitch};
    }

    /** Loose half-extent (blocks) of everything a spec draws, horizontally, measured from the entity position. */
    public static double reach(WormSpec s, double circumradius) {
        return switch (s.kind()) {
            case "chain", "wave" -> s.p(1, 12) * s.p(4, 2) + 2 * circumradius + 2;
            case "scale", "scales" -> s.p(1, 8) + 2 * circumradius + 4;
            case "move" -> MoveFactory.reach(s);
            case "segment" -> s.p(1, 2) + 2 * circumradius + 2;
            default -> 2 * circumradius + 4;
        };
    }
}
