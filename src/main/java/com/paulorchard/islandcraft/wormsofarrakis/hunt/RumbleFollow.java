package com.paulorchard.islandcraft.wormsofarrakis.hunt;

/**
 * Prompt 17 part 0.1: where the attack rumble of a member sits, engine-free. While the speed ramps down the rumble follows the player: every {@code followSeconds} it is centred again on the
 * player's current position if they have moved a block or more since the last centre. From the tick the player is held it is pinned to the place they are held on and never moves again.
 */
public final class RumbleFollow {
    private final double followSeconds;
    private double cx, cz, sinceCheck;
    private boolean pinned;
    public int recentred;

    public RumbleFollow(double followSeconds, double startX, double startZ) {
        this.followSeconds = followSeconds;
        this.cx = startX;
        this.cz = startZ;
    }

    public double x() { return cx; }
    public double z() { return cz; }
    public boolean pinned() { return pinned; }

    /** One tick: returns the new centre {x, z} when the rumble must be started again there, else null. */
    public double[] update(double dt, double px, double pz, boolean held) {
        if (pinned) return null;
        sinceCheck += dt;
        if (held) {
            pinned = true;
            sinceCheck = 0;
            return move(px, pz, 1e-6);
        }
        if (sinceCheck < followSeconds - 1e-9) return null;
        sinceCheck = 0;
        return move(px, pz, 1.0);
    }

    private double[] move(double px, double pz, double minDistance) {
        if (Math.hypot(px - cx, pz - cz) < minDistance) return null;
        cx = px;
        cz = pz;
        recentred++;
        return new double[]{cx, cz};
    }
}
