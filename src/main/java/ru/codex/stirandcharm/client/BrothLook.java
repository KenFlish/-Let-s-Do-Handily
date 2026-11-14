package ru.codex.stirandcharm.client;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import ru.codex.stirandcharm.KitchenIngredients;

/** The colour both the world surface and the scene surface use for the same pot contents. */
final class BrothLook {
    static int color(Level level, List<ItemStack> ingredients, ItemStack meal) {
        if (!meal.isEmpty()) return FoodColors.broth(level, meal);
        int red = 0, green = 0, blue = 0, count = 0;
        for (var stack : ingredients) {
            if (KitchenIngredients.water(stack)) continue;
            int rgb = KitchenIngredients.liquid(stack) ? KitchenIngredients.liquidColor(stack) : FoodColors.main(stack);
            red += rgb >> 16 & 255;
            green += rgb >> 8 & 255;
            blue += rgb & 255;
            count++;
        }
        if (count == 0) return 0x77B9D8;
        return FoodColors.lighter((red / count << 16) | (green / count << 8) | (blue / count), .14F);
    }

    private BrothLook() {}
}