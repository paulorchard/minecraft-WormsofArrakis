package com.paulorchard.islandcraft.wormsofarrakis;

import static org.junit.jupiter.api.Assertions.*;

import com.paulorchard.islandcraft.wormsofarrakis.rumble.Rumble;
import com.paulorchard.islandcraft.wormsofarrakis.rumble.RumbleBudget;
import com.paulorchard.islandcraft.wormsofarrakis.rumble.RumbleCell;
import com.paulorchard.islandcraft.wormsofarrakis.rumble.RumbleClaims;
import com.paulorchard.islandcraft.wormsofarrakis.rumble.RumbleCore;
import com.paulorchard.islandcraft.wormsofarrakis.rumble.RumbleSchedule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Prompt 15 part 2: the schedule, the claims, the budget and every restore path of the core (no Minecraft type is used). */
class Prompt15RumbleTest {
    static final RumbleSchedule.Numbers N = RumbleSchedule.Numbers.DEFAULTS;

    static RumbleSchedule sched(double radius, double seconds, int max, long seed) {
        return new RumbleSchedule(Rumble.Params.of(radius, seconds, max, 1.0, 0.25, seed), N);
    }

    // ---- the schedule ----

    @Test
    void sameArgumentsSameResult() {
        RumbleSchedule a = sched(12, 4, 8, 7), b = sched(12, 4, 8, 7), c = sched(12, 4, 8, 8);
        boolean differs = false;
        for (long col = 0; col < 200; col++) for (double t = 0; t < 4; t += 0.05) {
            assertEquals(a.offset(col, t, 5), b.offset(col, t, 5));
            if (a.offset(col, t, 5) != c.offset(col, t, 5)) differs = true;
        }
        assertTrue(differs, "another seed gives another result");
    }

    @Test
    void pulsesAreShortAndNeverOverlapTheNext() {
        RumbleSchedule s = sched(12, 40, 8, 3);
        assertEquals(0.57, s.maxPulseSeconds(), 1e-9, "2 x 0.06 + 4 x 0.09 x 1.25");
        for (long col = 0; col < 300; col++) for (long period = 0; period < 39; period++) {
            double start = s.pulseStart(col, period), len = s.pulseLength(col, period, 3);
            double from = s.phase(col) + period; assertTrue(start >= from - 1e-9 && start <= from + 1.0 - s.maxPulseSeconds() + 1e-9, "starts inside its own period, early enough");
            assertTrue(len <= s.maxPulseSeconds() + 1e-9);
            if (len > 0) { assertTrue(len >= 0.12 + 4 * 0.09 * 0.75 - 1e-9 && len < 0.6); assertTrue(start + len <= from + 1.0 + 1e-9, "ends before the next period of the column starts"); }
        }
        // the offset is 0 between the end of a pulse and the next period
        for (long col = 0; col < 100; col++) {
            double start = s.pulseStart(col, 5), len = s.pulseLength(col, 5, 0);
            if (len > 0) assertEquals(0, s.offset(col, start + len + 0.001, 0), "back at the surface after the rebound");
        }
    }

    @Test
    void weightAtTheCentreAndTheEdge() {
        RumbleSchedule s = sched(10, 4, 8, 1);
        assertEquals(1.0, s.weight(0), 1e-9);
        assertEquals(0.25, s.weight(10), 1e-9);
        assertEquals(0.625, s.weight(5), 1e-9);
        assertEquals(0.25, s.weight(50), 1e-9, "beyond the radius: the edge weight");
    }

    @Test
    void strengthScalesTheDepthAndTheOffsetsStayInRange() {
        for (int max : new int[]{1, 4, 8, 15}) {
            RumbleSchedule full = sched(12, 6, max, 5);
            RumbleSchedule half = new RumbleSchedule(Rumble.Params.of(12, 6, max, 1.0, 0.25, 5).withStrength(t -> 0.5), N);
            double sumFull = 0, sumHalf = 0;
            for (long col = 0; col < 400; col++) for (long period = 0; period < 6; period++) {
                int d1 = full.depth(col, period, 0), d2 = half.depth(col, period, 0);
                sumFull += d1;
                sumHalf += d2;
                assertTrue(d1 >= 0 && d1 <= max && d2 >= 0 && d2 <= max);
            }
            assertTrue(sumHalf < sumFull * 0.65 && sumHalf > sumFull * 0.35 || max == 1, "half strength is about half the depth (max " + max + ")");
            for (long col = 0; col < 200; col++) for (double t = 0; t < 6; t += 0.02) {
                int o = full.offset(col, t, col % 13);
                assertTrue(o >= -max && o <= max, "offset " + o + " within +-" + max);
            }
        }
        RumbleSchedule off = new RumbleSchedule(Rumble.Params.of(12, 6, 8, 1.0, 0.25, 5).withStrength(t -> 0), N);
        for (long col = 0; col < 100; col++) for (double t = 0; t < 6; t += 0.05) assertEquals(0, off.offset(col, t, 0));
        assertEquals(0, sched(12, 4, 8, 1).offset(1, 4.0, 0), "after the end: nothing");
        assertEquals(0, sched(12, 4, 8, 1).offset(1, -1, 0), "before the start: nothing");
    }

    @Test
    void atLeastOneSixteenthWhenStrengthTimesWeightIsAboveATenth() {
        RumbleSchedule s = new RumbleSchedule(Rumble.Params.of(12, 6, 8, 1.0, 0.25, 9).withStrength(t -> 0.2), N);   // 0.2 x 0.25 at the edge = 0.05: none; at the centre 0.2: at least 1
        for (long col = 0; col < 200; col++) for (long period = 0; period < 4; period++) {
            assertTrue(s.depth(col, period, 0) >= 1, "0.2 x weight 1 is above 0.1");
            assertTrue(s.depth(col, period, 12) <= 2, "0.05 at the edge: tiny (rounded 8 x 0.05 x (0.7..1.3) = 0 or 1)");
        }
    }

    @Test
    void columnsAreNotAllInStep() {
        RumbleSchedule s = sched(12, 4, 8, 11);
        for (double t : new double[]{0.7, 1.3, 2.45, 3.1}) {
            int up = 0, down = 0, still = 0, n = 2000;
            Set<Integer> values = new HashSet<>();
            for (long col = 0; col < n; col++) {
                int o = s.offset(col, t, col % 12);
                if (o > 0) up++; else if (o < 0) down++; else still++;
                values.add(o);
            }
            assertTrue(still > n * 0.2, "a good share of the columns is at rest at any moment (" + still + ")");
            assertTrue(up > 0 && down > 0, "some are raised and some lowered at the same moment (up " + up + ", down " + down + ")");
            assertTrue(up + down < n * 0.8, "not all moving");
            assertTrue(values.size() >= 5, "many different heights at once");
        }
    }

    @Test
    void thereIsNoGlobalBeat() {
        // every column has its own phase: at any moment about the same share of the columns is moving (a pulse is about half of a period)
        RumbleSchedule s = sched(12, 10, 8, 31);
        int n = 3000;
        double lo = 1, hi = 0;
        for (double t = 3.0; t < 4.0; t += 0.02) {
            int moving = 0;
            for (long col = 0; col < n; col++) if (s.offset(col, t, 4) != 0) moving++;
            lo = Math.min(lo, moving / (double) n);
            hi = Math.max(hi, moving / (double) n);
        }
        assertTrue(lo > 0.33 && hi < 0.66, "the share of moving columns stays in a narrow band over a whole period (" + lo + " to " + hi + ")");
    }

    @Test
    void theShareOfPulsingColumnsFollowsTheStrengthSquared() {
        // the stalk tremor: at strength S a share S squared of the columns pulses
        for (double sx : new double[]{0.3, 0.65, 1.0}) {
            final double st = sx;
            RumbleSchedule s = new RumbleSchedule(Rumble.Params.of(12, 4, 8, 1.0, 0.25, 21).withStrength(t -> st).withShare(x -> x * x), N);
            int pulsing = 0, n = 4000;
            for (long col = 0; col < n; col++) if (s.depth(col, 0, 0) > 0) pulsing++;
            assertEquals(st * st, pulsing / (double) n, 0.04, "S = " + st);
        }
    }

    // ---- claims and budget ----

    @Test
    void theNewerClaimDrawsAndTheRealBlockReturnsWhenNobodyClaims() {
        RumbleClaims c = new RumbleClaims();
        c.claim(5, 1);
        c.claim(5, 2);
        assertEquals(2, c.winner(5));
        assertFalse(c.release(5, 2), "event 1 still claims it: no restore");
        assertEquals(1, c.winner(5));
        assertTrue(c.release(5, 1), "nobody claims: the real block returns");
        assertEquals(-1, c.winner(5));
        c.claim(7, 1); c.claim(7, 2);
        assertFalse(c.release(7, 1), "the older one leaving changes nothing");
        assertEquals(2, c.winner(7));
    }

    @Test
    void overTheBudgetEveryEventIsThinnedByOneRatioAndNobodyIsStarved() {
        int[] a = RumbleBudget.allocate(new int[]{100, 300, 1, 0, 199}, 600);
        assertArrayEquals(new int[]{100, 300, 1, 0, 199}, a, "under the budget: everything");
        int[] b = RumbleBudget.allocate(new int[]{1000, 3000, 1, 0, 2}, 600);   // 4003 wanted, ratio 0.1499
        double ratio = 600 / 4003.0;
        assertEquals((int) Math.floor(1000 * ratio), b[0]);
        assertEquals((int) Math.floor(3000 * ratio), b[1]);
        assertEquals(1, b[2], "one waiting cell is not starved to zero");
        assertEquals(0, b[3]);
        assertEquals(1, b[4]);
        assertTrue(b[0] * 1.0 / 1000 < 0.16 && b[1] * 1.0 / 3000 < 0.16 && b[0] * 1.0 / 1000 > 0.14, "the same ratio for the big events");
        assertEquals(1.0, RumbleBudget.ratio(10, 600));
    }

    // ---- the core with a fake game ----

    static final class FakeHost implements RumbleCore.Host {
        final Map<UUID, Map<Long, Integer>> client = new HashMap<>();
        final Set<UUID> standing = new HashSet<>();   // viewers who stand in the middle: nothing is lowered, nothing is raised into them
        final Set<Long> blockedCells = new HashSet<>();
        int sends, cellsSent;

        @Override public int realState(int x, int y, int z) { return 1; }
        @Override public int fakeState(int level) { return 100 + level; }
        @Override public boolean raiseBlocked(UUID v, int x, int y, int z, int level) { return standing.contains(v) && Math.abs(x) <= 1 && Math.abs(z) <= 1 || blockedCells.contains(RumbleCore.pack(x, y, z)); }
        @Override public boolean lowerBlocked(UUID v, int x, int z, int sy) { return standing.contains(v) && Math.abs(x) <= 1 && Math.abs(z) <= 1; }
        @Override public boolean cellFree(int x, int y, int z) { return !blockedCells.contains(RumbleCore.pack(x, y, z)); }
        @Override public int send(UUID v, List<long[]> changes) {
            sends++;
            cellsSent += changes.size();
            Map<Long, Integer> m = client.computeIfAbsent(v, k -> new HashMap<>());
            int[] ids = new int[changes.size()];
            int i = 0;
            for (long[] c : changes) {
                if (c[1] == 1) m.remove(c[0]); else m.put(c[0], (int) c[1]);
                ids[i++] = (int) c[1];
            }
            return RumbleCore.packetBytes(ids);
        }
        int shown(UUID v) { return client.getOrDefault(v, Map.of()).size(); }
        int shownAll() { int n = 0; for (var m : client.values()) n += m.size(); return n; }
    }

    static List<RumbleCore.Col> grid(int r) {
        List<RumbleCore.Col> l = new ArrayList<>();
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) if (Math.hypot(x, z) <= r) l.add(new RumbleCore.Col(x, z, 64));
        return l;
    }

    static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3);

    static RumbleCore core(FakeHost h, int budget, List<String> log) { return new RumbleCore(h, budget, 3.0, log::add); }

    static long run(RumbleCore c, long from, int ticks) { for (int i = 0; i < ticks; i++) c.tick(from + i); return from + ticks; }

    @Test
    void aRunShowsFakesAndTheEndRestoresEverything() {
        FakeHost h = new FakeHost();
        List<String> log = new ArrayList<>();
        RumbleCore c = core(h, 600, log);
        c.start(sched(10, 2, 8, 4), 0.5, 0.5, grid(10), List.of(A), 0);
        long t = 0;
        int most = 0;
        for (int i = 0; i < 30; i++) { c.tick(++t); most = Math.max(most, h.shown(A)); }
        assertTrue(most > 10, "the sand moves (" + most + " cells shown at the most)");
        run(c, t + 1, 40);   // past the end (2 s = 40 ticks)
        assertEquals(0, h.shownAll(), "nothing is left raised or lowered");
        assertTrue(c.empty());
        assertTrue(log.stream().anyMatch(s -> s.contains("ended (done)") && s.contains("peak")), "the peak updates and the bytes are logged at the end: " + log);
    }

    @Test
    void stopRestoresAtOnce() {
        FakeHost h = new FakeHost();
        RumbleCore c = core(h, 600, new ArrayList<>());
        RumbleCore.Ev ev = c.start(sched(10, 20, 8, 4), 0.5, 0.5, grid(10), List.of(A, B), 0);
        run(c, 1, 25);
        assertTrue(h.shownAll() > 0);
        c.stop(ev);
        assertEquals(0, h.shownAll());
        assertTrue(c.empty());
        assertFalse(ev.running());
    }

    @Test
    void aViewerLeavingIsRestoredForThemOnly() {
        FakeHost h = new FakeHost();
        RumbleCore c = core(h, 600, new ArrayList<>());
        c.start(sched(10, 20, 8, 4), 0.5, 0.5, grid(10), List.of(A, B), 0);
        run(c, 1, 25);
        int bBefore = h.shown(B);
        assertTrue(h.shown(A) > 0 && bBefore > 0);
        c.dropViewer(A, true);
        assertEquals(0, h.shown(A), "restored for the one who left");
        assertEquals(0, c.shownCount(A));
        run(c, 26, 5);
        assertTrue(h.shown(B) > 0, "the other still sees the rumble");
        assertEquals(0, h.shown(A), "and the one who left is not sent anything any more");
        c.dropViewer(B, false);   // logged off: nothing can be sent, but the core forgets everything
        assertEquals(0, c.shownCount(B));
        c.stopAll("test");
        assertTrue(c.empty());
    }

    @Test
    void aChunkUnloadingDropsItsColumnsAndAChunkSentAgainIsResent() {
        FakeHost h = new FakeHost();
        RumbleCore c = core(h, 600, new ArrayList<>());
        c.start(sched(20, 20, 8, 4), 0.5, 0.5, grid(20), List.of(A), 0);
        run(c, 1, 25);
        int before = c.shownCount(A);
        assertTrue(before > 0);
        h.client.get(A).clear();   // the client has just been sent the real chunk(s): the fakes are gone from it
        int resent = 0;
        for (int cx = -2; cx <= 1; cx++) for (int cz = -2; cz <= 1; cz++) resent += c.chunkSent(A, cx, cz);
        assertEquals(before, resent, "every cell the core thinks the viewer has is sent again");
        assertEquals(before, h.shown(A));
        int dropped = c.unloadChunk(0, 0);
        assertTrue(dropped > 0);
        for (long pos : new ArrayList<>(h.client.get(A).keySet())) assertTrue(!((RumbleCore.ux(pos) >> 4) == 0 && (RumbleCore.uz(pos) >> 4) == 0) || c.shownTo(A, pos) == RumbleCore.REAL);
        c.stopAll("world unloaded");
        assertTrue(c.empty());
    }

    @Test
    void theHardTimeoutRestoresAndEndsAnEventThatCannotFinish() {
        FakeHost h = new FakeHost();
        List<String> log = new ArrayList<>();
        RumbleCore c = core(h, 600, log);
        RumbleCore.Ev ev = c.start(sched(10, 2, 8, 4), 0.5, 0.5, grid(10), List.of(A), 0);
        run(c, 1, 30);
        assertTrue(h.shownAll() > 0);
        c.tick(100 + 200);   // the server was stalled: the clock is far past seconds + 3
        assertFalse(ev.running());
        assertEquals("timeout", ev.endReason());
        assertEquals(0, h.shownAll());
        assertTrue(c.empty());
    }

    @Test
    void twoRumblesShareAColumnTheNewerDrawsAndTheRealBlockReturnsOnlyAtTheEnd() {
        FakeHost h = new FakeHost();
        RumbleCore c = core(h, 600, new ArrayList<>());
        RumbleCore.Ev older = c.start(sched(6, 20, 8, 1), 0.5, 0.5, grid(6), List.of(A), 0);
        run(c, 1, 25);
        RumbleCore.Ev newer = c.start(sched(6, 20, 8, 2), 0.5, 0.5, grid(6), List.of(A), 25);
        assertEquals(2, c.claims().claimants(RumbleCore.column(0, 0)).size());
        assertEquals(newer.id, c.claims().winner(RumbleCore.column(0, 0)));
        long t = run(c, 26, 40);
        // the client shows what the newer event says for each column it has a cell on
        assertTrue(h.shownAll() > 0);
        c.stop(newer);
        assertEquals(older.id, c.claims().winner(RumbleCore.column(0, 0)), "the older claim stays");
        t = run(c, t, 30);
        assertTrue(h.shownAll() > 0, "the older event keeps drawing: the real block is not restored while a claim is left");
        c.stop(older);
        assertEquals(0, h.shownAll());
        assertTrue(c.empty());
        assertTrue(t > 0);
    }

    @Test
    void underAViewerNothingIsLoweredAndNothingIsRaisedIntoThem() {
        FakeHost h = new FakeHost();
        h.standing.add(A);
        RumbleCore c = core(h, 600, new ArrayList<>());
        c.start(sched(10, 20, 8, 4), 0.5, 0.5, grid(10), List.of(A), 0);
        for (long t = 1; t <= 200; t++) {
            c.tick(t);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 64; y <= 65; y++)
                assertEquals(RumbleCore.REAL, c.shownTo(A, RumbleCore.pack(x, y, z)), "the middle stays real while a viewer stands there");
        }
        assertTrue(h.shown(A) > 0, "the rest of the patch moves around them");
        c.stopAll("test");
        assertTrue(c.empty());
    }

    @Test
    void aBlockInTheAirCellIsNeverRaisedInto() {
        FakeHost h = new FakeHost();
        for (int x = -10; x <= 10; x++) for (int z = -10; z <= 10; z++) h.blockedCells.add(RumbleCore.pack(x, 65, z));   // something placed on every column
        RumbleCore c = core(h, 600, new ArrayList<>());
        c.start(sched(10, 4, 8, 4), 0.5, 0.5, grid(10), List.of(A), 0);
        for (long t = 1; t <= 80; t++) {
            c.tick(t);
            for (long pos : h.client.getOrDefault(A, Map.of()).keySet()) assertEquals(64, RumbleCore.uy(pos), "only lowered cells (in the surface block), never a raise");
        }
        c.stopAll("test");
        assertTrue(c.empty());
    }

    @Test
    void overTheBudgetCellsWaitAndNoEventIsStarved() {
        FakeHost h = new FakeHost();
        List<String> log = new ArrayList<>();
        RumbleCore c = core(h, 30, log);
        RumbleCore.Ev e1 = c.start(sched(12, 20, 8, 1), 0.5, 0.5, grid(12), List.of(A), 0);
        RumbleCore.Ev e2 = c.start(sched(2, 20, 8, 2), 40.5, 0.5, shift(grid(2), 40), List.of(B), 0);
        run(c, 1, 60);
        assertTrue(c.peakUpdates >= 30, "the budget is used");
        assertTrue(e1.updates > 0 && e2.updates > 0, "both events were sent cells: the small one was not starved (" + e1.updates + " / " + e2.updates + ")");
        assertTrue(log.stream().anyMatch(s -> s.contains("over the budget")));
        c.stopAll("test");
        assertEquals(0, h.shownAll(), "the restores are never thinned");
        assertTrue(c.empty());
    }

    static List<RumbleCore.Col> shift(List<RumbleCore.Col> l, int dx) {
        List<RumbleCore.Col> o = new ArrayList<>();
        for (RumbleCore.Col c : l) o.add(new RumbleCore.Col(c.x() + dx, c.z(), c.surfaceY()));
        return o;
    }

    @Test
    void cellsAreGroupedPerViewerAndTheStatsCountBytes() {
        FakeHost h = new FakeHost();
        RumbleCore c = core(h, 600, new ArrayList<>());
        c.start(sched(10, 2, 8, 4), 0.5, 0.5, grid(10), List.of(A, B, C), 0);
        run(c, 1, 45);
        assertTrue(c.totalBytes > 0 && c.totalUpdates > 0 && c.peakUpdates > 0);
        assertTrue(h.sends <= (c.totalUpdates + 1) * 3, "one send per viewer per tick (not per cell): " + h.sends + " sends for " + c.totalUpdates + " updates");
        assertEquals(0, h.shownAll());
    }

    /** Counts what the real sender would send: one packet per chunk section per viewer, sized as the vanilla codec writes it. */
    static final class CountingHost implements RumbleCore.Host {
        long bytes, cells, sends;
        @Override public int realState(int x, int y, int z) { return 1; }
        @Override public int fakeState(int level) { return 20000 + level; }   // block state ids of this mod are in the tens of thousands: 3 bytes as a varint, 5 as the varlong with the position
        @Override public boolean raiseBlocked(UUID v, int x, int y, int z, int level) { return false; }
        @Override public boolean lowerBlocked(UUID v, int x, int z, int sy) { return false; }
        @Override public boolean cellFree(int x, int y, int z) { return true; }
        @Override public int send(UUID v, List<long[]> changes) {
            Map<Long, List<Integer>> bySection = new HashMap<>();
            for (long[] c : changes) bySection.computeIfAbsent(((long) (RumbleCore.ux(c[0]) >> 4) << 32) ^ (RumbleCore.uz(c[0]) >> 4) ^ ((long) (RumbleCore.uy(c[0]) >> 4) << 56), k -> new ArrayList<>()).add((int) c[1]);
            int b = 0;
            for (List<Integer> l : bySection.values()) { b += RumbleCore.packetBytes(l.stream().mapToInt(Integer::intValue).toArray()); sends++; }
            bytes += b;
            cells += changes.size();
            return b;
        }
    }

    @Test
    void numbersForTheReport() throws Exception {
        StringBuilder out = new StringBuilder();
        out.append("radius viewers: peak cell updates in a tick, packets per tick (peak), bytes per tick (peak), cells per viewer per second, core milliseconds per tick (mean / max, server thread, without the packet objects)\n");
        for (int radius : new int[]{6, 12, 40}) for (int viewers : new int[]{1, 5, 20}) {
            double bestMean = 1e9, bestMax = 1e9;
            long peakBytes = 0, peakSends = 0;
            int peakUpdates = 0;
            double cellsPerViewerSecond = 0;
            for (int round = 0; round < 3; round++) {   // the third round is the warm one
                CountingHost h = new CountingHost();
                RumbleCore c = new RumbleCore(h, 600, 3.0, s -> {});
                List<UUID> vs = new ArrayList<>();
                for (int i = 0; i < viewers; i++) vs.add(new UUID(1, i));
                c.start(sched(radius, 4, 8, 99), 0.5, 0.5, grid(radius), vs, 0);
                double sum = 0, max = 0;
                long prevBytes = 0, prevSends = 0, pb = 0, ps = 0;
                for (long t = 1; t <= 80; t++) {
                    long t0 = System.nanoTime();
                    c.tick(t);
                    double ms = (System.nanoTime() - t0) / 1e6;
                    sum += ms; max = Math.max(max, ms);
                    pb = Math.max(pb, h.bytes - prevBytes); ps = Math.max(ps, h.sends - prevSends);
                    prevBytes = h.bytes; prevSends = h.sends;
                }
                if (sum / 80 < bestMean) { bestMean = sum / 80; bestMax = max; peakBytes = pb; peakSends = ps; peakUpdates = c.peakUpdates; cellsPerViewerSecond = h.cells / (double) viewers / 4.0; }
            }
            out.append(String.format(java.util.Locale.ROOT, "r%-2d x%-2d viewers: %4d updates, %3d packets, %6d bytes, %6.0f cells/viewer/s, %.3f / %.3f ms%n", radius, viewers, peakUpdates, peakSends, peakBytes, cellsPerViewerSecond, bestMean, bestMax));
        }
        for (int radius : new int[]{6, 12, 40}) {   // what the rumble wants per tick with no budget at all (the budget of 600 thins the radius 40 one)
            CountingHost h = new CountingHost();
            RumbleCore c = new RumbleCore(h, 1000000, 3.0, s -> {});
            c.start(sched(radius, 4, 8, 99), 0.5, 0.5, grid(radius), List.of(new UUID(1, 1)), 0);
            for (long t = 1; t <= 80; t++) c.tick(t);
            out.append(String.format(java.util.Locale.ROOT, "r%d without any budget: %d columns, peak %d cell updates in a tick, %.0f on average%n", radius, grid(radius).size(), c.peakUpdates, c.totalUpdates / 80.0));
        }
        java.nio.file.Files.writeString(java.nio.file.Path.of("build", "prompt15-rumble-numbers.txt"), out.toString());
        assertTrue(out.length() > 100);
    }
}
