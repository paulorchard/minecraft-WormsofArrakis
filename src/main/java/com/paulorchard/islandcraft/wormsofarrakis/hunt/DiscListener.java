package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import java.util.UUID;

/**
 * Prompt 18: a subscriber to the disc crossings of a strike (a later multiplayer detector can use the time between the two discs as its marker). {@code t} is seconds after the move started. The
 * default implementations do nothing and log nothing.
 */
public interface DiscListener {
    default void onMouth(long event, UUID player, double t) {}

    default void onThroat(long event, UUID player, double t) {}
}
