package com.paulorchard.islandcraft.wormsofarrakis.rumble;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Prompt 15 part 2: the engine of the sand rumble, without any Minecraft type (the game is behind {@link Host}). It owns the events, the claims, what every viewer is shown, the
 * budget and every restore path. One core per dimension.
 * <ul>
 * <li>An event is a set of columns (x, z, surface y), each with the offset its schedule gives at the time, a set of viewers, and the cells it has still to send.</li>
 * <li>What a viewer is shown at a position is decided by the newest event that claims the column and lists the viewer ({@link #desired}); the real block is sent back when no such event is left.</li>
 * <li>Restores (end, stop, a viewer leaving, a hard timeout) are never budgeted or thinned.</li>
 * </ul>
 */
public final class RumbleCore {
    public static final double DT = 0.05;
    public static final int REAL = -1;

    /** The game behind the core. */
    public interface Host {
        /** The state id of the real block at the position (read from the world at the moment of the call). */
        int realState(int x, int y, int z);
        /** The state id of arrakis_rumble_sand at a level 1 to 15. */
        int fakeState(int level);
        /** True when raising a cell of this level at (x, y, z) would overlap the body of this viewer. */
        boolean raiseBlocked(UUID viewer, int x, int y, int z, int level);
        /** True when the viewer stands on the column (so it is not lowered). */
        boolean lowerBlocked(UUID viewer, int x, int z, int surfaceY);
        /** True when the cell above the surface block still is air (a raise may be drawn there). */
        boolean cellFree(int x, int y, int z);
        /** Sends the changes ({position, state id}) to one viewer. Returns the bytes of the packets. */
        int send(UUID viewer, List<long[]> changes);
    }

    /** One column of an event. */
    public record Col(int x, int z, int surfaceY) {}

    public static long pack(int x, int y, int z) { return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL); }
    public static int ux(long p) { return (int) (p >> 38); }
    public static int uz(long p) { return (int) (p << 26 >> 38); }
    public static int uy(long p) { return (int) (p << 52 >> 52); }
    public static long column(int x, int z) { return pack(x, 0, z); }

    /** One running rumble. */
    public static final class Ev {
        public final long id;
        public final RumbleSchedule sched;
        public final double cx, cz;
        final int[] xs, zs, ys;
        final double[] dist;
        final long[] keys;
        final Map<Long, Integer> index = new HashMap<>();
        final int[] off;
        final boolean[] alive;
        int[] fixed;
        final Set<UUID> viewers = new LinkedHashSet<>();
        final long startTick;
        final LinkedHashMap<Long, Long> pending = new LinkedHashMap<>();
        boolean ended;
        String endReason = "";
        public int peakUpdates, cellsPeak;
        public long updates, bytes;

        Ev(long id, RumbleSchedule sched, double cx, double cz, List<Col> cols, Collection<UUID> viewers, long startTick) {
            this.id = id;
            this.sched = sched;
            this.cx = cx;
            this.cz = cz;
            this.startTick = startTick;
            int n = cols.size();
            xs = new int[n]; zs = new int[n]; ys = new int[n]; dist = new double[n]; keys = new long[n]; off = new int[n]; alive = new boolean[n];
            for (int i = 0; i < n; i++) {
                Col c = cols.get(i);
                xs[i] = c.x(); zs[i] = c.z(); ys[i] = c.surfaceY();
                dist[i] = Math.hypot(c.x() + 0.5 - cx, c.z() + 0.5 - cz);
                keys[i] = column(c.x(), c.z());
                alive[i] = true;
                index.put(keys[i], i);
            }
            this.viewers.addAll(viewers);
        }

        public boolean running() { return !ended; }
        public double seconds() { return sched.params().seconds(); }
        public int columns() { int n = 0; for (boolean a : alive) if (a) n++; return n; }
        public Set<UUID> viewers() { return new LinkedHashSet<>(viewers); }
        public String endReason() { return endReason; }
        public int pending() { return pending.size(); }
    }

    private final Host host;
    private int budget;
    private final double hardMargin;
    private final Consumer<String> log;
    private final RumbleClaims claims = new RumbleClaims();
    private final Map<Long, Ev> events = new LinkedHashMap<>();
    private final Map<UUID, Map<Long, Integer>> shown = new HashMap<>();
    private long nextId = 1, now;
    public int peakUpdates;
    public long totalUpdates, totalBytes;
    private boolean overBudgetLogged;

    public RumbleCore(Host host, int budgetPerTick, double hardTimeoutMargin, Consumer<String> log) {
        this.host = host;
        this.budget = Math.max(1, budgetPerTick);
        this.hardMargin = hardTimeoutMargin;
        this.log = log == null ? s -> {} : log;
    }

    public void setBudget(int b) { this.budget = Math.max(1, b); }
    public RumbleClaims claims() { return claims; }
    public List<Ev> events() { return new ArrayList<>(events.values()); }
    public int running() { return events.size(); }
    public long now() { return now; }

    public Ev start(RumbleSchedule sched, double cx, double cz, List<Col> cols, Collection<UUID> viewers, long nowTick) {
        now = nowTick;
        Ev ev = new Ev(nextId++, sched, cx, cz, cols, viewers, nowTick);
        events.put(ev.id, ev);
        for (int i = 0; i < ev.keys.length; i++) {
            boolean others = claims.claimed(ev.keys[i]);
            claims.claim(ev.keys[i], ev.id);
            if (others) { enqueue(ev, pack(ev.xs[i], ev.ys[i], ev.zs[i])); enqueue(ev, pack(ev.xs[i], ev.ys[i] + 1, ev.zs[i])); }   // a newer event takes the column over: what the older one showed is resolved
        }
        return ev;
    }

    /** The gallery: every column holds a fixed offset (clamped to +-15) for the whole event. */
    public Ev startFixed(RumbleSchedule sched, double cx, double cz, List<Col> cols, int[] offsets, Collection<UUID> viewers, long nowTick) {
        Ev ev = start(sched, cx, cz, cols, viewers, nowTick);
        ev.fixed = new int[offsets.length];
        for (int i = 0; i < offsets.length; i++) ev.fixed[i] = Math.max(-15, Math.min(15, offsets[i]));
        return ev;
    }

    // ---- the tick ----

    public void tick(long nowTick) {
        now = nowTick;
        for (Ev ev : new ArrayList<>(events.values())) {
            double t = (nowTick - ev.startTick) * DT;
            if (t >= ev.seconds() + hardMargin) end(ev, "timeout");
            else if (t >= ev.seconds()) end(ev, "done");
            else compute(ev, t);
        }
        send();
    }

    private void compute(Ev ev, double t) {
        int cells = 0;
        for (int i = 0; i < ev.xs.length; i++) {
            if (!ev.alive[i]) continue;
            int raw = ev.fixed != null ? ev.fixed[i] : ev.sched.offset(ev.keys[i], t, ev.dist[i]);
            int eff = guard(ev, i, raw);
            if (eff != 0) cells++;
            if (eff != ev.off[i]) {
                RumbleCell o = RumbleCell.of(ev.off[i]), c = RumbleCell.of(eff);
                if (o.kind() != RumbleCell.Kind.NONE) enqueue(ev, pack(ev.xs[i], o.y(ev.ys[i]), ev.zs[i]));
                if (c.kind() != RumbleCell.Kind.NONE) enqueue(ev, pack(ev.xs[i], c.y(ev.ys[i]), ev.zs[i]));
                ev.off[i] = eff;
            }
        }
        ev.cellsPeak = Math.max(ev.cellsPeak, cells);
    }

    /** The offset after the safety rules: not raised into a block or onto a viewer's body, not lowered under a viewer's feet. */
    private int guard(Ev ev, int i, int raw) {
        RumbleCell c = RumbleCell.of(raw);
        if (c.kind() == RumbleCell.Kind.NONE) return 0;
        if (c.kind() == RumbleCell.Kind.RAISE) {
            int y = ev.ys[i] + 1;
            if (!host.cellFree(ev.xs[i], y, ev.zs[i])) return 0;
            for (UUID v : ev.viewers) if (host.raiseBlocked(v, ev.xs[i], y, ev.zs[i], c.level())) return 0;
        } else {
            for (UUID v : ev.viewers) if (host.lowerBlocked(v, ev.xs[i], ev.zs[i], ev.ys[i])) return 0;
        }
        return raw;
    }

    private void enqueue(Ev ev, long pos) { ev.pending.putIfAbsent(pos, now); }

    // ---- what is shown ----

    /** What the viewer should see at the position: a fake state id, or {@link #REAL}. The newest claiming event that lists the viewer decides. */
    int desired(UUID viewer, long pos) {
        long col = column(ux(pos), uz(pos));
        List<Long> cl = claims.claimants(col);
        for (int k = cl.size() - 1; k >= 0; k--) {
            Ev ev = events.get(cl.get(k));
            if (ev == null || ev.ended || !ev.viewers.contains(viewer)) continue;
            Integer idx = ev.index.get(col);
            if (idx == null || !ev.alive[idx]) continue;
            RumbleCell c = RumbleCell.of(ev.off[idx]);
            if (c.kind() == RumbleCell.Kind.NONE || c.y(ev.ys[idx]) != uy(pos)) return REAL;
            return host.fakeState(c.level());
        }
        return REAL;
    }

    private Set<UUID> audience(long pos) {
        Set<UUID> a = new LinkedHashSet<>();
        for (long id : claims.claimants(column(ux(pos), uz(pos)))) {
            Ev ev = events.get(id);
            if (ev != null) a.addAll(ev.viewers);
        }
        return a;
    }

    private boolean needsChange(long pos) {
        for (UUID v : audience(pos)) {
            Map<Long, Integer> sv = shown.get(v);
            int have = sv == null ? REAL : sv.getOrDefault(pos, REAL);
            if (desired(v, pos) != have) return true;
        }
        return false;
    }

    private void send() {
        List<Ev> list = new ArrayList<>();
        int total = 0;
        for (Ev ev : events.values()) {
            ev.pending.keySet().removeIf(p -> !needsChange(p));
            if (!ev.pending.isEmpty()) { list.add(ev); total += ev.pending.size(); }
        }
        if (list.isEmpty()) return;
        int[] waiting = new int[list.size()];
        for (int i = 0; i < waiting.length; i++) waiting[i] = list.get(i).pending.size();
        int[] alloc = RumbleBudget.allocate(waiting, budget);
        if (total > budget && !overBudgetLogged) {
            overBudgetLogged = true;
            log.accept("rumble: over the budget (" + total + " cells wanted, " + budget + " allowed per tick): every event thinned by one ratio, the cells that waited longest are sent first, none is starved");
        }
        Map<UUID, List<long[]>> out = new LinkedHashMap<>();
        int updates = 0;
        for (int i = 0; i < list.size(); i++) {
            Ev ev = list.get(i);
            int sent = 0;
            var it = ev.pending.entrySet().iterator();
            while (it.hasNext() && sent < alloc[i]) {
                long pos = it.next().getKey();
                it.remove();
                boolean any = false;
                for (UUID v : audience(pos)) {
                    Map<Long, Integer> sv = shown.computeIfAbsent(v, k -> new HashMap<>());
                    int have = sv.getOrDefault(pos, REAL), want = desired(v, pos);
                    if (want == have) continue;
                    if (want == REAL) sv.remove(pos); else sv.put(pos, want);
                    out.computeIfAbsent(v, k -> new ArrayList<>()).add(new long[]{pos, want == REAL ? host.realState(ux(pos), uy(pos), uz(pos)) : want});
                    any = true;
                }
                if (any) { sent++; updates++; ev.updates++; }
            }
            ev.peakUpdates = Math.max(ev.peakUpdates, sent);
        }
        long bytes = flush(out);
        for (Ev ev : list) ev.bytes += bytes / Math.max(1, list.size());
        peakUpdates = Math.max(peakUpdates, updates);
        totalUpdates += updates;
    }

    private long flush(Map<UUID, List<long[]>> out) {
        long bytes = 0;
        for (var e : out.entrySet()) bytes += Math.max(0, host.send(e.getKey(), e.getValue()));
        totalBytes += bytes;
        return bytes;
    }

    // ---- ends and restores (never budgeted) ----

    public void stop(Ev ev) { if (!ev.ended) end(ev, "stopped"); }

    public void stopAll(String reason) { for (Ev ev : new ArrayList<>(events.values())) end(ev, reason); }

    private void end(Ev ev, String reason) {
        if (ev.ended) return;
        ev.ended = true;
        ev.endReason = reason;
        events.remove(ev.id);
        List<Long> kept = new ArrayList<>();
        for (int i = 0; i < ev.keys.length; i++) if (ev.alive[i] && !claims.release(ev.keys[i], ev.id)) kept.add(ev.keys[i]);
        // everything the viewers of this event were shown on its columns now goes back to what the remaining claims say (the real block when none)
        Map<UUID, List<long[]>> out = new LinkedHashMap<>();
        int restored = 0;
        for (UUID v : ev.viewers) {
            Map<Long, Integer> sv = shown.get(v);
            if (sv == null) continue;
            for (var it = sv.entrySet().iterator(); it.hasNext(); ) {
                var en = it.next();
                long pos = en.getKey();
                if (!ev.index.containsKey(column(ux(pos), uz(pos)))) continue;
                int want = desired(v, pos);
                if (want == en.getValue()) continue;
                if (want == REAL) it.remove(); else en.setValue(want);
                out.computeIfAbsent(v, k -> new ArrayList<>()).add(new long[]{pos, want == REAL ? host.realState(ux(pos), uy(pos), uz(pos)) : want});
                restored++;
            }
        }
        long b = flush(out);
        ev.bytes += b;
        // the columns that other events still claim: what the winner draws is shown on the next tick
        for (long key : kept) {
            Ev w = events.get(claims.winner(key));
            Integer idx = w == null ? null : w.index.get(key);
            if (w != null && idx != null) { enqueue(w, pack(w.xs[idx], w.ys[idx], w.zs[idx])); enqueue(w, pack(w.xs[idx], w.ys[idx] + 1, w.zs[idx])); }
        }
        log.accept("rumble: event " + ev.id + " ended (" + reason + "): " + restored + " cells restored, " + ev.columns() + " columns, peak " + ev.peakUpdates + " updates in a tick, " + ev.updates + " updates and " + ev.bytes
                + " bytes in all, " + ev.cellsPeak + " columns moving at the most");
    }

    /** Prompt 16: the viewers of a running event change (players walked into or out of range): the ones who left get the real blocks of its columns now, the new ones get the cells that are up. Returns {added, removed}. */
    public int[] setViewers(Ev ev, Collection<UUID> now) {
        if (ev.ended) return new int[]{0, 0};
        Set<UUID> removed = new LinkedHashSet<>(ev.viewers);
        removed.removeAll(now);
        Set<UUID> added = new LinkedHashSet<>(now);
        added.removeAll(ev.viewers);
        ev.viewers.removeAll(removed);
        ev.viewers.addAll(added);
        Map<UUID, List<long[]>> out = new LinkedHashMap<>();
        for (UUID v : removed) {
            Map<Long, Integer> sv = shown.get(v);
            if (sv == null) continue;
            for (var it = sv.entrySet().iterator(); it.hasNext(); ) {
                var en = it.next();
                long pos = en.getKey();
                if (!ev.index.containsKey(column(ux(pos), uz(pos)))) continue;
                int want = desired(v, pos);
                if (want == en.getValue()) continue;
                if (want == REAL) it.remove(); else en.setValue(want);
                out.computeIfAbsent(v, k -> new ArrayList<>()).add(new long[]{pos, want == REAL ? host.realState(ux(pos), uy(pos), uz(pos)) : want});
            }
        }
        ev.bytes += flush(out);
        if (!added.isEmpty()) for (int i = 0; i < ev.xs.length; i++) {
            if (!ev.alive[i] || ev.off[i] == 0) continue;
            RumbleCell c = RumbleCell.of(ev.off[i]);
            if (c.kind() != RumbleCell.Kind.NONE) enqueue(ev, pack(ev.xs[i], c.y(ev.ys[i]), ev.zs[i]));
        }
        return new int[]{added.size(), removed.size()};
    }

    /** A viewer leaves (logs off, changes dimension, no longer in range): they are taken out of every event; with {@code canSend} the real blocks are sent to them. Returns the cells restored. */
    public int dropViewer(UUID viewer, boolean canSend) {
        for (Ev ev : events.values()) ev.viewers.remove(viewer);
        Map<Long, Integer> sv = shown.remove(viewer);
        if (sv == null || sv.isEmpty()) return 0;
        if (canSend) {
            List<long[]> ch = new ArrayList<>();
            for (long pos : sv.keySet()) ch.add(new long[]{pos, host.realState(ux(pos), uy(pos), uz(pos))});
            totalBytes += Math.max(0, host.send(viewer, ch));
        }
        return sv.size();
    }

    /** A chunk unloads: its columns leave every event and nothing is shown there any more (the chunk is gone from the viewers too). */
    public int unloadChunk(int chunkX, int chunkZ) {
        int n = 0;
        for (Ev ev : events.values()) {
            for (int i = 0; i < ev.xs.length; i++) {
                if (ev.alive[i] && (ev.xs[i] >> 4) == chunkX && (ev.zs[i] >> 4) == chunkZ) {
                    ev.alive[i] = false;
                    ev.off[i] = 0;
                    claims.release(ev.keys[i], ev.id);
                    n++;
                }
            }
        }
        for (Map<Long, Integer> sv : shown.values()) sv.keySet().removeIf(p -> (ux(p) >> 4) == chunkX && (uz(p) >> 4) == chunkZ);
        return n;
    }

    /** The chunk was (re)sent to a viewer: the real chunk has just replaced what they were shown there, so the cells the core thinks they have are sent again. Returns how many. */
    public int chunkSent(UUID viewer, int chunkX, int chunkZ) {
        Map<Long, Integer> sv = shown.get(viewer);
        if (sv == null) return 0;
        List<long[]> ch = new ArrayList<>();
        for (var e : sv.entrySet()) if ((ux(e.getKey()) >> 4) == chunkX && (uz(e.getKey()) >> 4) == chunkZ) ch.add(new long[]{e.getKey(), e.getValue()});
        if (ch.isEmpty()) return 0;
        totalBytes += Math.max(0, host.send(viewer, ch));
        return ch.size();
    }

    // ---- reading ----

    /** The state a viewer has been sent at a position (REAL when none). */
    public int shownTo(UUID viewer, long pos) { Map<Long, Integer> sv = shown.get(viewer); return sv == null ? REAL : sv.getOrDefault(pos, REAL); }

    /** Cells shown to a viewer. */
    public int shownCount(UUID viewer) { Map<Long, Integer> sv = shown.get(viewer); return sv == null ? 0 : sv.size(); }

    /** Distinct positions shown to anybody. */
    public int shownCells() {
        Set<Long> s = new HashSet<>();
        for (Map<Long, Integer> sv : shown.values()) s.addAll(sv.keySet());
        return s.size();
    }

    /** True when nothing is shown to anyone and nothing runs: every restore path ends here. */
    public boolean empty() { return events.isEmpty() && shownCells() == 0 && claims.isEmpty(); }

    /** Bytes of one section update packet of n changes as the vanilla codec writes it: 8 (section) + varint(n) + for each change a varlong of (state id << 12 | pos). */
    public static int packetBytes(int[] stateIds) {
        int b = 8 + varInt(stateIds.length);
        for (int id : stateIds) b += varLong(((long) id << 12) | 0xFFF);
        return b;
    }

    static int varInt(int v) { int n = 1; while ((v & -128) != 0) { v >>>= 7; n++; } return n; }
    static int varLong(long v) { int n = 1; while ((v & -128L) != 0) { v >>>= 7; n++; } return n; }
}
