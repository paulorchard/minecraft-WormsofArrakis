package com.paulorchard.islandcraft.wormsofarrakis.camera;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.SizeTable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Prompt 16 part 3: /arrakis_worm_test camera preview [small|medium|large|huge] [seconds=12] and camera stop (operator). */
public final class CameraCommands {
    private CameraCommands() {}

    private static final List<String> SIZES = List.of("small", "medium", "large", "huge");

    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("camera")
                .then(Commands.literal("preview").executes(ctx -> preview(ctx, "huge", 12))
                        .then(Commands.argument("size", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(SIZES, b)).executes(ctx -> preview(ctx, StringArgumentType.getString(ctx, "size"), 12))
                                .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(-100000, 100000)).executes(ctx -> preview(ctx, StringArgumentType.getString(ctx, "size"), DoubleArgumentType.getDouble(ctx, "seconds"))))))
                .then(Commands.literal("stop").executes(CameraCommands::stop))
                .then(Commands.literal("status").executes(ctx -> { ctx.getSource().sendSuccess(() -> Component.literal(DeathCamManager.instance().status()), false); return 1; })));
    }

    private static void say(CommandContext<CommandSourceStack> ctx, String s) {
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] camera command: {}", s);
        ctx.getSource().sendSuccess(() -> Component.literal(s), false);
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String s) {
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] camera refused: {}", s);
        ctx.getSource().sendFailure(Component.literal(s));
        return 0;
    }

    private static int preview(CommandContext<CommandSourceStack> ctx, String sizeWord, double seconds) throws CommandSyntaxException {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer p)) return fail(ctx, "camera preview needs a player: it runs the orbit on the player who types it, so it cannot be used from the console");
        SizeTable.Size size = SizeTable.Size.of(sizeWord.toLowerCase(Locale.ROOT));
        if (size == null) return fail(ctx, "camera preview: '" + sizeWord + "' is not small, medium, large or huge");
        double sec = Math.max(2, Math.min(600, seconds));
        if (sec != seconds) say(ctx, String.format(Locale.ROOT, "camera preview: %.1f s is outside 2 to 600: %.1f s used", seconds, sec));
        DeathCamManager m = DeathCamManager.instance();
        List<String> notes = new ArrayList<>();
        double g = DeathCamManager.groundAt(p.level(), p.getX(), p.getZ());
        double sandY = Double.isNaN(g) ? p.getY() : g;
        DeathCamManager.Session s = m.start(p, p.getX(), sandY, p.getZ(), p.getYRot(), size.ordinal(), System.nanoTime(), sec, sec + 1.0, -1, true, notes);
        for (String n : notes) say(ctx, "camera preview: " + n);
        if (s == null) return fail(ctx, "camera preview: the camera could not be started (see the log)");
        say(ctx, String.format(Locale.ROOT, "camera preview %s: radius %.0f, height %.0f above the sand, looking %.0f above the spot where you stand, %s %.0f degrees over %.1f s with ease-in and ease-out, then your own view returns (camera stop ends it now)",
                size.label, s.setup.radius(), s.setup.height(), s.setup.lookHeightBlocks(), s.setup.direction() > 0 ? "counter-clockwise" : "clockwise", m.params().turnDegrees(), sec));
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer p)) {
            say(ctx, "camera stop: the console has no camera; use it as a player");
            return 0;
        }
        boolean was = DeathCamManager.instance().stop(p.getUUID(), "the camera stop command");
        say(ctx, was ? "camera stop: your view is back" : "camera stop: you have no camera running");
        return was ? 1 : 0;
    }
}
