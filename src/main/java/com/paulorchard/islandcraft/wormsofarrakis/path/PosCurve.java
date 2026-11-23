package com.paulorchard.islandcraft.wormsofarrakis.path;

/** A planar curve parametrised by arc length: {x, y} at s (x horizontal, y up). */
public interface PosCurve {
    double[] pos(double s);
}
