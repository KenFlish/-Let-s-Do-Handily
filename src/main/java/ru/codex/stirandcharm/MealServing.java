package ru.codex.stirandcharm;

import com.pockyl.diegesis.api.station.StationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.satisfy.farm_and_charm.core.block.entity.RoasterBlockEntity;
import net.satisfy.farm_and_charm.core.recipe.RoasterRecipe;

/**
 * Charges real serving vessels only when a portion leaves the native inventory. Everything is planned
 * before anything is consumed: no vessel or dish is lost when the destination has no room.
 */
public final class MealServing {
    public static void publish(StationContext ctx) {
        var data = ctx.state().data();
        int pending = ctx.blockEntity() instanceof MealVessel m ? m.handily$unserved() : 0;
        int food = ctx.blockEntity() instanceof Container c ? c.getItem(7).getCount() : 0;
        pending = Math.max(0, Math.min(pending, food));
        String vessel = ctx.blockEntity() instanceof MealVessel m && !m.handily$vessel().isEmpty()
            ? BuiltInRegistries.ITEM.getKey(m.handily$vessel().getItem()).toString() : "";
        if (data.getInt("unserved") != pending || !data.getString("vessel").equals(vessel)) {
            data.putInt("unserved", pending);
            data.putString("vessel", vessel);
            ctx.state().setData(data);
        }
    }

    public static boolean enabled(Object be) {
        if (be instanceof BlockEntity entity) {
            var id = BuiltInRegistries.BLOCK.getKey(entity.getBlockState().getBlock());
            if (id.getNamespace().equals("candlelight") && id.getPath().equals("cooking_pot"))
                return Settings.CANDLELIGHT.get() && Settings.POT.get();
        }
        return be instanceof RoasterBlockEntity ? Settings.ROASTER.get() : Settings.POT.get();
    }

    public static ItemStack dish(Recipe<?> recipe) {
        return recipe instanceof RoasterRecipe r ? r.getContainer().copy() : KitchenRecipes.dish(recipe).copy();
    }

    public static boolean acceptsVessel(Level level, ItemStack stack) {
        return KitchenRecipes.all(level, false).stream()
            .map(h -> KitchenRecipes.dish(h.value()))
            .anyMatch(dish -> matches(stack, dish));
    }

    /** Whether the player's hand holds the vessel a ready pot on this block would serve into. */
    public static boolean servingInHand(Player player, BlockPos pos) {
        if (player == null) return false;
        var be = player.level().getBlockEntity(pos);
        return be instanceof Container c && be instanceof MealVessel && enabled(be) && canServeFromHand(player, c);
    }

    private static int pending(Container c) {
        return Math.max(0, Math.min(((MealVessel) c).handily$unserved(), c.getItem(7).getCount()));
    }

    private static boolean matches(ItemStack stack, ItemStack vessel) {
        return !stack.isEmpty() && !vessel.isEmpty() && stack.is(vessel.getItem());
    }

    private static int matching(Player player, ItemStack vessel) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            var stack = player.getInventory().getItem(i);
            if (matches(stack, vessel)) count += stack.getCount();
        }
        return count;
    }

    /** Room for {@code meal} in the inventory once {@code consumed} vessel items have been used up. */
    private static int capacity(Player player, ItemStack meal, ItemStack vessel, int consumed) {
        int capacity = 0, left = consumed;
        for (int i = 0; i < 36; i++) {
            var stack = player.getInventory().getItem(i);
            if (left > 0 && matches(stack, vessel)) {
                int used = Math.min(left, stack.getCount());
                left -= used;
                if (used == stack.getCount()) {
                    capacity += meal.getMaxStackSize();
                    continue;
                }
                stack = stack.copyWithCount(stack.getCount() - used);
            }
            if (stack.isEmpty()) capacity += meal.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(stack, meal)) capacity += Math.max(0, meal.getMaxStackSize() - stack.getCount());
        }
        return capacity;
    }

    private static void consumeInventory(Player player, ItemStack vessel, int amount) {
        int left = amount;
        for (int i = 0; i < 36 && left > 0; i++) {
            var stack = player.getInventory().getItem(i);
            if (!matches(stack, vessel)) continue;
            int used = Math.min(left, stack.getCount());
            stack.shrink(used);
            left -= used;
            if (stack.isEmpty()) player.getInventory().setItem(i, ItemStack.EMPTY);
        }
    }

    /**
     * Takes up to {@code one} portion (or all of them with shift) from the station. Vessels are paid from
     * the cursor first, then the station, then (with shift) the inventory. When the cursor held the last
     * vessel the dish takes its place; otherwise it goes to the inventory.
     */
    public static int collect(Player player, Container c, boolean one, boolean shift) {
        if (!(c instanceof MealVessel vesselData)) return 0;
        ItemStack food = c.getItem(7), vessel = vesselData.handily$vessel();
        if (food.isEmpty() || vessel.isEmpty()) return 0;
        ItemStack held = player.inventoryMenu.getCarried();
        boolean heldMatch = matches(held, vessel);
        boolean stationMatch = matches(c.getItem(6), vessel);
        int pending = pending(c), paid = Math.max(0, food.getCount() - pending);
        int heldCount = heldMatch ? held.getCount() : 0;
        int stationCount = stationMatch ? c.getItem(6).getCount() : 0;
        int inventoryCount = shift ? matching(player, vessel) : 0;
        int requested = one ? 1 : food.getCount();
        int most = Math.min(requested, paid + heldCount + stationCount + inventoryCount);
        int count = 0, useHeld = 0, useStation = 0, useInventory = 0;
        boolean toCursor = false;
        for (int n = most; n > 0; n--) {
            int cost = Math.max(0, n - paid);
            int fromHeld = Math.min(cost, heldCount);
            int fromStation = Math.min(cost - fromHeld, stationCount);
            int fromInventory = cost - fromHeld - fromStation;
            if (fromInventory > inventoryCount) continue;
            boolean cursor = !shift && (heldMatch ? heldCount - fromHeld == 0 : held.isEmpty());
            if (cursor) {
                if (n > food.getMaxStackSize()) continue;
            } else if (capacity(player, food, vessel, fromInventory) < n) continue;
            count = n; useHeld = fromHeld; useStation = fromStation; useInventory = fromInventory; toCursor = cursor;
            break;
        }
        if (count <= 0) return 0;
        int cost = Math.max(0, count - paid);
        if (useHeld > 0) {
            held.shrink(useHeld);
            player.inventoryMenu.setCarried(held.isEmpty() ? ItemStack.EMPTY : held);
        }
        if (useStation > 0) {
            var station = c.getItem(6);
            station.shrink(useStation);
            c.setItem(6, station.isEmpty() ? ItemStack.EMPTY : station);
        }
        if (useInventory > 0) consumeInventory(player, vessel, useInventory);
        ItemStack taken = food.split(count);
        vesselData.handily$unserved(pending - cost);
        c.setItem(7, food.isEmpty() ? ItemStack.EMPTY : food);
        if (toCursor) player.inventoryMenu.setCarried(taken);
        else {
            ItemStack rest = taken.copy();
            player.getInventory().add(rest);
            if (!rest.isEmpty()) player.drop(rest, false);
        }
        c.setChanged();
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        return count;
    }

    /** One portion from the vessel in the main hand, outside the scene. */
    public static boolean canServeFromHand(Player player, Container c) {
        if (!(c instanceof MealVessel vesselData)) return false;
        ItemStack food = c.getItem(7), vessel = vesselData.handily$vessel(), held = player.getMainHandItem();
        if (food.isEmpty() || vessel.isEmpty() || !matches(held, vessel)) return false;
        int pending = pending(c);
        if (food.getCount() > pending) return capacity(player, food, vessel, 0) >= 1;
        return held.getCount() == 1 || capacity(player, food, vessel, 0) >= 1;
    }

    public static int serveFromHand(Player player, Container c) {
        if (!canServeFromHand(player, c)) return 0;
        var vesselData = (MealVessel) c;
        ItemStack food = c.getItem(7), vessel = vesselData.handily$vessel(), held = player.getMainHandItem();
        int pending = pending(c), paid = Math.max(0, food.getCount() - pending);
        boolean useVessel = paid == 0;
        boolean toHand = useVessel && held.getCount() == 1;
        if (useVessel) {
            held.shrink(1);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held.isEmpty() ? ItemStack.EMPTY : held);
        }
        ItemStack portion = food.copyWithCount(1);
        food.shrink(1);
        c.setItem(7, food.isEmpty() ? ItemStack.EMPTY : food);
        vesselData.handily$unserved(pending - (useVessel ? 1 : 0));
        if (toHand) player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, portion);
        else {
            player.getInventory().add(portion);
            if (!portion.isEmpty()) player.drop(portion, false);
        }
        c.setChanged();
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        return 1;
    }

    /** The native output can still be pulled by a hopper; unpaid portions pay a vessel from the station. */
    public static ItemStack remove(Container c, int amount) {
        if (!(c instanceof MealVessel vesselData)) return ItemStack.EMPTY;
        ItemStack food = c.getItem(7), vessel = vesselData.handily$vessel();
        if (food.isEmpty()) return ItemStack.EMPTY;
        int pending = pending(c), paid = Math.max(0, food.getCount() - pending);
        int bowls = matches(c.getItem(6), vessel) ? c.getItem(6).getCount() : 0;
        int count = Math.min(Math.max(0, amount), food.getCount());
        int cost = Math.max(0, count - paid);
        if (cost > bowls) {
            count = paid + bowls;
            cost = bowls;
        }
        if (count <= 0) return ItemStack.EMPTY;
        if (cost > 0) {
            var station = c.getItem(6);
            station.shrink(cost);
            c.setItem(6, station.isEmpty() ? ItemStack.EMPTY : station);
        }
        ItemStack taken = food.split(count);
        vesselData.handily$unserved(pending - cost);
        c.setItem(7, food.isEmpty() ? ItemStack.EMPTY : food);
        c.setChanged();
        return taken;
    }

    private MealServing() {}
}