package com.paulorchard.islandcraft.wormsofarrakis.hazard;

/** Prompt 16 part 2: one of the lethal volumes of a worm. Engine-free. */
public interface WormHitShape {
    /** "body", "head" or "throat". */
    String part();

    /** True when the box touches the shape at the move's script time {@code t} (seconds): any sample point of the box is inside it. */
    boolean touches(Box box, double t);
}
