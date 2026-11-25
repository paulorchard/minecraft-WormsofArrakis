package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import com.paulorchard.islandcraft.wormsofarrakis.hunt.AggroEngine.PlayerState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Prompt 13 parts 1 to 4: the brain, engine-free. Every tick it feeds the aggro of the players, looks for trigger candidates, measures the depth, locks the roster, runs
 * the events and releases the lists. The game adapter gives it the players (an {@link Env}) and listens to what happens ({@link Listener}).
 */
public final class HuntManager {
    /** The world as the manager sees it: the players this tick and the depth D of the sand under a player (0 when not on sand). */
    public interface Env {
        List<PlayerState> players();
        int depth(UUID id);
    }

    /** What the game adapter does when something happens. */
    public interface Listener extends HuntEvent.Hooks {
        default void onStart(HuntEvent e, PlayerState trigger) {}
        default void onShallow(PlayerState p, int depth, double newScore) {}
    }

    public enum TriggerResult { STARTED, SHALLOW, IN_EVENT, NOT_ELIGIBLE, NOT_ON_SAND, UNKNOWN }

    private final HuntConfig cfg;
    private final AggroEngine aggro;
    private final Listener listener;
    private StrikeHook strike;
    private final Map<Long, HuntEvent> events = new LinkedHashMap<>();
    private final Map<UUID, HuntEvent> byPlayer = new HashMap<>();
    private final Set<UUID> ignored = new HashSet<>();
    private final Map<UUID, Long> shallowLoggedAt = new HashMap<>();
    private long nextId = 1;
    private long tickNo;
    private Map<UUID, PlayerState> lastStates = new HashMap<>();

    public HuntManager(HuntConfig cfg, Listener listener, StrikeHook strike) {
        this.cfg = cfg;
        this.aggro = new AggroEngine(cfg);
        this.listener = listener;
        this.strike = strike;
    }

    public HuntConfig config() { return cfg; }
    public AggroEngine aggro() { return aggro; }
    public void setStrikeHook(StrikeHook h) { this.strike = h; }
    public List<HuntEvent> events() { return new ArrayList<>(events.values()); }
    public HuntEvent eventOf(UUID id) { return byPlayer.get(id); }
    public boolean inEvent(UUID id) { return byPlayer.containsKey(id); }
    public long tickNo() { return tickNo; }
    public PlayerState lastState(UUID id) { return lastStates.get(id); }

    public boolean toggleIgnore(UUID id) { if (ignored.remove(id)) return false; ignored.add(id); return true; }
    public boolean ignored(UUID id) { return ignored.contains(id); }
    public Set<UUID> ignoredSet() { return new HashSet<>(ignored); }

    // ---- the tick ----

    /** One tick of {@code dt} seconds. */
    public void tick(double dt, Env env) {
        tickNo++;
        List<PlayerState> players = env.players();
        Map<UUID, PlayerState> byId = new HashMap<>();
        for (PlayerState s : players) byId.put(s.id(), s);
        lastStates = byId;

        // 1. aggro
        for (PlayerState s : players) aggro.update(s, tickNo, dt, byPlayer.containsKey(s.id()));

        // 2. members who cannot stay (gone, dead, creative, another dimension)
        for (HuntEvent e : events()) {
            for (UUID u : e.members()) {
                PlayerState s = byId.get(u);
                if (s == null || !s.eligible() || !s.dim().equals(e.dim)) removeFromEvent(e, u);
            }
        }

        // 3. triggers, highest score first
        List<PlayerState> cands = new ArrayList<>();
        for (PlayerState s : players) {
            if (byPlayer.containsKey(s.id()) || !s.eligible() || !s.onSand()) continue;
            if (aggro.getAggro(s.id()) >= cfg.aggroThreshold) cands.add(s);
        }
        cands.sort(Comparator.comparingDouble((PlayerState s) -> aggro.getAggro(s.id())).reversed());
        for (PlayerState s : cands) {
            if (byPlayer.containsKey(s.id())) continue;   // placed in an earlier event's list this tick
            int d = env.depth(s.id());
            SizeTable.Size size = SizeTable.forDepth(d, cfg);
            if (size == null) {
                double ns = Math.max(0, aggro.getAggro(s.id()) - cfg.shallowPenalty);
                aggro.setAggro(s.id(), ns);
                Long at = shallowLoggedAt.get(s.id());
                if (at == null || (tickNo - at) * dt >= cfg.shallowLogSeconds) {
                    shallowLoggedAt.put(s.id(), tickNo);
                    listener.onShallow(s, d, ns);
                }
                continue;
            }
            start(s, size, players);
        }

        // 4. the events
        HuntEvent.View view = new HuntEvent.View() {
            @Override public double score(UUID id) { return aggro.getAggro(id); }
            @Override public boolean onSand(UUID id) { PlayerState s = byId.get(id); return s != null && s.onSand(); }
            @Override public long lastGainTick(UUID id) { return aggro.lastGainTick(id); }
            @Override public String name(UUID id) { PlayerState s = byId.get(id); return s == null ? id.toString() : s.name(); }
        };
        for (HuntEvent e : events()) e.tick(dt, view);
    }

    // ---- starting ----

    /** Locks the roster and starts the event (the caller has checked the trigger). */
    private HuntEvent start(PlayerState trigger, SizeTable.Size size, List<PlayerState> players) {
        List<ListScan.Candidate> cs = new ArrayList<>();
        for (PlayerState p : players) cs.add(new ListScan.Candidate(p.id(), p.dim(), p.x(), p.z(), p.onSand(), byPlayer.containsKey(p.id()), p.eligible()));
        ListScan.Candidate t = new ListScan.Candidate(trigger.id(), trigger.dim(), trigger.x(), trigger.z(), true, false, true);
        List<UUID> roster = ListScan.scan(t, cs, cfg.groupRadius);
        HuntEvent[] holder = new HuntEvent[1];
        HuntEvent.Hooks bridge = new HuntEvent.Hooks() {
            @Override public void onPhaseChange(HuntEvent e, HuntEvent.Phase from, HuntEvent.Phase to) { listener.onPhaseChange(e, from, to); }
            @Override public void onTick(HuntEvent e) { listener.onTick(e); }
            @Override public void onTremor(HuntEvent e, UUID player, double strength, double progress) { listener.onTremor(e, player, strength, progress); }
            @Override public void onBuildUp(HuntEvent e, UUID player, int count, double rate, double progress) { listener.onBuildUp(e, player, count, rate, progress); }
            @Override public void onEnd(HuntEvent e, HuntEvent.EndReason reason) { release(e, reason); }
        };
        HuntEvent e = new HuntEvent(nextId++, trigger.id(), trigger.dim(), size, roster, cfg, bridge, (ev, v, w, s) -> strike.onAttack(ev, v, w, s));
        holder[0] = e;
        events.put(e.id, e);
        for (UUID u : roster) {
            byPlayer.put(u, e);
            aggro.setAggro(u, 0);   // the score is 0 as the list is locked, the trigger included
        }
        listener.onStart(e, trigger);
        return e;
    }

    /** The command: starts an event on a player now, without the threshold. With a size it ignores the depth; without one it uses the real depth. */
    public TriggerResult forceTrigger(UUID id, SizeTable.Size size, Env env) {
        PlayerState s = lastStates.get(id);
        if (s == null) return TriggerResult.UNKNOWN;
        if (byPlayer.containsKey(id)) return TriggerResult.IN_EVENT;
        if (!s.eligible()) return TriggerResult.NOT_ELIGIBLE;
        if (!s.onSand() && size == null) return TriggerResult.NOT_ON_SAND;
        SizeTable.Size use = size;
        if (use == null) {
            use = SizeTable.forDepth(env.depth(id), cfg);
            if (use == null) return TriggerResult.SHALLOW;
        }
        List<PlayerState> all = new ArrayList<>(lastStates.values());
        start(s, use, all);
        return TriggerResult.STARTED;
    }

    // ---- ending ----

    private void release(HuntEvent e, HuntEvent.EndReason reason) {
        for (UUID u : e.members()) {
            aggro.setAggro(u, 0);
            byPlayer.remove(u, e);
        }
        events.remove(e.id);
        listener.onEnd(e, reason);
    }

    private void removeFromEvent(HuntEvent e, UUID u) {
        byPlayer.remove(u, e);
        e.removeMember(u);
    }

    /** The command: ends the event of a player (all events when null); the lists are zeroed. Returns how many events ended. */
    public int stop(UUID player) {
        int n = 0;
        for (HuntEvent e : events()) {
            if (player != null && !e.has(player)) continue;
            e.end(HuntEvent.EndReason.STOPPED);
            n++;
        }
        return n;
    }

    // ---- players coming and going ----

    /** A player logs off: removed from the list with the score 99 kept for the next login (not saved); a player not in an event loses the score. */
    public void logoff(UUID id) {
        HuntEvent e = byPlayer.get(id);
        aggro.logoff(id, e != null);
        if (e != null) removeFromEvent(e, id);
        lastStates.remove(id);
        shallowLoggedAt.remove(id);
    }

    public void login(UUID id) { aggro.login(id); }

    /** A player died: removed from the list (the score stays as it is). */
    public void died(UUID id) {
        HuntEvent e = byPlayer.get(id);
        if (e != null) removeFromEvent(e, id);
    }
}
