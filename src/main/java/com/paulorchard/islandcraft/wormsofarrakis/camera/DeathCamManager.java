package com.paulorchard.islandcraft.wormsofarrakis.camera;

import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntAdapter;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntConfig;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.SizeTable;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveFactory;
import com.paulorchard.islandcraft.wormsofarrakis.test.TestSetup;
import com.paulorchard.islandcraft.wormsofarrakis.test.TestWormEntity;
import com.paulorchard.islandcraft.wormsofarrakis.test.WormSpec;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Prompt 16 part 3: the death camera, game side. When a player dies to the worm, an invisible {@link DeathCamEntity} is spawned at the start of the orbit, the dead player's connection is sent
 * {@code ClientboundSetCameraPacket(camera)} one tick later (so the client has the entity), and every tick the entity is moved along the orbit of {@link DeathCam}. The player is never
 * teleported and their chunks never change. The view is given back on respawn, log-off, dimension change, the timeout, an ended nokill strike, the stop command and the server stop: the
 * entity is discarded and, when the same connected player object is still there, {@code ClientboundSetCameraPacket(player)} is sent (a client whose camera entity vanishes would otherwise keep
 * looking at the frozen last place).
 */
public final class DeathCamManager {
    private static final DeathCamManager INSTANCE = new DeathCamManager();
    public static DeathCamManager instance() { return INSTANCE; }
    public static void init() { NeoForge.EVENT_BUS.register(INSTANCE); }

    private static final double DT = 0.05;

    /** One camera in use. */
    public static final class Session {
        public final UUID id;
        public final ServerPlayer player;
        public final ServerLevel level;
        public final DeathCamEntity entity;
        public final DeathCam.Setup setup;
        public final double runSeconds, maxSeconds;
        public final long startTick;
        public long eventId;     // the hunt event whose strike this camera belongs to (the view is given back when it ends), else -1 (a killed player keeps it until Respawn)
        public final boolean preview;
        public final DeathCam.Radius radius;
        public boolean sent;
        public DeathCam.Pose last;
        public double maxStep;

        Session(ServerPlayer p, DeathCamEntity e, DeathCam.Setup s, double run, double max, long start, long eventId, boolean preview, DeathCam.Radius radius) {
            this.id = p.getUUID();
            this.player = p;
            this.level = (ServerLevel) p.level();
            this.entity = e;
            this.setup = s;
            this.runSeconds = run;
            this.maxSeconds = max;
            this.startTick = start;
            this.eventId = eventId;
            this.preview = preview;
            this.radius = radius;
        }
    }

    private final Map<UUID, Session> sessions = new LinkedHashMap<>();
    private long tick;
    private boolean failedOnce;
    public int started, restored, leakedEntities;

    public Session sessionOf(UUID id) { return sessions.get(id); }
    public int running() { return sessions.size(); }

    private HuntConfig cfg() { return HuntAdapter.instance().config(); }

    public DeathCam.Params params() {
        HuntConfig c = cfg();
        return new DeathCam.Params(c.deathCamDistance.clone(), c.deathCamTurnDegrees, c.lookHeight, c.deathCamHeightFactor, c.deathCamMinHeight, c.deathCamSandClearance, c.deathCamMaxSeconds);
    }

    /** The height of the top of the ground at a column, or NaN when its chunk is not loaded. */
    public static double groundAt(ServerLevel level, double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        var ch = level.getChunkSource().getChunkNow(bx >> 4, bz >> 4);
        if (ch == null) return Double.NaN;
        return ch.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) + 1.0;
    }

    /** The view distance in chunks that counts for a player: what they asked for, at most the server's. */
    public static int viewDistanceOf(ServerPlayer p) {
        int server = p.level().getServer().getPlayerList().getViewDistance();
        return Math.max(2, Math.min(p.requestedViewDistance(), server));
    }

    public static int sizeIndexOf(double width) {
        int best = 0;
        for (SizeTable.Size s : SizeTable.Size.values()) if (Math.abs(s.width - width) < Math.abs(SizeTable.Size.values()[best].width - width)) best = s.ordinal();
        return best;
    }

    /**
     * Starts the camera for a player. Returns null (and adds the reason to {@code notes}) when it could not be started: the death still happens, the player just sees the normal death screen.
     * {@code eventId} is the hunt event of a nokill strike (the view is given back when it ends), else -1.
     */
    public Session start(ServerPlayer p, double cx, double sandY, double cz, double strikeYawDeg, int sizeIndex, long seed, double runSeconds, double maxSeconds, long eventId, boolean preview, List<String> notes) {
        try {
            if (!(p.level() instanceof ServerLevel level)) return null;
            Session old = sessions.get(p.getUUID());
            if (old != null) restore(old, CameraLifecycle.EndPath.NEW_CAMERA, "a new camera starts");
            DeathCam.Params prm = params();
            DeathCam.Radius r = DeathCam.radius(prm, sizeIndex, viewDistanceOf(p));
            if (r.clamped()) notes.add(String.format(Locale.ROOT, "the orbit radius %.0f is clamped to %.0f blocks = 16 x (view distance %d - 1) so the camera stays inside the area the player has loaded", r.wanted(), r.limit(), viewDistanceOf(p)));
            DeathCam.Setup setup = DeathCam.setup(prm, cx, sandY, cz, strikeYawDeg, r.value(), seed);
            DeathCam.Pose p0 = DeathCam.pose(setup, 0, runSeconds, (x, z) -> groundAt(level, x, z));
            DeathCamEntity e = TestSetup.DEATH_CAMERA.get().create(level, EntitySpawnReason.COMMAND);
            if (e == null) throw new IllegalStateException("the camera entity could not be created");
            e.snapTo(p0.x(), p0.y(), p0.z(), p0.yaw(), p0.pitch());
            if (!level.addFreshEntity(e)) throw new IllegalStateException("the camera entity could not be added to the level");
            Session s = new Session(p, e, setup, runSeconds, maxSeconds, tick, eventId, preview, r);
            s.last = p0;
            sessions.put(s.id, s);
            started++;
            WormsOfArrakis.LOGGER.info("[WormsOfArrakis] camera: start for {} centre ({}, {}, {}) radius {} height {} look-at height {} side {} direction {} turn {} degrees over {} s then hold (limit {} s), camera entity {} at ({}, {}, {}); the view is sent next tick",
                    p.getGameProfile().name(), f(cx), f(sandY), f(cz), f(r.value()), f(setup.height()), f(setup.lookHeightBlocks()), setup.side() > 0 ? "+" : "-", setup.direction() > 0 ? "counter-clockwise" : "clockwise", f(prm.turnDegrees()), f(runSeconds), f(maxSeconds), e.getId(), f(p0.x()), f(p0.y()), f(p0.z()));
            return s;
        } catch (RuntimeException ex) {
            notes.add("the camera could not be started: " + ex);
            if (!failedOnce) { failedOnce = true; WormsOfArrakis.LOGGER.warn("[WormsOfArrakis] camera: could not start for {}: {} (logged once; the death still happens, the player sees the normal death screen)", p.getGameProfile().name(), ex.toString()); }
            return null;
        }
    }

    private static String f(double v) { return String.format(Locale.ROOT, "%.1f", v); }

    // ---- every tick ----

    @SubscribeEvent
    public void onTick(ServerTickEvent.Post e) {
        tick++;
        if (sessions.isEmpty()) return;
        HuntAdapter a = HuntAdapter.instance();
        for (Session s : new ArrayList<>(sessions.values())) {
            ServerPlayer cur = a == null ? null : a.player(s.id);
            if (cur == null) cur = e.getServer().getPlayerList().getPlayer(s.id);
            if (cur != s.player) { restore(s, CameraLifecycle.EndPath.PLAYER_GONE, "the player object is gone (respawned or logged off)"); continue; }
            if (s.player.level() != s.level) { restore(s, CameraLifecycle.EndPath.DIMENSION, "the player changed dimension"); continue; }
            if (s.entity.isRemoved()) { restore(s, CameraLifecycle.EndPath.ENTITY_REMOVED, "the camera entity was removed by something else"); continue; }
            double t = (tick - s.startTick) * DT;
            if (t >= s.maxSeconds) { restore(s, CameraLifecycle.EndPath.TIMEOUT, "DeathCamMaxSeconds (" + f(s.maxSeconds) + " s) reached"); continue; }
            if (!s.sent && tick > s.startTick) {
                s.sent = true;
                try {
                    s.player.connection.send(new ClientboundSetCameraPacket(s.entity));
                    WormsOfArrakis.LOGGER.info("[WormsOfArrakis] camera: ClientboundSetCameraPacket(entity {}) sent to {}", s.entity.getId(), s.player.getGameProfile().name());
                } catch (RuntimeException ex) {
                    WormsOfArrakis.LOGGER.warn("[WormsOfArrakis] camera: the set-camera packet could not be sent to {}: {}", s.player.getGameProfile().name(), ex.toString());
                    restore(s, CameraLifecycle.EndPath.SEND_FAILED, "the packet could not be sent");
                    continue;
                }
            }
            DeathCam.Pose p = DeathCam.pose(s.setup, t, s.runSeconds, (x, z) -> groundAt(s.level, x, z));
            s.maxStep = Math.max(s.maxStep, DeathCam.step(s.last, p));
            s.last = p;
            s.entity.snapTo(p.x(), p.y(), p.z(), p.yaw(), p.pitch());
            if (s.preview && t >= s.runSeconds) restore(s, CameraLifecycle.EndPath.PREVIEW_OVER, "the preview is over");
        }
    }

    // ---- the strike camera of a held player (prompt 17 part 0.3) ----

    /**
     * The strike camera starts when the player is held: it orbits THEIR held spot, side-on to the worm (yaw {@code strikeYawDeg}), turning {@code DeathCamTurnDegrees} over the whole run of the move.
     * Returns null (the reason is logged) when it could not start; the player then just stays held and sees the normal view.
     */
    public Session startStrikeCamera(ServerPlayer sp, double strikeYawDeg, int sizeIndex, long seed, double runSeconds, long eventId) {
        List<String> notes = new ArrayList<>();
        Session s = start(sp, sp.getX(), sp.getY(), sp.getZ(), strikeYawDeg, sizeIndex, seed, Math.max(1.0, runSeconds), params().maxSeconds(), eventId, false, notes);
        for (String n : notes) WormsOfArrakis.LOGGER.info("[WormsOfArrakis] camera: {}", n);
        return s;
    }

    /** The player is killed at the end: the camera stays on its last pose behind the death screen until Respawn, the timeout or any other end path. */
    public void detachFromEvent(UUID id) {
        Session s = sessions.get(id);
        if (s != null) s.eventId = -1;
    }

    /** The player is released alive: the camera entity is discarded and their own view is sent back. */
    public boolean releaseView(UUID id, String why) {
        Session s = sessions.get(id);
        if (s == null) return false;
        restore(s, CameraLifecycle.EndPath.EVENT_END, why);
        return true;
    }

    // ---- giving the view back ----

    /** The strike of a nokill event is over: the view is given back to the (living) player. */
    public void onEventEnd(long eventId) {
        for (Session s : new ArrayList<>(sessions.values())) if (s.eventId == eventId) restore(s, CameraLifecycle.EndPath.EVENT_END, "the nokill strike ended");
    }

    /** Discards the camera entity and, when asked and possible, sends the player their own view back. Safe to call twice. */
    public void restore(Session s, CameraLifecycle.EndPath path, String why) {
        boolean sendBack = CameraLifecycle.sendsViewBack(path);
        if (!sessions.remove(s.id, s)) return;
        boolean sent = false;
        try {
            if (!s.entity.isRemoved()) s.entity.discard();
        } catch (RuntimeException ex) {
            leakedEntities++;
            WormsOfArrakis.LOGGER.warn("[WormsOfArrakis] camera: the camera entity {} could not be discarded: {}", s.entity.getId(), ex.toString());
        }
        if (sendBack && s.player.connection != null && !s.player.hasDisconnected()) {
            try {
                s.player.connection.send(new ClientboundSetCameraPacket(s.player));
                sent = true;
            } catch (RuntimeException ex) {
                WormsOfArrakis.LOGGER.warn("[WormsOfArrakis] camera: the view could not be given back to {}: {}", s.player.getGameProfile().name(), ex.toString());
            }
        }
        restored++;
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] camera: restored for {} ({}): entity discarded, view packet to the player {}, orbit step at most {} blocks per tick", s.player.getGameProfile().name(), why, sent ? "sent" : "not sent", String.format(Locale.ROOT, "%.3f", s.maxStep));
    }

    /** The command: the caller's camera ends now. Returns true when there was one. */
    public boolean stop(UUID id, String why) {
        Session s = sessions.get(id);
        if (s == null) return false;
        restore(s, CameraLifecycle.EndPath.STOP_COMMAND, why);
        return true;
    }

    @SubscribeEvent
    public void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent e) {
        Session s = sessions.get(e.getEntity().getUUID());
        if (s != null) restore(s, CameraLifecycle.EndPath.LOGOFF, "the player logged off");
    }

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        Session s = sessions.get(e.getEntity().getUUID());
        if (s != null) restore(s, CameraLifecycle.EndPath.DIMENSION, "the player changed dimension");
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        Session s = sessions.get(e.getEntity().getUUID());
        if (s != null) restore(s, CameraLifecycle.EndPath.RESPAWN, "the player pressed Respawn (a new player object; the client resets its view)");
    }

    @SubscribeEvent
    public void onStopping(ServerStoppingEvent e) {
        for (Session s : new ArrayList<>(sessions.values())) restore(s, CameraLifecycle.EndPath.SERVER_STOP, "the server is stopping");
    }

    public String status() {
        return String.format(Locale.ROOT, "camera: %d running, %d started, %d restored, %d entities that could not be discarded", sessions.size(), started, restored, leakedEntities);
    }
}
