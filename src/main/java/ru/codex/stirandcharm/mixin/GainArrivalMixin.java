package ru.codex.stirandcharm.mixin;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.codex.stirandcharm.client.ClientDelivery;

@Mixin(targets="com.pockyl.diegesis.impl.client.focus.Gains",remap=false)
public abstract class GainArrivalMixin {
    @Redirect(method="watch",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Inventory;getItem(I)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack handily$flyAfterSceneMotion(Inventory inventory,int slot){return ClientDelivery.visible(inventory,slot,true);}
}
