package ru.codex.stirandcharm;

import com.pockyl.diegesis.api.Diegesis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.satisfy.farm_and_charm.core.block.entity.CookingPotBlockEntity;
import net.satisfy.farm_and_charm.core.block.entity.RoasterBlockEntity;

/**
 * A right click with the right vessel on a ready station serves one portion without opening the scene.
 * Shift + right click keeps the block's own action; everything is checked on the server first.
 */
@EventBusSubscriber(modid = StirAndCharm.ID)
public final class ServingEvents {
    @SubscribeEvent
    public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) return;
        var player = event.getEntity();
        if (player.isSecondaryUseActive()) return;
        var level = event.getLevel();
        var pos = event.getPos();
        var type = Diegesis.stationFor(level.getBlockState(pos)).orElse(null);
        if (type == null || !type.id().getNamespace().equals(StirAndCharm.ID)) return;
        var be = level.getBlockEntity(pos);
        if (!(be instanceof Container container) || !(be instanceof MealVessel)) return;
        if (!MealServing.enabled(be)) return;
        if (player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > type.reach() * type.reach()) return;
        int taken = MealServing.serveFromHand(player, container);
        if (taken <= 0) return;
        if (level instanceof ServerLevel server) {
            if (be instanceof CookingPotBlockEntity pot) pot.dropExperience(server, Vec3.atCenterOf(pos));
            else if (be instanceof RoasterBlockEntity roaster) roaster.dropExperience(server, Vec3.atCenterOf(pos));
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private ServingEvents() {}
}