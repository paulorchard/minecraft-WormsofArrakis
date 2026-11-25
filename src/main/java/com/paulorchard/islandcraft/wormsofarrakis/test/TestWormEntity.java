package com.paulorchard.islandcraft.wormsofarrakis.test;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
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

    /** Prompt 16: called at the end of every server-side tick of a worm (the hazard of the strike worm and of the test worms). */
    public static volatile java.util.function.Consumer<TestWormEntity> SERVER_TICK_HOOK;
    private boolean strikeWorm;
    /** Prompt 16: the worm of a hunt event's strike (always deadly). */
    public void markStrikeWorm() { strikeWorm = true; }
    public boolean isStrikeWorm() { return strikeWorm; }

    private String watchedSpec = "";
    private boolean removeAtEnd;

    /** Server side: a move with loop=off plays once; MoveFactory.finished says when its run is over, then the worm is removed. */
    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel)) return;
        var hook = SERVER_TICK_HOOK;
        if (hook != null) {
            try { hook.accept(this); } catch (RuntimeException e) { WormsOfArrakis.LOGGER.warn("[WormsOfArrakis] hazard: the tick of worm {} failed: {}", this.getId(), e.toString()); }
        }
        String sp = getSpecString();
        if (!sp.equals(watchedSpec)) {
            watchedSpec = sp;
            removeAtEnd = false;
            try {
                if (sp.startsWith("move;")) removeAtEnd = WormSpec.parse(sp).p(19, 1) <= 0;
            } catch (RuntimeException e) {
                removeAtEnd = false;
            }
        }
        if (!removeAtEnd) return;
        try {
            if (MoveFactory.finished(WormSpec.parse(sp), this.tickCount)) {
                WormsOfArrakis.LOGGER.info("[WormsOfArrakis] loop=off: {} finished its single run at age {} ticks, worm removed", sp.split(";")[1], this.tickCount);
                this.discard();
            }
        } catch (RuntimeException e) {
            removeAtEnd = false;
        }
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
