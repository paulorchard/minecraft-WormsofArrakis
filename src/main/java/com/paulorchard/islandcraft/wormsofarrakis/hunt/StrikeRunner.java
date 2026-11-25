package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
import com.paulorchard.islandcraft.wormsofarrakis.hazard.HazardRules;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveFactory;
import com.paulorchard.islandcraft.wormsofarrakis.test.TestWormEntity;
import com.paulorchard.islandcraft.wormsofarrakis.test.WormSpawner;
import com.paulorchard.islandcraft.wormsofarrakis.test.WormSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Prompt 14 parts 2 to 4: the strike of a hunt event. During the last quarter of the freeze it reads the columns round the victim (a few at a time), at the end of the freeze it chooses
 * the type, places the move of the preset so that its entry crossing is at the victim's feet, kills the victim when the mouth encloses them, and waits for the move to finish.
 * All rules (corridor, disc, odds, alignment, devour time) are the engine-free classes; this class only reads blocks and runs the effects.
 */
final class StrikeRunner {
    private static final double SCAN_START = 0.75;
    /** A scan made this close to the victim's final spot is reused (blocks); further, the columns are read again at once. */
    static final double REUSE_TOLERANCE = 1.0;
    /** The safety net after the move's own end (seconds): the event ends even if the worm entity never reports its removal. */
    static final double END_MARGIN = 3.0;

    static final ResourceKey<DamageType> SWALLOWED = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(WormsOfArrakis.MOD_ID, "swallowed"));

    record Forced(StrikeType type, boolean force) {}

    record Plan(Moves.Move move, WormSpec spec, double entryZ, double playTime, double r0) {}

    /** The scan of the columns round the victim, spread over ticks. */
    private static final class Scan {
        final ScanTerrain terrain;
        final double x, z;
        final int groundY;
        final ServerLevel level;
        StrikeChoice.Availability result;
        int ticks;

        Scan(ServerLevel level, Corridor.Terrain src, double x, double z, int groundY) {
            this.level = level;
            this.terrain = new ScanTerrain(src);
            this.x = x;
            this.z = z;
            this.groundY = groundY;
        }
    }

    /** One strike in progress. */
    static final class Run {
        final HuntEvent event;
        final UUID victim;
        final StrikeType type;
        final DevourGate gate;
        final double devourSeconds, backupSeconds;
        TestWormEntity worm;
        boolean devoured;
        int ticks;
        ServerLevel level;

        Run(HuntEvent event, UUID victim, StrikeType type, double devourSeconds, double backupSeconds) {
            this.devourSeconds = devourSeconds;
            this.backupSeconds = backupSeconds;
            this.event = event;
            this.victim = victim;
            this.type = type;
            this.gate = new DevourGate(devourSeconds + backupSeconds);
        }
    }

    private final HuntAdapter a;
    private final Random rng = new Random();
    private final Map<String, Plan> plans = new HashMap<>();
    private final Map<Long, Scan> scans = new HashMap<>();
    private final Map<Long, Run> runs = new HashMap<>();
    private final Map<Long, Forced> forced = new HashMap<>();
    private boolean damageTypeWarned;

    StrikeRunner(HuntAdapter a) { this.a = a; }

    // ---- the plan of a move (built once) ----

    Plan plan(StrikeType t, SizeTable.Size s) {
        return plans.computeIfAbsent(t.label + "/" + s.label, k -> {
            WormSpec sp = Alignment.once(t, s);
            Moves.Move m = MoveFactory.get(sp);
            return new Plan(m, sp, Alignment.entryZ(m), m.playTime(), m.r0);
        });
    }

    /** Builds every move once at the start of the server and logs the derived devour times next to the configured ones. */
    void warmUp() {
        long t0 = System.nanoTime();
        for (StrikeType t : StrikeType.values()) {
            StringBuilder b = new StringBuilder();
            for (SizeTable.Size s : SizeTable.Size.values()) {
                Plan p = plan(t, s);
                b.append(String.format(Locale.ROOT, " %s: entryZ=%.2f play=%.2f s devour derived %.2f / config %.2f;", s.label, p.entryZ(), p.playTime(), Alignment.devourSeconds(t, p.move()), a.config().devourSeconds[t.ordinal()][s.ordinal()]));
            }
            WormsOfArrakis.LOGGER.info("[WormsOfArrakis] strike: {} plans (entry crossing in model z, run time, devour seconds derived from the move's frames vs the config table):{}", t.label, b);
        }
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] strike: 16 moves built in {} ms", (System.nanoTime() - t0) / 1_000_000);
        checkDamageType();
    }

    /** Start-up check: the damage type and its bypass tags are loaded (a totem of undying does not save a victim whose damage source is in bypasses_invulnerability). */
    void checkDamageType() {
        var server = a.server();
        if (server == null) return;
        try {
            DamageSource src = swallowed(server.overworld());
            WormsOfArrakis.LOGGER.info("[WormsOfArrakis] strike: damage type {} loaded; message id {}; bypasses_invulnerability={} (totem of undying does not save) bypasses_effects={} (absorption does not save) bypasses_armor={} bypasses_resistance={} bypasses_enchantments={}",
                    SWALLOWED.identifier(), src.getMsgId(), src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY), src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_EFFECTS), src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR),
                    src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_RESISTANCE), src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ENCHANTMENTS));
        } catch (RuntimeException ex) {
            WormsOfArrakis.LOGGER.warn("[WormsOfArrakis] strike: the damage type check failed: {}", ex.toString());
        }
    }

    // ---- the terrain ----

    private Corridor.Terrain terrainOf(ServerLevel level) {
        return (x, z) -> {
            if (!level.hasChunkAt(new BlockPos(x, level.getMinY(), z))) return new Corridor.Top(Corridor.Kind.UNLOADED, 0);
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);   // the y of the first free block above the top block
            int y = h - 1;
            if (y < level.getMinY()) return new Corridor.Top(Corridor.Kind.OTHER, y);
            BlockState st = level.getBlockState(new BlockPos(x, y, z));
            return new Corridor.Top(a.isSand(st) ? Corridor.Kind.SAND : Corridor.Kind.OTHER, y);
        };
    }

    private int groundY(ServerPlayer p) {
        Integer sy = a.surfaceOf(p.getUUID());
        return sy != null && sy != SandProbe.NONE ? sy : (int) Math.floor(p.getY() - 0.01) - 1;
    }

    private StrikeChoice.Availability evaluate(Corridor.Terrain t, ServerPlayer p, double x, double z, int groundY, SizeTable.Size size) {
        return StrikeChoice.evaluate(t, x, z, groundY, size, a.config(), (int) Math.round(plan(StrikeType.CREST, size).r0()), plan(StrikeType.ENGULF, size).r0());
    }

    /** The availability at the player's place right now, read at once (the command that forces a type checks it). */
    StrikeChoice.Availability availabilityNow(ServerPlayer p, SizeTable.Size size) {
        a.stateNow(p);
        return evaluate(terrainOf(p.level()), p, p.getX(), p.getZ(), groundY(p), size);
    }

    void force(long eventId, StrikeType t, boolean force) { forced.put(eventId, new Forced(t, force)); }

    // ---- every tick ----

    void tick() {
        // the scan of the last quarter of the freeze: a few columns per tick, never blocking
        for (HuntEvent e : a.manager().events()) {
            if (e.phase() != HuntEvent.Phase.FREEZE || e.victim() == null) continue;
            ServerPlayer p = a.player(e.victim());
            if (p == null) continue;
            Scan s = scans.get(e.id);
            if (s == null && e.phaseTime() >= SCAN_START * e.attackSeconds - 1e-9) {
                s = new Scan(p.level(), terrainOf(p.level()), p.getX(), p.getZ(), groundY(p));
                scans.put(e.id, s);
                WormsOfArrakis.LOGGER.info("[WormsOfArrakis] strike: scan of the columns round {} starts ({} columns per tick at most, spread over the last quarter of the freeze)", e.victimName(), a.config().scanColumnsPerTick);
            }
            if (s != null && s.result == null) {
                s.ticks++;
                s.terrain.newTick(a.config().scanColumnsPerTick);
                final Scan sc = s;
                s.result = s.terrain.step(() -> evaluate(sc.terrain, p, sc.x, sc.z, sc.groundY, e.size));
                if (s.result != null) WormsOfArrakis.LOGGER.info("[WormsOfArrakis] strike: scan finished in {} tick(s), {} columns read, available={}", s.ticks, s.terrain.columns(), s.result.available.stream().map(t -> t.label).toList());
            }
        }
        // the strikes: the devour at its moment, the end with the move
        for (Run r : new ArrayList<>(runs.values())) {
            if (r.event.ended()) continue;
            r.ticks++;
            boolean gone = r.worm == null || r.worm.isRemoved();
            if (!r.devoured && !gone) {
                ServerPlayer vic = a.player(r.victim);
                DevourGate.Action act = r.gate.check(r.worm.tickCount, vic != null && vic.isAlive() && !vic.isCreative() && !vic.isSpectator());
                if (act == DevourGate.Action.KILL) devour(r, null, r.worm.tickCount / 20.0);
                else if (act == DevourGate.Action.SKIP) {
                    r.devoured = true;
                    r.event.markDevoured();
                    WormsOfArrakis.LOGGER.info("[WormsOfArrakis] strike: devour moment reached but the victim is gone, dead or no longer hunted; nobody is killed");
                }
            }
            if (gone) {
                if (!r.devoured) { WormsOfArrakis.LOGGER.info("[WormsOfArrakis] strike: the worm is gone before the devour moment (victim not killed)"); }
                WormsOfArrakis.LOGGER.info("[WormsOfArrakis] strike: the move has finished after {} ticks; the event ends", r.ticks + (r.event.killCause().isEmpty() ? "" : "; the victim died by " + r.event.killCause()));
                r.event.finishStrike();
            }
        }
    }

    // ---- the strike starts ----

    /** FREEZE is over: choose, place, run. */
    void onStrike(HuntEvent e) {
        ServerPlayer p = a.player(e.victim());
        if (p == null || !p.isAlive()) {
            WormsOfArrakis.LOGGER.info("[WormsOfArrakis] strike: the victim is gone at the end of the freeze; no strike (FIZZLED)");
            e.end(HuntEvent.EndReason.FIZZLED);
            return;
        }
        ServerLevel level = p.level();
        double x = p.getX(), z = p.getZ();
        int gy = groundY(p);
        Scan sc = scans.remove(e.id);
        StrikeChoice.Availability av;
        if (sc != null && sc.result != null && Math.hypot(sc.x - x, sc.z - z) <= REUSE_TOLERANCE && sc.groundY == gy) {
            av = sc.result;
            WormsOfArrakis.LOGGER.info("[WormsOfArrakis] strike: using the scan made during the freeze ({} columns)", sc.terrain.columns());
        } else {
            long t0 = System.nanoTime();
            av = evaluate(terrainOf(level), p, x, z, gy, e.size);
            WormsOfArrakis.LOGGER.info("[WormsOfArrakis] strike: {} so the columns are read now: {} columns in {} us", sc == null ? "no scan was finished" : "the victim moved from the scan", av.reads, (System.nanoTime() - t0) / 1000);
        }
        if (av.unloaded) WormsOfArrakis.LOGGER.info("[WormsOfArrakis] strike: some columns were in unloaded chunks; they count as failures (no chunk is loaded for the check)");
        for (StrikeType t : new StrikeType[]{StrikeType.CREST, StrikeType.ENGULF}) if (!av.has(t)) WormsOfArrakis.LOGGER.info("[WormsOfArrakis] strike: {} not available: {}", t.label, av.why(t));
        Forced f = forced.remove(e.id);
        if (f != null && !av.has(f.type())) WormsOfArrakis.LOGGER.info("[WormsOfArrakis] strike: forced {} is not available here now ({}); it runs because it was ordered{}", f.type().label, av.why(f.type()), f.force() ? " with force" : " when it was available");
        StrikeChoice.Choice c = StrikeChoice.choose(av, a.config(), rng, f == null ? null : f.type());
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] {}", StrikeChoice.line(e.size, av, a.config(), c));
        Plan plan = plan(c.type(), e.size);
        double[] o = Alignment.origin(x, z, plan.entryZ(), c.yawDeg());
        double baseY = gy + 1.0;   // the top face of the sand block under the victim is the move's sand level
        TestWormEntity worm = WormSpawner.spawn(level, plan.spec(), o[0], baseY, o[1], (float) c.yawDeg());
        if (worm == null) {
            WormsOfArrakis.LOGGER.warn("[WormsOfArrakis] strike: the worm could not be spawned; the event ends FIZZLED");
            e.end(HuntEvent.EndReason.FIZZLED);
            return;
        }
        double devour = a.config().devourSeconds[c.type().ordinal()][e.size.ordinal()];
        Run r = new Run(e, e.victim(), c.type(), devour, a.config().throatBackupSeconds);
        r.worm = worm;
        r.level = level;
        worm.markStrikeWorm();
        runs.put(e.id, r);
        e.setStrikeInfo(c.type().label, c.dir() == null ? "-" : c.dir().name());
        e.setStrikeLimit(plan.playTime() + MoveFactory.REMOVE_MARGIN + END_MARGIN);
        double[] at = Alignment.worldOf(o, plan.entryZ(), c.yawDeg());
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] strike: start {} W{} on {} at ({}, {}, {}) yaw {}: origin ({}, {}), entry crossing z={} at ({}, {}) = the victim's feet; devour at {} s; run {} s; the event waits for the worm to remove itself (limit {} s)",
                c.type().label, HuntAdapter.trim(e.width), e.victimName(), HuntAdapter.fmt(x), HuntAdapter.fmt(baseY), HuntAdapter.fmt(z), HuntAdapter.fmt(c.yawDeg()), HuntAdapter.fmt(o[0]), HuntAdapter.fmt(o[1]), HuntAdapter.fmt(plan.entryZ()),
                HuntAdapter.fmt(at[0]), HuntAdapter.fmt(at[1]), HuntAdapter.fmt(devour), HuntAdapter.fmt(plan.playTime()), HuntAdapter.fmt(plan.playTime() + MoveFactory.REMOVE_MARGIN + END_MARGIN));
        for (ServerPlayer other : level.players()) {
            if (other.getUUID().equals(e.victim())) continue;
            double d = Math.hypot(other.getX() - x, other.getZ() - z);
            if (d <= e.width) WormsOfArrakis.LOGGER.info("[WormsOfArrakis] strike: {} stands {} blocks from the victim, within one worm width ({}) of the strike; nothing special is done", other.getGameProfile().name(), HuntAdapter.fmt(d), HuntAdapter.trim(e.width));
        }
    }

    // ---- the devour ----

    DamageSource swallowed(ServerLevel level) {
        try {
            Holder<DamageType> h = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(SWALLOWED);
            return new DamageSource(h);
        } catch (RuntimeException ex) {
            if (!damageTypeWarned) { damageTypeWarned = true; WormsOfArrakis.LOGGER.warn("[WormsOfArrakis] strike: damage type {} is missing, the generic kill is used (it also bypasses totems)", SWALLOWED.identifier()); }
            return level.damageSources().genericKill();
        }
    }

    /** The strike of a worm entity of the hazard (null when it is not a strike worm of a running event). */
    Run runOfWorm(TestWormEntity w) {
        for (Run r : runs.values()) if (r.worm == w) return r;
        return null;
    }

    /** The worm touched the victim: the first touch of any part kills them, at the substep in which it happened. */
    void devourByTouch(Run r, ServerPlayer v, String part, double wormSeconds) {
        if (r.devoured) return;
        devour(r, part, wormSeconds);
    }

    /**
     * The victim dies: by the first touch of a part of the worm ({@code part} = body, head or throat) or, when the worm never touched them by DevourSeconds + ThroatBackupSeconds, by the backup
     * ({@code part} = null). The kill itself is the swallow of prompt 14: invisible, damage type swallowed, die if still alive.
     */
    private void devour(Run r, String part, double wormSeconds) {
        r.devoured = true;
        r.gate.touched();      // no backup after a touch (or after the backup itself)
        r.event.markDevoured();   // before the death, so the victim leaving the list is the devour and not a cancel
        ServerPlayer v = a.player(r.victim);
        if (v == null || !v.isAlive() || v.isCreative() || v.isSpectator()) {
            WormsOfArrakis.LOGGER.info("[WormsOfArrakis] strike: contact or backup moment reached but the victim is gone, dead or no longer hunted; nobody is killed");
            return;
        }
        ServerLevel level = v.level();
        String who = v.getGameProfile().name();
        String cause = part == null ? "backup" : "touch:" + part;
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] {}", part == null ? HazardRules.backupLine(who, wormSeconds, r.devourSeconds, r.backupSeconds) : HazardRules.killLine(who, part, wormSeconds, r.devourSeconds, "victim"));
        r.event.setKillCause(cause + String.format(Locale.ROOT, " at %.2f s (devour table %.2f s)", wormSeconds, r.devourSeconds));
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] strike: devour {} at {} s after the move started by {}: {} is swallowed (hidden, then killed by {}, which bypasses totems, absorption and armour)", r.type.label, HuntAdapter.fmt(wormSeconds), part == null ? "the backup (no touch before the devour time plus the backup)" : "contact with the " + part, who, SWALLOWED.identifier());
        HazardRunner hz = HazardRunner.instance();
        boolean dead = hz != null ? hz.kill(level, v) : false;
        if (!dead) WormsOfArrakis.LOGGER.warn("[WormsOfArrakis] strike: {} could not be killed (damage and death were both refused)", who);
        a.stopAttackRumble(r.event, "the victim is dead (devoured by " + cause + ")");
        a.releaseFreezeOf(r.event);   // the victim is dead: nothing of the freeze stays
        if (dead && hz != null) hz.victimKilled(level, v, r.worm, wormSeconds);
    }

    // ---- the end ----

    /** The event ended: every strike object is cleaned up, whatever the reason. */
    void onEnd(HuntEvent e, HuntEvent.EndReason reason) {
        scans.remove(e.id);
        forced.remove(e.id);
        Run r = runs.remove(e.id);
        if (r != null && r.worm != null && !r.worm.isRemoved()) {
            r.worm.discard();
            WormsOfArrakis.LOGGER.info("[WormsOfArrakis] strike: the worm was still there at the end ({}); removed", reason);
        }
    }

    /** True while a strike of this event is running (for the status command). */
    boolean running(long eventId) { return runs.containsKey(eventId); }
}
