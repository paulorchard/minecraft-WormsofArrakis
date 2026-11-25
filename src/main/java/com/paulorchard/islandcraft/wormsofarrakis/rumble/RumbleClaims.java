package com.paulorchard.islandcraft.wormsofarrakis.rumble;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Prompt 15 part 2: the claim rule. Several rumbles may claim one column; the column shows the state of the newest claim (the last to claim), and the real block is restored
 * only when no event claims the column any more. Engine-free.
 */
public final class RumbleClaims {
    private final Map<Long, List<Long>> claims = new HashMap<>();

    public void claim(long column, long event) {
        List<Long> l = claims.computeIfAbsent(column, k -> new ArrayList<>(2));
        l.remove(event);
        l.add(event);
    }

    /** Releases the claim; true when no event claims the column any more (the real block returns). */
    public boolean release(long column, long event) {
        List<Long> l = claims.get(column);
        if (l == null) return true;
        l.remove(event);
        if (l.isEmpty()) { claims.remove(column); return true; }
        return false;
    }

    /** The newest claim, or -1. */
    public long winner(long column) {
        List<Long> l = claims.get(column);
        return l == null || l.isEmpty() ? -1 : l.get(l.size() - 1);
    }

    /** All claims, oldest first (a copy view; do not modify). */
    public List<Long> claimants(long column) { return claims.getOrDefault(column, List.of()); }

    public boolean claimed(long column) { return claims.containsKey(column); }
    public int columns() { return claims.size(); }
    public boolean isEmpty() { return claims.isEmpty(); }
}
