package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Prompt 13: the chat texts of the hunt commands. The keys live under the language file prefix of the mod ({@code islandcraft_wormsofarrakis.hunt.<key>}); the English text
 * here is also the fallback the game uses when a language file lacks the key, and the unit test checks that the file {@code lang/en_us.json} says the same. Pure data.
 */
public final class HuntTexts {
    private HuntTexts() {}

    public static final String PREFIX = "islandcraft_wormsofarrakis.hunt.";

    /** The death message (the damage type's message id is islandcraft_wormsofarrakis.swallowed; the game looks up death.attack.<id>). */
    public static final String DEATH_KEY = "death.attack.islandcraft_wormsofarrakis.swallowed";
    public static final String DEATH_TEXT = "%1$s was swallowed by a sandworm";

    public static final Map<String, String> EN = new LinkedHashMap<>();
    static {
        EN.put("status.none", "No hunt events are running.");
        EN.put("status.event", "Event %s: %s worm (W%s), %s, %s s left, list of %s");
        EN.put("status.member", "  %s: score %s, %s");
        EN.put("status.victim", "  victim: %s (score %s)");
        EN.put("status.self", "You: aggro %s, multiplier x%s, fed by: %s");
        EN.put("aggro.line", "%s: aggro %s, multiplier x%s, fed by %s; sand depth D=%s -> %s");
        EN.put("aggro.size", "a %s worm (W%s, stalk %s s)");
        EN.put("aggro.noevent", "no event (too shallow or not on sand)");
        EN.put("on_sand", "on sand");
        EN.put("off_sand", "off sand");
        EN.put("none", "nothing");
        EN.put("add.done", "%s: aggro is now %s");
        EN.put("trigger.started", "Event started on %s: %s worm (W%s), stalk %s s, %s in the list");
        EN.put("trigger.shallow", "The sand under %s is only %s deep (an event needs 5 or more); give a size to ignore the depth.");
        EN.put("trigger.in_event", "%s is already in an event.");
        EN.put("trigger.not_eligible", "%s cannot be hunted now (creative, spectator, dead or ignored).");
        EN.put("trigger.not_on_sand", "%s is not on sand; give a size to start anyway.");
        EN.put("trigger.unknown", "%s is not in the game.");
        EN.put("stop.done", "Stopped %s event(s); the lists are zeroed.");
        EN.put("ignore.on", "%s is now ignored by the worms (testing).");
        EN.put("ignore.off", "%s is no longer ignored.");
        EN.put("preview.done", "Tremor preview: strength %s, W%s (size factor %s): radius %s blocks, %s layer(s), %s particles, volume %s, range %s blocks");
        EN.put("bad.size", "size must be small, medium, large or huge, not %s");
        EN.put("bad.player", "no such player: %s");
        EN.put("bad.range", "%s is outside %s; clamped to %s");
        EN.put("bad.width", "W must be 1 to 128");
        EN.put("status.strike", "  strike: %s, direction %s (%s s left)");
        EN.put("status.strike_none", "  strike: not chosen yet");
        EN.put("strike.bad_type", "strike must be crest, crash, emerge or engulf, not %s");
        EN.put("strike.refused", "%s is not available at %s: %s (add force to run it anyway)");
        EN.put("strike.forced", "Strike %s will be used at the end of the freeze%s");
        EN.put("strike.forced_anyway", " (forced although it is not available here: %s)");
        EN.put("strike.needs_size", "strike needs a size (small, medium, large or huge) so the worm is known");
    }
}
