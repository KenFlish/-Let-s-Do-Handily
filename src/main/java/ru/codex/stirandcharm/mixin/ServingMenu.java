package ru.codex.stirandcharm.mixin;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.satisfy.farm_and_charm.client.gui.handler.*;
import net.satisfy.farm_and_charm.core.block.entity.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.codex.stirandcharm.*;
/** Native Shift+click must pay for serving too; it otherwise moves stacks without calling removeItem. */
@Mixin({CookingPotGuiHandler.class,RoasterGuiHandler.class})
public abstract class ServingMenu {
    @Shadow(remap=false) @Final private Container inventory;
    @Inject(method="quickMoveStack",at=@At("HEAD"),cancellable=true,remap=false)
    private void handily$serve(Player player,int index,CallbackInfoReturnable<ItemStack> ci) {
        if (index!=7 || !(inventory instanceof MealVessel vessel) || vessel.handily$unserved()<=0) return;
        ItemStack old=inventory.getItem(7).copy();
        int count=MealServing.collect(player,inventory,false,true);
        if (count>0 && player.level() instanceof ServerLevel level) {
            if (inventory instanceof CookingPotBlockEntity pot) pot.dropExperience(level,Vec3.atCenterOf(pot.getBlockPos()));
            if (inventory instanceof RoasterBlockEntity pan) pan.dropExperience(level,Vec3.atCenterOf(pan.getBlockPos()));
        }
        ci.setReturnValue(count>0 ? old.copyWithCount(count) : ItemStack.EMPTY);
    }
}
