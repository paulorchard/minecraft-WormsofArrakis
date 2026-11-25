package com.paulorchard.islandcraft.wormsofarrakis.test;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Prompt 12 part 2: {@code /arrakis-worm <small|medium|large|huge> <crash|ballistic|crest|emerge|engulf>}. Op level, literals only (no segment count, no key=value, no coordinates); it
 * builds the preset of {@link Presets} and goes through the same code as the long form of /arrakis_worm_test ({@link TestCommand#launch}: the same placement in front of the player,
 * the same base level of the sand, one new worm per command as the test command does).
 */
public final class PresetCommand {
    private PresetCommand() {}

    /** What a leaf of the tree does. */
    public interface Runner<S> { int run(CommandContext<S> ctx, Presets.Size size, String move); }

    /** The command tree: arrakis-worm, then the four sizes, then the five moves (all literals, so anything else is a parse error and tab completion lists them). */
    public static <S> LiteralArgumentBuilder<S> tree(Runner<S> runner) {
        LiteralArgumentBuilder<S> root = LiteralArgumentBuilder.literal("arrakis-worm");
        for (Presets.Size size : Presets.Size.values()) {
            LiteralArgumentBuilder<S> sz = LiteralArgumentBuilder.literal(size.label);
            for (String move : Presets.MOVES) {
                Command<S> cmd = ctx -> runner.run(ctx, size, move);
                sz.then(LiteralArgumentBuilder.<S>literal(move).executes(cmd));
            }
            root.then(sz);
        }
        return root;
    }

    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(PresetCommand.<CommandSourceStack>tree(PresetCommand::execute).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)));
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] command /arrakis-worm registered (hyphen literal; op level like /arrakis_worm_test; sizes small=8 medium=16 large=32 huge=48; moves crash ballistic crest emerge engulf)");
    }

    static int execute(CommandContext<CommandSourceStack> ctx, Presets.Size size, String move) {
        Presets.Preset p;
        try {
            p = Presets.of(size, move);
        } catch (Moves.Rejected r) {
            return TestCommand.fail(ctx, "preset " + size.label + " " + move + " refused: " + r.getMessage());
        }
        String line = p.describe();
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] {}", line);
        ctx.getSource().sendSuccess(() -> Component.literal(line), true);
        try {
            return TestCommand.launch(ctx, p.testKind(), p.spec(), p.width());
        } catch (RuntimeException e) {
            WormsOfArrakis.LOGGER.warn("[WormsOfArrakis] preset {} {} failed", size.label, move, e);
            return TestCommand.fail(ctx, "preset " + size.label + " " + move + " failed: " + e);
        }
    }
}
