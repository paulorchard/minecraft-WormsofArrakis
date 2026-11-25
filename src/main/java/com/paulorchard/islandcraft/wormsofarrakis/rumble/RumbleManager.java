package com.paulorchard.islandcraft.wormsofarrakis.rumble;

import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntAdapter;
import it.unimi.dsi.fastutil.shorts.ShortArraySet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Prompt 15 part 2: the game side of the rumble. One {@link RumbleCore} per dimension; the host builds multi-block update packets (one per chunk section per viewer) from a scratch
 * section, so the fake states never touch the world; every restore path ends in the core.
 */
public final class RumbleManager {
    private static RumbleManager instance;

    public static RumbleManager instance() {
        if (instance == null) instance = new RumbleManager();
        return instance;
    }

    static void init() { NeoForge.EVENT_BUS.register(instance()); }

    private MinecraftServer server;
    private final Map<ResourceKey<Level>, LevelCore> cores = new LinkedHashMap<>();
    private final Map<UUID, ServerPlayer> players = new HashMap<>();
    private long tickNo;
    /** The most columns one rumble draws (a guard: radius 64 is about 12,800 columns). */
    public static final double MAX_RADIUS = 64, MAX_SECONDS = 600;

    /** The state ids of arrakis_rumble_sand, level 0 (unused) to 15. */
    private static int[] fakeIds;

    static int fakeId(int level) {
        if (fakeIds == null) {
            int[] ids = new int[16];
            for (int l = 1; l <= 15; l++) ids[l] = Block.getId(RumbleBlocks.RUMBLE_SAND.get().defaultBlockState().setValue(RumbleSandBlock.LEVEL, l));
            fakeIds = ids;
        }
        return fakeIds[Math.max(1, Math.min(15, level))];
    }

    private final class LevelCore implements RumbleCore.Host {
        final ServerLevel level;
        final RumbleCore core;
        LevelChunkSection scratch;

        LevelCore(ServerLevel level, RumbleSettings st) {
            this.level = level;
            this.core = new RumbleCore(this, st.budgetPerTick(), RumbleSettings.HARD_TIMEOUT_MARGIN, s -> WormsOfArrakis.LOGGER.info("[WormsOfArrakis] {}", s));
        }

        @Override public int realState(int x, int y, int z) { return Block.getId(level.getBlockState(new BlockPos(x, y, z))); }
        @Override public int fakeState(int lv) { return fakeId(lv); }

        @Override public boolean raiseBlocked(UUID viewer, int x, int y, int z, int lv) {
            ServerPlayer p = players.get(viewer);
            if (p == null || p.level() != level) return false;
            return p.getBoundingBox().intersects(new AABB(x, y, z, x + 1, y + lv / 16.0, z + 1));
        }

        @Override public boolean lowerBlocked(UUID viewer, int x, int z, int surfaceY) {
            ServerPlayer p = players.get(viewer);
            if (p == null || p.level() != level) return false;
            AABB b = p.getBoundingBox();
            double top = surfaceY + 1.0;
            return b.minX < x + 1 && b.maxX > x && b.minZ < z + 1 && b.maxZ > z && p.getY() >= top - 0.25 && p.getY() <= top + 1.0;
        }

        @Override public boolean cellFree(int x, int y, int z) {
            BlockPos pos = new BlockPos(x, y, z);
            return level.hasChunkAt(pos) && level.getBlockState(pos).isAir();
        }

        @Override public int send(UUID viewer, List<long[]> changes) {
            ServerPlayer p = players.get(viewer);
            if (p == null || p.connection == null || p.level() != level) return 0;
            if (scratch == null) scratch = new LevelChunkSection(level.palettedContainerFactory());
            Map<Long, List<long[]>> bySection = new LinkedHashMap<>();
            for (long[] c : changes) {
                long pos = c[0];
                bySection.computeIfAbsent(SectionPos.asLong(RumbleCore.ux(pos) >> 4, RumbleCore.uy(pos) >> 4, RumbleCore.uz(pos) >> 4), k -> new ArrayList<>()).add(c);
            }
            int bytes = 0;
            for (var e : bySection.entrySet()) {
                SectionPos sp = SectionPos.of(e.getKey());
                ShortSet set = new ShortArraySet();
                int[] ids = new int[e.getValue().size()];
                int i = 0;
                for (long[] c : e.getValue()) {
                    BlockPos bp = new BlockPos(RumbleCore.ux(c[0]), RumbleCore.uy(c[0]), RumbleCore.uz(c[0]));
                    scratch.setBlockState(bp.getX() & 15, bp.getY() & 15, bp.getZ() & 15, Block.stateById((int) c[1]), false);
                    set.add(SectionPos.sectionRelativePos(bp));
                    ids[i++] = (int) c[1];
                }
                p.connection.send(new ClientboundSectionBlocksUpdatePacket(sp, set, scratch));
                bytes += RumbleCore.packetBytes(ids);
            }
            return bytes;
        }
    }

    private LevelCore coreOf(ServerLevel level) {
        LevelCore lc = cores.get(level.dimension());
        if (lc == null || lc.level != level) {
            lc = new LevelCore(level, RumbleSettings.current());
            cores.put(level.dimension(), lc);
        }
        return lc;
    }

    // ---- play ----

    Rumble.Handle play(ServerLevel level, double x, double z, Rumble.Params params, Collection<ServerPlayer> viewers) {
        RumbleSettings st = RumbleSettings.current();
        List<String> notes = new ArrayList<>();
        double radius = params.radius();
        if (radius > MAX_RADIUS) { notes.add(String.format(Locale.ROOT, "radius %.1f is over the limit: %.0f used", radius, MAX_RADIUS)); radius = MAX_RADIUS; }
        if (radius < 1) { notes.add(String.format(Locale.ROOT, "radius %.1f is under the limit: 1 used", radius)); radius = 1; }
        double seconds = params.seconds();
        if (seconds > MAX_SECONDS) { notes.add(String.format(Locale.ROOT, "seconds %.1f is over the limit: %.0f used", seconds, MAX_SECONDS)); seconds = MAX_SECONDS; }
        if (seconds < 0.1) { notes.add(String.format(Locale.ROOT, "seconds %.2f is under the limit: 0.1 used", seconds)); seconds = 0.1; }
        int max = params.maxSixteenths();
        if (max < 1 || max > 15) { notes.add("maxSixteenths " + max + " is outside 1 to 15: " + RumbleCell.limit(max) + " used"); max = RumbleCell.limit(max); }
        double minPeriod = RumbleSchedule.minPeriod(st.numbers());
        double period = params.pulseSeconds();
        if (period < minPeriod) { notes.add(String.format(Locale.ROOT, "pulseSeconds %.2f is under %.2f (pulses would overlap): %.2f used", period, minPeriod, minPeriod)); period = minPeriod; }
        double edge = Math.max(0, Math.min(1, params.edgeWeight()));
        if (edge != params.edgeWeight()) notes.add("edgeWeight " + params.edgeWeight() + " is outside 0 to 1: " + edge + " used");
        Rumble.Params p = new Rumble.Params(radius, seconds, params.strength(), max, period, edge, params.seed(), params.share());

        LevelCore lc = coreOf(level);
        lc.core.setBudget(st.budgetPerTick());
        Set<UUID> ids = new LinkedHashSet<>();
        for (ServerPlayer v : viewers) {
            if (v.level() != level) continue;
            players.put(v.getUUID(), v);
            ids.add(v.getUUID());
        }
        List<RumbleCore.Col> cols = columns(level, x, z, radius);
        if (cols.isEmpty()) notes.add("no sand column with air above it in the loaded chunks within " + String.format(Locale.ROOT, "%.0f", radius) + " blocks: nothing is drawn");
        RumbleCore.Ev ev = lc.core.start(new RumbleSchedule(p, st.numbers()), x, z, cols, ids, tickNo);
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] rumble: event {} started at {} {} radius {} for {} s, max {}/16, pulse {} s, edge {}, {} columns, {} viewer(s), seed {}", ev.id, fmt(x), fmt(z), fmt(radius), fmt(seconds), max, fmt(period), fmt(edge),
                cols.size(), ids.size(), p.seed());
        for (String n : notes) WormsOfArrakis.LOGGER.info("[WormsOfArrakis] rumble: event {}: {}", ev.id, n);
        return new HandleImpl(lc, ev, notes, cols.size());
    }

    /** The gallery: a fixed offset on every column of a row (the test command). */
    Rumble.Handle playFixed(ServerLevel level, double x, double z, List<RumbleCore.Col> cols, int[] offsets, double seconds, Collection<ServerPlayer> viewers, List<String> notes) {
        RumbleSettings st = RumbleSettings.current();
        LevelCore lc = coreOf(level);
        Set<UUID> ids = new LinkedHashSet<>();
        for (ServerPlayer v : viewers) {
            if (v.level() != level) continue;
            players.put(v.getUUID(), v);
            ids.add(v.getUUID());
        }
        Rumble.Params p = Rumble.Params.of(1, seconds, 15, st.pulseSeconds(), 1.0, 0);
        RumbleCore.Ev ev = lc.core.startFixed(new RumbleSchedule(p, st.numbers()), x, z, cols, offsets, ids, tickNo);
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] rumble: gallery event {} started: {} columns held still for {} s, {} viewer(s)", ev.id, cols.size(), fmt(seconds), ids.size());
        return new HandleImpl(lc, ev, notes, cols.size());
    }

    private static final class HandleImpl implements Rumble.Handle {
        final LevelCore lc;
        final RumbleCore.Ev ev;
        final List<String> notes;
        final int cols;

        HandleImpl(LevelCore lc, RumbleCore.Ev ev, List<String> notes, int cols) { this.lc = lc; this.ev = ev; this.notes = notes; this.cols = cols; }

        @Override public void stop() { lc.core.stop(ev); }
        @Override public boolean running() { return ev.running(); }
        @Override public double seconds() { return ev.seconds(); }
        @Override public List<String> notes() { return notes; }
        @Override public int columns() { return cols; }
        @Override public int[] setViewers(Collection<ServerPlayer> viewers) {
            Set<UUID> ids = new LinkedHashSet<>();
            for (ServerPlayer v : viewers) {
                if (v.level() != lc.level) continue;
                instance().players.put(v.getUUID(), v);
                ids.add(v.getUUID());
            }
            return lc.core.setViewers(ev, ids);
        }
    }

    private static boolean isSand(BlockState st) {
        HuntAdapter a = HuntAdapter.instance();
        return a != null ? a.isSandBlock(st) : st.is(Blocks.SAND) || st.is(Blocks.RED_SAND);
    }

    /**
     * The drawn columns round (cx, cz): only chunks that are loaded (never loaded or generated here), the top block found with the heightmap (MOTION_BLOCKING_NO_LEAVES) and a search of
     * a few blocks downwards; the top block must be sand and the block above it air. A column with a placed block on it, whose top is not sand, or in an unloaded chunk is skipped, not an error.
     */
    public List<RumbleCore.Col> columns(ServerLevel level, double cx, double cz, double radius) {
        List<RumbleCore.Col> out = new ArrayList<>();
        int r = (int) Math.ceil(radius);
        int bx = (int) Math.floor(cx), bz = (int) Math.floor(cz);
        Map<Long, ChunkAccess> chunks = new HashMap<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int x = bx + dx, z = bz + dz;
                if (Math.hypot(x + 0.5 - cx, z + 0.5 - cz) > radius) continue;
                long ck = net.minecraft.world.level.ChunkPos.pack(x >> 4, z >> 4);
                ChunkAccess ch = chunks.computeIfAbsent(ck, k -> level.getChunkSource().getChunkNow(x >> 4, z >> 4));
                if (ch == null) continue;
                int top = ch.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);   // ChunkAccess.getHeight: the y of the top block (Level.getHeight is one more)
                for (int y = top; y >= top - 3; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState st = ch.getBlockState(pos);
                    if (st.isAir()) continue;
                    if (isSand(st) && ch.getBlockState(pos.above()).isAir()) out.add(new RumbleCore.Col(x, z, y));
                    break;
                }
            }
        }
        return out;
    }

    // ---- the game's events ----

    @SubscribeEvent
    public void onStarting(ServerStartingEvent e) { server = e.getServer(); }

    @SubscribeEvent
    public void onTick(ServerTickEvent.Post e) {
        tickNo++;
        if (cores.isEmpty()) return;
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) players.put(p.getUUID(), p);
        for (LevelCore lc : new ArrayList<>(cores.values())) {
            for (RumbleCore.Ev ev : lc.core.events()) {
                for (UUID v : ev.viewers()) {
                    ServerPlayer p = players.get(v);
                    if (p == null || p.isRemoved() || p.level() != lc.level) {
                        int n = lc.core.dropViewer(v, p != null && !p.isRemoved() && p.level() == lc.level);
                        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] rumble: viewer {} left the event area or the dimension: {} cells forgotten for them", p == null ? v.toString() : p.getGameProfile().name(), n);
                    }
                }
            }
            lc.core.tick(tickNo);
        }
    }

    @SubscribeEvent
    public void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        int n = 0;
        for (LevelCore lc : cores.values()) n += lc.core.dropViewer(id, false);
        players.remove(id);
        if (n > 0) WormsOfArrakis.LOGGER.info("[WormsOfArrakis] rumble: {} logged off: {} cells forgotten for them (the real chunks are theirs on the next log-in)", e.getEntity().getGameProfile().name(), n);
    }

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        UUID id = e.getEntity().getUUID();
        for (LevelCore lc : cores.values()) lc.core.dropViewer(id, false);
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload e) {
        if (!(e.getLevel() instanceof ServerLevel sl)) return;
        LevelCore lc = cores.get(sl.dimension());
        if (lc == null) return;
        int n = lc.core.unloadChunk(e.getChunk().getPos().x(), e.getChunk().getPos().z());
        if (n > 0) WormsOfArrakis.LOGGER.info("[WormsOfArrakis] rumble: chunk {} unloaded: {} columns left their events, their cells forgotten", e.getChunk().getPos(), n);
    }

    /** A chunk sent again to a viewer (it was out of range and came back) while an event runs: the cells they should have are sent again (chosen: resend, not drop). */
    @SubscribeEvent
    public void onChunkSent(ChunkWatchEvent.Sent e) {
        LevelCore lc = cores.get(e.getLevel().dimension());
        if (lc == null) return;
        int n = lc.core.chunkSent(e.getPlayer().getUUID(), e.getPos().x(), e.getPos().z());
        if (n > 0) WormsOfArrakis.LOGGER.info("[WormsOfArrakis] rumble: chunk {} sent again to {} during an event: its {} cells sent again", e.getPos(), e.getPlayer().getGameProfile().name(), n);
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload e) {
        if (!(e.getLevel() instanceof ServerLevel sl)) return;
        LevelCore lc = cores.remove(sl.dimension());
        if (lc != null) lc.core.stopAll("world unloaded");
    }

    @SubscribeEvent
    public void onStopping(ServerStoppingEvent e) {
        int n = 0;
        for (LevelCore lc : cores.values()) { n += lc.core.running(); lc.core.stopAll("server stopping"); }
        cores.clear();
        players.clear();
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] rumble: server stopping: {} event(s) stopped, every fake cell restored", n);
    }

    // ---- reading (commands) ----

    public int stopAll(String reason) {
        int n = 0;
        for (LevelCore lc : cores.values()) { n += lc.core.running(); lc.core.stopAll(reason); }
        return n;
    }

    public String status() {
        int ev = 0, cells = 0, peak = 0;
        long updates = 0, bytes = 0;
        for (LevelCore lc : cores.values()) {
            ev += lc.core.running();
            cells += lc.core.shownCells();
            peak = Math.max(peak, lc.core.peakUpdates);
            updates += lc.core.totalUpdates;
            bytes += lc.core.totalBytes;
        }
        return "rumble: " + ev + " event(s) running, " + cells + " cells shown now, peak " + peak + " updates in a tick, " + updates + " updates and " + bytes + " bytes sent in all";
    }

    /** True when every dimension's core is empty: nothing running, nothing shown, no claim (used by the smoke test and the tests). */
    public boolean allEmpty() {
        for (LevelCore lc : cores.values()) if (!lc.core.empty()) return false;
        return true;
    }

    private static String fmt(double v) { return String.format(Locale.ROOT, "%.2f", v); }
}
