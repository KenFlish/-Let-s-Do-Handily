package ru.codex.stirandcharm;

import java.util.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Inventory transactions are planned on copies before ingredients, vessels or results change. */
public final class InventoryDelivery {
    public static ItemStack[] copy(Player player) {
        var result=new ItemStack[36];for(int i=0;i<36;i++)result[i]=player.getInventory().getItem(i).copy();return result;
    }
    public static boolean insert(ItemStack[] inventory,ItemStack offered) {
        var left=offered.copy();
        for(var stack:inventory)if(!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack,left)){
            int n=Math.min(left.getCount(),Math.max(0,stack.getMaxStackSize()-stack.getCount()));stack.grow(n);left.shrink(n);if(left.isEmpty())return true;
        }
        for(int i=0;i<inventory.length && !left.isEmpty();i++)if(inventory[i].isEmpty()){int n=Math.min(left.getCount(),left.getMaxStackSize());inventory[i]=left.split(n);}
        return left.isEmpty();
    }
    public static boolean fits(Player p,List<ItemStack> outputs){var copy=copy(p);for(var out:outputs)if(!insert(copy,out))return false;return true;}
    public static boolean give(Player p,List<ItemStack> outputs){var copy=copy(p);for(var out:outputs)if(!insert(copy,out))return false;commit(p,copy);return true;}
    public static void commit(Player p,ItemStack[] planned){for(int i=0;i<36;i++)p.getInventory().setItem(i,planned[i]);p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();}
    private InventoryDelivery(){}
}
