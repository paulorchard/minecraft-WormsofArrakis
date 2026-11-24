package com.paulorchard.islandcraft.wormsofarrakis.fx;

import java.util.ArrayList;

/**
 * Prompt 10: the bookkeeping that makes the puffs shrink and fade over their life. The engine does neither by itself, so every puff of an eruption
 * row is kept in a list and, once per client tick, its size and opacity are set from its age fraction f = age / life:
 * size(f) = start x (1 + (endScale - 1) f) and alpha = row alpha x {@link ParticleModel#fade}(f). A puff is dropped from the list when it dies.
 * Pure code: the game's particle is reached through the small {@link Puff} interface (the client implements it with the particle's quadSize and setAlpha).
 */
public final class PuffTracker {
    /** A tracked particle. */
    public interface Puff {
        boolean alive();
        void setSize(float size);
        void setAlpha(float alpha);
    }

    private static final class Entry {
        Puff puff;
        int age, life;
        float start, endScale, alpha;
    }

    private final ArrayList<Entry> list = new ArrayList<>();
    private long ticks, updates;

    /** Starts to follow a puff made now: its start size (after the scale to the row's size), its end scale, the row's alpha and its life in ticks. */
    public void add(Puff p, float startSize, float endScale, float alpha, int life) {
        Entry e = new Entry();
        e.puff = p;
        e.start = startSize;
        e.endScale = endScale;
        e.alpha = alpha;
        e.life = Math.max(1, life);
        p.setAlpha(0);   // faded in from nothing over the first 15 percent of the life
        list.add(e);
    }

    /** One client tick: ages every puff, sets its size and opacity, drops the dead. */
    public void tick() {
        ticks++;
        int n = list.size();
        for (int i = n - 1; i >= 0; i--) {
            Entry e = list.get(i);
            e.age++;
            if (e.age >= e.life || !e.puff.alive()) {
                if (e.age >= e.life && e.puff.alive()) {   // the last tick: the size at death is endScale x start, fully faded
                    e.puff.setSize((float) ParticleModel.sizeAt(e.start, e.endScale, 1));
                    e.puff.setAlpha(0);
                }
                int last = list.size() - 1;
                list.set(i, list.get(last));
                list.remove(last);
                continue;
            }
            double f = (double) e.age / e.life;
            e.puff.setSize((float) ParticleModel.sizeAt(e.start, e.endScale, f));
            e.puff.setAlpha((float) (e.alpha * ParticleModel.fade(f)));
            updates++;
        }
    }

    public int size() { return list.size(); }
    public void clear() { list.clear(); }
    public long ticks() { return ticks; }
    public long updates() { return updates; }
}
