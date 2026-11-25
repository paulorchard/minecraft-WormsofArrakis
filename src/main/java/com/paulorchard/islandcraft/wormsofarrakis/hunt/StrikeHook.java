package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import java.util.UUID;

/**
 * Prompt 13 part 4: the hook of the next prompt. At the start of ATTACK the event calls {@code onAttack(event, victim, widthW, sizeName)}; the default implementation only logs
 * {@code [WormsOfArrakis] hunt: attack victim=<name> size=<small|medium|large|huge> W<width> score=<n>}. The next prompt plugs in a real strike without touching the state machine.
 */
@FunctionalInterface
public interface StrikeHook {
    void onAttack(HuntEvent event, UUID victim, double widthW, String sizeName);

    /** The line the default hook writes. */
    static String attackLine(HuntEvent e, double widthW, String sizeName) {
        return String.format(java.util.Locale.ROOT, "hunt: attack victim=%s size=%s W%s score=%d", e.victimName(), sizeName, widthW == Math.rint(widthW) ? Long.toString((long) widthW) : Double.toString(widthW),
                Math.round(e.victimScore()));
    }
}
