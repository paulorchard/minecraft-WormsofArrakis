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
    public int tremorBlockBudgetPerTick = 600;
    /** The ripple radius (blocks), the ripple height (layers), the particles per tremor and the sound volume at strength 1 and the size factor 1. */
    public double tremorRippleRadius = 12;
    public int tremorRippleLayers = 2;
    public int tremorParticles = 40;
    public double tremorSoundVolume = 3.0;
    /** The ripple lasts this many ticks (1 s). */
    public int tremorRippleTicks = 20;

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
        l.add("TremorParticleBudgetPerTick=" + tremorParticleBudgetPerTick + " TremorBlockBudgetPerTick=" + tremorBlockBudgetPerTick);
        l.add("TremorRippleRadius=" + tremorRippleRadius + " TremorRippleLayers=" + tremorRippleLayers + " TremorParticles=" + tremorParticles + " TremorSoundVolume=" + tremorSoundVolume + " TremorRippleTicks=" + tremorRippleTicks);
        return l;
    }
}
