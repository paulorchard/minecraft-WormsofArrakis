package com.paulorchard.islandcraft.wormsofarrakis.hazard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Prompt 16 part 2: who dies when the worm touches them, in one small function ({@link #decide}) so the multiplayer discussion can change it in one place. Engine-free: the game side fills a
 * {@link Subject} from the entity.
 */
public final class HazardRules {
    private HazardRules() {}

    /** What the rule needs to know about an entity (filled by the adapter). */
    public record Subject(boolean living, boolean player, boolean creative, boolean spectator, boolean alive, boolean worm, boolean camera, boolean exempt, boolean refusedBefore) {}

    public enum Verdict {
        KILL, NOT_LIVING, DEAD, CREATIVE, SPECTATOR, WORM, CAMERA, EXEMPT, PLAYERS_OFF, MOBS_OFF, REFUSED_BEFORE
    }

    /**
     * The rule: everything living that touches the worm dies, except worms, the camera entity, the dead, players in Creative or Spectator, exempt entities (a predicate or the tag
     * worm_immune), a mob that has already refused to die, and the kinds the config switches off.
     */
    public static Verdict decide(Subject s, boolean killPlayers, boolean killMobs) {
        if (!s.living()) return Verdict.NOT_LIVING;
        if (s.worm()) return Verdict.WORM;
        if (s.camera()) return Verdict.CAMERA;
        if (!s.alive()) return Verdict.DEAD;
        if (s.exempt()) return Verdict.EXEMPT;
        if (s.player()) {
            if (s.creative()) return Verdict.CREATIVE;
            if (s.spectator()) return Verdict.SPECTATOR;
            return killPlayers ? Verdict.KILL : Verdict.PLAYERS_OFF;
        }
        if (s.refusedBefore()) return Verdict.REFUSED_BEFORE;
        return killMobs ? Verdict.KILL : Verdict.MOBS_OFF;
    }

    /** Which worms are deadly: the strike worm of a hunt event always; the others (the commands of the test) only when HazardOnTestWorms is on. */
    public static boolean deadlyWorm(boolean strikeWorm, boolean onTestWorms) { return strikeWorm || onTestWorms; }

    /** What one worm has already done: the first touch kills an entity exactly once, a mob that refused to die is remembered (logged once, ignored afterwards). */
    public static final class Ledger {
        private final java.util.Set<java.util.UUID> killed = new java.util.HashSet<>(), refused = new java.util.HashSet<>();

        /** True the first time for an entity. */
        public boolean firstKill(java.util.UUID id) { return killed.add(id); }
        public boolean killed(java.util.UUID id) { return killed.contains(id); }
        /** True the first time an entity refuses (the caller logs it then). */
        public boolean firstRefusal(java.util.UUID id) { return refused.add(id); }
        public boolean refused(java.util.UUID id) { return refused.contains(id); }
    }

    /** victim: the player the hunt chose; other: any other player; mob: everything else. */
    public static String kind(boolean victim, boolean player) { return victim ? "victim" : player ? "other" : "mob"; }

    /** The exemptions registered by code (the rideable worms of a later prompt will register theirs): any one saying yes exempts the entity. Generic so it is testable without the game. */
    public static final class Exemptions<T> {
        private final List<Predicate<T>> list = new ArrayList<>();

        public synchronized void register(Predicate<T> p) { if (p != null) list.add(p); }
        public synchronized void clear() { list.clear(); }
        public synchronized int size() { return list.size(); }

        public synchronized boolean exempt(T e) {
            for (Predicate<T> p : list) {
                try { if (p.test(e)) return true; } catch (RuntimeException ex) { /* a broken predicate never makes anything deadly or immune by itself */ }
            }
            return false;
        }
    }

    /** The log line of a kill. {@code touchSeconds} and {@code devourSeconds} are seconds after the move started. */
    public static String killLine(String who, String part, double touchSeconds, double devourSeconds, String kind) {
        return String.format(Locale.ROOT, "hazard: %s killed by %s at t=%.2f after the move started (devour table said %.2f) kind=%s", who, part, touchSeconds, devourSeconds, kind);
    }

    /** The line when the backup kills the victim (no touch happened). */
    public static String backupLine(String who, double atSeconds, double devourSeconds, double backupSeconds) {
        return String.format(Locale.ROOT, "hazard: %s killed by the backup at t=%.2f after the move started (devour table said %.2f plus %.2f s of backup; the worm never touched them) kind=victim", who, atSeconds, devourSeconds, backupSeconds);
    }
}
