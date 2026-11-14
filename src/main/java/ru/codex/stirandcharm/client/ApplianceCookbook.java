package ru.codex.stirandcharm.client;
import java.util.*;
import com.pockyl.diegesis.api.client.StationView;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import ru.codex.stirandcharm.*;

final class ApplianceCookbook implements RecipeBook.Source {
    private final Appliance kind;
    ApplianceCookbook(Appliance kind) { this.kind = kind; }
    public List<RecipeBook.Page> pages(StationView view) {
        var pages = new ArrayList<RecipeBook.Page>();
        for (var holder : kind.recipes(view.level())) {
            var foot = new ArrayList<ItemStack>();
            var dish = kind.dish(holder.value());
            if (!dish.isEmpty()) foot.add(dish);
            if (kind != Appliance.MINCER) foot.add(new ItemStack(Items.CLOCK));
            pages.add(new RecipeBook.Page(holder.id(), KitchenRecipes.result(view.level(), holder.value()),
                holder.value().getIngredients().stream().filter(i -> !i.isEmpty()).toList(), foot,
                kind == Appliance.MINCER ? null : Component.translatable("handson_farm_charm.book.seconds", kind == Appliance.STOVE ? 12 : 45), false));
        }
        return pages;
    }
    public List<ItemStack> inStation(StationView view) {
        var list = new ArrayList<>(List.of(view.state().getItems("ingredients")));
        if (kind == Appliance.ROASTER) list.addAll(List.of(view.state().getItems("containers")));
        return list;
    }
    public boolean available(StationView view, RecipeBook.Page page) {
        if (kind == Appliance.ROASTER && view.state().getControl("lid_open") < .5F) return false;
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        var holder = kind.recipes(view.level()).stream().filter(h -> h.id().equals(page.key())).findFirst();
        if (holder.isEmpty()) return false;
        var c = canonical(view);
        return RecipeTransfer.plan(player.getInventory(), c, kind.inputs, false, holder.get().value().getIngredients(), ItemStack.EMPTY, kind == Appliance.STOVE).isPresent();
    }
    private SimpleContainer canonical(StationView view) {
        var c = new SimpleContainer(8);
        var inputs = view.state().getItems("ingredients");
        for (int i = 0; i < inputs.length; i++) c.setItem(i, inputs[i].copy());
        if (kind == Appliance.ROASTER) c.setItem(6, view.state().getItems("containers")[0].copy());
        return c;
    }
    public void picked(StationView view, RecipeBook.Page page) {
        var data = new CompoundTag(); data.putString("recipe", page.key().toString());
        data.putBoolean("batch",net.minecraft.client.gui.screens.Screen.hasShiftDown()); view.sendAction("fill", data);
    }
    public Object current(StationView view) { return matching(view).map(RecipeHolder::id).orElse(null); }
    Optional<? extends RecipeHolder<?>> matching(StationView view) {
        var c = canonical(view);
        if (Arrays.stream(view.state().getItems("ingredients")).allMatch(ItemStack::isEmpty)) return Optional.empty();
        for (var holder : kind.recipes(view.level())) {
            if (kind == Appliance.ROASTER) c.setItem(6, kind.dish(holder.value()));
            if (RecipeTransfer.plan(new Inventory(null), c, kind.inputs, false, holder.value().getIngredients(), kind.dish(holder.value()), kind == Appliance.STOVE).isPresent())
                return Optional.of(holder);
        }
        return Optional.empty();
    }
}
