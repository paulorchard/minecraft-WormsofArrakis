package com.paulorchard.islandcraft.wormsofarrakis.fx;

/**
 * Prompt 09/10: the per-move effect keys (fx, fxsound, fxparticles, fxvol, fxcount, eruption). Pure code.
 * @param on        fx=on|off (default on for crest, crash, emerge, engulf since prompt 10; the two older crashes have none)
 * @param sound     fxsound=on|off
 * @param particles fxparticles=on|off
 * @param vol       fxvol, a multiplier on the volume of every cue, 0..2
 * @param count     fxcount, a multiplier on the count of every particle row, 0..3 (the eruption rows too)
 * @param eruption  eruption=on|off, the eruption rows of the breach (prompt 10)
 */
public record FxOptions(boolean on, boolean sound, boolean particles, double vol, double count, boolean eruption) {
    /** The prompt 09 form: no eruption rows (the earlier sheet alone). */
    public FxOptions(boolean on, boolean sound, boolean particles, double vol, double count) { this(on, sound, particles, vol, count, false); }

    /** The prompt 09 sheet alone (no eruption); what the earlier tests use. */
    public static FxOptions defaults(boolean on) { return new FxOptions(on, true, true, 1, 1, false); }

    /** Every default of a move: eruption on. */
    public static FxOptions full(boolean on) { return new FxOptions(on, true, true, 1, 1, true); }

    /** The default of fx for a move mode of the spec (fall = crash): on for crest, emerge, crash, engulf; off for the two older crashes (proj, crash = crasharc). */
    public static boolean defaultOn(String specMode) { return !specMode.equals("proj") && !specMode.equals("crash"); }

    public boolean isDefault(boolean dfltOn) { return on == dfltOn && sound && particles && vol == 1 && count == 1 && eruption; }

    /** The count scale of a particle row: r0 / 8 but at most {@link CrashFx#MAX_AUTO_SCALE}, times fxcount. */
    public double countScale(double r0) { return Math.min(r0 / CrashFx.COUNT_REFERENCE_R0, CrashFx.MAX_AUTO_SCALE) * count; }
}
