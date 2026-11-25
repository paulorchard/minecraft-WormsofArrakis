package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
import com.paulorchard.islandcraft.wormsofarrakis.camera.DeathCamManager;
import com.paulorchard.islandcraft.wormsofarrakis.hazard.Box;
import com.paulorchard.islandcraft.wormsofarrakis.hazard.WormHazard;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveFactory;
import com.paulorchard.islandcraft.wormsofarrakis.test.TestWormEntity;
import com.paulorchard.islandcraft.wormsofarrakis.test.WormSpawner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Prompt 18: {@code camera preview <size> <move>}: the real move plays at the caller's feet (a harmless test worm, loop off) and the strike camera starts the moment the caller's box crosses the mouth
 * disc, so the whole thing can be seen without dying. The caller is not frozen and is not swallowed; nothing acts on them.
 */
final class CameraMovePreview {
    private static final class Run {
        UUID player;
        ServerLevel level;
        TestWormEntity worm;
        WormHazard hz;
        double playSeconds;
        boolean started;
        StrikeType type;
        SizeTable.Size size;
    }

    private final HuntAdapter a;
    private final List<Run> runs = new ArrayList<>();

    CameraMovePreview(HuntAdapter a) { this.a = a; }

    List<String> start(ServerPlayer p, SizeTable.Size size, StrikeType type) {
        for (Run r : runs) if (r.player.equals(p.getUUID()) && !r.worm.isRemoved()) return List.of("REFUSED: your preview worm is still playing; wait for it or use camera stop");
        if (!a.onSandNow(p) && a.config().hazardPlayersImmuneOffSand) return List.of("REFUSED: stand on Dune Sand: off sand the discs do not act on you, so the camera would never start (sand-only immunity)");
        StrikeRunner.Plan plan = a.strikes().plan(type, size);
        ServerLevel level = p.level();
        double yaw = p.getYRot();
        double[] o = Alignment.origin(p.getX(), p.getZ(), plan.entryZ(), yaw);
        TestWormEntity worm = WormSpawner.spawn(level, plan.spec(), o[0], p.getY(), o[1], (float) yaw);
        if (worm == null) return List.of("REFUSED: the worm could not be spawned here");
        worm.previewWorm = true;
        Run r = new Run();
        r.player = p.getUUID();
        r.level = level;
        r.worm = worm;
        r.hz = new WormHazard(plan.spec(), worm.getX(), worm.getY(), worm.getZ(), worm.getYRot(), a.hazard().settings());
        r.hz.discs = a.config().discSpec();
        r.playSeconds = plan.playTime();
        r.type = type;
        r.size = size;
        runs.add(r);
        WormsOfArrakis.LOGGER.info("[WormsOfArrakis] camera: preview of the real {} {} for {}: worm {} at ({}, {}, {}), run {} s; the camera starts when their box crosses the mouth disc; harmless", type.label, size.label, p.getGameProfile().name(), worm.getId(), HuntAdapter.fmt(o[0]), HuntAdapter.fmt(p.getY()), HuntAdapter.fmt(o[1]), HuntAdapter.fmt(plan.playTime()));
        return List.of("the " + type.label + " " + size.label + " plays at your feet (run " + HuntAdapter.fmt(plan.playTime()) + " s); stand still: the camera starts when you are inside the mouth; you are not frozen, not swallowed and not hurt");
    }

    void tick() {
        if (runs.isEmpty()) return;
        for (var it = runs.iterator(); it.hasNext(); ) {
            Run r = it.next();
            ServerPlayer p = a.player(r.player);
            if (p == null || p.level() != r.level || r.worm.isRemoved()) { it.remove(); continue; }
            if (r.started) continue;
            int tk = r.worm.tickCount;
            double tn = MoveFactory.tau(r.hz.spec, tk) + r.hz.settings.timeOffset(), tp = MoveFactory.tau(r.hz.spec, Math.max(0, tk - 1)) + r.hz.settings.timeOffset();
            var bb = p.getBoundingBox();
            Box box = new Box(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ);
            if (r.hz.discContact(box, tp - r.hz.settings.timeOffset(), tn - r.hz.settings.timeOffset(), r.hz.mouthDisc()) == null) continue;
            r.started = true;
            double remaining = Math.max(1.0, r.playSeconds - tk / 20.0);
            List<String> notes = new ArrayList<>();
            var s = DeathCamManager.instance().start(p, p.getX(), p.getY(), p.getZ(), r.worm.getYRot(), r.hz.R, r.hz.headLength(), a.camPeak(r.type, r.size), System.nanoTime(), remaining,
                    new WormSurface(r.worm, r.hz), -1, true, notes);
            for (String n : notes) WormsOfArrakis.LOGGER.info("[WormsOfArrakis] camera: {}", n);
            WormsOfArrakis.LOGGER.info("[WormsOfArrakis] camera: preview camera {} for {} at t={} s after the move started (mouth disc crossed)", s == null ? "could not start" : "started", p.getGameProfile().name(), HuntAdapter.fmt(tk / 20.0));
        }
    }

}
