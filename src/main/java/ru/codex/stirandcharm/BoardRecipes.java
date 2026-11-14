package ru.codex.stirandcharm;

import java.util.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.satisfy.farm_and_charm.core.recipe.*;
import net.satisfy.farm_and_charm.core.registry.RecipeTypeRegistry;

public final class BoardRecipes {
    public static List<RecipeHolder<?>> all(Level level) {
        var list = new ArrayList<RecipeHolder<?>>();
        list.addAll(level.getRecipeManager().getAllRecipesFor(RecipeTypeRegistry.CUTTING_BOARD_RECIPE_TYPE.get()));
        list.addAll(level.getRecipeManager().getAllRecipesFor(RecipeTypeRegistry.CUTTING_BOARD_ASSEMBLY_RECIPE_TYPE.get()));
        return list;
    }
    /** Works on copies. Ordered assembly preserves the existing prefix, including a final topping. */
    public static Optional<RecipeTransfer.Plan> plan(Inventory player, List<ItemStack> existing, Recipe<?> recipe) {
        var c = new SimpleContainer(5);
        for (int i=0; i<existing.size(); i++) c.setItem(i, existing.get(i).copy());
        if (recipe instanceof CuttingBoardAssemblyRecipe a && a.isOrdered()) {
            if (!a.accepts(existing)) return Optional.empty();
            var needs = a.getIngredients().subList(existing.size(), a.getIngredients().size());
            if (needs.isEmpty()) return Optional.empty();
            var empty = new SimpleContainer(5);
            var planned = RecipeTransfer.plan(player, empty, 5-existing.size(), false, needs, ItemStack.EMPTY, true);
            if (planned.isEmpty()) return Optional.empty();
            var p = planned.get();
            var candidates = Arrays.stream(p.station()).filter(s -> !s.isEmpty()).toList();
            var sorted = new ArrayList<ItemStack>();
            if (!order(needs, 0, candidates, new boolean[candidates.size()], sorted)) return Optional.empty();
            for (int i=0;i<sorted.size();i++) c.setItem(existing.size()+i, sorted.get(i));
            ItemStack[] out = new ItemStack[5];
            for (int i=0;i<5;i++) out[i]=c.getItem(i);
            return Optional.of(new RecipeTransfer.Plan(p.player(),out));
        }
        var planned=RecipeTransfer.plan(player,c,recipe instanceof CuttingBoardRecipe ? 1 : 5,false,recipe.getIngredients(),ItemStack.EMPTY,true);
        if (planned.isPresent() && recipe instanceof CuttingBoardAssemblyRecipe) {
            var p=planned.get();
            var candidates=Arrays.stream(p.station()).filter(s -> !s.isEmpty()).toList();
            var sorted=new ArrayList<ItemStack>();
            if(!order(recipe.getIngredients(),0,candidates,new boolean[candidates.size()],sorted)) return Optional.empty();
            ItemStack[] out=new ItemStack[5];Arrays.fill(out,ItemStack.EMPTY);
            for(int i=0;i<sorted.size();i++) out[i]=sorted.get(i);
            return Optional.of(new RecipeTransfer.Plan(p.player(),out));
        }
        return planned;
    }
    private static boolean order(List<Ingredient> needs,int n,List<ItemStack> stacks,boolean[] used,List<ItemStack> out) {
        if (n==needs.size()) return true;
        for(int i=0;i<stacks.size();i++) if (!used[i] && needs.get(n).test(stacks.get(i))) {
            used[i]=true; out.add(stacks.get(i));
            if(order(needs,n+1,stacks,used,out)) return true;
            used[i]=false;out.removeLast();
        }
        return false;
    }
    private BoardRecipes() {}
}
