package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import com.paulorchard.islandcraft.wormsofarrakis.camera.DeathCamManager;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Prompt 17 part 2: {@code hunt preview area}: the strike area drawn as a ring of particles round the caller (only they see it), every 2 ticks for about 10 s. */
final class AreaPreview {
    static final int SECONDS = 10;
    private static final int[] COLORS = {0xFF3030, 0x30FF30, 0x3060FF, 0xFFFF30};   // crest, crash, emerge, engulf

    private record Ring(double radius, ParticleOptions particle) {}

    private static final class Draw {
        final UUID player;
        final ServerLevel level;
        final double cx, cz, y;
        final List<Ring> rings;
        int ticks;

        Draw(ServerPlayer p, List<Ring> rings) {
            this.player = p.getUUID();
            this.level = p.level();
            this.cx = p.getX();
            this.cz = p.getZ();
            this.y = p.getY();
            this.rings = rings;
        }
    }

    private final HuntAdapter a;
    private final List<Draw> draws = new ArrayList<>();

    AreaPreview(HuntAdapter a) { this.a = a; }

    /** Starts the drawing and returns the radii it draws, as text lines for the chat. {@code move} null = the four moves in four colours and the maximum; "max" = the maximum only. */
    List<String> start(ServerPlayer p, SizeTable.Size size, String move) {
        List<Ring> rings = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        draws.removeIf(d -> d.player.equals(p.getUUID()));
        if (move == null) {
            for (StrikeType t : StrikeType.values()) {
                double r = a.strikeAreaRadius(t, size);
                rings.add(new Ring(r, new DustParticleOptions(COLORS[t.ordinal()], 1.4f)));
                lines.add(t.label + " " + size.label + ": radius " + HuntAdapter.fmt(r) + " blocks (ring colour " + new String[]{"red", "green", "blue", "yellow"}[t.ordinal()] + ")");
            }
            double m = a.maxStrikeArea(size);
            rings.add(new Ring(m, ParticleTypes.END_ROD));
            lines.add("maximum (the group at the start of the freeze) " + size.label + ": radius " + HuntAdapter.fmt(m) + " blocks (white)");
        } else if (move.equals("max")) {
            double m = a.maxStrikeArea(size);
            rings.add(new Ring(m, ParticleTypes.END_ROD));
            lines.add("maximum " + size.label + ": radius " + HuntAdapter.fmt(m) + " blocks");
        } else {
            StrikeType t = StrikeType.of(move);
            double r = a.strikeAreaRadius(t, size);
            rings.add(new Ring(r, ParticleTypes.END_ROD));
            lines.add(t.label + " " + size.label + ": radius " + HuntAdapter.fmt(r) + " blocks");
        }
        draws.add(new Draw(p, rings));
        return lines;
    }

    void tick() {
        if (draws.isEmpty()) return;
        for (var it = draws.iterator(); it.hasNext(); ) {
            Draw d = it.next();
            ServerPlayer p = a.player(d.player);
            if (p == null || p.level() != d.level || ++d.ticks > SECONDS * 20) { it.remove(); continue; }
            if (d.ticks % 2 != 0) continue;
            for (Ring r : d.rings) {
                int n = (int) Math.max(24, Math.min(120, Math.round(2 * Math.PI * r.radius() / 1.5)));
                double phase = d.ticks * 0.013;
                for (int i = 0; i < n; i++) {
                    double an = 2 * Math.PI * i / n + phase;
                    double x = d.cx + r.radius() * Math.cos(an), z = d.cz + r.radius() * Math.sin(an);
                    double g = DeathCamManager.groundAt(d.level, x, z);
                    double y = Double.isNaN(g) ? d.y + 0.2 : g + 0.2;
                    d.level.sendParticles(p, r.particle(), true, true, x, y, z, 1, 0, 0, 0, 0);
                }
            }
        }
    }
}
