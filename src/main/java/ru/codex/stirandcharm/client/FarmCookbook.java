package ru.codex.stirandcharm.client;

import java.util.*;
import com.pockyl.diegesis.api.client.StationView;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import ru.codex.stirandcharm.*;

/** Farm & Charm recipe data for the MIT-licensed Hands-On Delight cookbook renderer. */
final class FarmCookbook implements RecipeBook.Source {
    private final boolean bowl;
    FarmCookbook(boolean bowl) { this.bowl = bowl; }
    @Override public List<RecipeBook.Page> pages(StationView view) {
        List<RecipeBook.Page> pages = new ArrayList<>();
        for (RecipeHolder<?> holder : KitchenRecipes.all(view.level(), bowl)) {
            var recipe = holder.value();
            var foot = new ArrayList<ItemStack>();
            ItemStack dish = KitchenRecipes.dish(recipe);
            if (!dish.isEmpty()) foot.add(dish);
            if (!bowl) foot.add(new ItemStack(Items.CLOCK));
            pages.add(new RecipeBook.Page(holder.id(), KitchenRecipes.result(view.level(), recipe),
                recipe.getIngredients().stream().filter(i -> !i.isEmpty()).toList(), foot,
                bowl ? null : Component.translatable("handson_farm_charm.book.pot_time"), false));
        }
        return pages;
    }
    @Override public List<ItemStack> inStation(StationView view) {
        var items = new ArrayList<>(List.of(view.state().getItems("ingredients")));
        if (!bowl) items.addAll(List.of(view.state().getItems("containers")));
        return items;
    }
    @Override public boolean available(StationView view, RecipeBook.Page page) {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        var recipe = KitchenRecipes.all(view.level(), bowl).stream().filter(h -> h.id().equals(page.key())).findFirst();
        if (recipe.isEmpty()) return false;
        SimpleContainer c = new SimpleContainer(bowl ? 5 : 8);
        ItemStack[] inputs = view.state().getItems("ingredients");
        for (int i = 0; i < inputs.length; i++) c.setItem(i, inputs[i].copy());
        c.setItem(bowl ? 4 : 7, view.state().getItems("result")[0].copy());
        if (!bowl) c.setItem(6, view.state().getItems("containers")[0].copy());
        return RecipeTransfer.plan(player.getInventory(), c, inputs.length, bowl, recipe.get().value().getIngredients(),
            ItemStack.EMPTY).isPresent();
    }
    @Override public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag data = new CompoundTag(); data.putString("recipe", page.key().toString());
        view.sendAction("fill", data);
    }
    @Override public Object current(StationView view) { return matching(view, bowl).map(RecipeHolder::id).orElse(null); }

    static Optional<? extends RecipeHolder<?>> matching(StationView view, boolean bowl) {
        var input = view.state().getItems("ingredients");
        if (Arrays.stream(input).allMatch(ItemStack::isEmpty)) return Optional.empty();
        var emptyPlayer = new Inventory(null);
        for (var holder : KitchenRecipes.all(view.level(), bowl)) {
            SimpleContainer c = new SimpleContainer(bowl ? 5 : 8);
            for (int i = 0; i < input.length; i++) c.setItem(i, input[i].copy());
            // A preview describes the dish even before its serving container is inserted.
            if (!bowl) c.setItem(6, KitchenRecipes.dish(holder.value()).copy());
            if (RecipeTransfer.plan(emptyPlayer, c, input.length, bowl, holder.value().getIngredients(), KitchenRecipes.dish(holder.value())).isPresent())
                return Optional.of(holder);
        }
        return Optional.empty();
    }
}
