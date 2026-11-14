package ru.codex.stirandcharm.mixin;

import net.minecraft.world.item.ItemStack;
import net.satisfy.farm_and_charm.core.block.entity.CuttingBoardBlockEntity;
import net.satisfy.farm_and_charm.core.recipe.CuttingBoardRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A cleaver takes as many chops as a knife: the recipe's own count, never the cleaver's shortcut. */
@Mixin(CuttingBoardBlockEntity.class)
public abstract class BoardChops {
    @Inject(method = "requiredChops", at = @At("HEAD"), cancellable = true, remap = false)
    private static void handily$sameChops(ItemStack tool, CuttingBoardRecipe recipe, CallbackInfoReturnable<Integer> ci) {
        if (recipe != null) ci.setReturnValue(Math.min(3, Math.max(1, recipe.getChops())));
    }
}