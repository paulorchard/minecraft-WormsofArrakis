package com.paulorchard.islandcraft.wormsofarrakis.path;

/** Height of the sand surface at a point, in model coordinates relative to the entity origin (model x is always 0 for these planar worms). */
@FunctionalInterface
public interface SurfaceProvider {
    /** Surface height (relative to the entity's y) at model position (z). */
    double heightAt(double z);

    /** A flat surface at the entity's own height (the block the player stood on when the command ran). */
    SurfaceProvider PLANE = z -> 0.0;
}
