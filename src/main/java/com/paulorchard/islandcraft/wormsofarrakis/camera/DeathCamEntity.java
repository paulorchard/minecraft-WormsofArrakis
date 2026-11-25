package com.paulorchard.islandcraft.wormsofarrakis.camera;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Prompt 16 part 3: the entity the death screen is seen from. Invisible and silent, no collision (noPhysics, size 0), no gravity, not pickable, not pushable, never saved, no damage. It is sent
 * to the clients (tracking range 10 chunks, a position update every tick) and the client smooths the teleports with the same {@link InterpolationHandler} the Display entities use (3 steps, the
 * yaw with rotLerp, so no jump at +-180).
 */
public class DeathCamEntity extends Entity {
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 3);

    public DeathCamEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setSilent(true);
        this.setInvisible(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) this.interpolation.interpolate();
    }

    @Override
    public InterpolationHandler getInterpolation() { return interpolation; }

    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {}
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void readAdditionalSaveData(ValueInput input) {}
    @Override protected void addAdditionalSaveData(ValueOutput output) {}
}
