package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;

/**
 * Prompt 13 part 1: the small API for other mods: named aggro sources, one-off adds and effect multipliers. The movement rule is the source {@code move}. All calls go to the
 * engine of the running hunt ({@link HuntAdapter}); before the server has started they do nothing and return 0.
 *
 * <p>The Holtzman shield of Equipment of Arrakis will call {@code registerEffectMultiplier(effectId, 10)} later. An effect id that does not exist is simply skipped.
 */
public final class WormAggro {
    private WormAggro() {}

    private static AggroEngine engine() { HuntAdapter a = HuntAdapter.instance(); return a == null ? null : a.manager().aggro(); }

    /** Registers a named source of aggro (a rate function and a predicate). Replaces a source of the same id. */
    public static void registerSource(AggroEngine.AggroSource s) { AggroEngine e = engine(); if (e != null) e.registerSource(s); }
    public static boolean unregisterSource(String id) { AggroEngine e = engine(); return e != null && e.unregisterSource(id); }

    /** A one-off add (negative removes, floor 0). Not multiplied by effects. Returns the new score. */
    public static double addAggro(Player p, double amount, String sourceId) { return addAggro(p.getUUID(), amount, sourceId); }
    public static double addAggro(UUID id, double amount, String sourceId) { AggroEngine e = engine(); return e == null ? 0 : e.addAggro(id, amount, sourceId); }

    public static double getAggro(Player p) { return getAggro(p.getUUID()); }
    public static double getAggro(UUID id) { AggroEngine e = engine(); return e == null ? 0 : e.getAggro(id); }

    /** A being that has this mob effect multiplies the aggro it gains from the built-in sources by the factor (the product of all, capped at AggroMultiplierMax). */
    public static void registerEffectMultiplier(Identifier effect, double factor) { AggroEngine e = engine(); if (e != null) e.registerEffectMultiplier(effect.toString(), factor); }
    public static boolean unregisterEffectMultiplier(Identifier effect) { AggroEngine e = engine(); return e != null && e.unregisterEffectMultiplier(effect.toString()); }
    public static Map<String, Double> effectMultipliers() { AggroEngine e = engine(); return e == null ? Map.of() : e.effectMultipliers(); }
}
