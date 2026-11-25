package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Prompt 18: the record of one player in one strike: when they crossed the mouth disc and the throat disc (seconds after the move started) and the gap between them. Engine-free. */
public final class DiscRecord {
    public double mouthAt = -1, throatAt = -1, backupAt = -1;

    /** Records the first mouth crossing and tells the listeners; returns true when it was the first. */
    public boolean mouth(long event, UUID player, double t, List<DiscListener> ls) {
        if (mouthAt >= 0) return false;
        mouthAt = t;
        for (DiscListener l : ls) l.onMouth(event, player, t);
        return true;
    }

    public boolean throat(long event, UUID player, double t, List<DiscListener> ls) {
        if (throatAt >= 0) return false;
        throatAt = t;
        for (DiscListener l : ls) l.onThroat(event, player, t);
        return true;
    }

    /** The victim's backup swallow (the throat disc was never crossed): kept apart from the throat time. */
    public void backup(double t) { if (backupAt < 0) backupAt = t; }

    /** Time from the mouth disc to the throat disc, or -1 when one of them is missing. */
    public double gap() { return mouthAt >= 0 && throatAt >= 0 ? throatAt - mouthAt : -1; }

    private static String f(double v) { return String.format(Locale.ROOT, "%.2f", v); }

    /** The words of the log line {@code discs: <name> ...}. */
    public String describe() {
        if (mouthAt >= 0 && throatAt >= 0) return "mouth at t=" + f(mouthAt) + " throat at t=" + f(throatAt) + " gap=" + f(gap());
        if (throatAt >= 0) return "throat only at t=" + f(throatAt) + " (the mouth disc was not crossed)";
        if (mouthAt >= 0) return "mouth only at t=" + f(mouthAt) + (backupAt >= 0 ? ", swallowed by the backup at t=" + f(backupAt) : " (the throat disc was not crossed)");
        return backupAt >= 0 ? "neither disc crossed; swallowed by the backup at t=" + f(backupAt) : "neither disc crossed";
    }
}
