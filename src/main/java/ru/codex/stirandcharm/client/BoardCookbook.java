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
        var pages=new ArrayList<RecipeBook.Page>();
        for(var h:BoardRecipes.all(view.level())) {
            var needs=new ArrayList<>(BoardDishes.needs(h.value()));
            boolean cut=h.value() instanceof CuttingBoardRecipe;
            boolean axe=h.id().getPath().startsWith("stripping/");
            if(cut) {
                var tools=net.minecraft.core.registries.BuiltInRegistries.ITEM.stream().map(ItemStack::new)
                    .filter(s -> axe?net.satisfy.farm_and_charm.core.util.Strippables.isAxe(s):BoardTools.knife(s)).toArray(ItemStack[]::new);
                if(tools.length>0)needs.add(Ingredient.of(tools));
            }
            pages.add(new RecipeBook.Page(h.id(),KitchenRecipes.result(view.level(),h.value()),needs,
                h.value() instanceof CuttingBoardRecipe c?c.getByproducts():List.of(),null,false,cut?(axe?"tools":"knife"):"dishes"));
        }
        return pages;
    }
    public List<RecipeBook.Tab> tabs(StationView view) {
        var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("farm_and_charm:iron_cleaver"));
        return List.of(new RecipeBook.Tab(null,new ItemStack(Items.COMPASS)),new RecipeBook.Tab("dishes",new ItemStack(Items.BREAD)),
            new RecipeBook.Tab("knife",new ItemStack(item)),new RecipeBook.Tab("tools",new ItemStack(Items.IRON_AXE)));
    }
    public String memory(){return StirAndCharm.BOARD.toString();}
    public List<ItemStack> inStation(StationView view) {
        var items=new ArrayList<>(List.of(view.state().getItems("ingredients")));
        items.addAll(List.of(view.state().getItems("pending")));items.addAll(List.of(view.state().getItems("knife")));return items;
    }
    public boolean available(StationView view,RecipeBook.Page page) {
        var player=Minecraft.getInstance().player;if(player==null) return false;
        var recipe=BoardRecipes.all(view.level()).stream().filter(h -> h.id().equals(page.key())).findFirst();
        if(recipe.isEmpty()) return false;
        var items=List.of(view.state().getItems("ingredients")).stream().filter(s -> !s.isEmpty()).toList();
        if(recipe.get().value() instanceof CuttingBoardRecipe && !items.isEmpty())return false;
        if(!view.state().getItems("pending")[0].isEmpty()) return false;
        return BoardRecipes.plan(player.getInventory(),items,recipe.get().value()).isPresent();
    }
    public Object current(StationView view) {
        var selected=net.minecraft.resources.ResourceLocation.tryParse(view.state().data().getString("selected_recipe"));
        if(selected!=null)return selected;
        var pending=view.state().getItems("pending")[0];
        var items=List.of(view.state().getItems("ingredients")).stream().filter(s -> !s.isEmpty()).toList();
        for(var h:BoardRecipes.all(view.level())) {
            if(h.value() instanceof CuttingBoardRecipe c && !pending.isEmpty() && c.matches(new SingleRecipeInput(pending),view.level())) return h.id();
            if(!(h.value() instanceof CuttingBoardRecipe) && !items.isEmpty() && BoardDishes.make(view.level(),h.value(),items).isPresent())return h.id();
        }
        return null;
    }
    public void picked(StationView view,RecipeBook.Page page) {
        var tag=new CompoundTag();tag.putString("recipe",page.key().toString());tag.putBoolean("batch",net.minecraft.client.gui.screens.Screen.hasShiftDown());view.sendAction("fill",tag);
    }
}
