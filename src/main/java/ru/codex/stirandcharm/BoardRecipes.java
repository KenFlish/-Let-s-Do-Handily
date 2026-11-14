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
        list.addAll(BoardDishes.crafting(level));
        for(var entry:net.satisfy.farm_and_charm.core.util.Strippables.all()) {
            var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(entry.log().getItem());
            list.add(new RecipeHolder<>(StirAndCharm.id("stripping/"+id.getNamespace()+"/"+id.getPath()),
                new CuttingBoardRecipe(Ingredient.of(entry.log()),entry.stripped(),List.of(),2,.05F)));
        }
        return list;
    }
    public static Optional<RecipeHolder<CuttingBoardRecipe>> cut(Level level,ItemStack stack) {
        var nativeRecipe=level.getRecipeManager().getRecipeFor(net.satisfy.farm_and_charm.core.registry.RecipeTypeRegistry.CUTTING_BOARD_RECIPE_TYPE.get(),new SingleRecipeInput(stack),level);
        if(nativeRecipe.isPresent())return nativeRecipe;
        return net.satisfy.farm_and_charm.core.util.Strippables.strip(stack).map(out -> new RecipeHolder<>(
            StirAndCharm.id("stripping/"+net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace()+"/"+net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()),
            new CuttingBoardRecipe(Ingredient.of(stack.getItem()),out,List.of(),2,.05F)));
    }
    /** Works on copies. Ordered assembly preserves the existing prefix, including a final topping. */
    public static Optional<RecipeTransfer.Plan> plan(Inventory player, List<ItemStack> existing, Recipe<?> recipe) {
        var c = new SimpleContainer(9);
        for (int i=0; i<existing.size(); i++) c.setItem(i, existing.get(i).copy());
        if (recipe instanceof CuttingBoardAssemblyRecipe a && a.isOrdered()) {
            if (!a.accepts(existing)) return Optional.empty();
            var needs = a.getIngredients().subList(existing.size(), a.getIngredients().size());
            if (needs.isEmpty()) return Optional.empty();
            var empty = new SimpleContainer(9);
            var planned = RecipeTransfer.plan(player, empty, 9-existing.size(), false, needs, ItemStack.EMPTY, true);
            if (planned.isEmpty()) return Optional.empty();
            var p = planned.get();
            var candidates = Arrays.stream(p.station()).filter(s -> !s.isEmpty()).toList();
            var sorted = new ArrayList<ItemStack>();
            if (!order(needs, 0, candidates, new boolean[candidates.size()], sorted)) return Optional.empty();
            for (int i=0;i<sorted.size();i++) c.setItem(existing.size()+i, sorted.get(i));
            ItemStack[] out = new ItemStack[9];
            for (int i=0;i<9;i++) out[i]=c.getItem(i);
            return Optional.of(new RecipeTransfer.Plan(p.player(),out));
        }
        var planned=RecipeTransfer.plan(player,c,recipe instanceof CuttingBoardRecipe ? 1 : 9,false,BoardDishes.needs(recipe),ItemStack.EMPTY,true);
        if (planned.isPresent() && !(recipe instanceof CuttingBoardRecipe)) {
            var p=planned.get();
            var candidates=Arrays.stream(p.station()).filter(s -> !s.isEmpty()).toList();
            var sorted=new ArrayList<ItemStack>();
            if(!order(BoardDishes.needs(recipe),0,candidates,new boolean[candidates.size()],sorted)) return Optional.empty();
            ItemStack[] out=new ItemStack[9];Arrays.fill(out,ItemStack.EMPTY);
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
