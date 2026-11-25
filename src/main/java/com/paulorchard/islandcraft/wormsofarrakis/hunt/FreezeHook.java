package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import java.util.UUID;

/**
 * Prompt 14 part 1: the hook of the camera prompt. {@code onFreezeStart} when the freeze of the victim begins (the attack starts), {@code onFreezeEnd} when it is released (the end of the
 * event, whatever the reason). The defaults only log. No camera work in this prompt.
 */
public interface FreezeHook {
    org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("WormsOfArrakis");

    default void onFreezeStart(UUID victim, HuntEvent event) {
        LOG.info("[WormsOfArrakis] hunt: freeze hook start victim={} event={}", victim, event.id);
    }

    default void onFreezeEnd(UUID victim, HuntEvent event) {
        LOG.info("[WormsOfArrakis] hunt: freeze hook end victim={} event={}", victim, event.id);
    }

    /** The hook that only logs. */
    FreezeHook LOGGING = new FreezeHook() {};
}
