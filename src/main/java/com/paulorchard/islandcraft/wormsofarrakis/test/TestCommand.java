package com.paulorchard.islandcraft.wormsofarrakis.test;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.BendLimit;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ScaleMath;
import com.paulorchard.islandcraft.wormsofarrakis.path.HeadSpec;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;
import com.paulorchard.islandcraft.wormsofarrakis.path.WorldBase;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.WormMath;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Locale;

/**
 * /arrakis_worm_test ring|segment|chain|wave|mouth|clear ... (test only, op level).
 * Optional arguments may be left out; the defaults are in {@link #run}.
 */
final class TestCommand {
    private record Arg(String name, com.mojang.brigadier.arguments.ArgumentType<?> type) {}

    private static final boolean MOUTH_DISABLED = false;

    private TestCommand() {}

    static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("arrakis_worm_test")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

        root.then(branch("ring", 1, List.of(w("width"), new Arg("faces", IntegerArgumentType.integer(3, 600)))));
        root.then(branch("segment", 2, List.of(w("width"), new Arg("length", DoubleArgumentType.doubleArg(0.1, 64)),
                new Arg("taper", DoubleArgumentType.doubleArg(0.05, 4)))));
        root.then(branch("scale", 1, List.of(w("width"), new Arg("direction", StringArgumentType.word()), new Arg("mesh", StringArgumentType.word()))));
        root.then(branch("scales", 1, List.of(w("width"), new Arg("count", IntegerArgumentType.integer(1, 200)),
                new Arg("direction", StringArgumentType.word()), new Arg("mesh", StringArgumentType.word()))));
        root.then(branch("chain", 3, List.of(w("width"), new Arg("segments", IntegerArgumentType.integer(1, 400)),
                new Arg("bend", DoubleArgumentType.doubleArg(-90, 90)), new Arg("pitch", DoubleArgumentType.doubleArg(-90, 90)),
                new Arg("seglen", DoubleArgumentType.doubleArg(0.25, 64)), new Arg("closer", StringArgumentType.word()),
                new Arg("taper", DoubleArgumentType.doubleArg(0.5, 1.5)), new Arg("per", IntegerArgumentType.integer(1, 16)),
                new Arg("lip", DoubleArgumentType.doubleArg(0.0, 1.0)), new Arg("liner", StringArgumentType.word()),
                new Arg("force", StringArgumentType.word()))));
        // curve <width> <segments> <radius_widths> [plane horizontal|vertical|diagonal] [per]
        root.then(branch("curve", 3, List.of(w("width"), new Arg("segments", IntegerArgumentType.integer(1, 400)),
                new Arg("radius_widths", DoubleArgumentType.doubleArg(0.25, 64)), new Arg("plane", StringArgumentType.word()),
                new Arg("per", IntegerArgumentType.integer(1, 16)), new Arg("force", StringArgumentType.word()))));
        root.then(Commands.literal("stats").then(Commands.argument("state", StringArgumentType.word()).executes(ctx -> {
            String st = StringArgumentType.getString(ctx, "state");
            TestFlags.stats = st.equals("on");
            WormsOfArrakis.LOGGER.info("[WormsOfArrakis] stats {}", TestFlags.stats ? "on (log line every 5 s from the client renderer)" : "off");
            ctx.getSource().sendSuccess(() -> Component.literal("stats " + (TestFlags.stats ? "on" : "off")), true);
            return 1;
        })));
        root.then(branch("wave", 2, List.of(w("width"), new Arg("segments", IntegerArgumentType.integer(1, 400)),
                new Arg("amplitude", DoubleArgumentType.doubleArg(0, 45)), new Arg("seglen", DoubleArgumentType.doubleArg(0.25, 64)),
                new Arg("closer", StringArgumentType.word()), new Arg("force", StringArgumentType.word()))));
        // crest <width> <segments> [height=0.4] [radius=6] [radius_down=<radius>] [speed=12] [force]   (keyed, any order; a bare number is the height)
        root.then(Commands.literal("crest").then(Commands.argument("width", DoubleArgumentType.doubleArg(1, 128))
                .then(Commands.argument("segments", IntegerArgumentType.integer(2, 400)).executes(ctx -> run(ctx, "crest", List.of()))
                        .then(Commands.argument("opts", StringArgumentType.greedyString()).executes(ctx -> run(ctx, "crest", List.of()))))));
        root.then(branch("emerge", 3, List.of(w("width"), new Arg("segments", IntegerArgumentType.integer(2, 400)),
                new Arg("n", IntegerArgumentType.integer(1, 399)), new Arg("angle", DoubleArgumentType.doubleArg(5, 90)),
                new Arg("speed", DoubleArgumentType.doubleArg(0.5, 64)), new Arg("hold", DoubleArgumentType.doubleArg(0, 120)),
                new Arg("mouth", StringArgumentType.word()), new Arg("force", StringArgumentType.word()))));
        root.then(branch("crash", 3, List.of(w("width"), new Arg("segments", IntegerArgumentType.integer(2, 400)),
                new Arg("n", IntegerArgumentType.integer(1, 399)), new Arg("angle", DoubleArgumentType.doubleArg(5, 85)),
                new Arg("speed", DoubleArgumentType.doubleArg(0.5, 64)), new Arg("gravity", DoubleArgumentType.doubleArg(0.5, 80)),
                new Arg("top_speed", DoubleArgumentType.doubleArg(0.5, 64)), new Arg("mouth", StringArgumentType.word()),
                new Arg("ballistic", StringArgumentType.word()), new Arg("force", StringArgumentType.word()))));
        root.then(Commands.literal("stop").executes(ctx -> {
            int n = 0;
            for (net.minecraft.world.entity.Entity e : ctx.getSource().getLevel().getAllEntities()) {
                if (e instanceof TestWormEntity t && t.getSpecString().startsWith("move;")) { e.discard(); n++; }
            }
            int removed = n;
            WormsOfArrakis.LOGGER.info("[WormsOfArrakis] stop: {} moving worms removed", removed);
            ctx.getSource().sendSuccess(() -> Component.literal("Stopped " + removed + " moving worms"), true);
            return removed;
        }));
        root.then(Commands.literal("speed").then(Commands.argument("value", DoubleArgumentType.doubleArg(0.5, 64)).executes(ctx -> {
            double v = DoubleArgumentType.getDouble(ctx, "value");
            int n = 0;
            for (net.minecraft.world.entity.Entity e : ctx.getSource().getLevel().getAllEntities()) {
                if (!(e instanceof TestWormEntity t) || !t.getSpecString().startsWith("move;")) continue;
                WormSpec s = WormSpec.parse(t.getSpecString());
                double nominal = MoveFactory.get(s).nominalSpeed;
                double tauNow = MoveFactory.tau(s, e.tickCount);
                double[] p = s.p().clone();
                p[11] = v / nominal;
                p[12] = e.tickCount;
                p[13] = tauNow;
                t.setSpec(new WormSpec(s.kind(), s.mode(), p));
                n++;
            }
            int changed = n;
            WormsOfArrakis.LOGGER.info("[WormsOfArrakis] speed {}: {} moving worms changed", v, changed);
            ctx.getSource().sendSuccess(() -> Component.literal("Speed " + v + " blocks/s for " + changed + " moving worms"), true);
            return changed;
        })));
        root.then(Commands.literal("surface").then(Commands.argument("mode", StringArgumentType.word()).executes(ctx -> {
            String m = StringArgumentType.getString(ctx, "mode");
            TestFlags.worldSurface = m.equals("world");
            notice(ctx, "surface " + (TestFlags.worldSurface ? "world (client height map)" : "plane (the height the worm was spawned at)"));
            return 1;
        })));
        root.then(Commands.literal("base").then(Commands.argument("value", StringArgumentType.word()).executes(ctx -> {
            String v = StringArgumentType.getString(ctx, "value");
            ServerLevel lv = ctx.getSource().getLevel();
            double feet = ctx.getSource().getPosition().y;
            if (v.equals("sea") || v.equals("here")) {
                WorldBase.mode = v;
            } else {
                try {
                    WorldBase.fixedY = Double.parseDouble(v);
                    WorldBase.mode = "y";
                } catch (NumberFormatException e) {
                    ctx.getSource().sendFailure(Component.literal("base <y|here|sea>"));
                    return 0;
                }
            }
            notice(ctx, String.format(Locale.ROOT, "base level: y=%.2f (mode %s; the sea level of this level is %d, the constant BASE_Y is %d, your feet are at %.2f)",
                    WorldBase.resolve(lv.getSeaLevel(), feet), WorldBase.mode, lv.getSeaLevel(), WorldBase.BASE_Y, feet));
            return 1;
        })));
        root.then(Commands.literal("stagger").then(Commands.argument("value", StringArgumentType.word()).executes(ctx -> staggerCmd(ctx, -1L))
                .then(Commands.argument("seed", com.mojang.brigadier.arguments.LongArgumentType.longArg(0)).executes(ctx -> staggerCmd(ctx, com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "seed"))))));
        root.then(Commands.literal("jitter").then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 0.1)).executes(ctx -> {
            TestFlags.jitter = DoubleArgumentType.getDouble(ctx, "value");
            notice(ctx, "jitter " + TestFlags.jitter + " (brightness multiplier 1 +- jitter per face)");
            return 1;
        })));
        root.then(Commands.literal("tiles").then(Commands.argument("state", StringArgumentType.word()).executes(ctx -> {
            TestFlags.tiles = !StringArgumentType.getString(ctx, "state").equals("off");
            notice(ctx, "tiles " + (TestFlags.tiles ? "on (seeded tile orientation per face)" : "off"));
            return 1;
        })));
        root.then(Commands.literal("freeze").then(Commands.argument("t", StringArgumentType.word()).executes(TestCommand::freezeCmd)));
        root.then(branch("mouth", 2, List.of(w("width"), new Arg("action", StringArgumentType.word()))));
        root.then(Commands.literal("clear").executes(ctx -> {
            int n = 0;
            for (net.minecraft.world.entity.Entity e : ctx.getSource().getLevel().getAllEntities()) {
                if (!(e instanceof TestWormEntity)) continue;
                e.discard();
                n++;
            }
            int removed = n;
            int all = 0, stands = 0; for (net.minecraft.world.entity.Entity e : ctx.getSource().getLevel().getAllEntities()) { all++; if (e instanceof net.minecraft.world.entity.decoration.ArmorStand) stands++; }
            WormsOfArrakis.LOGGER.info("[WormsOfArrakis] clear: {} worms found, {} entities in level, {} armor stands", removed, all, stands);
            ctx.getSource().sendSuccess(() -> Component.literal("Removed " + removed + " test worms"), true);
            return removed;
        }));
        d.register(root);
    }

    private static int staggerCmd(CommandContext<CommandSourceStack> ctx, long seed) {
        String v = StringArgumentType.getString(ctx, "value");
        double f;
        if (v.equals("off")) f = 0;
        else {
            try {
                f = Double.parseDouble(v);
            } catch (NumberFormatException e) {
                ctx.getSource().sendFailure(Component.literal("stagger <fraction|off> [seed]"));
                return 0;
            }
        }
        TestFlags.stagger = Math.max(0, Math.min(4, f));
        if (seed >= 0) TestFlags.seed = seed;
        notice(ctx, "stagger " + (TestFlags.stagger == 0 ? "off" : TestFlags.stagger + " of a face") + ", seed " + (TestFlags.seed >= 0 ? TestFlags.seed : "= entity id"));
        return 1;
    }

    /** freeze <t|off>: holds every running move at normalised time t (0 = head tip rises above the base level, 1 = back below it) and logs the frame. */
    private static int freezeCmd(CommandContext<CommandSourceStack> ctx) {
        String v = StringArgumentType.getString(ctx, "t");
        double t = -1;
        if (!v.equals("off")) {
            try {
                t = Double.parseDouble(v);
            } catch (NumberFormatException e) {
                ctx.getSource().sendFailure(Component.literal("freeze <t 0..1|off>"));
                return 0;
            }
            if (t < 0 || t > 1) {
                ctx.getSource().sendFailure(Component.literal("t must be between 0 and 1"));
                return 0;
            }
        }
        int n = 0;
        for (net.minecraft.world.entity.Entity e : ctx.getSource().getLevel().getAllEntities()) {
            if (!(e instanceof TestWormEntity w) || !w.getSpecString().startsWith("move;")) continue;
            WormSpec s = WormSpec.parse(w.getSpecString());
            double[] p = java.util.Arrays.copyOf(s.p(), Math.max(18, s.p().length));
            if (t < 0 && s.p(17, -1) >= 0) {   // resume from where it is held, without a jump
                p[12] = e.tickCount;
                p[13] = MoveFactory.tau(s, e.tickCount);
            }
            p[17] = t;
            w.setSpec(new WormSpec(s.kind(), s.mode(), p));
            Moves.Move m = MoveFactory.get(s);
            if (t >= 0) WormsOfArrakis.LOGGER.info("[WormsOfArrakis] freeze {} {}", m.kind, m.frame(t).line());
            n++;
        }
        int changed = n;
        String msg = t < 0 ? "freeze off: " + changed + " moving worms resume" : "frozen at t=" + t + ": " + changed + " moving worms (frame line in the log)";
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] {}", msg);
        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
        return changed;
    }

    /** True when the command carries the word force in any of its free-text arguments. */
    private static boolean hasForce(CommandContext<CommandSourceStack> ctx) {
        for (String name : new String[]{"force", "plane", "liner", "closer"}) {
            if (str(ctx, name, "").equals("force")) return true;
        }
        return false;
    }

    /** Message to the command source (chat) and to the log. */
    private static void notice(CommandContext<CommandSourceStack> ctx, String msg) {
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] {}", msg);
        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
    }

    /** Clamps a (yaw, pitch) pair per joint to the bend limit for segments of the given length unless force; says so. */
    private static double[] limitAngles(CommandContext<CommandSourceStack> ctx, double width, double yaw, double pitch, double seglen) {
        double max = BendLimit.maxJointAngleDegrees(width, seglen);
        if (Math.hypot(yaw, pitch) <= max + 1e-9 || hasForce(ctx)) return new double[]{yaw, pitch};
        double[] c = BendLimit.clampDegrees(yaw, pitch, width, seglen);
        notice(ctx, String.format(Locale.ROOT, "bend limit: %.2f deg per joint is more than the %.2f deg allowed at width %s with segments of %s (radius >= %.1f body widths); clamped to yaw %.2f pitch %.2f (add force to build it anyway)",
                Math.hypot(yaw, pitch), max, trim(width), trim(seglen), BendLimit.MIN_BEND_RADIUS_WIDTHS, c[0], c[1]));
        return c;
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%.2f", v);
    }

    /** One INFO line with the measured numbers of a move (pure maths, same as the unit tests), then the frame table. */
    private static void logMove(Moves.Move m, WormSpec s) {
        double rise = Math.sin(Math.toRadians(ScaleMath.TILT_DEG)) * ScaleMath.SKIN_LENGTH;
        String hl = String.format(Locale.ROOT, "%.2f", HeadSpec.headLength(m.width));
        switch (m.kind) {
            case "crest" -> WormsOfArrakis.LOGGER.info("[WormsOfArrakis] path crest: width={} segments={} height={}W radius={}W radius_down={}W apexTop={} (target {}) entryAngle={} exitAngle={} span={} exposedLength={} tunnelDepth={} speed={} blocks/s{}",
                    m.width, m.segments, s.p(2, 0.4), String.format(Locale.ROOT, "%.2f", m.topRadius / m.width), String.format(Locale.ROOT, "%.2f", m.radiusDown / m.width),
                    String.format(Locale.ROOT, "%.2f", m.apexY + m.r0 + rise), String.format(Locale.ROOT, "%.2f", s.p(2, 0.4) * m.width),
                    String.format(Locale.ROOT, "%.1f", m.entryAngle), String.format(Locale.ROOT, "%.1f", m.exitAngle), String.format(Locale.ROOT, "%.1f", m.span()),
                    String.format(Locale.ROOT, "%.1f", m.exposedLength), String.format(Locale.ROOT, "%.2f", m.tunnelDepth), m.speed, m.note.isEmpty() ? "" : " (" + m.note + ")");
            case "emerge" -> {
                double[] h = m.headHeights(m.sHold);
                WormsOfArrakis.LOGGER.info("[WormsOfArrakis] path emerge: width={} segments={} n={} angle={} exposed={} headBaseHeight={} headTopHeight={} HEAD_LENGTH={} hold={}s speed={} blocks/s",
                        m.width, m.segments, m.n, s.p(3, 90), m.pose(m.sHold).exposedBody(0), String.format(Locale.ROOT, "%.2f", h[0]), String.format(Locale.ROOT, "%.2f", h[1]),
                        hl, m.hold, m.speed);
            }
            default -> WormsOfArrakis.LOGGER.info("[WormsOfArrakis] path crash: width={} segments={} n={} angle={} maxExposed={} span={} apexHeight={} topRadius={} exposedPath={} HEAD_LENGTH={} ballistic={}",
                    m.width, m.segments, m.n, s.p(3, 70), m.maxExposed(), String.format(Locale.ROOT, "%.1f", m.span()), String.format(Locale.ROOT, "%.1f", m.apexY),
                    String.format(Locale.ROOT, "%.1f", m.topRadius), String.format(Locale.ROOT, "%.1f", m.sCross2 - m.sCross1), hl, s.p(9, 1) > 0);
        }
        for (int i = 0; i <= 3; i++) WormsOfArrakis.LOGGER.info("[WormsOfArrakis] {} {}", m.kind, m.frame(i / 3.0).line());
    }

    /** Parses the free text after crest width segments: height=, radius=, radius_down=, speed=, force; bare numbers are height then speed. */
    private static double[] crestOptions(String opts, boolean[] force) {
        double[] v = {0.4, 6.0, 0.0, 12.0};   // height, radius, radius_down (0 = same), speed
        int bare = 0;
        for (String tok : opts.trim().split("\s+")) {
            if (tok.isEmpty()) continue;
            if (tok.equals("force")) { force[0] = true; continue; }
            try {
                int eq = tok.indexOf('=');
                if (eq < 0) {
                    double x = Double.parseDouble(tok);
                    if (bare == 0) v[0] = x; else if (bare == 1) v[3] = x;
                    bare++;
                    continue;
                }
                double x = Double.parseDouble(tok.substring(eq + 1));
                switch (tok.substring(0, eq)) {
                    case "height", "crest_fraction" -> v[0] = x;
                    case "radius" -> v[1] = x;
                    case "radius_down" -> v[2] = x;
                    case "speed" -> v[3] = x;
                    default -> throw new IllegalArgumentException("unknown key " + tok.substring(0, eq));
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("not a number: " + tok);
            }
        }
        return v;
    }

    private static Arg w(String name) {
        return new Arg(name, DoubleArgumentType.doubleArg(1, 128));
    }

    /** literal -> arg1 -> arg2 ... ; every arg from the {@code required}-th on is optional (command runs there too). */
    private static LiteralArgumentBuilder<CommandSourceStack> branch(String kind, int required, List<Arg> args) {
        ArgumentBuilder<CommandSourceStack, ?> next = null;
        for (int i = args.size() - 1; i >= 0; i--) {
            RequiredArgumentBuilder<CommandSourceStack, ?> a = Commands.argument(args.get(i).name(), args.get(i).type());
            if (i >= required - 1) a.executes(ctx -> run(ctx, kind, args));
            if (next != null) a.then(next);
            next = a;
        }
        return Commands.literal(kind).then(next);
    }

    private static double num(CommandContext<CommandSourceStack> ctx, List<Arg> args, String name, double dflt) {
        try {
            Object v = ctx.getArgument(name, Object.class);
            return ((Number) v).doubleValue();
        } catch (IllegalArgumentException e) {
            return dflt;
        }
    }

    private static String str(CommandContext<CommandSourceStack> ctx, String name, String dflt) {
        try {
            return ctx.getArgument(name, String.class);
        } catch (IllegalArgumentException e) {
            return dflt;
        }
    }

    private static int run(CommandContext<CommandSourceStack> ctx, String kind, List<Arg> args) {
        double width = num(ctx, args, "width", 16);
        WormSpec spec;
        double reach;   // how far in front of the player the worm centre goes
        double axisShift = 0;   // moves: shift along the worm axis so the exposed part is in front of the player
        switch (kind) {
            case "ring" -> {
                int faces = (int) num(ctx, args, "faces", 0);
                spec = WormSpec.of("ring", "-", width, faces);
                reach = width / 2 + 6;
            }
            case "scale", "scales" -> {
                String dir = str(ctx, "direction", "tailward");
                String mesh = str(ctx, "mesh", "cuboid");
                if (!List.of("tailward", "headward").contains(dir) || !List.of("cuboid", "mesh").contains(mesh)) {
                    ctx.getSource().sendFailure(Component.literal("direction: tailward|headward, mesh flag: cuboid|mesh"));
                    return 0;
                }
                double count = kind.equals("scale") ? 1 : num(ctx, args, "count", 8);
                spec = WormSpec.of(kind, dir, width, count, mesh.equals("mesh") ? 1 : 0);
                reach = width / 2 + 6;
            }
            case "segment" -> {
                spec = WormSpec.of("segment", "-", width, num(ctx, args, "length", 2), num(ctx, args, "taper", 1));
                reach = width / 2 + 6;
            }
            case "chain" -> {
                double seglen = num(ctx, args, "seglen", 2);
                int segs = (int) num(ctx, args, "segments", 12);
                String closer = str(ctx, "closer", "lip");
                double per = num(ctx, args, "per", 1);
                if (closer.equals("scale") || closer.equals("shingle")) seglen = per;
                String linerWord = str(ctx, "liner", "on");
                double liner = linerWord.equals("off") ? 0 : linerWord.equals("dark") ? 2 : 1;
                double[] ang = limitAngles(ctx, width, num(ctx, args, "bend", 0), num(ctx, args, "pitch", 0), seglen);
                spec = WormSpec.of("chain", closer, width, segs, ang[0],
                        ang[1], seglen, num(ctx, args, "taper", 1), per, num(ctx, args, "lip", 0.132), liner);
                reach = width / 2 + 6;
            }
            case "curve" -> {
                int segs = (int) num(ctx, args, "segments", 40);
                double rw = num(ctx, args, "radius_widths", 2);
                String plane = str(ctx, "plane", "horizontal");
                int per = (int) num(ctx, args, "per", 1);
                if (plane.equals("force")) plane = "horizontal";
                if (!List.of("horizontal", "vertical", "diagonal").contains(plane)) {
                    ctx.getSource().sendFailure(Component.literal("plane: horizontal|vertical|diagonal"));
                    return 0;
                }
                // constant curvature: angle per joint = segment length / radius of curvature (radius = rw body widths)
                if (rw < BendLimit.MIN_BEND_RADIUS_WIDTHS && !hasForce(ctx)) {
                    notice(ctx, String.format(Locale.ROOT, "curve: radius %.2f body widths is below the limit of %.1f; clamped to %.1f (add force to build it anyway)",
                            rw, BendLimit.MIN_BEND_RADIUS_WIDTHS, BendLimit.MIN_BEND_RADIUS_WIDTHS));
                    rw = BendLimit.MIN_BEND_RADIUS_WIDTHS;
                }
                double deg = Math.toDegrees(per / (rw * width));
                double yaw = plane.equals("vertical") ? 0 : plane.equals("diagonal") ? deg / Math.sqrt(2) : deg;
                double pitch = plane.equals("horizontal") ? 0 : plane.equals("diagonal") ? deg / Math.sqrt(2) : deg;
                spec = WormSpec.of("chain", "shingle", width, segs, yaw, pitch, per, 1, per, 0.132, 1);
                WormsOfArrakis.LOGGER.info("[WormsOfArrakis] curve: width={} segments={} radius={} widths ({} blocks) plane={} => {} deg per joint (yaw {}, pitch {})",
                        width, segs, rw, rw * width, plane, String.format(Locale.ROOT, "%.3f", deg), String.format(Locale.ROOT, "%.3f", yaw), String.format(Locale.ROOT, "%.3f", pitch));
                reach = width / 2 + 6;
            }
            case "wave" -> {
                double seglen = num(ctx, args, "seglen", 2);
                int segs = (int) num(ctx, args, "segments", 12);
                String closer = str(ctx, "closer", "lip");
                if (closer.equals("scale") || closer.equals("shingle")) seglen = 1;
                double[] amp = limitAngles(ctx, width, num(ctx, args, "amplitude", 8), 0, seglen);
                spec = WormSpec.of("wave", closer, width, segs, amp[0], 0, seglen, 1, 1, 0.132);
                reach = width / 2 + 6;
            }
            case "crest", "emerge", "crash" -> {
                boolean force = hasForce(ctx);
                String mouthWord = str(ctx, "mouth", kind.equals("crest") ? "closed" : "open");
                boolean mouthOpen = mouthWord.equals("open");
                int segs = (int) num(ctx, args, "segments", 60);
                WormSpec ms;
                switch (kind) {
                    case "crest" -> {
                        boolean[] fo = {force};
                        double[] v;
                        try {
                            v = crestOptions(str(ctx, "opts", ""), fo);
                        } catch (IllegalArgumentException bad) {
                            ctx.getSource().sendFailure(Component.literal("crest: " + bad.getMessage() + " (crest <width> <segments> [height=0.4] [radius=6] [radius_down] [speed=12] [force])"));
                            return 0;
                        }
                        force = fo[0];
                        ms = MoveFactory.spec("crest", width, segs, v[0], v[1], v[3], 0, v[2], 0, false, true, force, 1.0, 1.0, 1);
                    }
                    case "emerge" -> ms = MoveFactory.spec("emerge", width, segs, num(ctx, args, "n", 10), num(ctx, args, "angle", 90), num(ctx, args, "speed", 12),
                            num(ctx, args, "hold", 2), 0, 0, mouthOpen, true, force, 1.0, 1.0, 1);
                    default -> ms = MoveFactory.spec("crash", width, segs, num(ctx, args, "n", 10), num(ctx, args, "angle", 70), num(ctx, args, "speed", 12),
                            0, num(ctx, args, "gravity", 12), num(ctx, args, "top_speed", 4), mouthOpen, !str(ctx, "ballistic", "on").equals("off"), force, 1.0, 1.0, 1);
                }
                Moves.Move mv;
                try {
                    mv = MoveFactory.get(ms);
                } catch (Moves.Rejected rej) {
                    WormsOfArrakis.LOGGER.info("[WormsOfArrakis] {} refused: {}", kind, rej.getMessage());
                    ctx.getSource().sendFailure(Component.literal(kind + " refused: " + rej.getMessage()));
                    return 0;
                }
                spec = ms;
                reach = Math.max(25.0, width * 1.2 + 15);
                axisShift = MoveFactory.centreZ(ms);
                logMove(mv, ms);
            }
            case "mouth" -> {
                if (MOUTH_DISABLED) { ctx.getSource().sendFailure(Component.literal("mouth arrives with experiment 5")); return 0; }
                String action = str(ctx, "action", "cycle");
                if (!List.of("open", "close", "cycle").contains(action)) {
                    ctx.getSource().sendFailure(Component.literal("action must be open, close or cycle"));
                    return 0;
                }
                spec = WormSpec.of("mouth", action, width);
                reach = width / 2 + 6;
            }
            default -> {
                return 0;
            }
        }
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        Vec3 pos = src.getPosition();
        Vec2 rot = src.getRotation();
        double yawRad = Math.toRadians(rot.y);
        // in front of the player, with the worm axis across the view (yaw + 90) so the side of the body is seen
        double x = pos.x - Math.sin(yawRad) * reach;
        double z = pos.z + Math.cos(yawRad) * reach;
        double axisYaw = Math.toRadians(rot.y + 90.0);   // the entity faces yaw + 90, model +Z is its facing direction
        x += Math.sin(axisYaw) * axisShift;
        z -= Math.cos(axisYaw) * axisShift;
        TestWormEntity worm = TestSetup.WORM_TEST.get().create(level, EntitySpawnReason.COMMAND);
        if (worm == null) {
            src.sendFailure(Component.literal("could not create arrakis_worm_test"));
            return 0;
        }
        worm.setSpec(spec);
        // moves: the path's zero (the base level of the sand) is the entity origin, so the engine stays relative and the renderer needs no translation
        boolean isMove = spec.kind().equals("move");
        double y = isMove ? WorldBase.resolve(level.getSeaLevel(), pos.y) : pos.y;
        if (isMove) WormsOfArrakis.LOGGER.info("[WormsOfArrakis] base level: y={} (mode {}, sea level of this level {}, constant {}, player feet {})", y, WorldBase.mode, level.getSeaLevel(), WorldBase.BASE_Y, pos.y);
        worm.snapTo(x, y, z, rot.y + 90.0F, 0.0F);
        boolean added = level.addFreshEntity(worm);
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] addFreshEntity -> {} removed={} pos={} inLevel={} chunkLoaded={}", added, worm.isRemoved(), worm.position(), level.getEntity(worm.getUUID()) != null, level.hasChunkAt(worm.blockPosition()));
        int n = WormMath.faceCount(width);
        String msg = String.format(Locale.ROOT, "spec=%s faces(auto)=%d R=%.2f at %.1f %.1f %.1f", spec.serialize(), n,
                WormMath.circumradius(n, WormMath.FACE_WIDTH), x, pos.y, z);
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] spawned test worm: {}", msg);
        src.sendSuccess(() -> Component.literal("Spawned " + msg), true);
        return 1;
    }
}
