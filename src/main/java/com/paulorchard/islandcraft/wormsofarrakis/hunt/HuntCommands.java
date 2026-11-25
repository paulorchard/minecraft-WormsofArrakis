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
        h.then(Commands.literal("trigger").executes(ctx -> trigger(ctx, ctx.getSource().getPlayerOrException(), null, null, false))
                .then(Commands.literal("self").executes(ctx -> trigger(ctx, ctx.getSource().getPlayerOrException(), null, null, false))
                        .then(sizeBranch(ctx -> ctx.getSource().getPlayerOrException())))
                .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> trigger(ctx, EntityArgument.getPlayer(ctx, "player"), null, null, false))
                        .then(sizeBranch(ctx -> EntityArgument.getPlayer(ctx, "player")))));
        h.then(Commands.literal("fake").then(Commands.literal("remove").executes(ctx -> { hunt().removeFakePlayers(); say(ctx, tr("stop.done", 0)); return 1; }))
                .then(Commands.literal("trigger").then(sizeBranchFake()))
                .then(Commands.argument("x", DoubleArgumentType.doubleArg(-30000000, 30000000)).then(Commands.argument("y", DoubleArgumentType.doubleArg(-64, 4000)).then(Commands.argument("z", DoubleArgumentType.doubleArg(-30000000, 30000000))
                        .executes(ctx -> fake(ctx, DoubleArgumentType.getDouble(ctx, "x"), DoubleArgumentType.getDouble(ctx, "y"), DoubleArgumentType.getDouble(ctx, "z")))))));
        h.then(Commands.literal("stop").executes(ctx -> stop(ctx, null))
                .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> stop(ctx, EntityArgument.getPlayer(ctx, "player")))));
        h.then(Commands.literal("ignore").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> ignore(ctx, EntityArgument.getPlayer(ctx, "player")))));
        h.then(Commands.literal("preview").then(Commands.literal("roar").executes(ctx -> previewRoar(ctx, "huge"))
                .then(Commands.argument("size", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("small", "medium", "large", "huge", "8", "16", "32", "48"), b))
                        .executes(ctx -> previewRoar(ctx, StringArgumentType.getString(ctx, "size")))))
                .then(Commands.literal("tremor").executes(ctx -> preview(ctx, 1.0, 48))
                .then(Commands.argument("strength", DoubleArgumentType.doubleArg(-1000, 1000)).executes(ctx -> preview(ctx, DoubleArgumentType.getDouble(ctx, "strength"), 48))
                        .then(Commands.argument("W", DoubleArgumentType.doubleArg(-1000, 1000)).executes(ctx -> preview(ctx, DoubleArgumentType.getDouble(ctx, "strength"), DoubleArgumentType.getDouble(ctx, "W")))))));
        root.then(h);
    }

    /** Test only: a fake player stands at x y z (the smoke start has no real player). */
    private static int fake(CommandContext<CommandSourceStack> ctx, double x, double y, double z) {
        ServerPlayer p = hunt().fakePlayer(ctx.getSource().getLevel(), x, y, z);
        say(ctx, Component.literal("Fake player " + p.getGameProfile().name() + " stands at " + HuntAdapter.fmt(x) + " " + HuntAdapter.fmt(y) + " " + HuntAdapter.fmt(z) + " (test only; it takes part in the hunt like a player)"));
        return 1;
    }

    private static ArgumentBuilder<CommandSourceStack, ?> sizeBranchFake() {
        return sizeBranch(ctx -> {
            ServerPlayer p = hunt().fakePlayerOrNull();
            if (p == null) throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("no fake player: hunt fake <x> <y> <z> first")).create();
            return p;
        });
    }

    private interface Who { ServerPlayer get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException; }

    private static final List<String> STRIKES = List.of("crest", "crash", "emerge", "engulf");

    /** trigger <who> <size> [strike <crest|crash|emerge|engulf> [force]] */
    private static ArgumentBuilder<CommandSourceStack, ?> sizeBranch(Who who) {
        return Commands.argument("size", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(SIZES, b))
                .executes(ctx -> trigger(ctx, who.get(ctx), StringArgumentType.getString(ctx, "size"), null, false))
                .then(Commands.literal("strike").then(Commands.argument("type", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(STRIKES, b))
                        .executes(ctx -> trigger(ctx, who.get(ctx), StringArgumentType.getString(ctx, "size"), StringArgumentType.getString(ctx, "type"), false))
                        .then(Commands.literal("force").executes(ctx -> trigger(ctx, who.get(ctx), StringArgumentType.getString(ctx, "size"), StringArgumentType.getString(ctx, "type"), true)))));
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

    private static int trigger(CommandContext<CommandSourceStack> ctx, ServerPlayer p, String sizeWord, String strikeWord, boolean force) {
        HuntAdapter a = hunt();
        SizeTable.Size size = null;
        if (sizeWord != null) {
            size = SizeTable.Size.of(sizeWord.toLowerCase(Locale.ROOT));
            if (size == null) return fail(ctx, tr("bad.size", sizeWord));
        }
        a.stateNow(p);
        String name = p.getGameProfile().name();
        StrikeType forcedType = null;
        String anyway = "";
        if (strikeWord != null) {
            forcedType = StrikeType.of(strikeWord.toLowerCase(Locale.ROOT));
            if (forcedType == null) return fail(ctx, tr("strike.bad_type", strikeWord));
            StrikeChoice.Availability av = a.strikes().availabilityNow(p, size);
            if (!av.has(forcedType)) {
                String why = av.why(forcedType);
                if (!force) return fail(ctx, tr("strike.refused", forcedType.label, name, why));
                anyway = tr("strike.forced_anyway", why).getString();
            }
        }
        HuntManager.TriggerResult r = a.manager().forceTrigger(p.getUUID(), size, a);
        switch (r) {
            case STARTED -> {
                HuntEvent e = a.manager().eventOf(p.getUUID());
                if (forcedType != null) { a.strikes().force(e.id, forcedType, force); say(ctx, tr("strike.forced", forcedType.label, anyway)); }
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

    /** hunt preview roar [W|small|medium|large|huge]: the roar of an event of that size for the caller (random bearing, the size's distance). */
    private static int previewRoar(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        SizeTable.Size size = SizeTable.Size.of(arg.toLowerCase(Locale.ROOT));
        if (size == null) {
            try {
                double w = Double.parseDouble(arg);
                size = SizeTable.Size.SMALL;
                for (SizeTable.Size s : SizeTable.Size.values()) if (Math.abs(s.width - w) < Math.abs(size.width - w)) size = s;
                say(ctx, Component.literal("hunt preview roar: W" + HuntAdapter.trim(w) + " is not a size of the table (8, 16, 32, 48): the nearest, " + size.label + " W" + HuntAdapter.trim(size.width) + ", is used"));
            } catch (NumberFormatException ex) {
                size = SizeTable.Size.HUGE;
                say(ctx, Component.literal("hunt preview roar: '" + arg + "' is not small, medium, large, huge or a width: huge W48 is used"));
            }
        }
        double d = Roar.distance(size, hunt().config());
        double v = Roar.volume(d, hunt().config().roarRangeMargin);
        hunt().playRoar(p, size, System.nanoTime());
        say(ctx, Component.literal(String.format(Locale.ROOT, "hunt preview roar: %s W%s: origin %.0f blocks away on a random bearing, volume %.2f (heard within %.0f blocks of it, loudness %.2f at you); see the log line 'hunt: roar origin=...'",
                size.label, HuntAdapter.trim(size.width), d, v, Roar.range(v), Roar.loudness(d, v))));
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
        say(ctx, tr("preview.done", HuntAdapter.fmt(strengthS), HuntAdapter.trim(w), HuntAdapter.fmt(f), HuntAdapter.fmt(plan.radius()), plan.maxSixteenths(), plan.particles(), HuntAdapter.fmt(plan.volume()), HuntAdapter.fmt(plan.range())));
        return 1;
    }
}
