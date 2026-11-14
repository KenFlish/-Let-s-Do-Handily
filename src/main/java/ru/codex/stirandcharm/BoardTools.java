package ru.codex.stirandcharm;

import net.minecraft.world.item.ItemStack;
import net.satisfy.farm_and_charm.core.block.CuttingBoardBlock;
import net.satisfy.farm_and_charm.core.item.CleaverItem;

public final class BoardTools {
    public static final float CENTER_X=6, CENTER_Z=8, ITEM_SIZE=8;
    public static boolean knife(ItemStack stack) {
        return stack.getItem() instanceof CleaverItem || stack.is(CuttingBoardBlock.KNIVES);
    }
    public static float markZ(int chop,int total) {return 8-3+6F*(chop+1)/(total+1);}
    private BoardTools() {}
}
