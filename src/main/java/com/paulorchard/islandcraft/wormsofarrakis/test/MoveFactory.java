package com.paulorchard.islandcraft.wormsofarrakis.test;

import com.paulorchard.islandcraft.wormsofarrakis.fx.FxOptions;
import com.paulorchard.islandcraft.wormsofarrakis.path.HeadSpec;
import com.paulorchard.islandcraft.wormsofarrakis.path.MouthAnim;
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
 * p17 freeze t (-1 off)  p18 pause s (-1 = the move's default)  p19 loop (1 on, 0 off)  p20 retreat speed (0 = same as speed)
 * </pre>
 * Modes: crest, emerge, fall (the crash command), engulf, proj (crashproj), crash (crasharc). Engulf reads p2 exposed, p3 lean, p4 speed, p5 hold, p6 gravity.
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

    /**
     * Prompt 07: the spec with the extra variables. p17 freeze (-1), p18 pause seconds (-1 = the move's own default), p19 loop (1 on, 0 off),
     * p20 retreat speed (0 = same as speed). When all three are at their defaults the spec is exactly the older, shorter one.
     */
    public static WormSpec specFull(String mode, double width, int segments, double p2, double angle, double speed, double hold, double gravity,
                                    double topSpeed, boolean mouthOpen, boolean ballistic, boolean force, double underRadiusWidths,
                                    double topRadiusFactor, double liner, double pause, boolean loop, double retreatSpeed) {
        WormSpec base = spec(mode, width, segments, p2, angle, speed, hold, gravity, topSpeed, mouthOpen, ballistic, force, underRadiusWidths, topRadiusFactor, liner);
        if (pause < 0 && loop && retreatSpeed <= 0) return base;
        double[] p = java.util.Arrays.copyOf(base.p(), 21);
        p[17] = -1;
        p[18] = pause;
        p[19] = loop ? 1 : 0;
        p[20] = retreatSpeed;
        return new WormSpec(base.kind(), base.mode(), p);
    }

    /** Mouth modes of p8: 0 static closed, 1 static open, 2 the closing animation (prompt 08). */
    public static final int MOUTH_CLOSED = 0, MOUTH_OPEN = 1, MOUTH_ANIM = 2;

    /**
     * Prompt 08: the mouth variables on top of a spec. p8 mode, p21 close_time, p22 close_end (-999 = the move's default), p23 open_deg,
     * p24 teeth (1/0), p25 mouthpose (-1 off, else the held fraction 0..1 of the closing). A spec whose mouth keys are all at their
     * defaults keeps the shorter older length (only p8 changes).
     */
    public static WormSpec withMouth(WormSpec base, int mode, double closeTime, double closeEnd, double openDeg, boolean teeth) {
        double[] p = java.util.Arrays.copyOf(base.p(), Math.max(base.p().length, 17));
        p[8] = mode;
        boolean dflt = closeTime == MouthAnim.DEFAULT_CLOSE_TIME && closeEnd == MouthAnim.CLOSE_END_DEFAULT_MARK && openDeg == HeadSpec.OPEN_DEGREES && teeth;
        if (dflt) return new WormSpec(base.kind(), base.mode(), p);
        p = java.util.Arrays.copyOf(p, Math.max(26, p.length));   // never shorter: the fx fields (p26..p30) may follow
        if (base.p().length <= 17) p[17] = -1;
        if (base.p().length <= 18) p[18] = -1;
        if (base.p().length <= 19) p[19] = 1;
        p[21] = closeTime;
        p[22] = closeEnd;
        p[23] = openDeg;
        p[24] = teeth ? 1 : 0;
        p[25] = -1;
        return new WormSpec(base.kind(), base.mode(), p);
    }

    /** The mouth pose command: holds the closing at a fraction (0 = open at open_deg, 1 = closed), or releases it (fraction < 0). */
    public static WormSpec withMouthPose(WormSpec s, double fraction) {
        double[] p = java.util.Arrays.copyOf(s.p(), Math.max(26, s.p().length));
        fillMouthDefaults(s, p);
        p[25] = fraction;
        return new WormSpec(s.kind(), s.mode(), p);
    }

    /** Puts the defaults into the p17..p25 slots that a shorter (older) spec did not have. */
    private static void fillMouthDefaults(WormSpec s, double[] p) {
        if (s.p().length <= 17) p[17] = -1;
        if (s.p().length <= 18) p[18] = -1;
        if (s.p().length <= 19) p[19] = 1;
        if (s.p().length <= 21) p[21] = MouthAnim.DEFAULT_CLOSE_TIME;
        if (s.p().length <= 22) p[22] = MouthAnim.CLOSE_END_DEFAULT_MARK;
        if (s.p().length <= 23) p[23] = HeadSpec.OPEN_DEGREES;
        if (s.p().length <= 24) p[24] = 1;
        if (s.p().length <= 25) p[25] = -1;
    }

    /**
     * Prompt 09: the effect keys on top of a spec: p26 fx (1/0), p27 fxsound, p28 fxparticles, p29 fxvol, p30 fxcount. A spec whose fx keys are all
     * at the defaults of its move (fx on for crash, emerge, engulf, off for crest) keeps its shorter older length.
     */
    public static WormSpec withFx(WormSpec base, FxOptions o) {
        if (o.isDefault(FxOptions.defaultOn(base.mode()))) return base;
        double[] p = java.util.Arrays.copyOf(base.p(), Math.max(31, base.p().length));
        fillMouthDefaults(base, p);
        p[26] = o.on() ? 1 : 0;
        p[27] = o.sound() ? 1 : 0;
        p[28] = o.particles() ? 1 : 0;
        p[29] = o.vol();
        p[30] = o.count();
        return new WormSpec(base.kind(), base.mode(), p);
    }

    /** The effect options of a spec (defaults when the spec is short). */
    public static FxOptions fxOptions(WormSpec s) {
        return new FxOptions(s.p(26, FxOptions.defaultOn(s.mode()) ? 1 : 0) > 0, s.p(27, 1) > 0, s.p(28, 1) > 0, s.p(29, 1), s.p(30, 1));
    }

    /** The mouth closing numbers of a spec (for the anchors): close_time and close_end. */
    public static double closeTime(WormSpec s) { return s.p(21, MouthAnim.DEFAULT_CLOSE_TIME); }
    public static double closeEnd(WormSpec s) { return s.p(22, MouthAnim.CLOSE_END_DEFAULT_MARK); }

    /** The mouth opening angle in degrees at script time tau: held pose, static open/closed, or the closing animation. */
    public static double mouthAngle(WormSpec s, double tau) {
        double open = s.p(23, HeadSpec.OPEN_DEGREES);
        double pose = s.p(25, -1);
        if (pose >= 0) return open * (1 - Math.max(0, Math.min(1, pose)));
        int mode = (int) s.p(8, 0);
        if (mode == MOUTH_CLOSED) return 0;
        if (mode == MOUTH_OPEN) return open;
        return MouthAnim.angleAt(get(s), tau, open, s.p(21, MouthAnim.DEFAULT_CLOSE_TIME), s.p(22, MouthAnim.CLOSE_END_DEFAULT_MARK));
    }

    /** The closing times of an animated spec. */
    public static MouthAnim.Timing mouthTiming(WormSpec s) {
        return MouthAnim.timing(get(s), s.p(21, MouthAnim.DEFAULT_CLOSE_TIME), s.p(22, MouthAnim.CLOSE_END_DEFAULT_MARK));
    }

    public static boolean teethOn(WormSpec s) { return s.p(24, 1) > 0; }

    /** @throws Moves.Rejected when the move cannot satisfy the curvature rule (and force is off) */
    public static Moves.Move build(WormSpec s) {
        Moves.Move m = build0(s);
        double pause = s.p(18, -1);
        if (pause >= 0) m.setPause(pause);
        return m;
    }

    private static Moves.Move build0(WormSpec s) {
        double width = s.p(0, 16);
        int segs = (int) s.p(1, 60);
        boolean force = s.p(10, 0) > 0;
        return switch (s.mode()) {
            case "crest" -> {
                double up = s.p(3, 6);
                yield Moves.crest(width, segs, s.p(2, Moves.DEFAULT_CREST_HEIGHT), up, s.p(6, 0) > 0 ? s.p(6, 0) : up, s.p(4, 12), force);
            }
            case "emerge" -> Moves.emerge(width, segs, (int) s.p(2, 10), s.p(3, 90), s.p(4, 12), s.p(5, 2), s.p(20, 0) > 0 ? s.p(20, 0) : s.p(4, 12), s.p(14, 1.0), force);
            case "engulf" -> Moves.engulf(width, segs, (int) s.p(2, Moves.DEFAULT_EXPOSED), s.p(3, Moves.DEFAULT_ENGULF_LEAN), s.p(5, Moves.ENGULF_HOLD), s.p(4, 12), s.p(6, 12), s.p(14, 1.0), force);
            case "fall" -> Moves.fall(width, segs, s.p(2, 2.5), s.p(3, 7), s.p(5, 1.5), s.p(4, 12), s.p(6, 12), s.p(15, 2.0), s.p(14, 1.0), force);
            case "proj" -> Moves.projectile(width, segs, s.p(2, 2.5), s.p(3, 40), s.p(6, 12), s.p(14, 1.0), force);
            case "crash" -> Moves.crash(width, segs, (int) s.p(2, 10), s.p(3, 70), s.p(4, 12), s.p(6, 12), s.p(7, 4), s.p(15, 1.0), s.p(14, 1.0),
                    s.p(9, 1) > 0, force);
            default -> throw new Moves.Rejected("unknown move " + s.mode());
        };
    }

    /** Move for a spec, cached by everything except the time fields. */
    public static synchronized Moves.Move get(WormSpec s) {
        // the key is the path part of the spec (p0..p20 with the defaults filled in); the time, mouth and fx fields (p21 up) never change the path
        double[] q = new double[21];
        for (int i = 0; i < 21; i++) q[i] = s.p(i, i == 18 ? -1 : i == 19 ? 1 : 0);
        for (int i : new int[]{8, 11, 12, 13, 16, 17}) q[i] = 0;
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
        double fz = s.p(17, -1);
        if (fz >= 0) return get(s).tauAt(fz);
        double t = rawTau(s, age);
        if (s.p(19, 1) <= 0) t = Math.min(t, get(s).playTime() - 1e-6);   // loop off: stay at the end (under the sand) until the server removes the worm
        return t;
    }

    private static double rawTau(WormSpec s, double age) {
        return s.p(13, 0) + s.p(11, 1) * (age - s.p(12, 0)) / 20.0;
    }

    /** Seconds the server waits after the end of a loop=off worm before removing it (the client already shows it under the sand). */
    public static final double REMOVE_MARGIN = 0.5;

    /** True for a loop=off worm (not frozen) whose single run is over. */
    public static boolean finished(WormSpec s, double age) {
        if (s.p(19, 1) > 0 || s.p(17, -1) >= 0) return false;
        return rawTau(s, age) >= get(s).playTime() + REMOVE_MARGIN;
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
        return max + HeadSpec.headLength(m.width) + m.r0 * 2 + 4 + (m.fam != null ? m.width * m.fallScale : 0);
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
