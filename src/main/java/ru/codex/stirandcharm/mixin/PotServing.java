package ru.codex.stirandcharm.mixin;
import net.minecraft.world.item.ItemStack;
import net.satisfy.farm_and_charm.core.block.entity.CookingPotBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import ru.codex.stirandcharm.MealServing;
@Mixin(CookingPotBlockEntity.class)
public abstract class PotServing {
    @Redirect(method="craft", at=@At(value="INVOKE", target="Lnet/satisfy/farm_and_charm/core/block/entity/CookingPotBlockEntity;getItem(I)Lnet/minecraft/world/item/ItemStack;"), remap=false)
    private ItemStack handily$defer(CookingPotBlockEntity be, int slot) {
        return slot == 6 && MealServing.enabled(be) ? ItemStack.EMPTY : be.getItem(slot);
    }
}
