package com.paulorchard.islandcraft.wormsofarrakis.fx;

import java.util.List;

/**
 * Prompt 09: the effects sheet of the crash (sounds and particles) as data, in one place. This is the owner's sheet from the web previs, copied
 * row for row; nothing here is typed anywhere else. No Minecraft classes (the sound event ids and particle types are names that the client sink
 * resolves), so the sheet, the anchors and the schedule are unit tested without a game.
 *
 * <p>Times are seconds on the move's loop clock (the clock of the phases). A cue plays a vanilla sound event; an emitter makes particles.
 */
public final class CrashFx {
    private CrashFx() {}

    /** The instants of a move that rows are fixed to (see {@link FxAnchors}). */
    public enum Anchor {
        APPROACH("approach"), BREACH("breach"), CLOSE_START("closeStart"), RISE_END("riseEnd"), FALL_START("fallStart"), REENTRY("reentry"), END("end"),
        /** Prompt 10: the instant the head tip is highest (the crest uses it). */
        APEX("apex");
        public final String label;
        Anchor(String label) { this.label = label; }
    }

    /** The sound groups (for {@code fx solo}). */
    public enum Group {
        TREMOR, BREACH, STREAM, ROAR, MOUTH, SLIDE, FALL;
        public String label() { return name().toLowerCase(java.util.Locale.ROOT); }
        public static Group byLabel(String s) {
            for (Group g : values()) if (g.label().equals(s)) return g;
            return null;
        }
    }

    // ---- the vanilla sound-event ids used by the sheet (SoundEvents constants of the same name, see Prompt09Test) ----
    public static final String GRAVEL_STEP = "block.gravel.step", WARDEN_HEARTBEAT = "entity.warden.heartbeat", GENERIC_EXPLODE = "entity.generic.explode",
            MACE_SMASH_GROUND_HEAVY = "item.mace.smash_ground_heavy", SAND_BREAK = "block.sand.break", WARDEN_EMERGE = "entity.warden.emerge",
            SAND_HIT = "block.sand.hit", BONE_BLOCK_HIT = "block.bone_block.hit", RAVAGER_ROAR = "entity.ravager.roar", ENDER_DRAGON_GROWL = "entity.ender_dragon.growl",
            BONE_BLOCK_BREAK = "block.bone_block.break", WARDEN_STEP = "entity.warden.step", WARDEN_DIG = "entity.warden.dig", IRON_GOLEM_DAMAGE = "entity.iron_golem.damage", SAND_STEP = "block.sand.step", GRAVEL_BREAK = "block.gravel.break";

    /**
     * One sound row: from {@code anchor + off} the event plays {@code rep} times, {@code every} seconds apart, at volume {@code vol} and pitch
     * {@code pitch}, the pitch varied by +-{@code jit} (a fraction) at random.
     */
    public record Cue(Group group, String name, String event, Anchor anchor, double off, double vol, double pitch, int rep, double every, double jit) {}

    public static final List<Cue> CUES = List.of(
            new Cue(Group.TREMOR, "Tremor: warden step", WARDEN_STEP, Anchor.APPROACH, -0.15, 4.6, 0.25, 7, 0.33, 0.12),
            new Cue(Group.TREMOR, "Tremor: deep heartbeat", WARDEN_HEARTBEAT, Anchor.APPROACH, 0.3, 2.4, 0.55, 1, 0, 0.05),
            new Cue(Group.BREACH, "Breach: boom", GENERIC_EXPLODE, Anchor.BREACH, 0, 4, 0.55, 1, 0, 0.05),
            new Cue(Group.BREACH, "Breach: ground smash", MACE_SMASH_GROUND_HEAVY, Anchor.BREACH, 0, 3.5, 0.6, 1, 0, 0.05),
            new Cue(Group.BREACH, "Breach: sand spray", SAND_BREAK, Anchor.BREACH, 0.05, 4.5, 0.55, 7, 0.08, 0.15),
            new Cue(Group.BREACH, "Breach: earth emerges", WARDEN_EMERGE, Anchor.BREACH, 0.1, 3, 0.7, 1, 0, 0.05),
            new Cue(Group.STREAM, "Rise: sand streaming", SAND_HIT, Anchor.BREACH, 0.4, 2.8, 0.5, 18, 0.3, 0.2),
            new Cue(Group.STREAM, "Rise: bone creak", BONE_BLOCK_HIT, Anchor.BREACH, 1, 3, 0.5, 6, 0.9, 0.2),
            new Cue(Group.ROAR, "Roar", RAVAGER_ROAR, Anchor.BREACH, 1.2, 4, 0.5, 4, 0.32, 0.03),
            new Cue(Group.ROAR, "Roar: low layer", ENDER_DRAGON_GROWL, Anchor.BREACH, 1.3, 3.5, 0.5, 1, 0, 0.03),
            new Cue(Group.MOUTH, "Mouth clamp: bone", BONE_BLOCK_BREAK, Anchor.RISE_END, -0.15, 1.4, 0.55, 3, 0.06, 0.1),
            new Cue(Group.MOUTH, "Mouth clamp: thud", IRON_GOLEM_DAMAGE, Anchor.RISE_END, -0.1, 2, 0.5, 1, 0, 0.05),
            new Cue(Group.SLIDE, "Hold: sand sliding", SAND_STEP, Anchor.RISE_END, 0.1, 0.9, 0.6, 6, 0.25, 0.2),
            new Cue(Group.FALL, "Fall: rushing sand", SAND_BREAK, Anchor.FALL_START, 0.1, 1.2, 0.55, 9, 0.25, 0.2),
            new Cue(Group.FALL, "Fall: rumble down", WARDEN_DIG, Anchor.FALL_START, 0.3, 3, 0.6, 1, 0, 0.04),
            new Cue(Group.FALL, "Re-entry: sand collapse", SAND_BREAK, Anchor.REENTRY, 0.05, 3.2, 0.5, 8, 0.1, 0.15),
            new Cue(Group.FALL, "Re-entry: gravel", GRAVEL_BREAK, Anchor.REENTRY, 0.15, 2.3, 0.55, 4, 0.15, 0.15));

    /** Particle types of the sheet. {@code vanillaWidth}: the mean full width in blocks of one quad of the type as the game makes it (read from the 26.1.2 sources, see the report). */
    public enum PType {
        BLOCK("block", 0.15, false), DUST_PLUME("dust_plume", 0.225, false), CLOUD("cloud", 0.5625, false), EXPLOSION("explosion", 4.0, true),
        /** Prompt 10: a sand block flying at 1/4 size: made as a sand block particle (vanilla width 0.15) and scaled to the size of the row. */
        CHUNK("chunk", 0.15, false),
        POOF("poof", 0.5, true), LARGE_SMOKE("large_smoke", 0.5, false);
        public final String id;
        public final double vanillaWidth;
        public final boolean overrideLimiter;
        PType(String id, double vanillaWidth, boolean overrideLimiter) { this.id = id; this.vanillaWidth = vanillaWidth; this.overrideLimiter = overrideLimiter; }
    }

    /** Where a particle is made (see {@link FxPlayer}). */
    public enum Place { CROSSING, OVERHEAD, BODY, MOUTH }

    public static final int NO_TINT = -1;

    /**
     * One particle row. From {@code from + off} it makes either one burst of {@code burst} particles or, when {@code until} is set, {@code rate}
     * particles per second until that anchor. {@code ring} (units of r0), {@code up}, {@code out}, {@code spread} (blocks per tick), {@code grav},
     * {@code size} (full width in blocks), {@code lifeMin..lifeMax} (ticks), {@code tint} (0xRRGGBB or {@link #NO_TINT}).
     */
    public record Emitter(String name, PType type, Place place, Anchor from, double off, Anchor until, double rate, int burst, double ring, double up,
                          double out, double spread, double grav, double size, int lifeMin, int lifeMax, int tint) {
        public boolean isBurst() { return until == null; }
    }

    public static final int SAND_DUST = 0xD8C38E, LOW_CLOUD = 0xE6D6A8;

    public static final List<Emitter> EMITTERS = List.of(
            new Emitter("Tremor grains on the sand above the head", PType.BLOCK, Place.OVERHEAD, Anchor.APPROACH, 0, Anchor.BREACH, 135, 0, 1.85, 0.29, 0.08, 0.14, 1, 0.10, 9, 58, NO_TINT),
            new Emitter("Breach: sand fountain", PType.BLOCK, Place.CROSSING, Anchor.BREACH, 0, null, 0, 500, 0.95, 0.85, 0.4, 0.12, 1, 0.15, 20, 60, NO_TINT),
            new Emitter("Breach: dust ring (puffs)", PType.DUST_PLUME, Place.CROSSING, Anchor.BREACH, 0, null, 0, 70, 1.05, 0.06, 0.22, 0.05, -0.02, 1.1, 16, 30, NO_TINT),
            new Emitter("Breach: explosion puffs", PType.EXPLOSION, Place.CROSSING, Anchor.BREACH, 0, null, 0, 4, 0.6, 0, 0, 0, 0, 4.5, 16, 16, NO_TINT),
            new Emitter("Rise: sand pouring off the crossing", PType.BLOCK, Place.CROSSING, Anchor.BREACH, 0.2, Anchor.RISE_END, 130, 0, 1.0, 0.28, 0.22, 0.06, 1, 0.15, 14, 40, NO_TINT),
            new Emitter("Rise: dust puffs", PType.CLOUD, Place.CROSSING, Anchor.BREACH, 0.2, Anchor.RISE_END, 12, 0, 1.0, 0.05, 0.09, 0.03, -0.03, 1.0, 14, 28, SAND_DUST),
            new Emitter("Mouth clamp: breath", PType.CLOUD, Place.MOUTH, Anchor.RISE_END, -0.2, null, 0, 25, 0.35, 0, 0.05, 0.05, -0.02, 0.9, 14, 30, NO_TINT),
            new Emitter("Hold: sand sliding off the body", PType.BLOCK, Place.BODY, Anchor.RISE_END, 0, Anchor.FALL_START, 70, 0, 1.0, 0, 0.02, 0.01, 1, 0.15, 20, 50, NO_TINT),
            new Emitter("Fall: sand pouring into the hole", PType.BLOCK, Place.CROSSING, Anchor.FALL_START, 0, Anchor.REENTRY, 110, 0, 1.0, 0.12, -0.18, 0.05, 1, 0.15, 14, 40, NO_TINT),
            new Emitter("Fall: re-entry burst", PType.BLOCK, Place.CROSSING, Anchor.REENTRY, 0, null, 0, 300, 0.9, 0.6, 0.3, 0.1, 1, 0.15, 20, 55, NO_TINT),
            new Emitter("Fall: re-entry dust", PType.DUST_PLUME, Place.CROSSING, Anchor.REENTRY, 0, null, 0, 45, 1.0, 0.05, 0.18, 0.05, -0.02, 1.2, 16, 30, NO_TINT),
            new Emitter("Base mask: dust skirt", PType.DUST_PLUME, Place.CROSSING, Anchor.BREACH, 0, Anchor.END, 170, 0, 1.15, 0.10, 0.06, 0.04, -0.02, 3.0, 20, 45, SAND_DUST),
            new Emitter("Base mask: low cloud", PType.CLOUD, Place.CROSSING, Anchor.BREACH, 0, Anchor.END, 110, 0, 1.3, 0.06, 0.10, 0.05, -0.02, 3.6, 25, 55, LOW_CLOUD),
            new Emitter("Base mask: sand skirt", PType.BLOCK, Place.CROSSING, Anchor.BREACH, 0, Anchor.END, 700, 0, 1.0, 0.26, 0.16, 0.08, 1, 0.15, 12, 38, NO_TINT));

    /** The three rows the owner wants as a mask at the base of the worm. */
    public static boolean isBaseMask(Emitter e) { return e.name().startsWith("Base mask"); }

    // ---- the global numbers of the scaling and the budget ----

    /** Counts are for W16 (r0 = 7.96) and scale with r0 / this. */
    public static final double COUNT_REFERENCE_R0 = 8.0;
    /** The default count scale is r0 / 8 but never more than this (reached at r0 = 16, a body width of about 32). */
    public static final double MAX_AUTO_SCALE = 2.0;
    /** Vanilla keeps 16384 quad particles per client (ParticleGroup.MAX_PARTICLES; one group for all quads, shared with the rest of the world). */
    public static final int VANILLA_PARTICLE_CAP = 16384;
    /** Particles alive that the sheet keeps itself under. */
    public static final int BUDGET = 14000;   // prompt 10: raised from 12000 so the approved eruption (about 13800 alive at W16) passes unthinned; the vanilla cap is 16384

    public static final double MAX_FXVOL = 2, MAX_FXCOUNT = 3;

    /** Number of sound plays of the whole sheet (the sum of the Repeat column). */
    public static int totalPlays() {
        int n = 0;
        for (Cue c : CUES) n += c.rep();
        return n;
    }
}
