package com.paulorchard.islandcraft.wormsofarrakis.test.client;

import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ShingleMath;
import com.paulorchard.islandcraft.wormsofarrakis.test.TestFlags;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Client-side timing of the shingle skin; prints one line to the log (and chat) every 5 s while stats are on. */
final class ShingleStats {
    private static final long INTERVAL_NS = 5_000_000_000L;
    private static long windowStart = System.nanoTime();
    private static int frames, rebuilt, reused;
    private static long buildSum, buildWorst, submitSum, submitWorst;
    private static int segments, faces;
    private static final int[] quads = new int[5];

    private ShingleStats() {}

    static synchronized void frame(boolean built, long buildNs, ShingleMath.Result res, int segs, int n) {
        if (!TestFlags.stats) return;
        frames++;
        if (built) {
            rebuilt++;
            buildSum += buildNs;
            buildWorst = Math.max(buildWorst, buildNs);
        } else {
            reused++;
        }
        segments = segs;
        faces = n;
        System.arraycopy(res.byKind, 0, quads, 0, 5);
        flush();
    }

    static synchronized void submitted(long ns) {
        if (!TestFlags.stats) return;
        submitSum += ns;
        submitWorst = Math.max(submitWorst, ns);
    }

    private static void flush() {
        long now = System.nanoTime();
        if (now - windowStart < INTERVAL_NS) return;
        String line = String.format(Locale.ROOT,
                "stats: segments=%d faces/ring=%d quads(liner/plates/lips/rims)=%d/%d/%d/%d total=%d | frames=%d rebuilt=%d reused=%d | build avg %.2f ms worst %.2f ms (rebuilt frames) | submit avg %.2f ms worst %.2f ms",
                segments, faces, quads[0], quads[1], quads[2], quads[3] + quads[4], quads[0] + quads[1] + quads[2] + quads[3] + quads[4],
                frames, rebuilt, reused, rebuilt == 0 ? 0 : buildSum / rebuilt / 1e6, buildWorst / 1e6,
                frames == 0 ? 0 : submitSum / (double) frames / 1e6, submitWorst / 1e6);
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] {}", line);
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[Worms] " + line));
        } catch (RuntimeException ignored) {
            // chat is a convenience only
        }
        windowStart = now;
        frames = rebuilt = reused = 0;
        buildSum = buildWorst = submitSum = submitWorst = 0;
    }
}
