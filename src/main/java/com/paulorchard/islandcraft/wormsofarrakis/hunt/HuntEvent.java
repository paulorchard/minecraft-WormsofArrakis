package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Prompt 13 part 4 and prompt 14: one event, a state machine, engine-free. STALKING (width + StalkExtraSeconds) then FREEZE (AttackSeconds: the victim is chosen,
 * {@link StrikeHook#onAttack} is called and the victim is held; formerly ATTACK) then STRIKE (the move runs, the event waits for it) then ENDED. The list is fixed at the lock; members can only leave. The size and the stalk length never change.
 */
public final class HuntEvent {
    public enum Phase { STALKING, FREEZE, STRIKE, ENDED }
    public enum EndReason { DONE, FIZZLED, STOPPED }

    /** What the event asks of the world about its members. */
    public interface View {
        double score(UUID id);
        boolean onSand(UUID id);
        long lastGainTick(UUID id);
        String name(UUID id);
    }

    /** The hooks of later prompts and of the game adapter. */
    public interface Hooks {
        default void onPhaseChange(HuntEvent e, Phase from, Phase to) {}
        default void onTick(HuntEvent e) {}
        default void onEnd(HuntEvent e, EndReason reason) {}
        /** FREEZE is over: the strike starts. The default (no strike runner) finishes it at once; the game adapter runs the move and calls {@link HuntEvent#finishStrike()} when it is over. */
        default void onStrike(HuntEvent e) { e.finishStrike(); }
        /** A tremor of strength S for one listed player (the camera of a later prompt can use this too). */
        default void onTremor(HuntEvent e, UUID player, double strength, double progress) {}
        /** The continuous build-up of one listed player: {@code count} sand grains this tick (whole number) at the rate per second {@code rate}. */
        default void onBuildUp(HuntEvent e, UUID player, int count, double rate, double progress) {}
    }

    public final long id;
    public final UUID trigger;
    public final String dim;
    public final SizeTable.Size size;
    public final double width, stalkSeconds, attackSeconds;
    public final double factor;
    private final HuntConfig cfg;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Map<UUID, Tremor.Clock> clocks = new HashMap<>();
    private final Map<UUID, Double> buildAcc = new HashMap<>();
    private final Hooks hooks;
    private final StrikeHook strike;
    private Phase phase = Phase.STALKING;
    private double phaseTime;
    private UUID victim;
    private String victimName = "";
    private double victimScore;
    private EndReason endReason;
    private boolean strikeDone, devoured;
    private double strikeLimit = Double.MAX_VALUE;
    private String strikeType = "", strikeDir = "";

    public HuntEvent(long id, UUID trigger, String dim, SizeTable.Size size, List<UUID> roster, HuntConfig cfg, Hooks hooks, StrikeHook strike) {
        this.id = id;
        this.trigger = trigger;
        this.dim = dim;
        this.size = size;
        this.width = size.width;
        this.cfg = cfg;
        this.stalkSeconds = SizeTable.stalkSeconds(size, cfg);
        this.attackSeconds = cfg.attackSeconds;
        this.factor = com.paulorchard.islandcraft.wormsofarrakis.fx.SizeFactor.of(size.width);
        this.hooks = hooks;
        this.strike = strike;
        this.members.addAll(roster);
        for (UUID u : roster) clocks.put(u, new Tremor.Clock(id, u, cfg));
    }

    public Phase phase() { return phase; }
    public double phaseTime() { return phaseTime; }
    public UUID victim() { return victim; }
    public String victimName() { return victimName; }
    public double victimScore() { return victimScore; }
    public EndReason endReason() { return endReason; }
    public boolean ended() { return phase == Phase.ENDED; }
    public boolean devoured() { return devoured; }
    public String strikeType() { return strikeType; }
    public String strikeDir() { return strikeDir; }
    private String killCause = "";
    /** How the victim died: "touch:head at 3.20 s (devour table 4.58 s)" or "backup ..." (empty while alive or when nobody died). */
    public String killCause() { return killCause; }
    public void setKillCause(String c) { killCause = c; }
    /** The victim has been devoured (killed); from now on the victim leaving the list does not cancel the event. */
    public void markDevoured() { devoured = true; }
    /** The strike is over (the move has finished): the event ends DONE on the next tick. */
    public void finishStrike() { strikeDone = true; }
    /** The longest the event waits for the strike, in seconds from the start of STRIKE (a safety net; the move's own end normally comes first). */
    public void setStrikeLimit(double seconds) { strikeLimit = seconds; }
    public void setStrikeInfo(String type, String dir) { strikeType = type; strikeDir = dir; }
    public List<UUID> members() { return new ArrayList<>(members); }
    public boolean has(UUID id) { return members.contains(id); }
    public Tremor.Clock clockOf(UUID id) { return clocks.get(id); }
    /** The fraction of the stalk elapsed, 0 to 1 (1 after it). */
    public double progress() { return phase == Phase.STALKING ? Math.min(1, phaseTime / stalkSeconds) : 1; }
    public double secondsLeft() { return phase == Phase.STALKING ? Math.max(0, stalkSeconds - phaseTime) : phase == Phase.FREEZE ? Math.max(0, attackSeconds - phaseTime) : phase == Phase.STRIKE && strikeLimit < Double.MAX_VALUE ? Math.max(0, strikeLimit - phaseTime) : 0; }

    /** A member leaves (log-off, death, mode, dimension). Nobody joins later. An empty list ends the event FIZZLED. */
    public void removeMember(UUID id) {
        if (ended() || !members.remove(id)) return;
        clocks.remove(id);
        buildAcc.remove(id);
        if (phase == Phase.STRIKE) {   // the victim leaving before the devour cancels the strike; after it (the death itself) the move runs on
            if (id.equals(victim) && !devoured) end(EndReason.FIZZLED);
            return;
        }
        if (members.isEmpty() || (phase == Phase.FREEZE && id.equals(victim))) end(EndReason.FIZZLED);   // the victim leaves during the freeze: no strike
    }

    /** The victim: the highest score among the members on sand right now; ties go to the one who gained most recently. Null when nobody is on sand. */
    public static UUID chooseVictim(Iterable<UUID> members, View v) {
        UUID best = null;
        for (UUID u : members) {
            if (!v.onSand(u)) continue;
            if (best == null || v.score(u) > v.score(best) || (v.score(u) == v.score(best) && v.lastGainTick(u) > v.lastGainTick(best))) best = u;
        }
        return best;
    }

    /** Advances the event by {@code dt} seconds. */
    public void tick(double dt, View v) {
        if (ended()) return;
        phaseTime += dt;
        if (phase == Phase.STALKING) {
            double p = progress();
            double s = Tremor.strength(p);
            for (UUID u : members()) {
                Tremor.Clock c = clocks.get(u);
                boolean on = v.onSand(u);
                if (c != null && c.due(phaseTime)) {
                    if (on) { c.fired(phaseTime); hooks.onTremor(this, u, s, p); }
                    else c.skipped(phaseTime);
                }
                double rate = on ? Tremor.buildUpRate(p, factor, cfg) : 0;
                if (rate > 0) {
                    double acc = buildAcc.merge(u, rate * dt, Double::sum);
                    int n = (int) Math.floor(acc);
                    buildAcc.put(u, acc - n);
                    if (n > 0) hooks.onBuildUp(this, u, n, rate, p);
                }
            }
            hooks.onTick(this);
            if (phaseTime >= stalkSeconds - 1e-9) {
                UUID vic = chooseVictim(members, v);
                if (vic == null) { end(EndReason.FIZZLED); return; }
                victim = vic;
                victimName = v.name(vic);
                victimScore = v.score(vic);
                Phase from = phase;
                phase = Phase.FREEZE;
                phaseTime = 0;
                hooks.onPhaseChange(this, from, phase);
                strike.onAttack(this, victim, width, size.label);
            }
        } else if (phase == Phase.FREEZE) {
            hooks.onTick(this);
            if (phaseTime >= attackSeconds - 1e-9) {
                Phase from = phase;
                phase = Phase.STRIKE;
                phaseTime = 0;
                hooks.onPhaseChange(this, from, phase);
                hooks.onStrike(this);
                if (!ended() && strikeDone) end(EndReason.DONE);
            }
        } else if (phase == Phase.STRIKE) {
            hooks.onTick(this);
            if (strikeDone || phaseTime >= strikeLimit - 1e-9) end(EndReason.DONE);
        }
    }

    /** Ends the event now (the list is released by the manager through {@link Hooks#onEnd}). */
    public void end(EndReason reason) {
        if (ended()) return;
        Phase from = phase;
        phase = Phase.ENDED;
        endReason = reason;
        hooks.onPhaseChange(this, from, Phase.ENDED);
        hooks.onEnd(this, reason);
    }
}
