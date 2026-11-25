package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
import com.paulorchard.islandcraft.wormsofarrakis.camera.DeathCamManager;
import com.paulorchard.islandcraft.wormsofarrakis.hazard.HazardRules;
import com.paulorchard.islandcraft.wormsofarrakis.test.TestWormEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Prompt 17 parts 0.2, 3 and 4: the strike group and the state of every member, game side. The group is fixed at the start of the freeze (the victim and everybody on Dune Sand in the maximum
 * strike area); every member is held, rumbled and given the strike camera through the same code as the victim; at the choice the members outside the circle of the chosen move are released; a
 * player the worm touches is swallowed (alive, hidden, held, protected) and killed only when the strike ends. The rules are the engine-free {@link StrikeGroup} and {@link SwallowRules}; this class
 * reads the world and runs the effects. Everything is per player, keyed by UUID.
 */
final class GroupRunner {
    /** One player of a group. */
    static final class Member {
        final UUID id;
        final String name;
        final boolean late;     // not chosen at the start of the freeze: a bystander the worm touched (held at once, no rumble)
        final ServerLevel level;
        SwallowRules.State state = SwallowRules.State.HELD;
        boolean inCircle = true, onSand = true;
        String part = "";
        double touchSeconds = -1;
        final DiscRecord rec = new DiscRecord();
        boolean camera, discLogged;
        double heldX, heldZ;

        Member(UUID id, String name, boolean late, ServerLevel level) {
            this.id = id;
            this.name = name;
            this.late = late;
            this.level = level;
        }
    }

    /** The group of one event. */
    static final class Group {
        final HuntEvent event;
        final UUID victim;
        final Map<UUID, Member> members = new LinkedHashMap<>();
        double maxRadius, circleRadius, cx, cz, playSeconds, safetySeconds;
        boolean chosen;
        int strikeTicks;
        TestWormEntity worm;
        String type = "-";
        SizeTable.Size strikeSize;   // the size of the strike (prompt 19)

        Group(HuntEvent e, UUID victim) {
            this.event = e;
            this.victim = victim;
        }
    }

    private final HuntAdapter a;
    private final Map<Long, Group> groups = new LinkedHashMap<>();
    private final Map<UUID, Long> memberOf = new HashMap<>();
    public long swallowed, killed, releasedAlive;

    GroupRunner(HuntAdapter a) { this.a = a; }

    Group groupOf(long eventId) { return groups.get(eventId); }
    Group groupOfMember(UUID id) { Long e = memberOf.get(id); return e == null ? null : groups.get(e); }

    SwallowRules.State stateOf(UUID id) {
        Group g = groupOfMember(id);
        Member m = g == null ? null : g.members.get(id);
        return m == null ? null : m.state;
    }

    boolean isSwallowed(UUID id) { return stateOf(id) == SwallowRules.State.SWALLOWED; }

    int count() { return groups.size(); }

    // ---- the start of the freeze ----

    /** Builds and holds the group of an event whose freeze starts now. The victim is always in. Returns it. */
    Group start(HuntEvent e, ServerPlayer victim) {
        double radius = a.maxStrikeArea(e.size);
        Group g = new Group(e, victim.getUUID());
        g.maxRadius = radius;
        Long other = memberOf.get(victim.getUUID());
        if (other != null && other != e.id) {
            Group og = groups.get(other);
            if (og != null) {
                WormsOfArrakis.LOGGER.info("[WormsOfArrakis] group: {} is the victim of event {} and was held by event {}: they are taken out of that group", victim.getGameProfile().name(), e.id, other);
                dropMember(og, victim.getUUID(), SwallowRules.End.STOPPED, "taken by event " + e.id);
            }
        }
        String dim = victim.level().dimension().toString();
        List<StrikeGroup.Cand> cands = new ArrayList<>();
        Map<UUID, ServerPlayer> byId = new HashMap<>();
        for (ServerPlayer p : a.allPlayers()) {
            byId.put(p.getUUID(), p);
            Long held = memberOf.get(p.getUUID());
            cands.add(new StrikeGroup.Cand(p.getUUID(), p.getGameProfile().name(), p.getX(), p.getZ(), p.level().dimension().toString(), p.isAlive(), !p.isCreative() && !p.isSpectator(),
                    a.onSandNow(p), held == null || held == e.id ? StrikeGroup.Cand.FREE : held));
        }
        StrikeGroup.Cand vc = new StrikeGroup.Cand(victim.getUUID(), victim.getGameProfile().name(), victim.getX(), victim.getZ(), dim, true, true, true, StrikeGroup.Cand.FREE);
        StrikeGroup.Selection sel = StrikeGroup.select(vc, cands, radius);
        groups.put(e.id, g);
        List<String> names = new ArrayList<>(), skipped = new ArrayList<>();
        for (UUID id : sel.members()) {
            ServerPlayer p = byId.get(id);
            if (p == null) continue;
            Member m = new Member(id, p.getGameProfile().name(), false, p.level());
            m.heldX = p.getX();
            m.heldZ = p.getZ();
            g.members.put(id, m);
            memberOf.put(id, e.id);
            names.add(m.name);
            a.holdMember(e, p, e.attackSeconds);
        }
        for (Map.Entry<UUID, String> s : sel.skipped().entrySet()) {
            ServerPlayer p = byId.get(s.getKey());
            skipped.add((p == null ? s.getKey().toString() : p.getGameProfile().name()) + ": " + s.getValue());
        }
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] group: event {} size={} radius={} members={} skipped={}", e.id, e.size.label, HuntAdapter.fmt(radius), names, skipped);
        return g;
    }

    // ---- the choice at the end of the freeze ----

    /** The strike is chosen and placed: members outside the circle of the move are released, everyone still held gets the strike camera. */
    void onChoice(HuntEvent e, StrikeType type, SizeTable.Size strikeSize, double entryX, double entryZ, double yawDeg, double playSeconds, TestWormEntity worm) {
        Group g = groups.get(e.id);
        if (g == null) return;
        g.chosen = true;
        g.worm = worm;
        g.type = type.label;
        g.cx = entryX;
        g.cz = entryZ;
        g.playSeconds = playSeconds;
        g.strikeSize = strikeSize;
        g.circleRadius = a.strikeAreaRadius(type, strikeSize);
        g.safetySeconds = SwallowRules.safetySeconds(playSeconds, a.config().swallowMaxSeconds);
        Map<UUID, double[]> spots = new LinkedHashMap<>();
        for (Member m : g.members.values()) {
            ServerPlayer p = a.player(m.id);
            if (p != null) { m.heldX = p.getX(); m.heldZ = p.getZ(); }
            spots.put(m.id, new double[]{m.heldX, m.heldZ});
        }
        List<UUID> out = StrikeGroup.releasedAtChoice(g.victim, spots, entryX, entryZ, g.circleRadius, a.config().groupReleaseOutside);
        List<String> released = new ArrayList<>(), held = new ArrayList<>();
        for (UUID id : out) {
            Member m = g.members.get(id);
            m.inCircle = false;
            double d = Math.hypot(m.heldX - entryX, m.heldZ - entryZ);
            released.add(m.name + " (" + HuntAdapter.fmt(d) + " blocks)");
            m.state = SwallowRules.State.RELEASED;
            releasedAlive++;
            a.releaseMember(id, e);
            memberOf.remove(id, e.id);
            WormsOfArrakis.LOGGER.info("[WormsOfArrakis] group: {} is outside the circle ({} blocks) of the chosen {}: released, they got away", m.name, HuntAdapter.fmt(g.circleRadius), g.type);
        }
        for (Member m : g.members.values()) if (m.state == SwallowRules.State.HELD && a.player(m.id) != null) held.add(m.name);
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] group: event {} chose {} circle radius {} at ({}, {}): held={} released={}; no camera yet: each player's camera starts when they cross the mouth disc or the throat disc", e.id, g.type, HuntAdapter.fmt(g.circleRadius), HuntAdapter.fmt(entryX), HuntAdapter.fmt(entryZ), held, released);
    }

    // ---- the touch ----

    /**
     * The worm touched a player (the victim by the throat, anyone else by a part of OthersParts) or the victim's backup time came: they are swallowed. A player who is not in the group (a
     * bystander, or a member released at the choice) is held at once as a late member. Returns true when they are swallowed now.
     */
    boolean swallow(HuntEvent e, ServerPlayer p, String part, double wormSeconds, double devourTable, double backupSeconds, boolean isVictim) {
        Group g = groups.get(e.id);
        if (g == null) return false;
        Member m = g.members.get(p.getUUID());
        if (m != null && (m.state == SwallowRules.State.SWALLOWED || m.state == SwallowRules.State.KILLED)) return false;
        if (m == null || m.state == SwallowRules.State.RELEASED) {
            Long held = memberOf.get(p.getUUID());
            if (held != null && held != e.id) return false;   // held by another strike: that one decides
            m = new Member(p.getUUID(), p.getGameProfile().name(), true, p.level());
            m.heldX = p.getX();
            m.heldZ = p.getZ();
            g.members.put(m.id, m);
            memberOf.put(m.id, e.id);
            a.holdMember(e, p, 0);
        }
        m.state = SwallowRules.State.SWALLOWED;
        m.part = part == null ? "backup" : part;
        m.touchSeconds = wormSeconds;
        p.setInvisible(true);
        swallowed++;
        if (isVictim) {
            e.markDevoured();
            e.setKillCause((part == null ? "backup" : "touch:" + part) + String.format(Locale.ROOT, " at %.2f s (devour table %.2f s)", wormSeconds, devourTable));
        }
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] swallowed: {} by {} at t={} after the move started (devour table said {}){}; the kill comes at the end of the strike{}", m.name, part == null ? "the backup (the throat never reached them within " + HuntAdapter.fmt(backupSeconds) + " s after the table time)" : part,
                HuntAdapter.fmt(wormSeconds), HuntAdapter.fmt(devourTable), isVictim ? " [victim]" : m.late ? " [bystander, held at once]" : " [group member]", e.noKill() ? " (nokill: they are released alive instead)" : "");
        if (part == null) m.rec.backup(wormSeconds); else m.rec.throat(e.id, p.getUUID(), wormSeconds, listeners);
        startCamera(e, g, m, p, part == null ? "the victim's backup swallow" : "the throat disc was crossed", wormSeconds);   // the camera starts at the first of mouth disc, throat disc, backup
        return true;
    }

    // ---- the discs (prompt 18) ----

    private final List<DiscListener> listeners = new ArrayList<>();

    /** A later feature (a multiplayer detector) subscribes here; nothing else in the code needs to change. */
    void addListener(DiscListener l) { listeners.add(l); }

    /**
     * A player crossed the mouth disc at {@code wormSeconds} after the move started: the strike camera starts for them if it has not, and the time is kept. A player who is not in the group (a bystander, or a
     * member released at the choice) is held at once as a late member, as for the throat.
     */
    boolean mouth(HuntEvent e, ServerPlayer p, double wormSeconds) {
        Group g = groups.get(e.id);
        if (g == null) return false;
        Member m = g.members.get(p.getUUID());
        if (m != null && (m.state == SwallowRules.State.KILLED)) return false;
        if (m == null || m.state == SwallowRules.State.RELEASED) {
            Long held = memberOf.get(p.getUUID());
            if (held != null && held != e.id) return false;
            m = new Member(p.getUUID(), p.getGameProfile().name(), true, p.level());
            m.heldX = p.getX();
            m.heldZ = p.getZ();
            g.members.put(m.id, m);
            memberOf.put(m.id, e.id);
            a.holdMember(e, p, 0);
        }
        if (m.rec.mouth(e.id, p.getUUID(), wormSeconds, listeners)) {
            WormsOfArrakis.LOGGER.info("[WormsOfArrakis] discs: {} crossed the mouth disc at t={} after the move started{}", m.name, HuntAdapter.fmt(wormSeconds), m.late ? " [bystander, held at once]" : "");
        }
        startCamera(e, g, m, p, "the mouth disc was crossed", wormSeconds);
        return true;
    }

    /** Starts the strike camera of a player once, framed outside the worm (prompt 18 part 3). */
    private void startCamera(HuntEvent e, Group g, Member m, ServerPlayer p, String why, double wormSeconds) {
        if (m.camera) return;
        HazardRunner hr = HazardRunner.instance();
        com.paulorchard.islandcraft.wormsofarrakis.hazard.WormHazard hz = hr == null || g.worm == null ? null : hr.hazardOf(g.worm);
        StrikeType t = StrikeType.of(g.type);
        if (hz == null || t == null) {
            WormsOfArrakis.LOGGER.warn("[WormsOfArrakis] camera: no worm geometry for {} ({}): no camera", m.name, why);
            return;
        }
        long seed = e.id * 0x9E3779B97F4A7C15L ^ m.id.getLeastSignificantBits();
        double remaining = Math.max(1.0, g.playSeconds - g.strikeTicks / 20.0);
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] camera: {} starts at t={} after the move started because {}", m.name, HuntAdapter.fmt(wormSeconds), why);
        var s = DeathCamManager.instance().startStrikeCamera(p, g.worm.getYRot(), hz.R, hz.headLength(), a.camPeak(t, g.strikeSize == null ? e.size : g.strikeSize), seed, remaining, new WormSurface(g.worm, hz), e.id);
        m.camera = s != null;
    }

    /** One line per player at the end: the two times and the gap (the marker for future events). */
    private void logDiscs(Group g, Member m) {
        if (m.discLogged) return;
        m.discLogged = true;
        String body = m.rec.describe();
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] discs: {} {} ({} W{})", m.name, body, g.type, HuntAdapter.trim(g.strikeSize == null ? g.event.width : g.strikeSize.width));
    }

    // ---- every tick ----

    void tick() {
        if (groups.isEmpty()) return;
        for (Group g : new ArrayList<>(groups.values())) {
            if (g.event.ended()) continue;
            if (g.chosen) g.strikeTicks++;
            for (Member m : new ArrayList<>(g.members.values())) {
                if (m.state == SwallowRules.State.KILLED || m.state == SwallowRules.State.RELEASED) continue;
                ServerPlayer p = a.player(m.id);
                if (p == null) { dropMember(g, m.id, SwallowRules.End.LOGOFF, "logged off"); continue; }
                if (p.level() != m.level) { onDimension(p); continue; }
                if (!p.isAlive()) { dropMember(g, m.id, SwallowRules.End.DIED_OTHER, "died"); continue; }
                if (p.isCreative() || p.isSpectator()) { dropMember(g, m.id, SwallowRules.End.MODE_CHANGE, "changed to Creative or Spectator"); continue; }
                if (m.state == SwallowRules.State.SWALLOWED) {
                    if (!p.isInvisible()) p.setInvisible(true);   // the game clears the flag every tick of a living entity that has no invisibility effect
                    if (g.chosen && g.strikeTicks / 20.0 >= g.safetySeconds) {
                        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] swallowed: safety time {} s after the strike started reached for {}; the end did not come", HuntAdapter.fmt(g.safetySeconds), m.name);
                        finishMember(g, m, SwallowRules.End.SAFETY_TIME);
                    }
                }
            }
        }
    }

    // ---- the ends ----

    /** The event ended: every member still in the group gets what the end owes them. */
    void finish(HuntEvent e, HuntEvent.EndReason reason, boolean serverStopping) {
        Group g = groups.remove(e.id);
        if (g == null) return;
        SwallowRules.End why = serverStopping ? SwallowRules.End.SERVER_STOP : switch (reason) {
            case DONE -> SwallowRules.End.STRIKE_END;
            case FIZZLED -> SwallowRules.End.FIZZLED;
            case STOPPED -> SwallowRules.End.STOPPED;
        };
        int k = 0, r = 0;
        List<String> lines = new ArrayList<>();
        for (Member m : new ArrayList<>(g.members.values())) {
            SwallowRules.State before = m.state;
            finishMember(g, m, why);
            if (m.state == SwallowRules.State.KILLED && before != SwallowRules.State.KILLED) k++;
            else if (before == SwallowRules.State.HELD || before == SwallowRules.State.SWALLOWED) r++;
            lines.add(m.name + ":" + m.state.name().toLowerCase(Locale.ROOT) + (m.touchSeconds >= 0 ? "(" + m.part + " at " + HuntAdapter.fmt(m.touchSeconds) + " s)" : ""));
            memberOf.remove(m.id, e.id);
        }
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] group: event {} ended {} ({}): {} killed, {} released alive; members {}", e.id, reason, why, k, r, lines);
    }

    /** One member leaves by {@code why}; the action is the rule of {@link SwallowRules#actionFor}. */
    private void finishMember(Group g, Member m, SwallowRules.End why) {
        logDiscs(g, m);
        SwallowRules.Action act = SwallowRules.actionFor(m.state, why, g.event.noKill());
        ServerPlayer p = a.player(m.id);
        switch (act) {
            case KILL -> {
                m.state = SwallowRules.State.KILLED;   // before the kill: the damage of the kill itself is the one thing the protection lets through
                a.releaseMember(m.id, g.event);
                DeathCamManager.instance().detachFromEvent(m.id);   // the camera stays behind the death screen until Respawn
                HazardRunner hz = HazardRunner.instance();
                boolean dead = p != null && hz != null && hz.kill(p.level(), p);
                killed++;
                if (hz != null) hz.countKill();
                WormsOfArrakis.LOGGER.info("[WormsOfArrakis] group: {} is killed ({}): swallowed by {} at t={} s; dead={}", m.name, why, m.part, HuntAdapter.fmt(m.touchSeconds), dead);
                if (!dead && p != null) WormsOfArrakis.LOGGER.warn("[WormsOfArrakis] strike: {} could not be killed (damage and death were both refused); they are released", m.name);
                if (!dead) { m.state = SwallowRules.State.RELEASED; DeathCamManager.instance().releaseView(m.id, "the kill was refused, released alive"); }
            }
            case RELEASE -> {
                boolean wasSwallowed = m.state == SwallowRules.State.SWALLOWED;
                m.state = SwallowRules.State.RELEASED;
                releasedAlive++;
                if (p != null && wasSwallowed) p.setInvisible(false);
                a.releaseMember(m.id, g.event);
                DeathCamManager.instance().releaseView(m.id, "released alive (" + why + ")");
                WormsOfArrakis.LOGGER.info("[WormsOfArrakis] group: {} is released alive ({}){}", m.name, why, wasSwallowed ? ": visible and vulnerable again, view and movement back" : "");
            }
            case NONE -> {
                if (m.state != SwallowRules.State.KILLED && m.state != SwallowRules.State.RELEASED) {
                    m.state = SwallowRules.State.RELEASED;
                    a.releaseMember(m.id, g.event);
                    DeathCamManager.instance().releaseView(m.id, "left the group (" + why + ")");
                }
            }
        }
    }

    /** A member leaves on their own (log-off, death by something else, mode change, a dimension change): only their own freeze, rumble and camera go; the strike goes on. */
    private void dropMember(Group g, UUID id, SwallowRules.End why, String text) {
        Member m = g.members.get(id);
        if (m == null) return;
        if (why == SwallowRules.End.LOGOFF && m.state == SwallowRules.State.SWALLOWED && !g.event.noKill()) {
            // a swallowed player who is already gone cannot be killed here; the log-off handler has done it while they were still there
            WormsOfArrakis.LOGGER.info("[WormsOfArrakis] group: {} left while swallowed", m.name);
        }
        finishMember(g, m, why);
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] group: {} {}: out of the group of event {} (their own freeze and rumble end; the strike goes on)", m.name, text, g.event.id);
        memberOf.remove(id, g.event.id);
    }

    /** NeoForge: a player is leaving. A swallowed one is killed at once (no dodging by logging out), any other member just leaves. */
    void onLogout(ServerPlayer p) {
        Group g = groupOfMember(p.getUUID());
        if (g == null) return;
        Member m = g.members.get(p.getUUID());
        if (m == null) return;
        if (m.state == SwallowRules.State.SWALLOWED) finishMember(g, m, SwallowRules.End.LOGOFF);
        else if (m.state == SwallowRules.State.HELD) finishMember(g, m, SwallowRules.End.LOGOFF);
        memberOf.remove(p.getUUID(), g.event.id);
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] group: {} logged off: out of the group of event {}", m.name, g.event.id);
    }

    /** NeoForge: a player changed dimension. A swallowed one is killed at once, a held one is released. */
    void onDimension(ServerPlayer p) {
        Group g = groupOfMember(p.getUUID());
        if (g == null) return;
        Member m = g.members.get(p.getUUID());
        if (m == null || m.state == SwallowRules.State.KILLED || m.state == SwallowRules.State.RELEASED) return;
        finishMember(g, m, SwallowRules.End.DIMENSION);
        memberOf.remove(p.getUUID(), g.event.id);
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] group: {} changed dimension: out of the group of event {}", m.name, g.event.id);
    }

    // ---- status ----

    List<String> statusLines(HuntEvent e) {
        Group g = groups.get(e.id);
        List<String> l = new ArrayList<>();
        if (g == null) return l;
        for (Member m : g.members.values()) {
            ServerPlayer p = a.player(m.id);
            boolean sand = p != null && a.onSandNow(p);
            l.add(String.format(Locale.ROOT, "  group: %s%s on sand %s, %s, %s", m.name, m.id.equals(g.victim) ? " (victim)" : "", sand ? "yes" : "no",
                    g.chosen ? "in the chosen circle " + (m.inCircle ? "yes" : "no") : "circle not chosen yet", m.state.name().toLowerCase(Locale.ROOT)));
            if (m.rec.mouthAt >= 0 || m.rec.throatAt >= 0 || m.rec.backupAt >= 0) l.add(String.format(Locale.ROOT, "    discs: mouth %s throat %s%s%s", m.rec.mouthAt >= 0 ? "at " + HuntAdapter.fmt(m.rec.mouthAt) + " s" : "-", m.rec.throatAt >= 0 ? "at " + HuntAdapter.fmt(m.rec.throatAt) + " s" : "-", m.rec.mouthAt >= 0 && m.rec.throatAt >= 0 ? " gap " + HuntAdapter.fmt(m.rec.throatAt - m.rec.mouthAt) + " s" : "", m.rec.backupAt >= 0 ? " backup at " + HuntAdapter.fmt(m.rec.backupAt) + " s" : ""));
        }
        return l;
    }

    /** The kinds the hazard needs: is this player of this event's victim? */
    boolean isVictim(UUID id) {
        Group g = groupOfMember(id);
        return g != null && g.victim.equals(id);
    }

    static String describe(HazardRules.Verdict v) { return v.name().toLowerCase(Locale.ROOT); }
}
