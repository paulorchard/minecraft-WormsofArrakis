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
                new Arg("lip", DoubleArgumentType.doubleArg(0.0, 1.0)))));
        root.then(branch("wave", 2, List.of(w("width"), new Arg("segments", IntegerArgumentType.integer(1, 400)),
                new Arg("amplitude", DoubleArgumentType.doubleArg(0, 45)), new Arg("seglen", DoubleArgumentType.doubleArg(0.25, 64)),
                new Arg("closer", StringArgumentType.word()))));
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
                if (closer.equals("scale")) seglen = per;
                spec = WormSpec.of("chain", closer, width, segs, num(ctx, args, "bend", 0),
                        num(ctx, args, "pitch", 0), seglen, num(ctx, args, "taper", 1), per, num(ctx, args, "lip", 0.132));
                reach = width / 2 + 6;
            }
            case "wave" -> {
                double seglen = num(ctx, args, "seglen", 2);
                int segs = (int) num(ctx, args, "segments", 12);
                String closer = str(ctx, "closer", "lip");
                if (closer.equals("scale")) seglen = 1;
                spec = WormSpec.of("wave", closer, width, segs, num(ctx, args, "amplitude", 8), 0, seglen, 1, 1, 0.132);
                reach = width / 2 + 6;
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
        TestWormEntity worm = TestSetup.WORM_TEST.get().create(level, EntitySpawnReason.COMMAND);
        if (worm == null) {
            src.sendFailure(Component.literal("could not create arrakis_worm_test"));
            return 0;
        }
        worm.setSpec(spec);
        worm.snapTo(x, pos.y, z, rot.y + 90.0F, 0.0F);
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
