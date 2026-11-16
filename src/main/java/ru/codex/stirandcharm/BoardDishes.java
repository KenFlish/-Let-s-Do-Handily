package ru.codex.stirandcharm;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CakeBlock;
import net.satisfy.farm_and_charm.core.recipe.CuttingBoardAssemblyRecipe;

/** Native assembly plus food crafting, with the original recipe and its container remainders. */
public final class BoardDishes {
    public static List<RecipeHolder<?>> crafting(Level level) {
        // Only Farm & Charm's own food crafts belong to this station; every other mod's general
        // crafting recipe would turn the board into a second crafting table (HOD keeps its own
        // Farmer's Delight recipes for its own board).
        return level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
            .filter(h -> h.id().getNamespace().equals("farm_and_charm"))
            .filter(h -> !h.value().isSpecial() && h.value().getIngredients().stream().filter(i -> !i.isEmpty()).count()>=2)
            .filter(h -> {
                ItemStack out=h.value().getResultItem(level.registryAccess());
                return out.has(DataComponents.FOOD) || out.getItem() instanceof BlockItem b &&
                    (b.getBlock() instanceof CakeBlock || b.getBlock() instanceof net.satisfy.foundation.food.IngredientEffectFoodBlock);
            }).<RecipeHolder<?>>map(h -> h).toList();
    }
    public static List<Ingredient> needs(Recipe<?> r) {return r.getIngredients().stream().filter(i -> !i.isEmpty()).toList();}
    public static boolean accepts(Recipe<?> recipe,List<ItemStack> items) {
        if(recipe instanceof CuttingBoardAssemblyRecipe a) return a.accepts(items);
        var needs=needs(recipe);return items.size()<=needs.size() && assign(items,0,needs,new boolean[needs.size()]);
    }
    private static boolean assign(List<ItemStack> items,int at,List<Ingredient> needs,boolean[] used) {
        if(at==items.size())return true;
        for(int i=0;i<needs.size();i++)if(!used[i] && needs.get(i).test(items.get(at))) {
            used[i]=true;if(assign(items,at+1,needs,used))return true;used[i]=false;
        }
        return false;
    }
    public record Meal(ItemStack result,List<ItemStack> remainders) {}
    public static Optional<Meal> make(Level level,Recipe<?> recipe,List<ItemStack> items) {
        if(items.size()!=needs(recipe).size() || !(recipe instanceof CuttingBoardAssemblyRecipe) && !accepts(recipe,items))return Optional.empty();
        if(recipe instanceof CuttingBoardAssemblyRecipe a) {
            if(!a.isOrdered()){
                var sorted=new ArrayList<ItemStack>();
                if(!order(needs(a),0,items,new boolean[items.size()],sorted))return Optional.empty();
                items=sorted;
            }
            var input=new CuttingBoardAssemblyRecipe.Input(items);
            return a.matches(input,level) ? Optional.of(new Meal(a.assemble(input,level.registryAccess()),List.of())) : Optional.empty();
        }
        if(!(recipe instanceof CraftingRecipe crafting))return Optional.empty();
        var sorted=new ArrayList<ItemStack>();
        if(!order(needs(recipe),0,items,new boolean[items.size()],sorted))return Optional.empty();
        int w=recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
        int h=recipe instanceof ShapedRecipe shaped ? shaped.getHeight() : 3;
        var grid=new ArrayList<ItemStack>();int index=0;
        if(recipe instanceof ShapedRecipe) for(var ingredient:recipe.getIngredients())grid.add(ingredient.isEmpty()?ItemStack.EMPTY:sorted.get(index++).copyWithCount(1));
        else {for(var stack:sorted)grid.add(stack.copyWithCount(1));while(grid.size()<9)grid.add(ItemStack.EMPTY);}
        var input=CraftingInput.of(w,h,grid);
        if(!crafting.matches(input,level))return Optional.empty();
        return Optional.of(new Meal(crafting.assemble(input,level.registryAccess()),crafting.getRemainingItems(input).stream().filter(s -> !s.isEmpty()).toList()));
    }
    private static boolean order(List<Ingredient> needs,int at,List<ItemStack> items,boolean[] used,List<ItemStack> sorted) {
        if(at==needs.size())return true;
        for(int i=0;i<items.size();i++)if(!used[i] && needs.get(at).test(items.get(i))) {
            used[i]=true;sorted.add(items.get(i));if(order(needs,at+1,items,used,sorted))return true;used[i]=false;sorted.removeLast();
        }
        return false;
    }
    private BoardDishes() {}
}
