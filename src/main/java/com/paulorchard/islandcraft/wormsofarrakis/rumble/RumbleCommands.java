package com.paulorchard.islandcraft.wormsofarrakis.rumble;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Prompt 15 part 2: the test commands /arrakis_worm_test rumble [radius] [strength] [seconds] [self|nearby], rumble ramp, stop, status and gallery. Operator only (the whole test command is). */
public final class RumbleCommands {
    private RumbleCommands() {}

    private static void say(CommandContext<CommandSourceStack> ctx, String s) { ctx.getSource().sendSuccess(() -> Component.literal(s), false); }

    private static final double D_RADIUS = 12, D_STRENGTH = 1, D_SECONDS = 4;

    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        var r = Commands.literal("rumble");
        r.executes(ctx -> play(ctx, D_RADIUS, D_STRENGTH, D_SECONDS, false));
        r.then(Commands.argument("radius", DoubleArgumentType.doubleArg(-100000, 100000)).executes(ctx -> play(ctx, DoubleArgumentType.getDouble(ctx, "radius"), D_STRENGTH, D_SECONDS, false))
                .then(Commands.argument("strength", DoubleArgumentType.doubleArg(-1000, 1000)).executes(ctx -> play(ctx, DoubleArgumentType.getDouble(ctx, "radius"), DoubleArgumentType.getDouble(ctx, "strength"), D_SECONDS, false))
                        .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(-100000, 100000)).executes(ctx -> play(ctx, DoubleArgumentType.getDouble(ctx, "radius"), DoubleArgumentType.getDouble(ctx, "strength"), DoubleArgumentType.getDouble(ctx, "seconds"), false))
                                .then(Commands.literal("self").executes(ctx -> play(ctx, DoubleArgumentType.getDouble(ctx, "radius"), DoubleArgumentType.getDouble(ctx, "strength"), DoubleArgumentType.getDouble(ctx, "seconds"), false)))
                                .then(Commands.literal("nearby").executes(ctx -> play(ctx, DoubleArgumentType.getDouble(ctx, "radius"), DoubleArgumentType.getDouble(ctx, "strength"), DoubleArgumentType.getDouble(ctx, "seconds"), true))))));
        r.then(Commands.literal("ramp").executes(ctx -> ramp(ctx, D_RADIUS, 6))
                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(-100000, 100000)).executes(ctx -> ramp(ctx, DoubleArgumentType.getDouble(ctx, "radius"), 6))
                        .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(-100000, 100000)).executes(ctx -> ramp(ctx, DoubleArgumentType.getDouble(ctx, "radius"), DoubleArgumentType.getDouble(ctx, "seconds"))))));
        r.then(Commands.literal("stop").executes(ctx -> {
            int n = RumbleManager.instance().stopAll("stopped by the command");
            say(ctx, "rumble stop: " + n + " event(s) stopped, every fake cell restored");
            return n;
        }));
        // test only (the smoke start has no real player): a rumble centred on the fake player of "hunt fake", who is its only viewer
        r.then(Commands.literal("fake").executes(ctx -> fake(ctx, D_RADIUS, D_STRENGTH, D_SECONDS))
                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(-100000, 100000)).then(Commands.argument("strength", DoubleArgumentType.doubleArg(-1000, 1000))
                        .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(-100000, 100000)).executes(ctx -> fake(ctx, DoubleArgumentType.getDouble(ctx, "radius"), DoubleArgumentType.getDouble(ctx, "strength"), DoubleArgumentType.getDouble(ctx, "seconds")))))));
        r.then(Commands.literal("status").executes(ctx -> { say(ctx, RumbleManager.instance().status()); return 1; }));
        r.then(Commands.literal("gallery").executes(RumbleCommands::gallery));
        root.then(r);
    }

    private static List<ServerPlayer> viewers(ServerPlayer p, double radius, boolean nearby) {
        List<ServerPlayer> l = new ArrayList<>();
        l.add(p);
        if (nearby) {
            double reach = radius + RumbleSettings.current().viewMargin();
            for (ServerPlayer o : p.level().players()) if (o != p && o.distanceToSqr(p.getX(), o.getY(), p.getZ()) <= reach * reach) l.add(o);
        }
        return l;
    }

    private static int play(CommandContext<CommandSourceStack> ctx, double radius, double strength, double seconds, boolean nearby) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        double s = Math.max(0, Math.min(1, strength));
        if (s != strength) say(ctx, String.format(Locale.ROOT, "rumble: strength %.2f is outside 0 to 1: %.2f used", strength, s));
        final double sf = s;
        Rumble.Params params = Rumble.Params.of(radius, seconds).withStrength(t -> sf);
        return start(ctx, p, params, nearby, "strength " + String.format(Locale.ROOT, "%.2f", s));
    }

    private static int fake(CommandContext<CommandSourceStack> ctx, double radius, double strength, double seconds) throws CommandSyntaxException {
        ServerPlayer p = com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntAdapter.instance().fakePlayerOrNull();
        if (p == null) throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("no fake player: hunt fake <x> <y> <z> first")).create();
        double s = Math.max(0, Math.min(1, strength));
        final double sf = s;
        return start(ctx, p, Rumble.Params.of(radius, seconds).withStrength(t -> sf), false, "strength " + String.format(Locale.ROOT, "%.2f", s));
    }

    private static int ramp(CommandContext<CommandSourceStack> ctx, double radius, double seconds) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Rumble.Params params = Rumble.Params.of(radius, seconds).withStrength(t -> 0.3 + 0.7 * t);
        return start(ctx, p, params, false, "strength rising from 0.3 to 1.0");
    }

    private static int start(CommandContext<CommandSourceStack> ctx, ServerPlayer p, Rumble.Params params, boolean nearby, String what) {
        ServerLevel level = p.level();
        List<ServerPlayer> v = viewers(p, Math.min(params.radius(), RumbleManager.MAX_RADIUS), nearby);
        Rumble.Handle h = Rumble.play(level, p.getX(), p.getZ(), params, v);
        for (String n : h.notes()) say(ctx, "rumble: " + n);
        say(ctx, String.format(Locale.ROOT, "rumble: playing for %.1f s, %s, %d columns drawn, %d viewer(s)%s", h.seconds(), what, h.columns(), v.size(), nearby ? " (everyone within radius + the view margin)" : " (you)"));
        return 1;
    }

    /** A row of 31 columns in front of the caller holding the offsets -15 ... 0 ... +15 still for 20 s (left to right, seen from the caller), restored afterwards. */
    private static int gallery(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ServerLevel level = p.level();
        double yaw = Math.toRadians(p.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        int sx, sz, rx, rz;   // forward and "right" steps snapped to a block axis
        if (Math.abs(fx) >= Math.abs(fz)) { sx = (int) Math.signum(fx); sz = 0; } else { sx = 0; sz = (int) Math.signum(fz); }
        rx = -sz;   // right of forward (x east, z south): forward south (0,1) has right = west (-1,0)
        rz = sx;
        int cx = (int) Math.floor(p.getX()) + sx * 4, cz = (int) Math.floor(p.getZ()) + sz * 4;
        RumbleManager m = RumbleManager.instance();
        List<RumbleCore.Col> cols = new ArrayList<>();
        List<Integer> offs = new ArrayList<>();
        for (int i = -15; i <= 15; i++) {
            int x = cx + rx * i, z = cz + rz * i;
            List<RumbleCore.Col> c = m.columns(level, x + 0.5, z + 0.5, 0.7);
            if (c.isEmpty()) continue;
            cols.add(c.get(0));
            offs.add(i);
        }
        int[] off = offs.stream().mapToInt(Integer::intValue).toArray();
        Set<ServerPlayer> v = new LinkedHashSet<>(List.of(p));
        List<String> notes = new ArrayList<>();
        Rumble.Handle h = m.playFixed(level, cx + 0.5, cz + 0.5, cols, off, 20, v, notes);
        say(ctx, "rumble gallery: " + cols.size() + " of 31 columns are sand with air above (the others are skipped), offsets -15 to +15 in sixteenths left to right, held for 20 s: stand 4 blocks back and look along the row");
        return h.columns();
    }
}
