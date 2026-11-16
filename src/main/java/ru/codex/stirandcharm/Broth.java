package ru.codex.stirandcharm;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** One rule for when a pot holds a broth, how full it is and how high its surface stands. */
public final class Broth {
    public static final int CAPACITY = 100;
    public static final int AMOUNT = 75;
    /** The surface the native pot models leave for the broth, in scene pixels. */
    public static final float FARM_HEIGHT = 7.25F;
    public static final float CANDLELIGHT_HEIGHT = 8.75F;

    public static List<ItemStack> contents(Container container) {
        var list = new ArrayList<ItemStack>();
        for (int i = 0; i < 6; i++) {
            var stack = container.getItem(i);
            if (stack.isEmpty() || KitchenIngredients.emptyContainer(stack)) continue;
            list.add(stack);
        }
        return list;
    }

    public static boolean present(Container container) {
        return true;
    }

    public static int amount(Container container) {
        return !container.getItem(7).isEmpty()?AMOUNT:Math.min(70,58+contents(container).size()*2);
    }

    public static float height(boolean candlelight,int amount){return 2.05F+(candlelight?8.9F:6.9F)*Math.clamp(amount,0,100)/100F;}
    private Broth() {}
}