package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Prompt 16 part 2: /arrakis_worm_test strike hazarddebug on|off (operator: the lethal shapes of every strike worm in range, drawn as particles for the one who ran it; off by default, never saved). */
public final class HazardCommands {
    private HazardCommands() {}

    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("strike").then(Commands.literal("hazarddebug")
                .then(Commands.literal("on").executes(ctx -> debug(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> debug(ctx, false)))
                .then(Commands.literal("status").executes(ctx -> { ctx.getSource().sendSuccess(() -> Component.literal(HuntAdapter.instance().hazard().status()), false); return 1; }))));
    }

    private static int debug(CommandContext<CommandSourceStack> ctx, boolean on) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        HuntAdapter.instance().hazard().setDebug(p, on);
        com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hazard: debug drawing {} for {}", on ? "on" : "off", p.getGameProfile().name());
        ctx.getSource().sendSuccess(() -> Component.literal(on
                ? "hazard debug on: every 2 ticks you see the body axis and rings (end_rod, red = the contact skin), the head flap outlines and the throat rim with four lines to the apex (red = the skin over the rim disc), on every deadly worm within 160 blocks. It is not saved."
                : "hazard debug off"), false);
        return 1;
    }
}
