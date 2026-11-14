package ru.codex.stirandcharm;
import java.util.function.Predicate;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
/** Transfer only the real carried stack, never a client-provided item or count. */
public final class ServingContainers {
    public static void place(Player player, Container container, boolean one, Predicate<ItemStack> accepts) {
        ItemStack held = player.inventoryMenu.getCarried();
        if (held.isEmpty() || !accepts.test(held)) return;
        ItemStack existing = container.getItem(6);
        if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, held)) return;
        int room = Math.min(held.getMaxStackSize(), container.getMaxStackSize()) - existing.getCount();
        int count = Math.min(room, one ? 1 : held.getCount());
        if (count <= 0) return;
        ItemStack placed = held.copyWithCount(existing.getCount() + count);
        container.setItem(6, placed);
        held.shrink(count);
        player.inventoryMenu.setCarried(held.isEmpty() ? ItemStack.EMPTY : held);
        container.setChanged(); player.inventoryMenu.broadcastChanges();
    }
    private ServingContainers() {}
}
