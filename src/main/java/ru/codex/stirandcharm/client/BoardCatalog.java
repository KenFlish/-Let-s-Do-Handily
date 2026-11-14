package ru.codex.stirandcharm.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.satisfy.farm_and_charm.core.recipe.CuttingBoardRecipe;
import ru.codex.stirandcharm.BoardDishes;
import ru.codex.stirandcharm.BoardRecipes;
import ru.codex.stirandcharm.BoardTools;
import ru.codex.stirandcharm.KitchenRecipes;
import ru.codex.stirandcharm.StirAndCharm;

/**
 * The board's recipe list, made once per recipe reload. Opening the book no longer walks every
 * recipe, tag and registry item; the tools are found once too and only refreshed when tags change.
 */
final class BoardCatalog {
    private static RecipeManager manager;
    private static List<RecipeHolder<?>> all = List.of();
    private static Map<ResourceLocation, RecipeHolder<?>> byId = Map.of();
    private static List<RecipeBook.Page> pages = List.of();
    private static ItemStack[] knives = new ItemStack[0];
    private static ItemStack[] axes = new ItemStack[0];

    static synchronized void invalidate() {
        manager = null;
        all = List.of();
        byId = Map.of();
        pages = List.of();
        knives = new ItemStack[0];
        axes = new ItemStack[0];
    }

    static List<RecipeHolder<?>> all(Level level) {
        ensure(level);
        return all;
    }

    static List<RecipeBook.Page> pages(Level level) {
        ensure(level);
        return pages;
    }

    static void warm() {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level != null) ensure(level);
    }

    static RecipeHolder<?> byId(Level level, ResourceLocation id) {
        ensure(level);
        return byId.get(id);
    }

    private static synchronized void ensure(Level level) {
        var current = level.getRecipeManager();
        if (current == manager && !pages.isEmpty()) return;
        var list = new ArrayList<>(BoardRecipes.all(level));
        var ids = new HashMap<ResourceLocation, RecipeHolder<?>>();
        for (var holder : list) ids.put(holder.id(), holder);
        var toolKnives = tools(false);
        var toolAxes = tools(true);
        var made = new ArrayList<RecipeBook.Page>();
        for (var holder : list) {
            var needs = new ArrayList<>(BoardDishes.needs(holder.value()));
            boolean cut = holder.value() instanceof CuttingBoardRecipe;
            boolean axe = holder.id().getPath().startsWith("stripping/");
            if (cut) {
                var tools = axe ? toolAxes : toolKnives;
                if (tools.length > 0) needs.add(Ingredient.of(tools));
            }
            var recipe = holder.value();
            made.add(new RecipeBook.Page(holder.id(), KitchenRecipes.result(level, recipe), needs,
                cut ? ((CuttingBoardRecipe) recipe).getByproducts() : List.of(), null, false,
                cut ? (axe ? "tools" : "knife") : "dishes"));
        }
        manager = current;
        all = List.copyOf(list);
        byId = Map.copyOf(ids);
        pages = List.copyOf(made);
    }

    private static synchronized ItemStack[] tools(boolean axe) {
        ItemStack[] known = axe ? axes : knives;
        if (known.length > 0) return known;
        var found = BuiltInRegistries.ITEM.stream().map(ItemStack::new)
            .filter(stack -> axe ? net.satisfy.farm_and_charm.core.util.Strippables.isAxe(stack) : BoardTools.knife(stack))
            .toArray(ItemStack[]::new);
        if (axe) axes = found; else knives = found;
        return found;
    }

    private BoardCatalog() {}
}