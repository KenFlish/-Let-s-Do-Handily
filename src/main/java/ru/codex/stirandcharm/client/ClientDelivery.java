package ru.codex.stirandcharm.client;

import java.util.ArrayList;
import java.util.List;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.impl.client.focus.FocusController;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import ru.codex.stirandcharm.StirAndCharm;

/** Cosmetic inventory counts: scene motion first, Diegesis flight second, the item in its slot last. */
public final class ClientDelivery {
    private record Arrival(ItemStack item,int before,long gainsAt,long visibleAt){}
    private static final List<Arrival> arrivals=new ArrayList<>();
    private static long animationTime;
    public static void frame(StationView view,float dt){if(view.isFocused())animationTime+=(long)(Math.clamp(dt,0,.1F)*1E9);}
    public static void effect(StationView view,ResourceLocation effect,CompoundTag data){
        var player=Minecraft.getInstance().player;
        if(!effect.equals(StirAndCharm.id("delivery")) || !view.isFocused() || player==null
            || !data.hasUUID("player") || !data.getUUID("player").equals(player.getUUID()))return;
        long now=animationTime,due=now+(long)(Math.clamp(data.getFloat("seconds"),0,3)*1E9);
        var station=FocusController.get().station();
        float flight=station==null?.45F:station.scene().definition().gains().time();
        var items=data.getList("items",Tag.TAG_COMPOUND);
        for(int i=0;i<items.size();i++){
            var entry=items.getCompound(i);var item=ItemStack.parseOptional(view.level().registryAccess(),entry.getCompound("item"));if(item.isEmpty())continue;
            int before=entry.getInt("before");long wait=due;
            for(var old:arrivals)if(old.visibleAt()>now && ItemStack.isSameItemSameComponents(old.item(),item)){before=Math.min(before,old.before());wait=Math.max(wait,old.gainsAt());}
            final var match=item;arrivals.removeIf(a->ItemStack.isSameItemSameComponents(a.item(),match));
            arrivals.add(new Arrival(item,before,wait,wait+(long)((.24F+flight)*1E9)));
        }
    }
    public static ItemStack visible(Inventory inventory,int slot,boolean forGains){
        var station=FocusController.get().station();
        if(station==null || !station.type().id().getNamespace().equals(StirAndCharm.ID)){arrivals.clear();return inventory.getItem(slot);}
        long now=animationTime;arrivals.removeIf(a->a.visibleAt()<=now);
        var stack=inventory.getItem(slot);if(stack.isEmpty())return stack;
        for(var a:arrivals){
            if(now>=(forGains?a.gainsAt():a.visibleAt()) || !ItemStack.isSameItemSameComponents(stack,a.item()))continue;
            int used=0;for(int i=0;i<slot;i++){var old=inventory.getItem(i);if(ItemStack.isSameItemSameComponents(old,stack))used+=old.getCount();}
            int count=Math.clamp(a.before()-used,0,stack.getCount());return count==0?ItemStack.EMPTY:stack.copyWithCount(count);
        }
        return stack;
    }
    private ClientDelivery(){}
}
