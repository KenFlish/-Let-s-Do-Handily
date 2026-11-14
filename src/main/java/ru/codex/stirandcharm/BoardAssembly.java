package ru.codex.stirandcharm;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CakeBlock;
import net.satisfy.farm_and_charm.core.recipe.CuttingBoardAssemblyRecipe;

/** The dishes the board puts together from layers, bottom first, as Hands-On Delight does. */
public final class BoardAssembly {
    public enum Finish { PRESS, SANDWICH, TOSS, ROLL, SKEWER, KNEAD, ARRANGE, SHAPE, FREEZE }

    public record Dish(RecipeHolder<?> holder, List<Ingredient> ingredients, int base, Finish finish, boolean ordered) {}

    public record Made(List<ItemStack> dishes, List<ItemStack> leftovers, List<ItemStack> remaining, int rounds) {}

    private static final Map<RecipeManager, List<Dish>> CACHE = new WeakHashMap<>();

    public static List<Dish> all(Level level) {
        RecipeManager manager = level.getRecipeManager();
        synchronized (CACHE) {
            List<Dish> cached = CACHE.get(manager);
            if (cached != null) return cached;
            var dishes = new ArrayList<Dish>();
            for (var holder : BoardRecipes.all(level)) {
                if (holder.value() instanceof net.satisfy.farm_and_charm.core.recipe.CuttingBoardRecipe) continue;
                var ingredients = BoardDishes.needs(holder.value());
                if (ingredients.size() < 2) continue;
                int base = baseOf(ingredients);
                dishes.add(new Dish(holder, ingredients, base,
                    finishOf(level, holder.value(), ingredients, base),
                    holder.value() instanceof CuttingBoardAssemblyRecipe a && a.isOrdered()));
            }
            cached = List.copyOf(dishes);
            CACHE.put(manager, cached);
            return cached;
        }
    }

    public static Optional<Dish> complete(Level level, List<ItemStack> layers) {
        for (var dish : all(level)) if (assign(dish, layers, true) != null) return Optional.of(dish);
        return Optional.empty();
    }

    public static boolean fits(Level level, List<ItemStack> layers) {
        for (var dish : all(level)) if (assign(dish, layers, false) != null) return true;
        return false;
    }

    public static boolean canGrow(Level level, List<ItemStack> layers) {
        for (var dish : all(level)) if (dish.ingredients().size() > layers.size() && assign(dish, layers, false) != null) return true;
        return false;
    }

    public static boolean isBase(Level level, ItemStack stack) {
        for (var dish : all(level)) if (dish.ingredients().get(dish.base()).test(stack)) return true;
        return false;
    }

    public static boolean isDish(Level level, ItemStack stack) {
        return all(level).stream().anyMatch(dish -> KitchenRecipes.result(level, dish.holder().value()).is(stack.getItem()));
    }

    public static int[] assign(Dish dish, List<ItemStack> layers) {
        return assign(dish, layers, true);
    }

    /** Which ingredient every layer is; the first layer is the base. Null when they do not make the dish. */
    public static int[] assign(Dish dish, List<ItemStack> layers, boolean all) {
        var ingredients = dish.ingredients();
        if (layers.isEmpty() || layers.size() > ingredients.size() || all && layers.size() != ingredients.size()) return null;
        if (dish.ordered()) {
            for (int i = 0; i < layers.size(); i++) if (!ingredients.get(i).test(layers.get(i))) return null;
            int[] taken = new int[layers.size()];
            for (int i = 0; i < taken.length; i++) taken[i] = i;
            return taken;
        }
        // The base is a hint for the finish, not a straitjacket: a generic crafting recipe may
        // list its ingredients in any order, so every ingredient is tried as the first layer.
        int first = dish.base();
        if (!ingredients.get(first).test(layers.get(0))) {
            for (int candidate = 0; candidate < ingredients.size(); candidate++) {
                if (candidate == first || !ingredients.get(candidate).test(layers.get(0))) continue;
                int[] tried = assignFrom(ingredients, layers, candidate);
                if (tried != null) return tried;
            }
            return null;
        }
        return assignFrom(ingredients, layers, first);
    }

    private static int[] assignFrom(List<Ingredient> ingredients, List<ItemStack> layers, int first) {
        int[] taken = new int[layers.size()];
        taken[0] = first;
        boolean[] used = new boolean[ingredients.size()];
        used[first] = true;
        return assign(ingredients, layers, 1, used, taken) ? taken : null;
    }

    private static boolean assign(List<Ingredient> ingredients, List<ItemStack> layers, int next, boolean[] used, int[] taken) {
        if (next == layers.size()) return true;
        for (int i = 0; i < ingredients.size(); i++) if (!used[i] && ingredients.get(i).test(layers.get(next))) {
            used[i] = true; taken[next] = i;
            if (assign(ingredients, layers, next + 1, used, taken)) return true;
            used[i] = false;
        }
        return false;
    }

    /** Makes up to {@code most} dishes, one item of each layer a round; remainders go to the cook. */
    public static Made make(Dish dish, List<ItemStack> layers, Level level, int most) {
        if (assign(dish, layers, true) == null) return new Made(List.of(), List.of(), List.copyOf(layers), 0);
        List<ItemStack> left = new ArrayList<>();
        layers.forEach(layer -> left.add(layer.copy()));
        var dishes = new ArrayList<ItemStack>();
        var leftovers = new ArrayList<ItemStack>();
        int rounds = 0;
        while (rounds < most && left.stream().allMatch(stack -> !stack.isEmpty())) {
            var one = new ArrayList<ItemStack>();
            for (var stack : left) one.add(stack.copyWithCount(1));
            var meal = BoardDishes.make(level, dish.holder().value(), one);
            if (meal.isEmpty()) break;
            add(dishes, meal.get().result());
            for (var remainder : meal.get().remainders()) add(leftovers, remainder);
            for (var stack : left) stack.shrink(1);
            rounds++;
        }
        left.removeIf(ItemStack::isEmpty);
        return new Made(dishes, leftovers, left, rounds);
    }

    private static void add(List<ItemStack> stacks, ItemStack stack) {
        for (var there : stacks) if (ItemStack.isSameItemSameComponents(there, stack)) {
            there.grow(stack.getCount());
            return;
        }
        stacks.add(stack.copy());
    }

    private static int baseOf(List<Ingredient> ingredients) {
        String[] bases = {"bowl", "glass_bottle", "bread", "tortilla", "flatbread", "toast", "pie_crust", "stick", "rod", "kelp", "rice", "wheat", "dough", "flour"};
        for (int i = 0; i < ingredients.size(); i++) {
            var items = ingredients.get(i).getItems();
            if (items.length == 0) continue;
            String path = BuiltInRegistries.ITEM.getKey(items[0].getItem()).getPath();
            for (var base : bases) if (path.contains(base)) return i;
        }
        return 0;
    }

    private static Finish finishOf(Level level, Recipe<?> recipe, List<Ingredient> ingredients, int base) {
        ItemStack result = KitchenRecipes.result(level, recipe);
        String path = BuiltInRegistries.ITEM.getKey(result.getItem()).getPath();
        String basePath = ingredients.get(base).getItems().length == 0 ? ""
            : BuiltInRegistries.ITEM.getKey(ingredients.get(base).getItems()[0].getItem()).getPath();
        if (result.getItem() instanceof BlockItem block && block.getBlock() instanceof CakeBlock) return Finish.PRESS;
        if (path.contains("dough")) return Finish.KNEAD;
        if (basePath.contains("bowl")) {
            boolean meat = ingredients.stream().skip(1).flatMap(i -> Arrays.stream(i.getItems())).findFirst()
                .map(stack -> stack.is(ItemTags.MEAT) || stack.is(ItemTags.FISHES)).orElse(false);
            return meat ? Finish.ARRANGE : Finish.TOSS;
        }
        if (basePath.contains("bread")) return path.contains("wrap") || path.contains("burrito") ? Finish.ROLL : Finish.SANDWICH;
        if (basePath.contains("tortilla") || basePath.contains("flatbread")) return Finish.ROLL;
        if (basePath.contains("stick") || basePath.contains("rod")) {
            boolean frozen = path.contains("popsicle") || path.contains("ice")
                || ingredients.stream().flatMap(i -> Arrays.stream(i.getItems())).anyMatch(s -> s.is(Items.ICE));
            return frozen ? Finish.FREEZE : Finish.SKEWER;
        }
        if (basePath.contains("rice")) return Finish.SHAPE;
        return Finish.PRESS;
    }

    private BoardAssembly() {}
}