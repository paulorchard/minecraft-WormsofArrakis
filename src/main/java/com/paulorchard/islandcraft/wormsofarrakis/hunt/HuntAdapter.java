package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
import com.paulorchard.islandcraft.wormsofarrakis.fx.SizeFactor;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.AggroEngine.PlayerState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Prompt 13: the thin adapter between the engine-free brain ({@link HuntManager}) and the game. It reads the players and the blocks, runs the events once per server tick,
 * and makes the start cue, the tremors (fake blocks sent as block update packets, sound, particles) and the build-up. Nothing in the world is ever edited.
 */
public final class HuntAdapter implements HuntManager.Env, HuntManager.Listener {
    private static HuntAdapter instance;

    public static HuntAdapter instance() { return instance; }

    /** Called once from the mod constructor: the brain exists from the start so other mods can register sources and effect multipliers at load time. */
    public static void init() {
        if (instance != null) return;
        instance = new HuntAdapter();
        NeoForge.EVENT_BUS.register(instance);
    }

    private static final double DT = 0.05;

    private final HuntConfig cfg = new HuntConfig();
    private final HuntManager mgr;
    private MinecraftServer server;
    private final Map<UUID, ServerPlayer> online = new HashMap<>();
    private final Map<UUID, Integer> surface = new HashMap<>();
    private final Map<UUID, ServerLevel> levelOf = new HashMap<>();
    private Set<Block> sandSet = new HashSet<>();
    private List<String> sandKey = List.of();
    private int tickCounter;
    private final List<Ripple> ripples = new ArrayList<>();
    private final List<ParticleReq> particleReqs = new ArrayList<>();
    private final Map<Long, Integer> tremorCount = new HashMap<>();
    private long lastParticlesMade, lastBlocksSent;

    private HuntAdapter() {
        this.mgr = new HuntManager(cfg, this, (e, v, w, s) -> WormsOfArrakis.LOGGER.info("[WormsOfArrakis] {}", StrikeHook.attackLine(e, w, s)));
        mgr.aggro().setEffectProbe(this::hasEffect);
    }

    public HuntManager manager() { return mgr; }
    public HuntConfig config() { return cfg; }
    public ServerPlayer player(UUID id) { return online.get(id); }
    public long lastParticlesMade() { return lastParticlesMade; }
    public long lastBlocksSent() { return lastBlocksSent; }
    public int rippleCount() { return ripples.size(); }

    // ---- reading the game ----

    private boolean hasEffect(UUID id, String effectId) {
        ServerPlayer p = online.get(id);
        Identifier rl = Identifier.tryParse(effectId);
        if (p == null || rl == null) return false;
        Optional<Holder.Reference<MobEffect>> h = BuiltInRegistries.MOB_EFFECT.get(rl);
        return h.isPresent() && p.hasEffect(h.get());
    }

    private void refreshConfig() {
        HuntConfigSpec.refresh(cfg);
        if (!cfg.sandBlocks.equals(sandKey)) {
            Set<Block> s = new HashSet<>();
            for (String id : cfg.sandBlocks) {
                Identifier rl = Identifier.tryParse(id);
                if (rl == null) continue;
                Optional<Holder.Reference<Block>> b = BuiltInRegistries.BLOCK.get(rl);
                if (b.isPresent()) s.add(b.get().value());
            }
            sandSet = s;
            sandKey = new ArrayList<>(cfg.sandBlocks);
            WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt: sand blocks in use: {} (ids that do not exist are skipped)", cfg.sandBlocks.stream().filter(id -> {
                Identifier rl = Identifier.tryParse(id);
                return rl != null && BuiltInRegistries.BLOCK.get(rl).isPresent();
            }).toList());
        }
    }

    private SandProbe.Cls classOf(ServerLevel level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.hasChunkAt(pos)) return SandProbe.Cls.OTHER;
        BlockState st = level.getBlockState(pos);
        if (sandSet.contains(st.getBlock())) return SandProbe.Cls.SAND;
        return st.isAir() ? SandProbe.Cls.AIR : SandProbe.Cls.OTHER;
    }

    private PlayerState stateOf(ServerPlayer p) {
        ServerLevel level = p.level();
        int bx = (int) Math.floor(p.getX()), bz = (int) Math.floor(p.getZ());
        int sy = SandProbe.surface(p.onGround(), p.getY(), cfg.sandTolerance, y -> classOf(level, bx, y, bz));
        surface.put(p.getUUID(), sy);
        levelOf.put(p.getUUID(), level);
        boolean eligible = p.isAlive() && !p.isCreative() && !p.isSpectator() && !mgr.ignored(p.getUUID());
        return new PlayerState(p.getUUID(), p.getGameProfile().name(), level.dimension().toString(), p.getX(), p.getY(), p.getZ(), p.onGround(), p.isSprinting(), p.isShiftKeyDown(),
                p.getAbilities().flying, p.isSwimming() || p.isInWater(), p.isPassenger(), p.isFallFlying(), eligible, sy != SandProbe.NONE);
    }

    @Override
    public List<PlayerState> players() {
        List<PlayerState> l = new ArrayList<>();
        online.clear();
        surface.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            online.put(p.getUUID(), p);
            l.add(stateOf(p));
        }
        return l;
    }

    @Override
    public int depth(UUID id) {
        ServerPlayer p = online.get(id);
        Integer sy = surface.get(id);
        if (p == null || sy == null || sy == SandProbe.NONE) return 0;
        ServerLevel level = levelOf.get(id);
        int bx = (int) Math.floor(p.getX()), bz = (int) Math.floor(p.getZ());
        return SandProbe.depth(sy, y -> classOf(level, bx, y, bz));
    }

    /** The depth D under a player right now (for the commands); 0 when not on sand. */
    public int depthNow(ServerPlayer p) {
        PlayerState s = stateOf(p);
        online.put(p.getUUID(), p);
        return s.onSand() ? depth(p.getUUID()) : 0;
    }

    public PlayerState stateNow(ServerPlayer p) { online.put(p.getUUID(), p); return stateOf(p); }

    // ---- events of the game ----

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent e) {
        server = e.getServer();
        refreshConfig();
        logConfig();
    }

    private boolean configLogged;

    private void logConfig() {
        if (configLogged) return;
        configLogged = true;
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt: config file islandcraft_wormsofarrakis-common.toml, keys:");
        for (String line : cfg.describe()) WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt config: {}", line);
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt: aggro sources {}; effect multipliers {} (default map empty: the Holtzman shield of Equipment of Arrakis registers its effect with factor 10 later)",
                mgr.aggro().sourceIds(), mgr.aggro().effectMultipliers());
    }

    @SubscribeEvent
    public void onTick(ServerTickEvent.Post e) {
        server = e.getServer();
        if (tickCounter++ % 40 == 0) refreshConfig();
        particleReqs.clear();
        lastBlocksSent = 0;
        mgr.tick(DT, this);
        stepRipples();
        flushParticles();
    }

    @SubscribeEvent
    public void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        boolean in = mgr.inEvent(id);
        mgr.logoff(id);
        online.remove(id);
        if (in) WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt: {} logged off inside an event: removed from the list, aggro kept at 99 until they log in again", e.getEntity().getGameProfile().name());
    }

    @SubscribeEvent
    public void onLoggedIn(PlayerEvent.PlayerLoggedInEvent e) { mgr.login(e.getEntity().getUUID()); }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) { if (e.getEntity() instanceof ServerPlayer sp) mgr.died(sp.getUUID()); }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent e) {
        int n = mgr.stop(null);
        cancelAllRipples();
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt: server stopping: {} event(s) stopped, every fake block restored", n);
    }

    /** Ends events and restores every fake block (the command and the server stop). */
    public int stopAll(UUID player) { return mgr.stop(player); }

    // ---- the listener ----

    @Override
    public void onStart(HuntEvent e, PlayerState t) {
        ServerPlayer p = online.get(t.id());
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt: event {} started on {}: {} worm W{} ({}), stalk {} s, list {} ({})", e.id, t.name(), e.size.label, trim(e.width), "size factor " + fmt(e.factor), trim(e.stalkSeconds),
                e.members().size(), names(e.members()));
        if (p == null) return;
        ServerLevel level = p.level();
        double f = e.factor;
        SoundEvent boom = sound("entity.generic.explode");
        if (boom != null) level.playSound(null, p.getX(), p.getY(), p.getZ(), boom, SoundSource.HOSTILE, (float) Math.max(1.0, 3.0 * f), 0.5f);
        BlockState sand = sandStateUnder(level, p);
        int count = (int) Math.max(4, Math.round(60 * f));
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, sand), p.getX(), p.getY() + 0.3, p.getZ(), count, 0.5 + 1.5 * f, 0.2, 0.5 + 1.5 * f, 0.05);
    }

    @Override
    public void onShallow(PlayerState p, int depth, double newScore) {
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt: {} reached the threshold on shallow sand (D={}, an event needs {} or more): aggro lowered by {} to {}", p.name(), depth,
                cfg.depthShallowMax + 1, trim(cfg.shallowPenalty), trim(newScore));
    }

    @Override
    public void onPhaseChange(HuntEvent e, HuntEvent.Phase from, HuntEvent.Phase to) {
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt: event {} phase {} -> {}", e.id, from, to);
    }

    @Override
    public void onEnd(HuntEvent e, HuntEvent.EndReason reason) {
        int restored = cancelRipples(e.id);
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt: event {} ended {} (list scores set to 0; {} fake blocks restored)", e.id, reason, restored);
    }

    @Override
    public void onTremor(HuntEvent e, UUID player, double strength, double progress) {
        ServerPlayer p = online.get(player);
        if (p == null) return;
        playTremor(p, e.id, e.factor, strength);
    }

    @Override
    public void onBuildUp(HuntEvent e, UUID player, int count, double rate, double progress) {
        ServerPlayer p = online.get(player);
        if (p == null) return;
        BlockState sand = sandStateUnder(p.level(), p);
        particleReqs.add(new ParticleReq(p.level(), sand, p.getX(), p.getY() + 0.1, p.getZ(), count, 0.7, 0.1, 0.7, 0.12));
    }

    // ---- tremors ----

    /** One tremor under a player: ripple of fake blocks, sound, particles, and the camera hook. Also the preview command. */
    public Tremor.Plan playTremor(ServerPlayer p, long eventId, double factor, double strength) {
        Tremor.Plan plan = Tremor.plan(strength, factor, cfg);
        ServerLevel level = p.level();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        SoundEvent low = sound("entity.warden.step"), crack = sound("block.sand.break");
        if (low != null) level.playSound(null, x, y, z, low, SoundSource.HOSTILE, (float) plan.volume(), 0.5f);
        if (crack != null) level.playSound(null, x, y, z, crack, SoundSource.BLOCKS, (float) Math.max(0.5, plan.volume() * 0.7), 0.6f);
        BlockState sand = sandStateUnder(level, p);
        particleReqs.add(new ParticleReq(level, sand, x, y + 0.2, z, plan.particles(), plan.radius() * 0.4, 0.3, plan.radius() * 0.4, 0.25));
        ripples.add(new Ripple(eventId, level, p, plan));
        TremorFx.onTremor(p, strength);
        return plan;
    }

    private BlockState sandStateUnder(ServerLevel level, ServerPlayer p) {
        Integer sy = surface.get(p.getUUID());
        if (sy != null && sy != SandProbe.NONE) {
            BlockPos pos = new BlockPos((int) Math.floor(p.getX()), sy, (int) Math.floor(p.getZ()));
            if (level.hasChunkAt(pos)) {
                BlockState st = level.getBlockState(pos);
                if (sandSet.contains(st.getBlock())) return st;
            }
        }
        return net.minecraft.world.level.block.Blocks.SAND.defaultBlockState();
    }

    private SoundEvent sound(String id) { return BuiltInRegistries.SOUND_EVENT.getValue(Identifier.withDefaultNamespace(id)); }

    private record ParticleReq(ServerLevel level, BlockState sand, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {}

    private void flushParticles() {
        int total = 0;
        for (ParticleReq r : particleReqs) total += r.count();
        double ratio = Tremor.ratio(total, cfg.tremorParticleBudgetPerTick);
        long made = 0;
        for (ParticleReq r : particleReqs) {
            int n = Tremor.thin(r.count(), ratio);
            r.level().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, r.sand()), r.x(), r.y(), r.z(), n, r.dx(), r.dy(), r.dz(), r.speed());
            made += n;
        }
        lastParticlesMade = made;
        if (ratio < 1 && !budgetLogged) {
            budgetLogged = true;
            WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt: tremor particles over the budget ({} wanted, {} allowed per tick): every player thinned by one ratio {} (none below 1)", total, cfg.tremorParticleBudgetPerTick, fmt(ratio));
        }
    }

    private boolean budgetLogged, blockBudgetLogged;

    /** One ripple of fake blocks: the columns round the player and what is shown on each. Never a real edit; every block shown is restored. */
    private final class Ripple {
        final long eventId;
        final ServerLevel level;
        final Tremor.Plan plan;
        final double cx, cz;
        final int ticks;
        int tick;
        final List<int[]> cols = new ArrayList<>();   // {x, z, surfaceY}
        final List<Double> dist = new ArrayList<>();
        final int[] shown;
        final Tremor.FakeBlocks fake = new Tremor.FakeBlocks();
        final Map<Long, BlockState> states = new HashMap<>();
        final Set<UUID> sentTo = new HashSet<>();

        Ripple(long eventId, ServerLevel level, ServerPlayer p, Tremor.Plan plan) {
            this.eventId = eventId;
            this.level = level;
            this.plan = plan;
            this.cx = p.getX();
            this.cz = p.getZ();
            this.ticks = cfg.tremorRippleTicks;
            int r = (int) Math.ceil(plan.radius() + Tremor.Ripple.BAND);
            int px = (int) Math.floor(cx), pz = (int) Math.floor(cz), py = (int) Math.floor(p.getY());
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d = Math.hypot(px + dx + 0.5 - cx, pz + dz + 0.5 - cz);
                    if (d < Tremor.Ripple.INNER || d > plan.radius() + Tremor.Ripple.BAND) continue;
                    int x = px + dx, z = pz + dz;
                    if (!level.hasChunkAt(new BlockPos(x, py, z))) continue;
                    for (int y = py + 2; y >= py - 4; y--) {
                        if (classOf(level, x, y, z) == SandProbe.Cls.SAND && classOf(level, x, y + 1, z) == SandProbe.Cls.AIR) {
                            cols.add(new int[]{x, z, y});
                            dist.add(d);
                            break;
                        }
                    }
                }
            }
            shown = new int[cols.size()];
        }

        /** The raise operations wanted this tick (pos -> state), and the restores (always applied). Returns the raises; restores are done at once. */
        List<long[]> wanted() {
            List<long[]> raises = new ArrayList<>();
            for (int i = 0; i < cols.size(); i++) {
                int[] c = cols.get(i);
                int want = Tremor.Ripple.layers(dist.get(i), tick, ticks, plan.radius(), plan.layers());
                if (want > shown[i]) for (int k = shown[i] + 1; k <= want; k++) raises.add(new long[]{i, k});
                else if (want < shown[i]) {
                    for (int k = shown[i]; k > want; k--) restoreAt(new BlockPos(c[0], c[2] + k, c[1]));
                    shown[i] = want;
                }
            }
            return raises;
        }

        void raise(long[] op) {
            int i = (int) op[0], k = (int) op[1];
            int[] c = cols.get(i);
            BlockPos pos = new BlockPos(c[0], c[2] + k, c[1]);
            BlockState st = level.getBlockState(new BlockPos(c[0], c[2], c[1]));
            if (!sandSet.contains(st.getBlock())) st = net.minecraft.world.level.block.Blocks.SAND.defaultBlockState();
            sendToViewers(pos, st);
            shown[i] = Math.max(shown[i], k);
            fake.show(pos.asLong(), UUID.randomUUID());
            states.put(pos.asLong(), st);
            lastBlocksSent++;
        }

        void restoreAt(BlockPos pos) {
            sendReal(pos);
            fake.restore(pos.asLong());
            states.remove(pos.asLong());
        }

        void sendToViewers(BlockPos pos, BlockState st) {
            ClientboundBlockUpdatePacket pk = new ClientboundBlockUpdatePacket(pos, st);
            double vd2 = cfg.tremorViewDistance * cfg.tremorViewDistance;
            for (ServerPlayer v : level.players()) {
                if (v.distanceToSqr(cx, v.getY(), cz) > vd2) continue;
                v.connection.send(pk);
                sentTo.add(v.getUUID());
            }
        }

        void sendReal(BlockPos pos) {
            if (!level.hasChunkAt(pos)) return;
            ClientboundBlockUpdatePacket pk = new ClientboundBlockUpdatePacket(level, pos);
            for (UUID id : sentTo) {
                ServerPlayer v = online.get(id);
                if (v != null && v.level() == level) v.connection.send(pk);
            }
        }

        /** Restores everything that is still shown. Returns how many. */
        int cancel() {
            return fake.restoreAll(l -> sendReal(BlockPos.of(l)));
        }
    }

    private void stepRipples() {
        // the raises of every ripple, thinned by one ratio when over the budget (restores are never thinned; nobody drops to zero)
        List<List<long[]>> wants = new ArrayList<>();
        int total = 0;
        for (Ripple r : ripples) {
            List<long[]> w = r.wanted();
            wants.add(w);
            total += w.size();
        }
        double ratio = Tremor.ratio(total, cfg.tremorBlockBudgetPerTick);
        if (ratio < 1 && !blockBudgetLogged) {
            blockBudgetLogged = true;
            WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt: tremor fake blocks over the budget ({} wanted, {} allowed per tick): every ripple thinned by one ratio {} (none below 1)", total, cfg.tremorBlockBudgetPerTick, fmt(ratio));
        }
        int idx = 0;
        for (Iterator<Ripple> it = ripples.iterator(); it.hasNext(); idx++) {
            Ripple r = it.next();
            List<long[]> w = wants.get(idx);
            int n = Tremor.thin(w.size(), ratio);
            for (int k = 0; k < n; k++) r.raise(w.get(k));
            r.tick++;
            if (r.tick > r.ticks) {
                r.cancel();   // the last frame is flat: everything left is restored
                it.remove();
            }
        }
    }

    private int cancelRipples(long eventId) {
        int n = 0;
        for (Iterator<Ripple> it = ripples.iterator(); it.hasNext(); ) {
            Ripple r = it.next();
            if (r.eventId == eventId) { n += r.cancel(); it.remove(); }
        }
        return n;
    }

    private void cancelAllRipples() {
        for (Ripple r : ripples) r.cancel();
        ripples.clear();
    }

    /** Fake blocks currently shown by all ripples (for the status command and the smoke test). */
    public int fakeBlocksShown() {
        int n = 0;
        for (Ripple r : ripples) n += r.fake.positions().size();
        return n;
    }

    // ---- helpers ----

    private String names(List<UUID> ids) {
        List<String> l = new ArrayList<>();
        for (UUID id : ids) { ServerPlayer p = online.get(id); l.add(p == null ? id.toString() : p.getGameProfile().name()); }
        return String.join(", ", l);
    }

    static String trim(double v) { return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%.2f", v); }
    static String fmt(double v) { return String.format(Locale.ROOT, "%.2f", v); }
    static double factorOf(double width) { return SizeFactor.of(width); }
}
