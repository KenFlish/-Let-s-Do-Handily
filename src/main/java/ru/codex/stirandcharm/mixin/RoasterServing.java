package ru.codex.stirandcharm.mixin;
import net.minecraft.world.item.ItemStack;
import net.satisfy.farm_and_charm.core.block.entity.RoasterBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import ru.codex.stirandcharm.MealServing;
@Mixin(RoasterBlockEntity.class)
public abstract class RoasterServing {
    @Redirect(method="craft", at=@At(value="INVOKE", target="Lnet/satisfy/farm_and_charm/core/block/entity/RoasterBlockEntity;getItem(I)Lnet/minecraft/world/item/ItemStack;"), remap=false)
    private ItemStack handily$defer(RoasterBlockEntity be, int slot) {
        return slot == 6 && MealServing.enabled(be) ? ItemStack.EMPTY : be.getItem(slot);
    }
}
