package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import java.util.ArrayList;
import java.util.List;

/**
 * Prompt 13: every number of the hunt (aggro, depth, roster, stalk, tremors). Pure data with the owner's defaults; the game side fills it from the mod's config file
 * ({@code HuntConfigSpec}). The field names are the config keys of the prompt in lower camel case (AggroThreshold = aggroThreshold).
 */
public final class HuntConfig {
    // aggro
    public double aggroThreshold = 100;
    public double minGainSpeed = 2.0;
    public double aggroMultiplierWalk = 1.0, aggroMultiplierSprint = 2.0, aggroMultiplierSprintJump = 2.5;
    public double aggroMultiplierMax = 20;
    public double stillDecayPerSecond = 1.0;
    public double offSandDecayMultiplier = 10;
    public double teleportSpeed = 30;
    // sand and depth
    /** Block ids that count as sand: the Dune sand of the Dunes of Arrakis mod first, then vanilla sand and red sand. */
    public List<String> sandBlocks = new ArrayList<>(List.of("islandcraft_dunesofarrakis:dune_sand", "minecraft:sand", "minecraft:red_sand"));
    public double sandTolerance = 0.3;
    public double shallowPenalty = 10;
    public double shallowLogSeconds = 10;
    /** The depth D thresholds: up to this is shallow (no event), small, medium, large; above the last is huge. */
    public int depthShallowMax = 4, depthSmallMax = 8, depthMediumMax = 16, depthLargeMax = 32;
    // event
    public double stalkExtraSeconds = 8;
    public double attackSeconds = 4;
    public double groupRadius = 200;
    // tremors
    public double tremorMinSeconds = 3.0, tremorMaxSeconds = 6.0;
    public double tremorViewDistance = 32;
    public double buildUpFraction = 0.5;
    public double buildUpMaxParticlesPerSecond = 60;
    public int tremorParticleBudgetPerTick = 400;
    // prompt 15: the sand rumble, the tremor radius and the distant roar
    public int rumbleMaxSixteenths = 8, rumbleBlockBudgetPerTick = 600, rumbleDropSteps = 2;
    public double rumblePulseSeconds = 1.0, rumbleEdgeWeight = 0.25, rumbleViewMargin = 32, rumbleStepSeconds = 0.06, rumbleSwingSeconds = 0.09, tremorRadius = 6;
    public double[] roarDistance = {50, 70, 90, 110};
    public double roarRangeMargin = 1.15, roarFarPitch = 0.9;
    public int tremorParticles = 40;
    public double tremorSoundVolume = 3.0;

    // strike (prompt 14)
    /** The odds of the four types, indexed by StrikeType (crest, crash, emerge, engulf); rescaled over the available ones. */
    public double[] strikeWeights = {30, 25, 25, 20};
    /** The crest corridor per size (small, medium, large, huge): blocks ahead (5.2 W), behind (2 W) and the height tolerance (0.25 W). */
    public int[] crestAhead = {42, 83, 166, 250}, crestBehind = {16, 32, 64, 96}, crestTolerance = {2, 4, 8, 12};
    /** cardinal (one of N E S W) or random (any yaw) for crash, emerge and engulf. */
    public String strikeYawMode = "cardinal";
    /** Seconds after the move starts at which the mouth encloses the victim: [type crest, crash, emerge, engulf][size small, medium, large, huge]. */
    public double[][] devourSeconds = {{2.92, 4.58, 7.91, 11.26}, {4.14, 7.45, 14.08, 20.70}, {3.79, 5.84, 9.93, 14.02}, {3.17, 5.01, 8.94, 12.86}};
    /** Columns the corridor scan reads per tick (it is spread over the last quarter of the freeze). */
    public int scanColumnsPerTick = 60;

    /** Every key and its value, one line each, for the log. */
    public List<String> describe() {
        List<String> l = new ArrayList<>();
        l.add("AggroThreshold=" + aggroThreshold);
        l.add("MinGainSpeed=" + minGainSpeed);
        l.add("AggroMultiplierWalk=" + aggroMultiplierWalk + " AggroMultiplierSprint=" + aggroMultiplierSprint + " AggroMultiplierSprintJump=" + aggroMultiplierSprintJump);
        l.add("AggroMultiplierMax=" + aggroMultiplierMax);
        l.add("StillDecayPerSecond=" + stillDecayPerSecond + " OffSandDecayMultiplier=" + offSandDecayMultiplier);
        l.add("TeleportSpeed=" + teleportSpeed);
        l.add("SandBlocks=" + sandBlocks + " SandTolerance=" + sandTolerance);
        l.add("ShallowPenalty=" + shallowPenalty + " ShallowLogSeconds=" + shallowLogSeconds);
        l.add("DepthShallowMax=" + depthShallowMax + " DepthSmallMax=" + depthSmallMax + " DepthMediumMax=" + depthMediumMax + " DepthLargeMax=" + depthLargeMax);
        l.add("StalkExtraSeconds=" + stalkExtraSeconds + " AttackSeconds=" + attackSeconds + " GroupRadius=" + groupRadius);
        l.add("TremorMinSeconds=" + tremorMinSeconds + " TremorMaxSeconds=" + tremorMaxSeconds + " TremorViewDistance=" + tremorViewDistance);
        l.add("BuildUpFraction=" + buildUpFraction + " BuildUpMaxParticlesPerSecond=" + buildUpMaxParticlesPerSecond);
        l.add("TremorParticleBudgetPerTick=" + tremorParticleBudgetPerTick + "");
        l.add("TremorParticles=" + tremorParticles + " TremorSoundVolume=" + tremorSoundVolume);
        l.add("StrikeWeights crest=" + strikeWeights[0] + " crash=" + strikeWeights[1] + " emerge=" + strikeWeights[2] + " engulf=" + strikeWeights[3] + " StrikeYawMode=" + strikeYawMode + " ScanColumnsPerTick=" + scanColumnsPerTick);
        l.add("CrestAhead=" + java.util.Arrays.toString(crestAhead) + " CrestBehind=" + java.util.Arrays.toString(crestBehind) + " CrestTolerance=" + java.util.Arrays.toString(crestTolerance) + " (small, medium, large, huge)");
        String[] mv = {"crest", "crash", "emerge", "engulf"}, sz = {"small", "medium", "large", "huge"};
        l.add("RumbleMaxSixteenths=" + rumbleMaxSixteenths + " RumblePulseSeconds=" + rumblePulseSeconds + " RumbleEdgeWeight=" + rumbleEdgeWeight + " RumbleViewMargin=" + rumbleViewMargin + " RumbleBlockBudgetPerTick=" + rumbleBlockBudgetPerTick + " RumbleDropSteps=" + rumbleDropSteps + " RumbleStepSeconds=" + rumbleStepSeconds + " RumbleSwingSeconds=" + rumbleSwingSeconds + " TremorRadius=" + tremorRadius);
        l.add("RoarDistance=" + java.util.Arrays.toString(roarDistance) + " (small, medium, large, huge) RoarRangeMargin=" + roarRangeMargin + " RoarFarPitch=" + roarFarPitch);
        for (int t = 0; t < 4; t++) for (int s = 0; s < 4; s++) l.add("DevourSeconds." + mv[t] + "." + sz[s] + "=" + devourSeconds[t][s]);
        return l;
    }
}
