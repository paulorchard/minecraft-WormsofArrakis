package com.paulorchard.islandcraft.wormsofarrakis.hunt;

/**
 * Prompt 17 part 0.2: the swallowed state of a player, engine-free. A player the worm touches is swallowed (alive, hidden, held, protected) and killed only when the strike ends; every
 * other way out of the state is listed here so nobody is ever left stuck.
 */
public final class SwallowRules {
    private SwallowRules() {}

    public enum State { HELD, SWALLOWED, KILLED, RELEASED }

    /** Why a player leaves the state. */
    public enum End { STRIKE_END, SAFETY_TIME, STOPPED, FIZZLED, SERVER_STOP, LOGOFF, DIMENSION, MODE_CHANGE, DIED_OTHER }

    public enum Action { KILL, RELEASE, NONE }

    /**
     * What is owed to a player in {@code state} when {@code why} happens. The kill comes only at the end of the strike or its safety time (and at once for a log-off or a dimension change, so
     * nobody dodges by leaving); every other path releases them alive. nokill releases instead of killing. A player who died of something else, or is already out, needs nothing.
     */
    public static Action actionFor(State state, End why, boolean nokill) {
        if (state == State.KILLED || state == State.RELEASED) return Action.NONE;
        if (why == End.DIED_OTHER) return Action.NONE;
        if (state == State.HELD) return Action.RELEASE;
        return switch (why) {
            case STRIKE_END, SAFETY_TIME, LOGOFF, DIMENSION -> nokill ? Action.RELEASE : Action.KILL;
            default -> Action.RELEASE;
        };
    }

    /** The safety time of the kill, seconds from the start of the strike: SwallowMaxSeconds when above zero, else the run time of the move + 3 s. */
    public static double safetySeconds(double playSeconds, double configured) { return configured > 0 ? configured : playSeconds + 3.0; }

    /** What a swallowed player cannot do. Chat and commands work. */
    public enum Interaction { BREAK_BLOCK, PLACE_BLOCK, USE_ITEM, ATTACK, INTERACT_ENTITY, DROP_ITEM, PICK_UP_ITEM, DAMAGE, TARGETED_BY_MOB, CHAT, COMMAND }

    public static boolean blocked(State s, Interaction i) {
        if (s != State.SWALLOWED) return false;
        return i != Interaction.CHAT && i != Interaction.COMMAND;
    }

    /** Damage to a swallowed player is cancelled unless it is the final kill. */
    public static boolean damageAllowed(State s, boolean finalKill) { return s != State.SWALLOWED || finalKill; }
}
