package com.paulorchard.islandcraft.wormsofarrakis.test.client;

import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
import com.paulorchard.islandcraft.wormsofarrakis.fx.FxAnchors;
import com.paulorchard.islandcraft.wormsofarrakis.fx.FxPlayer;
import com.paulorchard.islandcraft.wormsofarrakis.light.LightMath;
import com.paulorchard.islandcraft.wormsofarrakis.light.LightProbe;
import com.paulorchard.islandcraft.wormsofarrakis.light.LightWatch;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;
import com.paulorchard.islandcraft.wormsofarrakis.test.LightFlags;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveFactory;
import com.paulorchard.islandcraft.wormsofarrakis.test.TestWormEntity;
import com.paulorchard.islandcraft.wormsofarrakis.test.WormSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Prompt 10 part 3: the light of the worm on the client. Every 4 ticks, for each moving worm: the light vanilla gives it (the same call as
 * EntityRenderer.getPackedLightCoords: the block containing entity.getLightProbePosition, which is the eye position, and level.getBrightness for the
 * BLOCK and SKY layers), the light at the sample points along the exposed body ({@link LightProbe}), the verdict ({@link LightWatch}) and the log lines.
 * The renderer asks {@link #lightFor} for the packed light it draws with: light=probe (default) the smoothed maximum of the samples, light=vanilla the
 * entity's own, light=full 15/15, or the value of {@code light force}.
 */
@EventBusSubscriber(modid = WormsOfArrakis.MOD_ID, value = Dist.CLIENT)
public final class LightClient {
    private LightClient() {}

    private static final class W {
        final LightMath.Smoother smoother = new LightMath.Smoother();
        final LightWatch.Tracker tracker = new LightWatch.Tracker();
        String key, label = "?";
        double holeZ;
        LightWatch.Sample last;
        int vanilla;   // packed light of the entity's own probe at the last sample
    }

    private static final Map<Integer, W> WORMS = new HashMap<>();
    private static long tick;

    static {
        LightFlags.reporter = LightClient::report;
        LightFlags.dumper = LightClient::dump;
    }

    /** The packed light the renderer draws a move with (vanilla = state.lightCoords of the entity render state). */
    public static int lightFor(int entityId, WormSpec spec, int vanilla) {
        int forced = LightFlags.forced;
        if (forced >= 0) return forced;
        if (!spec.kind().equals("move")) return vanilla;
        W st = WORMS.get(entityId);
        return LightMath.choose(MoveFactory.lightMode(spec), vanilla, st == null ? null : st.smoother);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            WORMS.clear();
            return;
        }
        tick++;
        Set<Integer> seen = new HashSet<>();
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof TestWormEntity w) || e.isRemoved()) continue;
            String sp = w.getSpecString();
            if (!sp.startsWith("move;")) continue;
            int id = w.getId();
            seen.add(id);
            try {
                WormSpec spec = WormSpec.parse(sp);
                W st = WORMS.computeIfAbsent(id, k -> new W());
                if (st.last == null || (tick + id) % LightMath.SAMPLE_EVERY == 0) sample(level, w, spec, st);
                st.smoother.tick();
            } catch (RuntimeException ex) {
                if (tick % 200 == 0) WormsOfArrakis.LOGGER.warn("[WormsOfArrakis] light: sample failed for worm {}: {}", id, ex.toString());
            }
        }
        WORMS.keySet().retainAll(seen);
        if (LightFlags.forced >= 0 && tick % 20 == 0 && !seen.isEmpty())
            WormsOfArrakis.LOGGER.info("[WormsOfArrakis] light: FORCED to {} (block {} sky {}) for every worm (light force auto returns to the mode of each move)", LightFlags.forcedLabel,
                    LightMath.block(LightFlags.forced), LightMath.sky(LightFlags.forced));
    }

    private static LightProbe.LightGrid grid(ClientLevel level) {
        return new LightProbe.LightGrid() {
            private final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            @Override public int block(int x, int y, int z) { return level.getBrightness(LightLayer.BLOCK, p.set(x, y, z)); }
            @Override public int sky(int x, int y, int z) { return level.getBrightness(LightLayer.SKY, p.set(x, y, z)); }
            @Override public boolean solid(int x, int y, int z) {
                BlockState s = level.getBlockState(p.set(x, y, z));
                return s.isSolidRender() || s.isCollisionShapeFullBlock(level, p);
            }
        };
    }

    private static void sample(ClientLevel level, TestWormEntity w, WormSpec spec, W st) {
        Moves.Move m = MoveFactory.get(spec);
        String key = spec.serialize();
        if (!key.equals(st.key)) {
            st.key = key;
            st.label = m.label() + " W" + (m.width == Math.rint(m.width) ? Long.toString((long) m.width) : Double.toString(m.width));
            FxAnchors.Anchors an = FxAnchors.of(m, MoveFactory.closeTime(spec), MoveFactory.closeEnd(spec));
            st.holeZ = an.holeZ();
        }
        double t = MoveFactory.tau(spec, w.tickCount) % m.cycle;
        if (t < 0) t += m.cycle;
        // the probe vanilla uses: the block containing the eye position (EntityRenderer.getPackedLightCoords)
        BlockPos pp = BlockPos.containing(w.getLightProbePosition(1.0F));
        int vb = w.isOnFire() ? 15 : level.getBrightness(LightLayer.BLOCK, pp), vs = level.getBrightness(LightLayer.SKY, pp);
        BlockState ps = level.getBlockState(pp);
        boolean solid = ps.isSolidRender() || ps.isCollisionShapeFullBlock(level, pp);
        LightProbe.Frame f = new LightProbe.Frame(w.getX(), w.getY(), w.getZ(), Math.toRadians(w.getYRot()));
        LightProbe.Result r = LightProbe.sample(grid(level), f, w.getY(), LightProbe.points(m, t, st.holeZ));
        st.smoother.target(r.packed());
        st.vanilla = LightMath.pack(vb, vs);
        LightMath.Mode mode = MoveFactory.lightMode(spec);
        int used = LightFlags.forced >= 0 ? LightFlags.forced : LightMath.choose(mode, st.vanilla, st.smoother);
        List<LightWatch.Ref> refs = new ArrayList<>();
        for (LightProbe.Reading rd : r.readings()) if (rd.p().ref()) refs.add(new LightWatch.Ref(rd.p().name(), rd.block(), rd.sky()));
        int exposed = FxPlayer.FxGeometry.bodyRings(FxPlayer.FxGeometry.at(m, t)).size();
        String mname = LightFlags.forced >= 0 ? "forced:" + LightFlags.forcedLabel : mode.label();
        LightWatch.Sample s = new LightWatch.Sample(tick, w.getId(), st.label, mname, vb, vs, LightMath.block(used), LightMath.sky(used), pp.getX(), pp.getY(), pp.getZ(),
                BuiltInRegistries.BLOCK.getKey(ps.getBlock()).toString(), solid, level.dimension().identifier().toString(), level.getDefaultClockTime() % 24000, level.getSkyDarken(),
                w.getX(), w.getY() + w.getEyeHeight(), w.getZ(), w.getY(), exposed, refs);
        st.last = s;
        for (LightWatch.Line l : st.tracker.accept(s, LightFlags.log)) {
            if (l.warn()) WormsOfArrakis.LOGGER.warn(l.text());
            else WormsOfArrakis.LOGGER.info(l.text());
        }
    }

    /** The chat lines of the light command: the current sample of every worm with its verdict and the probe result. */
    private static List<String> report() {
        List<String> l = new ArrayList<>();
        for (Map.Entry<Integer, W> e : WORMS.entrySet()) {
            LightWatch.Sample s = e.getValue().last;
            if (s == null) continue;
            int sm = e.getValue().smoother.packed();
            l.add("light: " + LightWatch.describe(s));
            l.add(String.format(Locale.ROOT, "light: worm %d verdict=%s (vanilla b%d/s%d, probe smoothed b%d/s%d, mode %s); log %s%s", s.worm(), LightWatch.classify(s).label, s.vanillaBlock(), s.vanillaSky(),
                    LightMath.block(sm), LightMath.sky(sm), s.mode(), LightFlags.log.label(), LightFlags.forced >= 0 ? ", FORCED " + LightFlags.forcedLabel : ""));
        }
        return l;
    }

    /** Writes the ring buffers (the last 200 samples of every worm) to the log; returns the number of lines. */
    private static int dump() {
        int n = 0;
        for (Map.Entry<Integer, W> e : WORMS.entrySet()) {
            List<LightWatch.Sample> ss = e.getValue().tracker.samples();
            WormsOfArrakis.LOGGER.info("[WormsOfArrakis] light dump: worm {} has {} samples (warned {}, suppressed {})", e.getKey(), ss.size(), e.getValue().tracker.warned(), e.getValue().tracker.suppressedTotal());
            for (LightWatch.Sample s : ss) {
                WormsOfArrakis.LOGGER.info("[WormsOfArrakis] light dump: tick {} {} verdict={}", s.tick(), LightWatch.describe(s), LightWatch.classify(s).label);
                n++;
            }
        }
        return n;
    }
}
