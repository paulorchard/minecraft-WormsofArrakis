package com.paulorchard.islandcraft.wormsofarrakis.test.client;

import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
import com.paulorchard.islandcraft.wormsofarrakis.fx.CrashFx;
import com.paulorchard.islandcraft.wormsofarrakis.fx.FxOptions;
import com.paulorchard.islandcraft.wormsofarrakis.fx.FxPlayer;
import com.paulorchard.islandcraft.wormsofarrakis.fx.FxSink;
import com.paulorchard.islandcraft.wormsofarrakis.fx.ParticleModel;
import com.paulorchard.islandcraft.wormsofarrakis.test.FxFlags;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveFactory;
import com.paulorchard.islandcraft.wormsofarrakis.test.TestWormEntity;
import com.paulorchard.islandcraft.wormsofarrakis.test.WormSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Prompt 09: the client side of the crash effects. The worm is scripted and drawn on the client (the renderer runs its clock from the entity's
 * tickCount), so the sheet is played here, once per client tick (20 Hz), from the same clock: tau = {@link MoveFactory#tau}(spec, tickCount). Sounds
 * go through ClientLevel.playLocalSound, particles through the ParticleEngine (so each particle can be given its velocity, lifetime, size and tint).
 * Everything the sheet decides is in the pure {@link FxPlayer}; this class is only a {@link FxSink}. A server-side sink can replace it (report, answer 2).
 *
 * <p>A worm's player is dropped (pending cues and emission cancelled at once) when the worm is removed (stop, clear, loop=off ending) or when its move
 * changes; a worm met in the middle of its run does not replay what has passed.
 */
@EventBusSubscriber(modid = WormsOfArrakis.MOD_ID, value = Dist.CLIENT)
public final class FxClient {
    private FxClient() {}

    /** The sound source of every cue: the Hostile Creatures slider of the options (the worm is a hostile creature, like the ravager, the warden and the dragon whose sounds it uses). */
    public static final SoundSource SOURCE = SoundSource.HOSTILE;

    private static final class State {
        String key;
        FxPlayer player;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static final Map<String, SoundEvent> EVENTS = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();

    /** The part of a spec that identifies the move for the effects: time, freeze, speed, held pose and the mouth's look do not. */
    private static String keyOf(WormSpec s) {
        double[] p = s.p().clone();
        for (int i : new int[]{8, 11, 12, 13, 17, 23, 24, 25}) if (i < p.length) p[i] = 0;
        return new WormSpec(s.kind(), s.mode(), p).serialize();
    }

    private static int liveParticles(Minecraft mc) {
        try {
            return Integer.parseInt(mc.particleEngine.countParticles());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            dropAll("level closed");
            return;
        }
        Set<Integer> seen = new HashSet<>();
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof TestWormEntity w) || e.isRemoved()) continue;
            String sp = w.getSpecString();
            if (!sp.startsWith("move;")) continue;
            WormSpec spec;
            FxOptions opt;
            try {
                spec = WormSpec.parse(sp);
                opt = MoveFactory.fxOptions(spec);
            } catch (RuntimeException ex) {
                continue;
            }
            if (!opt.on()) continue;
            int id = w.getId();
            seen.add(id);
            String key = keyOf(spec);
            State st = STATES.get(id);
            try {
                if (st == null || !key.equals(st.key)) {
                    if (st != null) st.player.cancel();
                    st = new State();
                    st.key = key;
                    st.player = new FxPlayer(MoveFactory.get(spec), opt, MoveFactory.closeTime(spec), MoveFactory.closeEnd(spec), id);
                    STATES.put(id, st);
                    WormsOfArrakis.LOGGER.info("[WormsOfArrakis] fx player started for worm {}: {} anchors: {} plays={} count scale={} ({} rows of particles, {} cues)", id, spec.mode(),
                            st.player.anchors().line(), st.player.schedule().size(), String.format(Locale.ROOT, "%.2f", st.player.scale()), CrashFx.EMITTERS.size(), CrashFx.CUES.size());
                }
                st.player.setRuntime(FxFlags.sound, FxFlags.particles, FxFlags.solo);
                st.player.step(MoveFactory.tau(spec, w.tickCount), liveParticles(mc), new McSink(mc, level, w));
            } catch (RuntimeException ex) {
                WormsOfArrakis.LOGGER.warn("[WormsOfArrakis] fx error for worm {}: {}", id, ex.toString());
                if (st != null) st.player.cancel();
            }
        }
        for (Iterator<Map.Entry<Integer, State>> it = STATES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, State> en = it.next();
            if (seen.contains(en.getKey())) continue;
            FxPlayer p = en.getValue().player;
            p.cancel();
            WormsOfArrakis.LOGGER.info("[WormsOfArrakis] fx cancelled for worm {} (removed or fx off): {} sounds played, {} particles made, peak alive {}, thinned {} in {} capped ticks",
                    en.getKey(), p.soundsPlayed(), p.spawned(), p.peakAlive(), p.thinned(), p.cappedTicks());
            it.remove();
        }
    }

    private static void dropAll(String why) {
        if (STATES.isEmpty()) return;
        for (State s : STATES.values()) s.player.cancel();
        STATES.clear();
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] fx cancelled for every worm ({})", why);
    }

    // ---- the sink: the model frame of the worm to the world, then the vanilla calls ----

    private static final class McSink implements FxSink {
        private final Minecraft mc;
        private final ClientLevel level;
        private final double ox, oy, oz, cos, sin;

        McSink(Minecraft mc, ClientLevel level, TestWormEntity w) {
            this.mc = mc;
            this.level = level;
            this.ox = w.getX();
            this.oy = w.getY();
            this.oz = w.getZ();
            double yaw = Math.toRadians(w.getYRot());
            this.cos = Math.cos(yaw);
            this.sin = Math.sin(yaw);
        }

        // the renderer turns the model by YP(-yaw): world = origin + (x cos - z sin, y, x sin + z cos)
        private double wx(double x, double z) { return ox + x * cos - z * sin; }
        private double wz(double x, double z) { return oz + x * sin + z * cos; }

        @Override
        public void log(String line) { WormsOfArrakis.LOGGER.info(line); }

        @Override
        public void sound(CrashFx.Cue cue, double x, double y, double z, double volume, double pitch) {
            SoundEvent ev = EVENTS.computeIfAbsent(cue.event(), k -> BuiltInRegistries.SOUND_EVENT.getValue(Identifier.withDefaultNamespace(k)));
            if (ev == null) {
                if (MISSING.add(cue.event())) WormsOfArrakis.LOGGER.warn("[WormsOfArrakis] fx: sound event {} is not in the registry, cue {} skipped", cue.event(), cue.name());
                return;
            }
            // distanceDelay false: the sheet's times are the times the player hears (vanilla would delay by distance / 40 s)
            level.playLocalSound(wx(x, z), oy + y, wz(x, z), ev, SOURCE, (float) volume, (float) pitch, false);
        }

        @Override
        public void particle(Spawn s) {
            ParticleStatus status = mc.options.particles().get();
            if (!s.type().overrideLimiter) {   // the Particles option of the player acts as it does for vanilla (Minimal: none, Decreased: a third fewer)
                if (status == ParticleStatus.MINIMAL) return;
                if (status == ParticleStatus.DECREASED && java.util.concurrent.ThreadLocalRandom.current().nextInt(3) == 0) return;
            }
            ParticleOptions po = switch (s.type()) {
                case BLOCK -> new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
                case DUST_PLUME -> ParticleTypes.DUST_PLUME;
                case CLOUD -> ParticleTypes.CLOUD;
                case EXPLOSION -> ParticleTypes.EXPLOSION;
                case POOF -> ParticleTypes.POOF;
                case LARGE_SMOKE -> ParticleTypes.LARGE_SMOKE;
            };
            double px = wx(s.x(), s.z()), py = oy + s.y(), pz = wz(s.x(), s.z());
            double aux = s.type() == CrashFx.PType.EXPLOSION ? ParticleModel.explosionParam(s.size()) : 0;
            // the engine directly (not ClientLevel.addParticle): that one drops particles beyond 32 blocks, and the owner watches from 30 to 90
            Particle p = mc.particleEngine.createParticle(po, px, py, pz, aux, 0, 0);
            if (p == null) return;
            if (s.type() != CrashFx.PType.EXPLOSION) {
                double k = ParticleModel.driftFactor(s.type(), s.life());
                double vx = s.vx() * k, vy = s.vy() * k, vz = s.vz() * k;
                p.setParticleSpeed(vx * cos - vz * sin, vy, vx * sin + vz * cos);
                double sc = ParticleModel.scaleFor(s.type(), s.size());
                if (Math.abs(sc - 1) > 0.01) p.scale((float) sc);
            }
            p.setLifetime(s.life());
            if (s.tint() >= 0 && p instanceof SingleQuadParticle q) q.setColor(((s.tint() >> 16) & 255) / 255f, ((s.tint() >> 8) & 255) / 255f, (s.tint() & 255) / 255f);
        }
    }
}
