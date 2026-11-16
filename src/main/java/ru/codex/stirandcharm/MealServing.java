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
     * Serves one portion, or an available batch with Shift, straight into the inventory.
     * Vessels come from the cursor, legacy station stock, then inventory. The inventory transaction
     * accounts for a consumed inventory vessel freeing its slot; the last cursor vessel stays empty.
     */
    public static int collect(Player player, Container c, boolean one, boolean shift) {
        return collect(player,c,one,shift,stack->{});
    }
    public static int collect(Player player,Container c,boolean one,boolean shift,java.util.function.Consumer<ItemStack> beforeDelivery){
        if (!(c instanceof MealVessel vesselData)) return 0;
        int count=0;
        do {
            var food=c.getItem(7);var vessel=vesselData.handily$vessel();
            if(food.isEmpty())break;
            int debt=pending(c);boolean pay=debt>=food.getCount() && !vessel.isEmpty();
            var held=player.inventoryMenu.getCarried();var planned=InventoryDelivery.copy(player);
            int source=-3;
            if(pay){
                if(matches(held,vessel))source=-1;
                else if(matches(c.getItem(6),vessel))source=-2;
                else {int selected=player.getInventory().selected;if(matches(planned[selected],vessel))source=selected;
                    else for(int i=0;i<36;i++)if(matches(planned[i],vessel)){source=i;break;}}
                if(source==-3)break;
                if(source>=0)planned[source].shrink(1);
            }
            if(!InventoryDelivery.insert(planned,food.copyWithCount(1)))break;
            beforeDelivery.accept(food.copyWithCount(1));
            if(pay && source==-1){held.shrink(1);player.inventoryMenu.setCarried(held.isEmpty()?ItemStack.EMPTY:held);}
            if(pay && source==-2){var stack=c.getItem(6);stack.shrink(1);c.setItem(6,stack.isEmpty()?ItemStack.EMPTY:stack);}
            food.shrink(1);c.setItem(7,food.isEmpty()?ItemStack.EMPTY:food);
            vesselData.handily$unserved(debt-(pay?1:0));InventoryDelivery.commit(player,planned);c.setChanged();count++;
        } while(shift && count<64);
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
