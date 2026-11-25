package com.paulorchard.islandcraft.wormsofarrakis.hunt;

/** Prompt 14: the four strike types (the four worm moves that devour) and the four cardinal directions. Pure. */
public enum StrikeType {
    CREST("crest"), CRASH("crash"), EMERGE("emerge"), ENGULF("engulf");

    public final String label;
    StrikeType(String label) { this.label = label; }

    public static StrikeType of(String label) {
        for (StrikeType t : values()) if (t.label.equals(label)) return t;
        return null;
    }

    /** The four cardinal directions. {@code yaw} is the entity yaw (degrees) whose model +Z axis (the way the worm travels) points that way: S 0, W 90, N 180, E 270. */
    public enum Dir {
        N(0, -1, 180), E(1, 0, 270), S(0, 1, 0), W(-1, 0, 90);

        public final int dx, dz;
        public final double yaw;
        Dir(int dx, int dz, double yaw) { this.dx = dx; this.dz = dz; this.yaw = yaw; }
    }
}
