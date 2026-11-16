package ru.codex.stirandcharm;

import java.util.List;
import com.pockyl.diegesis.api.station.StationContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Announces the visual hand-off before the authoritative inventory packet; never delays ownership. */
public final class DeliveryPresentation {
    public static void before(StationContext c,Player p,List<ItemStack> outputs,float seconds){
        var data=new CompoundTag();data.putUUID("player",p.getUUID());data.putFloat("seconds",seconds);
        var entries=new ListTag();
        for(var stack:outputs){
            if(stack.isEmpty())continue;
            var entry=new CompoundTag();entry.put("item",stack.copyWithCount(1).save(c.level().registryAccess()));
            int before=0;for(int slot=0;slot<36;slot++){var old=p.getInventory().getItem(slot);if(ItemStack.isSameItemSameComponents(old,stack))before+=old.getCount();}
            entry.putInt("before",before);entries.add(entry);
        }
        if(entries.isEmpty())return;data.put("items",entries);c.effect(StirAndCharm.id("delivery"),"arrival",data);
    }
    private DeliveryPresentation(){}
}
