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

    // prompt 16: the attack rumble, the deadly worm and the death camera
    public double attackRumbleStart = 0.7, attackRumbleRadius = 10;
    public int attackRumbleMaxSixteenths = 12;
    public double hazardSkin = 0.3, throatReach = 0.4, hazardTimeOffsetSeconds = 0, throatBackupSeconds = 1.5;
    public String hazardParts = "body,head,throat";
    public int hazardSubsteps = 4, hazardEntitiesPerTick = 200;
    public boolean hazardKillsPlayers = true, hazardKillsMobs = true, hazardOnTestWorms = false;
    public double[] deathCamDistance = {24, 40, 70, 100};
    public double deathCamTurnDegrees = 120, deathCamMaxSeconds = 60, lookHeight = 0.25, deathCamHeightFactor = 0.45, deathCamMinHeight = 8, deathCamSandClearance = 3;
    // prompt 17: the strike group
    public double[][] strikeAreaRadius = new double[4][4];
    public double strikeAreaMargin = 0, attackRumbleFollowSeconds = 0.25, swallowMaxSeconds = 0;
    public boolean groupReleaseOutside = true, hazardPlayersImmuneOffSand = true, hazardMobsImmuneOffSand = true;
    public String victimParts = "throat", othersParts = "body,head,throat";
    // prompt 18: the discs and the strike camera (HazardParts, VictimParts, OthersParts and the DeathCam* keys are retired / superseded and only read to warn)
    public boolean throatDiscEnabled = true, mouthDiscEnabled = true;
    public double throatDiscRadiusFraction = 0.95, mouthDiscFraction = 0.5, mouthDiscRadiusFraction = 0.8, discThickness = 0.5;
    public double strikeCamFlareFactor = 0.7, strikeCamClearanceFactor = 0.5, strikeCamFov = 75, strikeCamFill = 0.85, strikeCamMaxDistance = 120, strikeCamSweepDegrees = 120, strikeCamLookHeight = 1.0,
            strikeCamPushPerTick = 1.5, strikeCamMaxSpeed = 2.0, strikeCamMinHeight = 3, strikeCamHeightFactor = 0.5, strikeCamMaxSeconds = 60;

    public com.paulorchard.islandcraft.wormsofarrakis.hazard.Discs.Spec discSpec() {
        return new com.paulorchard.islandcraft.wormsofarrakis.hazard.Discs.Spec(throatDiscEnabled, mouthDiscEnabled, throatDiscRadiusFraction, mouthDiscFraction, mouthDiscRadiusFraction, discThickness);
    }

    public com.paulorchard.islandcraft.wormsofarrakis.camera.StrikeCam.Params strikeCam() {
        return new com.paulorchard.islandcraft.wormsofarrakis.camera.StrikeCam.Params(strikeCamFlareFactor, strikeCamClearanceFactor, strikeCamFov, strikeCamFill, strikeCamMaxDistance, strikeCamSweepDegrees, strikeCamLookHeight, strikeCamPushPerTick, strikeCamMaxSpeed, strikeCamMinHeight, strikeCamHeightFactor, strikeCamMaxSeconds);
    }

    /** The retired and superseded keys whose value differs from the default: they no longer do anything (prompt 18); one warning line each, logged once at start-up. */
    public java.util.List<String> retiredKeysInUse() {
        java.util.List<String> l = new java.util.ArrayList<>();
        if (!java.util.Arrays.equals(strikeWeights, new double[]{30, 25, 25, 20})) l.add("StrikeWeights.crest/crash/emerge/engulf (retired single set: the per-size tables StrikeWeights.<size> replace it; the old numbers are ignored)");
        if (!"body,head,throat".equals(hazardParts)) l.add("HazardParts (retired: the body, head and mandibles are harmless; use ThroatDiscEnabled and MouthDiscEnabled)");
        if (!"throat".equals(victimParts)) l.add("VictimParts (retired: the throat disc swallows)");
        if (!"body,head,throat".equals(othersParts)) l.add("OthersParts (retired: the throat disc swallows)");
        if (!java.util.Arrays.equals(deathCamDistance, new double[]{24, 40, 70, 100})) l.add("DeathCamDistance.<size> (superseded by StrikeCamFlareFactor, StrikeCamClearanceFactor, StrikeCamFill, StrikeCamMaxDistance)");
        if (deathCamTurnDegrees != 120) l.add("DeathCamTurnDegrees (superseded by StrikeCamSweepDegrees)");
        if (deathCamMaxSeconds != 60) l.add("DeathCamMaxSeconds (superseded by StrikeCamMaxSeconds)");
        if (lookHeight != 0.25) l.add("LookHeight (superseded by StrikeCamLookHeight)");
        if (deathCamHeightFactor != 0.45) l.add("DeathCamHeightFactor (superseded by StrikeCamHeightFactor)");
        if (deathCamMinHeight != 8) l.add("DeathCamMinHeight (superseded by StrikeCamMinHeight)");
        if (deathCamSandClearance != 3) l.add("DeathCamSandClearance (superseded by the clearance rule)");
        return l;
    }
    public int strikeChunkTicketMax = 400;

    // strike (prompt 14)
    /** The odds of the four types, indexed by StrikeType (crest, crash, emerge, engulf); rescaled over the available ones. */
    /** RETIRED (prompt 19): the single weight set of prompts 14 to 18; only read to warn. */
    public double[] strikeWeights = {30, 25, 25, 20};

    /** The weights per size [small, medium, large, huge][crest, crash, emerge, engulf] (prompt 19). */
    public static final double[][] DEFAULT_WEIGHTS = {{10, 70, 10, 10}, {70, 5, 5, 20}, {70, 5, 5, 20}, {0, 0, 0, 100}};
    public double[][] strikeWeightsBySize = {DEFAULT_WEIGHTS[0].clone(), DEFAULT_WEIGHTS[1].clone(), DEFAULT_WEIGHTS[2].clone(), DEFAULT_WEIGHTS[3].clone()};
    public String[] fallbackSize = {"small", "medium", "large", "medium"};
    public String[] fallbackType = {"crash", "crash", "crash", "crash"};

    private static boolean badRow(double[] w) {
        double sum = 0;
        for (double v : w) { if (v < 0) return true; sum += v; }
        return sum <= 0;
    }

    /** The effective weights of a size [crest, crash, emerge, engulf]: the configured row, or the default row when it is bad (a negative number or a sum of 0). */
    public double[] weightsFor(SizeTable.Size size) {
        double[] w = strikeWeightsBySize[size.ordinal()];
        return badRow(w) ? DEFAULT_WEIGHTS[size.ordinal()].clone() : w.clone();
    }

    /** One line for every bad weight table or fallback key: what was wrong and what is used instead (logged at start-up). */
    public java.util.List<String> weightProblems() {
        java.util.List<String> l = new java.util.ArrayList<>();
        for (SizeTable.Size s : SizeTable.Size.values()) {
            if (badRow(strikeWeightsBySize[s.ordinal()])) l.add("StrikeWeights." + s.label + " " + java.util.Arrays.toString(strikeWeightsBySize[s.ordinal()]) + " is refused (a negative number or a sum of 0): the default " + java.util.Arrays.toString(DEFAULT_WEIGHTS[s.ordinal()]) + " is used");
            if (SizeTable.Size.of(fallbackSize[s.ordinal()].toLowerCase(java.util.Locale.ROOT)) == null) l.add("FallbackSize." + s.label + " '" + fallbackSize[s.ordinal()] + "' is not small, medium, large or huge: the default " + fallbackSizeFor(s).label + " is used");
            StrikeType t = StrikeType.of(fallbackType[s.ordinal()].toLowerCase(java.util.Locale.ROOT));
            if (t != StrikeType.CRASH && t != StrikeType.EMERGE) l.add("FallbackType." + s.label + " '" + fallbackType[s.ordinal()] + "' is not crash or emerge (the always available types): crash is used");
        }
        return l;
    }

    /** The size of the strike when no weighted type is available for the event's size. */
    public SizeTable.Size fallbackSizeFor(SizeTable.Size size) {
        SizeTable.Size f = SizeTable.Size.of(fallbackSize[size.ordinal()].toLowerCase(java.util.Locale.ROOT));
        if (f != null) return f;
        return size == SizeTable.Size.HUGE ? SizeTable.Size.MEDIUM : size;
    }

    /** The type of that fallback: crash or emerge only (crash otherwise). */
    public StrikeType fallbackTypeFor(SizeTable.Size size) {
        StrikeType t = StrikeType.of(fallbackType[size.ordinal()].toLowerCase(java.util.Locale.ROOT));
        return t == StrikeType.EMERGE ? StrikeType.EMERGE : StrikeType.CRASH;
    }
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
        for (SizeTable.Size s : SizeTable.Size.values()) l.add("StrikeWeights." + s.label + " crest=" + weightsFor(s)[0] + " crash=" + weightsFor(s)[1] + " emerge=" + weightsFor(s)[2] + " engulf=" + weightsFor(s)[3] + " FallbackSize=" + fallbackSizeFor(s).label + " FallbackType=" + fallbackTypeFor(s).label);
        l.add("StrikeYawMode=" + strikeYawMode + " ScanColumnsPerTick=" + scanColumnsPerTick);
        l.add("CrestAhead=" + java.util.Arrays.toString(crestAhead) + " CrestBehind=" + java.util.Arrays.toString(crestBehind) + " CrestTolerance=" + java.util.Arrays.toString(crestTolerance) + " (small, medium, large, huge)");
        String[] mv = {"crest", "crash", "emerge", "engulf"}, sz = {"small", "medium", "large", "huge"};
        l.add("RumbleMaxSixteenths=" + rumbleMaxSixteenths + " RumblePulseSeconds=" + rumblePulseSeconds + " RumbleEdgeWeight=" + rumbleEdgeWeight + " RumbleViewMargin=" + rumbleViewMargin + " RumbleBlockBudgetPerTick=" + rumbleBlockBudgetPerTick + " RumbleDropSteps=" + rumbleDropSteps + " RumbleStepSeconds=" + rumbleStepSeconds + " RumbleSwingSeconds=" + rumbleSwingSeconds + " TremorRadius=" + tremorRadius);
        l.add("RoarDistance=" + java.util.Arrays.toString(roarDistance) + " (small, medium, large, huge) RoarRangeMargin=" + roarRangeMargin + " RoarFarPitch=" + roarFarPitch);
        l.add("AttackRumbleStart=" + attackRumbleStart + " AttackRumbleRadius=" + attackRumbleRadius + " AttackRumbleMaxSixteenths=" + attackRumbleMaxSixteenths);
        l.add("HazardSkin=" + hazardSkin + " ThroatReach=" + throatReach + " HazardParts=" + hazardParts + " HazardSubsteps=" + hazardSubsteps + " HazardTimeOffsetSeconds=" + hazardTimeOffsetSeconds + " HazardEntitiesPerTick=" + hazardEntitiesPerTick
                + " HazardKillsPlayers=" + hazardKillsPlayers + " HazardKillsMobs=" + hazardKillsMobs + " HazardOnTestWorms=" + hazardOnTestWorms + " ThroatBackupSeconds=" + throatBackupSeconds);
        l.add("DeathCamDistance=" + java.util.Arrays.toString(deathCamDistance) + " (small, medium, large, huge) DeathCamTurnDegrees=" + deathCamTurnDegrees + " DeathCamMaxSeconds=" + deathCamMaxSeconds + " LookHeight=" + lookHeight
                + " DeathCamHeightFactor=" + deathCamHeightFactor + " DeathCamMinHeight=" + deathCamMinHeight + " DeathCamSandClearance=" + deathCamSandClearance);
        l.add("ThroatDiscEnabled=" + throatDiscEnabled + " MouthDiscEnabled=" + mouthDiscEnabled + " ThroatDiscRadiusFraction=" + throatDiscRadiusFraction + " MouthDiscFraction=" + mouthDiscFraction + " MouthDiscRadiusFraction=" + mouthDiscRadiusFraction + " DiscThickness=" + discThickness);
        l.add("StrikeCamFlareFactor=" + strikeCamFlareFactor + " StrikeCamClearanceFactor=" + strikeCamClearanceFactor + " StrikeCamFov=" + strikeCamFov + " StrikeCamFill=" + strikeCamFill + " StrikeCamMaxDistance=" + strikeCamMaxDistance + " StrikeCamSweepDegrees=" + strikeCamSweepDegrees + " StrikeCamLookHeight=" + strikeCamLookHeight
                + " StrikeCamPushPerTick=" + strikeCamPushPerTick + " StrikeCamMaxSpeed=" + strikeCamMaxSpeed + " StrikeCamMinHeight=" + strikeCamMinHeight + " StrikeCamHeightFactor=" + strikeCamHeightFactor + " StrikeCamMaxSeconds=" + strikeCamMaxSeconds);
        l.add("StrikeAreaMargin=" + strikeAreaMargin + " GroupReleaseOutside=" + groupReleaseOutside + " HazardPlayersImmuneOffSand=" + hazardPlayersImmuneOffSand + " HazardMobsImmuneOffSand=" + hazardMobsImmuneOffSand + " VictimParts=" + victimParts + " OthersParts=" + othersParts
                + " AttackRumbleFollowSeconds=" + attackRumbleFollowSeconds + " SwallowMaxSeconds=" + swallowMaxSeconds + " StrikeChunkTicketMax=" + strikeChunkTicketMax);
        for (int t = 0; t < 4; t++) for (int s = 0; s < 4; s++) if (strikeAreaRadius[t][s] > 0) l.add("StrikeAreaRadius." + mv[t] + "." + sz[s] + "=" + strikeAreaRadius[t][s] + " (override)");
        for (int t = 0; t < 4; t++) for (int s = 0; s < 4; s++) l.add("DevourSeconds." + mv[t] + "." + sz[s] + "=" + devourSeconds[t][s]);
        return l;
    }
}
