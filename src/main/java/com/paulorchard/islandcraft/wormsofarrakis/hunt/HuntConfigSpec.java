package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

/** Prompt 13: the mod's config file (config/islandcraft_wormsofarrakis-common.toml); every key of the hunt, with the owner's defaults. {@link #refresh} copies the values into a {@link HuntConfig}. */
public final class HuntConfigSpec {
    private HuntConfigSpec() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    private static ModConfigSpec.DoubleValue d(String key, double def, double min, double max, String comment) { return B.comment(comment).defineInRange(key, def, min, max); }
    private static ModConfigSpec.IntValue i(String key, int def, int min, int max, String comment) { return B.comment(comment).defineInRange(key, def, min, max); }

    static {
        B.push("aggro");
    }
    static final ModConfigSpec.DoubleValue AGGRO_THRESHOLD = d("AggroThreshold", 100, 1, 100000, "The score at which a player on sand becomes a trigger candidate.");
    static final ModConfigSpec.DoubleValue MIN_GAIN_SPEED = d("MinGainSpeed", 2.0, 0, 50, "Below this speed (blocks per second) a player gains nothing (counts as standing still).");
    static final ModConfigSpec.DoubleValue MUL_WALK = d("AggroMultiplierWalk", 1.0, 0, 50, "Aggro per block walked.");
    static final ModConfigSpec.DoubleValue MUL_SPRINT = d("AggroMultiplierSprint", 2.0, 0, 50, "Aggro per block sprinted.");
    static final ModConfigSpec.DoubleValue MUL_SPRINT_JUMP = d("AggroMultiplierSprintJump", 2.5, 0, 50, "Aggro per block sprint-jumped (sprinting and airborne).");
    static final ModConfigSpec.DoubleValue MUL_MAX = d("AggroMultiplierMax", 20, 1, 1000, "The most the effect multipliers can multiply the aggro gained.");
    static final ModConfigSpec.DoubleValue STILL_DECAY = d("StillDecayPerSecond", 1.0, 0, 1000, "Score lost per second by a player who gained nothing (not in an event).");
    static final ModConfigSpec.DoubleValue OFF_SAND_DECAY = d("OffSandDecayMultiplier", 10, 1, 1000, "Decay is this many times faster off sand.");
    static final ModConfigSpec.DoubleValue TELEPORT_SPEED = d("TeleportSpeed", 30, 1, 10000, "A move faster than this (blocks per second) is a teleport and is never aggro.");
    static {
        B.pop();
        B.push("sand");
    }
    static final ModConfigSpec.ConfigValue<List<? extends String>> SAND_BLOCKS = B.comment("Block ids that count as sand. Ids that do not exist are skipped (Dunes of Arrakis not installed: vanilla sand and red sand remain).")
            .defineListAllowEmpty("SandBlocks", List.of("islandcraft_dunesofarrakis:dune_sand", "minecraft:sand", "minecraft:red_sand"), () -> "minecraft:sand", o -> o instanceof String);
    static final ModConfigSpec.DoubleValue SAND_TOLERANCE = d("SandTolerance", 0.3, 0, 2, "How far above the sand surface (blocks, a step) a player still counts as on sand.");
    static final ModConfigSpec.DoubleValue SHALLOW_PENALTY = d("ShallowPenalty", 10, 0, 100000, "Score lost when a player reaches the threshold on sand that is too shallow.");
    static final ModConfigSpec.DoubleValue SHALLOW_LOG = d("ShallowLogSeconds", 10, 0, 3600, "The shallow-sand log line is written once per player per this many seconds.");
    static final ModConfigSpec.IntValue DEPTH_SHALLOW = i("DepthShallowMax", 4, 0, 1000, "Depth D up to this: no event (too shallow).");
    static final ModConfigSpec.IntValue DEPTH_SMALL = i("DepthSmallMax", 8, 1, 1000, "Depth up to this: a small worm (width 8).");
    static final ModConfigSpec.IntValue DEPTH_MEDIUM = i("DepthMediumMax", 16, 1, 1000, "Depth up to this: a medium worm (width 16).");
    static final ModConfigSpec.IntValue DEPTH_LARGE = i("DepthLargeMax", 32, 1, 1000, "Depth up to this: a large worm (width 32); deeper: huge (width 48).");
    static {
        B.pop();
        B.push("event");
    }
    static final ModConfigSpec.DoubleValue STALK_EXTRA = d("StalkExtraSeconds", 8, 0, 3600, "The stalk lasts the width of the worm plus this many seconds.");
    static final ModConfigSpec.DoubleValue ATTACK_SECONDS = d("AttackSeconds", 4, 0, 3600, "The attack phase (a placeholder until the strike exists).");
    static final ModConfigSpec.DoubleValue GROUP_RADIUS = d("GroupRadius", 200, 1, 100000, "Players within this horizontal distance of the trigger are put in the list.");
    static {
        B.pop();
        B.push("tremor");
    }
    static final ModConfigSpec.DoubleValue TREMOR_MIN = d("TremorMinSeconds", 3.0, 0.1, 3600, "The shortest gap between two tremors of one player.");
    static final ModConfigSpec.DoubleValue TREMOR_MAX = d("TremorMaxSeconds", 6.0, 0.1, 3600, "The longest gap between two tremors of one player.");
    static final ModConfigSpec.DoubleValue TREMOR_VIEW = d("TremorViewDistance", 32, 1, 512, "Fake blocks of a tremor are sent to players within this distance.");
    static final ModConfigSpec.DoubleValue BUILD_UP_FRACTION = d("BuildUpFraction", 0.5, 0.01, 1, "The last fraction of the stalk in which the continuous build-up runs.");
    static final ModConfigSpec.DoubleValue BUILD_UP_MAX = d("BuildUpMaxParticlesPerSecond", 60, 0, 100000, "Sand grains per second round a player's feet at the end of the stalk (full size factor).");
    static final ModConfigSpec.IntValue PARTICLE_BUDGET = i("TremorParticleBudgetPerTick", 400, 1, 100000, "Particles of tremors and build-up per tick for the whole server (every player thinned by one ratio when over).");
    static final ModConfigSpec.IntValue BLOCK_BUDGET = i("TremorBlockBudgetPerTick", 600, 1, 100000, "Fake block updates of tremors per tick for the whole server (every player thinned by one ratio when over).");
    static final ModConfigSpec.DoubleValue RIPPLE_RADIUS = d("TremorRippleRadius", 12, 2.5, 64, "Ripple radius (blocks) at strength 1 and a huge worm.");
    static final ModConfigSpec.IntValue RIPPLE_LAYERS = i("TremorRippleLayers", 2, 1, 4, "Ripple height (fake layers of sand) at strength 1.");
    static final ModConfigSpec.IntValue TREMOR_PARTICLES = i("TremorParticles", 40, 1, 10000, "Particles of one tremor at strength 1 and a huge worm.");
    static final ModConfigSpec.DoubleValue TREMOR_VOLUME = d("TremorSoundVolume", 3.0, 0.1, 10, "Sound volume of a tremor at strength 1 and a huge worm (range = 16 x volume x size factor).");
    static final ModConfigSpec.IntValue RIPPLE_TICKS = i("TremorRippleTicks", 20, 4, 200, "How long a ripple lasts in ticks.");
    static {
        B.pop();
    }

    public static final ModConfigSpec SPEC = B.build();

    /** Copies the config values into {@code c}; the defaults stay when the config is not loaded yet. Returns true when it was loaded. */
    public static boolean refresh(HuntConfig c) {
        try {
            c.aggroThreshold = AGGRO_THRESHOLD.get();
            c.minGainSpeed = MIN_GAIN_SPEED.get();
            c.aggroMultiplierWalk = MUL_WALK.get();
            c.aggroMultiplierSprint = MUL_SPRINT.get();
            c.aggroMultiplierSprintJump = MUL_SPRINT_JUMP.get();
            c.aggroMultiplierMax = MUL_MAX.get();
            c.stillDecayPerSecond = STILL_DECAY.get();
            c.offSandDecayMultiplier = OFF_SAND_DECAY.get();
            c.teleportSpeed = TELEPORT_SPEED.get();
            c.sandBlocks = new ArrayList<>(SAND_BLOCKS.get());
            c.sandTolerance = SAND_TOLERANCE.get();
            c.shallowPenalty = SHALLOW_PENALTY.get();
            c.shallowLogSeconds = SHALLOW_LOG.get();
            c.depthShallowMax = DEPTH_SHALLOW.get();
            c.depthSmallMax = DEPTH_SMALL.get();
            c.depthMediumMax = DEPTH_MEDIUM.get();
            c.depthLargeMax = DEPTH_LARGE.get();
            c.stalkExtraSeconds = STALK_EXTRA.get();
            c.attackSeconds = ATTACK_SECONDS.get();
            c.groupRadius = GROUP_RADIUS.get();
            c.tremorMinSeconds = TREMOR_MIN.get();
            c.tremorMaxSeconds = Math.max(TREMOR_MAX.get(), c.tremorMinSeconds);
            c.tremorViewDistance = TREMOR_VIEW.get();
            c.buildUpFraction = BUILD_UP_FRACTION.get();
            c.buildUpMaxParticlesPerSecond = BUILD_UP_MAX.get();
            c.tremorParticleBudgetPerTick = PARTICLE_BUDGET.get();
            c.tremorBlockBudgetPerTick = BLOCK_BUDGET.get();
            c.tremorRippleRadius = RIPPLE_RADIUS.get();
            c.tremorRippleLayers = RIPPLE_LAYERS.get();
            c.tremorParticles = TREMOR_PARTICLES.get();
            c.tremorSoundVolume = TREMOR_VOLUME.get();
            c.tremorRippleTicks = RIPPLE_TICKS.get();
            return true;
        } catch (RuntimeException e) {
            return false;   // not loaded yet: the defaults stay
        }
    }
}
