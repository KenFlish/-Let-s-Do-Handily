package ru.codex.stirandcharm.client;

import com.pockyl.diegesis.api.client.StationView;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** The vessel a ready station asks for, as published with the station state. */
final class Vessels {
    static boolean holds(StationView view, ItemStack stack) {
        if(stack.isEmpty())return false;
        var choices=view.state().data().getList("vessels",8);
        for(int i=0;i<choices.size();i++)if(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(choices.getString(i)))return true;
        ResourceLocation id = ResourceLocation.tryParse(view.state().data().getString("vessel"));
        return id != null && !stack.isEmpty() && stack.is(BuiltInRegistries.ITEM.get(id));
    }

    private Vessels() {}
}