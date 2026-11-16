package ru.codex.stirandcharm.mixin;

import com.pockyl.diegesis.impl.client.focus.FocusScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.codex.stirandcharm.client.ClientDelivery;

@Mixin(value=FocusScreen.class,remap=false)
public abstract class InventoryArrivalMixin {
    @Redirect(method="renderSlots",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Inventory;getItem(I)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack handily$showAfterArrival(Inventory inventory,int slot){return ClientDelivery.visible(inventory,slot,false);}
}
