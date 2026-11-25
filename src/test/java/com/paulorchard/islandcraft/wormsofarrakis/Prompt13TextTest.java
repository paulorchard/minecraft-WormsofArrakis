package com.paulorchard.islandcraft.wormsofarrakis;

import com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntConfig;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntTexts;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Prompt 13: the chat texts live under the language file prefix of the mod; the fallback in code says the same. */
class Prompt13TextTest {
    @Test
    void theLanguageFileHasEveryKeyWithTheSameText() throws IOException {
        String json;
        try (InputStream in = Prompt13TextTest.class.getResourceAsStream("/assets/islandcraft_wormsofarrakis/lang/en_us.json")) {
            assertNotNull(in, "lang/en_us.json is in the mod");
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Map<String, String> file = new LinkedHashMap<>();
        Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        while (m.find()) file.put(m.group(1), m.group(2));
        assertEquals(HuntTexts.EN.size() + 2, file.size(), "the hunt keys, the death message and the rumble block name");
        assertEquals(HuntTexts.DEATH_TEXT, file.get(HuntTexts.DEATH_KEY));
        for (Map.Entry<String, String> e : HuntTexts.EN.entrySet()) assertEquals(e.getValue(), file.get(HuntTexts.PREFIX + e.getKey()), e.getKey());
        assertTrue(HuntTexts.PREFIX.startsWith("islandcraft_wormsofarrakis."));
    }

    @Test
    void everyConfigKeyIsListedForTheLog() {
        String all = String.join(" ", new HuntConfig().describe());
        for (String k : new String[]{"AggroThreshold", "MinGainSpeed", "AggroMultiplierWalk", "AggroMultiplierSprint", "AggroMultiplierSprintJump", "AggroMultiplierMax", "StillDecayPerSecond",
                "OffSandDecayMultiplier", "TeleportSpeed", "SandBlocks", "SandTolerance", "ShallowPenalty", "ShallowLogSeconds", "DepthShallowMax", "DepthSmallMax", "DepthMediumMax", "DepthLargeMax",
                "StalkExtraSeconds", "AttackSeconds", "GroupRadius", "TremorMinSeconds", "TremorMaxSeconds", "TremorViewDistance", "BuildUpFraction", "BuildUpMaxParticlesPerSecond",
                "TremorParticleBudgetPerTick", "RumbleBlockBudgetPerTick", "RumbleMaxSixteenths", "TremorRadius", "RoarDistance", "RoarRangeMargin", "RoarFarPitch"})
            assertTrue(all.contains(k + "="), k);
        assertEquals(java.util.List.of("islandcraft_dunesofarrakis:dune_sand", "minecraft:sand", "minecraft:red_sand"), new HuntConfig().sandBlocks);
    }
}
