package ru.codex.stirandcharm;
import com.pockyl.diegesis.api.station.StationContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
/** Cosmetic acknowledgement: a refused transaction never plays a successful scoop. */
public final class ServingEffects {
    public static void served(StationContext c,ItemStack meal,ItemStack vessel){
        if(meal.isEmpty())return;var tag=new CompoundTag();tag.put("meal",meal.copyWithCount(1).saveOptional(c.level().registryAccess()));
        if(!vessel.isEmpty())tag.put("vessel",vessel.copyWithCount(1).saveOptional(c.level().registryAccess()));
        c.effect(StirAndCharm.id("served"),"serve_area",tag);
    }
}
