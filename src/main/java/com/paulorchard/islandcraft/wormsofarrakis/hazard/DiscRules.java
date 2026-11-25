package com.paulorchard.islandcraft.wormsofarrakis.hazard;

/** Prompt 18: what a disc does to whom, engine-free. Only the two discs act; the body, head, mandibles and funnel are geometry. */
public final class DiscRules {
    private DiscRules() {}

    public enum Outcome { IGNORE, SPARED, CAMERA, SWALLOW, KILL }

    /** Does this disc act on this kind of subject at all (the switches, and the mouth disc is for players only)? */
    public static boolean acts(String disc, boolean player, boolean throatOn, boolean mouthOn) {
        return switch (disc) {
            case Discs.THROAT -> throatOn;
            case Discs.MOUTH -> mouthOn && player;
            default -> false;   // body, head, mandible, funnel: harmless
        };
    }

    /**
     * The outcome of a crossing: SPARED when the subject is off Dune Sand and its kind is immune; CAMERA (mouth disc, players); SWALLOW (throat disc, players; the camera starts too if it had not);
     * KILL (throat disc, mobs, no camera); IGNORE otherwise.
     */
    public static Outcome outcome(String disc, boolean player, boolean onSand, boolean playersImmuneOffSand, boolean mobsImmuneOffSand, boolean throatOn, boolean mouthOn) {
        if (!acts(disc, player, throatOn, mouthOn)) return Outcome.IGNORE;
        if (!onSand && (player ? playersImmuneOffSand : mobsImmuneOffSand)) return Outcome.SPARED;
        if (disc.equals(Discs.MOUTH)) return Outcome.CAMERA;
        return player ? Outcome.SWALLOW : Outcome.KILL;
    }
}
