package ru.codex.stirandcharm;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.satisfy.farm_and_charm.core.recipe.RoasterRecipe;
import net.satisfy.farm_and_charm.core.registry.RecipeTypeRegistry;

/** Native slot layouts; no replacement crafting or cooking ticks. */
public enum Appliance {
    MINCER("mincer", 1, 0, 1), STOVE("stove", 3, 1, 0), ROASTER("roaster", 6, 0, 7);
    public final String block;
    public final int inputs, start, output;
    Appliance(String block, int inputs, int start, int output) {
        this.block = block; this.inputs = inputs; this.start = start; this.output = output;
    }
    public List<? extends RecipeHolder<?>> recipes(Level level) {
        return switch (this) {
            case MINCER -> level.getRecipeManager().getAllRecipesFor(RecipeTypeRegistry.MINCER_RECIPE_TYPE.get());
            case STOVE -> level.getRecipeManager().getAllRecipesFor(RecipeTypeRegistry.STOVE_RECIPE_TYPE.get());
            case ROASTER -> level.getRecipeManager().getAllRecipesFor(RecipeTypeRegistry.ROASTER_RECIPE_TYPE.get());
        };
    }
    public ItemStack dish(Recipe<?> recipe) {
        return recipe instanceof RoasterRecipe r ? r.getContainer().copy() : ItemStack.EMPTY;
    }
    public boolean enabled() {
        return switch (this) { case MINCER -> false; case STOVE -> Settings.STOVE.get(); case ROASTER -> Settings.ROASTER.get(); };
    }
    /** The native grill numbers are in world axes, unlike the scene which follows the stove's facing. */
    public static int grillSlot(int sceneSlot, net.minecraft.core.Direction facing) {
        return switch (facing) {
            case EAST -> new int[]{1,3,0,2}[sceneSlot];
            case SOUTH -> 3 - sceneSlot;
            case WEST -> new int[]{2,0,3,1}[sceneSlot];
            default -> sceneSlot;
        };
    }
}
