package com.paulorchard.islandcraft.wormsofarrakis.path;

/**
 * Prompt 08 part 3: the mouth angle as a pure function of the loop clock (no state). The mouth starts open every loop, closes once, and then
 * nothing moves until the loop restarts under the sand: angle(t) = open x (1 - smoothstep((t - tStart) / closeTime)), so open for t <= tStart and
 * exactly 0 for t >= tStart + closeTime. The close ends at tEnd = apex + closeEnd (apex = {@link Moves.Move#apexTime()}).
 */
public final class MouthAnim {
    public static final double DEFAULT_CLOSE_TIME = 0.9;
    public static final double DEFAULT_CLOSE_END = 0.0;
    public static final double CREST_CLOSE_END = 0.3;
    /** Spec value of "close_end not given": the move's own default (0, crest 0.3). */
    public static final double CLOSE_END_DEFAULT_MARK = -999;
    public static final double MAX_CLOSE_TIME = 30, MAX_CLOSE_END = 30;

    private MouthAnim() {}

    public static double defaultCloseEnd(String kind) { return kind.equals("crest") ? CREST_CLOSE_END : DEFAULT_CLOSE_END; }

    public static double smoothstep(double x) {
        double c = Math.max(0, Math.min(1, x));
        return c * c * (3 - 2 * c);
    }

    /** The law itself. */
    public static double angle(double t, double openDeg, double tStart, double closeTime) {
        if (closeTime <= 0) return t < tStart ? openDeg : 0.0;
        return openDeg * (1 - smoothstep((t - tStart) / closeTime));
    }

    /**
     * The cache key of a mouth pose: the angle in tenths of a degree, 0 once closed. The renderer rebuilds the shell and the teeth only when this
     * changes, so once the angle has reached 0 nothing is rebuilt for the rest of the loop.
     */
    public static int angleKey(double angleDeg) { return angleDeg <= 0 ? 0 : (int) Math.round(angleDeg * 10); }

    /** The times of one move: apex, start and end of the closing, and the closing duration actually used. */
    public record Timing(double apex, double tStart, double tEnd, double closeTime, boolean clamped) {}

    /** closeEnd = NaN or {@link #CLOSE_END_DEFAULT_MARK} for the move's default. */
    public static Timing timing(Moves.Move m, double closeTime, double closeEnd) {
        if (Double.isNaN(closeEnd) || closeEnd == CLOSE_END_DEFAULT_MARK) closeEnd = defaultCloseEnd(m.kind);
        double apex = m.apexTime();
        double tEnd = Math.max(1e-3, apex + closeEnd);
        double tStart = tEnd - closeTime;
        boolean clamped = false;
        if (tStart < 0) { tStart = 0; clamped = true; }   // the rise is shorter than the closing: it closes faster than the default
        return new Timing(apex, tStart, tEnd, tEnd - tStart, clamped);
    }

    /** Angle at script time tau (the loop clock is tau modulo the cycle). */
    public static double angleAt(Moves.Move m, double tau, double openDeg, double closeTime, double closeEnd) {
        double t = tau % m.cycle;
        if (t < 0) t += m.cycle;
        Timing g = timing(m, closeTime, closeEnd);
        return angle(t, openDeg, g.tStart(), g.closeTime());
    }
}
