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
    static final ModConfigSpec.DoubleValue ATTACK_SECONDS = d("AttackSeconds", 4, 0, 3600, "The freeze: the victim slows to zero over this long, then the strike starts.");
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
    static final ModConfigSpec.IntValue TREMOR_PARTICLES = i("TremorParticles", 40, 1, 10000, "Particles of one tremor at strength 1 and a huge worm.");
    static final ModConfigSpec.DoubleValue TREMOR_VOLUME = d("TremorSoundVolume", 3.0, 0.1, 10, "Sound volume of a tremor at strength 1 and a huge worm (range = 16 x volume x size factor).");
    static final ModConfigSpec.IntValue RUMBLE_MAX = i("RumbleMaxSixteenths", 8, 1, 15, "The most a sand column rises or sinks in the rumble, in sixteenths of a block (at most 15).");
    static final ModConfigSpec.DoubleValue RUMBLE_PULSE = d("RumblePulseSeconds", 1.0, 0.6, 10, "Each column starts one pulse in every period of this length (at least 0.6 s so pulses never overlap).");
    static final ModConfigSpec.DoubleValue RUMBLE_EDGE = d("RumbleEdgeWeight", 0.25, 0, 1, "The strength weight at the edge of a rumble (1 at the centre, falling linearly).");
    static final ModConfigSpec.DoubleValue RUMBLE_MARGIN = d("RumbleViewMargin", 32, 0, 512, "Players within radius + this many blocks of a rumble see it (when it is played for everyone nearby).");
    static final ModConfigSpec.IntValue RUMBLE_BUDGET = i("RumbleBlockBudgetPerTick", 600, 1, 100000, "Fake cell updates of rumbles per tick for the whole server (every event thinned by one ratio when over, none starved).");
    static final ModConfigSpec.IntValue RUMBLE_DROPS = i("RumbleDropSteps", 2, 1, 4, "Descent steps at the start of a pulse.");
    static final ModConfigSpec.DoubleValue RUMBLE_STEP = d("RumbleStepSeconds", 0.06, 0.02, 0.5, "Length of one descent step.");
    static final ModConfigSpec.DoubleValue RUMBLE_SWING = d("RumbleSwingSeconds", 0.09, 0.03, 0.5, "Length of one swing of the rebound (plus or minus 25 percent).");
    static final ModConfigSpec.DoubleValue TREMOR_RADIUS = d("TremorRadius", 6, 1, 64, "Radius (blocks) of the sand rumble of one stalk tremor at a huge worm; smaller worms scale it by their size factor (at least 3).");
    static final ModConfigSpec.DoubleValue ROAR_MARGIN = d("RoarRangeMargin", 1.15, 1, 4, "The roar volume is chosen so that the range (16 x volume) reaches the trigger player with this margin.");
    static final ModConfigSpec.DoubleValue ROAR_PITCH = d("RoarFarPitch", 0.9, 0.5, 1.5, "The pitch of the distant roar is the crash roar pitch times this.");
    static final ModConfigSpec.DoubleValue[] ROAR_DIST = new ModConfigSpec.DoubleValue[]{d("RoarDistance.small", 50, 5, 1000, "Distance (blocks) of the roar from the trigger player, W8 worm."), d("RoarDistance.medium", 70, 5, 1000, "W16 worm."), d("RoarDistance.large", 90, 5, 1000, "W32 worm."), d("RoarDistance.huge", 110, 5, 1000, "W48 worm.")};
    static {
        B.pop();
    }

    static final ModConfigSpec.DoubleValue[] WEIGHT = new ModConfigSpec.DoubleValue[4];
    static final ModConfigSpec.IntValue[] AHEAD = new ModConfigSpec.IntValue[4], BEHIND = new ModConfigSpec.IntValue[4], TOL = new ModConfigSpec.IntValue[4];
    static final ModConfigSpec.DoubleValue[][] DEVOUR = new ModConfigSpec.DoubleValue[4][4];
    static final ModConfigSpec.ConfigValue<String> YAW_MODE;
    static final ModConfigSpec.IntValue SCAN_COLUMNS;
    static {
        HuntConfig def = new HuntConfig();
        String[] mv = {"crest", "crash", "emerge", "engulf"}, sz = {"small", "medium", "large", "huge"};
        B.push("strike");
        B.comment("The odds of the four strike types; rescaled over the types available at the victim's place (crash and emerge always are).").push("StrikeWeights");
        for (int t = 0; t < 4; t++) WEIGHT[t] = d(mv[t], def.strikeWeights[t], 0, 100000, "Weight of the " + mv[t] + ".");
        B.pop();
        B.comment("Crest corridor: sand needed ahead of the victim (blocks), per size (5.2 W).").push("CrestAhead");
        for (int s = 0; s < 4; s++) AHEAD[s] = i(sz[s], def.crestAhead[s], 1, 10000, "Blocks ahead, " + sz[s] + ".");
        B.pop();
        B.comment("Crest corridor: sand needed behind the victim (blocks), per size (2 W).").push("CrestBehind");
        for (int s = 0; s < 4; s++) BEHIND[s] = i(sz[s], def.crestBehind[s], 0, 10000, "Blocks behind, " + sz[s] + ".");
        B.pop();
        B.comment("Crest corridor: how far a sample's top block may differ in height from the victim's ground (blocks), per size (0.25 W).").push("CrestTolerance");
        for (int s = 0; s < 4; s++) TOL[s] = i(sz[s], def.crestTolerance[s], 0, 1000, "Height tolerance, " + sz[s] + ".");
        B.pop();
        YAW_MODE = B.comment("cardinal: crash, emerge and engulf face one of N E S W at random; random: any yaw.").define("StrikeYawMode", "cardinal", o -> "cardinal".equals(o) || "random".equals(o));
        SCAN_COLUMNS = i("ScanColumnsPerTick", def.scanColumnsPerTick, 1, 100000, "Columns the corridor scan reads per tick (spread over the last quarter of the freeze).");
        B.comment("Seconds after the move starts at which the mouth encloses the victim and they are killed (derived from the move's own frames; tune here).").push("DevourSeconds");
        for (int t = 0; t < 4; t++) {
            B.push(mv[t]);
            for (int s = 0; s < 4; s++) DEVOUR[t][s] = d(sz[s], def.devourSeconds[t][s], 0, 600, "Devour time of the " + mv[t] + ", " + sz[s] + ".");
            B.pop();
        }
        B.pop();
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
            c.tremorParticles = TREMOR_PARTICLES.get();
            c.tremorSoundVolume = TREMOR_VOLUME.get();
            c.rumbleMaxSixteenths = RUMBLE_MAX.get();
            c.rumblePulseSeconds = RUMBLE_PULSE.get();
            c.rumbleEdgeWeight = RUMBLE_EDGE.get();
            c.rumbleViewMargin = RUMBLE_MARGIN.get();
            c.rumbleBlockBudgetPerTick = RUMBLE_BUDGET.get();
            c.rumbleDropSteps = RUMBLE_DROPS.get();
            c.rumbleStepSeconds = RUMBLE_STEP.get();
            c.rumbleSwingSeconds = RUMBLE_SWING.get();
            c.tremorRadius = TREMOR_RADIUS.get();
            c.roarRangeMargin = ROAR_MARGIN.get();
            c.roarFarPitch = ROAR_PITCH.get();
            for (int s = 0; s < 4; s++) c.roarDistance[s] = ROAR_DIST[s].get();
            for (int t = 0; t < 4; t++) c.strikeWeights[t] = WEIGHT[t].get();
            for (int s = 0; s < 4; s++) { c.crestAhead[s] = AHEAD[s].get(); c.crestBehind[s] = BEHIND[s].get(); c.crestTolerance[s] = TOL[s].get(); }
            for (int t = 0; t < 4; t++) for (int s = 0; s < 4; s++) c.devourSeconds[t][s] = DEVOUR[t][s].get();
            c.strikeYawMode = YAW_MODE.get();
            c.scanColumnsPerTick = SCAN_COLUMNS.get();
            return true;
        } catch (RuntimeException e) {
            return false;   // not loaded yet: the defaults stay
        }
    }
}
