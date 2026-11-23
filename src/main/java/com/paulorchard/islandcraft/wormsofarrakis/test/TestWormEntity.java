package com.paulorchard.islandcraft.wormsofarrakis.test;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Test-only entity (prompt 01): no AI, no hit box, not saved, no spawn egg. It only carries a {@link WormSpec}
 * string so the client renderer knows what to draw. Spawned only by /arrakis_worm_test.
 */
public class TestWormEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<String> DATA_SPEC =
            SynchedEntityData.defineId(TestWormEntity.class, EntityDataSerializers.STRING);

    /** Test worms are drawn within this distance (blocks), whatever their tiny real hit box says. */
    public static final double RENDER_DISTANCE = 1024.0;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public TestWormEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public String getSpecString() {
        return this.entityData.get(DATA_SPEC);
    }

    public void setSpec(WormSpec spec) {
        this.entityData.set(DATA_SPEC, spec.serialize());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SPEC, "ring;-;16;0");
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < RENDER_DISTANCE * RENDER_DISTANCE;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Experiment 5 adds the mouth controller on the client side of the animatable; none needed before.
        TestControllers.register(this, controllers);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
