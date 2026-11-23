package com.paulorchard.islandcraft.wormsofarrakis.test;

import com.paulorchard.islandcraft.wormsofarrakis.path.HeadSpec;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;
import com.paulorchard.islandcraft.wormsofarrakis.path.PlanarPath;

import java.util.HashMap;
import java.util.Map;

/**
 * Turns a "move" {@link WormSpec} into a {@link Moves.Move} (shared by the server command and the client renderer; no
 * Minecraft classes). Spec layout (kind "move", mode crest|emerge|crash):
 * <pre>
 * p0 width  p1 segments  p2 crest_fraction | n  p3 angle  p4 speed  p5 hold  p6 gravity  p7 top_speed
 * p8 mouth open (1/0)  p9 ballistic (1/0)  p10 force (1/0)  p11 time ratio  p12 age at last speed change (ticks)
 * p13 script time at that moment (s)  p14 under_radius_widths  p15 top_radius_factor  p16 liner (1 tiled, 2 dark, 0 off)
 * </pre>
 * The script time is tau = p13 + p11 * (age - p12) / 20, so the speed command changes p11/p12/p13 without a jump.
 */
public final class MoveFactory {
    private static final Map<String, Moves.Move> CACHE = new HashMap<>();

    private MoveFactory() {}

    public static WormSpec spec(String mode, double width, int segments, double p2, double angle, double speed, double hold, double gravity,
                                double topSpeed, boolean mouthOpen, boolean ballistic, boolean force, double underRadiusWidths,
                                double topRadiusFactor, double liner) {
        return WormSpec.of("move", mode, width, segments, p2, angle, speed, hold, gravity, topSpeed, mouthOpen ? 1 : 0, ballistic ? 1 : 0,
                force ? 1 : 0, 1, 0, 0, underRadiusWidths, topRadiusFactor, liner);
    }

    /** @throws Moves.Rejected when the move cannot satisfy the curvature rule (and force is off) */
    public static Moves.Move build(WormSpec s) {
        double width = s.p(0, 16);
        int segs = (int) s.p(1, 60);
        boolean force = s.p(10, 0) > 0;
        return switch (s.mode()) {
            case "crest" -> Moves.crest(width, segs, s.p(2, 0.4), s.p(4, 8), 1.0, force);
            case "emerge" -> Moves.emerge(width, segs, (int) s.p(2, 10), s.p(3, 90), s.p(4, 12), s.p(5, 2), s.p(14, 1.0), force);
            case "crash" -> Moves.crash(width, segs, (int) s.p(2, 10), s.p(3, 70), s.p(4, 12), s.p(6, 12), s.p(7, 4), s.p(15, 1.0), s.p(14, 1.0),
                    s.p(9, 1) > 0, force);
            default -> throw new Moves.Rejected("unknown move " + s.mode());
        };
    }

    /** Move for a spec, cached by everything except the time fields. */
    public static synchronized Moves.Move get(WormSpec s) {
        double[] q = s.p().clone();
        for (int i : new int[]{8, 11, 12, 13, 16}) if (i < q.length) q[i] = 0;
        String key = new WormSpec(s.kind(), s.mode(), q).serialize();
        Moves.Move m = CACHE.get(key);
        if (m == null) {
            if (CACHE.size() > 32) CACHE.clear();
            m = build(s);
            CACHE.put(key, m);
        }
        return m;
    }

    /** Script time in seconds for an entity of the given age (ticks, with partial tick). */
    public static double tau(WormSpec s, double age) {
        return s.p(13, 0) + s.p(11, 1) * (age - s.p(12, 0)) / 20.0;
    }

    /** Loose half extent (blocks) of everything the move draws above the sand, measured from the entity position. */
    public static double reach(WormSpec s) {
        Moves.Move m = get(s);
        PlanarPath p = m.path;
        double max = 0;
        for (double a = m.s0; a <= m.sEnd + HeadSpec.headLength(m.width); a += 1.0) {
            double[] q = p.pos(a);
            if (q[1] + m.r0 >= -2) max = Math.max(max, Math.abs(q[0]));
        }
        return max + HeadSpec.headLength(m.width) + m.r0 * 2 + 4;
    }

    /** Highest point of the path plus the head and the ring. */
    public static double height(WormSpec s) {
        Moves.Move m = get(s);
        return m.maxPathHeight() + HeadSpec.headLength(m.width) + m.r0 + 4;
    }

    /** Middle (model z) of the part of the path that is above the sand, used to put the exposed part in front of the player. */
    public static double centreZ(WormSpec s) {
        Moves.Move m = get(s);
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (double a = m.s0; a <= m.sEnd; a += 0.5) {
            double[] q = m.path.pos(a);
            if (q[1] + m.r0 >= -1) { lo = Math.min(lo, q[0]); hi = Math.max(hi, q[0]); }
        }
        return lo > hi ? 0 : 0.5 * (lo + hi);
    }
}
