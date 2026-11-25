package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
import com.paulorchard.islandcraft.wormsofarrakis.hazard.Box;
import com.paulorchard.islandcraft.wormsofarrakis.hazard.HazardRules;
import com.paulorchard.islandcraft.wormsofarrakis.hazard.WormHazard;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveFactory;
import com.paulorchard.islandcraft.wormsofarrakis.test.TestWormEntity;
import com.paulorchard.islandcraft.wormsofarrakis.test.WormSpec;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Prompt 16 part 2: the deadly worm, game side. Every server tick of a worm entity (the hook of {@link TestWormEntity}) the strike worm (always) or a test worm (only with HazardOnTestWorms) is
 * tested against the living entities near it: a cheap prefilter (the whole pose box inflated by HazardSkin + 2, {@code level.getEntities}, at most HazardEntitiesPerTick nearest first), then the
 * engine-free {@link WormHazard} samples the real box of each at HazardSubsteps instants between the previous tick and this one. The first touch kills, once. The rule of who dies is
 * {@link HazardRules#decide}.
 */
public final class HazardRunner {
    public static final TagKey<EntityType<?>> WORM_IMMUNE = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(WormsOfArrakis.MOD_ID, "worm_immune"));

    private static final HazardRules.Exemptions<Entity> EXEMPTIONS = new HazardRules.Exemptions<>();

    /** The extension point of the rideable worms and their riders (a later prompt): an entity for which any registered predicate says true is never killed by a worm. */
    public static void registerExemption(Predicate<Entity> p) { EXEMPTIONS.register(p); }

    private static HazardRunner instance;
    public static HazardRunner instance() { return instance; }

    private final HuntAdapter a;
    private final StrikeRunner strikes;

    /** The state of one worm: its assembled hazard (rebuilt if the spec changes), the entities already killed by it. */
    private static final class State {
        final TestWormEntity worm;
        String spec = "";
        WormHazard hazard;
        final HazardRules.Ledger killed = new HazardRules.Ledger();
        boolean builtLogged;
        State(TestWormEntity w) { worm = w; }
    }

    private final Map<UUID, State> states = new HashMap<>();
    private final HazardRules.Ledger ledger = new HazardRules.Ledger();
    private final Set<UUID> debug = new HashSet<>();
    private int debugTick;

    /** Counters for the report and the status. */
    public long ticksRun, entitiesTested, kills;
    public long nanosTotal;
    public int entitiesPeak;

    /** A kill that other features can follow (the death camera of part 3). */
    public interface KillListener { void onKill(ServerLevel level, LivingEntity victim, boolean isStrikeVictim, TestWormEntity worm, double wormAgeSeconds); }

    private final List<KillListener> listeners = new ArrayList<>();
    public void addKillListener(KillListener l) { listeners.add(l); }

    HazardRunner(HuntAdapter a, StrikeRunner strikes) {
        this.a = a;
        this.strikes = strikes;
        instance = this;
        TestWormEntity.SERVER_TICK_HOOK = this::step;
    }

    private HuntConfig cfg() { return a.config(); }

    public WormHazard.Settings settings() {
        HuntConfig c = cfg();
        return WormHazard.Settings.of(c.hazardSkin, c.throatReach, c.hazardParts, c.hazardSubsteps, c.hazardTimeOffsetSeconds);
    }

    private boolean deadly(TestWormEntity w) { return HazardRules.deadlyWorm(w.isStrikeWorm(), cfg().hazardOnTestWorms); }

    // ---- the tick of a worm ----

    private void step(TestWormEntity worm) {
        if (!(worm.level() instanceof ServerLevel level) || worm.isRemoved() || !deadly(worm)) return;
        long t0 = System.nanoTime();
        State st = states.computeIfAbsent(worm.getUUID(), k -> new State(worm));
        String sp = worm.getSpecString();
        WormSpec spec;
        try {
            if (!sp.startsWith("move;")) return;
            if (st.hazard == null || !sp.equals(st.spec)) {
                spec = WormSpec.parse(sp);
                st.hazard = new WormHazard(spec, worm.getX(), worm.getY(), worm.getZ(), worm.getYRot(), settings());
                st.spec = sp;
                if (!st.builtLogged) {
                    st.builtLogged = true;
                    WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hazard: worm {} {} W{} armed ({}; skin {} throat reach {} parts body={} head={} throat={}, {} substeps per tick, time offset {} s)",
                            worm.getId(), st.hazard.move.label(), HuntAdapter.trim(st.hazard.move.width), worm.isStrikeWorm() ? "strike worm, always deadly" : "test worm, HazardOnTestWorms is on", HuntAdapter.fmt(cfg().hazardSkin), HuntAdapter.fmt(cfg().throatReach),
                            st.hazard.settings.body(), st.hazard.settings.head(), st.hazard.settings.throat(), st.hazard.settings.substeps(), HuntAdapter.fmt(cfg().hazardTimeOffsetSeconds));
                }
            }
            spec = WormSpec.parse(sp);
        } catch (RuntimeException e) {
            return;
        }
        WormHazard hz = st.hazard;
        double tNow = MoveFactory.tau(spec, worm.tickCount), tPrev = MoveFactory.tau(spec, Math.max(0, worm.tickCount - 1));
        // the prefilter: the box of the whole pose over the substeps, inflated by the skin and 2 blocks
        double[] b = null;
        int N = hz.settings.substeps();
        for (int i = 1; i <= N; i++) {
            double[] bi = hz.boundsWorld(tPrev + (tNow - tPrev) * i / N + hz.settings.timeOffset(), hz.settings.skin() + 2);
            if (b == null) b = bi;
            else for (int k = 0; k < 3; k++) { b[k] = Math.min(b[k], bi[k]); b[k + 3] = Math.max(b[k + 3], bi[k + 3]); }
        }
        AABB aabb = new AABB(b[0], b[1], b[2], b[3], b[4], b[5]);
        List<Entity> near = new ArrayList<>(level.getEntities(worm, aabb, e -> e instanceof LivingEntity && !(e instanceof TestWormEntity)));
        double hx = 0.5 * (b[0] + b[3]), hy = 0.5 * (b[1] + b[4]), hz2 = 0.5 * (b[2] + b[5]);
        int cap = cfg().hazardEntitiesPerTick;
        if (near.size() > cap) {
            near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(hx, hy, hz2)));
            near = near.subList(0, cap);
        }
        entitiesPeak = Math.max(entitiesPeak, near.size());
        StrikeRunner.Run run = strikeRun(worm);
        for (Entity e : near) {
            entitiesTested++;
            if (!(e instanceof LivingEntity le) || st.killed.killed(e.getUUID())) continue;
            boolean isVictim = run != null && e.getUUID().equals(run.victim);
            HazardRules.Verdict v = HazardRules.decide(subject(le), cfg().hazardKillsPlayers, cfg().hazardKillsMobs);
            if (v != HazardRules.Verdict.KILL) continue;
            AABB bb = e.getBoundingBox();
            Box box = new Box(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ);
            WormHazard.Contact c = hz.firstContact(box, tPrev, tNow);
            if (c == null) continue;
            double wormSeconds = (worm.tickCount - 1 + (double) c.substep() / N) / 20.0;
            st.killed.firstKill(e.getUUID());
            touch(level, worm, run, le, isVictim, c.part(), wormSeconds);
        }
        ticksRun++;
        nanosTotal += System.nanoTime() - t0;
    }

    private StrikeRunner.Run strikeRun(TestWormEntity w) { return w.isStrikeWorm() ? strikes.runOfWorm(w) : null; }

    private static Predicate<Entity> cameraTest = e -> e instanceof com.paulorchard.islandcraft.wormsofarrakis.camera.DeathCamEntity;
    /** The camera entity of part 3 is never a target. */
    public static void setCameraTest(Predicate<Entity> p) { cameraTest = p; }

    HazardRules.Subject subject(LivingEntity e) {
        ServerPlayer sp = e instanceof ServerPlayer p ? p : null;
        boolean exempt = e.getType().builtInRegistryHolder().is(WORM_IMMUNE) || EXEMPTIONS.exempt(e);
        return new HazardRules.Subject(true, sp != null, sp != null && sp.isCreative(), sp != null && sp.isSpectator(), e.isAlive(), ((Entity) e) instanceof TestWormEntity, cameraTest.test(e), exempt, ledger.refused(e.getUUID()));
    }

    // ---- the kill ----

    private void touch(ServerLevel level, TestWormEntity worm, StrikeRunner.Run run, LivingEntity e, boolean isVictim, String part, double wormSeconds) {
        double table = run != null ? run.devourSeconds : 0;
        String who = e instanceof ServerPlayer sp ? sp.getGameProfile().name() : e.getType().builtInRegistryHolder().key().identifier().toString();
        String kind = HazardRules.kind(isVictim, e instanceof ServerPlayer);
        if (isVictim) {
            strikes.devourByTouch(run, (ServerPlayer) e, part, wormSeconds);
            return;
        }
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] {}", HazardRules.killLine(who, part, wormSeconds, table, kind));
        if (kill(level, e)) {
            kills++;
            for (KillListener l : listeners) l.onKill(level, e, false, worm, wormSeconds);
        }
    }

    /** The swallowed kill of prompt 14: invisible, the damage type with its bypass tags, die if still alive. Returns true when the entity is dead; a mob that refuses is logged once and ignored from now on. */
    boolean kill(ServerLevel level, LivingEntity e) {
        DamageSource src = strikes.swallowed(level);
        e.setInvisible(true);
        e.hurtServer(level, src, Float.MAX_VALUE);
        if (e.isAlive()) e.die(src);
        if (e.isAlive()) {
            e.setInvisible(false);
            if (ledger.firstRefusal(e.getUUID())) WormsOfArrakis.LOGGER.info("[WormsOfArrakis] hazard: {} ({}) refused the damage and the death and stays alive; it is ignored from now on", e.getType().builtInRegistryHolder().key().identifier(), e.getUUID());
            return false;
        }
        return true;
    }

    /** The victim was killed by the strike (touch or backup): other features follow. */
    void victimKilled(ServerLevel level, LivingEntity v, TestWormEntity worm, double wormSeconds) {
        kills++;
        for (KillListener l : listeners) l.onKill(level, v, true, worm, wormSeconds);
    }

    /** nokill: the worm touched the victim, who is not killed; the kill listeners (the camera) are told as for a death. */
    void victimTouched(ServerLevel level, LivingEntity v, TestWormEntity worm, double wormSeconds) {
        for (KillListener l : listeners) l.onKill(level, v, true, worm, wormSeconds);
    }

    /** Removes the state of worms that are gone. */
    void purge() { states.values().removeIf(s -> s.worm.isRemoved()); }

    public int tracked() { return states.size(); }

    // ---- the debug drawing ----

    public void setDebug(ServerPlayer p, boolean on) { if (on) debug.add(p.getUUID()); else debug.remove(p.getUUID()); }
    public boolean debugOn(UUID id) { return debug.contains(id); }
    public void debugOffAll() { debug.clear(); }

    private static final DustParticleOptions RED = new DustParticleOptions(0xFF2020, 1.0f);

    /** Every second tick: the lethal shapes of every worm in the level of each operator who switched the drawing on, as particles only that operator sees. */
    void drawDebug() {
        if (debug.isEmpty() || ++debugTick % 2 != 0) return;
        for (UUID id : new ArrayList<>(debug)) {
            ServerPlayer p = a.player(id);
            if (p == null) { debug.remove(id); continue; }
            for (State st : states.values()) {
                if (st.hazard == null || st.worm.isRemoved() || st.worm.level() != p.level()) continue;
                if (st.worm.distanceToSqr(p) > 160.0 * 160.0) continue;
                draw(p, st.hazard, MoveFactory.tau(WormSpec.parse(st.spec), st.worm.tickCount) + st.hazard.settings.timeOffset());
            }
        }
    }

    private void draw(ServerPlayer p, WormHazard hz, double tau) {
        ServerLevel level = p.level();
        WormHazard.Pose ps = hz.poseAt(tau);
        double skin = hz.settings.skin();
        int ring = 0;
        for (int k = 0; k < ps.drawn.length; k++) {
            if (!ps.drawn[k]) continue;
            double[] c = ps.centre[k];
            put(level, p, hz, ParticleTypes.END_ROD, c[0], c[1], c[2]);   // the body axis
            if (ring++ % 4 != 0 || k + 1 >= ps.centre.length) continue;
            double[] d = {ps.centre[k + 1][0] - c[0], ps.centre[k + 1][1] - c[1], ps.centre[k + 1][2] - c[2]};
            double dl = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
            if (dl < 1e-9) continue;
            d[0] /= dl; d[1] /= dl; d[2] /= dl;
            double[] u = Math.abs(d[1]) < 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0};
            double[] s1 = cross(d, u), s2;
            double l1 = Math.sqrt(s1[0] * s1[0] + s1[1] * s1[1] + s1[2] * s1[2]);
            s1 = new double[]{s1[0] / l1, s1[1] / l1, s1[2] / l1};
            s2 = cross(d, s1);
            for (int i = 0; i < 16; i++) {
                double an = 2 * Math.PI * i / 16, ca = Math.cos(an), sa = Math.sin(an);
                for (int sk = 0; sk < 2; sk++) {
                    double r = ps.radius[k] + (sk == 1 ? skin : 0);
                    put(level, p, hz, sk == 1 ? RED : ParticleTypes.END_ROD, c[0] + r * (ca * s1[0] + sa * s2[0]), c[1] + r * (ca * s1[1] + sa * s2[1]), c[2] + r * (ca * s1[2] + sa * s2[2]));
                }
            }
        }
        // the head: the outline of every flap (hinge arc, the two ridges to the apex)
        for (int j = 0; j < ps.flaps.arc.length; j++) {
            double[][] arc = ps.flaps.arc[j];
            double[] ap = ps.flaps.apex[j];
            for (double[] v : arc) putLocal(level, p, hz, ps, ParticleTypes.END_ROD, v);
            for (int side = 0; side < 2; side++) line(level, p, hz, ps, ParticleTypes.END_ROD, arc[side == 0 ? 0 : arc.length - 1], ap, 8);
            double[] nr = ps.flaps.normal[j][ps.flaps.normal[j].length / 2];
            putLocal(level, p, hz, ps, RED, new double[]{ap[0] + nr[0] * skin, ap[1] + nr[1] * skin, ap[2] + nr[2] * skin});
        }
        // the throat: the rim, four lines to the apex, and the contact skin over the rim disc in red
        double R = hz.R;
        double[] apex = {0, 0, com.paulorchard.islandcraft.wormsofarrakis.geometry.HeadMouth.THROAT_APEX_R * R};
        for (int i = 0; i < 32; i++) {
            double an = 2 * Math.PI * i / 32;
            putLocal(level, p, hz, ps, ParticleTypes.END_ROD, new double[]{R * Math.sin(an), R * Math.cos(an), 0});
            putLocal(level, p, hz, ps, RED, new double[]{R * Math.sin(an), R * Math.cos(an), hz.settings.throatReach()});
        }
        for (int i = 0; i < 4; i++) {
            double an = Math.PI / 2 * i;
            line(level, p, hz, ps, ParticleTypes.END_ROD, new double[]{R * Math.sin(an), R * Math.cos(an), 0}, apex, 8);
        }
    }

    private static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }

    private void line(ServerLevel level, ServerPlayer p, WormHazard hz, WormHazard.Pose ps, net.minecraft.core.particles.ParticleOptions o, double[] a, double[] b, int n) {
        for (int i = 0; i <= n; i++) {
            double f = (double) i / n;
            putLocal(level, p, hz, ps, o, new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f});
        }
    }

    private void putLocal(ServerLevel level, ServerPlayer p, WormHazard hz, WormHazard.Pose ps, net.minecraft.core.particles.ParticleOptions o, double[] local) {
        double[] m = WormHazard.localToModel(ps, local);
        put(level, p, hz, o, m[0], m[1], m[2]);
    }

    private void put(ServerLevel level, ServerPlayer p, WormHazard hz, net.minecraft.core.particles.ParticleOptions o, double mx, double my, double mz) {
        double[] w = hz.toWorld(mx, my, mz);
        level.sendParticles(p, o, true, true, w[0], w[1], w[2], 1, 0, 0, 0, 0);
    }

    // ---- the numbers ----

    public String status() {
        return String.format(Locale.ROOT, "hazard: %d worm(s) armed, %d ticks run (%.1f us each), %d entities tested (peak %d in one tick), %d kills", states.size(), ticksRun, ticksRun == 0 ? 0.0 : nanosTotal / 1000.0 / ticksRun, entitiesTested, entitiesPeak, kills);
    }

    /** The debug status and the smoke start. */
    public List<String> lines() { return List.of(status()); }
}
