package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import com.paulorchard.islandcraft.wormsofarrakis.camera.StrikeCam;
import com.paulorchard.islandcraft.wormsofarrakis.hazard.WormHazard;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveFactory;
import com.paulorchard.islandcraft.wormsofarrakis.test.TestWormEntity;

/** Prompt 18: the outer surface of a live worm for the camera clearance: the hazard's geometry at the script time of the worm now (null once the worm is gone). */
final class WormSurface implements StrikeCam.Surface {
    private final TestWormEntity worm;
    private final WormHazard hz;

    WormSurface(TestWormEntity worm, WormHazard hz) {
        this.worm = worm;
        this.hz = hz;
    }

    @Override
    public double[] nearest(double x, double y, double z) {
        if (worm.isRemoved()) return null;
        return hz.nearestSurface(x, y, z, MoveFactory.tau(hz.spec, worm.tickCount) + hz.settings.timeOffset());
    }
}
