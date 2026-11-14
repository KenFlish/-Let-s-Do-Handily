package ru.codex.stirandcharm;
import net.minecraft.resources.ResourceLocation;
/** Recipe selected in the cookbook, saved alongside the native board contents. */
public interface BoardSelection {
    ResourceLocation handily$recipe();
    void handily$recipe(ResourceLocation recipe);
}
