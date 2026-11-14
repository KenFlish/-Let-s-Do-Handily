package ru.codex.stirandcharm;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.satisfy.farm_and_charm.core.recipe.CookingPotRecipe;
import net.satisfy.farm_and_charm.core.recipe.CraftingBowlRecipe;
import net.satisfy.farm_and_charm.core.registry.RecipeTypeRegistry;

public final class KitchenRecipes {
    public static List<? extends RecipeHolder<?>> all(Level level, boolean bowl) {
        return bowl ? level.getRecipeManager().getAllRecipesFor(RecipeTypeRegistry.CRAFTING_BOWL_RECIPE_TYPE.get())
                    : level.getRecipeManager().getAllRecipesFor(RecipeTypeRegistry.COOKING_POT_RECIPE_TYPE.get());
    }
    public static ItemStack dish(Recipe<?> recipe) {
        return recipe instanceof CookingPotRecipe pot && pot.isContainerRequired() ? pot.getContainerItem() : ItemStack.EMPTY;
    }
    public static ItemStack result(Level level, Recipe<?> recipe) {
        ItemStack stack = recipe.getResultItem(level.registryAccess()).copy();
        if (recipe instanceof CraftingBowlRecipe bowl) stack.setCount(bowl.getOutputCount());
        return stack;
    }
    private KitchenRecipes() {}
}
