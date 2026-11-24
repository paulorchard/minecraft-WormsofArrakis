package com.paulorchard.islandcraft.wormsofarrakis.fx;

/**
 * Prompt 09: the per-move effect keys (fx, fxsound, fxparticles, fxvol, fxcount). Pure code.
 * @param on        fx=on|off (default on for crash, emerge, engulf; off for crest)
 * @param sound     fxsound=on|off
 * @param particles fxparticles=on|off
 * @param vol       fxvol, a multiplier on the volume of every cue, 0..2
 * @param count     fxcount, a multiplier on the count of every particle row, 0..3
 */
public record FxOptions(boolean on, boolean sound, boolean particles, double vol, double count) {
    public static FxOptions defaults(boolean on) { return new FxOptions(on, true, true, 1, 1); }

    /** The default of fx for a move mode of the spec (fall = crash): on for crash, emerge, engulf; off for crest and the two older crashes. */
    public static boolean defaultOn(String specMode) { return specMode.equals("fall") || specMode.equals("emerge") || specMode.equals("engulf"); }

    public boolean isDefault(boolean dfltOn) { return on == dfltOn && sound && particles && vol == 1 && count == 1; }

    /** The count scale of a particle row: r0 / 8 but at most {@link CrashFx#MAX_AUTO_SCALE}, times fxcount. */
    public double countScale(double r0) { return Math.min(r0 / CrashFx.COUNT_REFERENCE_R0, CrashFx.MAX_AUTO_SCALE) * count; }
}
