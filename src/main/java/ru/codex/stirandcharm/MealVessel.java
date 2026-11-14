package ru.codex.stirandcharm;
import net.minecraft.world.item.ItemStack;
/** Persisted serving debt for food cooked before a bowl or plate was supplied. */
public interface MealVessel {
    ItemStack handily$vessel();
    int handily$unserved();
    void handily$unserved(int count);
}
