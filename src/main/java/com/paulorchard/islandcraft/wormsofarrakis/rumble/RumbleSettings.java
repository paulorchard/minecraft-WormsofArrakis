package com.paulorchard.islandcraft.wormsofarrakis.rumble;

import com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntAdapter;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntConfig;

/** Prompt 15: the config numbers of the rumble in one record. {@link #DEFAULTS} are the defaults of the config file. */
public record RumbleSettings(int maxSixteenths, double pulseSeconds, double edgeWeight, double viewMargin, int budgetPerTick, int dropSteps, double stepSeconds, double swingSeconds) {
    public static final RumbleSettings DEFAULTS = new RumbleSettings(8, 1.0, 0.25, 32, 600, 2, 0.06, 0.09);
    /** After the end the event waits at most this long for stragglers, then everything is restored and the event ended (seconds + 3). */
    public static final double HARD_TIMEOUT_MARGIN = 3.0;

    public static RumbleSettings of(HuntConfig c) {
        return new RumbleSettings(RumbleCell.limit(c.rumbleMaxSixteenths), Math.max(c.rumblePulseSeconds, RumbleSchedule.minPeriod(numbers(c))), c.rumbleEdgeWeight, c.rumbleViewMargin,
                c.rumbleBlockBudgetPerTick, c.rumbleDropSteps, c.rumbleStepSeconds, c.rumbleSwingSeconds);
    }

    private static RumbleSchedule.Numbers numbers(HuntConfig c) { return new RumbleSchedule.Numbers(c.rumbleDropSteps, c.rumbleStepSeconds, c.rumbleSwingSeconds, 0.1); }

    public static RumbleSettings current() {
        HuntAdapter a = HuntAdapter.instance();
        return a == null ? DEFAULTS : of(a.config());
    }

    public RumbleSchedule.Numbers numbers() { return new RumbleSchedule.Numbers(dropSteps, stepSeconds, swingSeconds, 0.1); }
}
