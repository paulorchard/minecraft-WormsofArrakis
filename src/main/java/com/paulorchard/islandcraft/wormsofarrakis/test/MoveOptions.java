package com.paulorchard.islandcraft.wormsofarrakis.test;

import com.paulorchard.islandcraft.wormsofarrakis.fx.CrashFx;
import com.paulorchard.islandcraft.wormsofarrakis.fx.FxOptions;
import com.paulorchard.islandcraft.wormsofarrakis.path.HeadSpec;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The keyed options of the four moves (crest, emerge, crash, engulf), parsed and validated in one place (prompt 07). Pure code, no
 * Minecraft classes. The table {@link #keys} is the only list of variables: the parser, the usage text of every refusal and the
 * {@code moves} command are all generated from it, so a new variable is added in one place.
 *
 * <p>Free text after {@code <width> <segments>}: {@code name=value} tokens in any order, the word {@code force}, the words
 * {@code open} / {@code closed}, and bare numbers that keep their old meaning by position (crest: height, speed; emerge: n, angle, speed,
 * hold; crash: height; engulf: exposed). Unknown keys, repeated keys and bad values are refused ({@link Bad}); numbers outside the
 * range of a "clamped" key are pulled into it with a note for the player. Limits that say force in their message live in {@link Moves}
 * and come back as {@link Moves.Rejected} when the move is built.
 */
public final class MoveOptions {
    private MoveOptions() {}

    /** A refusal: the message goes to the player in chat. */
    public static final class Bad extends IllegalArgumentException {
        public Bad(String message) { super(message); }
    }

    public enum Type { NUM, ONOFF, MOUTH, LIGHT }

    /**
     * One variable of a move. {@code clamp}: a number outside min..max is pulled into the range with a note; otherwise min and max
     * are only shown in the table (the limit is enforced by the move itself, which may be lifted by force).
     */
    public record Key(String name, Type type, double dflt, double min, double max, boolean clamp, String dfltText, String range, String meaning) {}

    public record Parsed(WormSpec spec, boolean force, boolean mouthOpen, boolean loop, List<String> notes, Map<String, Double> values) {}

    public static final List<String> MOVES = List.of("crest", "emerge", "crash", "engulf");

    private static final Map<String, List<Key>> KEYS = new LinkedHashMap<>();
    private static final Map<String, List<String>> BARE = new LinkedHashMap<>();

    private static String t(double v) { return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v); }

    private static Key num(String name, double dflt, double min, double max, boolean clamp, String range, String meaning) {
        return new Key(name, Type.NUM, dflt, min, max, clamp, Double.isNaN(dflt) ? "-" : t(dflt), range, meaning);
    }

    private static Key numText(String name, double dflt, String dfltText, String range, String meaning) {
        return new Key(name, Type.NUM, dflt, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false, dfltText, range, meaning);
    }

    private static Key speed() {
        return num("speed", 12, 0.5, Moves.MAX_SPEED, true, "0.5..64", "head travel speed along the path in blocks per second");
    }

    private static List<Key> tail(double pause, String kind) {
        double closeEndDefault = com.paulorchard.islandcraft.wormsofarrakis.path.MouthAnim.defaultCloseEnd(kind);
        return List.of(
                num("pause", pause, 0, 600, true, "0..600", "seconds of rest between loops (everything under the sand)"),
                new Key("loop", Type.ONOFF, 1, 0, 1, false, "on", "on|off", "off plays once and removes the worm at the end"),
                new Key("mouth", Type.MOUTH, 2, 0, 2, false, "anim", "anim|open|closed", "anim = open at the start of every loop, closes once, then holds; open = static open_deg; closed = static 0"),
                num("close_time", com.paulorchard.islandcraft.wormsofarrakis.path.MouthAnim.DEFAULT_CLOSE_TIME, 0.05, com.paulorchard.islandcraft.wormsofarrakis.path.MouthAnim.MAX_CLOSE_TIME, true, "0.05..30", "seconds the closing takes (shorter if the rise is shorter)"),
                num("close_end", closeEndDefault, -com.paulorchard.islandcraft.wormsofarrakis.path.MouthAnim.MAX_CLOSE_END, com.paulorchard.islandcraft.wormsofarrakis.path.MouthAnim.MAX_CLOSE_END, true, "-30..30",
                        kind.equals("crest") ? "seconds after the highest point of the head tip at which the closing ends" : "seconds after the start of the hold at which the closing ends"),
                num("open_deg", HeadSpec.OPEN_DEGREES, 0, HeadSpec.OPEN_DEGREES, true, "0..45", "the start (open) angle in degrees"),
                new Key("teeth", Type.ONOFF, 1, 0, 1, false, "on", "on|off", "the bristles on the mandibles and the throat"),
                new Key("fx", Type.ONOFF, 1, 0, 1, false, "on", "on|off", "the sounds and particles of the sheet (prompts 09 and 10; on a crest the entry and the exit both play)"),
                new Key("eruption", Type.ONOFF, 1, 0, 1, false, "on", "on|off", "the eruption rows of the breach: jets of sand, dust columns, flying sand blocks, the dust bank (prompt 10)"),
                new Key("light", Type.LIGHT, 0, 0, 2, false, "probe", "probe|vanilla|full", "the light of the worm: probe = the best light along the exposed body (prompt 10); vanilla = the light at the entity origin only; full = 15/15"),
                new Key("fxsound", Type.ONOFF, 1, 0, 1, false, "on", "on|off", "the sounds of the sheet"),
                new Key("fxparticles", Type.ONOFF, 1, 0, 1, false, "on", "on|off", "the particles of the sheet"),
                num("fxvol", 1, 0, CrashFx.MAX_FXVOL, true, "0..2", "multiplier on the volume of every sound cue (above 1 a sound is heard further, not louder)"),
                num("fxcount", 1, 0, CrashFx.MAX_FXCOUNT, true, "0..3", "multiplier on the count of every particle row (the counts also scale with the body width)"));
    }

    private static List<Key> join(List<Key> a, List<Key> b) {
        List<Key> r = new ArrayList<>(a);
        r.addAll(b);
        return r;
    }

    static {
        KEYS.put("crest", join(List.of(
                numText("height", Moves.DEFAULT_CREST_HEIGHT, t(Moves.DEFAULT_CREST_HEIGHT), "above 0 to " + t(Moves.MAX_CREST_HEIGHT) + ", force lifts the " + t(Moves.MAX_CREST_HEIGHT), "body widths of the highest plate above the sand (1.3 is the good reference)"),
                numText("radius", 6, "6", "limit " + t(com.paulorchard.islandcraft.wormsofarrakis.geometry.BendLimit.MIN_BEND_RADIUS_WIDTHS) + " and up, force lifts the limit", "body widths, the way up"),
                numText("radius_down", 0, "=radius", "limit " + t(com.paulorchard.islandcraft.wormsofarrakis.geometry.BendLimit.MIN_BEND_RADIUS_WIDTHS) + " and up, force lifts the limit", "body widths, the way down"),
                speed()), tail(Moves.CREST_PAUSE, "crest")));
        KEYS.put("emerge", join(List.of(
                numText("n", Moves.DEFAULT_EMERGE_N, t(Moves.DEFAULT_EMERGE_N), "1..1000", "exposed body rings at the hold"),
                numText("height", Double.NaN, "-", "above 0", "head tip height in body widths instead of n: (n + HEAD_LENGTH) x sin(angle) = height x width, n rounded; not together with n"),
                numText("angle", Moves.DEFAULT_EMERGE_ANGLE, t(Moves.DEFAULT_EMERGE_ANGLE), "5..90", "degrees above the horizontal"),
                num("hold", Moves.DEFAULT_EMERGE_HOLD, 0, 120, true, "0..120", "seconds still at the top"),
                speed(),
                new Key("retreat_speed", Type.NUM, Double.NaN, 0.5, Moves.MAX_SPEED, true, "=speed", "0.5..64", "blocks per second of the way back"),
                tail(Moves.EMERGE_PAUSE, "emerge").get(0)), tail(Moves.EMERGE_PAUSE, "emerge").subList(1, tail(Moves.EMERGE_PAUSE, "emerge").size())));
        KEYS.put("crash", join(List.of(
                numText("height", Moves.DEFAULT_HEIGHT, t(Moves.DEFAULT_HEIGHT), t(Moves.MIN_FALL_HEIGHT) + ".." + t(Moves.MAX_FALL_HEIGHT) + ", force lifts", "body widths of the head tip at the hold"),
                numText("lean", Moves.DEFAULT_LEAN, t(Moves.DEFAULT_LEAN), "0..45", "degrees off vertical (the rise is at 90 - lean)"),
                num("hold", Moves.DEFAULT_HOLD, 0, 120, true, "0..120", "seconds still at the top"),
                num("speed", 12, 0.5, Moves.MAX_SPEED, true, "0.5..64", "rise speed in blocks per second (the fall is gravity)"),
                num("gravity", 12, 0.5, 80, true, "0.5..80", "blocks per second squared of the fall"),
                numText("min_radius", 2, "2", "limit " + t(com.paulorchard.islandcraft.wormsofarrakis.geometry.BendLimit.MIN_BEND_RADIUS_WIDTHS) + " and up, force lifts the limit", "smallest bend radius of the fall pose, body widths")),
                tail(Moves.CRASH_PAUSE, "crash")));
        KEYS.put("engulf", join(List.of(
                numText("exposed", Moves.DEFAULT_EXPOSED, t(Moves.DEFAULT_EXPOSED), "1..3, force up to 6", "body rings above the sand at the hold (the head ring not counted)"),
                numText("lean", Moves.DEFAULT_ENGULF_LEAN, t(Moves.DEFAULT_ENGULF_LEAN), "0..45", "degrees off vertical (the rise is at 90 - lean)"),
                num("hold", Moves.ENGULF_HOLD, 0, 120, true, "0..120", "seconds still at the top"),
                num("speed", 12, 0.5, Moves.MAX_SPEED, true, "0.5..64", "rise speed in blocks per second (the drop is gravity)"),
                num("gravity", 12, 0.5, 80, true, "0.5..80", "blocks per second squared of the drop")),
                tail(Moves.ENGULF_PAUSE, "engulf")));
        BARE.put("crest", List.of("height", "speed"));
        BARE.put("emerge", List.of("n", "angle", "speed", "hold"));
        BARE.put("crash", List.of("height"));
        BARE.put("engulf", List.of("exposed"));
    }

    public static List<Key> keys(String move) {
        List<Key> k = KEYS.get(move);
        if (k == null) throw new Bad("unknown move " + move);
        return k;
    }

    /** {@code crash <width> <segments> [height=2.5] [lean=7] ... [force]}, generated from the table. */
    public static String usage(String move) {
        StringBuilder b = new StringBuilder(move).append(" <width> <segments>");
        for (Key k : keys(move)) b.append(" [").append(k.name()).append('=').append(k.dfltText()).append(']');
        return b.append(" [force]").toString();
    }

    /** The line the {@code moves} command prints for a move: every key with its default and range, generated from the table. */
    public static String line(String move) {
        StringBuilder b = new StringBuilder(move).append(": ");
        for (Key k : keys(move)) b.append(k.name()).append('=').append(k.dfltText()).append(" (").append(k.range()).append(") ");
        b.append("force (lifts the limits that say force); bare numbers in order: ");
        b.append(String.join(", ", BARE.get(move)));
        return b.toString();
    }

    public static List<String> lines() {
        List<String> l = new ArrayList<>();
        for (String m : MOVES) l.add(line(m));
        return l;
    }

    private static Key find(List<Key> keys, String name) {
        for (Key k : keys) if (k.name().equals(name)) return k;
        return null;
    }

    /**
     * Parses and validates the free text of a move command and builds its spec.
     * @throws Bad for an unknown key, a repeated key, a bad value, or a combination that makes no sense (n and height)
     */
    public static Parsed parse(String move, double width, int segments, String opts, boolean forceFlag) {
        List<Key> keys = keys(move);
        List<String> bare = BARE.get(move);
        Map<String, Double> given = new LinkedHashMap<>();
        Map<String, String> givenText = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();
        boolean force = forceFlag;
        int bareIndex = 0;
        for (String tok : opts == null ? new String[0] : opts.trim().split("\\s+")) {
            if (tok.isEmpty()) continue;
            if (tok.equals("force")) { force = true; continue; }
            String name, val;
            int eq = tok.indexOf('=');
            if (tok.equals("open") || tok.equals("closed")) {
                name = "mouth";
                val = tok;
            } else if (eq < 0) {
                if (bareIndex >= bare.size()) throw new Bad("unknown word '" + tok + "' (" + usage(move) + ")");
                name = bare.get(bareIndex++);
                val = tok;
            } else {
                name = tok.substring(0, eq);
                val = tok.substring(eq + 1);
                if (move.equals("crest") && name.equals("crest_fraction")) name = "height";
            }
            Key k = find(keys, name);
            if (k == null) throw new Bad("unknown key '" + name + "' (" + usage(move) + ")");
            if (given.containsKey(name) || givenText.containsKey(name)) throw new Bad("'" + name + "' is given twice (" + usage(move) + ")");
            switch (k.type()) {
                case NUM -> {
                    double v;
                    try {
                        v = Double.parseDouble(val);
                    } catch (NumberFormatException e) {
                        throw new Bad("not a number: " + tok + " (" + usage(move) + ")");
                    }
                    if (Double.isNaN(v) || Double.isInfinite(v)) throw new Bad("not a number: " + tok);
                    if (k.clamp() && (v < k.min() || v > k.max())) {
                        double c = Math.max(k.min(), Math.min(k.max(), v));
                        notes.add(String.format(Locale.ROOT, "%s=%s is outside %s; clamped to %s", name, t(v), k.range(), t(c)));
                        v = c;
                    }
                    given.put(name, v);
                }
                case ONOFF -> {
                    if (!val.equals("on") && !val.equals("off")) throw new Bad(name + " must be on or off, not " + val);
                    givenText.put(name, val);
                }
                case LIGHT -> {
                    if (!val.equals("probe") && !val.equals("vanilla") && !val.equals("full")) throw new Bad("light must be probe, vanilla or full, not " + val);
                    givenText.put(name, val);
                }
                case MOUTH -> {
                    if (!val.equals("open") && !val.equals("closed") && !val.equals("anim")) throw new Bad("mouth must be anim, open or closed, not " + val);
                    givenText.put(name, val);
                }
            }
        }
        boolean loop = !"off".equals(givenText.get("loop"));
        String mouthWord = givenText.getOrDefault("mouth", "anim");
        int mouthMode = mouthWord.equals("open") ? MoveFactory.MOUTH_OPEN : mouthWord.equals("closed") ? MoveFactory.MOUTH_CLOSED : MoveFactory.MOUTH_ANIM;
        boolean mouthOpen = mouthMode == MoveFactory.MOUTH_OPEN;
        boolean teeth = !"off".equals(givenText.get("teeth"));
        double closeEndArg = given.containsKey("close_end") ? given.get("close_end") : com.paulorchard.islandcraft.wormsofarrakis.path.MouthAnim.CLOSE_END_DEFAULT_MARK;
        Map<String, Double> v = new LinkedHashMap<>();
        for (Key k : keys) if (k.type() == Type.NUM) v.put(k.name(), given.containsKey(k.name()) ? given.get(k.name()) : k.dflt());
        Key pk = find(keys, "pause");
        double pause = given.containsKey("pause") && given.get("pause") != pk.dflt() ? given.get("pause") : -1;   // -1 = the move's own default
        WormSpec spec;
        switch (move) {
            case "crest" -> {
                double radius = v.get("radius");
                if (radius <= 0) throw new Bad("radius must be above 0");
                double rd = given.containsKey("radius_down") ? given.get("radius_down") : 0;
                if (given.containsKey("radius_down") && rd <= 0) throw new Bad("radius_down must be above 0");
                spec = MoveFactory.specFull("crest", width, segments, v.get("height"), radius, v.get("speed"), 0, rd, 0, mouthOpen, true, force, 1.0, 1.0, 1, pause, loop, 0);
            }
            case "emerge" -> {
                double n = v.get("n"), angle = v.get("angle");
                if (given.containsKey("height")) {
                    if (given.containsKey("n")) throw new Bad("give either n or height, not both (n counts exposed rings, height is the head tip height in body widths)");
                    double h = given.get("height");
                    if (h <= 0) throw new Bad("height must be above 0");
                    if (angle < 5 || angle > 90) throw new Bad("angle must be between 5 and 90 degrees");
                    double nReal = Moves.emergeRingsForHeight(width, h, angle);
                    if (nReal < 0.5) throw new Bad(String.format(Locale.ROOT, "height %s is too low: even one ring needs a head tip height of %.2f body widths at this angle and width", t(h), Moves.emergeHeightForRings(width, 1, angle)));
                    n = Math.max(1, Math.round(nReal));
                    notes.add(String.format(Locale.ROOT, "height=%s body widths -> n=%d exposed rings (formula %.2f); the head tip at the hold is %.2f body widths", t(h), (long) n, nReal, Moves.emergeHeightForRings(width, (int) n, angle)));
                    v.put("n", n);
                }
                if (n != Math.rint(n)) throw new Bad("n must be a whole number of rings");
                if (n < 1 || n > 1000) throw new Bad("n must be between 1 and 1000");
                double retreat = given.containsKey("retreat_speed") ? given.get("retreat_speed") : 0;
                spec = MoveFactory.specFull("emerge", width, segments, n, angle, v.get("speed"), v.get("hold"), 0, 0, mouthOpen, true, force, 1.0, 1.0, 1, pause, loop, retreat);
            }
            case "crash" -> spec = MoveFactory.specFull("fall", width, segments, v.get("height"), v.get("lean"), v.get("speed"), v.get("hold"), v.get("gravity"), 0,
                    mouthOpen, true, force, 1.0, v.get("min_radius"), 1, pause, loop, 0);
            case "engulf" -> {
                double ex = v.get("exposed");
                if (ex != Math.rint(ex)) throw new Bad("exposed must be a whole number of rings");
                spec = MoveFactory.specFull("engulf", width, segments, ex, v.get("lean"), v.get("speed"), v.get("hold"), v.get("gravity"), 0,
                        mouthOpen, true, force, 1.0, 1.0, 1, pause, loop, 0);
            }
            default -> throw new Bad("unknown move " + move);
        }
        spec = MoveFactory.withMouth(spec, mouthMode, v.get("close_time"), closeEndArg, v.get("open_deg"), teeth);
        boolean fxOn = givenText.containsKey("fx") ? givenText.get("fx").equals("on") : FxOptions.defaultOn(spec.mode());
        FxOptions fx = new FxOptions(fxOn, !"off".equals(givenText.get("fxsound")), !"off".equals(givenText.get("fxparticles")), v.get("fxvol"), v.get("fxcount"), !"off".equals(givenText.get("eruption")));
        spec = MoveFactory.withFx(spec, fx);
        spec = MoveFactory.withLight(spec, givenText.getOrDefault("light", "probe"));
        return new Parsed(spec, force, mouthOpen, loop, notes, v);
    }
}
