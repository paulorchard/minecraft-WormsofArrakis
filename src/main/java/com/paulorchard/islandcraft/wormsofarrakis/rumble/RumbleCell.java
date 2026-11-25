package com.paulorchard.islandcraft.wormsofarrakis.rumble;

/**
 * Prompt 15 part 1: how an offset of a sand column in sixteenths of a block becomes one fake cell, engine-free.
 * <pre>
 * offset 0     the real block: nothing is sent
 * offset +n    (1 to 15) a level n block in the air cell above the real surface block (dy = +1)
 * offset -n    (1 to 15) the real surface block is replaced by a level 16 - n block (dy = 0)
 * </pre>
 * The level of a block is its height in sixteenths, measured from the bottom of its block space.
 */
public record RumbleCell(Kind kind, int dy, int level) {
    public enum Kind { NONE, RAISE, LOWER }

    public static final int MAX_LEVEL = 15;
    public static final RumbleCell NONE = new RumbleCell(Kind.NONE, 0, 0);

    /** The cell of an offset; an offset beyond +-15 is taken as 15. */
    public static RumbleCell of(int offset) {
        int n = Math.min(MAX_LEVEL, Math.abs(offset));
        if (offset == 0) return NONE;
        if (offset > 0) return new RumbleCell(Kind.RAISE, 1, n);
        return new RumbleCell(Kind.LOWER, 0, 16 - n);
    }

    /** The limit RumbleMaxSixteenths may have: 1 to 15. */
    public static int limit(int configured) { return Math.max(1, Math.min(MAX_LEVEL, configured)); }

    /** The offset limited to +-max (max itself limited to 1..15). */
    public static int clamp(int offset, int max) {
        int m = limit(max);
        return Math.max(-m, Math.min(m, offset));
    }

    /** The y of the cell for a real surface block at surfaceY. */
    public int y(int surfaceY) { return surfaceY + dy; }

    /** The offset this cell stands for (0 for none): raise n gives +n, lower with level 16 - n gives -n. */
    public int offset() {
        return switch (kind) { case NONE -> 0; case RAISE -> level; case LOWER -> level - 16; };
    }
}
