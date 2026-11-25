package com.paulorchard.islandcraft.wormsofarrakis.rumble;

import java.util.Collection;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Prompt 15 part 2: the sand rumble as an event any feature can call, on the server thread. Only packets are sent; nothing in the world is ever written.
 * <pre>
 * Rumble.Handle h = Rumble.play(level, x, z, Rumble.Params.of(radius, seconds), viewers);
 * h.stop();   // restores everything now
 * </pre>
 */
public final class Rumble {
    private Rumble() {}

    /**
     * What a caller asks for. {@code strength}: time 0..1 to strength 0..1 (default constant 1). {@code share}: the strength to the share of columns that pulse (default all of them).
     * {@code maxSixteenths}, {@code pulseSeconds} and {@code edgeWeight} default to the config values (RumbleMaxSixteenths, 1.0 s, 0.25).
     */
    public record Params(double radius, double seconds, DoubleUnaryOperator strength, int maxSixteenths, double pulseSeconds, double edgeWeight, long seed, DoubleUnaryOperator share) {
        public static final DoubleUnaryOperator CONSTANT_ONE = t -> 1.0;
        public static final DoubleUnaryOperator ALL = s -> 1.0;

        public static Params of(double radius, double seconds, int maxSixteenths, double pulseSeconds, double edgeWeight, long seed) {
            return new Params(radius, seconds, CONSTANT_ONE, maxSixteenths, pulseSeconds, edgeWeight, seed, ALL);
        }

        /** The defaults of the config for the given radius and seconds. */
        public static Params of(double radius, double seconds) {
            RumbleSettings c = RumbleSettings.current();
            return of(radius, seconds, c.maxSixteenths(), c.pulseSeconds(), c.edgeWeight(), System.nanoTime());
        }

        public Params withStrength(DoubleUnaryOperator s) { return new Params(radius, seconds, s, maxSixteenths, pulseSeconds, edgeWeight, seed, share); }
        public Params withShare(DoubleUnaryOperator s) { return new Params(radius, seconds, strength, maxSixteenths, pulseSeconds, edgeWeight, seed, s); }
        public Params withMax(int m) { return new Params(radius, seconds, strength, m, pulseSeconds, edgeWeight, seed, share); }
        public Params withSeed(long s) { return new Params(radius, seconds, strength, maxSixteenths, pulseSeconds, edgeWeight, s, share); }
    }

    /** A running rumble. */
    public interface Handle {
        /** Restores everything now. */
        void stop();
        boolean running();
        double seconds();
        /** What the call changed or refused (clamped values, no columns), for the caller to tell the player. */
        List<String> notes();
        /** Columns that are drawn (sand with air above, loaded). */
        int columns();
        /** Prompt 16: replaces the viewers of the running rumble (those who left get the real blocks, new ones the cells that are up). Returns {added, removed}. */
        int[] setViewers(Collection<ServerPlayer> viewers);
    }

    /** Plays a rumble centred on (x, z). The viewers are the players who are sent the fake cells. */
    public static Handle play(ServerLevel level, double x, double z, Params params, Collection<ServerPlayer> viewers) {
        return RumbleManager.instance().play(level, x, z, params, viewers);
    }
}
