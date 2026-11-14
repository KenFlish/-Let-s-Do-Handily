package ru.codex.stirandcharm;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.satisfy.farm_and_charm.core.block.entity.RoasterBlockEntity;
import net.satisfy.farm_and_charm.core.recipe.RoasterRecipe;
import com.pockyl.diegesis.api.station.StationContext;
import net.minecraft.core.registries.BuiltInRegistries;

/** Charge real serving vessels only when a portion leaves the native inventory. */
public final class MealServing {
    public static void publish(StationContext ctx) {
        var data=ctx.state().data();
        int pending=ctx.blockEntity() instanceof MealVessel m ? m.handily$unserved() : 0;
        String vessel=ctx.blockEntity() instanceof MealVessel m && !m.handily$vessel().isEmpty()
            ? BuiltInRegistries.ITEM.getKey(m.handily$vessel().getItem()).toString() : "";
        if (data.getInt("unserved")!=pending || !data.getString("vessel").equals(vessel)) {
            data.putInt("unserved",pending); data.putString("vessel",vessel); ctx.state().setData(data);
        }
    }
    public static boolean enabled(Object be) {
        return be instanceof RoasterBlockEntity ? Settings.ROASTER.get() : Settings.POT.get();
    }
    public static ItemStack dish(Recipe<?> recipe) {
        return recipe instanceof RoasterRecipe r ? r.getContainer().copy() : KitchenRecipes.dish(recipe).copy();
    }
    private static int debt(Container c) {
        return Math.clamp(((MealVessel)c).handily$unserved(), 0, c.getItem(7).getCount());
    }
    private static boolean matches(ItemStack stack, ItemStack dish) {
        return !stack.isEmpty() && !dish.isEmpty() && stack.is(dish.getItem());
    }
    public static ItemStack remove(Container c, int amount) {
        ItemStack food = c.getItem(7), dish = ((MealVessel)c).handily$vessel();
        int pending = debt(c), paid = food.getCount() - pending;
        int bowls = matches(c.getItem(6), dish) ? c.getItem(6).getCount() : 0;
        int count = Math.min(Math.max(0, amount), Math.min(food.getCount(), paid + bowls));
        if (pending == 0) count = Math.min(Math.max(0, amount), food.getCount());
        if (count == 0) return ItemStack.EMPTY;
        int cost = Math.max(0, count - paid);
        if (cost > 0) c.getItem(6).shrink(cost);
        ItemStack taken = food.split(count);
        ((MealVessel)c).handily$unserved(pending - cost);
        c.setItem(7, food.isEmpty() ? ItemStack.EMPTY : food); c.setChanged();
        return taken;
    }
    public static int collect(Player player, Container c, boolean one, boolean intoInventory) {
        ItemStack food = c.getItem(7), dish = ((MealVessel)c).handily$vessel();
        if (food.isEmpty()) return 0;
        ItemStack held = player.inventoryMenu.getCarried();
        // A bowl held over the dish serves into the inventory; the remaining bowls stay in the hand.
        if (matches(held, dish)) intoInventory = true;
        int pending = debt(c), paid = food.getCount() - pending;
        int bowls = matches(c.getItem(6), dish) ? c.getItem(6).getCount() : 0;
        if (matches(held, dish)) bowls += held.getCount();
        for (int i = 0; i < 36; i++) if (matches(player.getInventory().getItem(i), dish)) bowls += player.getInventory().getItem(i).getCount();
        int count = Math.min(one ? 1 : food.getCount(), pending == 0 ? food.getCount() : paid + bowls);
        int capacity = 0;
        if (intoInventory) {
            for (int i = 0; i < 36; i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (s.isEmpty()) capacity += food.getMaxStackSize();
                else if (ItemStack.isSameItemSameComponents(s, food)) capacity += Math.max(0, food.getMaxStackSize() - s.getCount());
            }
        } else capacity = held.isEmpty() ? food.getMaxStackSize()
            : ItemStack.isSameItemSameComponents(held, food) ? Math.max(0, food.getMaxStackSize() - held.getCount()) : 0;
        count = Math.min(count, capacity);
        if (count <= 0) return 0;
        int cost = Math.max(0, count - paid), remaining = cost;
        if (matches(c.getItem(6), dish)) {
            int n = Math.min(remaining, c.getItem(6).getCount()); c.getItem(6).shrink(n); remaining -= n;
        }
        if (matches(held, dish)) {
            int n = Math.min(remaining, held.getCount()); held.shrink(n); remaining -= n;
            player.inventoryMenu.setCarried(held.isEmpty() ? ItemStack.EMPTY : held);
        }
        for (int i = 0; i < 36 && remaining > 0; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (matches(s, dish)) { int n = Math.min(remaining, s.getCount()); s.shrink(n); remaining -= n; }
        }
        ItemStack taken = food.split(count);
        ((MealVessel)c).handily$unserved(pending - cost);
        c.setItem(7, food.isEmpty() ? ItemStack.EMPTY : food);
        if (intoInventory) player.getInventory().add(taken);
        else player.inventoryMenu.setCarried(held.isEmpty() ? taken : held.copyWithCount(held.getCount() + count));
        c.setChanged(); player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges();
        return count;
    }
    private MealServing() {}
}
