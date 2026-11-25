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
    static final ModConfigSpec.DoubleValue[][] WEIGHT2 = new ModConfigSpec.DoubleValue[4][4];
    @SuppressWarnings("unchecked")
    static final ModConfigSpec.ConfigValue<String>[] FB_SIZE = new ModConfigSpec.ConfigValue[4], FB_TYPE = new ModConfigSpec.ConfigValue[4];
    static final ModConfigSpec.IntValue[] AHEAD = new ModConfigSpec.IntValue[4], BEHIND = new ModConfigSpec.IntValue[4], TOL = new ModConfigSpec.IntValue[4];
    static final ModConfigSpec.DoubleValue[][] DEVOUR = new ModConfigSpec.DoubleValue[4][4];
    static final ModConfigSpec.ConfigValue<String> YAW_MODE;
    static final ModConfigSpec.IntValue SCAN_COLUMNS;
    static {
        HuntConfig def = new HuntConfig();
        String[] mv = {"crest", "crash", "emerge", "engulf"}, sz = {"small", "medium", "large", "huge"};
        B.push("strike");
        B.comment("The odds of the four strike types PER SIZE (the tables small, medium, large, huge); rescaled over the types available at the victim's place and with a weight above 0. 0 = never chosen for that size (and its availability is not even checked). A negative number or a table that sums to 0 is refused and the default table is used. The four keys directly under StrikeWeights are the RETIRED single set of prompts 14 to 18 and are ignored.").push("StrikeWeights");
        for (int t = 0; t < 4; t++) WEIGHT[t] = d(mv[t], def.strikeWeights[t], -100000, 100000, "RETIRED (prompt 19): replaced by the per-size tables below; ignored.");
        for (int s = 0; s < 4; s++) {
            B.push(sz[s]);
            for (int t = 0; t < 4; t++) WEIGHT2[s][t] = d(mv[t], HuntConfig.DEFAULT_WEIGHTS[s][t], -100000, 100000, "Weight of the " + mv[t] + " for the " + sz[s] + " worm.");
            B.pop();
        }
        B.pop();
        B.comment("When no type with a weight above 0 is available for the event's size, the strike is a worm of this size (huge: a medium).").push("FallbackSize");
        for (int s = 0; s < 4; s++) FB_SIZE[s] = B.comment("Fallback strike size for the " + sz[s] + " event (small, medium, large or huge).").define(sz[s], def.fallbackSize[s], o -> o instanceof String);
        B.pop();
        B.comment("The strike type of the fallback (only crash or emerge: the always available ones).").push("FallbackType");
        for (int s = 0; s < 4; s++) FB_TYPE[s] = B.comment("Fallback strike type for the " + sz[s] + " event (crash or emerge).").define(sz[s], def.fallbackType[s], o -> o instanceof String);
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

    // prompt 16
    static final ModConfigSpec.DoubleValue ATTACK_START, ATTACK_RADIUS, HAZ_SKIN, THROAT_REACH, HAZ_OFFSET, THROAT_BACKUP, CAM_TURN, CAM_MAX, CAM_LOOK, CAM_HFACTOR, CAM_HMIN, CAM_CLEAR;
    static final ModConfigSpec.IntValue ATTACK_MAX, HAZ_SUBSTEPS, HAZ_ENTITIES;
    static final ModConfigSpec.BooleanValue HAZ_PLAYERS, HAZ_MOBS, HAZ_TEST;
    static final ModConfigSpec.ConfigValue<String> HAZ_PARTS;
    static final ModConfigSpec.DoubleValue[] CAM_DIST = new ModConfigSpec.DoubleValue[4];
    // prompt 17
    static final ModConfigSpec.DoubleValue[][] AREA = new ModConfigSpec.DoubleValue[4][4];
    static final ModConfigSpec.DoubleValue AREA_MARGIN, FOLLOW, SWALLOW_MAX;
    static final ModConfigSpec.IntValue TICKET_MAX;
    static final ModConfigSpec.BooleanValue GROUP_RELEASE, HAZ_PL_SAND, HAZ_MOB_SAND;
    static final ModConfigSpec.ConfigValue<String> VICTIM_PARTS, OTHERS_PARTS;
    static final ModConfigSpec.BooleanValue THROAT_DISC_ON, MOUTH_DISC_ON;
    static final ModConfigSpec.DoubleValue DISC_THROAT_R, DISC_MOUTH_F, DISC_MOUTH_R, DISC_THICK, SC_FLARE, SC_CLEAR, SC_FOV, SC_FILL, SC_MAXDIST, SC_SWEEP, SC_LOOK, SC_PUSH, SC_SPEED, SC_HMIN, SC_HFACTOR, SC_MAXSEC;
    static {
        B.push("attack");
        ATTACK_START = d("AttackRumbleStart", 0.7, 0, 1, "Strength of the rumble round the victim at the start of the freeze; it rises linearly to 1.0 at the end of the freeze and stays 1.0 until the death.");
        ATTACK_RADIUS = d("AttackRumbleRadius", 10, 1, 64, "Radius (blocks) of the rumble round the victim at a huge worm; smaller worms scale it by their size factor (at least 5).");
        ATTACK_MAX = i("AttackRumbleMaxSixteenths", 12, 1, 15, "The most a column moves in the attack rumble (sixteenths), times 0.5 + 0.5 x the size factor.");
        B.pop();
        B.push("hazard");
        HAZ_SKIN = d("HazardSkin", 0.3, 0, 5, "Blocks added to the skin radius of the body and to the outer flaps of the head: how far from the worm still counts as touching it.");
        THROAT_REACH = d("ThroatReach", 0.4, 0, 5, "Blocks in front of the rim disc of the throat (head frame z from 0) that count as contact.");
        HAZ_PARTS = B.comment("Which shapes kill: any of body, head, throat (comma separated).").define("HazardParts", "body,head,throat", o -> o instanceof String);
        HAZ_SUBSTEPS = i("HazardSubsteps", 4, 1, 64, "Instants per tick at which the pose of the worm is tested between the previous tick and this one (a fast worm cannot skip a player).");
        HAZ_OFFSET = d("HazardTimeOffsetSeconds", 0, -2, 2, "Added to the script time of the worm for the contact test (the client pose lags the server by a little; tune here).");
        HAZ_ENTITIES = i("HazardEntitiesPerTick", 200, 1, 100000, "At most this many entities (nearest first) are tested per tick.");
        HAZ_PLAYERS = B.comment("Players in Survival or Adventure die when they touch the worm.").define("HazardKillsPlayers", true);
        HAZ_MOBS = B.comment("Living non-worm mobs die when they touch the worm.").define("HazardKillsMobs", true);
        HAZ_TEST = B.comment("Test worms (/arrakis-worm and /arrakis_worm_test) are deadly too; the strike worm always is.").define("HazardOnTestWorms", false);
        THROAT_BACKUP = d("ThroatBackupSeconds", 1.5, 0, 60, "The victim is killed this long after the DevourSeconds time if the worm has not touched them by then.");
        B.pop();
        B.push("deathcam");
        String[] szn = {"small", "medium", "large", "huge"};
        double[] dd = {24, 40, 70, 100};
        B.comment("Orbit radius (blocks) of the strike camera (the key keeps its old name), per size; clamped to 16 x (view distance - 1).").push("DeathCamDistance");
        for (int s = 0; s < 4; s++) CAM_DIST[s] = d(szn[s], dd[s], 4, 1000, "Orbit radius, " + szn[s] + ".");
        B.pop();
        CAM_TURN = d("DeathCamTurnDegrees", 120, 0, 3600, "Degrees the strike camera turns round the held spot over the whole run of the move (0 = a locked camera).");
        CAM_MAX = d("DeathCamMaxSeconds", 60, 1, 3600, "The camera is given back to the player after this long if they have not respawned.");
        CAM_LOOK = d("LookHeight", 0.25, 0, 2, "The camera looks at the death spot raised by this x the orbit radius.");
        CAM_HFACTOR = d("DeathCamHeightFactor", 0.45, 0, 4, "Camera height above the sand = this x the orbit radius (at least DeathCamMinHeight).");
        CAM_HMIN = d("DeathCamMinHeight", 8, 0, 1000, "Camera height above the sand is at least this.");
        CAM_CLEAR = d("DeathCamSandClearance", 3, 0, 1000, "Camera height above the sand surface is at least this.");
        B.pop();
        B.push("group");
        String[] mvn = {"crest", "crash", "emerge", "engulf"};
        B.comment("Radius (blocks) of the strike area per move and size; 0 = derived from the eruption rows (largest ring or disc x r0 + StrikeAreaMargin).").push("StrikeAreaRadius");
        for (int t = 0; t < 4; t++) { B.push(mvn[t]); for (int s = 0; s < 4; s++) AREA[t][s] = d(szn[s], 0, 0, 1000, "Strike area radius of the " + mvn[t] + ", " + szn[s] + " (0 = derived)."); B.pop(); }
        B.pop();
        AREA_MARGIN = d("StrikeAreaMargin", 0, -100, 1000, "Blocks added to the derived strike area radius.");
        GROUP_RELEASE = B.comment("At the choice of the strike, members of the group outside the chosen move's own circle are released at once.").define("GroupReleaseOutside", true);
        HAZ_PL_SAND = B.comment("Players that are not on Dune Sand cannot be hurt by the worm.").define("HazardPlayersImmuneOffSand", true);
        HAZ_MOB_SAND = B.comment("Mobs that are not on Dune Sand cannot be hurt by the worm.").define("HazardMobsImmuneOffSand", true);
        VICTIM_PARTS = B.comment("Which parts swallow the strike victim: any of body, head, throat (default: only the throat, the mouth closed round them).").define("VictimParts", "throat", o -> o instanceof String);
        OTHERS_PARTS = B.comment("Which parts swallow everybody else (group members, bystanders).").define("OthersParts", "body,head,throat", o -> o instanceof String);
        FOLLOW = d("AttackRumbleFollowSeconds", 0.25, 0.05, 5, "While the speed ramps down the attack rumble is centred again on the player this often; once they are held it stays on their block.");
        SWALLOW_MAX = d("SwallowMaxSeconds", 0, 0, 3600, "Safety: a swallowed player is killed this long after the strike started if the end never comes (0 = the run time of the move + 3 s).");
        TICKET_MAX = i("StrikeChunkTicketMax", 400, 0, 100000, "At most this many chunks (nearest first) are kept loaded and entity-ticking round the worm during a strike (0 = none).");
        B.pop();
        B.push("discs");
        THROAT_DISC_ON = B.comment("The throat disc swallows players (and kills mobs) that cross it. It is the only thing that acts on a player besides the backup.").define("ThroatDiscEnabled", true);
        MOUTH_DISC_ON = B.comment("Crossing the mouth disc starts the strike camera for a player.").define("MouthDiscEnabled", true);
        DISC_THROAT_R = d("ThroatDiscRadiusFraction", 0.95, 0.05, 2, "Radius of the throat disc (the plane z = 0 of the head frame, the funnel's rim) as a fraction of the ring radius R.");
        DISC_MOUTH_F = d("MouthDiscFraction", 0.5, 0.0, 1.5, "Position of the mouth disc along the head axis as a fraction of the head length HL (0 = the ring plane).");
        DISC_MOUTH_R = d("MouthDiscRadiusFraction", 0.8, 0.05, 2, "Radius of the mouth disc as a fraction of R.");
        DISC_THICK = d("DiscThickness", 0.5, 0.05, 5, "Thickness of both discs in blocks, centred on the plane.");
        B.pop();
        B.push("strikecam");
        SC_FLARE = d("StrikeCamFlareFactor", 0.7, 0, 5, "Base camera distance = R + this x HL (the open mandibles) + the clearance.");
        SC_CLEAR = d("StrikeCamClearanceFactor", 0.5, 0, 5, "The camera stays at least this x R outside the worm's outer surface.");
        SC_FOV = d("StrikeCamFov", 75, 30, 130, "The field of view the framing is tuned for, in degrees (the server cannot set the client's).");
        SC_FILL = d("StrikeCamFill", 0.85, 0.1, 3, "The worm's peak fills this fraction of the screen height (backs the camera off).");
        SC_MAXDIST = d("StrikeCamMaxDistance", 120, 4, 2000, "The camera is never further than this from the held spot (and never outside the loaded area).");
        SC_SWEEP = d("StrikeCamSweepDegrees", 120, 0, 360, "The orbit sweeps this many degrees, symmetric about side-on (0 = a locked camera).");
        SC_LOOK = d("StrikeCamLookHeight", 1.0, 0, 5, "The camera looks at the held spot raised by this many blocks.");
        SC_PUSH = d("StrikeCamPushPerTick", 1.5, 0.05, 50, "The clearance push changes by at most this many blocks per tick.");
        SC_SPEED = d("StrikeCamMaxSpeed", 2.0, 0.1, 50, "The camera moves at most this many blocks per tick.");
        SC_HMIN = d("StrikeCamMinHeight", 3, 0, 1000, "The camera is at least this high above the ground under it.");
        SC_HFACTOR = d("StrikeCamHeightFactor", 0.5, 0, 4, "Camera height above the ground = this x the worm's peak height (at least StrikeCamMinHeight).");
        SC_MAXSEC = d("StrikeCamMaxSeconds", 60, 1, 3600, "The camera is given back to the player after this long if they have not respawned.");
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
            for (int s = 0; s < 4; s++) {
                for (int t = 0; t < 4; t++) c.strikeWeightsBySize[s][t] = WEIGHT2[s][t].get();
                c.fallbackSize[s] = FB_SIZE[s].get();
                c.fallbackType[s] = FB_TYPE[s].get();
            }
            for (int s = 0; s < 4; s++) { c.crestAhead[s] = AHEAD[s].get(); c.crestBehind[s] = BEHIND[s].get(); c.crestTolerance[s] = TOL[s].get(); }
            for (int t = 0; t < 4; t++) for (int s = 0; s < 4; s++) c.devourSeconds[t][s] = DEVOUR[t][s].get();
            c.strikeYawMode = YAW_MODE.get();
            c.scanColumnsPerTick = SCAN_COLUMNS.get();
            c.attackRumbleStart = ATTACK_START.get();
            c.attackRumbleRadius = ATTACK_RADIUS.get();
            c.attackRumbleMaxSixteenths = ATTACK_MAX.get();
            c.hazardSkin = HAZ_SKIN.get();
            c.throatReach = THROAT_REACH.get();
            c.hazardParts = HAZ_PARTS.get();
            c.hazardSubsteps = HAZ_SUBSTEPS.get();
            c.hazardTimeOffsetSeconds = HAZ_OFFSET.get();
            c.hazardEntitiesPerTick = HAZ_ENTITIES.get();
            c.hazardKillsPlayers = HAZ_PLAYERS.get();
            c.hazardKillsMobs = HAZ_MOBS.get();
            c.hazardOnTestWorms = HAZ_TEST.get();
            c.throatBackupSeconds = THROAT_BACKUP.get();
            for (int s = 0; s < 4; s++) c.deathCamDistance[s] = CAM_DIST[s].get();
            c.deathCamTurnDegrees = CAM_TURN.get();
            c.deathCamMaxSeconds = CAM_MAX.get();
            c.lookHeight = CAM_LOOK.get();
            c.deathCamHeightFactor = CAM_HFACTOR.get();
            c.deathCamMinHeight = CAM_HMIN.get();
            c.deathCamSandClearance = CAM_CLEAR.get();
            for (int t = 0; t < 4; t++) for (int s = 0; s < 4; s++) c.strikeAreaRadius[t][s] = AREA[t][s].get();
            c.strikeAreaMargin = AREA_MARGIN.get();
            c.groupReleaseOutside = GROUP_RELEASE.get();
            c.hazardPlayersImmuneOffSand = HAZ_PL_SAND.get();
            c.hazardMobsImmuneOffSand = HAZ_MOB_SAND.get();
            c.victimParts = VICTIM_PARTS.get();
            c.othersParts = OTHERS_PARTS.get();
            c.throatDiscEnabled = THROAT_DISC_ON.get();
            c.mouthDiscEnabled = MOUTH_DISC_ON.get();
            c.throatDiscRadiusFraction = DISC_THROAT_R.get();
            c.mouthDiscFraction = DISC_MOUTH_F.get();
            c.mouthDiscRadiusFraction = DISC_MOUTH_R.get();
            c.discThickness = DISC_THICK.get();
            c.strikeCamFlareFactor = SC_FLARE.get();
            c.strikeCamClearanceFactor = SC_CLEAR.get();
            c.strikeCamFov = SC_FOV.get();
            c.strikeCamFill = SC_FILL.get();
            c.strikeCamMaxDistance = SC_MAXDIST.get();
            c.strikeCamSweepDegrees = SC_SWEEP.get();
            c.strikeCamLookHeight = SC_LOOK.get();
            c.strikeCamPushPerTick = SC_PUSH.get();
            c.strikeCamMaxSpeed = SC_SPEED.get();
            c.strikeCamMinHeight = SC_HMIN.get();
            c.strikeCamHeightFactor = SC_HFACTOR.get();
            c.strikeCamMaxSeconds = SC_MAXSEC.get();
            c.attackRumbleFollowSeconds = FOLLOW.get();
            c.swallowMaxSeconds = SWALLOW_MAX.get();
            c.strikeChunkTicketMax = TICKET_MAX.get();
            return true;
        } catch (RuntimeException e) {
            return false;   // not loaded yet: the defaults stay
        }
    }
}
