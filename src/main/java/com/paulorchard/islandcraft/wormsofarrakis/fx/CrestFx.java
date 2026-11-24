package com.paulorchard.islandcraft.wormsofarrakis.fx;

import com.paulorchard.islandcraft.wormsofarrakis.fx.CrashFx.Anchor;
import com.paulorchard.islandcraft.wormsofarrakis.fx.CrashFx.Cue;
import com.paulorchard.islandcraft.wormsofarrakis.fx.CrashFx.Group;

import java.util.ArrayList;
import java.util.List;

/**
 * Prompt 10 part 2: the sound sheet of the crest, the owner's tuned values from the crest previs, as data (vanilla event ids only; nothing is typed anywhere else).
 * The particle rows of the crest are the rows of {@link CrashFx} and {@link EruptionFx} placed by {@link FxPlayer}; only the sounds differ from the crash sheet.
 *
 * <p>Pitches of 0.5 are "as low as Minecraft goes" (the previs went lower; the game clamps to 0.5..2). A row with {@code untilEnd} has no typed repeat count: it plays every
 * {@code every} seconds from its start until the end of the move, rep = floor((end - start) / every) + 1. The whole-event rumble is two such rows; the owner will
 * pick a better earthquake sound later: change {@link #RUMBLE_EVENT} (one line) and nothing else.
 */
public final class CrestFx {
    private CrestFx() {}

    public static final String WARDEN_STEP = "entity.warden.step", WARDEN_DIG = "entity.warden.dig";
    /** The event of the whole-event grumble; the owner may give another (a one-line change). */
    public static final String RUMBLE_EVENT = WARDEN_STEP;

    /** One crest sound row. {@code on = false} keeps the row in the sheet but plays nothing ("off by default"). */
    public record Row(Cue cue, boolean untilEnd, boolean on) {}

    private static Row row(Group g, String name, String event, Anchor from, double off, double vol, double pitch, int rep, double every, double jit) {
        return new Row(new Cue(g, name, event, from, off, vol, pitch, rep, every, jit), false, true);
    }

    private static Row untilEnd(Group g, String name, String event, Anchor from, double off, double vol, double pitch, double every, double jit) {
        return new Row(new Cue(g, name, event, from, off, vol, pitch, 1, every, jit), true, true);
    }

    public static final List<Row> ROWS = List.of(
            row(Group.TREMOR, "Tremor: grumble", WARDEN_STEP, Anchor.APPROACH, -0.15, 5.5, 0.5, 7, 0.33, 0.12),
            row(Group.TREMOR, "Tremor: deep heartbeat", CrashFx.WARDEN_HEARTBEAT, Anchor.APPROACH, 0.3, 2.4, 0.55, 4, 0.7, 0.05),
            row(Group.BREACH, "Breach: boom", CrashFx.GENERIC_EXPLODE, Anchor.BREACH, 0, 4, 0.55, 1, 0, 0.05),
            row(Group.BREACH, "Breach: ground smash", CrashFx.MACE_SMASH_GROUND_HEAVY, Anchor.BREACH, 0, 3.5, 0.6, 1, 0, 0.05),
            row(Group.BREACH, "Breach: sand spray", CrashFx.SAND_BREAK, Anchor.BREACH, 0.05, 4.5, 0.55, 7, 0.08, 0.15),
            row(Group.BREACH, "Breach: earth emerges", CrashFx.WARDEN_EMERGE, Anchor.BREACH, 0.1, 3, 0.7, 1, 0, 0.05),
            row(Group.STREAM, "Rise: streaming", WARDEN_STEP, Anchor.BREACH, 0.4, 3.1, 0.5, 20, 0.3, 0.2),
            new Row(new Cue(Group.STREAM, "Rise: bone creak", CrashFx.BONE_BLOCK_HIT, Anchor.BREACH, 1, 3, 0.5, 6, 0.9, 0.2), false, false),
            row(Group.ROAR, "Roar", CrashFx.RAVAGER_ROAR, Anchor.BREACH, 1.2, 4, 0.5, 4, 0.32, 0.03),
            row(Group.ROAR, "Roar: low layer", CrashFx.ENDER_DRAGON_GROWL, Anchor.BREACH, 1.3, 3.5, 0.5, 4, 2.87, 0.03),
            untilEnd(Group.STREAM, "Rumble (whole event): grumble", RUMBLE_EVENT, Anchor.BREACH, 0.9, 4, 0.5, 0.33, 0.12),
            untilEnd(Group.STREAM, "Rumble (whole event): heartbeat", CrashFx.WARDEN_HEARTBEAT, Anchor.BREACH, 1.4, 2.4, 0.55, 0.7, 0.05),
            row(Group.MOUTH, "Mouth clamp: bone", CrashFx.BONE_BLOCK_BREAK, Anchor.RISE_END, -0.15, 1.4, 0.55, 3, 0.06, 0.1),
            row(Group.MOUTH, "Mouth clamp: thud", CrashFx.IRON_GOLEM_DAMAGE, Anchor.RISE_END, -0.1, 2, 0.5, 1, 0, 0.05),
            row(Group.FALL, "Fall: rushing sand", CrashFx.SAND_BREAK, Anchor.REENTRY, 0.1, 1.2, 0.55, 9, 0.25, 0.2),
            row(Group.FALL, "Fall: rumble down", WARDEN_DIG, Anchor.REENTRY, 0.3, 3, 0.6, 1, 0, 0.05),
            row(Group.FALL, "Re-entry: impact", CrashFx.GENERIC_EXPLODE, Anchor.REENTRY, 0, 2, 0.7, 1, 0, 0.05),
            row(Group.FALL, "Re-entry: sand collapse", CrashFx.SAND_BREAK, Anchor.REENTRY, 0.05, 3.2, 0.5, 8, 0.1, 0.15),
            row(Group.FALL, "Re-entry: gravel", CrashFx.GRAVEL_BREAK, Anchor.REENTRY, 0.15, 2.3, 0.55, 4, 0.15, 0.15));

    /** The repeat count of an "until end" row that starts at {@code start}: floor((end - start) / every) + 1 (0 when it would start after the end). */
    public static int untilEndReps(double start, double end, double every) {
        if (Double.isNaN(start) || Double.isNaN(end) || start > end + 1e-9) return 0;
        return (int) Math.floor((end - start) / every + 1e-9) + 1;
    }

    /** The cues that play on a crest with these anchors: the rows that are on, the "until end" rows with their computed repeat counts. */
    public static List<Cue> cues(FxAnchors.Anchors a) {
        List<Cue> l = new ArrayList<>();
        for (Row r : ROWS) {
            if (!r.on()) continue;
            Cue c = r.cue();
            if (r.untilEnd()) {
                int reps = untilEndReps(a.at(c.anchor()) + c.off(), a.end(), c.every());
                if (reps <= 0) continue;
                c = new Cue(c.group(), c.name(), c.event(), c.anchor(), c.off(), c.vol(), c.pitch(), reps, c.every(), c.jit());
            }
            l.add(c);
        }
        return l;
    }

    /** The distinct vanilla sound event ids of the crest sheet (rows that are off included). */
    public static List<String> events() { return ROWS.stream().map(r -> r.cue().event()).distinct().toList(); }
}
