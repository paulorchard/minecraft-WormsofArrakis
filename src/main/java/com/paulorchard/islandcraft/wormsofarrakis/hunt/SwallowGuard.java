package com.paulorchard.islandcraft.wormsofarrakis.hunt;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Prompt 17 part 0.2: what a swallowed player cannot do, by the NeoForge events of 26.1.2. The rule is {@link SwallowRules#blocked}; chat and commands are not events of this list, so they work.
 * Damage is cancelled for every source (void, fall, drowning, suffocation, mobs, /kill) except the final kill of the strike, which is dealt after the state has changed to KILLED.
 * What still gets through: effects already on the player keep ticking (they cannot be killed by them, damage is cancelled), commands of operators ({@code /tp}, {@code /gamemode}: a gamemode change releases the
 * player), and a mod that changes health without a damage event.
 */
public final class SwallowGuard {
    private static final SwallowGuard INSTANCE = new SwallowGuard();

    private SwallowGuard() {}

    public static void init() { NeoForge.EVENT_BUS.register(INSTANCE); }

    private static boolean swallowed(net.minecraft.world.entity.Entity e) {
        HuntAdapter a = HuntAdapter.instance();
        return a != null && e instanceof ServerPlayer && a.groups().isSwallowed(e.getUUID());
    }

    @SubscribeEvent
    public void onDamage(LivingIncomingDamageEvent e) {
        if (swallowed(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onBreak(BreakBlockEvent e) {
        if (swallowed(e.getPlayer())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (swallowed(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (swallowed(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        if (swallowed(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock e) {
        if (swallowed(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract e) {
        if (swallowed(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific e) {
        if (swallowed(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent e) {
        if (swallowed(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onToss(ItemTossEvent e) {
        if (swallowed(e.getPlayer())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onPickup(ItemEntityPickupEvent.Pre e) {
        Player p = e.getPlayer();
        if (swallowed(p)) e.setCanPickup(TriState.FALSE);
    }

    /** Mobs must not target a swallowed player. */
    @SubscribeEvent
    public void onTarget(LivingChangeTargetEvent e) {
        LivingEntity t = e.getNewAboutToBeSetTarget();
        if (t != null && swallowed(t)) e.setNewAboutToBeSetTarget(null);
    }
}
