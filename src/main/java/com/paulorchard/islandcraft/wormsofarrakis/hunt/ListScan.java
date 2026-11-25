package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Prompt 13 part 3: the roster of an event, engine-free. Everyone within the radius (horizontal) who is eligible, on sand now and in no event; the trigger first. */
public final class ListScan {
    private ListScan() {}

    public record Candidate(UUID id, String dim, double x, double z, boolean onSand, boolean inEvent, boolean eligible) {}

    public static List<UUID> scan(Candidate trigger, Collection<Candidate> all, double radius) {
        List<UUID> l = new ArrayList<>();
        l.add(trigger.id());
        for (Candidate c : all) {
            if (c.id().equals(trigger.id())) continue;
            if (!c.dim().equals(trigger.dim())) continue;
            if (!c.eligible() || !c.onSand() || c.inEvent()) continue;
            if (Math.hypot(c.x() - trigger.x(), c.z() - trigger.z()) > radius) continue;
            l.add(c.id());
        }
        return l;
    }
}
