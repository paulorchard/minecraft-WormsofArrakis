package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Prompt 17 parts 3 and 4: who is in the strike group and who is let go at the choice, engine-free. The group is built once at the start of the freeze: the victim always, and every other
 * player within the radius (horizontal) of the victim, in the same dimension, alive, in Survival or Adventure, on Dune Sand and not already held by another event. No cap. Never extended.
 */
public final class StrikeGroup {
    private StrikeGroup() {}

    /** What the selection needs to know about one player (filled by the adapter). */
    public record Cand(UUID id, String name, double x, double z, String dim, boolean alive, boolean survivalOrAdventure, boolean onSand, long heldByEvent) {
        public static final long FREE = -1;
    }

    /** The result: the members (victim first) and the reasons for the others that were inside the radius and skipped. */
    public record Selection(List<UUID> members, Map<UUID, String> skipped) {}

    /** {@code radius} is the StrikeArea maximum of the size; only players inside it get a reason when skipped (the others are simply far away). */
    public static Selection select(Cand victim, List<Cand> all, double radius) {
        List<UUID> members = new ArrayList<>();
        Map<UUID, String> skipped = new LinkedHashMap<>();
        members.add(victim.id());
        for (Cand c : all) {
            if (c.id().equals(victim.id())) continue;
            if (!c.dim().equals(victim.dim())) continue;
            double d = Math.hypot(c.x() - victim.x(), c.z() - victim.z());
            if (d > radius) continue;
            String why = null;
            if (!c.alive()) why = "dead";
            else if (!c.survivalOrAdventure()) why = "not in Survival or Adventure";
            else if (!c.onSand()) why = "not on Dune Sand";
            else if (c.heldByEvent() != Cand.FREE) why = "held by event " + c.heldByEvent();
            if (why == null) members.add(c.id()); else skipped.put(c.id(), why);
        }
        return new Selection(members, skipped);
    }

    /** The members to release at the choice: those whose held spot is outside the circle of the chosen move (the victim never). Empty when {@code releaseOutside} is false. */
    public static List<UUID> releasedAtChoice(UUID victim, Map<UUID, double[]> heldSpots, double cx, double cz, double radius, boolean releaseOutside) {
        List<UUID> out = new ArrayList<>();
        if (!releaseOutside) return out;
        for (Map.Entry<UUID, double[]> e : heldSpots.entrySet()) {
            if (e.getKey().equals(victim)) continue;
            if (Math.hypot(e.getValue()[0] - cx, e.getValue()[1] - cz) > radius) out.add(e.getKey());
        }
        return out;
    }
}
