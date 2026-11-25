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
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
    private final List<ParticleReq> particleReqs = new ArrayList<>();
    private final Map<Long, Integer> tremorCount = new HashMap<>();
    private long lastParticlesMade;

    private HuntAdapter() {
        this.mgr = new HuntManager(cfg, this, this::onAttack);
        mgr.aggro().setEffectProbe(this::hasEffect);
    }

    private final StrikeRunner strikes = new StrikeRunner(this);
    private final HazardRunner hazard = new HazardRunner(this, strikes);
    private volatile FreezeHook freezeHook = FreezeHook.LOGGING;
    private final Map<UUID, FreezeRun> freezes = new HashMap<>();
    private final List<ServerPlayer> extra = new ArrayList<>();

    /** Test only (the smoke start): a fake player standing at a place; it is hunted like a real one. Revived if it was killed before. */
    public ServerPlayer fakePlayer(ServerLevel level, double x, double y, double z) {
        net.neoforged.neoforge.common.util.FakePlayer fp = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes("WormFake".getBytes()), "WormFake"));
        if (!fp.isAlive()) fp.revive();
        fp.setInvisible(false);
        fp.setPos(x, y, z);
        if (!extra.contains(fp)) extra.add(fp);
        return fp;
    }

    public ServerPlayer fakePlayerOrNull() { return extra.isEmpty() ? null : extra.get(0); }

    public void removeFakePlayers() { for (ServerPlayer p : extra) mgr.logoff(p.getUUID()); extra.clear(); }

    /** The camera prompt plugs in here. */
    public void setFreezeHook(FreezeHook h) { this.freezeHook = h == null ? FreezeHook.LOGGING : h; }

    public HuntManager manager() { return mgr; }
    public HuntConfig config() { return cfg; }
    public ServerPlayer player(UUID id) { return online.get(id); }
    public long lastParticlesMade() { return lastParticlesMade; }

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
        for (ServerPlayer p : extra) {   // test-only fake players (hunt fake): the dedicated-server smoke start has no real player
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
        strikes.warmUp();
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
        mgr.tick(DT, this);
        tickFreezes();
        tickAttackRumbles();
        playRoarShots();
        strikes.tick();
        hazard.purge();
        hazard.drawDebug();
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
    public void onLoggedIn(PlayerEvent.PlayerLoggedInEvent e) { 
        mgr.login(e.getEntity().getUUID());
        if (e.getEntity() instanceof ServerPlayer sp) FreezeRun.clean(sp);   // belt and braces: a modifier left by a crash is removed (they are transient, so none is saved)
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) { if (e.getEntity() instanceof ServerPlayer sp) mgr.died(sp.getUUID()); }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent e) {
        int n = mgr.stop(null);
        stopAllRumbles();
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt: server stopping: {} event(s) stopped, every fake cell restored", n);
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
        playRoar(p, e.size, e.id * 0x9E3779B97F4A7C15L ^ t.id().getLeastSignificantBits());   // the only thing that plays at the start (prompt 15 part 4): no explosion, no puff
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
    public void onStrike(HuntEvent e) { strikes.onStrike(e); }

    @Override
    public void onEnd(HuntEvent e, HuntEvent.EndReason reason) {
        strikes.onEnd(e, reason);
        releaseFreeze(e);
        int restored = stopRumbles(e.id);
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt: event {} ended {} (list scores set to 0; {} tremor rumble(s) stopped, their cells restored)", e.id, reason, restored);
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

    // ---- the freeze (prompt 14 part 1) ----

    /** The start of FREEZE: the log line of prompt 13 (unchanged) and the victim is held. */
    private void onAttack(HuntEvent e, UUID victim, double w, String sizeName) {
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] {}", StrikeHook.attackLine(e, w, sizeName));
        ServerPlayer p = online.get(victim);
        if (p == null) return;
        FreezeRun run = new FreezeRun(p, e);
        freezes.put(victim, run);
        run.freeze.tick(0);   // nothing moves yet, but the jump block applies from this very tick
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt: freeze start victim={} for {} s (speed cut linearly to zero, jumping blocked, held on the spot); nothing else of the world is changed", p.getGameProfile().name(), trim(e.attackSeconds));
        freezeHook.onFreezeStart(victim, e);
        startAttackRumble(e, p);
    }

    private void tickFreezes() {
        for (FreezeRun r : new ArrayList<>(freezes.values())) {
            ServerPlayer cur = online.get(r.victim);
            if (cur != null && cur != r.player) r.player = cur;   // a new object after a respawn: nothing to hold
            if (r.event.phase() == HuntEvent.Phase.FREEZE || r.event.phase() == HuntEvent.Phase.STRIKE) r.freeze.tick(r.event.phase() == HuntEvent.Phase.FREEZE ? DT : 0);
        }
    }

    private void releaseFreeze(HuntEvent e) {
        if (e.victim() == null) return;
        FreezeRun r = freezes.remove(e.victim());
        if (r == null) return;
        r.freeze.release();
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt: freeze end victim={} (modifiers removed)", e.victimName());
        freezeHook.onFreezeEnd(e.victim(), e);
    }

    /** The freeze of the event's victim ends now (the devour: they are dead, nothing of it stays). */
    void releaseFreezeOf(HuntEvent e) { releaseFreeze(e); }

    boolean isSand(BlockState st) { return sandSet.contains(st.getBlock()); }
    /** The set SandBlocks of the config (prompt 13): Dune Sand, sand, red sand. */
    public boolean isSandBlock(BlockState st) { return sandSet.contains(st.getBlock()); }
    Integer surfaceOf(UUID id) { return surface.get(id); }
    StrikeRunner strikes() { return strikes; }
    HazardRunner hazard() { return hazard; }
    MinecraftServer server() { return server; }

    public boolean frozen(UUID id) { FreezeRun r = freezes.get(id); return r != null && r.freeze.started() && !r.freeze.released(); }

    /** The attribute modifiers and the hold of one frozen victim. */
    private static final class FreezeRun implements Freeze.Body {
        static final Identifier SPEED = Identifier.fromNamespaceAndPath(WormsOfArrakis.MOD_ID, "freeze_speed");
        static final Identifier JUMP = Identifier.fromNamespaceAndPath(WormsOfArrakis.MOD_ID, "freeze_jump");
        final UUID victim;
        final HuntEvent event;
        ServerPlayer player;
        final Freeze freeze;
        double ax, az;
        boolean anchored;
        int lastLogged = -1;

        FreezeRun(ServerPlayer p, HuntEvent e) {
            this.victim = p.getUUID();
            this.event = e;
            this.player = p;
            this.freeze = new Freeze(e.attackSeconds, this);
        }

        static void clean(ServerPlayer p) {
            var sp = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (sp != null) sp.removeModifier(SPEED);
            var ju = p.getAttribute(Attributes.JUMP_STRENGTH);
            if (ju != null) ju.removeModifier(JUMP);
        }

        @Override public void apply(double cut, boolean jumpBlocked) {
            var sp = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (sp != null) {
                sp.removeModifier(SPEED);
                if (cut > 0) sp.addTransientModifier(new AttributeModifier(SPEED, -cut, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            var ju = player.getAttribute(Attributes.JUMP_STRENGTH);
            if (ju != null && jumpBlocked && !ju.hasModifier(JUMP)) ju.addTransientModifier(new AttributeModifier(JUMP, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            if (player.isPassenger()) player.stopRiding();   // dismounted at the start and never allowed to mount again
            if (player.isFallFlying()) player.stopFallFlying();
            int tenth = (int) Math.floor(cut * 4);
            if (tenth != lastLogged) { lastLogged = tenth; WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt: freeze {} cut={}% of the speed", player.getGameProfile().name(), Math.round(cut * 100)); }
        }

        @Override public void hold() {
            if (!anchored) { anchored = true; ax = player.getX(); az = player.getZ(); return; }
            double d = Math.hypot(player.getX() - ax, player.getZ() - az);
            if (d > FREEZE_HOLD_TOLERANCE && player.connection != null) {
                player.connection.teleport(ax, player.getY(), az, player.getYRot(), player.getXRot());   // pushed or pearled away: put back on the spot (only past the tolerance)
                WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt: freeze {} was moved {} blocks from the spot, put back", player.getGameProfile().name(), trim(d));
            }
        }

        @Override public void release() { clean(player); }
    }

    /** Past this distance (blocks) from the spot a held victim is put back; below it nothing is done (no rubber band for a nudge). */
    public static final double FREEZE_HOLD_TOLERANCE = 0.6;

    // ---- the distant roar (prompt 15 part 4) ----

    private record RoarShot(ServerLevel level, double x, double y, double z, long due, String sound, float volume, float pitch) {}

    private final List<RoarShot> roarQueue = new ArrayList<>();
    private long roarTick;

    /** The roar of an event of this size for this player: one shared origin at RoarDistance on a bearing seeded by the event; the volume is computed so the range reaches the player with the margin. */
    public void playRoar(ServerPlayer p, SizeTable.Size size, long seed) {
        ServerLevel level = p.level();
        double d = Roar.distance(size, cfg);
        double b0 = Roar.bearing(seed);
        double bearing = b0;
        double[] xz = Roar.origin(p.getX(), p.getZ(), b0, d);
        Double oy = null;
        // ground level on sand where possible: the seeded bearing first, then the seven others 45 degrees apart (only loaded chunks are read, none is loaded)
        for (int k = 0; k < 8 && oy == null; k++) {
            double b = (b0 + 45.0 * k) % 360.0;
            double[] c = Roar.origin(p.getX(), p.getZ(), b, d);
            Double y = sandTop(level, (int) Math.floor(c[0]), (int) Math.floor(c[1]));
            if (y != null) { oy = y; xz = c; bearing = b; }
        }
        if (oy == null) {
            Double y = surfaceTop(level, (int) Math.floor(xz[0]), (int) Math.floor(xz[1]));
            oy = y != null ? y : p.getY();
        }
        double volume = Roar.volume(d, cfg.roarRangeMargin);
        double pitchFactor = cfg.roarFarPitch;
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] {}", Roar.line(xz[0], oy, xz[1], bearing, d, volume, size));
        List<Roar.Shot> shots = Roar.shots(volume, pitchFactor, seed);
        double lowest = Double.MAX_VALUE;
        for (Roar.Shot s : shots) lowest = Math.min(lowest, s.pitch());
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt: roar {} sounds over {} s, pitch x{} ({} the game's minimum 0.5), heard within {} blocks of the origin (loudness at the trigger player {})", shots.size(), fmt(shots.get(shots.size() - 1).t()),
                fmt(pitchFactor), lowest < Roar.MIN_PITCH ? "some below" : "none below", trim(Roar.range(volume)), fmt(Roar.loudness(d, volume)));
        for (Roar.Shot s : shots) roarQueue.add(new RoarShot(level, xz[0], oy, xz[1], roarTick + Math.round(s.t() / DT), s.sound(), (float) s.volume(), (float) Roar.playedPitch(s.pitch())));
    }

    /** The y above the top block when the top block of the column is sand and its chunk is loaded; else null. */
    private Double sandTop(ServerLevel level, int x, int z) {
        var ch = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (ch == null) return null;
        int top = ch.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);   // the y of the top block
        return isSandBlock(ch.getBlockState(new BlockPos(x, top, z))) ? Double.valueOf(top + 1) : null;
    }

    private Double surfaceTop(ServerLevel level, int x, int z) {
        var ch = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        return ch == null ? null : Double.valueOf(ch.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1);
    }

    private void playRoarShots() {
        roarTick++;
        if (roarQueue.isEmpty()) return;
        for (var it = roarQueue.iterator(); it.hasNext(); ) {
            RoarShot s = it.next();
            if (s.due() > roarTick) continue;
            it.remove();
            SoundEvent ev = sound(s.sound());
            if (ev != null) s.level().playSound(null, s.x(), s.y(), s.z(), ev, SoundSource.HOSTILE, s.volume(), s.pitch());
        }
    }

    // ---- tremors ----

    private long tremorSeq;

    /** One tremor under a player: a sand rumble (Rumble.play), sound, particles, and the camera hook. Also the preview command. */
    public Tremor.Plan playTremor(ServerPlayer p, long eventId, double factor, double strength) {
        Tremor.Plan plan = Tremor.plan(strength, factor, cfg);
        ServerLevel level = p.level();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        tremorFx(p, plan);
        var h = rumbleFor(eventId, p, plan, eventId * 0x9E3779B97F4A7C15L + (tremorSeq++));
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt: tremor rumble S={} radius {} blocks max {}/16 share of columns {} columns drawn {}", fmt(plan.strength()), fmt(plan.radius()), plan.maxSixteenths(), fmt(Tremor.share(plan.strength())), h.columns());
        for (String n : h.notes()) WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt: tremor rumble: {}", n);
        TremorFx.onTremor(p, strength);
        return plan;
    }

    /** The sound and the sand particles of one tremor under a player (prompt 13), also repeated by the attack rumble at strength 1. */
    private void tremorFx(ServerPlayer p, Tremor.Plan plan) {
        ServerLevel level = p.level();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        SoundEvent low = sound("entity.warden.step"), crack = sound("block.sand.break");
        if (low != null) level.playSound(null, x, y, z, low, SoundSource.HOSTILE, (float) plan.volume(), 0.5f);
        if (crack != null) level.playSound(null, x, y, z, crack, SoundSource.BLOCKS, (float) Math.max(0.5, plan.volume() * 0.7), 0.6f);
        BlockState sand = sandStateUnder(level, p);
        particleReqs.add(new ParticleReq(level, sand, x, y + 0.2, z, plan.particles(), plan.radius() * 0.4, 0.3, plan.radius() * 0.4, 0.25));
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

    private boolean budgetLogged;

    /** The sand rumbles of the tremors of each event (stopped when the event ends; the rumble itself ends after one second). */
    private final Map<Long, List<com.paulorchard.islandcraft.wormsofarrakis.rumble.Rumble.Handle>> rumbles = new HashMap<>();

    private int stopRumbles(long eventId) {
        AttackRun ar = attackRuns.get(eventId);
        if (ar != null) stopAttackRun(ar, "the event ended or the server is stopping");
        List<com.paulorchard.islandcraft.wormsofarrakis.rumble.Rumble.Handle> l = rumbles.remove(eventId);
        int n = 0;
        if (l != null) for (var h : l) if (h.running()) { h.stop(); n++; }
        return n;
    }

    private void stopAllRumbles() {
        for (AttackRun ar : new ArrayList<>(attackRuns.values())) stopAttackRun(ar, "server stopping");
        for (long id : new ArrayList<>(rumbles.keySet())) stopRumbles(id);
    }

    /** The tremor rumble of one player: a Rumble.play centred on them (see {@link #playTremor}). */
    private com.paulorchard.islandcraft.wormsofarrakis.rumble.Rumble.Handle rumbleFor(long eventId, ServerPlayer p, Tremor.Plan plan, long seed) {
        ServerLevel level = p.level();
        List<ServerPlayer> viewers = new ArrayList<>();
        viewers.add(p);
        double vd2 = cfg.tremorViewDistance * cfg.tremorViewDistance;
        for (ServerPlayer v : level.players()) if (v != p && v.distanceToSqr(p.getX(), v.getY(), p.getZ()) <= vd2) viewers.add(v);
        for (ServerPlayer v : extra) if (v != p && v.level() == level && !viewers.contains(v) && v.distanceToSqr(p.getX(), v.getY(), p.getZ()) <= vd2) viewers.add(v);
        final double s = plan.strength();
        var params = com.paulorchard.islandcraft.wormsofarrakis.rumble.Rumble.Params.of(plan.radius(), Tremor.RUMBLE_SECONDS).withMax(plan.maxSixteenths()).withSeed(seed)
                .withStrength(t -> s).withShare(Tremor::share);
        var h = com.paulorchard.islandcraft.wormsofarrakis.rumble.Rumble.play(level, p.getX(), p.getZ(), params, viewers);
        rumbles.computeIfAbsent(eventId, k -> new ArrayList<>()).add(h);
        rumbles.get(eventId).removeIf(x -> !x.running());
        return h;
    }

    // ---- the attack rumble (prompt 16 part 1): one long rumble round the victim from the start of the freeze until the death ----

    /** One running attack rumble: of an event (until the death or the end), or a preview (until its seconds are over). */
    private static final class AttackRun {
        final long key;
        final HuntEvent event;   // null for a preview
        final UUID victim;
        final ServerLevel level;
        final com.paulorchard.islandcraft.wormsofarrakis.rumble.Rumble.Handle handle;
        final AttackRumble.Plan plan;
        final AttackRumble.FxClock fx;
        final Tremor.Plan fxPlan;
        double t, viewerT;
        int viewerChanges;
        double previewSeconds;

        AttackRun(long key, HuntEvent event, UUID victim, ServerLevel level, com.paulorchard.islandcraft.wormsofarrakis.rumble.Rumble.Handle handle, AttackRumble.Plan plan, long seed, Tremor.Plan fxPlan) {
            this.key = key;
            this.event = event;
            this.victim = victim;
            this.level = level;
            this.handle = handle;
            this.plan = plan;
            this.fx = new AttackRumble.FxClock(seed);
            this.fxPlan = fxPlan;
        }
    }

    private final Map<Long, AttackRun> attackRuns = new HashMap<>();
    private long previewSeq = -1;

    /** The players who see the rumble round a victim: the victim and everybody within TremorViewDistance of them, in the same dimension. */
    private List<ServerPlayer> viewersOf(ServerPlayer p) {
        List<ServerPlayer> viewers = new ArrayList<>();
        viewers.add(p);
        double vd2 = cfg.tremorViewDistance * cfg.tremorViewDistance;
        ServerLevel level = p.level();
        for (ServerPlayer v : level.players()) if (v != p && v.distanceToSqr(p.getX(), v.getY(), p.getZ()) <= vd2) viewers.add(v);
        for (ServerPlayer v : extra) if (v != p && v.level() == level && !viewers.contains(v) && v.distanceToSqr(p.getX(), v.getY(), p.getZ()) <= vd2) viewers.add(v);
        return viewers;
    }

    private double longestPlay(SizeTable.Size size) {
        double m = 0;
        for (StrikeType t : StrikeType.values()) m = Math.max(m, strikes.plan(t, size).playTime());
        return m;
    }

    private void startAttackRumble(HuntEvent e, ServerPlayer p) {
        AttackRumble.Plan plan = AttackRumble.plan(e.factor, cfg, longestPlay(e.size));
        long seed = e.id * 0x9E3779B97F4A7C15L ^ 0xA77ACCL;
        var params = com.paulorchard.islandcraft.wormsofarrakis.rumble.Rumble.Params.of(plan.radius(), plan.seconds()).withMax(plan.maxSixteenths()).withSeed(seed)
                .withStrength(plan.asFraction()).withShare(Tremor::share);
        var h = com.paulorchard.islandcraft.wormsofarrakis.rumble.Rumble.play(p.level(), p.getX(), p.getZ(), params, viewersOf(p));
        AttackRun r = new AttackRun(e.id, e, p.getUUID(), p.level(), h, plan, seed ^ 0x5EED, Tremor.plan(1.0, e.factor, cfg));
        attackRuns.put(e.id, r);
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt: attack rumble start victim={} radius {} blocks max {}/16 strength {} -> 1.0 over {} s then 1.0 until the death (limit {} s), {} columns drawn, viewers within {} blocks re-chosen every {} s",
                p.getGameProfile().name(), fmt(plan.radius()), plan.maxSixteenths(), fmt(plan.start()), trim(plan.freezeSeconds()), fmt(plan.seconds()), h.columns(), trim(cfg.tremorViewDistance), trim(AttackRumble.VIEWER_PERIOD));
        for (String n : h.notes()) WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt: attack rumble: {}", n);
    }

    /** The command: the attack rumble of a size on the caller with the same curve, for {@code seconds}, then it stops. */
    public AttackRumble.Plan previewAttackRumble(ServerPlayer p, SizeTable.Size size, double seconds) {
        double factor = SizeFactor.of(size.width);
        AttackRumble.Plan plan = AttackRumble.plan(factor, cfg, 0);
        plan = new AttackRumble.Plan(plan.radius(), plan.maxSixteenths(), plan.start(), plan.freezeSeconds(), seconds, factor);
        long key = previewSeq--;
        long seed = System.nanoTime();
        var params = com.paulorchard.islandcraft.wormsofarrakis.rumble.Rumble.Params.of(plan.radius(), seconds).withMax(plan.maxSixteenths()).withSeed(seed).withStrength(plan.asFraction()).withShare(Tremor::share);
        var h = com.paulorchard.islandcraft.wormsofarrakis.rumble.Rumble.play(p.level(), p.getX(), p.getZ(), params, viewersOf(p));
        AttackRun r = new AttackRun(key, null, p.getUUID(), p.level(), h, plan, seed ^ 0x5EED, Tremor.plan(1.0, factor, cfg));
        r.previewSeconds = seconds;
        attackRuns.put(key, r);
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt: attack rumble preview {} W{} radius {} max {}/16 strength {} -> 1.0 over {} s, {} s in all, {} columns drawn", p.getGameProfile().name(), trim(size.width), fmt(plan.radius()), plan.maxSixteenths(), fmt(plan.start()), trim(plan.freezeSeconds()), fmt(seconds), h.columns());
        for (String n : h.notes()) WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt: attack rumble preview: {}", n);
        return plan;
    }

    private void tickAttackRumbles() {
        if (attackRuns.isEmpty()) return;
        for (AttackRun r : new ArrayList<>(attackRuns.values())) {
            ServerPlayer p = online.get(r.victim);
            String why = null;
            if (!r.handle.running()) why = "the rumble ran out (hard timeout)";
            else if (r.event != null && r.event.ended()) why = "the event ended";
            else if (r.event != null && r.event.devoured()) why = "the victim was devoured";
            else if (p == null) why = "the victim logged off";
            else if (p.level() != r.level) why = "the victim changed dimension";
            else if (!p.isAlive()) why = "the victim is dead";
            else if (r.event == null && r.t >= r.previewSeconds) why = "the preview is over";
            if (why != null) { stopAttackRun(r, why); continue; }
            r.t += DT;
            r.viewerT += DT;
            if (r.viewerT >= AttackRumble.VIEWER_PERIOD - 1e-9) {
                r.viewerT = 0;
                int[] ar = r.handle.setViewers(viewersOf(p));
                if (ar[0] + ar[1] > 0) {
                    r.viewerChanges += ar[0] + ar[1];
                    WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt: attack rumble viewers changed: {} came into range, {} left (their blocks are real again)", ar[0], ar[1]);
                }
            }
            if (r.fx.due(r.t)) {
                r.fx.fired(r.t);
                tremorFx(p, r.fxPlan);
            }
        }
    }

    private void stopAttackRun(AttackRun r, String why) {
        if (attackRuns.remove(r.key) == null) return;
        boolean was = r.handle.running();
        r.handle.stop();
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hunt: attack rumble stopped after {} s: {} (cells restored: {}; {} viewer changes)", fmt(r.t), why, was ? "yes" : "already ended", r.viewerChanges);
    }

    /** The victim is dead (or the strike is over): the attack rumble of the event stops now. Returns true when one was running. */
    boolean stopAttackRumble(HuntEvent e, String why) {
        AttackRun r = attackRuns.get(e.id);
        if (r == null) return false;
        stopAttackRun(r, why);
        return true;
    }

    /** Test and status: the number of attack rumbles running. */
    public int attackRumblesRunning() { return attackRuns.size(); }

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
