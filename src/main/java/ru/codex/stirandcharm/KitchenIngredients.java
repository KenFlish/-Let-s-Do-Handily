package ru.codex.stirandcharm;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;

/** Shared classification: a container colours a broth with its contents, not its metal or glass. */
public final class KitchenIngredients {
    public static String name(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(); }
    public static boolean water(ItemStack stack) {
        if (stack.is(Items.WATER_BUCKET)) return true;
        var potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null && potion.is(Potions.WATER)) return true;
        String name = name(stack);
        return name.contains("water") && (name.contains("bottle") || name.contains("bucket"));
    }
    public static boolean milk(ItemStack stack) {
        return stack.is(Items.MILK_BUCKET) || name(stack).contains("milk") && (name(stack).contains("bucket") || name(stack).contains("bottle"));
    }
    public static boolean liquid(ItemStack stack) { return water(stack) || milk(stack) || stack.is(Items.HONEY_BOTTLE); }
    public static int liquidColor(ItemStack stack) {
        return water(stack) ? 0x8877B9D8 : milk(stack) ? 0xF8F4EAD2 : 0xE8D29A35;
    }
    public static boolean egg(ItemStack stack) { return stack.is(Items.EGG) || name(stack).equals("duck_egg"); }
    public static boolean dissolved(ItemStack stack) {
        String name = name(stack);
        return stack.is(Items.SUGAR) || name.contains("flour") || name.contains("yeast") || name.contains("salt") || name.contains("powder");
    }
    public static boolean emptyContainer(ItemStack stack) {
        return stack.is(Items.BUCKET) || stack.is(Items.GLASS_BOTTLE) || stack.is(Items.BOWL);
    }
    public static boolean recoverablePour(ItemStack stack) { return liquid(stack) || dissolved(stack) || egg(stack); }
    private KitchenIngredients() {}
}
