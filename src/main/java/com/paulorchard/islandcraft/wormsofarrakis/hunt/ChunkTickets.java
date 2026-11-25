package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * Prompt 17 part 0.4: for the duration of a strike the chunks the worm and its footprint cover stay loaded and entity-ticking, so the worm is never left un-ticked and un-tracked while its move
 * plays. A vanilla chunk ticket of type {@code TicketType.DRAGON} (no timeout, flags loading + simulation, not persisted: a crash cannot leave a forced chunk behind) with radius 2 (ticket level 31 =
 * entity ticking) on each chunk, only on chunks that are loaded now (nothing is generated), nearest first, at most StrikeChunkTicketMax. Released at the end of the event on every path.
 */
public final class ChunkTickets {
    static final int RADIUS = 2;

    private record Held(ServerLevel level, List<ChunkPos> chunks) {}

    private final Map<Long, Held> held = new HashMap<>();

    /** The chunks whose centre is within {@code radius} blocks of (x, z) and that satisfy {@code loaded}, nearest first, at most {@code max}. Engine-free. */
    public static List<int[]> select(double x, double z, double radius, int max, BiPredicate<Integer, Integer> loaded) {
        int c0x = (int) Math.floor((x - radius) / 16.0) - 1, c1x = (int) Math.floor((x + radius) / 16.0) + 1;
        int c0z = (int) Math.floor((z - radius) / 16.0) - 1, c1z = (int) Math.floor((z + radius) / 16.0) + 1;
        List<int[]> l = new ArrayList<>();
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                double dx = (cx * 16 + 8) - x, dz = (cz * 16 + 8) - z;
                if (Math.hypot(dx, dz) > radius + 11.4) continue;   // 11.4 = half the diagonal of a chunk: any chunk the circle touches
                if (!loaded.test(cx, cz)) continue;
                l.add(new int[]{cx, cz});
            }
        }
        l.sort(Comparator.comparingDouble(c -> Math.hypot(c[0] * 16 + 8 - x, c[1] * 16 + 8 - z)));
        if (l.size() > max) l = new ArrayList<>(l.subList(0, Math.max(0, max)));
        return l;
    }

    void acquire(ServerLevel level, long eventId, double x, double z, double radius, int max) {
        if (max <= 0) return;
        release(eventId);
        List<int[]> sel = select(x, z, radius, max, (cx, cz) -> level.getChunkSource().getChunkNow(cx, cz) != null);
        List<ChunkPos> chunks = new ArrayList<>();
        try {
            for (int[] c : sel) {
                ChunkPos p = new ChunkPos(c[0], c[1]);
                level.getChunkSource().addTicketWithRadius(TicketType.DRAGON, p, RADIUS);
                chunks.add(p);
            }
        } catch (RuntimeException ex) {
            WormsOfArrakis.LOGGER.warn("[WormsOfArrakis] tick: the chunk tickets of event {} could not all be set: {}", eventId, ex.toString());
        }
        held.put(eventId, new Held(level, chunks));
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] tick: event {} keeps {} chunk(s) loaded and entity-ticking round ({}, {}) within {} blocks for the strike (ticket type dragon, level 31; limit {}); they are released when the event ends", eventId, chunks.size(),
                HuntAdapter.fmt(x), HuntAdapter.fmt(z), HuntAdapter.fmt(radius), max);
    }

    void release(long eventId) {
        Held h = held.remove(eventId);
        if (h == null) return;
        int n = 0;
        for (ChunkPos p : h.chunks()) {
            try {
                h.level().getChunkSource().removeTicketWithRadius(TicketType.DRAGON, p, RADIUS);
                n++;
            } catch (RuntimeException ex) {
                WormsOfArrakis.LOGGER.warn("[WormsOfArrakis] tick: a chunk ticket of event {} could not be released: {}", eventId, ex.toString());
            }
        }
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] tick: event {} released {} chunk ticket(s)", eventId, n);
    }

    int holding() { return held.values().stream().mapToInt(h -> h.chunks().size()).sum(); }
}
