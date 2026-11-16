package ru.codex.stirandcharm;
import net.minecraft.resources.ResourceLocation;
/** Recipe selected in the cookbook, saved alongside the native board contents. */
public interface BoardSelection {
    ResourceLocation handily$recipe();
    void handily$recipe(ResourceLocation recipe);
    net.minecraft.nbt.CompoundTag handily$cuts();
    void handily$cuts(net.minecraft.nbt.CompoundTag cuts);
    net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> handily$outputs();
    void handily$sync();
}
