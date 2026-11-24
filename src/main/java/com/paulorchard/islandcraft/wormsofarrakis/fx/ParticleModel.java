package com.paulorchard.islandcraft.wormsofarrakis.fx;

import com.paulorchard.islandcraft.wormsofarrakis.fx.CrashFx.PType;

/**
 * Prompt 09 part 3: how far a particle of each vanilla type travels, so the sheet's velocities (tuned in the previs, where every particle slowed down
 * by the default friction) give the same drift in the game. Pure code; the numbers are read from the 26.1.2 sources:
 * <pre>
 * block (TerrainParticle)        friction 0.98 (Particle default), gravity 1.0 -> what the previs assumed: factor 1
 * cloud (PlayerCloudParticle)    friction 0.96 constant, gravity 0
 * dust_plume (DustPlumeParticle) friction 0.96 x 0.92 each tick (so it stops within ~8 ticks), gravity 0.5 x 0.88 each tick
 * explosion                      does not move
 * </pre>
 * A velocity v (blocks per tick) travels v x (sum over the life of the product of the frictions) blocks. {@link #driftFactor} is the ratio of that sum
 * with the previs friction (0.98) to the sum of the vanilla type, so that the velocity is multiplied by it and the particle drifts as far as in the previs.
 * Assumption (inferred, the previs file cannot be read): the previs slowed every particle by 0.98 per tick.
 */
public final class ParticleModel {
    private ParticleModel() {}

    public static final double PREVIS_FRICTION = 0.98;

    /** Sum over ticks 1..life of the cumulative friction product of the vanilla type. */
    public static double travelSum(PType t, int life) {
        double prod = 1, sum = 0, f = t == PType.CLOUD ? 0.96 : t == PType.DUST_PLUME ? 0.96 : PREVIS_FRICTION;
        for (int k = 1; k <= life; k++) {
            if (t == PType.DUST_PLUME) f *= 0.92;
            prod *= f;
            sum += prod;
        }
        return sum;
    }

    /** The velocity multiplier that makes a particle of this type and life drift as far as the previs's (1 for block grains and the explosion). */
    public static double driftFactor(PType t, int life) {
        if (t != PType.CLOUD && t != PType.DUST_PLUME) return 1;
        double previs = 0, prod = 1;
        for (int k = 1; k <= life; k++) {
            prod *= PREVIS_FRICTION;
            previs += prod;
        }
        return previs / travelSum(t, life);
    }

    /** Explosion particle: the size parameter xAux that gives a quad of the wanted full width (quad half-size = 2 x (1 - size / 2)). */
    public static double explosionParam(double widthBlocks) { return 2 * (1 - widthBlocks / 4); }

    /** Scale to apply to a particle of this type so that its mean full width is the sheet's size (1 for the explosion: its size is the parameter). */
    public static double scaleFor(PType t, double sizeBlocks) { return t == PType.EXPLOSION ? 1 : sizeBlocks / t.vanillaWidth; }
}
