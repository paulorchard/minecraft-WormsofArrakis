package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
import com.paulorchard.islandcraft.wormsofarrakis.fx.SizeFactor;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.AggroEngine.PlayerState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Prompt 13: {@code /arrakis_worm_test hunt ...}, operator only (it hangs under the test command). Chat text under the mod's language file prefix. */
public final class HuntCommands {
    private HuntCommands() {}

    private static final List<String> SIZES = List.of("small", "medium", "large", "huge");

    static Component tr(String key, Object... args) {
        return Component.translatableWithFallback(HuntTexts.PREFIX + key, HuntTexts.EN.get(key), args);
    }

    private static void say(CommandContext<CommandSourceStack> ctx, Component c) {
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt command: {}", c.getString());
        ctx.getSource().sendSuccess(() -> c, false);
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, Component c) {
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt refused: {}", c.getString());
        ctx.getSource().sendFailure(c);
        return 0;
    }

    private static HuntAdapter hunt() { return HuntAdapter.instance(); }

    /** Adds the {@code hunt} branch to the test command. */
    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> h = Commands.literal("hunt");
        h.then(Commands.literal("status").executes(HuntCommands::status));
        h.then(Commands.literal("aggro").executes(ctx -> aggro(ctx, ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> aggro(ctx, EntityArgument.getPlayer(ctx, "player")))));
        h.then(Commands.literal("add").then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(-100000, 100000)).executes(ctx -> add(ctx, EntityArgument.getPlayer(ctx, "player"), DoubleArgumentType.getDouble(ctx, "amount"))))));
        h.then(Commands.literal("trigger").executes(ctx -> trigger(ctx, ctx.getSource().getPlayerOrException(), null))
                .then(Commands.literal("self").executes(ctx -> trigger(ctx, ctx.getSource().getPlayerOrException(), null))
                        .then(sizeArg(ctx -> trigger(ctx, ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "size")))))
                .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> trigger(ctx, EntityArgument.getPlayer(ctx, "player"), null))
                        .then(sizeArg(ctx -> trigger(ctx, EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "size"))))));
        h.then(Commands.literal("stop").executes(ctx -> stop(ctx, null))
                .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> stop(ctx, EntityArgument.getPlayer(ctx, "player")))));
        h.then(Commands.literal("ignore").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> ignore(ctx, EntityArgument.getPlayer(ctx, "player")))));
        h.then(Commands.literal("preview").then(Commands.literal("tremor").executes(ctx -> preview(ctx, 1.0, 48))
                .then(Commands.argument("strength", DoubleArgumentType.doubleArg(-1000, 1000)).executes(ctx -> preview(ctx, DoubleArgumentType.getDouble(ctx, "strength"), 48))
                        .then(Commands.argument("W", DoubleArgumentType.doubleArg(-1000, 1000)).executes(ctx -> preview(ctx, DoubleArgumentType.getDouble(ctx, "strength"), DoubleArgumentType.getDouble(ctx, "W")))))));
        root.then(h);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> sizeArg(com.mojang.brigadier.Command<CommandSourceStack> run) {
        return Commands.argument("size", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(SIZES, b)).executes(run);
    }

    // ---- the commands ----

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        HuntAdapter a = hunt();
        List<HuntEvent> events = a.manager().events();
        if (events.isEmpty()) say(ctx, tr("status.none"));
        for (HuntEvent e : events) {
            say(ctx, tr("status.event", e.id, e.size.label, HuntAdapter.trim(e.width), e.phase().name(), HuntAdapter.fmt(e.secondsLeft()), e.members().size()));
            for (UUID u : e.members()) {
                PlayerState s = a.manager().lastState(u);
                String name = s == null ? u.toString() : s.name();
                say(ctx, tr("status.member", name, HuntAdapter.trim(Math.round(a.manager().aggro().getAggro(u))), s != null && s.onSand() ? tr("on_sand").getString() : tr("off_sand").getString()));
            }
            if (e.victim() != null) say(ctx, tr("status.victim", e.victimName(), HuntAdapter.trim(Math.round(e.victimScore()))));
        }
        if (ctx.getSource().getEntity() instanceof ServerPlayer p) {
            AggroEngine g = a.manager().aggro();
            say(ctx, tr("status.self", HuntAdapter.trim(Math.round(g.getAggro(p.getUUID()))), HuntAdapter.fmt(g.lastMultiplier(p.getUUID())), feeds(g, p.getUUID())));
        }
        return events.size();
    }

    private static String feeds(AggroEngine g, UUID id) {
        List<String> f = g.feedsOf(id);
        return f.isEmpty() ? HuntTexts.EN.get("none") : String.join(", ", f);
    }

    private static int aggro(CommandContext<CommandSourceStack> ctx, ServerPlayer p) {
        HuntAdapter a = hunt();
        AggroEngine g = a.manager().aggro();
        int d = a.depthNow(p);
        SizeTable.Size size = SizeTable.forDepth(d, a.config());
        String sz = size == null ? tr("aggro.noevent").getString() : tr("aggro.size", size.label, HuntAdapter.trim(size.width), HuntAdapter.trim(SizeTable.stalkSeconds(size, a.config()))).getString();
        say(ctx, tr("aggro.line", p.getGameProfile().name(), HuntAdapter.fmt(g.getAggro(p.getUUID())), HuntAdapter.fmt(g.lastMultiplier(p.getUUID())), feeds(g, p.getUUID()), d, sz));
        return 1;
    }

    private static int add(CommandContext<CommandSourceStack> ctx, ServerPlayer p, double amount) {
        double now = hunt().manager().aggro().addAggro(p.getUUID(), amount, "command");
        say(ctx, tr("add.done", p.getGameProfile().name(), HuntAdapter.fmt(now)));
        return 1;
    }

    private static int trigger(CommandContext<CommandSourceStack> ctx, ServerPlayer p, String sizeWord) {
        HuntAdapter a = hunt();
        SizeTable.Size size = null;
        if (sizeWord != null) {
            size = SizeTable.Size.of(sizeWord.toLowerCase(Locale.ROOT));
            if (size == null) return fail(ctx, tr("bad.size", sizeWord));
        }
        a.stateNow(p);
        String name = p.getGameProfile().name();
        HuntManager.TriggerResult r = a.manager().forceTrigger(p.getUUID(), size, a);
        switch (r) {
            case STARTED -> {
                HuntEvent e = a.manager().eventOf(p.getUUID());
                say(ctx, tr("trigger.started", name, e.size.label, HuntAdapter.trim(e.width), HuntAdapter.trim(e.stalkSeconds), e.members().size()));
                return 1;
            }
            case SHALLOW -> { return fail(ctx, tr("trigger.shallow", name, a.depthNow(p))); }
            case IN_EVENT -> { return fail(ctx, tr("trigger.in_event", name)); }
            case NOT_ELIGIBLE -> { return fail(ctx, tr("trigger.not_eligible", name)); }
            case NOT_ON_SAND -> { return fail(ctx, tr("trigger.not_on_sand", name)); }
            default -> { return fail(ctx, tr("trigger.unknown", name)); }
        }
    }

    private static int stop(CommandContext<CommandSourceStack> ctx, ServerPlayer p) {
        int n = hunt().stopAll(p == null ? null : p.getUUID());
        say(ctx, tr("stop.done", n));
        return n;
    }

    private static int ignore(CommandContext<CommandSourceStack> ctx, ServerPlayer p) {
        boolean on = hunt().manager().toggleIgnore(p.getUUID());
        if (on) hunt().manager().died(p.getUUID());   // an ignored player leaves any list at once
        say(ctx, tr(on ? "ignore.on" : "ignore.off", p.getGameProfile().name()));
        return 1;
    }

    private static int preview(CommandContext<CommandSourceStack> ctx, double strength, double width) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        double s = Math.max(0, Math.min(1, strength));
        if (s != strength) say(ctx, tr("bad.range", "strength " + HuntAdapter.fmt(strength), "0..1", HuntAdapter.fmt(s)));
        double w = Math.max(1, Math.min(128, width));
        if (w != width) say(ctx, tr("bad.range", "W " + HuntAdapter.trim(width), "1..128", HuntAdapter.trim(w)));
        double f = SizeFactor.of(w);
        HuntAdapter a = hunt();
        a.stateNow(p);
        double strengthS = s;   // the strength S of the formula (0.3 at the start of a stalk, 1.0 at its end)
        Tremor.Plan plan = a.playTremor(p, 0, f, strengthS);
        say(ctx, tr("preview.done", HuntAdapter.fmt(strengthS), HuntAdapter.trim(w), HuntAdapter.fmt(f), HuntAdapter.fmt(plan.radius()), plan.layers(), plan.particles(), HuntAdapter.fmt(plan.volume()), HuntAdapter.fmt(plan.range())));
        return 1;
    }
}
