package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Prompt 13 part 1: the aggro of the beings, engine-free. A score per being (at least 0); it rises only through registered sources (the movement rule is the source
 * {@code move}); one-off adds go through {@link #addAggro}; a being that gained nothing this tick decays unless it is inside an event. The game side feeds one
 * {@link PlayerState} per player per tick.
 */
public final class AggroEngine {
    /** Everything the engine needs to know of a player this tick. {@code eligible}: alive, survival or adventure, not ignored for testing. */
    public record PlayerState(UUID id, String name, String dim, double x, double y, double z, boolean onGround, boolean sprinting, boolean sneaking, boolean flying,
                              boolean swimming, boolean riding, boolean fallFlying, boolean eligible, boolean onSand) {
        /** True when the player may gain aggro by moving: no flying, swimming, vehicle, mount, elytra or boat. */
        public boolean canGainNow() { return eligible && !flying && !swimming && !riding && !fallFlying; }
    }

    /** One tick of one player as the sources see it: the horizontal blocks moved, the speed (blocks per second), the length of the tick and the movement multiplier. */
    public record Tick(PlayerState state, double blocks, double speed, double dt, double movementMultiplier) {}

    /** A named source of aggro: the gain of this tick (a rate function) and a predicate that says whether it applies. */
    public record AggroSource(String id, ToDoubleFunction<Tick> rate, Predicate<Tick> applies) {}

    /** The aggro of one being, keyed by UUID, with its kind ("player"; mobs and totems later) and the tick of the last gain. */
    public record BeingAggro(UUID id, String kind, double score, long lastGainTick) {}

    public static final String SOURCE_MOVE = "move";

    private final HuntConfig cfg;
    private final Map<UUID, BeingAggro> beings = new HashMap<>();
    private final Map<UUID, double[]> lastPos = new HashMap<>();   // {x, z}
    private final Map<UUID, String> lastDim = new HashMap<>();
    private final Map<UUID, List<String>> feeds = new HashMap<>();
    private final Map<UUID, Double> lastMultiplier = new HashMap<>();
    private final Map<String, AggroSource> sources = new LinkedHashMap<>();
    private final Map<String, Double> effectFactors = new LinkedHashMap<>();
    private final Map<UUID, Double> pendingLogin = new HashMap<>();
    private BiPredicate<UUID, String> effectProbe = (u, e) -> false;

    public AggroEngine(HuntConfig cfg) {
        this.cfg = cfg;
        registerSource(new AggroSource(SOURCE_MOVE, t -> t.blocks() * t.movementMultiplier(), t -> t.state().canGainNow() && t.state().onSand() && t.movementMultiplier() > 0));
    }

    public HuntConfig config() { return cfg; }

    // ---- the registry of sources ----

    public void registerSource(AggroSource s) { sources.put(s.id(), s); }
    public boolean unregisterSource(String id) { return sources.remove(id) != null; }
    public List<String> sourceIds() { return new ArrayList<>(sources.keySet()); }

    // ---- effect multipliers ----

    public void registerEffectMultiplier(String effectId, double factor) { effectFactors.put(effectId, factor); }
    public boolean unregisterEffectMultiplier(String effectId) { return effectFactors.remove(effectId) != null; }
    public Map<String, Double> effectMultipliers() { return Collections.unmodifiableMap(effectFactors); }
    /** How the game side says whether a being has a mob effect (an id that does not exist is simply never active). */
    public void setEffectProbe(BiPredicate<UUID, String> probe) { this.effectProbe = probe; }

    /** The product of the factors of the registered effects the being has now, at most AggroMultiplierMax; 1 with none. */
    public double effectFactor(UUID id) {
        double f = 1;
        for (Map.Entry<String, Double> e : effectFactors.entrySet()) if (effectProbe.test(id, e.getKey())) f *= e.getValue();
        return Math.min(f, cfg.aggroMultiplierMax);
    }

    // ---- scores ----

    public double getAggro(UUID id) { BeingAggro b = beings.get(id); return b == null ? 0 : b.score(); }
    public long lastGainTick(UUID id) { BeingAggro b = beings.get(id); return b == null ? -1 : b.lastGainTick(); }
    public BeingAggro being(UUID id) { return beings.get(id); }
    public List<String> feedsOf(UUID id) { return feeds.getOrDefault(id, List.of()); }
    public double lastMultiplier(UUID id) { return lastMultiplier.getOrDefault(id, 0.0); }

    public void setAggro(UUID id, double score) {
        BeingAggro b = beings.get(id);
        beings.put(id, new BeingAggro(id, b == null ? "player" : b.kind(), Math.max(0, score), b == null ? -1 : b.lastGainTick()));
    }

    /** A one-off add (negative removes, floor 0). Not multiplied by effects. Returns the new score. */
    public double addAggro(UUID id, double amount, String sourceId) {
        setAggro(id, getAggro(id) + amount);
        return getAggro(id);
    }

    // ---- log-off ----

    /** A being leaves: kept at 99 in memory when it was inside an event (given back at login), else its score is lost. */
    public void logoff(UUID id, boolean wasInEvent) {
        if (wasInEvent) pendingLogin.put(id, 99.0);
        beings.remove(id);
        lastPos.remove(id);
        lastDim.remove(id);
        feeds.remove(id);
    }

    /** A being comes back: the 99 of an event log-off is restored (not saved to disk). */
    public void login(UUID id) {
        Double p = pendingLogin.remove(id);
        if (p != null) setAggro(id, p);
    }

    public boolean hasPendingLogin(UUID id) { return pendingLogin.containsKey(id); }

    // ---- the movement rule ----

    /** The multiplier of the speed band: 0 below MinGainSpeed or sneaking or flying and so on; walking 1.0, sprinting 2.0, sprint-jumping (sprinting and airborne) 2.5. */
    public static double movementMultiplier(HuntConfig cfg, PlayerState s, double speed) {
        if (!s.canGainNow() || s.sneaking() || speed < cfg.minGainSpeed) return 0;
        if (s.sprinting()) return s.onGround() ? cfg.aggroMultiplierSprint : cfg.aggroMultiplierSprintJump;
        return cfg.aggroMultiplierWalk;
    }

    /**
     * One tick of one player: measures the horizontal move from the last position (a move above TeleportSpeed is a teleport and counts for nothing), asks every source,
     * multiplies the gain by the effect factor, adds it or decays the score (StillDecayPerSecond per second, OffSandDecayMultiplier times faster off sand; no decay in an event).
     * Returns the gain of this tick.
     */
    public double update(PlayerState s, long tickNo, double dt, boolean inEvent) {
        double[] last = lastPos.put(s.id(), new double[]{s.x(), s.z()});
        String ld = lastDim.put(s.id(), s.dim());
        double blocks = 0;
        if (last != null && s.dim().equals(ld)) blocks = Math.hypot(s.x() - last[0], s.z() - last[1]);
        double speed = blocks / dt;
        boolean teleport = speed > cfg.teleportSpeed;
        if (teleport || last == null || !s.dim().equals(ld)) { blocks = 0; speed = 0; }
        double mult = movementMultiplier(cfg, s, speed);
        lastMultiplier.put(s.id(), mult);
        Tick t = new Tick(s, blocks, speed, dt, mult);
        double gain = 0;
        List<String> fed = new ArrayList<>();
        for (AggroSource src : sources.values()) {
            if (!src.applies().test(t)) continue;
            double g = src.rate().applyAsDouble(t);
            if (g > 0) { gain += g; fed.add(src.id()); }
        }
        feeds.put(s.id(), fed);
        if (gain > 0) gain *= effectFactor(s.id());
        BeingAggro b = beings.get(s.id());
        double score = b == null ? 0 : b.score();
        long lg = b == null ? -1 : b.lastGainTick();
        if (gain > 0) {
            score += gain;
            lg = tickNo;
        } else if (!inEvent && !teleport) {
            score = Math.max(0, score - cfg.stillDecayPerSecond * dt * (s.onSand() ? 1 : cfg.offSandDecayMultiplier));
        }
        beings.put(s.id(), new BeingAggro(s.id(), b == null ? "player" : b.kind(), score, lg));
        return gain;
    }

    /** Forgets a being completely (dead, gone). */
    public void forget(UUID id) { beings.remove(id); lastPos.remove(id); lastDim.remove(id); feeds.remove(id); }
}
