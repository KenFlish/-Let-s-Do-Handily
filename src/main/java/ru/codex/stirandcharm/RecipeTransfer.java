package ru.codex.stirandcharm;

import java.util.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/** Plans on copies, then commits on the server thread. Failure never moves an item. */
public final class RecipeTransfer {
    public record Plan(ItemStack[] player, ItemStack[] station) {
        public void commit(Inventory inventory, Container container) {
            for (int i = 0; i < player.length; i++) inventory.setItem(i, player[i]);
            for (int i = 0; i < station.length; i++) container.setItem(i, station[i]);
            inventory.setChanged();
            container.setChanged();
        }
    }

    public static Optional<Plan> plan(Inventory player, Container station, int inputs, boolean bowl,
                                      List<Ingredient> ingredients, ItemStack dish) {
        return plan(player, station, inputs, bowl, ingredients, dish, false);
    }

    public static Optional<Plan> plan(Inventory player, Container station, int inputs, boolean bowl,
                                      List<Ingredient> ingredients, ItemStack dish, boolean distinctSlots) {
        var needs = ingredients.stream().filter(i -> !i.isEmpty()).toList();
        if (needs.isEmpty() || needs.size() > inputs) return Optional.empty();
        ItemStack[] inv = new ItemStack[36], out = new ItemStack[station.getContainerSize()];
        for (int i = 0; i < inv.length; i++) inv[i] = player.getItem(i).copy();
        for (int i = 0; i < out.length; i++) out[i] = station.getItem(i).copy();
        if (bowl && !out[4].isEmpty()) return Optional.empty();
        for (int i = 0; i < inputs; i++) {
            ItemStack existing = out[i];
            if (!existing.isEmpty() && ((bowl && existing.getCount() != 1) || needs.stream().noneMatch(n -> n.test(existing))))
                return Optional.empty();
        }
        // Most constrained ingredients first avoids wasting a narrow ingredient on a broad tag.
        ItemStack[] resources = new ItemStack[inputs + 36];
        for (int i = 0; i < inputs; i++) resources[i] = out[i];
        System.arraycopy(inv, 0, resources, inputs, 36);
        var ordered = new ArrayList<>(needs);
        ordered.sort(Comparator.comparingInt(n -> (int) Arrays.stream(resources).filter(n::test).count()));
        int[] available = Arrays.stream(resources).mapToInt(ItemStack::getCount).toArray();
        if (distinctSlots) for (int i = 0; i < inputs; i++) available[i] = Math.min(1, available[i]);
        int[] used = new int[resources.length];
        if (!assign(ordered, 0, resources, available, used)) return Optional.empty();
        if (bowl || distinctSlots) for (int i = 0; i < inputs; i++) if (!out[i].isEmpty() && used[i] == 0) return Optional.empty();
        // Already present ingredients are counted, not removed. Add only the missing ingredients.
        for (int i = 0; i < 36; i++) if (used[inputs + i] > 0) {
            ItemStack moving = inv[i].copyWithCount(used[inputs + i]);
            if (distinctSlots) {
                for (int slot = 0; slot < inputs && !moving.isEmpty(); slot++) if (out[slot].isEmpty()) {
                    out[slot] = moving.copyWithCount(1); moving.shrink(1);
                }
                if (!moving.isEmpty()) return Optional.empty();
            } else if (!insert(out, 0, inputs, moving, bowl ? 1 : 64)) return Optional.empty();
            inv[i].shrink(used[inputs + i]);
        }
        if (!dish.isEmpty()) {
            if (!out[6].isEmpty() && !ItemStack.isSameItemSameComponents(out[6], dish)) return Optional.empty();
            if (out[6].isEmpty()) {
                int found = -1;
                for (int i = 0; i < 36; i++) if (inv[i].is(dish.getItem())) { found = i; break; }
                if (found < 0) return Optional.empty();
                out[6] = inv[found].copyWithCount(1);
                inv[found].shrink(1);
            }
        }
        return Optional.of(new Plan(inv, out));
    }

    private static boolean assign(List<Ingredient> needs, int index, ItemStack[] stacks, int[] left, int[] used) {
        if (index == needs.size()) return true;
        for (int i = 0; i < stacks.length; i++) if (left[i] > 0 && needs.get(index).test(stacks[i])) {
            left[i]--; used[i]++;
            if (assign(needs, index + 1, stacks, left, used)) return true;
            left[i]++; used[i]--;
        }
        return false;
    }

    private static boolean insert(ItemStack[] slots, int from, int to, ItemStack stack, int limit) {
        for (int pass = 0; pass < 2; pass++) for (int i = from; i < to; i++) {
            ItemStack s = slots[i];
            if (pass == 0 && !s.isEmpty() && ItemStack.isSameItemSameComponents(s, stack)) {
                int n = Math.min(stack.getCount(), Math.min(limit, s.getMaxStackSize()) - s.getCount());
                if (n > 0) { s.grow(n); stack.shrink(n); }
            } else if (pass == 1 && s.isEmpty()) {
                int n = Math.min(stack.getCount(), Math.min(limit, stack.getMaxStackSize()));
                slots[i] = stack.copyWithCount(n); stack.shrink(n);
            }
            if (stack.isEmpty()) return true;
        }
        return stack.isEmpty();
    }
    private RecipeTransfer() {}
}
