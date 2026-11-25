package com.paulorchard.islandcraft.wormsofarrakis.hunt;

/**
 * Prompt 14 part 1: the freeze of the victim, engine-free. Over {@code seconds} the speed of the victim falls linearly to zero (the "cut" goes 0 to 1), jumping is blocked from the
 * first tick, and at zero the victim is held on the spot until the strike. The game side ({@link Body}) turns the cut into attribute modifiers and holds the position.
 * {@link #release()} is idempotent and leaves nothing on the body.
 */
public final class Freeze {
    /** What the freeze does to a being; the game adapter implements it with attribute modifiers, a fake object does in the tests. */
    public interface Body {
        /** The speed is cut by {@code cut} (0 = full speed, 1 = zero); jumping is blocked when {@code jumpBlocked}. Called every tick while frozen. */
        void apply(double cut, boolean jumpBlocked);
        /** Called every tick once the cut is 1: keep the body where it stands (dismount, no elytra, push back beyond a small tolerance). */
        void hold();
        /** Removes everything the freeze put on the body. */
        void release();
    }

    private final double seconds;
    private final Body body;
    private double t;
    private boolean started, released;

    public Freeze(double seconds, Body body) {
        this.seconds = seconds;
        this.body = body;
    }

    /** The cut after {@code t} seconds of a freeze of {@code seconds}: linear, 0 to 1. */
    public static double cut(double t, double seconds) {
        if (seconds <= 0 || t >= seconds - 1e-9) return 1;   // the tick sum of 80 x 0.05 is 3.9999999999999996
        return Math.max(0, Math.min(1, t / seconds));
    }

    public double elapsed() { return t; }
    public double cut() { return cut(t, seconds); }
    public boolean started() { return started; }
    public boolean released() { return released; }
    public boolean held() { return started && !released && cut() >= 1; }

    /** One tick of {@code dt} seconds (the first call is the first tick of the freeze: jumping is blocked at once). */
    public void tick(double dt) {
        if (released) return;
        started = true;
        t += dt;
        double c = cut();
        body.apply(c, true);
        if (c >= 1) body.hold();
    }

    /** Releases the body (any end of the event, a death, a log-off, a dimension change, a stop). Safe to call twice. */
    public void release() {
        if (released) return;
        released = true;
        body.release();
    }
}
