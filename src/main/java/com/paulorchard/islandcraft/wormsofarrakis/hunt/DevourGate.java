package com.paulorchard.islandcraft.wormsofarrakis.hunt;

/**
 * Prompt 14 part 2: decides once, per strike, when the victim is devoured. Engine-free. {@code KILL} exactly once when the worm's age reaches the configured moment and the victim is
 * still there and alive; {@code SKIP} once when the moment is reached but the victim is gone, dead or no longer hunted (nobody is killed); {@code WAIT} otherwise, and forever after.
 */
public final class DevourGate {
    public enum Action { WAIT, KILL, SKIP }

    private final double devourTicks;
    private boolean done;

    public DevourGate(double devourSeconds) { this.devourTicks = devourSeconds * 20.0; }

    public boolean done() { return done; }

    public Action check(double wormAgeTicks, boolean victimPresent) {
        if (done || wormAgeTicks < devourTicks) return Action.WAIT;
        done = true;
        return victimPresent ? Action.KILL : Action.SKIP;
    }
}
