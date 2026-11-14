package ru.codex.stirandcharm.client;

import java.util.*;
import com.pockyl.diegesis.api.client.StationView;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.satisfy.farm_and_charm.core.recipe.*;
import ru.codex.stirandcharm.*;

final class BoardCookbook implements RecipeBook.Source {
    public List<RecipeBook.Page> pages(StationView view) {
        return BoardRecipes.all(view.level()).stream().map(h -> new RecipeBook.Page(h.id(),
            KitchenRecipes.result(view.level(),h.value()),h.value().getIngredients(),
            h.value() instanceof CuttingBoardRecipe c ? c.getByproducts() : List.<ItemStack>of(),
            Component.translatable(h.value() instanceof CuttingBoardRecipe ? "handson_farm_charm.board.book_cut" : "handson_farm_charm.board.book_assembly"),false)).toList();
    }
    public List<ItemStack> inStation(StationView view) {
        var items=new ArrayList<>(List.of(view.state().getItems("ingredients")));
        items.addAll(List.of(view.state().getItems("pending")));return items;
    }
    public boolean available(StationView view,RecipeBook.Page page) {
        var player=Minecraft.getInstance().player;if(player==null) return false;
        var recipe=BoardRecipes.all(view.level()).stream().filter(h -> h.id().equals(page.key())).findFirst();
        if(recipe.isEmpty()) return false;
        boolean knife=!view.state().getItems("knife")[0].isEmpty();
        var items=List.of(view.state().getItems("ingredients")).stream().filter(s -> !s.isEmpty()).toList();
        if(recipe.get().value() instanceof CuttingBoardRecipe ? !knife || !items.isEmpty() : knife) return false;
        if(!view.state().getItems("pending")[0].isEmpty()) return false;
        return BoardRecipes.plan(player.getInventory(),items,recipe.get().value()).isPresent();
    }
    public Object current(StationView view) {
        var pending=view.state().getItems("pending")[0];
        var items=List.of(view.state().getItems("ingredients")).stream().filter(s -> !s.isEmpty()).toList();
        for(var h:BoardRecipes.all(view.level())) {
            if(h.value() instanceof CuttingBoardRecipe c && !pending.isEmpty() && c.matches(new SingleRecipeInput(pending),view.level())) return h.id();
            if(h.value() instanceof CuttingBoardAssemblyRecipe a && !items.isEmpty() && a.matches(new CuttingBoardAssemblyRecipe.Input(items),view.level())) return h.id();
        }
        return null;
    }
    public void picked(StationView view,RecipeBook.Page page) {
        var tag=new CompoundTag();tag.putString("recipe",page.key().toString());view.sendAction("fill",tag);
    }
}
