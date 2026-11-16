package ru.codex.stirandcharm.client;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import ru.codex.stirandcharm.KitchenIngredients;

/** The colour both the world surface and the scene surface use for the same pot contents. */
final class BrothLook {
    static int color(Level level,net.minecraft.core.BlockPos pos,List<ItemStack> ingredients,ItemStack meal,float mixture){
        int water=net.minecraft.client.renderer.BiomeColors.getAverageWaterColor(level,pos);
        float flow=1;
        if(level.getBlockEntity(pos) instanceof ru.codex.stirandcharm.BrothState b && b.handily$poured()!=Long.MIN_VALUE)
            flow=Math.clamp((level.getGameTime()-b.handily$poured()-10)/16F,0,1);
        for(var stack:ingredients)if(KitchenIngredients.liquid(stack) && !KitchenIngredients.water(stack))water=blend(water,KitchenIngredients.liquidColor(stack),.65F*flow);
        if(!meal.isEmpty())return FoodColors.broth(level,meal);
        var food=ingredients.stream().filter(s -> !KitchenIngredients.water(s)).toList();
        if(food.isEmpty())return water;
        int raw=FoodColors.mix(food);
        return blend(water,raw,Math.clamp(mixture,0,1)*.85F);
    }
    static int blend(int a,int b,float t){int color=0;for(int shift=0;shift<24;shift+=8)color|=Math.round((a>>shift&255)*(1-t)+(b>>shift&255)*t)<<shift;return color;}
    private BrothLook() {}
}
